package com.psst.aurora

import android.content.Intent
import android.graphics.drawable.Drawable

/** A launchable app discovered on the device. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
    val systemBanner: Drawable?,
    val systemIcon: Drawable?,
    val accent: Int,            // brand color, else extracted from the icon
    val defaultCategory: String // brand mapping, else from ApplicationInfo.category
)

/** A named row of apps on the home screen. */
data class Category(
    val name: String,
    val apps: List<AppEntry>
)
