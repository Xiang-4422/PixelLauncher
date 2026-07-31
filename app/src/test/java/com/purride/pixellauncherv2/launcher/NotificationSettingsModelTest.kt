package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSettingsModelTest {

    @Test
    fun rowsMergeDrawerAppsActiveSourcesAndConfiguredWhitelistSources() {
        val rows = NotificationSettingsModel.rows(
            apps = listOf(
                app(label = "BANK", packageName = "com.bank"),
                app(label = "CHAT", packageName = "com.chat"),
            ),
            sources = listOf(
                NotificationSourceInfo(sourceId = "com.bank", sourceLabel = "BANK"),
                NotificationSourceInfo(sourceId = "com.service", sourceLabel = "SERVICE"),
            ),
            allowedSourceIds = setOf("com.bank", "com.noisy"),
        )

        assertEquals(
            listOf(
                NotificationSettingsRow("com.bank", "BANK", true),
                NotificationSettingsRow("com.noisy", "NOISY", true),
                NotificationSettingsRow("com.chat", "CHAT", false),
                NotificationSettingsRow("com.service", "SERVICE", false),
            ),
            rows,
        )
    }

    @Test
    fun rowsMarkSourcesOutsideWhitelistAsBlocked() {
        val rows = NotificationSettingsModel.rows(
            apps = emptyList(),
            sources = listOf(NotificationSourceInfo(sourceId = "com.bank", sourceLabel = "BANK")),
            allowedSourceIds = emptySet(),
        )

        assertEquals(false, rows.single().isAllowed)
    }

    @Test
    fun rowsExposeDrawerAppsBeforeTheyPostNotificationsAndKeepAllowedAppsFirst() {
        val rows = NotificationSettingsModel.rows(
            apps = listOf(
                app(label = "ALPHA", packageName = "com.alpha"),
                app(label = "ZULU", packageName = "com.zulu"),
            ),
            sources = emptyList(),
            allowedSourceIds = setOf("com.zulu"),
        )

        assertEquals(
            listOf(
                NotificationSettingsRow("com.zulu", "ZULU", true),
                NotificationSettingsRow("com.alpha", "ALPHA", false),
            ),
            rows,
        )
    }

    @Test
    fun rowsReuseDrawerSearchAcrossPinyinAliasesAndPackageNames() {
        val apps = listOf(
            app(label = "微信", packageName = "com.tencent.mm", aliases = listOf("CHAT")),
            app(label = "支付宝", packageName = "com.eg.android.AlipayGphone"),
        )

        assertEquals(
            listOf("com.tencent.mm"),
            NotificationSettingsModel.rows(
                apps = apps,
                sources = emptyList(),
                allowedSourceIds = emptySet(),
                query = "weixin",
            ).map(NotificationSettingsRow::sourceId),
        )
        assertEquals(
            listOf("com.tencent.mm"),
            NotificationSettingsModel.rows(
                apps = apps,
                sources = emptyList(),
                allowedSourceIds = emptySet(),
                query = "chat",
            ).map(NotificationSettingsRow::sourceId),
        )
        assertEquals(
            listOf("com.eg.android.AlipayGphone"),
            NotificationSettingsModel.rows(
                apps = apps,
                sources = emptyList(),
                allowedSourceIds = emptySet(),
                query = "alipay",
            ).map(NotificationSettingsRow::sourceId),
        )
    }

    @Test
    fun summaryShowsConfiguredCountsOnly() {
        assertEquals("0 ON", NotificationSettingsModel.summary(emptySet()))
        assertEquals("2 ON", NotificationSettingsModel.summary(setOf("a", "b")))
    }

    /** 构造只包含白名单搜索所需字段的测试应用。 */
    private fun app(
        label: String,
        packageName: String,
        aliases: List<String> = emptyList(),
    ): AppEntry {
        return AppEntry(
            label = label,
            packageName = packageName,
            activityName = "$packageName.MainActivity",
            aliases = aliases,
        )
    }
}
