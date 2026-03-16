package com.purride.pixellauncherv2.launcher

object DrawerAsciiInputSanitizer {

    fun filter(text: String): String {
        return buildString(text.length) {
            text.forEach { char ->
                if (char.code in 32..126) {
                    append(char)
                }
            }
        }
    }
}
