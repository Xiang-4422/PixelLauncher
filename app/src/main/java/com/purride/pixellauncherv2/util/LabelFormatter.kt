package com.purride.pixellauncherv2.util

import java.util.Locale

object LabelFormatter {

    private val whitespace = Regex("\\s+")

    fun displayLabel(label: String): String {
        val normalized = collapseWhitespace(label)
        return if (normalized.isBlank()) "APP" else normalized
    }

    fun fallbackLabel(label: String, packageName: String): String {
        val normalized = collapseWhitespace(label)
        if (normalized.isNotBlank()) {
            return normalized
        }

        val packageTail = packageName.substringAfterLast('.')
        return packageTail.ifBlank { "app" }
    }

    private fun collapseWhitespace(label: String): String {
        return whitespace.replace(label.trim(), " ")
    }

    fun sortKey(label: String): String = collapseWhitespace(label).lowercase(Locale.getDefault())
}
