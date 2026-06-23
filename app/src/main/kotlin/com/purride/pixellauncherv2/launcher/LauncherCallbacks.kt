package com.purride.pixellauncherv2.launcher

/**
 * 聚合所有 Launcher 屏幕回调的数据类。
 *
 * Phase 8 — LauncherRootHost 使用此类替代原来分散在五个 Host 中的 Callbacks 类型。
 */
data class LauncherCallbacks(
    // ── HOME ──────────────────────────────────────────────────────────────────
    /** 用户点击 CONTACT 按钮 → 打开通讯录 */
    val onOpenContacts: () -> Unit,
    /** 用户点击 SMS 按钮 → 进入短信模块 */
    val onOpenSms: () -> Unit,
    /** 用户点击 HOME 信息行。 */
    val onHomeInfoAction: (HomeInfoAction) -> Unit,
    /** 用户长按 HOME 信息行。 */
    val onHomeInfoDetail: (HomeInfoAction) -> Unit,

    // ── APP_DRAWER ────────────────────────────────────────────────────────────
    val onDrawerQueryChanged: (String) -> Unit,
    val onDrawerSubmitSearch: () -> Unit,
    val onDrawerAppPressed: (Int) -> Unit,
    val onDrawerAppLongPressed: (Int) -> Unit,
    val onDrawerAppMenuEdit: () -> Unit,
    val onDrawerAppMenuRefresh: () -> Unit,
    val onDrawerAppMenuDismiss: () -> Unit,

    // ── SETTINGS ──────────────────────────────────────────────────────────────
    val onSettingsItemAction: (SettingsMenuItem, Int) -> Unit,
    val onPixelSizePresetSelected: (Int) -> Unit,
    val onStatusBarAction: () -> Unit,
    val onAppEditorPrevious: () -> Unit,
    val onAppEditorNext: () -> Unit,
    val onAppEditorNameChanged: (String) -> Unit,
    val onAppEditorAliasChanged: (String) -> Unit,
    val onAppEditorSave: () -> Unit,
    val onAppEditorReset: () -> Unit,
    val onAppCacheReset: () -> Unit,
    val onOpenDataHealth: () -> Unit,
    val onDataHealthItemPressed: (DataHealthItem) -> Unit,
    val onNotificationSourcePressed: (String) -> Unit,

    // ── SMS ───────────────────────────────────────────────────────────────────
    val onRequestSmsRole: () -> Unit,
    val onOpenThread: (threadId: Long, address: String) -> Unit,
    val onSmsPageSelected: (Int) -> Unit,
    val onSelectSmsIndex: (Int) -> Unit,
    val onDraftChanged: (String) -> Unit,
    val onSmsThreadSearchChanged: (String) -> Unit,
    val onSendDraft: () -> Unit,
    val onSmsMessagePressed: (Long) -> Unit,

    // ── Navigation ────────────────────────────────────────────────────────────
    /**
     * 主页面 Pager 翻页回调（HOME=0 / APP_DRAWER=1 / SETTINGS=2）。
     * 仅在用户手势驱动翻页时触发；外部调用 jumpToPage 不触发此回调。
     */
    val onMainPageChanged: (LauncherMode) -> Unit,
)
