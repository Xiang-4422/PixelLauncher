package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.purride.pixellauncherv2.data.AppCustomizationRepository
import com.purride.pixellauncherv2.data.AppRepository
import com.purride.pixellauncherv2.data.CommunicationStatus
import com.purride.pixellauncherv2.data.CommunicationStatusRepository
import com.purride.pixellauncherv2.data.DeviceLocationRepository
import com.purride.pixellauncherv2.data.DeviceMotionRepository
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.data.GeoPoint
import com.purride.pixellauncherv2.data.HandTrackingRepository
import com.purride.pixellauncherv2.data.LauncherStatsRepository
import com.purride.pixellauncherv2.data.MediaPlaybackRepository
import com.purride.pixellauncherv2.data.NextAlarmRepository
import com.purride.pixellauncherv2.data.NotificationCommandStore
import com.purride.pixellauncherv2.data.NotificationSummaryRepository
import com.purride.pixellauncherv2.data.NotificationSummarySettingsRepository
import com.purride.pixellauncherv2.data.NotificationSummaryStore
import com.purride.pixellauncherv2.data.RainForecastRepository
import com.purride.pixellauncherv2.data.ScreenUsageRepository
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DataHealthItem
import com.purride.pixellauncherv2.launcher.DataHealthRepairAction
import com.purride.pixellauncherv2.launcher.DataHealthRepairActionModel
import com.purride.pixellauncherv2.launcher.DrawerAsciiInputSanitizer
import com.purride.pixellauncherv2.launcher.DrawerContentTapAction
import com.purride.pixellauncherv2.launcher.DrawerContentTapResolver
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.DrawerSearchAutoLaunchPolicy
import com.purride.pixellauncherv2.launcher.HomeInfoAction
import com.purride.pixellauncherv2.launcher.HomeInfoDetailModel
import com.purride.pixellauncherv2.launcher.IdleAutoEntryPolicy
import com.purride.pixellauncherv2.launcher.LauncherChromeLayout
import com.purride.pixellauncherv2.launcher.LauncherFontSelection
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.LauncherCallbacks
import com.purride.pixellauncherv2.launcher.LauncherRootHost
import com.purride.pixellauncherv2.launcher.MediaPlaybackSnapshot
import com.purride.pixellauncherv2.launcher.NotificationSummary
import com.purride.pixellauncherv2.launcher.PixelMatterEffectMode
import com.purride.pixellauncherv2.launcher.PixelMatterShakeDetector
import com.purride.pixellauncherv2.launcher.SmsLayout
import com.purride.pixellauncherv2.launcher.CallPageIndex
import com.purride.pixellauncherv2.launcher.DialInputModel
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SnakeModel
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.animation.LauncherAnimationState
import com.purride.pixellauncherv2.launcher.LauncherThemeFamily
import com.purride.pixellauncherv2.launcher.LauncherThemeMode
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory
import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.model.DeviceMotionSnapshot
import com.purride.pixellauncherv2.model.DeviceStatus
import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import com.purride.pixellauncherv2.system.AndroidAppLauncher
import com.purride.pixellauncherv2.system.ScreenGravityMapper
import com.purride.pixellauncherv2.system.WindowModeController
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.ui.text.LauncherFontRepository
import com.purride.pixellauncherv2.ui.text.PreparedLauncherFont
import com.purride.pixellauncherv2.util.ThrottleClickHelper
import com.purride.pixellauncherv2.util.TimeTextProvider
import com.purride.pixellauncherv2.viewmodel.LauncherViewModel
import com.purride.pixellauncherv2.viewmodel.toLauncherUiState
import com.purride.pixelui.PixelHapticType
import androidx.lifecycle.ViewModelProvider
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * 启动器运行时的总编排入口。
 *
 * 持有各类仓库，把 Android 输入转换成 [LauncherState] 变化，驱动充电动画 ticker，
 * 并通过 [LauncherRootHost] 把最新状态提交给 pixel-engine 渲染。
 */
class MainActivity : AppCompatActivity() {

    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    /** 字体 IO、索引和协调使用的独立双线程池，避免阻塞短信等后台任务。 */
    private val fontExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeTextProvider = TimeTextProvider()
    private val throttleClickHelper = ThrottleClickHelper()

    private lateinit var launcherViewModel: LauncherViewModel

    // Phase 8: unified root host (replaces Phases 3–7 individual hosts)
    private lateinit var launcherRootHost: LauncherRootHost
    /** 冷启动或切换成功后提交给 Host 的完整字体。 */
    private lateinit var activePreparedFont: PreparedLauncherFont
    /** 负责字体异步准备、single-flight 与有限历史缓存。 */
    private lateinit var launcherFontRepository: LauncherFontRepository
    /** 冷启动先展示主题背景，字体准备后再挂载唯一 Host。 */
    private lateinit var rootContainer: FrameLayout
    /** 字体请求期间阻止设置重复入队。 */
    private var isFontLoading: Boolean = false
    private lateinit var handTrackingDebugOverlayView: HandTrackingDebugOverlayView

    private lateinit var appRepository: AppRepository
    private lateinit var appCustomizationRepository: AppCustomizationRepository
    private lateinit var fontSettingsRepository: FontSettingsRepository
    private lateinit var launcherStatsRepository: LauncherStatsRepository
    private lateinit var deviceStatusRepository: DeviceStatusRepository
    private lateinit var nextAlarmRepository: NextAlarmRepository
    private lateinit var screenUsageRepository: ScreenUsageRepository
    private lateinit var communicationStatusRepository: CommunicationStatusRepository
    private lateinit var notificationSummaryRepository: NotificationSummaryRepository
    private lateinit var notificationSummarySettingsRepository: NotificationSummarySettingsRepository
    private lateinit var mediaPlaybackRepository: MediaPlaybackRepository
    private lateinit var deviceLocationRepository: DeviceLocationRepository
    private lateinit var deviceMotionRepository: DeviceMotionRepository
    private lateinit var handTrackingRepository: HandTrackingRepository
    private lateinit var smsController: SmsController
    private lateinit var callController: CallController
    private lateinit var contactsController: ContactsController
    private lateinit var rainForecastRepository: RainForecastRepository
    private lateinit var appLauncher: AndroidAppLauncher
    private lateinit var windowModeController: WindowModeController
    private var screenProfile: LauncherLayoutProfile = LauncherLayoutProfileFactory.create(widthPx = 1, heightPx = 1)
    /** 冷启动至状态初始化期间使用的主题家族。 */
    private var selectedThemeFamily: LauncherThemeFamily = LauncherThemeFamily.MIDNIGHT
    /** 冷启动至状态初始化期间使用的主题亮暗模式。 */
    private var selectedThemeMode: LauncherThemeMode = LauncherThemeMode.NIGHT
    private var pendingPixelAppearanceBaseline: PixelAppearanceBaseline? = null
    private var pendingPixelConfirmDeadlineUptimeMs: Long = 0L
    // Single source of truth lives in [launcherViewModel]; `state` delegates onto it,
    // so every existing `state = transition(state)` reducer call reads/writes there.
    private var state: LauncherState
        get() = launcherViewModel.current
        set(value) {
            launcherViewModel.current = value
        }
    private var animationState = LauncherAnimationState()
    private var loadGeneration = 0
    private var launchPending = false
    private var launchRunnable: Runnable? = null
    private var rainRefreshInFlight = false
    private var lastRainRefreshElapsedRealtimeMs: Long = 0L
    private var lastRainLocation: GeoPoint? = null
    private var lastSuccessfulRainHintText: String = ""
    private val pixelMatterShakeDetector = PixelMatterShakeDetector()
    private var pixelMatterMotionListening = false
    private var pixelMatterHandTracking = false
    private var activityResumed = false

