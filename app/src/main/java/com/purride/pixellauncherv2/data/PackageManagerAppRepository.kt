package com.purride.pixellauncherv2.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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

                val rawLabel = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                val label = LabelFormatter.fallbackLabel(
                    label = rawLabel,
                    packageName = activityInfo.packageName,
                )

                AppEntry(
                    label = label,
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
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
}
