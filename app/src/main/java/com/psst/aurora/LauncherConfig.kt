package com.psst.aurora

import android.content.Context
import android.text.format.DateFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bundled custom banners shipped with the app, keyed by package name. */
object Banners {
    val map: Map<String, Int> = mapOf(
        "org.smarttube.beta" to R.drawable.banner_youtube,
        "com.stremio.one" to R.drawable.banner_stremio,
        "org.jellyfin.androidtv" to R.drawable.banner_jellyfin,
        "org.videolan.vlc" to R.drawable.banner_vlc,
        "com.esaba.downloader" to R.drawable.banner_downloader,
        "org.localsend.localsend_app" to R.drawable.banner_localsend,
        "com.gaditek.purevpnics" to R.drawable.banner_purevpn,
        "com.netflix.ninja" to R.drawable.banner_netflix,
        "com.rblive.app" to R.drawable.banner_rblive,
        "app.cricfy.tv" to R.drawable.banner_cricfy,
        "com.phlox.tvwebbrowser" to R.drawable.banner_browser
    )
}

/** Default category assignment for known apps; everything else falls into "Apps". */
object DefaultCategories {
    val order = listOf("Streaming", "Media", "Music", "Games", "Social", "News", "Tools", "Apps")
    private val map: Map<String, String> = mapOf(
        "org.smarttube.beta" to "Streaming",
        "com.netflix.ninja" to "Streaming",
        "com.stremio.one" to "Streaming",
        "com.rblive.app" to "Streaming",
        "app.cricfy.tv" to "Streaming",
        "org.jellyfin.androidtv" to "Media",
        "org.videolan.vlc" to "Media",
        "com.esaba.downloader" to "Tools",
        "org.localsend.localsend_app" to "Tools",
        "com.gaditek.purevpnics" to "Tools",
        "com.phlox.tvwebbrowser" to "Tools",
        "org.fossify.gallery" to "Tools"
    )

    /** Hand-mapped category for known apps; null otherwise. */
    fun brandCategory(pkg: String): String? = map[pkg]

    /** Map ApplicationInfo.category (API 26+) to one of our categories. */
    fun fromAppInfo(category: Int): String = when (category) {
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Media"
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Media"
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Music"
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News"
        else -> "Apps"
    }
}

/** User configuration persisted as JSON in filesDir/config.json. */
class ConfigStore(context: Context) {

    private val file = File(context.filesDir, "config.json")
    private val iconsDir = File(context.filesDir, "icons").apply { mkdirs() }

    private val hidden = mutableSetOf<String>()
    private val customIcons = mutableMapOf<String, String>()
    private val categoryOverride = mutableMapOf<String, String>()
    private val recents = mutableListOf<String>()
    private val categoryOrder = mutableListOf<String>()
    private val appOrder = mutableMapOf<String, MutableList<String>>()  // category -> ordered pkgs

    var wallpaperPath: String? = null;  private set
    var clock24: Boolean = DateFormat.is24HourFormat(context);  private set
    var showClock: Boolean = true;  private set
    var cardScale: Float = 1.0f;  private set       // 0.85 .. 1.25
    var globalAccent: Int = 0;  private set         // 0 = per-app accents
    var baseColor: Int = 0xFF08080C.toInt();  private set
    var useGradient: Boolean = true;  private set   // true = Aurora gradient, false = solid baseColor
    var fontScale: Float = 1.0f;  private set       // 0.9 .. 1.2

    companion object { const val MAX_RECENTS = 8 }

