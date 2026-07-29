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
            LauncherMode.DIALER -> "CALL"
            LauncherMode.CONTACT_DETAIL -> "CONTACT"
            LauncherMode.CONTACT_EDITOR -> "EDIT"
            LauncherMode.DATA_HEALTH -> "DATA"
            LauncherMode.NOTIFICATION_SETTINGS -> "NOTIFY"
            LauncherMode.LOADING_PREVIEW -> "LOAD"
            LauncherMode.DIAGNOSTICS -> "DIAG"
            LauncherMode.SNAKE -> "SNAKE"
            LauncherMode.IDLE -> "IDLE"
        }

        fun smsDetailPageTitle(
            conversationTitle: String,
            address: String,
        ): String = detailPageTitle(
            title = conversationTitle.trim().ifBlank { address.trim() },
            fallback = pageTitleFor(LauncherMode.SMS_THREAD_DETAIL),
        )

        /** 联系人详情标题：显示联系人名，缺失时回落到模式默认标题。 */
        fun contactDetailPageTitle(displayName: String): String = detailPageTitle(
            title = displayName.trim(),
            fallback = pageTitleFor(LauncherMode.CONTACT_DETAIL),
        )

        /** 详情页通用标题：清洗、回落、按状态栏预算截断。 */
        private fun detailPageTitle(title: String, fallback: String): String {
            val resolved = title.ifBlank { fallback }
            val trimmed = resolved.take(DETAIL_PAGE_TITLE_MAX)
            return if (trimmed.length < resolved.length) "$trimmed…" else trimmed
        }

        private const val DETAIL_PAGE_TITLE_MAX = 12
    }
}
