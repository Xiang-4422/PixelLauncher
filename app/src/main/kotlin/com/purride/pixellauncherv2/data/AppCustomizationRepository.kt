package com.purride.pixellauncherv2.data

import android.content.Context
import com.purride.pixellauncherv2.launcher.AppEntry
import org.json.JSONArray
import org.json.JSONObject

data class AppCustomization(
    val labelOverride: String = "",
    val aliases: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = labelOverride.isBlank() && aliases.isEmpty()
}

class AppCustomizationRepository(
    context: Context,
) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readAll(): Map<String, AppCustomization> {
        val rawJson = sharedPreferences.getString(KEY_CUSTOMIZATIONS, null).orEmpty()
        if (rawJson.isBlank()) {
            return emptyMap()
        }
        return runCatching {
            val root = JSONObject(rawJson)
            buildMap {
                root.keys().forEach { identity ->
                    val item = root.optJSONObject(identity) ?: return@forEach
                    val customization = AppCustomization(
                        labelOverride = item.optString(FIELD_LABEL_OVERRIDE).trim(),
                        aliases = item.optJSONArray(FIELD_ALIASES).orEmptyStrings(),
                    )
                    if (!customization.isEmpty) {
                        put(identity, customization)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun applyCustomizations(apps: List<AppEntry>): List<AppEntry> {
        return AppCustomizationModel.applyCustomizations(
            apps = apps,
            customizations = readAll(),
        )
    }

    fun saveCustomization(app: AppEntry, labelOverride: String, aliasText: String) {
        saveCustomization(
            identity = AppCustomizationModel.identity(app),
            customization = AppCustomizationModel.fromDraft(
                labelOverride = labelOverride,
                aliasText = aliasText,
            ),
        )
    }

    fun resetCustomization(app: AppEntry) {
        saveCustomization(identity = AppCustomizationModel.identity(app), customization = AppCustomization())
    }

    private fun saveCustomization(identity: String, customization: AppCustomization) {
        val next = readAll().toMutableMap()
        if (customization.isEmpty) {
            next.remove(identity)
        } else {
            next[identity] = customization
        }
        val root = JSONObject()
        next.toSortedMap().forEach { (key, value) ->
            root.put(
                key,
                JSONObject()
                    .put(FIELD_LABEL_OVERRIDE, value.labelOverride)
                    .put(FIELD_ALIASES, JSONArray(value.aliases)),
            )
        }
        sharedPreferences.edit()
            .putString(KEY_CUSTOMIZATIONS, root.toString())
            .apply()
    }

    private fun JSONArray?.orEmptyStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }.distinct().take(AppCustomizationModel.MAX_ALIAS_COUNT)
    }

    companion object {
        fun identity(app: AppEntry): String = AppCustomizationModel.identity(app)

        private const val PREFERENCES_NAME = "app_customizations"
        private const val KEY_CUSTOMIZATIONS = "customizations"
        private const val FIELD_LABEL_OVERRIDE = "labelOverride"
        private const val FIELD_ALIASES = "aliases"
    }
}
