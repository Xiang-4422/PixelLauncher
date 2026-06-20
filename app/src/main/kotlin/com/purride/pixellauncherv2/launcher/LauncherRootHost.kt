package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.widget.FrameLayout
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelShape as EnginePixelShape
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.showItem
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.ui.screen.AppManagementScreen
import com.purride.pixellauncherv2.ui.screen.DiagnosticsScreen
import com.purride.pixellauncherv2.ui.screen.DataHealthScreen
import com.purride.pixellauncherv2.ui.screen.DrawerScreen
import com.purride.pixellauncherv2.ui.screen.HomeScreen
import com.purride.pixellauncherv2.ui.screen.IdleScreen
import com.purride.pixellauncherv2.ui.screen.NotificationSettingsScreen
import com.purride.pixellauncherv2.ui.screen.SmsInboxScreen
import com.purride.pixellauncherv2.ui.screen.SmsRolePromptScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadDetailScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadsScreen
import com.purride.pixellauncherv2.ui.text.LauncherTextRasterizers
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.ui.widget.LauncherSearchHeader
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * Phase 8：统一 Launcher 宿主，替换原来 5 个独立 PixelEngineXxxHost。
 *
 * 架构：
 * - HOME / APP_DRAWER / SETTINGS → 3 页横向 [PageView]（主页面 Pager）
 * - SMS_ROLE_PROMPT / SMS_THREADS / SMS_INBOX / SMS_THREAD_DETAIL → 全屏 SMS 内容
 * - DIAGNOSTICS / IDLE → 全屏杂项内容
 *
 * 所有控制器/状态均由本类持有，[update] 负责外部状态同步。
 */
