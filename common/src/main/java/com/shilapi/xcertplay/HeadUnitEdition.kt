package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.host.R

/** Build resources select branding; package IDs isolate settings and identity between editions. */
internal object HeadUnitEdition {
    fun universal(context: Context) = context.resources.getBoolean(R.bool.config_universal_headunit)
    fun title(context: Context) = context.getString(R.string.wireless_app_name)
    fun oemLabel(context: Context) = if (universal(context)) "HeadUnit" else "Golf"
    fun reportDirectory(context: Context) = if (universal(context)) "HeadUnitWireless" else "GolfWireless"
    fun reportPrefix(context: Context) = if (universal(context)) "headunit-wireless" else "golf-wireless"
    @Suppress("DEPRECATION")
    fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "source-preview"
}
