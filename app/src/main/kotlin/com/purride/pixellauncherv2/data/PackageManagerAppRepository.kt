package com.purride.pixellauncherv2.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.os.Build
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.util.LabelFormatter
import java.text.Collator
import java.util.Locale

class PackageManagerAppRepository(
    private val context: Context,
) : AppRepository {

    private val packageManager: PackageManager = context.packageManager
    private val labelCollator: Collator = Collator.getInstance(Locale.getDefault())

    /**
     * 读取所有可启动 Activity，推导多语言标签，并返回稳定的本地化排序结果。
     */
    override fun loadLaunchableApps(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return queryLauncherActivities(launcherIntent)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName == context.packageName) {
                    return@mapNotNull null
                }

                val systemLabel = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                val chineseLabel = loadLabelForLocale(
                    resolveInfo = resolveInfo,
                    locale = Locale.SIMPLIFIED_CHINESE,
                    fallbackLabel = systemLabel,
                )
                val englishLabel = loadLabelForLocale(
                    resolveInfo = resolveInfo,
                    locale = Locale.ENGLISH,
                    fallbackLabel = systemLabel,
                )
                val displayLabel = selectDisplayLabel(
                    chineseLabel = chineseLabel,
                    fallbackLabel = systemLabel,
                    packageName = activityInfo.packageName,
                )

                AppEntry(
                    label = displayLabel,
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    englishLabel = LabelFormatter.fallbackLabel(
                        label = englishLabel,
                        packageName = activityInfo.packageName,
                    ),
                )
            }
            .sortedWith { left, right ->
                labelCollator.compare(
                    LabelFormatter.sortKey(left.label),
                    LabelFormatter.sortKey(right.label),
                )
            }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    /**
     * 在不切换整个进程语言环境的前提下，尝试读取某个指定语言下的应用标签。
     */
    private fun loadLabelForLocale(
        resolveInfo: ResolveInfo,
        locale: Locale,
        fallbackLabel: String,
    ): String {
        val activityInfo = resolveInfo.activityInfo ?: return fallbackLabel
        return runCatching {
            val packageContext = context.createPackageContext(
                activityInfo.packageName,
                Context.CONTEXT_IGNORE_SECURITY,
            )
            val localizedConfig = Configuration(packageContext.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedResources = packageContext.createConfigurationContext(localizedConfig).resources
            val rawLocalizedLabel = when {
                activityInfo.nonLocalizedLabel != null -> activityInfo.nonLocalizedLabel.toString()
                activityInfo.labelRes != 0 -> localizedResources.getString(activityInfo.labelRes)
                activityInfo.applicationInfo.nonLocalizedLabel != null -> {
                    activityInfo.applicationInfo.nonLocalizedLabel.toString()
                }

                activityInfo.applicationInfo.labelRes != 0 -> {
                    localizedResources.getString(activityInfo.applicationInfo.labelRes)
                }

                else -> resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
            }
            rawLocalizedLabel
        }.getOrElse { fallbackLabel }
    }

    /**
     * 优先选择更适合中文设备显示的标签，否则回退到系统标签或包名派生值。
     */
    private fun selectDisplayLabel(
        chineseLabel: String,
        fallbackLabel: String,
        packageName: String,
    ): String {
        val normalizedChinese = LabelFormatter.fallbackLabel(
            label = chineseLabel,
            packageName = packageName,
        )
        if (normalizedChinese.any { isCjk(it) }) {
            return normalizedChinese
        }
        return LabelFormatter.fallbackLabel(
            label = fallbackLabel,
            packageName = packageName,
        )
    }

    private fun isCjk(char: Char): Boolean {
        return Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN
    }
}
