package com.shilapi.xcertplay.media

/** WFD and specialized secure/tunneled components are not general AVC decoders. */
internal fun legacyAvcDecoderNames(names: List<String>): List<String> = names
    .filterNot {
        it.contains("wfd", ignoreCase = true) || it.endsWith(".secure", ignoreCase = true) ||
            it.contains("tunnel", ignoreCase = true)
    }
    .distinct()
    .sortedBy {
        when {
            isSoftwareAvcDecoder(it) -> 2
            it.startsWith("OMX.MTK.", ignoreCase = true) || it.startsWith("c2.mtk.", ignoreCase = true) -> 0
            else -> 1
        }
    }

private fun isSoftwareAvcDecoder(name: String): Boolean =
    name.startsWith("OMX.google.", ignoreCase = true) || name.startsWith("c2.android.", ignoreCase = true)