    private val smsHost = object : SmsController.Host {
        override var state: LauncherState
            get() = this@MainActivity.state
            set(value) {
                this@MainActivity.state = value
            }

        override fun render() = renderCurrentFrame()

        override fun isActive(): Boolean = !(isDestroyed || isFinishing)

        override fun smsThreadsVisibleRows(): Int =
            SmsLayout.threadVisibleRows(screenProfile, state.fontSelection)

        override fun smsInboxVisibleRows(): Int =
            SettingsMenuLayout.largeVisibleRows(screenProfile, state.fontSelection)

        override fun updateTextInputFocus() = this@MainActivity.updateTextInputFocus()

        override fun scheduleIdleCheck() = this@MainActivity.scheduleIdleCheck()

        override fun refreshCommunicationStatus(render: Boolean) =
            this@MainActivity.refreshCommunicationStatus(render)

        override fun requestSmsPermissions(permissions: Array<String>) =
            requestPermissions(permissions, smsPermissionRequestCode)

        override fun startSmsRoleRequest(intent: Intent) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, smsRoleRequestCode)
        }

        override fun showStatusBarMessage(message: String) =
            this@MainActivity.showStatusBarMessage(message)

    }

    private val callHost = object : CallController.Host {
        override var state: LauncherState
            get() = this@MainActivity.state
            set(value) {
                this@MainActivity.state = value
            }

        override fun render() = renderCurrentFrame()

        override fun isActive(): Boolean = !(isDestroyed || isFinishing)

        override fun refreshCommunicationStatus(render: Boolean) =
            this@MainActivity.refreshCommunicationStatus(render)

        override fun requestCallPermissions(permissions: Array<String>) =
            requestPermissions(permissions, callPermissionRequestCode)

        override fun showStatusBarMessage(message: String) =
            this@MainActivity.showStatusBarMessage(message)

        override fun scheduleIdleCheck() = this@MainActivity.scheduleIdleCheck()
    }

    private val contactsHost = object : ContactsController.Host {
        override var state: LauncherState
            get() = this@MainActivity.state
            set(value) {
                this@MainActivity.state = value
            }

        override fun render() = renderCurrentFrame()

        override fun isActive(): Boolean = !(isDestroyed || isFinishing)

        override fun showStatusBarMessage(message: String) =
            this@MainActivity.showStatusBarMessage(message)

        override fun requestContactsWritePermission() =
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_CONTACTS),
                callPermissionRequestCode,
            )
    }

    private val clockTicker = object : Runnable {
        override fun run() {
            state = LauncherStateTransitions.updateTime(
                state = state,
                currentTimeText = timeTextProvider.currentTimeText(),
                currentDateText = timeTextProvider.currentDateText(),
                currentWeekdayText = timeTextProvider.currentWeekdayText(),
            )
            refreshDerivedUiState(render = true)
            refreshScreenUsageSummary(render = true)
            refreshRainHint(force = false, render = true)
            mainHandler.postDelayed(this, timeTextProvider.millisUntilNextMinute())
        }
    }

    private val animationTicker = object : Runnable {
        override fun run() {
            if (!shouldRunDecorationTicker()) {
                return
            }
            animationState = animationState.nextFrame()
            renderCurrentFrame()
            if (shouldRunDecorationTicker()) {
                mainHandler.postDelayed(this, LauncherAnimationState.frameDelayMs)
            }
        }
    }

    private val idleRunnable = Runnable {
        val delay = IdleAutoEntryPolicy.nextInactivityDelayMs(
            state = state,
            nowUptimeMs = SystemClock.uptimeMillis(),
            launchPending = launchPending,
        ) ?: return@Runnable
        if (delay == 0L) {
            enterIdleIfAllowed()
        } else {
            scheduleIdleCheck()
        }
    }

    private val statusBarMessageClearRunnable = Runnable {
        if (state.statusBarMessageText.isBlank()) {
            return@Runnable
        }
        state = LauncherStateTransitions.updateStatusBarMessage(
            state = state,
            message = "",
        )
        renderCurrentFrame()
    }

    private val pixelChangeConfirmTicker = object : Runnable {
        override fun run() {
            updatePixelChangeConfirmStatusBar()
        }
    }

    /**
     * 启动整个 launcher 运行时，恢复设置，接线仓库与渲染链路，并完成首次渲染。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherViewModel = ViewModelProvider(this)[LauncherViewModel::class.java]
        supportActionBar?.hide()
        window.setWindowAnimations(0)

        // 依赖组装收敛到 AppContainer；仅在 onCreate 内临时使用，把取到的依赖赋给现有字段后即可丢弃，
        // 因此不作为 Activity 字段持有。
        val appContainer = AppContainer(
            context = applicationContext,
            backgroundExecutor = backgroundExecutor,
            mainHandler = mainHandler,
        )
        appRepository = appContainer.appRepository
        appCustomizationRepository = appContainer.appCustomizationRepository
        fontSettingsRepository = appContainer.fontSettingsRepository
        launcherStatsRepository = appContainer.launcherStatsRepository
        deviceStatusRepository = appContainer.deviceStatusRepository
        nextAlarmRepository = appContainer.nextAlarmRepository
        screenUsageRepository = appContainer.screenUsageRepository
        communicationStatusRepository = appContainer.communicationStatusRepository
        notificationSummaryRepository = appContainer.notificationSummaryRepository
        notificationSummarySettingsRepository = appContainer.notificationSummarySettingsRepository
        mediaPlaybackRepository = appContainer.mediaPlaybackRepository
        smsController = SmsController(
            context = applicationContext,
            smsRepository = appContainer.smsRepository,
            smsNotificationHelper = appContainer.smsNotificationHelper,
            smsMuteSettingsRepository = appContainer.smsMuteSettingsRepository,
            backgroundExecutor = backgroundExecutor,
            mainHandler = mainHandler,
            host = smsHost,
        )
        callController = CallController(
            callLogRepository = appContainer.callLogRepository,
            dialerRepository = appContainer.dialerRepository,
            contactSearchRepository = appContainer.contactSearchRepository,
            contactDirectoryRepository = appContainer.contactDirectoryRepository,
            backgroundExecutor = backgroundExecutor,
            mainHandler = mainHandler,
            host = callHost,
        )
        contactsController = ContactsController(
            contactDirectoryRepository = appContainer.contactDirectoryRepository,
            contactSearchRepository = appContainer.contactSearchRepository,
            backgroundExecutor = backgroundExecutor,
            mainHandler = mainHandler,
            host = contactsHost,
        )
        deviceLocationRepository = appContainer.deviceLocationRepository
        deviceMotionRepository = appContainer.deviceMotionRepository
        handTrackingRepository = appContainer.handTrackingRepository
        rainForecastRepository = appContainer.rainForecastRepository
        val appearanceSettings = fontSettingsRepository.getAppearanceSettings()
        val uiBehaviorSettings = fontSettingsRepository.getUiBehaviorSettings()
        selectedThemeFamily = appearanceSettings.themeFamily
        selectedThemeMode = appearanceSettings.themeMode
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedPixelShape = appearanceSettings.pixelShape,
            selectedDotSizePx = appearanceSettings.dotSizePx,
            isPixelGapEnabled = appearanceSettings.pixelGapEnabled,
            selectedThemeFamily = appearanceSettings.themeFamily,
            selectedThemeMode = appearanceSettings.themeMode,
            fontSelection = appearanceSettings.fontSelection,
        )
        state = LauncherStateTransitions.updateUiBehavior(
            state = state,
            drawerListAlignment = uiBehaviorSettings.drawerListAlignment,
            isIdlePageEnabled = uiBehaviorSettings.isIdlePageEnabled,
            chargeAutoIdleEnabled = uiBehaviorSettings.chargeAutoIdleEnabled,
            inactivityAutoIdleEnabled = uiBehaviorSettings.inactivityAutoIdleEnabled,
            idleTimeoutSeconds = uiBehaviorSettings.idleTimeoutSeconds,
            openDrawerInSearchMode = uiBehaviorSettings.openDrawerInSearchMode,
            chargeIdleEffect = uiBehaviorSettings.chargeIdleEffect,
            isPixelMatterEffectEnabled = uiBehaviorSettings.pixelMatterEffectEnabled,
            pixelMatterEffectMode = uiBehaviorSettings.pixelMatterEffectMode,
            isPixelMatterHandControlEnabled = uiBehaviorSettings.pixelMatterHandControlEnabled,
            isPixelMatterHandDebugEnabled = uiBehaviorSettings.pixelMatterHandDebugEnabled,
        )
        state = notificationSummarySettingsRepository.rules().let { rules ->
            LauncherStateTransitions.updateNotificationRules(
                state = state,
                mutedSourceIds = rules.mutedSourceIds,
                prioritySourceIds = rules.prioritySourceIds,
            )
        }
        state = LauncherStateTransitions.updateStats(state, launcherStatsRepository.read())
        state = LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis())
        state = LauncherStateTransitions.updateTime(
            state = state,
            currentTimeText = timeTextProvider.currentTimeText(),
            currentDateText = timeTextProvider.currentDateText(),
            currentWeekdayText = timeTextProvider.currentWeekdayText(),
        )
        refreshDerivedUiState(render = false)

        appLauncher = AndroidAppLauncher(this)
        windowModeController = WindowModeController(window)
        windowModeController.hideSystemBars()
        val metrics = resources.displayMetrics
        screenProfile = LauncherLayoutProfileFactory.create(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            dotSizePx = appearanceSettings.dotSizePx,
            pixelShape = appearanceSettings.pixelShape,
            statusBarHeightPx = currentStatusBarHeightPx(),
        )
        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(
                LauncherThemes.resolve(
                    family = selectedThemeFamily,
                    brightness = selectedThemeMode.resolve(isSystemInDarkMode()),
                ).surface.bezelColor.argb,
            )
        }
        setContentView(rootContainer)
        launcherFontRepository = LauncherFontRepository(applicationContext, fontExecutor, mainHandler)
        isFontLoading = true
        launcherFontRepository.prepare(state.fontSelection) { result ->
            isFontLoading = false
            result.onSuccess(::initializeLauncherRootHost)
                .onFailure { error -> showFatalFontLoadError(error) }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::launcherRootHost.isInitialized && launcherRootHost.handlePixelMatterBack()) {
                    return
                }
                when (state.mode) {
                    LauncherMode.SETTINGS -> closeSettingsMenu()
                    LauncherMode.MORE_SETTINGS -> closeMoreSettings()
                    LauncherMode.SMS_ROLE_PROMPT -> smsController.closeModule()
                    LauncherMode.SMS_THREADS -> smsController.closeModule()
                    LauncherMode.SMS_THREAD_DETAIL -> smsController.closeThreadDetail()
                    LauncherMode.DIALER -> callController.closeCallLog()
                    LauncherMode.CONTACT_DETAIL -> contactsController.closeContact()
                    LauncherMode.CONTACT_EDITOR -> contactsController.closeEditor()
                    LauncherMode.APP_MANAGEMENT -> closeAppManagement()
                    LauncherMode.DATA_HEALTH -> closeDataHealth()
                    LauncherMode.NOTIFICATION_SETTINGS -> closeNotificationSettings()
                    LauncherMode.LOADING_PREVIEW -> closeLoadingPreview()
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
                    LauncherMode.SNAKE -> closeSnake()
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        state = if (state.isAppActionMenuVisible) {
                            LauncherStateTransitions.hideAppActionMenu(state)
                        } else if (state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()) {
                            LauncherStateTransitions.exitDrawerSearch(
                                state = state,
                                visibleRows = visibleRows(),
                            )
                        } else {
                            LauncherStateTransitions.showHome(state)
                        }
                        renderCurrentFrame()
                        startAnimationTickerIfNeeded()
                        updateDrawerInputFocus()
                    }

                    LauncherMode.IDLE -> wakeFromIdle()
                    LauncherMode.HOME -> Unit
                }
            }
        })
        suppressActivityAnimations()
    }

    /** 在持久化字体准备成功后一次性创建 Host、调试层和首帧。 */
    private fun initializeLauncherRootHost(preparedFont: PreparedLauncherFont) {
        if (isDestroyed || ::launcherRootHost.isInitialized) return
        activePreparedFont = preparedFont
        refreshFontCacheSummary()
        launcherRootHost = LauncherRootHost(
            context = this,
            initialFont = preparedFont,
            onPixelMatterEffectStart = ::syncPixelMatterHandTracking,
            onPixelMatterRestoreStart = ::stopPixelMatterHandTracking,
            onPixelMatterEffectClear = ::stopPixelMatterHandTracking,
            callbacks = createLauncherCallbacks(),
        )
        rootContainer.addView(
            launcherRootHost.rootView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        handTrackingDebugOverlayView = HandTrackingDebugOverlayView(this)
        rootContainer.addView(
            handTrackingDebugOverlayView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        launcherRootHost.rootView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newWidth = right - left
            val newHeight = bottom - top
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop
            if (newWidth > 0 && newHeight > 0 && (newWidth != oldWidth || newHeight != oldHeight)) {
                updateScreenProfile(newWidth, newHeight)
                updateMediaGestureExclusion(state.mediaPlayback.hasTrack)
            }
        }
        updateTextInputFocus()
        renderCurrentFrame()
        handleLaunchIntent(intent)
    }

    /** 集中构建唯一 Host 使用的全部业务回调。 */
    private fun createLauncherCallbacks(): LauncherCallbacks = LauncherCallbacks(
        onOpenCall = ::onHomeOpenCall,
        onOpenSms = ::onHomeOpenSms,
        onHomeInfoAction = ::onHomeInfoAction,
        onHomeInfoDetail = ::onHomeInfoDetail,
        onMediaOpenPlayer = ::onMediaOpenPlayer,
        onMediaToggleFavorite = ::onMediaToggleFavorite,
        onMediaTogglePlayPause = ::onMediaTogglePlayPause,
        onMediaSkipPrevious = ::onMediaSkipPrevious,
        onMediaSkipNext = ::onMediaSkipNext,
        onMediaSeek = ::onMediaSeek,
        onHomeNotificationPressed = ::onHomeNotificationPressed,
        onHomeNotificationAction = ::onHomeNotificationAction,
        onDrawerQueryChanged = ::onPixelEngineDrawerQueryChanged,
        onDrawerSubmitSearch = ::onPixelEngineDrawerSubmitSearch,
        onDrawerAppPressed = ::onPixelEngineDrawerAppPressed,
        onDrawerAppLongPressed = ::onPixelEngineDrawerAppLongPressed,
        onDrawerAppMenuEdit = ::onDrawerAppMenuEdit,
        onDrawerAppMenuRefresh = ::onDrawerAppMenuRefresh,
        onDrawerAppMenuDismiss = ::onDrawerAppMenuDismiss,
        onSettingsItemAction = ::onSettingsItemAction,
        onStatusBarAction = ::onStatusBarAction,
        onAppEditorPrevious = ::onAppEditorPrevious,
        onAppEditorNext = ::onAppEditorNext,
        onAppEditorNameChanged = ::onAppEditorNameChanged,
        onAppEditorAliasChanged = ::onAppEditorAliasChanged,
        onAppEditorSave = ::onAppEditorSave,
        onAppEditorReset = ::onAppEditorReset,
        onAppCacheReset = ::onAppCacheReset,
        onOpenDataHealth = ::openDataHealth,
        onDataHealthItemPressed = ::onDataHealthItemPressed,
        onNotificationSourcePressed = ::onNotificationSourcePressed,
        onRequestSmsRole = smsController::requestDefaultRole,
        onOpenThread = smsController::openThread,
        onSmsPageSelected = smsController::selectPage,
        onMarkSmsRead = smsController::markAllRead,
        onMarkUnreadMessageRead = smsController::markMessageRead,
        onDraftChanged = smsController::draftChanged,
        onSmsThreadSearchChanged = smsController::threadSearchChanged,
        onSendDraft = smsController::sendDraft,
        onSmsMessagePressed = smsController::messagePressed,
        onSmsMessageLongPressed = smsController::messageLongPressed,
        onSmsMessageMenuCopy = smsController::messageMenuCopyBody,
        onSmsMessageMenuCopyCode = smsController::messageMenuCopyCode,
        onSmsMessageMenuResend = smsController::messageMenuResend,
        onSmsMessageMenuDelete = smsController::messageMenuDelete,
        onSmsMessageMenuDismiss = smsController::messageMenuDismiss,
        onSmsThreadLongPressed = smsController::threadLongPressed,
        onSmsThreadMenuMarkRead = smsController::threadMenuMarkRead,
        onSmsThreadMenuToggleMute = smsController::threadMenuToggleMute,
        onSmsThreadMenuDelete = smsController::threadMenuDelete,
        onSmsThreadMenuDismiss = smsController::threadMenuDismiss,
        onComposeNewThread = smsController::composeNewThread,
        onCallGroupPressed = { number -> callController.callNumber(number) },
        onRequestCallLogPermission = callController::retryCallPermissions,
        onContactPressed = contactsController::openContact,
        onRequestContactsPermission = callController::retryCallPermissions,
        onContactCallNumber = callController::callNumber,
        onContactSmsNumber = smsController::composeNewThread,
        onCreateContact = { contactsController.openEditor("") },
        onEditContact = contactsController::openEditor,
        onContactEditorNameChanged = contactsController::updateEditorName,
        onContactEditorNumberChanged = contactsController::updateEditorNumber,
        onContactEditorDeleteNumber = contactsController::deleteNumber,
        onContactEditorSave = contactsController::saveEditor,
        onCallPageSelected = callController::selectPage,
        onDialDigit = callController::appendDialDigit,
        onDialBackspace = callController::backspaceDialInput,
        onDialClear = callController::clearDialInput,
        onDialCall = callController::callDialInput,
        onDialMatchPressed = callController::callDialMatch,
        onMainPageChanged = ::onMainPageChanged,
        onMainPageDragStart = ::onMainPageDragStart,
    )

    /** 冷启动字体损坏时不换用其他字体，只显示系统级错误并保留背景。 */
    private fun showFatalFontLoadError(error: Throwable) {
        Log.e(fontLoadLogTag, "Initial font load failed", error)
        android.widget.Toast.makeText(this, "FONT LOAD ERROR", android.widget.Toast.LENGTH_LONG).show()
    }

    /**
     * 在回到前台时重启监听器，并刷新设备状态、Home 数据和天气等前台专属工作。
     */
    override fun onResume() {
        super.onResume()
        activityResumed = true
        windowModeController.hideSystemBars()
        startClockTicker()
        deviceStatusRepository.start(::onDeviceStatusChanged)
        nextAlarmRepository.start(::onNextAlarmChanged)
        notificationSummaryRepository.start(::onNotificationSummaryChanged)
        mediaPlaybackRepository.start(::onMediaPlaybackChanged)
        resetDrawerVerticalGesture()
        // 覆盖层页面回到前台后应当留在原处；不在白名单里的模式才复位到 Home。
        // 新增覆盖层模式时必须同步加进来，否则切后台再回来会被弹回 Home。
        if (
            state.mode != LauncherMode.SMS_ROLE_PROMPT &&
            state.mode != LauncherMode.SMS_THREADS &&
            state.mode != LauncherMode.SMS_THREAD_DETAIL &&
            state.mode != LauncherMode.DIALER &&
            state.mode != LauncherMode.CONTACT_DETAIL &&
            state.mode != LauncherMode.CONTACT_EDITOR
        ) {
            state = LauncherStateTransitions.showHome(state)
        }
        state = LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis())
        refreshDerivedUiState(render = false)
        // Launcher 冷启动不能被统计、通话记录、短信或位置等可选能力阻塞。
        // 缺少权限时对应数据源返回空态，用户可在 DATA HEALTH 或具体功能页主动修复。
        communicationStatusRepository.start(::onCommunicationStatusChanged)
        smsController.start()
        callController.start()
        refreshScreenUsageSummary(render = false)
        smsController.refreshSmsCapability(render = false)
        refreshRainHint(force = true, render = false)
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        syncPixelMatterMotionListening()
        updateTextInputFocus()
        scheduleIdleCheck()
        loadApps()
        suppressActivityAnimations()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowModeController.hideSystemBars()
            updateTextInputFocus()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // configChanges="uiMode" keeps the activity alive across light/dark toggles;
        // re-render so an AUTO theme follows the new system uiMode.
        if (::launcherRootHost.isInitialized) {
            val widthPx = launcherRootHost.rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val heightPx = launcherRootHost.rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            if (!updateScreenProfile(widthPx, heightPx)) {
                renderCurrentFrame()
            }
        } else {
            renderCurrentFrame()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(
            smsIntentLogTag,
            "onNewIntent action=${intent.action} data=${intent.data} extras=${intent.extras?.keySet()?.joinToString()} mode=${state.mode}",
        )
        handleLaunchIntent(intent)
    }

    /**
     * 在进入后台时停止前台监听器和不应继续运行的临时 UI 工作。
     */
    override fun onPause() {
        activityResumed = false
        syncPixelMatterMotionListening()
        if (::launcherRootHost.isInitialized) {
            launcherRootHost.stopPixelMatterEffect()
        }
        stopPixelMatterHandTracking()
        hideDrawerKeyboard()
        resetDrawerVerticalGesture()
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.removeCallbacks(idleRunnable)
        mainHandler.removeCallbacks(statusBarMessageClearRunnable)
        mainHandler.removeCallbacks(pixelChangeConfirmTicker)
        restorePendingPixelAppearanceChange(render = false)
        state = LauncherStateTransitions.updateStatusBarMessage(state, message = "")
        launchRunnable?.let(mainHandler::removeCallbacks)
        launchRunnable = null
        launchPending = false
        deviceStatusRepository.stop()
        nextAlarmRepository.stop()
        notificationSummaryRepository.stop()
        mediaPlaybackRepository.stop()
        callController.stop()
        communicationStatusRepository.stop()
        smsController.stop()
        suppressActivityAnimations()
        super.onPause()
    }

    private fun syncPixelMatterMotionListening() {
        if (!::deviceMotionRepository.isInitialized || !::launcherRootHost.isInitialized) {
            return
        }
        if (!state.isPixelMatterEffectEnabled) {
            launcherRootHost.stopPixelMatterEffect()
            stopPixelMatterHandTracking()
            stopPixelMatterMotionListening()
            return
        }
        if (activityResumed) {
            startPixelMatterMotionListening()
        } else {
            stopPixelMatterMotionListening()
        }
    }

    private fun startPixelMatterMotionListening() {
        if (pixelMatterMotionListening) return
        pixelMatterMotionListening = true
        pixelMatterShakeDetector.reset()
        deviceMotionRepository.start(::onDeviceMotionChanged)
    }

    private fun stopPixelMatterMotionListening() {
        if (!pixelMatterMotionListening) return
        deviceMotionRepository.stop()
        pixelMatterMotionListening = false
        pixelMatterShakeDetector.reset()
    }

    private fun onDeviceMotionChanged(snapshot: DeviceMotionSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handleDeviceMotionChanged(snapshot.withCurrentScreenAxes()) }
        } else {
            handleDeviceMotionChanged(snapshot.withCurrentScreenAxes())
        }
    }

    private fun handleDeviceMotionChanged(snapshot: DeviceMotionSnapshot) {
        if (!state.isPixelMatterEffectEnabled || !::launcherRootHost.isInitialized) {
            return
        }
        launcherRootHost.updatePixelMatterMotion(snapshot)
        if (pixelMatterShakeDetector.record(snapshot) && launcherRootHost.triggerPixelMatter(snapshot)) {
            launcherRootHost.performHapticFeedback(PixelHapticType.TAP)
        }
    }

    private fun syncPixelMatterHandTracking() {
        if (!::handTrackingRepository.isInitialized || !::launcherRootHost.isInitialized) {
            return
        }
        if (!activityResumed ||
            !state.isPixelMatterEffectEnabled ||
            !state.isPixelMatterHandControlEnabled ||
            !launcherRootHost.isPixelMatterEffectActive() ||
            !handTrackingRepository.hasCameraPermission()
        ) {
            stopPixelMatterHandTracking()
            return
        }
        startPixelMatterHandTracking()
    }

    private fun startPixelMatterHandTracking() {
        if (pixelMatterHandTracking) return
        pixelMatterHandTracking = true
        handTrackingRepository.start(
            lifecycleOwner = this,
            logicalWidth = screenProfile.logicalWidth,
            logicalHeight = screenProfile.logicalHeight,
            listener = { snapshot ->
                launcherRootHost.updatePixelMatterHandInput(snapshot)
            },
            onDebugFrame = { frame ->
                if (::handTrackingDebugOverlayView.isInitialized) {
                    handTrackingDebugOverlayView.updateFrame(
                        if (state.isPixelMatterHandDebugEnabled) frame else null,
                    )
                }
            },
            onError = { message ->
                onPixelMatterHandTrackingError(message)
            },
        )
    }

    private fun stopPixelMatterHandTracking() {
        if (!pixelMatterHandTracking && !::handTrackingRepository.isInitialized) return
        if (::launcherRootHost.isInitialized) {
            launcherRootHost.updatePixelMatterHandInput(null)
        }
        if (::handTrackingRepository.isInitialized) {
            handTrackingRepository.stop()
        }
        if (::handTrackingDebugOverlayView.isInitialized) {
            handTrackingDebugOverlayView.updateFrame(null)
        }
        pixelMatterHandTracking = false
    }

    private fun syncPixelMatterHandDebugOverlay() {
        if (!::handTrackingDebugOverlayView.isInitialized) return
        if (!state.isPixelMatterHandDebugEnabled || !pixelMatterHandTracking) {
            handTrackingDebugOverlayView.updateFrame(null)
        }
    }

    private fun onPixelMatterHandTrackingError(message: String) {
        stopPixelMatterHandTracking()
        showStatusBarMessage(message.ifBlank { "CAMERA" })
    }

    @Suppress("DEPRECATION")
    private fun DeviceMotionSnapshot.withCurrentScreenAxes(): DeviceMotionSnapshot {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: windowManager.defaultDisplay.rotation
        } else {
            windowManager.defaultDisplay.rotation
        }
        val gravity = ScreenGravityMapper.mapToScreen(
            rawGravityX = gravityX,
            rawGravityY = gravityY,
            rawGravityZ = gravityZ,
            rotation = rotation,
        )
        val linear = ScreenGravityMapper.mapToScreen(
            rawGravityX = linearAccelX,
            rawGravityY = linearAccelY,
            rawGravityZ = linearAccelZ,
            rotation = rotation,
        )
        return copy(
            screenGravityX = gravity.first,
            screenGravityY = gravity.second,
            screenLinearAccelX = linear.first,
            screenLinearAccelY = linear.second,
        )
    }

    /**
     * 响应运行时权限变化，并立即刷新依赖这些权限的 Home 数据。
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            homeDataPermissionRequestCode -> {
                communicationStatusRepository.start(::onCommunicationStatusChanged)
                smsController.refreshSmsCapability(render = false)
                refreshCommunicationStatus(render = false)
                refreshRainHint(force = true, render = true)
            }

            smsPermissionRequestCode -> smsController.onPermissionsResult()

            callPermissionRequestCode -> {
                callController.onPermissionsResult()
                // 同一次授权可能带上 READ_CONTACTS：联系人页与 T9 都要立即受益。
                contactsController.refreshContacts()
            }

            cameraPermissionRequestCode -> {
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    applyUiBehavior(isPixelMatterHandControlEnabled = true)
                } else {
                    applyUiBehavior(isPixelMatterHandControlEnabled = false)
                    showStatusBarMessage("CAMERA")
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != smsRoleRequestCode) {
            return
        }
        smsController.onRoleRequestResult()
    }

    override fun onDestroy() {
        stopPixelMatterMotionListening()
        stopPixelMatterHandTracking()
        if (::launcherRootHost.isInitialized) {
            launcherRootHost.dispose()
        }
        if (::launcherFontRepository.isInitialized) {
            launcherFontRepository.dispose()
        }
        mainHandler.removeCallbacksAndMessages(null)
        backgroundExecutor.shutdownNow()
        fontExecutor.shutdownNow()
        super.onDestroy()
    }

    /** 根据系统内存压力释放最近字体和解压 glyph，不替换当前字体。 */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::launcherFontRepository.isInitialized) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && ::activePreparedFont.isInitialized) {
            launcherFontRepository.trimToActive(activePreparedFont.selection)
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            launcherFontRepository.clearGlyphCaches()
        }
    }

    /** 极端内存压力下仅清理解压 glyph，当前压缩索引仍保持可渲染。 */
    override fun onLowMemory() {
        super.onLowMemory()
        if (::launcherFontRepository.isInitialized) launcherFontRepository.clearGlyphCaches()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_HOME) {
                navigateHomeFromHardwareKey()
                return true
            }
            // BACK 必须走正常的 onBackPressedDispatcher 流程（onBackPressed 按 mode
            // 关闭 SMS / 设置等覆盖层）。绝不能在这里经 onKeyDown 处理：onKeyDown 无
            // BACK 分支，会落到 super.onKeyDown(KEYCODE_BACK)，后者 startTracking() 并
            // 返回 true，从而吞掉按键、导致 onBackPressed 永不触发（SMS 里返回键失效）。
            if (event.keyCode != KeyEvent.KEYCODE_BACK && onKeyDown(event.keyCode, event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 统一处理硬件按键导航，包括 pager、设置页、抽屉搜索和应用启动。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (launchPending) {
            return true
        }
        if (wakeIfIdle()) {
            return true
        }
        // 浮层菜单打开时，方向键与确认键只作用于菜单本身：直接消费掉，避免穿透
        // 操作被浮层遮住的底层列表（菜单以 modal=false 换回“点击外部关闭”，引擎
        // 不再为它拦截按键，必须在这里挡住）。BACK 不走 onKeyDown，仍由
        // onBackPressed 关闭菜单。
        if (state.isAppActionMenuVisible ||
            state.isSmsThreadMenuVisible ||
            state.isSmsMessageMenuVisible
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    recordInteraction()
                    return true
                }
            }
        }
        if (state.mode == LauncherMode.HOME ||
            state.mode == LauncherMode.APP_DRAWER ||
            state.mode == LauncherMode.SETTINGS
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> return false
            }
        }
        recordInteraction()
        // 拨号盘页把数字与 * # + 键直接当作拨号输入，退格键删除末位。
        if (state.mode == LauncherMode.DIALER && state.callPageIndex == CallPageIndex.DIAL) {
            DialInputModel.digitForKeyCode(keyCode)?.let { digit ->
                callController.appendDialDigit(digit)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                callController.backspaceDialInput()
                return true
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                when (state.mode) {
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        if (state.smsPageIndex == SmsPageIndex.UNREAD) {
                            smsController.moveInboxSelection(-1)
                        } else {
                            smsController.moveThreadSelection(-1)
                        }
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    // 通话记录页无选中态呈现，方向键不再驱动一个看不见的选中项。
                    LauncherMode.DIALER -> Unit
                    LauncherMode.CONTACT_DETAIL -> Unit
                    LauncherMode.CONTACT_EDITOR -> Unit
                    LauncherMode.SNAKE -> launcherRootHost.turnSnake(SnakeModel.Direction.UP)
                    LauncherMode.SETTINGS,
                    LauncherMode.MORE_SETTINGS -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.LOADING_PREVIEW,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.HOME,
                    LauncherMode.IDLE,
                    LauncherMode.SMS_ROLE_PROMPT -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (state.mode) {
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        if (state.smsPageIndex == SmsPageIndex.UNREAD) {
                            smsController.moveInboxSelection(1)
                        } else {
                            smsController.moveThreadSelection(1)
                        }
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.DIALER -> Unit
                    LauncherMode.CONTACT_DETAIL -> Unit
                    LauncherMode.CONTACT_EDITOR -> Unit
                    LauncherMode.SNAKE -> launcherRootHost.turnSnake(SnakeModel.Direction.DOWN)
                    LauncherMode.SETTINGS,
                    LauncherMode.MORE_SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.LOADING_PREVIEW,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE,
                    LauncherMode.SMS_ROLE_PROMPT -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS,
                    LauncherMode.MORE_SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
                    LauncherMode.SMS_ROLE_PROMPT,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.DIALER -> callController.selectPage(state.callPageIndex - 1)
                    LauncherMode.CONTACT_DETAIL -> Unit
                    LauncherMode.CONTACT_EDITOR -> Unit
                    LauncherMode.SNAKE -> launcherRootHost.turnSnake(SnakeModel.Direction.LEFT)
                    LauncherMode.SMS_THREADS -> smsController.selectPage(SmsPageIndex.UNREAD)
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.LOADING_PREVIEW,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS,
                    LauncherMode.MORE_SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
                    LauncherMode.SMS_ROLE_PROMPT,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.DIALER -> callController.selectPage(state.callPageIndex + 1)
                    LauncherMode.CONTACT_DETAIL -> Unit
                    LauncherMode.CONTACT_EDITOR -> Unit
                    LauncherMode.SNAKE -> launcherRootHost.turnSnake(SnakeModel.Direction.RIGHT)
                    LauncherMode.SMS_THREADS -> smsController.selectPage(SmsPageIndex.ALL)
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.LOADING_PREVIEW,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (state.mode) {
                    LauncherMode.SETTINGS,
                    LauncherMode.MORE_SETTINGS -> Unit
                    LauncherMode.SMS_ROLE_PROMPT -> smsController.ensureReadAccessAndRole()
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        if (state.smsPageIndex == SmsPageIndex.UNREAD) {
                            smsController.openSelectedUnreadThread()
                        } else {
                            smsController.openSelectedThread()
                        }
                    }
                    LauncherMode.DIALER -> {
                        // 只有拨号盘页响应回车：号码就在眼前，用户知道自己要拨什么。
                        // 通话记录页没有选中态呈现（列表以触摸为主通路，见 UI 规范），
                        // 回车拨号意味着用户看不见将拨给谁——外呼不可逆，不能这么办。
                        if (state.callPageIndex == CallPageIndex.DIAL) {
                            callController.callDialInput()
                        }
                    }
                    LauncherMode.CONTACT_DETAIL -> Unit
                    LauncherMode.CONTACT_EDITOR -> Unit
                    LauncherMode.SNAKE -> Unit
                    LauncherMode.SMS_THREAD_DETAIL -> {
                        if (state.smsDraftText.isBlank()) {
                            Unit // engine TextField handles SMS draft focus
                        } else {
                            smsController.sendDraft()
                        }
                    }
                    LauncherMode.DATA_HEALTH -> closeDataHealth()
                    LauncherMode.NOTIFICATION_SETTINGS -> closeNotificationSettings()
                    LauncherMode.LOADING_PREVIEW -> closeLoadingPreview()
                    LauncherMode.APP_MANAGEMENT -> onAppEditorSave()
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
                    LauncherMode.HOME -> Unit
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            else -> if (handleDrawerTextInput(keyCode, event)) true else super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateHomeFromHardwareKey() {
        if (launchPending) {
            return
        }
        recordInteraction()
        if (state.mode == LauncherMode.HOME) {
            return
        }
        state = LauncherStateTransitions.showHome(state)
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        updateTextInputFocus()
    }

    /**
     * 在后台线程加载可启动应用，并在结果返回后刷新派生 UI 状态。
     */
    private fun loadApps() {
        val generation = ++loadGeneration
        val cachedApps = applyAppCustomizations(appRepository.loadCachedLaunchableApps())
        if (cachedApps.isNotEmpty()) {
            state = LauncherStateTransitions.withApps(
                previous = state,
                apps = cachedApps,
                visibleRows = visibleRows(),
            )
            refreshDerivedUiState(render = true)
        } else {
            state = LauncherStateTransitions.beginAppCatalogLoading(state)
            renderCurrentFrame()
        }

        runInBackground {
            val apps = applyAppCustomizations(appRepository.loadLaunchableApps())
            mainHandler.post {
                if (generation != loadGeneration || isDestroyed || isFinishing) {
                    return@post
                }

                state = LauncherStateTransitions.updateTime(
                    state = state,
                    currentTimeText = timeTextProvider.currentTimeText(),
                    currentDateText = timeTextProvider.currentDateText(),
                    currentWeekdayText = timeTextProvider.currentWeekdayText(),
                )
                state = LauncherStateTransitions.withApps(
                    previous = state,
                    apps = apps,
                    visibleRows = visibleRows(),
                )
                refreshDerivedUiState(render = true)
            }
        }
    }

    private fun applyAppCustomizations(apps: List<AppEntry>): List<AppEntry> {
        return appCustomizationRepository.applyCustomizations(apps)
    }

    private fun launchSelectedApp() {
        val selectedApp = currentDrawerApps().getOrNull(state.selectedIndex) ?: return
        launchApp(selectedApp)
    }

    private fun launchAppAtIndex(index: Int) {
        val app = currentDrawerApps().getOrNull(index) ?: return
        launchApp(app)
    }

    /**
     * 执行启动遮罩动画、拉起目标应用，并记录 launcher 的启动统计。
     */
    private fun launchApp(selectedApp: AppEntry): Boolean {
        if (launchPending || !throttleClickHelper.canClick()) {
            return false
        }
        settleDrawerMotionBeforeExplicitAction()
        launchPending = true
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        val pendingRunnable = Runnable {
            launchRunnable = null
            val launched = appLauncher.launch(selectedApp)
            launchPending = false
            if (launched) {
                state = LauncherStateTransitions.updateStats(state, launcherStatsRepository.recordLaunch(selectedApp))
                state = LauncherStateTransitions.showHome(state)
                refreshDerivedUiState(render = false)
                updateDrawerInputFocus()
                suppressActivityAnimations()
            } else {
                renderCurrentFrame()
            }
        }
        launchRunnable = pendingRunnable
        mainHandler.postDelayed(pendingRunnable, LauncherAnimationState.launchShutterDurationMs)
        return true
    }

    /**
     * 收集当前渲染输入并提交一帧完整像素画面到显示视图。
     */
    private fun renderCurrentFrame() {
        if (!::launcherRootHost.isInitialized) return
        refreshDataHealthState()
        if (::mediaPlaybackRepository.isInitialized) {
            val mediaPlayback = mediaPlaybackRepository.current()
            if (state.mediaPlayback != mediaPlayback) {
                state = LauncherStateTransitions.updateMediaPlayback(
                    state = state,
                    mediaPlayback = mediaPlayback,
                )
            }
        }
        val uiState = state.toLauncherUiState()
        launcherRootHost.update(
            state           = uiState,
            theme           = LauncherThemes.resolve(
                family = uiState.selectedThemeFamily,
                brightness = uiState.selectedThemeMode.resolve(isSystemInDarkMode()),
            ),
            screenProfile   = screenProfile,
            chargeTick      = animationState.headerChargeTick,
            preparedFont    = activePreparedFont,
            pixelGapEnabled = uiState.isPixelGapEnabled,
        )
        updateMediaGestureExclusion(uiState.mediaPlayback.hasTrack)
    }

    private fun updateMediaGestureExclusion(showMediaControls: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !::launcherRootHost.isInitialized) {
            return
        }
        val view = launcherRootHost.rootView
        val widthPx = view.width
        val heightPx = view.height
        if (!showMediaControls || widthPx <= 0 || heightPx <= 0) {
            view.systemGestureExclusionRects = emptyList()
            return
        }
        /** HOME 底栏边框行加底部页面留白，随当前 CHROME face 同步增长。 */
        val exclusionLogicalHeight = LauncherChromeLayout
            .geometry(state.fontSelection)
            .bottomRegionHeight(LauncherSpacing.CONTENT_VERTICAL)
        val exclusionHeightPx = (screenProfile.dotSizePx * exclusionLogicalHeight).coerceAtLeast(1)
        view.systemGestureExclusionRects = listOf(
            Rect(
                0,
                (heightPx - exclusionHeightPx).coerceAtLeast(0),
                widthPx,
                heightPx,
            ),
        )
    }

    /** 系统是否处于暗色模式，用于把 AUTO 主题解析成 NIGHT/DAY。 */
    private fun isSystemInDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** 主页 Pager 手势翻页回调 — 同步旧 state + ViewModel state 的 mode 字段。 */
    private fun onMainPageChanged(mode: LauncherMode) {
        if (state.mode == mode) return
        state = when (mode) {
            LauncherMode.HOME -> LauncherStateTransitions.showHome(state)
            LauncherMode.APP_DRAWER -> {
                val previousMode = state.mode
                val shouldFocusSearchOnEntry = previousMode != LauncherMode.APP_DRAWER && state.openDrawerInSearchMode
                val drawerState = if (previousMode != LauncherMode.APP_DRAWER) {
                    LauncherStateTransitions.clearDrawerQuery(
                        state = state,
                        visibleRows = visibleRows(),
                    )
                } else {
                    state
                }
                LauncherStateTransitions.prepareDrawerEntryFocus(
                    state = LauncherStateTransitions.showAppDrawer(
                        state = drawerState,
                        visibleRows = visibleRows(),
                    ),
                    focusSearch = shouldFocusSearchOnEntry,
                )
            }
            LauncherMode.SETTINGS -> LauncherStateTransitions.showSettings(
                state = state,
                visibleRows = settingsVisibleRows(),
            )
            else -> state
        }
        renderCurrentFrame()
        updateTextInputFocus()
        scheduleIdleCheck()
    }

    private fun onMainPageDragStart() {
        if (!state.isDrawerSearchFocused && !state.isAppActionMenuVisible) {
            return
        }
        state = LauncherStateTransitions.dismissDrawerOverlaysForPagerDrag(state)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    // End Phase 8 ─────────────────────────────────────────────────────────────


    // ── SETTINGS callback (called from LauncherCallbacks) ─────────────────────

    /**
     * SETTINGS 屏幕行动作回调。
     *
     * 同时更新旧 [state] + [launcherViewModel]，确保两条渲染路径的数据保持同步。
     */
    private fun onSettingsItemAction(item: SettingsMenuItem, direction: Int) {
        val s = state
        when (item) {
            SettingsMenuItem.RESOLUTION -> applyPixelSizeChange(
                newDotSizePx = (s.selectedDotSizePx + direction).coerceAtLeast(1),
            )
            SettingsMenuItem.PIXEL_GAP -> {
                val enabled = !s.isPixelGapEnabled
                applyPixelAppearancePreview(
                    newDotSizePx = s.selectedDotSizePx,
                    newPixelGapEnabled = enabled,
                )
            }
            SettingsMenuItem.STYLE -> {
                restorePendingPixelAppearanceChange(render = false)
                val current = state
                applyAppearance(
                    newPixelShape = SettingsMenuModel.nextStyle(current.selectedPixelShape, direction),
                    newDotSizePx = current.selectedDotSizePx,
                    newPixelGapEnabled = current.isPixelGapEnabled,
                    newThemeMode = current.selectedThemeMode,
                )
            }
            SettingsMenuItem.THEME -> {
                restorePendingPixelAppearanceChange(render = false)
                val current = state
                applyAppearance(
                    newPixelShape = current.selectedPixelShape,
                    newDotSizePx = current.selectedDotSizePx,
                    newPixelGapEnabled = current.isPixelGapEnabled,
                    newThemeFamily = SettingsMenuModel.nextThemeFamily(
                        current.selectedThemeFamily,
                        direction,
                    ),
                    newThemeMode = current.selectedThemeMode,
                )
            }
            SettingsMenuItem.THEME_MODE -> {
                restorePendingPixelAppearanceChange(render = false)
                val current = state
                applyAppearance(
                    newPixelShape = current.selectedPixelShape,
                    newDotSizePx = current.selectedDotSizePx,
                    newPixelGapEnabled = current.isPixelGapEnabled,
                    newThemeFamily = current.selectedThemeFamily,
                    newThemeMode = SettingsMenuModel.nextThemeMode(current.selectedThemeMode, direction),
                )
            }
            SettingsMenuItem.FONT -> {
                restorePendingPixelAppearanceChange(render = false)
                val target = SettingsMenuModel.nextFontFamily(state.fontSelection, direction)
                requestFontSelection(fontSettingsRepository.selectionForFamily(target.family))
            }
            SettingsMenuItem.FONT_WIDTH -> {
                restorePendingPixelAppearanceChange(render = false)
                val target = SettingsMenuModel.nextFontWidth(state.fontSelection, direction)
                requestFontSelection(fontSettingsRepository.selectionForWidth(target.family, target.widthMode))
            }
            SettingsMenuItem.FONT_SIZE -> {
                restorePendingPixelAppearanceChange(render = false)
                requestFontSelection(SettingsMenuModel.nextFontSize(state.fontSelection, direction))
            }
            SettingsMenuItem.APP_LIST_ALIGNMENT -> applyUiBehavior(
                drawerListAlignment = SettingsMenuModel.nextDrawerListAlignment(s.drawerListAlignment, direction),
                isIdlePageEnabled = s.isIdlePageEnabled,
                openDrawerInSearchMode = s.openDrawerInSearchMode,
                chargeIdleEffect = s.chargeIdleEffect,
            )
            SettingsMenuItem.IDLE_PAGE -> applyUiBehavior(
                drawerListAlignment = s.drawerListAlignment,
                isIdlePageEnabled = SettingsMenuModel.toggle(s.isIdlePageEnabled),
                openDrawerInSearchMode = s.openDrawerInSearchMode,
                chargeIdleEffect = s.chargeIdleEffect,
            )
            SettingsMenuItem.CHARGE_AUTO_IDLE -> applyUiBehavior(
                chargeAutoIdleEnabled = SettingsMenuModel.toggle(s.chargeAutoIdleEnabled),
            )
            SettingsMenuItem.INACTIVITY_AUTO_IDLE -> applyUiBehavior(
                inactivityAutoIdleEnabled = SettingsMenuModel.toggle(s.inactivityAutoIdleEnabled),
            )
            SettingsMenuItem.IDLE_TIMEOUT -> applyUiBehavior(
                idleTimeoutSeconds = SettingsMenuModel.nextIdleTimeoutSeconds(s.idleTimeoutSeconds, direction),
            )
            SettingsMenuItem.CHARGE_IDLE_EFFECT -> applyUiBehavior(
                drawerListAlignment = s.drawerListAlignment,
                isIdlePageEnabled = s.isIdlePageEnabled,
                openDrawerInSearchMode = s.openDrawerInSearchMode,
                chargeIdleEffect = SettingsMenuModel.nextChargeIdleEffect(s.chargeIdleEffect, direction),
            )
            SettingsMenuItem.DRAWER_AUTO_SEARCH -> applyUiBehavior(
                drawerListAlignment = s.drawerListAlignment,
                isIdlePageEnabled = s.isIdlePageEnabled,
                openDrawerInSearchMode = SettingsMenuModel.toggle(s.openDrawerInSearchMode),
                chargeIdleEffect = s.chargeIdleEffect,
            )
            SettingsMenuItem.MORE -> openMoreSettings()
            SettingsMenuItem.PIXEL_MATTER_EFFECT -> applyUiBehavior(
                isPixelMatterEffectEnabled = SettingsMenuModel.toggle(s.isPixelMatterEffectEnabled),
            )
            SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE -> applyUiBehavior(
                pixelMatterEffectMode = SettingsMenuModel.nextPixelMatterEffectMode(s.pixelMatterEffectMode, direction),
            )
            SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL -> {
                if (s.isPixelMatterHandControlEnabled) {
                    applyUiBehavior(isPixelMatterHandControlEnabled = false)
                } else {
                    enablePixelMatterHandControl()
                }
            }
            SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG -> applyUiBehavior(
                isPixelMatterHandDebugEnabled = SettingsMenuModel.toggle(s.isPixelMatterHandDebugEnabled),
            )
            SettingsMenuItem.NOTIFICATIONS -> openNotificationSettings()
            SettingsMenuItem.DATA_HEALTH -> openDataHealth()
            SettingsMenuItem.LOADING_PREVIEW -> openLoadingPreview()
            SettingsMenuItem.ADVANCED -> openDiagnostics()
        }
    }

    private fun enablePixelMatterHandControl() {
        if (handTrackingRepository.hasCameraPermission()) {
            applyUiBehavior(isPixelMatterHandControlEnabled = true)
            return
        }
        requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequestCode)
    }

    private fun onStatusBarAction() {
        if (pendingPixelAppearanceBaseline != null) {
            confirmPendingPixelAppearanceChange()
        }
    }

    // ── HOME callbacks (called from LauncherCallbacks) ────────────────────────

    /** CALL 按钮：打开拨号模块，联系人目录随之后台刷新。 */
    private fun closeSnake() {
        state = LauncherStateTransitions.hideSnake(state)
        renderCurrentFrame()
    }

    private fun onHomeOpenCall() {
        callController.openCallLog()
        contactsController.refreshContacts()
    }

    /** SMS 按钮：进入短信模块。 */
    private fun onHomeOpenSms() {
        if (state.unreadSmsCount > 0) {
            smsController.openUnreadSummaryTarget()
        } else {
            smsController.openModule(initialPage = SmsPageIndex.UNREAD)
        }
    }

    private fun onMediaOpenPlayer() {
        mediaPlaybackRepository.openPlayer()
    }

    private fun onMediaToggleFavorite() {
        mediaPlaybackRepository.toggleFavorite()
        renderCurrentFrame()
    }

    private fun onMediaTogglePlayPause() {
        mediaPlaybackRepository.togglePlayPause()
        renderCurrentFrame()
    }

    private fun onMediaSkipPrevious() {
        mediaPlaybackRepository.skipPrevious()
        renderCurrentFrame()
    }

    private fun onMediaSkipNext() {
        mediaPlaybackRepository.skipNext()
        renderCurrentFrame()
    }

    private fun onMediaSeek(progress: Float) {
        mediaPlaybackRepository.seekToProgress(progress)
        renderCurrentFrame()
    }

    private fun onHomeInfoAction(action: HomeInfoAction) {
        when (action) {
            HomeInfoAction.RAIN -> {
                if (deviceLocationRepository.hasLocationPermission()) {
                    showStatusBarMessage(HomeInfoDetailModel.rainRefreshStarted())
                    refreshRainHint(force = true, render = true, showFeedback = true)
                } else {
                    showStatusBarMessage(HomeInfoDetailModel.notice(action, state))
                    requestHomeDataPermissions(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                }
            }

            HomeInfoAction.CALL -> onHomeOpenCall()

            HomeInfoAction.ALARM -> openAlarmClock()

            HomeInfoAction.BATTERY -> openBatterySettings()

            HomeInfoAction.NOTIFICATION -> openNotificationSummaryTarget()

            HomeInfoAction.SMS -> {
                if (state.unreadSmsCount > 0) {
                    smsController.openUnreadSummaryTarget()
                } else {
                    smsController.openModule(initialPage = SmsPageIndex.UNREAD)
                }
            }

            HomeInfoAction.USAGE -> {
                if (screenUsageRepository.hasUsageAccess()) {
                    showStatusBarMessage(HomeInfoDetailModel.usageRefreshStarted())
                    refreshScreenUsageSummary(render = true, showFeedback = true)
                } else {
                    showStatusBarMessage(HomeInfoDetailModel.notice(action, state))
                    launchSystemIntent(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
        }
    }

    private fun onHomeInfoDetail(action: HomeInfoAction) {
        showStatusBarMessage(HomeInfoDetailModel.notice(action, state))
    }

    private fun showStatusBarMessage(message: String) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            return
        }
        state = LauncherStateTransitions.updateStatusBarMessage(
            state = state,
            message = normalizedMessage,
        )
        renderCurrentFrame()
        scheduleStatusBarMessageClear()
    }

    private fun openAlarmClock() {
        launchFirstAvailableIntent(
            Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun openBatterySettings() {
        launchFirstAvailableIntent(
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun openNotificationSummaryTarget() {
        val sourceIntent = state.notificationSources.singleOrNull()?.let { source ->
            packageManager.getLaunchIntentForPackage(source.sourceId)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val notificationSettingsIntent = Intent(ACTION_NOTIFICATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val systemSettingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (sourceIntent != null) {
            launchFirstAvailableIntent(sourceIntent, notificationSettingsIntent, systemSettingsIntent)
        } else {
            launchFirstAvailableIntent(notificationSettingsIntent, systemSettingsIntent)
        }
    }

    private fun onHomeNotificationPressed(notificationKey: String) {
        if (NotificationCommandStore.sendContent(notificationKey)) {
            return
        }
        val sourceId = state.notificationItems
            .firstOrNull { item -> item.key == notificationKey }
            ?.sourceId
            ?: NotificationCommandStore.command(notificationKey)?.sourceId
            ?: ""
        val sourceIntent = sourceId.takeIf(String::isNotBlank)?.let { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        if (sourceIntent == null) {
            showStatusBarMessage("OPEN NOTIFY FAILED")
            return
        }
        launchFirstAvailableIntent(sourceIntent)
    }

    private fun onHomeNotificationAction(notificationKey: String, actionIndex: Int) {
        if (!NotificationCommandStore.sendAction(notificationKey, actionIndex)) {
            showStatusBarMessage("NOTIFY ACTION FAILED")
        }
    }

    // End Phase 4 ──────────────────────────────────────────────────────────────

    /** 尝试依次启动多个 Intent，成功即返回。 */
    private fun launchFirstAvailableIntent(vararg intents: Intent) {
        intents.forEach { intent ->
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                return@forEach
            }
        }
    }

    /** 启动一个系统 Intent（catch ActivityNotFoundException 防崩溃）。 */
    private fun launchSystemIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // no-op
        }
    }

    private fun updateScreenProfile(widthPx: Int, heightPx: Int, render: Boolean = true): Boolean {
        val newProfile = LauncherLayoutProfileFactory.create(
            widthPx = widthPx,
            heightPx = heightPx,
            dotSizePx = state.selectedDotSizePx,
            pixelShape = state.selectedPixelShape,
            statusBarHeightPx = currentStatusBarHeightPx(),
        )
        if (newProfile == screenProfile) {
            return false
        }

        screenProfile = newProfile
        state = LauncherStateTransitions.reflowWindow(
            state = state,
            visibleRows = visibleRows(),
        )
        state = LauncherStateTransitions.reflowSettingsWindow(
            state = state,
            visibleRows = settingsVisibleRows(),
        )
        state = LauncherStateTransitions.reflowSmsWindow(
            state = state,
            visibleRows = SettingsMenuLayout.largeVisibleRows(screenProfile, state.fontSelection),
        )
        state = LauncherStateTransitions.reflowSmsThreadWindow(
            state = state,
            visibleRows = SmsLayout.threadVisibleRows(screenProfile, state.fontSelection),
        )
        if (render) {
            renderCurrentFrame()
        }
        return true
    }

    private fun currentStatusBarHeightPx(): Int {
        val insetHeight = ViewCompat.getRootWindowInsets(window.decorView)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
        return insetHeight.takeIf { it > 0 } ?: statusBarHeightResourcePx()
    }

    private fun statusBarHeightResourcePx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private fun visibleRows(): Int = AppListLayout.visibleRows(
        screenProfile = screenProfile,
        fontSelection = state.fontSelection,
    )

    private fun settingsVisibleRows(): Int = SettingsMenuLayout.visibleRows(
        screenProfile = screenProfile,
        fontSelection = state.fontSelection,
    )

    /**
     * 从当前页面打开抽屉，并根据持久化偏好决定是否默认进入搜索态。
     */
    private fun showAppDrawer() {
        settleDrawerMotionBeforeExplicitAction()
        val previousMode = state.mode
        val shouldFocusSearchOnEntry = previousMode != LauncherMode.APP_DRAWER && state.openDrawerInSearchMode
        if (previousMode != LauncherMode.APP_DRAWER) {
            state = LauncherStateTransitions.clearDrawerQuery(
                state = state,
                visibleRows = visibleRows(),
            )
        }
        state = LauncherStateTransitions.prepareDrawerEntryFocus(
            state = LauncherStateTransitions.showAppDrawer(
                state = state,
                visibleRows = visibleRows(),
            ),
            focusSearch = shouldFocusSearchOnEntry,
        )
        if (previousMode != LauncherMode.APP_DRAWER) {
            startAnimationTickerIfNeeded()
        }
        renderCurrentFrame()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun currentDrawerApps(): List<AppEntry> {
        if (state.drawerVisibleApps.isNotEmpty()) {
            return state.drawerVisibleApps
        }
        if (state.drawerQuery.isNotBlank()) {
            return emptyList()
        }
        return state.apps
    }

    private fun onPixelEngineDrawerQueryChanged(query: String) {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return
        }
        val filteredQuery = DrawerAsciiInputSanitizer.filter(query)
        if (filteredQuery == state.drawerQuery && state.isDrawerSearchFocused) {
            return
        }
        recordInteraction()
        settleDrawerMotionBeforeExplicitAction()
        state = LauncherStateTransitions.updateDrawerQuery(
            state = LauncherStateTransitions.focusDrawerSearchInput(state),
            query = filteredQuery,
            visibleRows = visibleRows(),
        )
        renderCurrentFrame()
        DrawerSearchAutoLaunchPolicy.resolve(state)?.let { app ->
            if (launchApp(app)) return
        }
        startAnimationTickerIfNeeded()
    }

    private fun onPixelEngineDrawerSubmitSearch() {
        if (state.mode != LauncherMode.APP_DRAWER || state.drawerQuery.isBlank()) {
            return
        }
        settleDrawerMotionBeforeExplicitAction()
        launchSelectedApp()
    }

    private fun onPixelEngineDrawerAppPressed(index: Int) {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return
        }
        recordInteraction()
        val decision = DrawerContentTapResolver.resolve(state, index)
        when (decision.action) {
            DrawerContentTapAction.LAUNCH_SELECTED -> {
                settleDrawerMotionBeforeExplicitAction()
                launchAppAtIndex(decision.targetIndex ?: state.selectedIndex)
            }

            DrawerContentTapAction.SELECT_INDEX -> {
                val targetIndex = decision.targetIndex ?: return
                settleDrawerMotionBeforeExplicitAction()
                state = LauncherStateTransitions.selectIndex(
                    state = state,
                    index = targetIndex,
                    visibleRows = visibleRows(),
                )
                renderCurrentFrame()
            }

            DrawerContentTapAction.EXIT_SEARCH -> {
                settleDrawerMotionBeforeExplicitAction()
                state = LauncherStateTransitions.exitDrawerSearch(
                    state = state,
                    visibleRows = visibleRows(),
                )
                renderCurrentFrame()
                startAnimationTickerIfNeeded()
                updateDrawerInputFocus()
            }

            DrawerContentTapAction.NONE -> Unit
        }
    }

    private fun onPixelEngineDrawerAppLongPressed(index: Int) {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return
        }
        recordInteraction()
        val selectedApp = currentDrawerApps().getOrNull(index) ?: return
        val appIndex = state.apps.indexOfFirst { app ->
            app.packageName == selectedApp.packageName &&
                app.activityName == selectedApp.activityName
        }.takeIf { it >= 0 } ?: return
        settleDrawerMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showAppActionMenu(state, selectedIndex = appIndex)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun onDrawerAppMenuEdit() {
        if (state.mode != LauncherMode.APP_DRAWER || !state.isAppActionMenuVisible) {
            return
        }
        openAppManagement(selectedIndex = state.appEditorSelectedIndex)
    }

    private fun onDrawerAppMenuRefresh() {
        if (state.mode != LauncherMode.APP_DRAWER || !state.isAppActionMenuVisible) {
            return
        }
        state = LauncherStateTransitions.hideAppActionMenu(state)
        appRepository.clearCachedLaunchableApps()
        loadApps()
    }

    private fun onDrawerAppMenuDismiss() {
        if (!state.isAppActionMenuVisible) {
            return
        }
        state = LauncherStateTransitions.hideAppActionMenu(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun hideDrawerKeyboard() {
        val inputManager = getSystemService(InputMethodManager::class.java) ?: return
        inputManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun updateTextInputFocus() {
        val drawerWantsTextInput = state.mode == LauncherMode.APP_DRAWER && state.isDrawerSearchFocused
        val smsSearchWantsTextInput =
            state.mode == LauncherMode.SMS_THREADS &&
                state.smsPageIndex == SmsPageIndex.ALL
        val smsDraftWantsTextInput =
            state.mode == LauncherMode.SMS_THREAD_DETAIL && !state.smsCurrentIsServiceConversation
        if (!smsSearchWantsTextInput && !smsDraftWantsTextInput && !drawerWantsTextInput) {
            hideDrawerKeyboard()
        }
    }

    private fun updateDrawerInputFocus() {
        updateTextInputFocus()
    }

    /**
     * 把硬件键盘输入映射到抽屉的 ASCII 搜索模型，并处理删除、退出搜索等行为。
     */
    private fun handleDrawerTextInput(keyCode: Int, event: KeyEvent?): Boolean {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                settleDrawerMotionBeforeExplicitAction()
                if (!state.isDrawerSearchFocused) {
                    state = LauncherStateTransitions.focusDrawerSearchInput(state)
                    updateDrawerInputFocus()
                }
                state = LauncherStateTransitions.backspaceDrawerQuery(
                    state = state,
                    visibleRows = visibleRows(),
                )
                renderCurrentFrame()
                return true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                settleDrawerMotionBeforeExplicitAction()
                state = LauncherStateTransitions.exitDrawerSearch(
                    state = state,
                    visibleRows = visibleRows(),
                )
                renderCurrentFrame()
                startAnimationTickerIfNeeded()
                updateDrawerInputFocus()
                return true
            }
        }

        val keyEvent = event ?: return false
        val unicode = keyEvent.unicodeChar
        if (unicode <= 0) {
            return false
        }
        val inputChar = unicode.toChar()
        if (Character.isISOControl(inputChar.code)) {
            return false
        }
        val filteredText = DrawerAsciiInputSanitizer.filter(inputChar.toString())
        if (filteredText.isEmpty()) {
            return true
        }
        settleDrawerMotionBeforeExplicitAction()
        if (!state.isDrawerSearchFocused) {
            state = LauncherStateTransitions.focusDrawerSearchInput(state)
            updateDrawerInputFocus()
        }

        state = LauncherStateTransitions.appendDrawerQuery(
            state = state,
            text = filteredText,
            visibleRows = visibleRows(),
        )
        renderCurrentFrame()
        return true
    }

    private fun openSettingsMenu() {
        settleDrawerMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showSettings(
            state = state,
            visibleRows = settingsVisibleRows(),
        )
        renderCurrentFrame()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun closeSettingsMenu() {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.hideSettings(state)
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    /** 打开低频设置二级页面。 */
    private fun openMoreSettings() {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showMoreSettings(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    /** 关闭低频设置二级页面并返回顶层设置。 */
    private fun closeMoreSettings() {
        state = LauncherStateTransitions.hideMoreSettings(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun openDiagnostics() {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showDiagnostics(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun closeDiagnostics() {
        state = LauncherStateTransitions.hideDiagnostics(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun openDataHealth() {
        settleSettingsMotionBeforeExplicitAction()
        refreshDataHealthState(updateTimestamp = true)
        state = LauncherStateTransitions.showDataHealth(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun closeDataHealth() {
        state = LauncherStateTransitions.hideDataHealth(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun openNotificationSettings() {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showNotificationSettings(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun closeNotificationSettings() {
        state = LauncherStateTransitions.hideNotificationSettings(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun onNotificationSourcePressed(sourceId: String) {
        val rules = notificationSummarySettingsRepository.cycleSource(sourceId)
        state = LauncherStateTransitions.updateNotificationRules(
            state = state,
            mutedSourceIds = rules.mutedSourceIds,
            prioritySourceIds = rules.prioritySourceIds,
        )
        NotificationSummaryStore.updateRules(rules)
        renderCurrentFrame()
    }

    private fun openLoadingPreview() {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showLoadingPreview(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun closeLoadingPreview() {
        state = LauncherStateTransitions.hideLoadingPreview(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun openAppManagement(selectedIndex: Int = state.appEditorSelectedIndex) {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showAppManagement(state, selectedIndex)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun closeAppManagement() {
        state = LauncherStateTransitions.hideAppManagement(state)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun onAppEditorPrevious() {
        state = LauncherStateTransitions.moveAppEditorSelection(state, -1)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun onAppEditorNext() {
        state = LauncherStateTransitions.moveAppEditorSelection(state, 1)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun onAppEditorNameChanged(text: String) {
        state = LauncherStateTransitions.updateAppEditorNameDraft(state, text)
    }

    private fun onAppEditorAliasChanged(text: String) {
        state = LauncherStateTransitions.updateAppEditorAliasDraft(state, text)
    }

    private fun onAppEditorSave() {
        val selectedApp = state.apps.getOrNull(state.appEditorSelectedIndex) ?: return
        val labelOverride = state.appEditorNameDraft.trim().takeIf { it.isNotBlank() && it != selectedApp.systemLabel }
            .orEmpty()
        appCustomizationRepository.saveCustomization(
            app = selectedApp,
            labelOverride = labelOverride,
            aliasText = state.appEditorAliasDraft,
        )
        refreshAppsAfterCustomization(state.appEditorSelectedIndex)
    }

    private fun onAppEditorReset() {
        val selectedApp = state.apps.getOrNull(state.appEditorSelectedIndex) ?: return
        appCustomizationRepository.resetCustomization(selectedApp)
        refreshAppsAfterCustomization(state.appEditorSelectedIndex)
    }

    private fun onAppCacheReset() {
        appRepository.clearCachedLaunchableApps()
        loadApps()
    }

    private fun refreshAppsAfterCustomization(selectedIndex: Int) {
        val baseApps = state.apps.map { app ->
            app.copy(label = app.systemLabel, aliases = emptyList())
        }
        state = LauncherStateTransitions.withApps(
            previous = state,
            apps = applyAppCustomizations(baseApps),
            visibleRows = visibleRows(),
        )
        state = LauncherStateTransitions.showAppManagement(state, selectedIndex)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun onDataHealthItemPressed(item: DataHealthItem) {
        val action = DataHealthRepairActionModel.actionFor(
            item = item,
            postNotificationsRuntimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
        when (action) {
            DataHealthRepairAction.NONE -> Unit

            DataHealthRepairAction.OPEN_USAGE_ACCESS_SETTINGS -> launchSystemIntent(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )

            DataHealthRepairAction.REQUEST_LOCATION_PERMISSION -> requestHomeDataPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

            DataHealthRepairAction.REQUEST_CALL_LOG_PERMISSION -> requestHomeDataPermissions(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
            )

            DataHealthRepairAction.REQUEST_SMS_READ_PERMISSION -> requestHomeDataPermissions(Manifest.permission.READ_SMS)

            DataHealthRepairAction.ENSURE_SMS_ROLE -> smsController.ensureReadAccessAndRole()

            DataHealthRepairAction.REQUEST_POST_NOTIFICATIONS_PERMISSION ->
                requestHomeDataPermissions(Manifest.permission.POST_NOTIFICATIONS)

            DataHealthRepairAction.OPEN_NOTIFICATION_LISTENER_SETTINGS -> launchSystemIntent(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
        refreshDataHealthState(updateTimestamp = true)
        renderCurrentFrame()
    }


    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        Log.d(
            smsIntentLogTag,
            "handleLaunchIntent action=${intent.action} data=${intent.data} extras=${intent.extras?.keySet()?.joinToString()} mode=${state.mode}",
        )
        if (isLauncherHomeIntent(intent)) {
            navigateHomeFromHardwareKey()
            return
        }
        val openThreadId = intent.getLongExtra(EXTRA_OPEN_SMS_THREAD_ID, -1L).takeIf { it >= 0L }
        val openAddress = intent.getStringExtra(EXTRA_OPEN_SMS_ADDRESS).orEmpty()
        val draftBody = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""
        val isSmsSendTo = intent.action == Intent.ACTION_SENDTO &&
            intent.data?.scheme?.lowercase(Locale.US) in setOf("sms", "smsto")
        if (openThreadId != null || openAddress.isNotBlank() || isSmsSendTo) {
            val resolvedAddress = when {
                openAddress.isNotBlank() -> openAddress
                isSmsSendTo -> intent.data?.schemeSpecificPart.orEmpty().substringBefore('?')
                else -> ""
            }
            Log.d(
                smsIntentLogTag,
                "handleLaunchIntent resolved threadId=$openThreadId address=$resolvedAddress isSmsSendTo=$isSmsSendTo draftLength=${draftBody.length}",
            )
            if (resolvedAddress.isNotBlank() || openThreadId != null) {
                smsController.openDeepLinkedThread(
                    threadId = openThreadId,
                    address = resolvedAddress,
                    draft = draftBody,
                )
            }
        }
    }

    private fun isLauncherHomeIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MAIN) {
            return false
        }
        val categories = intent.categories.orEmpty()
        return Intent.CATEGORY_HOME in categories || Intent.CATEGORY_LAUNCHER in categories
    }


    private fun settleSettingsMotionBeforeExplicitAction() = Unit

    private fun stopDrawerVerticalListAnimation(resetOffset: Boolean) = Unit

    private fun resetDrawerVerticalGesture() = Unit

    private fun settleDrawerMotionBeforeExplicitAction() = Unit

    private fun startClockTicker() {
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.post(clockTicker)
    }

    private fun startDecorationTicker() {
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.postDelayed(animationTicker, LauncherAnimationState.frameDelayMs)
    }

    private fun startAnimationTickerIfNeeded() {
        if (shouldRunDecorationTicker()) {
            startDecorationTicker()
        } else {
            mainHandler.removeCallbacks(animationTicker)
        }
    }

    private fun shouldRunDecorationTicker(): Boolean {
        return shouldAnimateHeaderCharge()
    }

    private fun shouldAnimateHeaderCharge(): Boolean {
        if (!state.isCharging) {
            return false
        }
        return when (state.mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
            LauncherMode.MORE_SETTINGS,
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_THREAD_DETAIL,
            LauncherMode.DIALER,
            LauncherMode.CONTACT_DETAIL,
            LauncherMode.CONTACT_EDITOR,
            LauncherMode.SNAKE,
            LauncherMode.APP_MANAGEMENT,
            LauncherMode.DATA_HEALTH,
            LauncherMode.NOTIFICATION_SETTINGS,
            LauncherMode.LOADING_PREVIEW,
            LauncherMode.DIAGNOSTICS -> true

            LauncherMode.IDLE -> false
        }
    }

    private fun scrollSmsDetailBy(deltaPx: Int) = Unit

    private fun applyPixelSizeChange(newDotSizePx: Int) {
        applyPixelAppearancePreview(
            newDotSizePx = newDotSizePx,
            newPixelGapEnabled = state.isPixelGapEnabled,
        )
    }

    private fun applyPixelAppearancePreview(
        newDotSizePx: Int,
        newPixelGapEnabled: Boolean,
    ) {
        val current = state
        if (
            newDotSizePx == current.selectedDotSizePx &&
            newPixelGapEnabled == current.isPixelGapEnabled
        ) {
            return
        }

        val baseline = pendingPixelAppearanceBaseline ?: PixelAppearanceBaseline(
            dotSizePx = current.selectedDotSizePx,
            pixelGapEnabled = current.isPixelGapEnabled,
        ).also { pendingPixelAppearanceBaseline = it }

        applyAppearanceState(
            newPixelShape = current.selectedPixelShape,
            newDotSizePx = newDotSizePx,
            newPixelGapEnabled = newPixelGapEnabled,
            newThemeMode = current.selectedThemeMode,
        )

        if (
            newDotSizePx == baseline.dotSizePx &&
            newPixelGapEnabled == baseline.pixelGapEnabled
        ) {
            clearPendingPixelAppearanceChange()
        } else {
            startPixelChangeConfirmCountdown()
        }
    }

    private fun applyAppearance(
        newPixelShape: PixelShape,
        newDotSizePx: Int,
        newPixelGapEnabled: Boolean,
        newThemeFamily: LauncherThemeFamily = state.selectedThemeFamily,
        newThemeMode: LauncherThemeMode,
        newFontSelection: LauncherFontSelection = state.fontSelection,
    ) {
        persistAppearance(
            pixelShape = newPixelShape,
            dotSizePx = newDotSizePx,
            pixelGapEnabled = newPixelGapEnabled,
            themeFamily = newThemeFamily,
            themeMode = newThemeMode,
            fontSelection = newFontSelection,
        )
        applyAppearanceState(
            newPixelShape = newPixelShape,
            newDotSizePx = newDotSizePx,
            newPixelGapEnabled = newPixelGapEnabled,
            newThemeFamily = newThemeFamily,
            newThemeMode = newThemeMode,
            newFontSelection = newFontSelection,
        )
    }

    /** 后台准备候选字体，成功后才原子更新状态、Host 与持久化设置。 */
    private fun requestFontSelection(candidate: LauncherFontSelection) {
        if (isFontLoading || candidate == state.fontSelection) return
        val activeSelection = state.fontSelection
        isFontLoading = true
        state = LauncherStateTransitions.updateFontLoading(state, isLoading = true)
        mainHandler.removeCallbacks(statusBarMessageClearRunnable)
        state = LauncherStateTransitions.updateStatusBarMessage(state, message = "FONT LOADING")
        renderCurrentFrame()
        launcherFontRepository.prepare(candidate) { result ->
            isFontLoading = false
            state = LauncherStateTransitions.updateFontLoading(state, isLoading = false)
            result.onSuccess { prepared ->
                if (state.fontSelection != activeSelection) return@onSuccess
                activePreparedFont = prepared
                refreshFontCacheSummary()
                val current = state
                applyAppearance(
                    newPixelShape = current.selectedPixelShape,
                    newDotSizePx = current.selectedDotSizePx,
                    newPixelGapEnabled = current.isPixelGapEnabled,
                    newThemeMode = current.selectedThemeMode,
                    newFontSelection = candidate,
                )
                state = LauncherStateTransitions.updateStatusBarMessage(state, message = "")
                renderCurrentFrame()
            }.onFailure { error ->
                Log.e(fontLoadLogTag, "Font switch failed: $candidate", error)
                showStatusBarMessage("FONT LOAD ERROR")
            }
        }
    }

    /** 把 indexed pack 条目数和 KiB 占用写入诊断状态。 */
    private fun refreshFontCacheSummary() {
        val snapshot = launcherFontRepository.cacheSnapshot()
        state = LauncherStateTransitions.updateFontCacheSummary(
            state = state,
            summary = "${snapshot.glyphPackCount}/${snapshot.glyphPackBytes / 1024L}K",
        )
    }

    private fun persistAppearance(
        pixelShape: PixelShape,
        dotSizePx: Int,
        pixelGapEnabled: Boolean,
        themeFamily: LauncherThemeFamily,
        themeMode: LauncherThemeMode,
        fontSelection: LauncherFontSelection = state.fontSelection,
    ) {
        fontSettingsRepository.setAppearanceSettings(
            pixelShape = pixelShape,
            dotSizePx = dotSizePx,
            pixelGapEnabled = pixelGapEnabled,
            themeFamily = themeFamily,
            themeMode = themeMode,
            fontSelection = fontSelection,
        )
    }

    private fun applyAppearanceState(
        newPixelShape: PixelShape,
        newDotSizePx: Int,
        newPixelGapEnabled: Boolean,
        newThemeFamily: LauncherThemeFamily = state.selectedThemeFamily,
        newThemeMode: LauncherThemeMode,
        newFontSelection: LauncherFontSelection = state.fontSelection,
        render: Boolean = true,
    ) {
        selectedThemeFamily = newThemeFamily
        selectedThemeMode = newThemeMode
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedPixelShape = newPixelShape,
            selectedDotSizePx = newDotSizePx,
            isPixelGapEnabled = newPixelGapEnabled,
            selectedThemeFamily = newThemeFamily,
            selectedThemeMode = newThemeMode,
            fontSelection = newFontSelection,
        )

        val widthPx = launcherRootHost.rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val heightPx = launcherRootHost.rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val screenProfileChanged = updateScreenProfile(widthPx, heightPx, render = render)
        refreshDerivedUiState(render = render && !screenProfileChanged)
    }

    private fun startPixelChangeConfirmCountdown() {
        pendingPixelConfirmDeadlineUptimeMs = SystemClock.uptimeMillis() + pixelAppearanceConfirmTimeoutMs
        mainHandler.removeCallbacks(statusBarMessageClearRunnable)
        mainHandler.removeCallbacks(pixelChangeConfirmTicker)
        updatePixelChangeConfirmStatusBar()
    }

    private fun updatePixelChangeConfirmStatusBar() {
        if (pendingPixelAppearanceBaseline == null) {
            return
        }
        val remainingMs = pendingPixelConfirmDeadlineUptimeMs - SystemClock.uptimeMillis()
        if (remainingMs <= 0L) {
            restorePendingPixelAppearanceChange()
            return
        }
        val remainingSeconds = ((remainingMs + 999L) / 1_000L).coerceIn(1L, 5L)
        state = LauncherStateTransitions.updateStatusBarAction(
            state = state,
            leadingText = "${remainingSeconds}s",
            actionLabel = "CONFIRM",
            isDanger = true,
        )
        renderCurrentFrame()
        mainHandler.postDelayed(pixelChangeConfirmTicker, 1_000L)
    }

    private fun confirmPendingPixelAppearanceChange() {
        if (pendingPixelAppearanceBaseline == null) {
            return
        }
        val confirmedState = state
        persistAppearance(
            pixelShape = confirmedState.selectedPixelShape,
            dotSizePx = confirmedState.selectedDotSizePx,
            pixelGapEnabled = confirmedState.isPixelGapEnabled,
            themeFamily = confirmedState.selectedThemeFamily,
            themeMode = confirmedState.selectedThemeMode,
            fontSelection = confirmedState.fontSelection,
        )
        clearPendingPixelAppearanceChange()
    }

    private fun restorePendingPixelAppearanceChange(render: Boolean = true) {
        val baseline = pendingPixelAppearanceBaseline ?: return
        clearPendingPixelAppearanceChange(render = false)
        applyAppearanceState(
            newPixelShape = state.selectedPixelShape,
            newDotSizePx = baseline.dotSizePx,
            newPixelGapEnabled = baseline.pixelGapEnabled,
            newThemeMode = state.selectedThemeMode,
            render = render,
        )
        state = LauncherStateTransitions.updateStatusBarMessage(state, message = "")
        if (render) {
            renderCurrentFrame()
        }
    }

    private fun clearPendingPixelAppearanceChange(render: Boolean = true) {
        mainHandler.removeCallbacks(pixelChangeConfirmTicker)
        pendingPixelAppearanceBaseline = null
        pendingPixelConfirmDeadlineUptimeMs = 0L
        state = LauncherStateTransitions.updateStatusBarMessage(state, message = "")
        if (render) {
            renderCurrentFrame()
        }
    }

    private fun applyUiBehavior(
        drawerListAlignment: DrawerListAlignment = state.drawerListAlignment,
        isIdlePageEnabled: Boolean = state.isIdlePageEnabled,
        chargeAutoIdleEnabled: Boolean = state.chargeAutoIdleEnabled,
        inactivityAutoIdleEnabled: Boolean = state.inactivityAutoIdleEnabled,
        idleTimeoutSeconds: Int = state.idleTimeoutSeconds,
        openDrawerInSearchMode: Boolean = state.openDrawerInSearchMode,
        chargeIdleEffect: ChargeIdleEffect = state.chargeIdleEffect,
        isPixelMatterEffectEnabled: Boolean = state.isPixelMatterEffectEnabled,
        pixelMatterEffectMode: PixelMatterEffectMode = state.pixelMatterEffectMode,
        isPixelMatterHandControlEnabled: Boolean = state.isPixelMatterHandControlEnabled,
        isPixelMatterHandDebugEnabled: Boolean = state.isPixelMatterHandDebugEnabled,
    ) {
        fontSettingsRepository.setUiBehaviorSettings(
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            chargeAutoIdleEnabled = chargeAutoIdleEnabled,
            inactivityAutoIdleEnabled = inactivityAutoIdleEnabled,
            idleTimeoutSeconds = idleTimeoutSeconds,
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
            pixelMatterEffectEnabled = isPixelMatterEffectEnabled,
            pixelMatterEffectMode = pixelMatterEffectMode,
            pixelMatterHandControlEnabled = isPixelMatterHandControlEnabled,
            pixelMatterHandDebugEnabled = isPixelMatterHandDebugEnabled,
        )
        state = LauncherStateTransitions.updateUiBehavior(
            state = state,
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            chargeAutoIdleEnabled = chargeAutoIdleEnabled,
            inactivityAutoIdleEnabled = inactivityAutoIdleEnabled,
            idleTimeoutSeconds = idleTimeoutSeconds,
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
            isPixelMatterEffectEnabled = isPixelMatterEffectEnabled,
            pixelMatterEffectMode = pixelMatterEffectMode,
            isPixelMatterHandControlEnabled = isPixelMatterHandControlEnabled,
            isPixelMatterHandDebugEnabled = isPixelMatterHandDebugEnabled,
        )
        syncPixelMatterMotionListening()
        syncPixelMatterHandTracking()
        syncPixelMatterHandDebugOverlay()
        if (!isIdlePageEnabled && state.mode == LauncherMode.IDLE) {
            wakeFromIdle()
            return
        }
        if (IdleAutoEntryPolicy.shouldEnterForCurrentCharging(state, launchPending) && enterIdleIfAllowed()) {
            updateDrawerInputFocus()
            scheduleIdleCheck()
            return
        }
        refreshDerivedUiState(render = true)
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun onDeviceStatusChanged(deviceStatus: DeviceStatus) {
        val wasCharging = state.isCharging
        state = LauncherStateTransitions.updateDeviceStatus(state, deviceStatus)
        refreshDerivedUiState(render = true)
        startAnimationTickerIfNeeded()
        if (IdleAutoEntryPolicy.shouldEnterForCharging(
                wasCharging = wasCharging,
                isCharging = deviceStatus.isCharging,
                state = state,
                launchPending = launchPending,
            )
        ) {
            enterIdleIfAllowed()
        } else {
            scheduleIdleCheck()
        }
    }

    private fun onNextAlarmChanged(nextAlarmText: String) {
        state = LauncherStateTransitions.updateNextAlarmText(
            state = state,
            nextAlarmText = nextAlarmText,
        )
        refreshDerivedUiState(render = true)
    }

    private fun onCommunicationStatusChanged(communicationStatus: CommunicationStatus) {
        state = LauncherStateTransitions.updateCommunicationStatus(
            state = state,
            missedCallCount = communicationStatus.missedCallCount,
            unreadSmsCount = communicationStatus.unreadSmsCount,
        )
        refreshDerivedUiState(render = true)
    }

    private fun onNotificationSummaryChanged(notificationSummary: NotificationSummary) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onNotificationSummaryChanged(notificationSummary) }
            return
        }
        if (isDestroyed || isFinishing) {
            return
        }
        state = LauncherStateTransitions.updateNotificationSummary(
            state = state,
            notificationSummaryText = notificationSummary.text,
            notificationCount = notificationSummary.count,
            notificationSources = notificationSummary.sources,
            notificationItems = notificationSummary.items,
        )
        renderCurrentFrame()
    }

    private fun onMediaPlaybackChanged(mediaPlayback: MediaPlaybackSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onMediaPlaybackChanged(mediaPlayback) }
            return
        }
        if (isDestroyed || isFinishing) {
            return
        }
        state = LauncherStateTransitions.updateMediaPlayback(
            state = state,
            mediaPlayback = mediaPlayback,
        )
        renderCurrentFrame()
    }

    private fun refreshScreenUsageSummary(
        render: Boolean,
        showFeedback: Boolean = false,
    ) {
        runInBackground {
            val snapshot = screenUsageRepository.readTodaySummary()
            mainHandler.post {
                if (isDestroyed || isFinishing) {
                    return@post
                }
                state = LauncherStateTransitions.updateScreenUsageSummary(
                    state = state,
                    screenUsageTimeText = snapshot.usageTimeText,
                    screenOpenCountText = snapshot.openCountText,
                )
                if (showFeedback) {
                    refreshDataHealthState()
                    showStatusBarMessage(HomeInfoDetailModel.usageRefreshResult(state))
                } else if (render) {
                    refreshDerivedUiState(render = true)
                } else {
                    renderCurrentFrame()
                }
            }
        }
    }

    /**
     * 提交后台任务。onDestroy 里 backgroundExecutor.shutdownNow() 之后，
     * 迟到的异步回调（如短信标记已读完成后回调本类刷新计数）再提交任务会抛
     * RejectedExecutionException；此时结果已无处落地，静默丢弃而不是杀掉进程。
     */
    private fun runInBackground(task: () -> Unit) {
        try {
            backgroundExecutor.execute { task() }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun refreshCommunicationStatus(render: Boolean) {
        runInBackground {
            val communicationStatus = communicationStatusRepository.readStatus()
            mainHandler.post {
                if (isDestroyed || isFinishing) {
                    return@post
                }
                state = LauncherStateTransitions.updateCommunicationStatus(
                    state = state,
                    missedCallCount = communicationStatus.missedCallCount,
                    unreadSmsCount = communicationStatus.unreadSmsCount,
                )
                if (render) {
                    refreshDerivedUiState(render = true)
                } else {
                    renderCurrentFrame()
                }
            }
        }
    }

    private fun refreshRainHint(
        force: Boolean,
        render: Boolean,
        showFeedback: Boolean = false,
    ) {
        if (!force) {
            val elapsedSinceLastRefresh = SystemClock.elapsedRealtime() - lastRainRefreshElapsedRealtimeMs
            if (lastRainRefreshElapsedRealtimeMs > 0L && elapsedSinceLastRefresh < rainRefreshIntervalMs) {
                return
            }
        }
        if (rainRefreshInFlight) {
            if (showFeedback) {
                showStatusBarMessage(HomeInfoDetailModel.rainRefreshStarted())
            }
            return
        }
        if (!deviceLocationRepository.hasLocationPermission()) {
            lastRainRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
            applyRainHintText(rainLocationPromptText, render = render)
            if (showFeedback) {
                showStatusBarMessage(HomeInfoDetailModel.notice(HomeInfoAction.RAIN, state))
                requestHomeDataPermissions(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            }
            return
        }

        rainRefreshInFlight = true
        deviceLocationRepository.requestBestLocation { location ->
            if (isDestroyed || isFinishing) {
                rainRefreshInFlight = false
                return@requestBestLocation
            }
            if (location == null) {
                lastRainRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
                rainRefreshInFlight = false
                applyRainHintText(rainLocationPromptText, render = render)
                if (showFeedback) {
                    showStatusBarMessage(HomeInfoDetailModel.rainLocationUnavailable())
                }
                return@requestBestLocation
            }

            val nowElapsedRealtime = SystemClock.elapsedRealtime()
            val shouldFetch = force ||
                lastRainRefreshElapsedRealtimeMs <= 0L ||
                (nowElapsedRealtime - lastRainRefreshElapsedRealtimeMs) >= rainRefreshIntervalMs ||
                lastRainLocation?.distanceToMeters(location)?.let { it >= rainRefreshDistanceThresholdMeters } != false
            if (!shouldFetch) {
                rainRefreshInFlight = false
                if (showFeedback) {
                    showStatusBarMessage(HomeInfoDetailModel.rainRefreshUpdated(state))
                }
                return@requestBestLocation
            }

            runInBackground {
                val previousSuccessfulHint = lastSuccessfulRainHintText
                runCatching {
                    rainForecastRepository.fetchWeatherSummary(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    )
                }.onSuccess { weatherSummary ->
                    mainHandler.post {
                        if (isDestroyed || isFinishing) {
                            rainRefreshInFlight = false
                            return@post
                        }
                        lastRainRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
                        lastRainLocation = location
                        lastSuccessfulRainHintText = weatherSummary.orEmpty()
                        rainRefreshInFlight = false
                        applyRainHintText(weatherSummary.orEmpty(), render = render)
                        if (showFeedback) {
                            showStatusBarMessage(HomeInfoDetailModel.rainRefreshUpdated(state))
                        }
                    }
                }.onFailure {
                    mainHandler.post {
                        if (isDestroyed || isFinishing) {
                            rainRefreshInFlight = false
                            return@post
                        }
                        lastRainRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
                        rainRefreshInFlight = false
                        applyRainHintText(previousSuccessfulHint, render = render)
                        if (showFeedback) {
                            showStatusBarMessage(
                                HomeInfoDetailModel.rainRefreshFailed(
                                    hasPreviousHint = previousSuccessfulHint.isNotBlank(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyRainHintText(rainHintText: String, render: Boolean) {
        state = LauncherStateTransitions.updateRainHintText(
            state = state,
            rainHintText = rainHintText,
            rainUpdatedTimeText = timeTextProvider.currentTimeText(),
        )
        if (render) {
            refreshDerivedUiState(render = true)
        } else {
            renderCurrentFrame()
        }
    }

    private fun refreshDataHealthState(updateTimestamp: Boolean = false) {
        state = LauncherStateTransitions.updateDataHealth(
            state = state,
            hasUsageAccess = screenUsageRepository.hasUsageAccess(),
            hasLocationPermission = deviceLocationRepository.hasLocationPermission(),
            hasCallLogPermission = communicationStatusRepository.hasCallLogPermission(),
            hasSmsReadPermission = communicationStatusRepository.hasSmsPermission(),
            hasPostNotificationPermission = hasPostNotificationPermission(),
            hasNotificationListenerAccess = hasNotificationListenerAccess(),
            dataHealthUpdatedTimeText = if (updateTimestamp) {
                timeTextProvider.currentTimeText()
            } else {
                state.dataHealthUpdatedTimeText
            },
        )
    }

    private fun hasPostNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabledListeners
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == packageName }
    }

    /** 用户从 DATA HEALTH 或具体功能入口主动申请一组 Home 数据权限。 */
    private fun requestHomeDataPermissions(vararg permissions: String) {
        val missingPermissions = permissions
            .filter { permission -> checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED }
            .distinct()
        if (missingPermissions.isEmpty()) {
            return
        }
        requestPermissions(
            missingPermissions.toTypedArray(),
            homeDataPermissionRequestCode,
        )
    }

    private fun refreshDerivedUiState(render: Boolean) {
        refreshDataHealthState()
        if (render) {
            renderCurrentFrame()
        }
    }

    private fun recordInteraction() {
        state = LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis())
        scheduleIdleCheck()
    }

    private fun wakeIfIdle(): Boolean {
        if (state.mode != LauncherMode.IDLE) {
            return false
        }
        wakeFromIdle()
        return true
    }

    private fun wakeFromIdle() {
        state = LauncherStateTransitions.hideIdle(
            LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis()),
        )
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun scheduleIdleCheck() {
        mainHandler.removeCallbacks(idleRunnable)
        val delay = IdleAutoEntryPolicy.nextInactivityDelayMs(
            state = state,
            nowUptimeMs = SystemClock.uptimeMillis(),
            launchPending = launchPending,
        )
        if (delay != null) {
            mainHandler.postDelayed(idleRunnable, delay)
        }
    }

    private fun scheduleStatusBarMessageClear() {
        mainHandler.removeCallbacks(statusBarMessageClearRunnable)
        mainHandler.postDelayed(statusBarMessageClearRunnable, statusBarMessageTimeoutMs)
    }

    private fun enterIdleIfAllowed(): Boolean {
        val nextState = LauncherStateTransitions.showIdle(state)
        if (nextState == state) {
            return false
        }
        state = nextState
        renderCurrentFrame()
        return true
    }

    @Suppress("DEPRECATION")
    private fun suppressActivityAnimations() {
        overridePendingTransition(0, 0)
    }

    companion object {
        const val LOW_BATTERY_THRESHOLD = 15
        // 权限/角色请求码必须两两不同：onRequestPermissionsResult 用 when 按顺序匹配，
        // 撞号会让后面的分支永久不可达，并把回调派发给错误的模块。
        // 编译器不会对重复的 const 值报错，由 MainActivityRequestCodeContractTest 守。
        const val homeDataPermissionRequestCode = 1001
        const val smsPermissionRequestCode = 1002
        const val smsRoleRequestCode = 1003
        const val cameraPermissionRequestCode = 1004
        const val callPermissionRequestCode = 1005
        const val rainRefreshIntervalMs: Long = 30 * 60 * 1000L
        const val rainRefreshDistanceThresholdMeters = 1_000f
        const val rainLocationPromptText = "LOC"
        const val statusBarMessageTimeoutMs: Long = 2_500L
        const val pixelAppearanceConfirmTimeoutMs: Long = 5_000L
        const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
        const val EXTRA_OPEN_SMS_THREAD_ID = "open_sms_thread_id"
        const val EXTRA_OPEN_SMS_ADDRESS = "open_sms_address"
        const val smsIntentLogTag = "SmsIntent"
        /** 字体冷启动和切换失败使用的日志标签。 */
        const val fontLoadLogTag = "LauncherFont"
    }
}

private data class PixelAppearanceBaseline(
    val dotSizePx: Int,
    val pixelGapEnabled: Boolean,
)
