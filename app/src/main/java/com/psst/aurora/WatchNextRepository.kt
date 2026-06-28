package com.psst.aurora

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri

/**
 * Reads the system "watch next" list (continue-watching) published by other apps to the
 * TV provider. Requires the normal `com.android.providers.tv.permission.READ_EPG_DATA`
 * permission. Column names are the stable TvContract.WatchNextPrograms contract, used as
 * raw strings to avoid pulling in the androidx.tvprovider dependency.
 */
class WatchNextRepository(private val context: Context) {

    fun load(limit: Int = 12): List<WatchItem> {
        val items = ArrayList<WatchItem>()
        runCatching {
            context.contentResolver.query(URI, PROJECTION, null, null, "$COL_ENGAGED DESC")?.use { c ->
                val iId = c.getColumnIndex(COL_ID)
                val iTitle = c.getColumnIndex(COL_TITLE)
                val iPoster = c.getColumnIndex(COL_POSTER)
                val iIntent = c.getColumnIndex(COL_INTENT)
                val iPkg = c.getColumnIndex(COL_PKG)
                val iPos = c.getColumnIndex(COL_POSITION)
                val iDur = c.getColumnIndex(COL_DURATION)
                val iEng = c.getColumnIndex(COL_ENGAGED)
                while (c.moveToNext() && items.size < limit) {
                    val intentUri = c.str(iIntent) ?: continue
                    val intent = runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }
                        .getOrNull() ?: continue
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val pos = c.lng(iPos)
                    val dur = c.lng(iDur)
                    val progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else -1f
                    items.add(
                        WatchItem(
                            id = c.lng(iId),
                            title = c.str(iTitle).orEmpty(),
                            posterUri = c.str(iPoster),
                            launchIntent = intent,
                            packageName = c.str(iPkg).orEmpty(),
                            progress = progress,
                            lastEngaged = c.lng(iEng),
                            accent = 0
                        )
                    )
                }
            }
        }
        return items
    }

    private fun Cursor.str(i: Int): String? = if (i >= 0 && !isNull(i)) getString(i) else null
    private fun Cursor.lng(i: Int): Long = if (i >= 0 && !isNull(i)) getLong(i) else 0L

    companion object {
        private val URI: Uri = Uri.parse("content://android.media.tv/watch_next_program")
        private const val COL_ID = "_id"
        private const val COL_TITLE = "title"
        private const val COL_POSTER = "poster_art_uri"
        private const val COL_INTENT = "intent_uri"
        private const val COL_PKG = "package_name"
        private const val COL_POSITION = "last_playback_position_millis"
        private const val COL_DURATION = "duration_millis"
        private const val COL_ENGAGED = "last_engagement_time_utc_millis"
        private val PROJECTION = arrayOf(
            COL_ID, COL_TITLE, COL_POSTER, COL_INTENT, COL_PKG, COL_POSITION, COL_DURATION, COL_ENGAGED
        )
    }
}
