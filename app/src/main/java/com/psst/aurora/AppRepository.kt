package com.psst.aurora

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/** Discovers launchable apps (TV leanback first, then regular launcher apps). */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun loadApps(): List<AppEntry> {
        val byPackage = LinkedHashMap<String, AppEntry>()

        // 1) Android TV (leanback) launchable apps
        resolve(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { ri ->
            addEntry(byPackage, ri)
        }
        // 2) Regular launcher apps (sideloaded phone apps without a leanback entry)
        resolve(Intent.CATEGORY_LAUNCHER).forEach { ri ->
            addEntry(byPackage, ri)
        }

        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    private fun resolve(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0)
    }

    private fun addEntry(map: LinkedHashMap<String, AppEntry>, ri: ResolveInfo) {
        val pkg = ri.activityInfo.packageName ?: return
        if (pkg == context.packageName) return            // skip ourselves
        if (map.containsKey(pkg)) return                  // prefer first (leanback) match

        val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
        val banner = runCatching { ri.activityInfo.loadBanner(pm) }.getOrNull()
        val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
        val accent = AccentColors.accentFor(pkg, icon)
        val appInfoCat =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                runCatching { ri.activityInfo.applicationInfo.category }.getOrDefault(-1)
            else -1   // ApplicationInfo.category (and CATEGORY_UNDEFINED) require API 26
        val category = DefaultCategories.brandCategory(pkg) ?: DefaultCategories.fromAppInfo(appInfoCat)

        map[pkg] = AppEntry(pkg, label, launch, banner, icon, accent, category)
    }
}
