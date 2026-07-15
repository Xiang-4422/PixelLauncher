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
            LauncherMode.SMS_ROLE_PROMPT to "SMS",
            LauncherMode.SMS_THREADS to "SMS",
            LauncherMode.SMS_INBOX to "SMS",
            LauncherMode.SMS_THREAD_DETAIL to "SMS",
            LauncherMode.APP_MANAGEMENT to "APP",
            LauncherMode.DATA_HEALTH to "DATA",
            LauncherMode.NOTIFICATION_SETTINGS to "NOTIFY",
            LauncherMode.LOADING_PREVIEW to "LOAD",
            LauncherMode.DIAGNOSTICS to "DIAG",
            LauncherMode.IDLE to "IDLE",
        )

        expectedTitles.forEach { (mode, title) ->
            assertEquals(title, LauncherStatusBarPresentation.pageTitleFor(mode))
        }
    }

    @Test
    fun smsReadActionOnlyAppearsOnSmsListPresentation() {
        val smsThreads = LauncherStatusBarPresentation.forMode(LauncherMode.SMS_THREADS)
        val smsInbox = LauncherStatusBarPresentation.forMode(LauncherMode.SMS_INBOX)
        val smsDetail = LauncherStatusBarPresentation.forMode(LauncherMode.SMS_THREAD_DETAIL)
        val home = LauncherStatusBarPresentation.forMode(LauncherMode.HOME)

        assertTrue((smsThreads as LauncherStatusBarPresentation.Standard).showSmsReadAction)
        assertTrue((smsInbox as LauncherStatusBarPresentation.Standard).showSmsReadAction)
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
