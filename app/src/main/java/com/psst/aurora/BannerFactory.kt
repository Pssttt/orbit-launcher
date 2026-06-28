package com.psst.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Generates a consistent banner (app icon on the left, label on the right, on the
 * shared navy background) for apps that don't ship a bundled custom banner.
 * Results are cached per package.
 */
object BannerFactory {
    private const val W = 640
    private const val H = 360
    private const val BG = 0xFF000B25.toInt()
    private val cache = HashMap<String, Bitmap>()

    fun get(app: AppEntry): Bitmap = cache.getOrPut(app.packageName) { render(app) }

    private fun render(app: AppEntry): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)

        val iconSize = 148
        val iconLeft = 72
        val iconTop = (H - iconSize) / 2
        app.systemIcon?.let { d ->
            d.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            d.draw(c)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 54f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val textLeft = (iconLeft + iconSize + 38).toFloat()
        val maxWidth = W - textLeft - 28f
        val label = ellipsize(app.label, paint, maxWidth)
        val fm = paint.fontMetrics
        val baseline = H / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(label, textLeft, baseline, paint)
        return bmp
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }
}
