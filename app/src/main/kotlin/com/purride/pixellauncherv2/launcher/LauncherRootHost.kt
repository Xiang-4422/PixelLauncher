package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelShape as EnginePixelShape
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorState
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.ScrollController
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.showItem
import com.purride.pixellauncherv2.ui.screen.AiSettingsScreen
import com.purride.pixellauncherv2.ui.screen.AppManagementScreen
import com.purride.pixellauncherv2.ui.screen.DiagnosticsScreen
import com.purride.pixellauncherv2.ui.screen.DataHealthScreen
import com.purride.pixellauncherv2.ui.screen.DrawerScreen
import com.purride.pixellauncherv2.ui.screen.HomeScreen
import com.purride.pixellauncherv2.ui.screen.IdleScreen
import com.purride.pixellauncherv2.ui.screen.LoadingPreviewScreen
import com.purride.pixellauncherv2.ui.screen.NotificationSettingsScreen
import com.purride.pixellauncherv2.ui.screen.SmsRolePromptScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadDetailScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadsScreen
import com.purride.pixellauncherv2.ui.text.LauncherTextRasterizers
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.ui.widget.LauncherSearchHeader
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import kotlin.time.Duration.Companion.milliseconds

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
    private val onPixelMatterEffectStart: () -> Unit = {},
    private val onPixelMatterRestoreStart: () -> Unit = {},
    private val onPixelMatterEffectClear: () -> Unit = {},
) {
    // ── Mutable model fields ──────────────────────────────────────────────────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.fallbackFrom(PixelTheme.DAY)
    private var chargeTick: Int = 0
    private var screenProfile: ScreenProfile = ScreenProfile(logicalWidth = 1, logicalHeight = 1, dotSizePx = 1)
    private val textRasterizers = LauncherTextRasterizers(context)
    private val frameScheduler = PixelFrameScheduler.Default
    private val routeTickerProvider = PixelTickerProvider(frameScheduler)
    private val hostView = PixelHostView(context)
    private val pixelMatterController = PixelMatterController(
        vsync = routeTickerProvider,
        onFrame = { hostView.postInvalidateOnAnimation() },
        onEffectStart = {
            cancelHostTouchState()
            onPixelMatterEffectStart()
        },
        onEffectClear = {
            cancelHostTouchState()
            onPixelMatterEffectClear()
        },
    )
    private var navigatorState: PixelNavigatorState? = null
    private var navigatorDestination: LauncherRouteDestination? = null

    // ── Main pager: HOME=0, APP_DRAWER=1, SETTINGS=2 ─────────────────────────
    private val mainPagerController = PageController()
    private val mainPagerState = mainPagerController.create(pageCount = MAIN_PAGE_COUNT)

    // ── APP_DRAWER controllers ────────────────────────────────────────────────
    private val drawerTextController = TextEditingController()
    private val drawerQueryState = drawerTextController.create()
    private val drawerListController = ScrollController()
    private val drawerListState = drawerListController.create()

    // ── SMS home pager + lists ────────────────────────────────────────────────
    private val smsPagerController = PageController()
    private val smsPagerState = smsPagerController.create(pageCount = SmsPageIndex.COUNT)
    private val unreadListController = ScrollController()
    private val unreadListState = unreadListController.create()
    private val threadListController = ScrollController()
    private val threadListState = threadListController.create()

    // ── SMS search + detail message list + draft ──────────────────────────────
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

    // ── AI settings field ───────────────────────────────────────────────────
    private val deepSeekApiKeyController = TextEditingController()
    private val deepSeekApiKeyState = deepSeekApiKeyController.create()

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        hostView = hostView,
        config = PixelHostSetupConfig(
            textRasterizer = textRasterizers.getRasterizer(
                PixelFontCatalog.defaultUiFontSize,
            ),
            frameScheduler = frameScheduler,
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
        val previousState = uiState
        val messagesWereAtEnd = !msgListState.isDragging &&
            !msgListState.isSettling &&
            msgListController.isAtEnd(msgListState)
        uiState = state
        this.theme = theme
        this.chargeTick = chargeTick
        this.screenProfile = screenProfile
        syncNavigatorRoute(state.mode)

        setup.hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape.toEngineShape(),
        )
        setup.hostView.setPixelGapEnabled(pixelGapEnabled)
        setup.hostView.setPixelGapRatio(if (pixelGapEnabled) 1f else 0f)
        setup.hostView.bezelColor = theme.surface.bezelColor
        setup.hostView.offPixelColor = theme.surface.offPixelColor
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

        // ── Sync SMS home pager ───────────────────────────────────────────────
        if (smsPagerState.pageCount != SmsPageIndex.COUNT) {
            smsPagerController.sync(
                state = smsPagerState,
                axis = PixelAxis.HORIZONTAL,
                pageCount = SmsPageIndex.COUNT,
            )
        }
        if (state.mode == LauncherMode.SMS_THREADS || state.mode == LauncherMode.SMS_INBOX) {
            val targetSmsPage = SmsPageIndex.coerce(state.smsPageIndex)
            if (smsPagerState.currentPage != targetSmsPage) {
                smsPagerController.jumpToPage(smsPagerState, targetSmsPage)
            }
        }

        // ── Sync SMS unread list scroll ───────────────────────────────────────
        if (SmsScrollSyncPolicy.shouldRevealSelectedUnread(previousState, state)) {
            val target = state.smsSelectedIndex.coerceIn(
                0, (state.unreadSmsEntries.size - 1).coerceAtLeast(0),
            )
            unreadListController.showItem(unreadListState, target)
        }

        // ── Sync SMS thread list scroll ───────────────────────────────────────
        if (SmsScrollSyncPolicy.shouldRevealSelectedThread(previousState, state)) {
            val target = state.smsThreadSelectedIndex.coerceIn(
                0, (state.smsThreads.size - 1).coerceAtLeast(0),
            )
            threadListController.showItem(threadListState, target)
        }

        // ── Sync SMS message list to bottom ───────────────────────────────────
        if (SmsScrollSyncPolicy.shouldFollowMessagesToEnd(
                previous = previousState,
                current = state,
                wasAtEnd = messagesWereAtEnd,
            )
        ) {
            msgListController.jumpToEnd(msgListState)
        }

        syncSmsSearchState()

        // ── Sync draft text ───────────────────────────────────────────────────
        syncDraftState()

        // ── Sync app editor fields ────────────────────────────────────────────
        syncAppEditorState()

        // ── Sync AI settings field ────────────────────────────────────────────
        syncAiSettingsState()

        setup.hostView.invalidate()
    }

    // ── Content dispatching ───────────────────────────────────────────────────

    private fun buildRoot(): Widget {
        pixelMatterController.simulation?.takeIf { pixelMatterController.isVisible() }?.let { simulation ->
            return PixelMatterEffectLayer(
                simulation = simulation,
                onTapToRestore = { requestPixelMatterRestore() },
                key = "pixel-matter-effect",
            )
        }
        val initialDestination = navigatorDestination
            ?: destinationFor(uiState.mode).also { navigatorDestination = it }
        return Column(
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            spacing = 0,
            children = listOf(
                buildGlobalStatusBar(),
                Expanded(
                    child = PixelNavigator(
                        initialRoute = routeFor(initialDestination),
                        vsync = routeTickerProvider,
                        transitionDuration = ROUTE_TRANSITION_DURATION_MS.milliseconds,
                        defaultTransition = PixelRouteTransition.SlideHorizontal,
                        key = "launcher-navigator",
                    ),
                ),
            ),
        )
    }

    fun updatePixelMatterMotion(snapshot: DeviceMotionSnapshot) {
        pixelMatterController.updateMotion(snapshot)
    }

    fun updatePixelMatterHandInput(snapshot: PixelMatterHandSnapshot?) {
        pixelMatterController.updateHandInput(snapshot)
    }

    fun isPixelMatterEffectActive(): Boolean = pixelMatterController.isActive()

    fun triggerPixelMatter(snapshot: DeviceMotionSnapshot): Boolean {
        if (pixelMatterController.isVisible()) {
            return pixelMatterController.applyShakeImpulse(snapshot)
        }
        val currentFrame = setup.hostView.snapshotCurrentFrameBuffer() ?: return false
        return pixelMatterController.start(
            mode = uiState.pixelMatterEffectMode,
            buffer = currentFrame,
            snapshot = snapshot,
            backgroundColor = setup.hostView.offPixelColor.argb,
            ignoredBackgroundColors = intArrayOf(
                setup.hostView.offPixelColor.argb,
                theme.surface.panel.argb,
                theme.surface.panelSubtle.argb,
                theme.surface.bezelColor.argb,
            ),
        )
    }

    fun handlePixelMatterBack(): Boolean {
        return when {
            pixelMatterController.isActive() -> requestPixelMatterRestore()
            pixelMatterController.isRestoring() -> {
                pixelMatterController.clear()
                true
            }
            else -> false
        }
    }

    fun stopPixelMatterEffect() {
        pixelMatterController.clear()
    }

    private fun requestPixelMatterRestore(): Boolean {
        val restored = pixelMatterController.requestRestore()
        if (restored) {
            onPixelMatterRestoreStart()
        }
        return restored
    }

    private fun cancelHostTouchState() {
        hostView.cancelPendingInputEvents()
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_CANCEL,
            0f,
            0f,
            0,
        )
        try {
            hostView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun routeFor(destination: LauncherRouteDestination): PixelRoute = PixelRoute(
        name = destination.routeName,
        transition = transitionFor(destination),
        builder = { context ->
            navigatorState = PixelNavigator.of(context)
            buildDestination(destination)
        },
    )

    private fun buildDestination(destination: LauncherRouteDestination): Widget = when (destination) {
        LauncherRouteDestination.MAIN -> buildMainPager()
        LauncherRouteDestination.SMS_ROLE_PROMPT -> SmsRolePromptScreen(
            theme = theme,
            onRequestRole = callbacks.onRequestSmsRole,
        )
        LauncherRouteDestination.SMS_THREADS -> SmsThreadsScreen(
            uiState = uiState,
            theme = theme,
            vsync = routeTickerProvider,
            pagerController = smsPagerController,
            pagerState = smsPagerState,
            unreadListState = unreadListState,
            unreadListController = unreadListController,
            listState = threadListState,
            listController = threadListController,
            searchController = smsSearchController,
            searchState = smsSearchState,
            onSmsPageSelected = callbacks.onSmsPageSelected,
            onSearchChanged = callbacks.onSmsThreadSearchChanged,
            onMarkUnreadMessageRead = callbacks.onMarkUnreadMessageRead,
            onOpenThread = callbacks.onOpenThread,
        )
        LauncherRouteDestination.SMS_THREAD_DETAIL -> SmsThreadDetailScreen(
            uiState = uiState,
            theme = theme,
            msgListState = msgListState,
            msgListController = msgListController,
            draftController = draftController,
            draftState = draftState,
            onDraftChanged = callbacks.onDraftChanged,
            onSendDraft = callbacks.onSendDraft,
            onMessagePressed = callbacks.onSmsMessagePressed,
        )
        LauncherRouteDestination.DIAGNOSTICS -> DiagnosticsScreen(
            uiState = uiState,
            theme = theme,
            screenProfile = screenProfile,
            onOpenDataHealth = callbacks.onOpenDataHealth,
        )
        LauncherRouteDestination.DATA_HEALTH -> DataHealthScreen(
            uiState = uiState,
            theme = theme,
            onItemPressed = callbacks.onDataHealthItemPressed,
        )
        LauncherRouteDestination.NOTIFICATION_SETTINGS -> NotificationSettingsScreen(
            uiState = uiState,
            theme = theme,
            onSourcePressed = callbacks.onNotificationSourcePressed,
        )
        LauncherRouteDestination.AI_SETTINGS -> AiSettingsScreen(
            uiState = uiState,
            theme = theme,
            apiKeyController = deepSeekApiKeyController,
            apiKeyState = deepSeekApiKeyState,
            onDeepSeekApiKeyChanged = callbacks.onDeepSeekApiKeyChanged,
        )
        LauncherRouteDestination.LOADING_PREVIEW -> LoadingPreviewScreen(
            theme = theme,
            screenProfile = screenProfile,
            vsync = routeTickerProvider,
        )
        LauncherRouteDestination.APP_MANAGEMENT -> AppManagementScreen(
            uiState = uiState,
            theme = theme,
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
        LauncherRouteDestination.IDLE -> IdleScreen(
            uiState = uiState,
            theme = theme,
        )
    }

    private fun syncNavigatorRoute(mode: LauncherMode) {
        val destination = destinationFor(mode)
        if (navigatorDestination == destination) return
        navigatorDestination = destination
        val navigator = navigatorState ?: return
        when (navigationAction(navigator.stack.map { route -> route.name }, destination)) {
            LauncherRouteNavigationAction.NONE -> Unit
            LauncherRouteNavigationAction.PUSH -> navigator.push(routeFor(destination))
            LauncherRouteNavigationAction.POP -> navigator.pop()
            LauncherRouteNavigationAction.POP_TO_ROOT -> navigator.popToRoot()
            LauncherRouteNavigationAction.REPLACE -> navigator.replace(routeFor(destination), animated = true)
        }
    }

    // ── Main pager ────────────────────────────────────────────────────────────

    private fun buildMainPager(): Widget = PageView(
        axis = Axis.HORIZONTAL,
        controller = mainPagerController,
        state = mainPagerState,
        pages = listOf(
            buildSettingsPage(),
            buildHomePage(),
            buildDrawerPage(),
        ),
        onPageChanged = { page ->
            MAIN_PAGE_MODES.getOrNull(page)?.let { mode ->
                callbacks.onMainPageChanged(mode)
            }
        },
        onPageDragStart = callbacks.onMainPageDragStart,
    )

    private fun buildHomePage(): Widget = HomeScreen(
        uiState = uiState,
        theme = theme,
        screenWidthPx = screenProfile.logicalWidth,
        vsync = routeTickerProvider,
        onOpenCall = callbacks.onOpenCall,
        onOpenSms = callbacks.onOpenSms,
        onInfoAction = callbacks.onHomeInfoAction,
        onInfoDetail = callbacks.onHomeInfoDetail,
        onMediaTogglePlayPause = callbacks.onMediaTogglePlayPause,
        onMediaSkipPrevious = callbacks.onMediaSkipPrevious,
        onMediaSkipNext = callbacks.onMediaSkipNext,
        onMediaSeek = callbacks.onMediaSeek,
        onNotificationPressed = callbacks.onHomeNotificationPressed,
        onNotificationAction = callbacks.onHomeNotificationAction,
    )

    private fun buildSettingsPage(): Widget = com.purride.pixellauncherv2.ui.screen.SettingsScreen(
        uiState = uiState,
        theme = theme,
        onItemAction = callbacks.onSettingsItemAction,
    )

    private fun buildGlobalStatusBar(): Widget =
        when (val presentation = LauncherStatusBarPresentation.forMode(uiState.mode)) {
            LauncherStatusBarPresentation.Search -> {
                LauncherSearchHeader(
                    state = drawerQueryState,
                    controller = drawerTextController,
                    placeholder = "SEARCH APP",
                    autofocus = uiState.isDrawerSearchFocused,
                    textAlign = when (uiState.drawerListAlignment) {
                        DrawerListAlignment.LEFT -> TextAlign.START
                        DrawerListAlignment.CENTER -> TextAlign.CENTER
                        DrawerListAlignment.RIGHT -> TextAlign.END
                    },
                    batteryLevel = uiState.batteryLevel,
                    isCharging = uiState.isCharging,
                    chargeTick = chargeTick,
                    theme = theme,
                    statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
                    onChanged = callbacks.onDrawerQueryChanged,
                    onSubmitted = callbacks.onDrawerSubmitSearch,
                )
            }
            is LauncherStatusBarPresentation.Standard -> {
                val mediaPlayback = uiState.mediaPlayback
                val showMediaTitle = uiState.mode == LauncherMode.HOME && mediaPlayback.hasTrack
                val showSmsReadAction = presentation.showSmsReadAction
                LauncherHeader(
                    timeText = uiState.currentTimeText.ifEmpty { "--:--" },
                    screenTitle = statusBarPageTitle(presentation),
                    messageText = uiState.statusBarMessageText,
                    actionLeadingText = uiState.statusBarActionLeadingText,
                    actionLabel = uiState.statusBarActionLabel,
                    isActionDanger = uiState.isStatusBarActionDanger,
                    centerActionLabel = if (showSmsReadAction) "READ" else "",
                    isCenterActionEnabled = uiState.unreadSmsEntries.isNotEmpty(),
                    centerText = if (showMediaTitle) mediaPlayback.title else "",
                    centerTextColor = if (showMediaTitle && mediaPlayback.isFavorite) {
                        theme.semantic.danger
                    } else {
                        null
                    },
                    batteryLevel = uiState.batteryLevel,
                    isCharging = uiState.isCharging,
                    chargeTick = chargeTick,
                    theme = theme,
                    statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile),
                    pageTagVsync = routeTickerProvider,
                    onAction = callbacks.onStatusBarAction,
                    onCenterAction = if (showSmsReadAction) callbacks.onMarkSmsRead else null,
                    onCenterTap = if (showMediaTitle) callbacks.onMediaOpenPlayer else null,
                    onCenterDoubleTap = if (showMediaTitle && mediaPlayback.canToggleFavorite) {
                        callbacks.onMediaToggleFavorite
                    } else {
                        null
                    },
                )
            }
        }

    private fun statusBarPageTitle(presentation: LauncherStatusBarPresentation.Standard): String {
        return if (uiState.mode == LauncherMode.SMS_THREAD_DETAIL) {
            LauncherStatusBarPresentation.smsDetailPageTitle(
                conversationTitle = uiState.smsCurrentConversationTitle,
                address = uiState.smsCurrentAddress,
            )
        } else {
            presentation.pageTitle
        }
    }

    // ── APP_DRAWER content ────────────────────────────────────────────────────

    private fun buildDrawerPage(): Widget = DrawerScreen(
        uiState = uiState,
        theme = theme,
        vsync = routeTickerProvider,
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
        val shouldFocus = uiState.mode == LauncherMode.APP_DRAWER && uiState.isDrawerSearchFocused
        if (shouldFocus) {
            drawerTextController.requestFocus(drawerQueryState)
        } else {
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
        if (uiState.mode != LauncherMode.SMS_THREAD_DETAIL || uiState.smsCurrentIsServiceConversation) {
            draftController.requestBlur(draftState)
        }
    }

    private fun syncSmsSearchState() {
        val text = uiState.smsThreadSearchQuery
        if (smsSearchState.text != text) {
            smsSearchController.updateText(
                state = smsSearchState,
                text = text,
                selectionStart = text.length,
            )
        }
        val searchIsVisible =
            (uiState.mode == LauncherMode.SMS_THREADS || uiState.mode == LauncherMode.SMS_INBOX) &&
                uiState.smsPageIndex == SmsPageIndex.ALL
        if (!searchIsVisible) {
            smsSearchController.requestBlur(smsSearchState)
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

    private fun syncAiSettingsState() {
        if (deepSeekApiKeyState.text != uiState.deepSeekApiKey) {
            deepSeekApiKeyController.updateText(
                state = deepSeekApiKeyState,
                text = uiState.deepSeekApiKey,
                selectionStart = uiState.deepSeekApiKey.length,
            )
        }
    }

    /** 计算当前应显示的 APP 列表（等同于 MainActivity.currentDrawerApps()）。 */
    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val MAIN_PAGE_COUNT = 3
        const val ROUTE_TRANSITION_DURATION_MS = 200
        val MAIN_PAGE_MODES = listOf(
            LauncherMode.SETTINGS,
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
        )
        fun modeToMainPage(mode: LauncherMode): Int? = MAIN_PAGE_MODES.indexOf(mode).takeIf { it >= 0 }

        internal fun destinationFor(mode: LauncherMode): LauncherRouteDestination = when (mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
            -> LauncherRouteDestination.MAIN
            LauncherMode.SMS_ROLE_PROMPT -> LauncherRouteDestination.SMS_ROLE_PROMPT
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_INBOX,
            -> LauncherRouteDestination.SMS_THREADS
            LauncherMode.SMS_THREAD_DETAIL -> LauncherRouteDestination.SMS_THREAD_DETAIL
            LauncherMode.APP_MANAGEMENT -> LauncherRouteDestination.APP_MANAGEMENT
            LauncherMode.DATA_HEALTH -> LauncherRouteDestination.DATA_HEALTH
            LauncherMode.NOTIFICATION_SETTINGS -> LauncherRouteDestination.NOTIFICATION_SETTINGS
            LauncherMode.AI_SETTINGS -> LauncherRouteDestination.AI_SETTINGS
            LauncherMode.LOADING_PREVIEW -> LauncherRouteDestination.LOADING_PREVIEW
            LauncherMode.DIAGNOSTICS -> LauncherRouteDestination.DIAGNOSTICS
            LauncherMode.IDLE -> LauncherRouteDestination.IDLE
        }

        internal fun transitionFor(destination: LauncherRouteDestination): PixelRouteTransition? = when (destination) {
            LauncherRouteDestination.SMS_ROLE_PROMPT,
            LauncherRouteDestination.SMS_THREADS,
            -> PixelRouteTransition.SlideVertical

            LauncherRouteDestination.MAIN,
            LauncherRouteDestination.SMS_THREAD_DETAIL,
            LauncherRouteDestination.APP_MANAGEMENT,
            LauncherRouteDestination.DATA_HEALTH,
            LauncherRouteDestination.NOTIFICATION_SETTINGS,
            LauncherRouteDestination.AI_SETTINGS,
            LauncherRouteDestination.LOADING_PREVIEW,
            LauncherRouteDestination.DIAGNOSTICS,
            LauncherRouteDestination.IDLE,
            -> null
        }

        internal fun navigationAction(
            currentRouteNames: List<String>,
            destination: LauncherRouteDestination,
        ): LauncherRouteNavigationAction {
            val target = destination.routeName
            if (currentRouteNames.lastOrNull() == target) {
                return LauncherRouteNavigationAction.NONE
            }
            if (destination == LauncherRouteDestination.MAIN && currentRouteNames.size > 1) {
                return LauncherRouteNavigationAction.POP_TO_ROOT
            }
            if (currentRouteNames.dropLast(1).lastOrNull() == target) {
                return LauncherRouteNavigationAction.POP
            }
            return if (currentRouteNames.isNotEmpty()) {
                LauncherRouteNavigationAction.PUSH
            } else {
                LauncherRouteNavigationAction.REPLACE
            }
        }

        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}

internal enum class LauncherRouteNavigationAction {
    NONE,
    PUSH,
    POP,
    POP_TO_ROOT,
    REPLACE,
}

internal enum class LauncherRouteDestination(
    val routeName: String,
) {
    MAIN("main"),
    SMS_ROLE_PROMPT("sms-role-prompt"),
    SMS_THREADS("sms-threads"),
    SMS_THREAD_DETAIL("sms-thread-detail"),
    APP_MANAGEMENT("app-management"),
    DATA_HEALTH("data-health"),
    NOTIFICATION_SETTINGS("notification-settings"),
    AI_SETTINGS("ai-settings"),
    LOADING_PREVIEW("loading-preview"),
    DIAGNOSTICS("diagnostics"),
    IDLE("idle"),
}
