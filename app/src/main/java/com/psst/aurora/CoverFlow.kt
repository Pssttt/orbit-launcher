package com.psst.aurora

import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Curved-shelf effect for a horizontal RecyclerView: non-focused cards tilt in
 * 3D (rotationY) and recede based on distance from the row's horizontal centre.
 * The focused card stays flat and facing the viewer. Rows remain left-aligned
 * and scroll normally (no centre-snap, no edge padding).
 */
object CoverFlow {
    private const val MAX_TILT = 24f
    private const val FADE = 0.22f
    private const val MIN_SCALE = 0.93f

    fun attach(row: RecyclerView) {
        row.clipChildren = false
        row.clipToPadding = false
        row.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = transform(rv)
        })
        row.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> transform(v as RecyclerView) }
    }

    fun transform(row: RecyclerView) {
        val center = row.width / 2f
        if (center <= 0f) return
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (c.isFocused) {
                c.rotationY = 0f
                c.alpha = 1f
                continue   // focus animator owns its scale; keep it flat & facing us
            }
            val cc = c.x + c.width / 2f
            val ratio = ((cc - center) / center).coerceIn(-1f, 1f)
            c.pivotX = c.width / 2f
            c.pivotY = c.height / 2f
            c.rotationY = -ratio * MAX_TILT
            c.alpha = 1f - abs(ratio) * FADE
            val s = 1f - abs(ratio) * (1f - MIN_SCALE)
            c.scaleX = s
            c.scaleY = s
        }
    }
}
