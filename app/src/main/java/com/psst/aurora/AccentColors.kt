package com.psst.aurora

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette

/** Per-app accent colors: hand-picked brand colors where known, else extracted from the icon. */
object AccentColors {
    val DEFAULT = 0xFF8B5CF6.toInt()

    private val brand: Map<String, Int> = mapOf(
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

    fun brandFor(pkg: String): Int? = brand[pkg]

    /** Brand color if known, otherwise a vibrant color pulled from the app icon. */
    fun accentFor(pkg: String, icon: Drawable?): Int = brand[pkg] ?: fromIcon(icon)

    private fun fromIcon(icon: Drawable?): Int {
        val bmp = icon?.toBitmapSafe() ?: return DEFAULT
        return runCatching {
            val p = Palette.from(bmp).clearFilters().generate()
            p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(DEFAULT)))
        }.getOrDefault(DEFAULT)
    }

    private fun Drawable.toBitmapSafe(): Bitmap? {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = intrinsicWidth.takeIf { it > 0 } ?: 96
        val h = intrinsicHeight.takeIf { it > 0 } ?: 96
        return runCatching {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            setBounds(0, 0, w, h)
            draw(Canvas(b))
            b
        }.getOrNull()
    }
}
