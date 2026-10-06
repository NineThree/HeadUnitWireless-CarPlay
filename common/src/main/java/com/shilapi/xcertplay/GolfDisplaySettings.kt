package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.CarPlayDisplayScale

/** Owner-selected display settings, separate from connection and pairing preferences. */
internal object GolfDisplaySettings {
    enum class Quality(val id: String, val scaleTenths: Int, val label: String) {
        BALANCED("balanced", 8, "均衡"),
        SMOOTH("smooth", 6, "流畅"),
        NATIVE("native", 10, "清晰"),
    }

    const val topBarInsetDp = 64

    fun label(context: Context, quality: Quality): String {
        val metrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(2)
        val fullHeight = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(2)
        val inset = if (avoidTopBar(context)) (topBarInsetDp * metrics.density).toInt() else 0
        return labelFor(quality, width, (fullHeight - inset).coerceAtLeast(2))
    }

    internal fun labelFor(quality: Quality, width: Int, height: Int): String {
        val size = CarPlayDisplayScale.apply(AirPlayDisplayConfig(width, height), quality.scaleTenths)
        val detail = if (quality == Quality.NATIVE) "跟随屏幕，约" else "约"
        return "${quality.label}（$detail${size.widthPixels}×${size.heightPixels}）"
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("golf_wireless_display", Context.MODE_PRIVATE)

    fun quality(context: Context): Quality = when (preferences(context).getString("quality", null)) {
        Quality.SMOOTH.id -> Quality.SMOOTH
        Quality.NATIVE.id -> Quality.NATIVE
        else -> Quality.BALANCED
    }

    fun avoidTopBar(context: Context): Boolean = preferences(context).getBoolean("avoid_top_bar", false)

    fun save(context: Context, quality: Quality, avoidTopBar: Boolean) {
        preferences(context).edit()
            .putString("quality", quality.id)
            .putBoolean("avoid_top_bar", avoidTopBar)
            .apply()
    }
}
