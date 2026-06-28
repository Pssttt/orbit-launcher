package com.psst.aurora

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from the public GitHub Releases of this repo. Checks the latest release,
 * compares versions, downloads the APK, and hands it to the system installer. The repo is
 * public, so no auth is needed. Self-update only works when each release is signed with the
 * same key as the installed build.
 */
object Updater {

    private const val OWNER = "Pssttt"
    private const val REPO = "orbit-launcher"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class Release(val versionName: String, val apkUrl: String)

    /** Show an update prompt if a newer release exists. [manual] also toasts when up to date. */
    fun promptIfAvailable(activity: AppCompatActivity, manual: Boolean) {
        activity.lifecycleScope.launch {
            val release = withContext(Dispatchers.IO) { checkForUpdate(activity) }
            if (release == null) {
                if (manual) toast(activity, "Orbit is up to date")
                return@launch
            }
            AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage("Orbit ${release.versionName} is available (you have ${currentVersion(activity)}).\n\nUpdate now?")
                .setPositiveButton("Update") { _, _ -> startUpdate(activity, release) }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun startUpdate(activity: AppCompatActivity, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            toast(activity, "Allow Orbit to install apps, then update again")
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                )
            }
            return
        }
        toast(activity, "Downloading update…")
        activity.lifecycleScope.launch {
            val apk = withContext(Dispatchers.IO) { downloadApk(activity, release.apkUrl) }
            if (apk != null) installApk(activity, apk) else toast(activity, "Download failed")
        }
    }

    /** Returns a newer release, or null if up to date / unreachable. Call off the main thread. */
    fun checkForUpdate(context: Context): Release? {
        val obj = runCatching { JSONObject(fetch(API)) }.getOrNull() ?: return null
        val version = obj.optString("tag_name").removePrefix("v").ifEmpty { return null }
        val assets = obj.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break }
        }
        val url = apkUrl ?: return null
        if (compareVersions(version, currentVersion(context)) <= 0) return null
        return Release(version, url)
    }

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0"

    private fun downloadApk(context: Context, url: String): File? {
        val dest = File(context.cacheDir, "orbit-update.apk")
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            }
            try { conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } } }
            finally { conn.disconnect() }
            dest
        }.getOrNull()
    }

    private fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(i) }
    }

    private fun fetch(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "orbit-launcher")
        }
        return try { conn.inputStream.use { it.readBytes().decodeToString() } } finally { conn.disconnect() }
    }

    /** Positive if [a] is newer than [b], using dotted numeric segments. */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private fun toast(context: Context, msg: String) =
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}
