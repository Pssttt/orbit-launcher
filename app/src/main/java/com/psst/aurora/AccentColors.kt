package com.psst.aurora

import android.graphics.Color

/** Per-app accent colors used for the reactive glow and focus ring. */
object AccentColors {
    private val map: Map<String, Int> = mapOf(
        "org.smarttube.beta" to 0xFFFF3B30.toInt(),
        "com.netflix.ninja" to 0xFFE50914.toInt(),
        "com.stremio.one" to 0xFF8B5CF6.toInt(),
        "com.rblive.app" to 0xFFF43F5E.toInt(),
        "app.cricfy.tv" to 0xFF22C55E.toInt(),
        "org.jellyfin.androidtv" to 0xFF19B0E6.toInt(),
        "org.videolan.vlc" to 0xFFFF7A00.toInt(),
        "com.esaba.downloader" to 0xFFFFB020.toInt(),
        "org.localsend.localsend_app" to 0xFF14C4B0.toInt(),
        "com.gaditek.purevpnics" to 0xFF3DDC84.toInt(),
        "com.phlox.tvwebbrowser" to 0xFF3B9BFF.toInt(),
        "org.fossify.gallery" to 0xFF6D8BFF.toInt()
    )

    private val default = 0xFF8B5CF6.toInt()

    fun forPackage(pkg: String): Int = map[pkg] ?: default
}
