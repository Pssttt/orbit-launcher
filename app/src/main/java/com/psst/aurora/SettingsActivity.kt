package com.psst.aurora

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.psst.aurora.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var config: ConfigStore

    private val accentNames = arrayOf(
        "Auto (per app)", "Violet", "Blue", "Teal", "Green", "Amber", "Red", "Pink", "White"
    )
    private val accentValues = intArrayOf(
        0, 0xFF8B5CF6.toInt(), 0xFF3B9BFF.toInt(), 0xFF14C4B0.toInt(), 0xFF3DDC84.toInt(),
        0xFFFFB020.toInt(), 0xFFFF3B30.toInt(), 0xFFF43F5E.toInt(), 0xFFFFFFFF.toInt()
    )
    private val baseNames = arrayOf("Aurora gradient", "Midnight", "Pure black", "Deep navy", "Charcoal")
    private val baseValues = intArrayOf(0xFF08080C.toInt(), 0xFF000000.toInt(), 0xFF000B25.toInt(), 0xFF14141A.toInt())
    private val sizeNames = arrayOf("Small", "Medium", "Large")
    private val sizeValues = floatArrayOf(0.85f, 1.0f, 1.18f)

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importWallpaper(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        config = ConfigStore(this)

        binding.btnAccent.setOnClickListener {
            pick("Accent color", accentNames) { config.setGlobalAccent(accentValues[it]) }
        }
        binding.btnBase.setOnClickListener {
            pick("Background", baseNames) {
                if (it == 0) config.useAuroraGradient() else config.setBaseColor(baseValues[it - 1])
            }
        }
        binding.btnCardSize.setOnClickListener {
            pick("Card size", sizeNames) { config.setCardScale(sizeValues[it]) }
        }
        binding.btnFontSize.setOnClickListener {
            pick("Font size", sizeNames) { config.setFontScale(floatArrayOf(0.9f, 1.0f, 1.15f)[it]) }
        }
        binding.btnClock.setOnClickListener {
            pick("Clock format", arrayOf("24-hour", "12-hour")) { config.setClock24(it == 0) }
        }
        binding.btnShowClock.setOnClickListener {
            pick("Show clock", arrayOf("Show", "Hide")) { config.setShowClock(it == 0) }
        }
        binding.btnWallpaper.setOnClickListener { pickWallpaper.launch("image/*") }
        binding.btnResetWallpaper.setOnClickListener { config.setWallpaper(null); toast("Wallpaper reset") }
        binding.btnManageApps.setOnClickListener { startActivity(Intent(this, ManageAppsActivity::class.java)) }
        binding.btnCategories.setOnClickListener { startActivity(Intent(this, CategoriesActivity::class.java)) }
        binding.btnFreeMemory.setOnClickListener { freeMemory() }
        binding.btnScreensaver.setOnClickListener {
            pick("Screensaver", arrayOf("On (after 3 min idle)", "Off")) { config.setScreensaver(it == 0) }
        }
        binding.btnCheckUpdates.setOnClickListener { Updater.promptIfAvailable(this, manual = true) }

        binding.btnAccent.requestFocus()
    }

    private fun freeMemory() {
        lifecycleScope.launch {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val freedMb = withContext(Dispatchers.IO) {
                val before = availMem(am)
                val apps = AppRepository(this@SettingsActivity).loadApps()
                apps.forEach { runCatching { am.killBackgroundProcesses(it.packageName) } }
                ((availMem(am) - before) / (1024 * 1024)).coerceAtLeast(0)
            }
            toast(if (freedMb > 0) "Freed ${freedMb} MB" else "Memory cleared")
        }
    }

    private fun availMem(am: android.app.ActivityManager): Long {
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    private fun pick(title: String, items: Array<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which -> onPick(which); toast("Saved") }
            .show()
    }

    private fun importWallpaper(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(filesDir, "wallpaper.jpg")
                    contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    config.setWallpaper(dest.absolutePath); true
                }.getOrDefault(false)
            }
            toast(if (ok) "Wallpaper set" else "Couldn't set wallpaper")
        }
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
