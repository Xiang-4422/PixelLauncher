package com.purride.pixellauncherv2.launcher

data class DrawerAlphaIndexModel(
    val letterToFirstAppIndex: List<Int>,
    val selectedLetterIndex: Int,
) {

    fun resolveNearestLetterAppIndex(letterIndex: Int): Int? {
        val safeLetterIndex = letterIndex.coerceIn(0, lastLetterIndex)
        val directHit = letterToFirstAppIndex[safeLetterIndex]
        if (directHit >= 0) {
            return directHit
        }

        for (delta in 1..letterCount) {
            val left = safeLetterIndex - delta
            if (left >= 0) {
                val leftHit = letterToFirstAppIndex[left]
                if (leftHit >= 0) {
                    return leftHit
                }
            }

            val right = safeLetterIndex + delta
            if (right <= lastLetterIndex) {
                val rightHit = letterToFirstAppIndex[right]
                if (rightHit >= 0) {
                    return rightHit
                }
            }
        }
        return null
    }

    companion object {
        const val letterCount: Int = 26
        const val lastLetterIndex: Int = letterCount - 1

        fun create(
            apps: List<AppEntry>,
            selectedIndex: Int,
        ): DrawerAlphaIndexModel {
            val firstIndices = MutableList(letterCount) { -1 }
            apps.forEachIndexed { index, appEntry ->
                val letterIndex = letterIndexForLabel(appEntry.label)
                if (firstIndices[letterIndex] < 0) {
                    firstIndices[letterIndex] = index
                }
            }

            val selectedLetter = if (apps.isEmpty()) {
                0
            } else {
                val safeIndex = selectedIndex.coerceIn(0, apps.lastIndex)
                letterIndexForLabel(apps[safeIndex].label)
            }

            return DrawerAlphaIndexModel(
                letterToFirstAppIndex = firstIndices,
                selectedLetterIndex = selectedLetter,
            )
        }

        fun letterAt(index: Int): Char {
            return ('A'.code + index.coerceIn(0, lastLetterIndex)).toChar()
        }

        fun letterIndexForLabel(label: String): Int {
            return DrawerSearchSupport.letterIndexForLabel(label)
        }
    }
}