    init { load() }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val r = JSONObject(file.readText())
            r.optJSONArray("hidden")?.let { for (i in 0 until it.length()) hidden.add(it.getString(i)) }
            r.optJSONObject("icons")?.let { o -> o.keys().forEach { customIcons[it] = o.getString(it) } }
            r.optJSONObject("categories")?.let { o -> o.keys().forEach { categoryOverride[it] = o.getString(it) } }
            r.optJSONArray("recents")?.let { for (i in 0 until it.length()) recents.add(it.getString(i)) }
            r.optJSONArray("categoryOrder")?.let { for (i in 0 until it.length()) categoryOrder.add(it.getString(i)) }
            r.optJSONObject("appOrder")?.let { o ->
                o.keys().forEach { cat ->
                    val arr = o.getJSONArray(cat)
                    appOrder[cat] = MutableList(arr.length()) { arr.getString(it) }
                }
            }
            wallpaperPath = r.optString("wallpaper", "").ifEmpty { null }
            clock24 = r.optBoolean("clock24", clock24)
            showClock = r.optBoolean("showClock", true)
            cardScale = r.optDouble("cardScale", 1.0).toFloat()
            globalAccent = r.optInt("globalAccent", 0)
            baseColor = r.optInt("baseColor", baseColor)
            useGradient = r.optBoolean("useGradient", true)
            fontScale = r.optDouble("fontScale", 1.0).toFloat()
        }
    }

    private fun save() {
        runCatching {
            val r = JSONObject()
            r.put("hidden", JSONArray(hidden.toList()))
            r.put("icons", JSONObject(customIcons as Map<*, *>))
            r.put("categories", JSONObject(categoryOverride as Map<*, *>))
            r.put("recents", JSONArray(recents.toList()))
            r.put("categoryOrder", JSONArray(categoryOrder.toList()))
            r.put("appOrder", JSONObject(appOrder.mapValues { JSONArray(it.value) } as Map<*, *>))
            r.put("wallpaper", wallpaperPath ?: "")
            r.put("clock24", clock24)
            r.put("showClock", showClock)
            r.put("cardScale", cardScale.toDouble())
            r.put("globalAccent", globalAccent)
            r.put("baseColor", baseColor)
            r.put("useGradient", useGradient)
            r.put("fontScale", fontScale.toDouble())
            file.writeText(r.toString())
        }
    }

    // hidden
    fun isHidden(pkg: String) = hidden.contains(pkg)
    fun setHidden(pkg: String, value: Boolean) { if (value) hidden.add(pkg) else hidden.remove(pkg); save() }
    fun hiddenPackages(): Set<String> = hidden.toSet()

    // categories
    fun categoryOverrideOf(pkg: String): String? = categoryOverride[pkg]
    fun setCategory(pkg: String, category: String) { categoryOverride[pkg] = category; save() }
    fun categoryOrder(): List<String> = categoryOrder.ifEmpty { DefaultCategories.order }
    fun appOrderFor(category: String): List<String> = appOrder[category]?.toList() ?: emptyList()

    /** Swap [pkg] with its neighbour (delta -1 = left, +1 = right) within [category]'s current order. */
    fun moveAppWithin(category: String, orderedPkgs: List<String>, pkg: String, delta: Int): Boolean {
        val list = orderedPkgs.toMutableList()
        val i = list.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in list.indices) return false
        list[i] = list[j].also { list[j] = list[i] }
        appOrder[category] = list
        save()
        return true
    }
    fun setCategoryOrder(list: List<String>) { categoryOrder.clear(); categoryOrder.addAll(list); save() }
    fun renameCategory(old: String, new: String, affected: List<String>) {
        val idx = categoryOrder().indexOf(old)
        val list = categoryOrder().toMutableList()
        if (idx >= 0) list[idx] = new else list.add(new)
        affected.forEach { categoryOverride[it] = new }
        setCategoryOrder(list)   // also saves
    }

    // icons
    fun customIconPath(pkg: String): String? = customIcons[pkg]?.takeIf { File(it).exists() }
    fun iconFileFor(pkg: String) = File(iconsDir, "${pkg}.png")
    fun setCustomIcon(pkg: String, path: String) { customIcons[pkg] = path; save() }
    fun clearCustomIcon(pkg: String) { customIcons.remove(pkg)?.let { runCatching { File(it).delete() } }; save() }

    // wallpaper
    fun setWallpaper(path: String?) { wallpaperPath = path; save() }

    // recents
    fun recordLaunch(pkg: String) {
        recents.remove(pkg); recents.add(0, pkg)
        while (recents.size > MAX_RECENTS) recents.removeAt(recents.lastIndex)
        save()
    }
    fun recentPackages(): List<String> = recents.toList()

    // appearance
    fun resolveAccent(appAccent: Int): Int = if (globalAccent != 0) globalAccent else appAccent
    fun setClock24(v: Boolean) { clock24 = v; save() }
    fun setShowClock(v: Boolean) { showClock = v; save() }
    fun setCardScale(v: Float) { cardScale = v; save() }
    fun setGlobalAccent(color: Int) { globalAccent = color; save() }
    fun setBaseColor(color: Int) { baseColor = color; useGradient = false; save() }
    fun useAuroraGradient() { useGradient = true; save() }
    fun setFontScale(v: Float) { fontScale = v; save() }
}
