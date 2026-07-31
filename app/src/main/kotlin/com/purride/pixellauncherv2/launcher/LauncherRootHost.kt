package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import com.purride.pixelcore.PixelAxis
import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.PixelHostProfilePolicy
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorState
import com.purride.pixelui.PixelRouteDestination
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.ScrollController
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.pixelRouteDestination
import com.purride.pixelui.showItem
import com.purride.pixellauncherv2.ui.screen.AppManagementScreen
import com.purride.pixellauncherv2.ui.screen.ContactDetailScreen
import com.purride.pixellauncherv2.ui.screen.ContactEditorScreen
import com.purride.pixellauncherv2.ui.screen.DiagnosticsScreen
import com.purride.pixellauncherv2.ui.screen.DialerScreen
import com.purride.pixellauncherv2.ui.screen.DataHealthScreen
import com.purride.pixellauncherv2.ui.screen.DrawerScreen
import com.purride.pixellauncherv2.ui.screen.HomeScreen
import com.purride.pixellauncherv2.ui.screen.IdleScreen
import com.purride.pixellauncherv2.ui.screen.LoadingPreviewScreen
import com.purride.pixellauncherv2.ui.screen.MoreSettingsScreen
import com.purride.pixellauncherv2.ui.screen.NotificationSettingsScreen
import com.purride.pixellauncherv2.ui.screen.SmsRolePromptScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadDetailScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadsScreen
import com.purride.pixellauncherv2.ui.screen.SnakeScreen
import com.purride.pixellauncherv2.ui.text.PreparedLauncherFont
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.ui.widget.LauncherHeader
import com.purride.pixellauncherv2.ui.widget.LauncherSearchHeader
import com.purride.pixellauncherv2.ui.widget.SettingsTextEdgeResolvers
import com.purride.pixellauncherv2.model.DeviceMotionSnapshot
import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import kotlin.time.Duration.Companion.milliseconds

/**
 * Phase 8：统一 Launcher 宿主，替换原来 5 个独立 PixelEngineXxxHost。
 *
 * 架构：
 * - HOME / APP_DRAWER / SETTINGS → 3 页横向 [PageView]（主页面 Pager）
 * - SMS_ROLE_PROMPT / SMS_THREADS / SMS_THREAD_DETAIL → 全屏 SMS 内容
 * - DIAGNOSTICS / IDLE → 全屏杂项内容
 *
 * 所有控制器/状态均由本类持有，[update] 负责外部状态同步。
 */