internal class LauncherRootHost(
    context: Context,
    private val callbacks: LauncherCallbacks,
) {
    // ── Mutable model fields ──────────────────────────────────────────────────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.fallbackFrom(PixelTheme.DAY)
    private var chargeTick: Int = 0
    private var screenProfile: ScreenProfile = ScreenProfile(logicalWidth = 1, logicalHeight = 1, dotSizePx = 1)
    private val textRasterizers = LauncherTextRasterizers(context)

    // ── Main pager: HOME=0, APP_DRAWER=1, SETTINGS=2 ─────────────────────────
    private val mainPagerController = PageController()
    private val mainPagerState = mainPagerController.create(pageCount = MAIN_PAGE_COUNT)

    // ── APP_DRAWER controllers ────────────────────────────────────────────────
    private val drawerTextController = TextEditingController()
    private val drawerQueryState = drawerTextController.create()
    private val drawerListController = ScrollController()
    private val drawerListState = drawerListController.create()

    // ── SMS_THREADS list ──────────────────────────────────────────────────────
    private val threadListController = ScrollController()
    private val threadListState = threadListController.create()

    // ── SMS_INBOX pager + per-page scroll ─────────────────────────────────────
    private val inboxPagerController = PageController()
    private val inboxPagerState = inboxPagerController.create(pageCount = 1)
    private val inboxScrollController = ScrollController()
    private val inboxScrollStates = mutableListOf<PixelListState>()

    // ── SMS_THREAD_DETAIL message list + draft ────────────────────────────────
    private val msgListController = ScrollController()
    private val msgListState = msgListController.create()
    private val smsSearchController = TextEditingController()
    private val smsSearchState = smsSearchController.create()
    private val draftController = TextEditingController()
    private val draftState = draftController.create()

    // ── APP_MANAGEMENT editor fields ────────────────────────────────────────
    private val appNameController = TextEditingController()
    private val appNameState = appNameController.create()
    private val appAliasController = TextEditingController()
    private val appAliasState = appAliasController.create()

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            textRasterizer = textRasterizers.getRasterizer(
                PixelFontCatalog.defaultUiFontSize,
            ),
            content = { buildRoot() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    /**
     * 每帧调用。更新内部状态并触发重绘。
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: ScreenProfile,
        chargeTick: Int,
        pixelGapEnabled: Boolean = state.isPixelGapEnabled,
    ) {
        uiState = state
        this.theme = theme
        this.chargeTick = chargeTick
        this.screenProfile = screenProfile

        setup.hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape.toEngineShape(),
        )
        setup.hostView.setPixelGapEnabled(pixelGapEnabled)
        setup.hostView.setPixelGapRatio(if (pixelGapEnabled) 1f else 0f)
        setup.hostView.backgroundColor = theme.surface.appBackground
        setup.hostView.pixelGridColor  = theme.surface.pixelGrid
        setup.hostView.textRasterizer = textRasterizers.getRasterizer(
            PixelFontCatalog.defaultUiFontSize,
        )

        // ── Sync main pager ───────────────────────────────────────────────────
        val targetMainPage = modeToMainPage(state.mode)
        if (targetMainPage != null && mainPagerState.currentPage != targetMainPage) {
            mainPagerController.jumpToPage(mainPagerState, targetMainPage)
        }

        // ── Sync drawer ───────────────────────────────────────────────────────
        syncDrawerQueryState()

        // ── Sync SMS inbox pager page count ───────────────────────────────────
        val needed = state.unreadSmsEntries.size.coerceAtLeast(1)
        while (inboxScrollStates.size < needed) {
            inboxScrollStates.add(inboxScrollController.create())
        }
        if (inboxPagerState.pageCount != needed) {
            inboxPagerController.sync(
                state = inboxPagerState,
                axis = PixelAxis.HORIZONTAL,
                pageCount = needed,
            )
        }
        if (state.mode == LauncherMode.SMS_INBOX) {
            val targetInboxPage = state.smsSelectedIndex.coerceIn(
                0, (state.unreadSmsEntries.size - 1).coerceAtLeast(0),
            )
            if (inboxPagerState.currentPage != targetInboxPage) {
                inboxPagerController.jumpToPage(inboxPagerState, targetInboxPage)
            }
        }

        // ── Sync SMS thread list scroll ───────────────────────────────────────
        if (state.mode == LauncherMode.SMS_THREADS) {
            val target = state.smsThreadSelectedIndex.coerceIn(
                0, (state.smsThreads.size - 1).coerceAtLeast(0),
            )
            threadListController.showItem(threadListState, target)
        }

        // ── Sync SMS message list to bottom ───────────────────────────────────
        if (state.mode == LauncherMode.SMS_THREAD_DETAIL && state.smsMessages.isNotEmpty()) {
            msgListController.jumpToEnd(msgListState)
        }

        // ── Sync draft text ───────────────────────────────────────────────────
        syncDraftState()

        // ── Sync app editor fields ────────────────────────────────────────────
        syncAppEditorState()

        setup.hostView.invalidate()
    }

    // ── Content dispatching ───────────────────────────────────────────────────

    private fun buildRoot(): Widget = when (uiState.mode) {
        LauncherMode.HOME,
        LauncherMode.APP_DRAWER,
        LauncherMode.SETTINGS          -> buildMainPager()
        LauncherMode.SMS_ROLE_PROMPT   -> SmsRolePromptScreen(
            theme = theme,
            onRequestRole = callbacks.onRequestSmsRole,
        )
        LauncherMode.SMS_THREADS       -> SmsThreadsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
            listState = threadListState,
            listController = threadListController,
            onOpenThread = callbacks.onOpenThread,
        )
        LauncherMode.SMS_INBOX         -> SmsInboxScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
            pagerController = inboxPagerController,
            pagerState = inboxPagerState,
            scrollController = inboxScrollController,
            scrollStates = inboxScrollStates.toList().ifEmpty {
                listOf(inboxScrollController.create())
            },
            onSelectSmsIndex = callbacks.onSelectSmsIndex,
            onOpenThread = callbacks.onOpenThread,
        )
        LauncherMode.SMS_THREAD_DETAIL -> SmsThreadDetailScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
            msgListState = msgListState,
            msgListController = msgListController,
            searchController = smsSearchController,
            searchState = smsSearchState,
            draftController = draftController,
            draftState = draftState,
            onSearchChanged = callbacks.onSmsThreadSearchChanged,
            onDraftChanged = callbacks.onDraftChanged,
            onSendDraft = callbacks.onSendDraft,
            onMessagePressed = callbacks.onSmsMessagePressed,
        )
        LauncherMode.DIAGNOSTICS       -> DiagnosticsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            screenProfile = screenProfile,
            onOpenDataHealth = callbacks.onOpenDataHealth,
        )
        LauncherMode.DATA_HEALTH       -> DataHealthScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            screenProfile = screenProfile,
            onItemPressed = callbacks.onDataHealthItemPressed,
        )
        LauncherMode.NOTIFICATION_SETTINGS -> NotificationSettingsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            screenProfile = screenProfile,
            onSourcePressed = callbacks.onNotificationSourcePressed,
        )
        LauncherMode.APP_MANAGEMENT    -> AppManagementScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            screenProfile = screenProfile,
            nameController = appNameController,
            nameState = appNameState,
            aliasController = appAliasController,
            aliasState = appAliasState,
            onPrevious = callbacks.onAppEditorPrevious,
            onNext = callbacks.onAppEditorNext,
            onNameChanged = callbacks.onAppEditorNameChanged,
            onAliasChanged = callbacks.onAppEditorAliasChanged,
            onSave = callbacks.onAppEditorSave,
            onReset = callbacks.onAppEditorReset,
            onCacheReset = callbacks.onAppCacheReset,
        )
        LauncherMode.IDLE              -> IdleScreen(
            uiState = uiState,
            theme = theme,
            statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
        )
    }

    // ── Main pager ────────────────────────────────────────────────────────────

    private fun buildMainPager(): Widget = Column(
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        spacing = 0,
        children = listOf(
            buildSharedStatusBar(),
            Expanded(
                child = PageView(
                    axis = Axis.HORIZONTAL,
                    controller = mainPagerController,
                    state = mainPagerState,
                    pages = listOf(
                        buildHomePage(),
                        buildDrawerPage(),
                        buildSettingsPage(),
                    ),
                    onPageChanged = { page ->
                        MAIN_PAGE_MODES.getOrNull(page)?.let { mode ->
                            callbacks.onMainPageChanged(mode)
                        }
                    },
                ),
            ),
        ),
    )

    private fun buildHomePage(): Widget = HomeScreen(
        uiState = uiState,
        theme = theme,
        onOpenContacts = callbacks.onOpenContacts,
        onOpenSms = callbacks.onOpenSms,
        onInfoAction = callbacks.onHomeInfoAction,
        onInfoDetail = callbacks.onHomeInfoDetail,
    )

    private fun buildSettingsPage(): Widget = com.purride.pixellauncherv2.ui.screen.SettingsScreen(
        uiState = uiState,
        theme = theme,
        onItemAction = callbacks.onSettingsItemAction,
    )

    private fun buildSharedStatusBar(): Widget =
        if (uiState.mode == LauncherMode.APP_DRAWER) {
            LauncherSearchHeader(
                state = drawerQueryState,
                controller = drawerTextController,
                placeholder = "SEARCH APP",
                messageText = uiState.statusBarMessageText,
                autofocus = uiState.isDrawerSearchFocused,
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                chargeTick = chargeTick,
                theme = theme,
                statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
                onChanged = callbacks.onDrawerQueryChanged,
                onSubmitted = callbacks.onDrawerSubmitSearch,
            )
        } else {
            LauncherHeader(
                timeText = uiState.currentTimeText.ifEmpty { "--:--" },
                screenTitle = when (uiState.mode) {
                    LauncherMode.SETTINGS -> "SETTINGS"
                    else -> "HOME"
                },
                messageText = uiState.statusBarMessageText,
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                chargeTick = chargeTick,
                theme = theme,
                statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
            )
        }

    // ── APP_DRAWER content ────────────────────────────────────────────────────

    private fun buildDrawerPage(): Widget = DrawerScreen(
        uiState = uiState,
        theme = theme,
        listState = drawerListState,
        listController = drawerListController,
        onAppPressed = callbacks.onDrawerAppPressed,
        onAppLongPressed = callbacks.onDrawerAppLongPressed,
        onAppMenuEdit = callbacks.onDrawerAppMenuEdit,
        onAppMenuRefresh = callbacks.onDrawerAppMenuRefresh,
        onAppMenuDismiss = callbacks.onDrawerAppMenuDismiss,
    )

    // ── Sync helpers ──────────────────────────────────────────────────────────

    private fun syncDrawerQueryState() {
        if (drawerQueryState.text != uiState.drawerQuery) {
            drawerTextController.updateText(
                state = drawerQueryState,
                text = uiState.drawerQuery,
                selectionStart = uiState.drawerQuery.length,
            )
        }
        if (uiState.mode == LauncherMode.APP_DRAWER && uiState.isDrawerSearchFocused) {
            drawerTextController.requestFocus(drawerQueryState)
        } else if (drawerQueryState.isFocused) {
            drawerTextController.requestBlur(drawerQueryState)
        }
    }

    private fun syncDraftState() {
        val text = uiState.smsDraftText
        if (draftState.text != text) {
            draftController.updateText(
                state = draftState,
                text = text,
                selectionStart = text.length,
            )
        }
    }

    private fun syncAppEditorState() {
        if (appNameState.text != uiState.appEditorNameDraft) {
            appNameController.updateText(
                state = appNameState,
                text = uiState.appEditorNameDraft,
                selectionStart = uiState.appEditorNameDraft.length,
            )
        }
        if (appAliasState.text != uiState.appEditorAliasDraft) {
            appAliasController.updateText(
                state = appAliasState,
                text = uiState.appEditorAliasDraft,
                selectionStart = uiState.appEditorAliasDraft.length,
            )
        }
    }

    /** 计算当前应显示的 APP 列表（等同于 MainActivity.currentDrawerApps()）。 */
    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val MAIN_PAGE_COUNT = 3
        val MAIN_PAGE_MODES = listOf(
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
        )
        fun modeToMainPage(mode: LauncherMode): Int? = MAIN_PAGE_MODES.indexOf(mode).takeIf { it >= 0 }

        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}
