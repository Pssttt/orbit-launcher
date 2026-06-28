package com.psst.aurora

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Horizontal layout manager that keeps LEFT/RIGHT focus inside its own row.
 * At the first item, LEFT is swallowed; at the last item, RIGHT is swallowed —
 * so the d-pad never diagonally escapes to a card in another category row.
 * UP/DOWN are left to default handling (they move between rows).
 */
class RowLayoutManager(context: Context) :
    LinearLayoutManager(context, HORIZONTAL, false) {

    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        val pos = getPosition(focused)
        if (pos == RecyclerView.NO_POSITION) return super.onInterceptFocusSearch(focused, direction)
        if (direction == View.FOCUS_LEFT && pos == 0) return focused
        if (direction == View.FOCUS_RIGHT && pos == itemCount - 1) return focused
        return super.onInterceptFocusSearch(focused, direction)
    }
}