internal class LauncherRootHost(
    context: Context,
    /** 冷启动前已完整准备、不会在 Host 内执行 IO 的字体。 */
    initialFont: PreparedLauncherFont,
    private val callbacks: LauncherCallbacks,
    private val onPixelMatterEffectStart: () -> Unit = {},
    private val onPixelMatterRestoreStart: () -> Unit = {},
    private val onPixelMatterEffectClear: () -> Unit = {},
) {
    // ── Mutable model fields ──────────────────────────────────────────────────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.resolve(
        family = LauncherThemeFamily.MIDNIGHT,
        brightness = LauncherThemeBrightness.DARK,
    ).copy(
        typography = initialFont.typography,
    )
    private var chargeTick: Int = 0
    private var screenProfile: LauncherLayoutProfile = LauncherLayoutProfile(logicalWidth = 1, logicalHeight = 1, dotSizePx = 1)
    /** 每帧与业务 selection 原子更新的完整字体。 */
    private var preparedFont: PreparedLauncherFont = initialFont
    /** Launcher 唯一的 Android Host View。 */
    private val hostView = PixelHostView(context)
    /** 与当前 Host 一一对应的新版 Engine 实例。 */
    private val engine = LauncherPixelEngineFactory.create(hostView = hostView)

    /** 已显式绑定 Engine、输入桥和根内容的标准 Android Host 装配。 */
    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        engine = engine,
        hostView = hostView,
        config = PixelHostSetupConfig(
            textRasterizer = initialFont.defaultRasterizer,
            content = { buildRoot() },
        ),
    )

    /** Engine 绑定完成后取得的 Host 私有 ticker provider。 */
    private val routeTickerProvider = setup.hostView.tickerProvider
    /** 管理像素物质特效生命周期与帧提交的控制器。 */
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
    /** 沙钟：待机页的时间由沙粒堆成，分钟变化时坍塌重组。 */
    private val sandClockController = SandClockController(
        vsync = routeTickerProvider,
        onFrame = { hostView.postInvalidateOnAnimation() },
    )
    /** 贪吃蛇：设置页入口的游戏彩蛋。 */
    private val snakeController = SnakeController(
        vsync = routeTickerProvider,
        onFrame = { hostView.postInvalidateOnAnimation() },
    )

    /** 当前 Widget 树中的 typed Navigator 状态。 */
    private var navigatorState: PixelNavigatorState? = null
    /** 当前业务状态映射到的目的地。 */
    private var navigatorDestination: LauncherRouteDestination? = null
    /** 标记 Host 是否已经执行终态释放，确保 dispose 恰好一次。 */
    private var isDisposed: Boolean = false

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

    // ── Dialer pager + call log list ──────────────────────────────────────────
    private val callPagerController = PageController()
    private val callPagerState = callPagerController.create(pageCount = CallPageIndex.COUNT)
    private val callLogListController = ScrollController()
    private val callLogListState = callLogListController.create()
    private val contactsListController = ScrollController()
    private val contactsListState = contactsListController.create()
    private val contactNameController = TextEditingController()
    private val contactNameState = contactNameController.create()
    private val contactNumberController = TextEditingController()
    private val contactNumberState = contactNumberController.create()

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

    /** 每个业务目的地对应的可复用 typed route 定义。 */
    private val routeDestinations: Map<LauncherRouteDestination, PixelRouteDestination<LauncherRouteArguments, Unit>> =
        LauncherRouteDestination.entries.associateWith { destination ->
            pixelRouteDestination<LauncherRouteArguments, Unit>(
                id = destination.routeName,
                maintainState = true,
                transition = transitionFor(destination),
            ) { context, scope ->
                require(scope.arguments.destination == destination) {
                    "Launcher route arguments do not match destination ${destination.routeName}."
                }
                navigatorState = PixelNavigator.of(context)
                buildDestination(destination)
            }
        }

    /** Activity 挂载的完整 Launcher 根容器。 */
    val rootView: FrameLayout
        get() = setup.rootView

    /** 通过 Engine 的聚焦 Host capability 执行语义化震动。 */
    fun performHapticFeedback(type: PixelHapticType): PixelCapabilityResult {
        return engine.services.hostServices.performHapticFeedback(type)
    }

    /** 终态释放特效、typed route entry、输入桥和 retained runtime。 */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        pixelMatterController.clear()
        sandClockController.dispose()
        snakeController.dispose()
        setup.dispose()
        navigatorState = null
    }

    /**
     * 每帧调用。更新内部状态并触发重绘。
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: LauncherLayoutProfile,
        chargeTick: Int,
        preparedFont: PreparedLauncherFont,
        pixelGapEnabled: Boolean = state.isPixelGapEnabled,
    ) {
        val previousState = uiState
        val messagesWereAtEnd = !msgListState.isDragging &&
            !msgListState.isSettling &&
            msgListController.isAtEnd(msgListState)
        uiState = state
        require(preparedFont.selection == state.fontSelection) { "Prepared font must match UI state" }
        this.preparedFont = preparedFont
        this.theme = theme.copy(typography = preparedFont.typography)
        this.chargeTick = chargeTick
        this.screenProfile = screenProfile
        syncNavigatorRoute(state.mode)

        // Launcher 的逻辑网格随点阵大小自适应，统一走 Host 唯一的 profile 策略入口。
        setup.hostView.profilePolicy = PixelHostProfilePolicy.AdaptivePixels(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape,
        )
        setup.hostView.setPixelGapEnabled(pixelGapEnabled)
        setup.hostView.setPixelGapRatio(if (pixelGapEnabled) 1f else 0f)
        setup.hostView.bezelColor = theme.surface.bezelColor
        setup.hostView.offPixelColor = theme.surface.offPixelColor
        setup.hostView.textRasterizer = preparedFont.defaultRasterizer

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
        if (state.mode == LauncherMode.DIALER) {
            val targetCallPage = CallPageIndex.coerce(state.callPageIndex)
            if (callPagerState.currentPage != targetCallPage) {
                callPagerController.jumpToPage(callPagerState, targetCallPage)
            }
        }
        if (state.mode == LauncherMode.SMS_THREADS) {
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
            // 搜索态 ALL 页渲染的是搜索结果（命中消息数常多于会话数），
            // 用会话数收敛会把定位目标钳在错误上限，列表提前停止滚动。
            val rowCount = if (state.smsThreadSearchQuery.isNotBlank()) {
                SmsThreadSearchModel.filter(state.smsAllMessages, state.smsThreadSearchQuery).size
            } else {
                state.smsThreads.size
            }
            val target = state.smsThreadSelectedIndex.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
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

        // ── Sync contact editor fields ────────────────────────────────────────
        syncContactEditorState()

        // ── Sand clock lifecycle ──────────────────────────────────────────────
        syncSandClock()

        // ── Snake lifecycle ───────────────────────────────────────────────────
        syncSnake()

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
                        initialRequest = routeRequestFor(initialDestination),
                        vsync = routeTickerProvider,
                        transitionDuration = ROUTE_TRANSITION_DURATION_MS.milliseconds,
                        defaultTransition = PixelRouteTransition.SlideHorizontal,
                        key = "launcher-navigator",
                    ),
                ),
            ),
        )
    }

    /** 硬件方向键操控贪吃蛇（次要通路，按钮是主通路）。 */
    fun turnSnake(direction: SnakeModel.Direction) {
        snakeController.turn(direction)
    }

    fun updatePixelMatterMotion(snapshot: DeviceMotionSnapshot) {
        pixelMatterController.updateMotion(snapshot)
        sandClockController.updateMotion(snapshot)
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

    /** 为一次具体导航操作创建独立 typed entry 请求。 */
    private fun routeRequestFor(
        destination: LauncherRouteDestination,
    ): PixelRouteRequest<LauncherRouteArguments, Unit> {
        /** 与当前目的地一一对应的可复用定义。 */
        val routeDestination = checkNotNull(routeDestinations[destination]) {
            "Missing typed route destination for ${destination.routeName}."
        }
        return PixelRouteRequest(
            destination = routeDestination,
            arguments = LauncherRouteArguments(destination = destination),
        )
    }

    private fun buildDestination(destination: LauncherRouteDestination): Widget = when (destination) {
        LauncherRouteDestination.MAIN -> buildMainPager()
        LauncherRouteDestination.MORE_SETTINGS -> MoreSettingsScreen(
            uiState = uiState,
            theme = theme,
            textEdgeResolvers = settingsTextEdgeResolvers(),
            onItemAction = callbacks.onSettingsItemAction,
        )
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
            onComposeNewThread = callbacks.onComposeNewThread,
            onThreadLongPressed = callbacks.onSmsThreadLongPressed,
            onThreadMenuMarkRead = callbacks.onSmsThreadMenuMarkRead,
            onThreadMenuToggleMute = callbacks.onSmsThreadMenuToggleMute,
            onThreadMenuDelete = callbacks.onSmsThreadMenuDelete,
            onThreadMenuDismiss = callbacks.onSmsThreadMenuDismiss,
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
            onMessageLongPressed = callbacks.onSmsMessageLongPressed,
            onMenuCopy = callbacks.onSmsMessageMenuCopy,
            onMenuCopyCode = callbacks.onSmsMessageMenuCopyCode,
            onMenuResend = callbacks.onSmsMessageMenuResend,
            onMenuDelete = callbacks.onSmsMessageMenuDelete,
            onMenuDismiss = callbacks.onSmsMessageMenuDismiss,
        )
        LauncherRouteDestination.DIALER -> DialerScreen(
            uiState = uiState,
            theme = theme,
            vsync = routeTickerProvider,
            pagerController = callPagerController,
            pagerState = callPagerState,
            listState = callLogListState,
            listController = callLogListController,
            contactsListState = contactsListState,
            contactsListController = contactsListController,
            onCallPageSelected = callbacks.onCallPageSelected,
            onCallGroupPressed = callbacks.onCallGroupPressed,
            onRequestCallLogPermission = callbacks.onRequestCallLogPermission,
            onContactPressed = callbacks.onContactPressed,
            onCreateContact = callbacks.onCreateContact,
            onRequestContactsPermission = callbacks.onRequestContactsPermission,
            onDialDigit = callbacks.onDialDigit,
            onDialBackspace = callbacks.onDialBackspace,
            onDialClear = callbacks.onDialClear,
            onDialCall = callbacks.onDialCall,
            onDialMatchPressed = callbacks.onDialMatchPressed,
        )
        LauncherRouteDestination.CONTACT_DETAIL -> ContactDetailScreen(
            uiState = uiState,
            theme = theme,
            onCallNumber = callbacks.onContactCallNumber,
            onSmsNumber = callbacks.onContactSmsNumber,
            onEditContact = callbacks.onEditContact,
        )
        LauncherRouteDestination.CONTACT_EDITOR -> ContactEditorScreen(
            uiState = uiState,
            theme = theme,
            nameController = contactNameController,
            nameState = contactNameState,
            numberController = contactNumberController,
            numberState = contactNumberState,
            onNameChanged = callbacks.onContactEditorNameChanged,
            onNumberChanged = callbacks.onContactEditorNumberChanged,
            onDeleteNumber = callbacks.onContactEditorDeleteNumber,
            onSave = callbacks.onContactEditorSave,
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
        LauncherRouteDestination.SNAKE -> SnakeScreen(
            controller = snakeController,
            preparedFont = preparedFont,
            theme = theme,
        )
        LauncherRouteDestination.IDLE -> IdleScreen(
            uiState = uiState,
            theme = theme,
            sandClockController = sandClockController.takeIf { it.isVisible() },
        )
    }

    private fun syncNavigatorRoute(mode: LauncherMode) {
        val destination = destinationFor(mode)
        if (navigatorDestination == destination) return
        navigatorDestination = destination
        val navigator = navigatorState ?: return
        when (navigationAction(navigator.entries.map { entry -> entry.destination.id }, destination)) {
            LauncherRouteNavigationAction.NONE -> Unit
            LauncherRouteNavigationAction.PUSH -> navigator.push(routeRequestFor(destination))
            LauncherRouteNavigationAction.POP -> navigator.pop()
            LauncherRouteNavigationAction.POP_TO_ROOT -> navigator.popToRoot()
            LauncherRouteNavigationAction.REPLACE -> navigator.replace(
                request = routeRequestFor(destination),
                animated = true,
            )
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
        resolveLeadingInkInset = { text ->
            preparedFont.leadingInkInset(
                text = text,
                faceSelection = uiState.fontSelection,
            )
        },
        measureChromeTextWidth = { text ->
            preparedFont.measureTextWidth(
                text = text,
                faceSelection = chromeFontSelection(),
            )
        },
        chromeGeometry = chromeGeometry(),
    )

    private fun buildSettingsPage(): Widget = com.purride.pixellauncherv2.ui.screen.SettingsScreen(
        uiState = uiState,
        theme = theme,
        textEdgeResolvers = settingsTextEdgeResolvers(),
        onItemAction = callbacks.onSettingsItemAction,
    )

    /** 创建顶层设置与 MORE 页面共用的真实字形墨迹边界解析器。 */
    private fun settingsTextEdgeResolvers(): SettingsTextEdgeResolvers =
        SettingsTextEdgeResolvers(
            leadingInkInset = { text ->
                preparedFont.leadingInkInset(
                    text = text,
                    faceSelection = uiState.fontSelection,
                )
            },
            trailingInkInset = { text ->
                preparedFont.trailingInkInset(
                    text = text,
                    faceSelection = uiState.fontSelection,
                )
            },
        )

    private fun buildGlobalStatusBar(): Widget =
        when (val presentation = LauncherStatusBarPresentation.forMode(uiState.mode)) {
            LauncherStatusBarPresentation.Search -> {
                LauncherSearchHeader(
                    state = drawerQueryState,
                    controller = drawerTextController,
                    placeholder = "SEARCH APP",
                    placeholderLeadingInkInset = preparedFont.leadingInkInset(
                        text = "SEARCH APP",
                        faceSelection = PixelFontCatalog.selectionForRole(
                            family = uiState.fontSelection.family,
                            widthMode = uiState.fontSelection.widthMode,
                            role = LauncherTextRole.CHROME,
                        ),
                    ),
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
                    statusBarWidth = screenProfile.logicalWidth,
                    chromeGeometry = chromeGeometry(),
                    statusBarHeight = LauncherHeaderLayout.statusBarHeight(
                        screenProfile,
                        uiState.fontSelection,
                    ),
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
                    statusBarWidth = screenProfile.logicalWidth,
                    resolveLeadingInkInset = { text ->
                        preparedFont.leadingInkInset(
                            text = text,
                            faceSelection = chromeFontSelection(),
                        )
                    },
                    measureTextWidth = { text ->
                        preparedFont.measureTextWidth(
                            text = text,
                            faceSelection = chromeFontSelection(),
                        )
                    },
                    chromeGeometry = chromeGeometry(),
                    statusBarHeight = LauncherHeaderLayout.statusBarHeight(
                        screenProfile,
                        uiState.fontSelection,
                    ),
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

    /** 返回当前家族和宽度模式承担状态栏语义角色的精确原生 face。 */
    private fun chromeFontSelection(): LauncherFontSelection = PixelFontCatalog.selectionForRole(
        family = uiState.fontSelection.family,
        widthMode = uiState.fontSelection.widthMode,
        role = LauncherTextRole.CHROME,
    )

    /** 返回当前 CHROME face 驱动的状态栏与 HOME 底栏共享几何。 */
    private fun chromeGeometry(): LauncherChromeGeometry =
        LauncherChromeLayout.geometry(uiState.fontSelection)

    private fun statusBarPageTitle(presentation: LauncherStatusBarPresentation.Standard): String {
        return when (uiState.mode) {
            LauncherMode.SMS_THREAD_DETAIL -> LauncherStatusBarPresentation.smsDetailPageTitle(
                conversationTitle = uiState.smsCurrentConversationTitle,
                address = uiState.smsCurrentAddress,
            )
            LauncherMode.CONTACT_DETAIL -> LauncherStatusBarPresentation.contactDetailPageTitle(
                displayName = uiState.contacts
                    .firstOrNull { contact -> contact.lookupKey == uiState.contactDetailLookupKey }
                    ?.displayName
                    .orEmpty()
                    .uppercase(),
            )
            else -> presentation.pageTitle
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
        resolveLabelLeadingInkInset = { label ->
            preparedFont.leadingInkInset(
                text = label,
                faceSelection = uiState.fontSelection,
            )
        },
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
            uiState.mode == LauncherMode.SMS_THREADS &&
                uiState.smsPageIndex == SmsPageIndex.ALL
        if (!searchIsVisible) {
            smsSearchController.requestBlur(smsSearchState)
        }
    }

    /**
     * 沙钟只活在待机页：进入时以当前时间落沙成形，驻留时分钟变化触发坍塌重组，
     * 离开立即释放粒子并停帧。种子在这里构造——只有宿主同时知道字体、主题与
     * 屏幕轮廓，控制器保持对渲染环境无知。
     */
    private fun syncSnake() {
        if (uiState.mode != LauncherMode.SNAKE) {
            snakeController.clear()
        }
    }

    private fun syncSandClock() {
        if (uiState.mode != LauncherMode.IDLE) {
            sandClockController.clear()
            return
        }
        val fieldWidth = screenProfile.logicalWidth
        val fieldHeight = screenProfile.logicalHeight - LauncherHeaderLayout.statusBarHeight(
            screenProfile,
            uiState.fontSelection,
        )
        if (fieldWidth <= 0 || fieldHeight <= 0) {
            return
        }
        val sandColor = if (IdlePresentationModel.presentation(uiState).isNight) {
            theme.text.secondary
        } else {
            theme.text.primary
        }
        val rasterizer = preparedFont.defaultRasterizer
        sandClockController.sync(uiState.currentTimeText) { text ->
            SandClockSeedRenderer.renderTimeSeed(
                text = text,
                rasterizer = rasterizer,
                fieldWidth = fieldWidth,
                fieldHeight = fieldHeight,
                color = sandColor,
            )
        }
    }

    private fun syncContactEditorState() {
        if (contactNameState.text != uiState.contactEditorNameDraft) {
            contactNameController.updateText(
                state = contactNameState,
                text = uiState.contactEditorNameDraft,
                selectionStart = uiState.contactEditorNameDraft.length,
            )
        }
        if (contactNumberState.text != uiState.contactEditorNumberDraft) {
            contactNumberController.updateText(
                state = contactNumberState,
                text = uiState.contactEditorNumberDraft,
                selectionStart = uiState.contactEditorNumberDraft.length,
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
            LauncherMode.MORE_SETTINGS -> LauncherRouteDestination.MORE_SETTINGS
            LauncherMode.SMS_ROLE_PROMPT -> LauncherRouteDestination.SMS_ROLE_PROMPT
            LauncherMode.SMS_THREADS -> LauncherRouteDestination.SMS_THREADS
            LauncherMode.SMS_THREAD_DETAIL -> LauncherRouteDestination.SMS_THREAD_DETAIL
            LauncherMode.DIALER -> LauncherRouteDestination.DIALER
            LauncherMode.CONTACT_DETAIL -> LauncherRouteDestination.CONTACT_DETAIL
            LauncherMode.CONTACT_EDITOR -> LauncherRouteDestination.CONTACT_EDITOR
            LauncherMode.APP_MANAGEMENT -> LauncherRouteDestination.APP_MANAGEMENT
            LauncherMode.DATA_HEALTH -> LauncherRouteDestination.DATA_HEALTH
            LauncherMode.NOTIFICATION_SETTINGS -> LauncherRouteDestination.NOTIFICATION_SETTINGS
            LauncherMode.LOADING_PREVIEW -> LauncherRouteDestination.LOADING_PREVIEW
            LauncherMode.DIAGNOSTICS -> LauncherRouteDestination.DIAGNOSTICS
            LauncherMode.SNAKE -> LauncherRouteDestination.SNAKE
            LauncherMode.IDLE -> LauncherRouteDestination.IDLE
        }

        internal fun transitionFor(destination: LauncherRouteDestination): PixelRouteTransition? = when (destination) {
            LauncherRouteDestination.SMS_ROLE_PROMPT,
            LauncherRouteDestination.SMS_THREADS,
            LauncherRouteDestination.DIALER,
            -> PixelRouteTransition.SlideVertical

            LauncherRouteDestination.MAIN,
            LauncherRouteDestination.MORE_SETTINGS,
            LauncherRouteDestination.SMS_THREAD_DETAIL,
            LauncherRouteDestination.CONTACT_DETAIL,
            LauncherRouteDestination.CONTACT_EDITOR,
            LauncherRouteDestination.APP_MANAGEMENT,
            LauncherRouteDestination.DATA_HEALTH,
            LauncherRouteDestination.NOTIFICATION_SETTINGS,
            LauncherRouteDestination.LOADING_PREVIEW,
            LauncherRouteDestination.DIAGNOSTICS,
            LauncherRouteDestination.SNAKE,
            LauncherRouteDestination.IDLE,
            -> null
        }

        internal fun navigationAction(
            currentDestinationIds: List<String>,
            destination: LauncherRouteDestination,
        ): LauncherRouteNavigationAction {
            val target = destination.routeName
            if (currentDestinationIds.lastOrNull() == target) {
                return LauncherRouteNavigationAction.NONE
            }
            if (destination == LauncherRouteDestination.MAIN && currentDestinationIds.size > 1) {
                return LauncherRouteNavigationAction.POP_TO_ROOT
            }
            if (currentDestinationIds.dropLast(1).lastOrNull() == target) {
                return LauncherRouteNavigationAction.POP
            }
            return if (currentDestinationIds.isNotEmpty()) {
                LauncherRouteNavigationAction.PUSH
            } else {
                LauncherRouteNavigationAction.REPLACE
            }
        }
    }
}

/** Launcher typed Navigator 使用的封闭业务参数。 */
internal data class LauncherRouteArguments(
    /** 当前 entry 应构建的业务目的地。 */
    val destination: LauncherRouteDestination,
)

/** Launcher 根据外部状态同步 typed route 栈时使用的操作。 */
internal enum class LauncherRouteNavigationAction {
    NONE,
    PUSH,
    POP,
    POP_TO_ROOT,
    REPLACE,
}

/** Launcher 可进入的稳定 typed route 目的地集合。 */
internal enum class LauncherRouteDestination(
    /** 持久稳定、用于 entry 诊断和栈比较的目的地 ID。 */
    val routeName: String,
) {
    MAIN("main"),
    MORE_SETTINGS("more-settings"),
    SMS_ROLE_PROMPT("sms-role-prompt"),
    SMS_THREADS("sms-threads"),
    SMS_THREAD_DETAIL("sms-thread-detail"),
    DIALER("dialer"),
    CONTACT_DETAIL("contact-detail"),
    CONTACT_EDITOR("contact-editor"),
    APP_MANAGEMENT("app-management"),
    DATA_HEALTH("data-health"),
    NOTIFICATION_SETTINGS("notification-settings"),
    LOADING_PREVIEW("loading-preview"),
    DIAGNOSTICS("diagnostics"),
    SNAKE("snake"),
    IDLE("idle"),
}
