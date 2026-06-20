package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.AppEntry

object AppCustomizationModel {

    fun identity(app: AppEntry): String = "${app.packageName}/${app.activityName}"

    fun fromDraft(labelOverride: String, aliasText: String): AppCustomization {
        return AppCustomization(
            labelOverride = labelOverride.trim(),
            aliases = parseAliases(aliasText),
        )
    }

    fun applyCustomizations(
        apps: List<AppEntry>,
        customizations: Map<String, AppCustomization>,
    ): List<AppEntry> {
        if (customizations.isEmpty()) {
            return apps
        }
        return apps.map { app ->
            val customization = customizations[identity(app)] ?: return@map app
            app.copy(
                label = customization.labelOverride.ifBlank { app.systemLabel },
                aliases = customization.aliases,
            )
        }
    }

    fun parseAliases(raw: String): List<String> {
        return raw
            .split(aliasSeparator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_ALIAS_COUNT)
    }

    const val MAX_ALIAS_COUNT: Int = 8
    private val aliasSeparator = Regex("[,，\\s]+")
}
