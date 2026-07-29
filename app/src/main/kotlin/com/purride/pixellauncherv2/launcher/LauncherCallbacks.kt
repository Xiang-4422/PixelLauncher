package com.purride.pixellauncherv2.launcher

/**
 * 聚合所有 Launcher 屏幕回调的数据类。
 *
 * Phase 8 — LauncherRootHost 使用此类替代原来分散在五个 Host 中的 Callbacks 类型。
 */
data class LauncherCallbacks(
    // ── HOME ──────────────────────────────────────────────────────────────────
    /** 用户点击 CALL 按钮 → 打开通话记录 */
    val onOpenCall: () -> Unit,
    /** 用户点击 SMS 按钮 → 进入短信模块 */
    val onOpenSms: () -> Unit,
    /** 用户点击 HOME 信息行。 */
    val onHomeInfoAction: (HomeInfoAction) -> Unit,
    /** 用户长按 HOME 信息行。 */
    val onHomeInfoDetail: (HomeInfoAction) -> Unit,
    /** 用户点击状态栏歌曲名或底栏中间区域 → 打开当前播放器。 */
    val onMediaOpenPlayer: () -> Unit,
    /** 用户双击状态栏歌曲名 → 切换喜欢状态。 */
    val onMediaToggleFavorite: () -> Unit,
    val onMediaTogglePlayPause: () -> Unit,
    val onMediaSkipPrevious: () -> Unit,
    val onMediaSkipNext: () -> Unit,
    val onMediaSeek: (Float) -> Unit,
    val onHomeNotificationPressed: (String) -> Unit,
    val onHomeNotificationAction: (String, Int) -> Unit,

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
    val onOpenThread: (conversationKey: String) -> Unit,
    /** 用户点按搜索页的"新建会话"入口 → 对该号码发起新会话。 */
    val onComposeNewThread: (address: String) -> Unit,
    val onSmsPageSelected: (Int) -> Unit,
    val onMarkSmsRead: () -> Unit,
    val onMarkUnreadMessageRead: (Long) -> Unit,
    val onDraftChanged: (String) -> Unit,
    val onSmsThreadSearchChanged: (String) -> Unit,
    val onSendDraft: () -> Unit,
    val onSmsMessagePressed: (Long) -> Unit,
    /** 详情页消息长按 → 打开浮层操作菜单。 */
    val onSmsMessageLongPressed: (Long) -> Unit,
    val onSmsMessageMenuCopy: () -> Unit,
    val onSmsMessageMenuCopyCode: () -> Unit,
    val onSmsMessageMenuResend: () -> Unit,
    val onSmsMessageMenuDelete: () -> Unit,
    val onSmsMessageMenuDismiss: () -> Unit,
    /** 会话列表行长按 → 打开会话浮层操作菜单。 */
    val onSmsThreadLongPressed: (conversationKey: String) -> Unit,
    val onSmsThreadMenuMarkRead: () -> Unit,
    val onSmsThreadMenuToggleMute: () -> Unit,
    val onSmsThreadMenuDelete: () -> Unit,
    val onSmsThreadMenuDismiss: () -> Unit,

    // ── CALL ──────────────────────────────────────────────────────────────────
    /** 用户点按通话记录行 → 回电该号码。 */
    val onCallGroupPressed: (number: String) -> Unit,
    /** 拨号模块翻页（最近通话 / 拨号盘）。 */
    val onCallPageSelected: (Int) -> Unit,
    /** 拨号盘按键。 */
    val onDialDigit: (Char) -> Unit,
    val onDialBackspace: () -> Unit,
    val onDialClear: () -> Unit,
    val onDialCall: () -> Unit,
    /** 点按 T9 匹配槽 → 直接拨该联系人。 */
    val onDialMatchPressed: (number: String) -> Unit,

    // ── Navigation ────────────────────────────────────────────────────────────
    /**
     * 主页面 Pager 翻页回调（HOME=0 / APP_DRAWER=1 / SETTINGS=2）。
     * 仅在用户手势驱动翻页时触发；外部调用 jumpToPage 不触发此回调。
     */
    val onMainPageChanged: (LauncherMode) -> Unit,
    /** 主页面 Pager 手势开始时触发，用于立即退出会被滑动打断的输入焦点。 */
    val onMainPageDragStart: () -> Unit,
)
