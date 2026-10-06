package com.shilapi.xcertplay.media

/** Prefer a normal AVC vendor decoder; a Wi-Fi Display component is not a generic decoder. */
internal fun legacyAvcDecoderNames(names: List<String>): List<String> = names
    .filterNot { it.contains("wfd", ignoreCase = true) }
    .sortedBy { if (it.startsWith("OMX.google.", ignoreCase = true) ||
        it.startsWith("c2.android.", ignoreCase = true)) 1 else 0 }
