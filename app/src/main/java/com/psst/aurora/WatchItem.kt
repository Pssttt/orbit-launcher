package com.psst.aurora

import android.content.Intent

/** A "continue watching" entry read from the system watch-next provider. */
data class WatchItem(
    val id: Long,
    val title: String,
    val posterUri: String?,
    val launchIntent: Intent,
    val packageName: String,
    val progress: Float,   // 0..1 watched fraction, or -1 if unknown
    val lastEngaged: Long,
    val accent: Int        // resolved accent for the owning app
)

/** A row on the home screen: either a row of apps or the continue-watching row. */
sealed class HomeRow {
    data class Apps(val category: Category) : HomeRow()
    data class Watch(val title: String, val items: List<WatchItem>) : HomeRow()
}
