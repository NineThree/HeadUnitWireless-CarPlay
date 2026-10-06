package com.shilapi.xcertplay

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager

/** WindowInsetsControllerCompat does not implement system bar visibility below API20. */
internal object LegacyFullscreen {
    @Suppress("DEPRECATION")
    fun apply(window: Window, hideTop: Boolean, hideBottom: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 20) return false
        if (hideTop) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Do not draw behind a bar which an OEM may refuse to hide. Preserve unrelated flags.
        val owned = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        var flags = window.decorView.systemUiVisibility and owned.inv()
        if (hideTop) flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN
        // API17/18's temporary navigation hiding consumes the first touch. Keep it visible.
        if (Build.VERSION.SDK_INT >= 19 && hideBottom) {
            flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        window.decorView.systemUiVisibility = flags
        return true
    }
}
