package com.psst.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal image loader for watch-next poster art. Handles http(s) and content:// URIs,
 * downsamples to a poster-sized bitmap, and keeps a small in-memory LRU cache. Deliberately
 * tiny — no Glide/Coil — to stay light on 2 GB hardware.
 */
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(uri: String): Bitmap? = cache.get(uri)

    /** Blocking load; call off the main thread. Returns null on any failure. */
    fun load(context: Context, uri: String): Bitmap? {
        cache.get(uri)?.let { return it }
        val bytes = runCatching {
            when {
                uri.startsWith("http") -> {
                    val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        instanceFollowRedirects = true
                    }
                    try { conn.inputStream.use { it.readBytes() } } finally { conn.disconnect() }
                }
                else -> context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            }
        }.getOrNull() ?: return null
        val bmp = decodeScaled(bytes) ?: return null
        cache.put(uri, bmp)
        return bmp
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 540) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
