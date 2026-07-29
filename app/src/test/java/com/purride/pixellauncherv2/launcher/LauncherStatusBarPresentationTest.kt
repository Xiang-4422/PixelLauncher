package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherStatusBarPresentationTest {

    @Test
    fun appDrawerUsesSearchPresentation() {
        assertEquals(
            LauncherStatusBarPresentation.Search,
            LauncherStatusBarPresentation.forMode(LauncherMode.APP_DRAWER),
        )
    }

    @Test
    fun pageTitlesMatchGlobalStatusBarLabels() {
        val expectedTitles = mapOf(
            LauncherMode.HOME to "HOME",
            LauncherMode.SETTINGS to "SETTINGS",
            // Drawer 在状态栏走搜索态，标题实际用不到，但 pageTitleFor 仍有定义。
            LauncherMode.APP_DRAWER to "APP",
            LauncherMode.SMS_ROLE_PROMPT to "SMS",
            LauncherMode.SMS_THREADS to "SMS",
            LauncherMode.SMS_THREAD_DETAIL to "SMS",
            LauncherMode.DIALER to "CALL",
            LauncherMode.CONTACT_DETAIL to "CONTACT",
            LauncherMode.CONTACT_EDITOR to "EDIT",
            LauncherMode.APP_MANAGEMENT to "APP",
            LauncherMode.DATA_HEALTH to "DATA",
            LauncherMode.NOTIFICATION_SETTINGS to "NOTIFY",
            LauncherMode.LOADING_PREVIEW to "LOAD",
            LauncherMode.DIAGNOSTICS to "DIAG",
            LauncherMode.IDLE to "IDLE",
        )

        // 遍历枚举而不是遍历 map：新增模式忘了补标题时这里必须变红，
        // 否则漏项是一块无人看守的退化面。
        LauncherMode.entries.forEach { mode ->
            val expected = expectedTitles[mode]
                ?: error("LauncherMode.$mode has no expected status bar title in this test.")
            assertEquals(expected, LauncherStatusBarPresentation.pageTitleFor(mode))
        }
    }

    @Test
    fun smsReadActionOnlyAppearsOnSmsListPresentation() {
        val smsThreads = LauncherStatusBarPresentation.forMode(LauncherMode.SMS_THREADS)
        val smsDetail = LauncherStatusBarPresentation.forMode(LauncherMode.SMS_THREAD_DETAIL)
        val home = LauncherStatusBarPresentation.forMode(LauncherMode.HOME)

        assertTrue((smsThreads as LauncherStatusBarPresentation.Standard).showSmsReadAction)
        assertFalse((smsDetail as LauncherStatusBarPresentation.Standard).showSmsReadAction)
        assertFalse((home as LauncherStatusBarPresentation.Standard).showSmsReadAction)
    }

    @Test
    fun smsDetailTitleUsesConversationIdentityWithSmsFallback() {
        assertEquals(
            "China Mobile",
            LauncherStatusBarPresentation.smsDetailPageTitle(
                conversationTitle = "China Mobile",
                address = "+15551234567",
            ),
        )
        assertEquals(
            "+15551234567",
            LauncherStatusBarPresentation.smsDetailPageTitle(
                conversationTitle = "",
                address = "+15551234567",
            ),
        )
        assertEquals(
            "VERY LONG CO…",
            LauncherStatusBarPresentation.smsDetailPageTitle(
                conversationTitle = "VERY LONG CONTACT NAME",
                address = "",
            ),
        )
        assertEquals(
            "SMS",
            LauncherStatusBarPresentation.smsDetailPageTitle(
                conversationTitle = "",
                address = "",
            ),
        )
    }
}
