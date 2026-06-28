package com.psst.aurora

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.psst.aurora.databinding.ActivityLauncherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private lateinit var config: ConfigStore
    private lateinit var repo: AppRepository
    private lateinit var watchRepo: WatchNextRepository

    private var pendingIconPkg: String? = null
    private var cachedApps: List<AppEntry>? = null
    private var appliedWallpaper: String? = "__none__"
    private var createdFontScale = 1.0f

    private val idleHandler = Handler(Looper.getMainLooper())
    private val showSaver = Runnable { showScreensaver() }
    private val idleTimeoutMs = 180_000L

    override fun attachBaseContext(newBase: Context) {
        val fs = ConfigStore(newBase).fontScale
        val cfg = android.content.res.Configuration(newBase.resources.configuration)
        cfg.fontScale = fs
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val pkg = pendingIconPkg ?: return@registerForActivityResult
        pendingIconPkg = null
        if (uri != null) importIcon(pkg, uri)
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            cachedApps = null
            loadAndRender(animate = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ConfigStore(this)
        repo = AppRepository(this)
        watchRepo = WatchNextRepository(this)
        createdFontScale = config.fontScale

        binding.categoryList.layoutManager = LinearLayoutManager(this)
        binding.categoryList.clipChildren = false
        binding.categoryList.clipToPadding = false
        (binding.root as? ViewGroup)?.clipChildren = false
        binding.categoryList.setItemViewCacheSize(12)

        binding.searchChip.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        binding.settingsChip.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        applyWallpaper()
        startClock()
        startKenBurns()
        registerPkgReceiver()
        loadAndRender(animate = true)
    }

    override fun onResume() {
        super.onResume()
        val fresh = ConfigStore(this)
        if (fresh.fontScale != createdFontScale) { recreate(); return }  // re-apply font scale
        config = fresh                   // re-read from disk so Settings changes apply
        binding.root.setBackgroundColor(config.baseColor)
        applyWallpaper()
        loadAndRender(animate = false)   // cheap (cached) — refreshes recents/settings
        resetIdle()
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacks(showSaver)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleHandler.removeCallbacks(showSaver)
        runCatching { unregisterReceiver(pkgReceiver) }
    }

    // ---------- screensaver ----------

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetIdle()
    }

    private fun resetIdle() {
        idleHandler.removeCallbacks(showSaver)
        if (binding.screensaver.visibility == View.VISIBLE) hideScreensaver()
        if (config.screensaver) idleHandler.postDelayed(showSaver, idleTimeoutMs)
    }

    private fun showScreensaver() {
        updateSaverClock()
        binding.screensaver.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(900).start()
        }
    }

    private fun hideScreensaver() {
        binding.screensaver.animate().alpha(0f).setDuration(300).withEndAction {
            binding.screensaver.visibility = View.GONE
        }.start()
    }

    private fun updateSaverClock() {
        val now = Date()
        val tf = SimpleDateFormat(if (config.clock24) "HH:mm" else "h:mm", Locale.getDefault())
        binding.saverClock.text = tf.format(now)
        binding.saverDate.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    }

    private fun registerPkgReceiver() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, f)
    }

    // ---------- data ----------

    private fun loadAndRender(animate: Boolean) {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { cachedApps ?: repo.loadApps().also { cachedApps = it } }
            val watch = withContext(Dispatchers.IO) { watchRepo.load() }
            val rows = buildRows(apps, watch)
            binding.emptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            binding.categoryList.adapter = CategoryAdapter(
                rows, config, ::launchApp, ::showAppMenu, ::onCardFocus,
                ::launchWatch, ::onWatchFocus, lifecycleScope
            )
            binding.categoryList.scrollToPosition(0)   // keep top row clear of the header after re-render
            if (animate) {
                binding.categoryList.layoutAnimation =
                    AnimationUtils.loadLayoutAnimation(this@LauncherActivity, R.anim.layout_stagger)
                binding.categoryList.scheduleLayoutAnimation()
            }
            binding.categoryList.post { binding.categoryList.requestFocus() }
        }
    }

    private fun buildRows(apps: List<AppEntry>, watch: List<WatchItem>): List<HomeRow> {
        val visible = apps.filterNot { config.isHidden(it.packageName) }
        val byPkg = visible.associateBy { it.packageName }
        val result = mutableListOf<HomeRow>()

        val watchItems = watch.map { item ->
            val app = byPkg[item.packageName]
            item.copy(accent = config.resolveAccent(app?.accent ?: 0))
        }
        if (watchItems.isNotEmpty()) result.add(HomeRow.Watch(getString(R.string.continue_watching), watchItems))

        val favs = config.favoritePackages().mapNotNull { byPkg[it] }
        if (favs.isNotEmpty()) result.add(HomeRow.Apps(Category("Favorites", favs)))

        val recents = config.recentPackages().mapNotNull { byPkg[it] }
        if (recents.isNotEmpty()) result.add(HomeRow.Apps(Category(getString(R.string.recent), recents)))

        val grouped = visible.groupBy { config.categoryOverrideOf(it.packageName) ?: it.defaultCategory }
        val order = LinkedHashSet<String>().apply {
            addAll(config.categoryOrder())
            addAll(grouped.keys.sorted())
        }
        order.forEach { name ->
            grouped[name]?.takeIf { it.isNotEmpty() }
                ?.let { result.add(HomeRow.Apps(Category(name, sortInCategory(name, it)))) }
        }
        return result
    }

    private fun sortInCategory(category: String, apps: List<AppEntry>): List<AppEntry> {
        val ord = config.appOrderFor(category)
        return apps.sortedWith(
            compareBy(
                { val i = ord.indexOf(it.packageName); if (i < 0) Int.MAX_VALUE else i },
                { it.label.lowercase() }
            )
        )
    }

    // ---------- reactive glow + parallax ----------

    private fun onCardFocus(app: AppEntry, card: View, hasFocus: Boolean) {
        if (hasFocus) applyGlow(config.resolveAccent(app.accent), card)
    }

    private fun onWatchFocus(accent: Int, card: View, hasFocus: Boolean) {
        if (hasFocus) applyGlow(accent, card)
    }

    private fun applyGlow(accent: Int, card: View) {
        val root = binding.root
        val loc = IntArray(2); card.getLocationInWindow(loc)
        val rootLoc = IntArray(2); root.getLocationInWindow(rootLoc)
        val cx = (loc[0] - rootLoc[0]) + card.width / 2f
        val cy = (loc[1] - rootLoc[1]) + card.height / 2f

        val glow = binding.ambientGlow
        val gw = if (glow.width > 0) glow.width else (760 * resources.displayMetrics.density).toInt()
        glow.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        glow.animate().x(cx - gw / 2f).y(cy - gw / 2f).alpha(0.45f).setDuration(280).start()

        val dx = cx - root.width / 2f
        val dy = cy - root.height / 2f
        binding.wallpaper.animate()
            .translationX(-dx * 0.022f).translationY(-dy * 0.022f).setDuration(320).start()
    }

    // ---------- actions ----------

    private fun launchApp(app: AppEntry) {
        config.recordLaunch(app.packageName)
        runCatching { startActivity(app.launchIntent) }.onFailure { toast("Can't open ${app.label}") }
    }

    private fun launchWatch(item: WatchItem) {
        if (item.packageName.isNotEmpty()) config.recordLaunch(item.packageName)
        runCatching { startActivity(item.launchIntent) }
            .onFailure { toast("Can't resume ${item.title}") }
    }

    private fun showAppMenu(app: AppEntry) {
        val favLabel = if (config.isFavorite(app.packageName)) "Remove from favorites" else "Add to favorites"
        val options = arrayOf(
            favLabel,
            "Move left",
            "Move right",
            getString(R.string.set_icon),
            getString(R.string.reset_icon),
            getString(R.string.move_category),
            getString(R.string.hide_app),
            getString(R.string.clear_memory),
            getString(R.string.uninstall),
            "App info",
            getString(R.string.settings)
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { config.toggleFavorite(app.packageName); loadAndRender(false) }
                    1 -> moveApp(app, -1)
                    2 -> moveApp(app, +1)
                    3 -> { pendingIconPkg = app.packageName; pickIcon.launch("image/*") }
                    4 -> { config.clearCustomIcon(app.packageName); loadAndRender(false) }
                    5 -> chooseCategory(app)
                    6 -> { config.setHidden(app.packageName, true); loadAndRender(false) }
                    7 -> clearFromMemory(app)
                    8 -> uninstallApp(app.packageName)
                    9 -> openAppInfo(app.packageName)
                    10 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .show()
    }

    private fun clearFromMemory(app: AppEntry) {
        runCatching {
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager)
                .killBackgroundProcesses(app.packageName)
        }
        toast("Cleared ${app.label} from memory")
    }

    private fun uninstallApp(pkg: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        }.onFailure { toast("Can't uninstall") }
    }

    private fun moveApp(app: AppEntry, delta: Int) {
        val cat = config.categoryOverrideOf(app.packageName) ?: app.defaultCategory
        val pkgs = (cachedApps ?: emptyList())
            .filterNot { config.isHidden(it.packageName) }
            .filter { (config.categoryOverrideOf(it.packageName) ?: it.defaultCategory) == cat }
            .let { sortInCategory(cat, it) }
            .map { it.packageName }
        if (config.moveAppWithin(cat, pkgs, app.packageName, delta)) loadAndRender(false)
        else toast(if (delta < 0) "Already first" else "Already last")
    }

    private fun chooseCategory(app: AppEntry) {
        val cats = config.categoryOrder().toMutableList().apply { add("New category…") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.move_category))
            .setItems(cats) { _, which ->
                if (which == cats.lastIndex) promptNewCategory(app)
                else { config.setCategory(app.packageName, cats[which]); loadAndRender(false) }
            }
            .show()
    }

    private fun promptNewCategory(app: AppEntry) {
        val input = android.widget.EditText(this)
        AlertDialog.Builder(this)
            .setTitle("New category")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    config.setCategoryOrder(config.categoryOrder() + name)
                    config.setCategory(app.packageName, name)
                    loadAndRender(false)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppInfo(pkg: String) {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg")))
        }
    }

    private fun importIcon(pkg: String, uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = config.iconFileFor(pkg)
                    contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    config.setCustomIcon(pkg, dest.absolutePath); true
                }.getOrDefault(false)
            }
            if (ok) loadAndRender(false) else toast("Couldn't import image")
        }
    }

    private fun applyWallpaper() {
        val path = config.wallpaperPath
        val key = when {
            path != null -> "file:$path"
            config.useGradient -> "grad"
            else -> "solid:${config.baseColor}"
        }
        if (key == appliedWallpaper) return
        appliedWallpaper = key
        when {
            path != null && File(path).exists() ->
                BitmapFactory.decodeFile(path)?.let { binding.wallpaper.setImageBitmap(it) }
            config.useGradient -> binding.wallpaper.setImageResource(R.drawable.bg_aurora)
            else -> binding.wallpaper.setImageDrawable(
                android.graphics.drawable.ColorDrawable(config.baseColor)
            )
        }
    }

    private fun startKenBurns() {
        binding.wallpaper.scaleX = 1.08f
        binding.wallpaper.scaleY = 1.08f
        listOf("scaleX", "scaleY").forEach { prop ->
            ObjectAnimator.ofFloat(binding.wallpaper, prop, 1.08f, 1.16f).apply {
                duration = 42000
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        }
    }

    private fun startClock() {
        lifecycleScope.launch {
            while (isActive) {
                if (config.showClock) {
                    binding.clock.visibility = View.VISIBLE
                    binding.date.visibility = View.VISIBLE
                    val now = Date()
                    val tf = SimpleDateFormat(if (config.clock24) "HH:mm" else "h:mm a", Locale.getDefault())
                    binding.clock.text = tf.format(now)
                    binding.date.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
                    if (binding.screensaver.visibility == View.VISIBLE) updateSaverClock()
                } else {
                    binding.clock.visibility = View.GONE
                    binding.date.visibility = View.GONE
                }
                delay(15_000)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.screensaver.visibility == View.VISIBLE) {
            if (event.action == KeyEvent.ACTION_DOWN) resetIdle()  // dismiss + restart timer
            val passThrough = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER -> true
                else -> false
            }
            if (!passThrough) return true  // swallow nav keys so they only dismiss
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(Intent(this, SettingsActivity::class.java)); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Home launcher: BACK should stay on home")
    override fun onBackPressed() { /* stay on home */ }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
