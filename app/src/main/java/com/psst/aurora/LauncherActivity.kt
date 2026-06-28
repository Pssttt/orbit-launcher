package com.psst.aurora

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var lastFocusedPkg: String? = null   // restore focus here on re-render
    private var reorderPkg: String? = null        // non-null while in rearrange mode
    private var reorderRow: String? = null        // which row is being rearranged

    companion object { private const val FAVORITES_ROW = "Favorites" }

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
        maybeCheckForUpdates()
    }

    private fun maybeCheckForUpdates() {
        val now = System.currentTimeMillis()
        if (now - config.lastUpdateCheck < 12 * 3600_000L) return
        config.setLastUpdateCheck(now)
        Updater.promptIfAvailable(this, manual = false)
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
            restoreFocus()
        }
    }

    /** Re-focus the last-focused app after a re-render; falls back to the first card. */
    private fun restoreFocus(retries: Int = 2) {
        val pkg = lastFocusedPkg
        binding.categoryList.post {
            val card = pkg?.let { findCardByPkg(it) }
            when {
                card != null -> card.requestFocus()
                pkg != null && retries > 0 -> restoreFocus(retries - 1)
                else -> binding.categoryList.requestFocus()
            }
        }
    }

    private fun findCardByPkg(pkg: String): View? {
        val list = binding.categoryList
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i).findViewById<RecyclerView>(R.id.appRow) ?: continue
            for (j in 0 until row.childCount) {
                val card = row.getChildAt(j)
                if (card.tag == pkg) return card
            }
        }
        return null
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
        if (favs.isNotEmpty()) result.add(HomeRow.Apps(Category(FAVORITES_ROW, favs)))

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
        if (hasFocus) {
            lastFocusedPkg = app.packageName
            applyGlow(config.resolveAccent(app.accent), card)
        }
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
        val gw = if (glow.width > 0) glow.width else (1200 * resources.displayMetrics.density).toInt()
        glow.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        // large, soft, low-alpha glow: broad ambiance, no visible light/dark edge near the clock
        glow.animate().x(cx - gw / 2f).y(cy - gw / 2f).alpha(0.30f).setDuration(280).start()

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

    private data class MenuAction(val icon: Int, val label: String, val run: () -> Unit)

    private fun showAppMenu(app: AppEntry, rowName: String) {
        val accent = config.resolveAccent(app.accent)
        val density = resources.displayMetrics.density
        val dialog = Dialog(this)

        val favLabel = if (config.isFavorite(app.packageName)) "Remove from favorites" else "Add to favorites"
        val actions = listOf(
            MenuAction(R.drawable.ic_star, favLabel) { config.toggleFavorite(app.packageName); loadAndRender(false) },
            MenuAction(R.drawable.ic_swap, "Rearrange") { enterReorder(app, rowName) },
            MenuAction(R.drawable.ic_image, getString(R.string.set_icon)) { pendingIconPkg = app.packageName; pickIcon.launch("image/*") },
            MenuAction(R.drawable.ic_refresh, getString(R.string.reset_icon)) { config.clearCustomIcon(app.packageName); loadAndRender(false) },
            MenuAction(R.drawable.ic_label, getString(R.string.move_category)) { chooseCategory(app) },
            MenuAction(R.drawable.ic_visibility_off, getString(R.string.hide_app)) { config.setHidden(app.packageName, true); loadAndRender(false) },
            MenuAction(R.drawable.ic_memory, getString(R.string.clear_memory)) { clearFromMemory(app) },
            MenuAction(R.drawable.ic_delete, getString(R.string.uninstall)) { uninstallApp(app.packageName) },
            MenuAction(R.drawable.ic_info, "App info") { openAppInfo(app.packageName) },
            MenuAction(R.drawable.ic_settings, getString(R.string.settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }
        )

        val panel = layoutInflater.inflate(R.layout.dialog_app_menu, null)
        panel.findViewById<TextView>(R.id.menuTitle).text = app.label
        val rows = panel.findViewById<LinearLayout>(R.id.menuRows)
        val accentTint = ColorStateList.valueOf(accent)
        actions.forEach { a ->
            val row = layoutInflater.inflate(R.layout.item_app_menu_row, rows, false)
            row.findViewById<ImageView>(R.id.rowIcon).apply { setImageResource(a.icon); imageTintList = accentTint }
            row.findViewById<TextView>(R.id.rowLabel).text = a.label
            row.setOnClickListener { dialog.dismiss(); a.run() }
            row.setOnFocusChangeListener { v, hasFocus ->
                // No translationX: shifting the row pushes its highlight past the scroll
                // clip and squares off the right corners. Fill-only keeps all corners round.
                v.background = if (hasFocus) menuRowFocusBg(accent, density) else null
            }
            rows.addView(row)
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((380 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.6f)
        }
        // cap height so long menus scroll instead of running off-screen
        val maxH = (resources.displayMetrics.heightPixels * 0.72f).toInt()
        val scroll = panel.findViewById<View>(R.id.menuScroll)
        scroll.post {
            if (scroll.height > maxH) { scroll.layoutParams = scroll.layoutParams.also { it.height = maxH } }
        }
        dialog.show()
        rows.getChildAt(0)?.requestFocus()
        panel.alpha = 0f; panel.scaleX = 0.94f; panel.scaleY = 0.94f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun menuRowFocusBg(accent: Int, density: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor((accent and 0x00FFFFFF) or (0x40 shl 24))   // accent at ~25% alpha
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

    /** Reorder [app] by [delta] within whichever row it was invoked from. */
    private fun moveApp(app: AppEntry, delta: Int, rowName: String) {
        val moved = if (rowName == FAVORITES_ROW) {
            config.moveFavorite(app.packageName, delta)
        } else {
            val cat = config.categoryOverrideOf(app.packageName) ?: app.defaultCategory
            val pkgs = (cachedApps ?: emptyList())
                .filterNot { config.isHidden(it.packageName) }
                .filter { (config.categoryOverrideOf(it.packageName) ?: it.defaultCategory) == cat }
                .let { sortInCategory(cat, it) }
                .map { it.packageName }
            config.moveAppWithin(cat, pkgs, app.packageName, delta)
        }
        // re-render restores focus to lastFocusedPkg (the moving card), so it follows the move
        if (moved) loadAndRender(false)
    }

    // ---------- rearrange mode ----------

    private fun enterReorder(app: AppEntry, rowName: String) {
        if (rowName == getString(R.string.recent)) {
            toast("Recent is ordered by recent use"); return
        }
        reorderPkg = app.packageName
        reorderRow = rowName
        lastFocusedPkg = app.packageName
        binding.appTitle.text = getString(R.string.rearranging_hint)
        restoreFocus()
        toast("Move with left / right, OK to finish")
    }

    private fun exitReorder() {
        reorderPkg = null
        reorderRow = null
        binding.appTitle.text = getString(R.string.app_name)
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
        val rp = reorderPkg
        if (rp != null && isReorderKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val app = cachedApps?.firstOrNull { it.packageName == rp }
                val rowName = reorderRow ?: ""
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> app?.let { moveApp(it, -1, rowName) }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> app?.let { moveApp(it, +1, rowName) }
                    else -> exitReorder()   // OK / BACK / UP / DOWN finish rearranging
                }
            }
            return true   // consume down and up so keys only rearrange
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isReorderKey(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
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
