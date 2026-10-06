package com.shilapi.xcertplay

import android.content.Context
import java.io.File

/** Metadata only. Reuses bounded, redacted file logging; it never records audio or typed text. */
internal object GolfHardwareKeyLog {
    const val FILE_NAME = "hardware-keys.log"
    const val PREVIOUS_FILE_NAME = "hardware-keys-previous.log"
    private var writer: SessionLogFile? = null
    private var path: File? = null

    @Synchronized fun record(context: Context, message: String) {
        val file = File(context.filesDir, "logs/$FILE_NAME")
        if (path != file || writer == null) {
            writer?.close()
            path = file
            file.parentFile?.mkdirs()
            writer = SessionLogFile(file, listOf(PREVIOUS_FILE_NAME))
        }
        writer?.append("${System.currentTimeMillis()}  $message")
    }
}
