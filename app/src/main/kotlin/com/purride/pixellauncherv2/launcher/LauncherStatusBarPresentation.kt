package com.purride.pixellauncherv2.launcher

internal sealed class LauncherStatusBarPresentation {
    data object Search : LauncherStatusBarPresentation()

    data class Standard(
        val pageTitle: String,
        val showSmsReadAction: Boolean = false,
    ) : LauncherStatusBarPresentation()

    companion object {
        fun forMode(mode: LauncherMode): LauncherStatusBarPresentation = when (mode) {
            LauncherMode.APP_DRAWER -> Search
            else -> Standard(
                pageTitle = pageTitleFor(mode),
                showSmsReadAction = mode == LauncherMode.SMS_THREADS,
            )
        }

        fun pageTitleFor(mode: LauncherMode): String = when (mode) {
            LauncherMode.HOME -> "HOME"
            LauncherMode.SETTINGS -> "SETTINGS"
            LauncherMode.APP_DRAWER,
            LauncherMode.APP_MANAGEMENT,
            -> "APP"
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_THREAD_DETAIL,
            -> "SMS"
            LauncherMode.DATA_HEALTH -> "DATA"
            LauncherMode.NOTIFICATION_SETTINGS -> "NOTIFY"
            LauncherMode.LOADING_PREVIEW -> "LOAD"
            LauncherMode.DIAGNOSTICS -> "DIAG"
            LauncherMode.IDLE -> "IDLE"
        }

        fun smsDetailPageTitle(
            conversationTitle: String,
            address: String,
        ): String {
            val title = conversationTitle
                .trim()
                .ifBlank { address.trim() }
                .ifBlank { pageTitleFor(LauncherMode.SMS_THREAD_DETAIL) }
            val trimmed = title.take(SMS_DETAIL_PAGE_TITLE_MAX)
            return if (trimmed.length < title.length) "$trimmed…" else trimmed
        }

        private const val SMS_DETAIL_PAGE_TITLE_MAX = 12
    }
}
