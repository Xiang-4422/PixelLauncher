package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
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
import android.provider.CallLog
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
import com.purride.pixellauncherv2.data.AiSettingsRepository
import com.purride.pixellauncherv2.data.AppCustomizationRepository
import com.purride.pixellauncherv2.data.AppRepository
import com.purride.pixellauncherv2.data.CommunicationStatus
import com.purride.pixellauncherv2.data.CommunicationStatusRepository
import com.purride.pixellauncherv2.data.DeviceLocationRepository
import com.purride.pixellauncherv2.data.DeviceMotionRepository
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.data.GeoPoint
import com.purride.pixellauncherv2.data.LauncherStatsRepository
import com.purride.pixellauncherv2.data.MediaPlaybackRepository
import com.purride.pixellauncherv2.data.NextAlarmRepository
import com.purride.pixellauncherv2.data.NotificationCommandStore
import com.purride.pixellauncherv2.data.NotificationSummaryRepository
import com.purride.pixellauncherv2.data.NotificationSummarySettingsRepository
import com.purride.pixellauncherv2.data.NotificationSummaryStore
import com.purride.pixellauncherv2.data.PackageManagerAppRepository
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
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.LauncherCallbacks
import com.purride.pixellauncherv2.launcher.LauncherRootHost
import com.purride.pixellauncherv2.launcher.MediaPlaybackSnapshot
import com.purride.pixellauncherv2.launcher.NotificationSummary
import com.purride.pixellauncherv2.launcher.PixelDustShakeDetector
import com.purride.pixellauncherv2.launcher.SmsLayout
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.render.LauncherAnimationState
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.system.AndroidAppLauncher
import com.purride.pixellauncherv2.system.ScreenGravityMapper
import com.purride.pixellauncherv2.system.WindowModeController
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.util.ThrottleClickHelper
import com.purride.pixellauncherv2.util.TimeTextProvider
import com.purride.pixellauncherv2.viewmodel.LauncherViewModel
import com.purride.pixellauncherv2.viewmodel.toLauncherUiState
import com.purride.pixelui.PixelHapticType
import androidx.lifecycle.ViewModelProvider
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 启动器运行时的总编排入口。
 *
 * 持有各类仓库，把 Android 输入转换成 [LauncherState] 变化，驱动充电动画 ticker，
 * 并通过 [LauncherRootHost] 把最新状态提交给 pixel-engine 渲染。
 */
class MainActivity : AppCompatActivity() {

    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeTextProvider = TimeTextProvider()
    private val throttleClickHelper = ThrottleClickHelper()

    private lateinit var launcherViewModel: LauncherViewModel

    // Phase 8: unified root host (replaces Phases 3–7 individual hosts)
    private lateinit var launcherRootHost: LauncherRootHost

    private lateinit var appRepository: AppRepository
    private lateinit var appCustomizationRepository: AppCustomizationRepository
    private lateinit var aiSettingsRepository: AiSettingsRepository
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
    private lateinit var smsController: SmsController
    private lateinit var rainForecastRepository: RainForecastRepository
    private lateinit var appLauncher: AndroidAppLauncher
    private lateinit var windowModeController: WindowModeController
    private var screenProfile: ScreenProfile = ScreenProfileFactory.create(widthPx = 1, heightPx = 1)
    private var selectedTheme: PixelTheme = PixelTheme.DAY
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
    private var usageAccessPromptShown = false
    private var homeDataPermissionPromptShown = false
    private var rainRefreshInFlight = false
    private var lastRainRefreshElapsedRealtimeMs: Long = 0L
    private var lastRainLocation: GeoPoint? = null
    private var lastSuccessfulRainHintText: String = ""
    private val pixelDustShakeDetector = PixelDustShakeDetector()
    private var pixelDustMotionListening = false
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
            SmsLayout.threadVisibleRows(screenProfile)

        override fun smsInboxVisibleRows(): Int =
            SettingsMenuLayout.largeVisibleRows(screenProfile)

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

        appRepository = PackageManagerAppRepository(applicationContext)
        appCustomizationRepository = AppCustomizationRepository(applicationContext)
        aiSettingsRepository = AiSettingsRepository(applicationContext)
        fontSettingsRepository = FontSettingsRepository(applicationContext)
        launcherStatsRepository = LauncherStatsRepository(applicationContext)
        deviceStatusRepository = DeviceStatusRepository(applicationContext)
        nextAlarmRepository = NextAlarmRepository(applicationContext)
        screenUsageRepository = ScreenUsageRepository(applicationContext)
        communicationStatusRepository = CommunicationStatusRepository(applicationContext)
        notificationSummaryRepository = NotificationSummaryRepository()
        notificationSummarySettingsRepository = NotificationSummarySettingsRepository(applicationContext)
        mediaPlaybackRepository = MediaPlaybackRepository(
            context = applicationContext,
            notificationListener = ComponentName(applicationContext, LauncherNotificationListenerService::class.java),
            mainHandler = mainHandler,
        )
        smsController = SmsController(
            context = applicationContext,
            backgroundExecutor = backgroundExecutor,
            mainHandler = mainHandler,
            host = smsHost,
        )
        deviceLocationRepository = DeviceLocationRepository(applicationContext)
        deviceMotionRepository = DeviceMotionRepository(applicationContext)
        rainForecastRepository = RainForecastRepository()
        val appearanceSettings = fontSettingsRepository.getAppearanceSettings()
        val uiBehaviorSettings = fontSettingsRepository.getUiBehaviorSettings()
        val aiSettings = aiSettingsRepository.getSettings()
        selectedTheme = appearanceSettings.theme
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedPixelShape = appearanceSettings.pixelShape,
            selectedDotSizePx = appearanceSettings.dotSizePx,
            isPixelGapEnabled = appearanceSettings.pixelGapEnabled,
            selectedTheme = appearanceSettings.theme,
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
            isPixelDustEasterEggEnabled = uiBehaviorSettings.pixelDustEasterEggEnabled,
        )
        state = LauncherStateTransitions.updateAiSettings(
            state = state,
            deepSeekApiKey = aiSettings.deepSeekApiKey,
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
        screenProfile = ScreenProfileFactory.create(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            dotSizePx = appearanceSettings.dotSizePx,
            pixelShape = appearanceSettings.pixelShape,
            statusBarHeightPx = currentStatusBarHeightPx(),
        )
        // Phase 8: unified root host (single host for all 9 modes)
        launcherRootHost = LauncherRootHost(
            context = this,
            callbacks = LauncherCallbacks(
                onOpenCall           = ::onHomeOpenCall,
                onOpenSms            = ::onHomeOpenSms,
                onHomeInfoAction     = ::onHomeInfoAction,
                onHomeInfoDetail     = ::onHomeInfoDetail,
                onMediaOpenPlayer    = ::onMediaOpenPlayer,
                onMediaToggleFavorite = ::onMediaToggleFavorite,
                onMediaTogglePlayPause = ::onMediaTogglePlayPause,
                onMediaSkipPrevious  = ::onMediaSkipPrevious,
                onMediaSkipNext      = ::onMediaSkipNext,
                onMediaSeek          = ::onMediaSeek,
                onHomeNotificationPressed = ::onHomeNotificationPressed,
                onHomeNotificationAction = ::onHomeNotificationAction,
                onDrawerQueryChanged = ::onPixelEngineDrawerQueryChanged,
                onDrawerSubmitSearch = ::onPixelEngineDrawerSubmitSearch,
                onDrawerAppPressed   = ::onPixelEngineDrawerAppPressed,
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
                onDeepSeekApiKeyChanged = ::onDeepSeekApiKeyChanged,
                onRequestSmsRole     = smsController::requestDefaultRole,
                onOpenThread         = smsController::openThread,
                onSmsPageSelected    = smsController::selectPage,
                onSelectSmsIndex     = smsController::selectIndex,
                onMarkSmsRead        = smsController::markAllRead,
                onMarkUnreadMessageRead = smsController::markMessageRead,
                onDraftChanged       = smsController::draftChanged,
                onSmsThreadSearchChanged = smsController::threadSearchChanged,
                onSendDraft          = smsController::sendDraft,
                onSmsMessagePressed  = smsController::copyMessageCodeOrBody,
                onMainPageChanged    = ::onMainPageChanged,
                onMainPageDragStart  = ::onMainPageDragStart,
            ),
        )
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // Phase 8: unified root host (single engine overlay for all modes)
            addView(
                launcherRootHost.rootView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(rootContainer)
        updateTextInputFocus()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::launcherRootHost.isInitialized && launcherRootHost.handlePixelDustBack()) {
                    return
                }
                when (state.mode) {
                    LauncherMode.SETTINGS -> closeSettingsMenu()
                    LauncherMode.SMS_ROLE_PROMPT -> smsController.closeModule()
                    LauncherMode.SMS_THREADS -> smsController.closeModule()
                    LauncherMode.SMS_THREAD_DETAIL -> smsController.closeThreadDetail()
                    LauncherMode.SMS_INBOX -> smsController.closeUnreadInbox()
                    LauncherMode.APP_MANAGEMENT -> closeAppManagement()
                    LauncherMode.DATA_HEALTH -> closeDataHealth()
                    LauncherMode.NOTIFICATION_SETTINGS -> closeNotificationSettings()
                    LauncherMode.AI_SETTINGS -> closeAiSettings()
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
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

        renderCurrentFrame()
        handleLaunchIntent(intent)
        suppressActivityAnimations()
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
        if (
            state.mode != LauncherMode.SMS_ROLE_PROMPT &&
            state.mode != LauncherMode.SMS_THREADS &&
            state.mode != LauncherMode.SMS_THREAD_DETAIL &&
            state.mode != LauncherMode.SMS_INBOX
        ) {
            state = LauncherStateTransitions.showHome(state)
        }
        state = LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis())
        refreshDerivedUiState(render = false)
        val launchedUsageAccessSettings = maybeRequestUsageAccess()
        if (!launchedUsageAccessSettings) {
            communicationStatusRepository.start(::onCommunicationStatusChanged)
            smsController.start()
            refreshScreenUsageSummary(render = false)
            smsController.refreshSmsCapability(render = false)
            refreshRainHint(force = true, render = false)
            maybeRequestHomeDataPermissions()
        }
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        syncPixelDustMotionListening()
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
        syncPixelDustMotionListening()
        if (::launcherRootHost.isInitialized) {
            launcherRootHost.stopPixelDustEffect()
        }
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
        communicationStatusRepository.stop()
        smsController.stop()
        suppressActivityAnimations()
        super.onPause()
    }

    private fun syncPixelDustMotionListening() {
        if (!::deviceMotionRepository.isInitialized || !::launcherRootHost.isInitialized) {
            return
        }
        if (!state.isPixelDustEasterEggEnabled) {
            launcherRootHost.stopPixelDustEffect()
            stopPixelDustMotionListening()
            return
        }
        if (activityResumed) {
            startPixelDustMotionListening()
        } else {
            stopPixelDustMotionListening()
        }
    }

    private fun startPixelDustMotionListening() {
        if (pixelDustMotionListening) return
        pixelDustMotionListening = true
        pixelDustShakeDetector.reset()
        deviceMotionRepository.start(::onDeviceMotionChanged)
    }

    private fun stopPixelDustMotionListening() {
        if (!pixelDustMotionListening) return
        deviceMotionRepository.stop()
        pixelDustMotionListening = false
        pixelDustShakeDetector.reset()
    }

    private fun onDeviceMotionChanged(snapshot: DeviceMotionSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handleDeviceMotionChanged(snapshot.withCurrentScreenAxes()) }
        } else {
            handleDeviceMotionChanged(snapshot.withCurrentScreenAxes())
        }
    }

    private fun handleDeviceMotionChanged(snapshot: DeviceMotionSnapshot) {
        if (!state.isPixelDustEasterEggEnabled || !::launcherRootHost.isInitialized) {
            return
        }
        launcherRootHost.updatePixelDustMotion(snapshot)
        if (pixelDustShakeDetector.record(snapshot) && launcherRootHost.triggerPixelDust(snapshot)) {
            launcherRootHost.setup.hostView.hostBridge?.performHapticFeedback(PixelHapticType.TAP)
        }
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
        stopPixelDustMotionListening()
        if (::launcherRootHost.isInitialized) {
            launcherRootHost.stopPixelDustEffect()
        }
        mainHandler.removeCallbacksAndMessages(null)
        backgroundExecutor.shutdownNow()
        super.onDestroy()
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
                    LauncherMode.SMS_INBOX -> {
                        smsController.moveInboxSelection(-1)
                    }
                    LauncherMode.SETTINGS -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.AI_SETTINGS,
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
                    LauncherMode.SMS_INBOX -> {
                        smsController.moveInboxSelection(1)
                    }
                    LauncherMode.SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.AI_SETTINGS,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE,
                    LauncherMode.SMS_ROLE_PROMPT -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
                    LauncherMode.SMS_ROLE_PROMPT,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_THREADS -> smsController.selectPage(SmsPageIndex.UNREAD)
                    LauncherMode.SMS_INBOX -> smsController.moveInboxSelection(-1)
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.AI_SETTINGS,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
                    LauncherMode.SMS_ROLE_PROMPT,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_THREADS -> smsController.selectPage(SmsPageIndex.ALL)
                    LauncherMode.SMS_INBOX -> smsController.moveInboxSelection(1)
                    LauncherMode.APP_DRAWER -> Unit
                    LauncherMode.APP_MANAGEMENT,
                    LauncherMode.DATA_HEALTH,
                    LauncherMode.NOTIFICATION_SETTINGS,
                    LauncherMode.AI_SETTINGS,
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> Unit
                    LauncherMode.SMS_ROLE_PROMPT -> smsController.ensureReadAccessAndRole()
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        if (state.smsPageIndex == SmsPageIndex.UNREAD) {
                            smsController.openSelectedUnreadThread()
                        } else {
                            smsController.openSelectedThread()
                        }
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> {
                        if (state.smsDraftText.isBlank()) {
                            Unit // engine TextField handles SMS draft focus
                        } else {
                            smsController.sendDraft()
                        }
                    }
                    LauncherMode.SMS_INBOX -> {
                        settleSettingsMotionBeforeExplicitAction()
                        smsController.openSelectedUnreadThread()
                    }
                    LauncherMode.DATA_HEALTH -> closeDataHealth()
                    LauncherMode.NOTIFICATION_SETTINGS -> closeNotificationSettings()
                    LauncherMode.AI_SETTINGS -> closeAiSettings()
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
            state = state.copy(isLoading = true)
            renderCurrentFrame()
        }

        backgroundExecutor.execute {
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
            theme           = LauncherThemes.from(applicationContext, uiState.selectedTheme.resolve(isSystemInDarkMode())),
            screenProfile   = screenProfile,
            chargeTick      = animationState.headerChargeTick,
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
        val exclusionHeightPx = (
            screenProfile.dotSizePx * MEDIA_BOTTOM_BAR_GESTURE_EXCLUSION_LOGICAL_HEIGHT
        ).coerceAtLeast(1)
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
                LauncherStateTransitions.showAppDrawer(
                    state = drawerState,
                    visibleRows = visibleRows(),
                ).copy(
                    isDrawerSearchFocused = shouldFocusSearchOnEntry,
                    isDrawerRailSliding = false,
                )
            }
            LauncherMode.SETTINGS -> LauncherStateTransitions.showSettings(
                state = state,
                visibleRows = settingsVisibleRows(),
            )
            else -> state.copy(mode = mode)
        }
        renderCurrentFrame()
        updateTextInputFocus()
        scheduleIdleCheck()
    }

    private fun onMainPageDragStart() {
        if (!state.isDrawerSearchFocused && !state.isAppActionMenuVisible) {
            return
        }
        state = state.copy(
            isDrawerSearchFocused = false,
            isAppActionMenuVisible = false,
        )
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
                    newTheme = current.selectedTheme,
                )
            }
            SettingsMenuItem.THEME -> {
                restorePendingPixelAppearanceChange(render = false)
                val current = state
                applyAppearance(
                    newPixelShape = current.selectedPixelShape,
                    newDotSizePx = current.selectedDotSizePx,
                    newPixelGapEnabled = current.isPixelGapEnabled,
                    newTheme = SettingsMenuModel.nextTheme(current.selectedTheme, direction),
                )
            }
            SettingsMenuItem.HOME_STATUS -> {
                state = LauncherStateTransitions.showHome(state)
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
            SettingsMenuItem.PIXEL_DUST_EASTER_EGG -> applyUiBehavior(
                isPixelDustEasterEggEnabled = SettingsMenuModel.toggle(s.isPixelDustEasterEggEnabled),
            )
            SettingsMenuItem.APP_MANAGEMENT -> openAppManagement()
            SettingsMenuItem.NOTIFICATIONS -> openNotificationSettings()
            SettingsMenuItem.DATA_HEALTH -> openDataHealth()
            SettingsMenuItem.DEEPSEEK_API_KEY -> openAiSettings()
            SettingsMenuItem.ADVANCED -> openDiagnostics()
        }
    }

    private fun onStatusBarAction() {
        if (pendingPixelAppearanceBaseline != null) {
            confirmPendingPixelAppearanceChange()
        }
    }

    // ── HOME callbacks (called from LauncherCallbacks) ────────────────────────

    /** CALL 按钮：打开通话记录。 */
    private fun onHomeOpenCall() {
        openCallLog()
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

            HomeInfoAction.CALL -> openCallLog()

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

    private fun openCallLog() {
        launchFirstAvailableIntent(
            Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
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
        val newProfile = ScreenProfileFactory.create(
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
            visibleRows = SettingsMenuLayout.largeVisibleRows(screenProfile),
        )
        state = LauncherStateTransitions.reflowSmsThreadWindow(
            state = state,
            visibleRows = SmsLayout.threadVisibleRows(screenProfile),
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

    private fun visibleRows(): Int = AppListLayout.visibleRows(screenProfile)

    private fun settingsVisibleRows(): Int = SettingsMenuLayout.visibleRows(screenProfile)

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
        state = LauncherStateTransitions.showAppDrawer(
            state = state,
            visibleRows = visibleRows(),
        ).copy(
            isDrawerSearchFocused = shouldFocusSearchOnEntry,
            isDrawerRailSliding = false,
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
            state = state.copy(
                isDrawerSearchFocused = true,
                isDrawerRailSliding = false,
            ),
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
        val aiSettingsWantsTextInput = state.mode == LauncherMode.AI_SETTINGS
        val smsSearchWantsTextInput =
            (state.mode == LauncherMode.SMS_THREADS || state.mode == LauncherMode.SMS_INBOX) &&
                state.smsPageIndex == SmsPageIndex.ALL
        val smsDraftWantsTextInput =
            state.mode == LauncherMode.SMS_THREAD_DETAIL && !state.smsCurrentIsServiceConversation
        if (!smsSearchWantsTextInput && !smsDraftWantsTextInput &&
            !drawerWantsTextInput && !aiSettingsWantsTextInput
        ) {
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
                    state = state.copy(
                        isDrawerSearchFocused = true,
                        isDrawerRailSliding = false,
                    )
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
            state = state.copy(
                isDrawerSearchFocused = true,
                isDrawerRailSliding = false,
            )
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

    private fun openAiSettings() {
        settleSettingsMotionBeforeExplicitAction()
        state = LauncherStateTransitions.showAiSettings(state)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun closeAiSettings() {
        state = LauncherStateTransitions.hideAiSettings(state)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun onDeepSeekApiKeyChanged(apiKey: String) {
        aiSettingsRepository.setDeepSeekApiKey(apiKey)
        state = LauncherStateTransitions.updateAiSettings(
            state = state,
            deepSeekApiKey = apiKey.trim(),
        )
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

            DataHealthRepairAction.REQUEST_CALL_LOG_PERMISSION -> requestHomeDataPermissions(Manifest.permission.READ_CALL_LOG)

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
            LauncherMode.SMS_INBOX,
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_THREAD_DETAIL,
            LauncherMode.APP_MANAGEMENT,
            LauncherMode.DATA_HEALTH,
            LauncherMode.NOTIFICATION_SETTINGS,
            LauncherMode.AI_SETTINGS,
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
            newTheme = current.selectedTheme,
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
        newTheme: PixelTheme,
    ) {
        persistAppearance(
            pixelShape = newPixelShape,
            dotSizePx = newDotSizePx,
            pixelGapEnabled = newPixelGapEnabled,
            theme = newTheme,
        )
        applyAppearanceState(
            newPixelShape = newPixelShape,
            newDotSizePx = newDotSizePx,
            newPixelGapEnabled = newPixelGapEnabled,
            newTheme = newTheme,
        )
    }

    private fun persistAppearance(
        pixelShape: PixelShape,
        dotSizePx: Int,
        pixelGapEnabled: Boolean,
        theme: PixelTheme,
    ) {
        fontSettingsRepository.setAppearanceSettings(
            pixelShape = pixelShape,
            dotSizePx = dotSizePx,
            pixelGapEnabled = pixelGapEnabled,
            theme = theme,
        )
    }

    private fun applyAppearanceState(
        newPixelShape: PixelShape,
        newDotSizePx: Int,
        newPixelGapEnabled: Boolean,
        newTheme: PixelTheme,
        render: Boolean = true,
    ) {
        selectedTheme = newTheme
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedPixelShape = newPixelShape,
            selectedDotSizePx = newDotSizePx,
            isPixelGapEnabled = newPixelGapEnabled,
            selectedTheme = newTheme,
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
            theme = confirmedState.selectedTheme,
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
            newTheme = state.selectedTheme,
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
        isPixelDustEasterEggEnabled: Boolean = state.isPixelDustEasterEggEnabled,
    ) {
        fontSettingsRepository.setUiBehaviorSettings(
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            chargeAutoIdleEnabled = chargeAutoIdleEnabled,
            inactivityAutoIdleEnabled = inactivityAutoIdleEnabled,
            idleTimeoutSeconds = idleTimeoutSeconds,
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
            pixelDustEasterEggEnabled = isPixelDustEasterEggEnabled,
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
            isPixelDustEasterEggEnabled = isPixelDustEasterEggEnabled,
        )
        syncPixelDustMotionListening()
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
        backgroundExecutor.execute {
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

    private fun refreshCommunicationStatus(render: Boolean) {
        backgroundExecutor.execute {
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
            }
            maybeRequestHomeDataPermissions()
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

            backgroundExecutor.execute {
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

    private fun maybeRequestHomeDataPermissions() {
        if (homeDataPermissionPromptShown) {
            return
        }
        val missingPermissions = buildList {
            if (!deviceLocationRepository.hasLocationPermission()) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (!communicationStatusRepository.hasCallLogPermission()) {
                add(Manifest.permission.READ_CALL_LOG)
            }
            if (!communicationStatusRepository.hasSmsPermission()) {
                add(Manifest.permission.READ_SMS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.distinct()
        if (missingPermissions.isEmpty()) {
            return
        }
        homeDataPermissionPromptShown = true
        requestHomeDataPermissions(*missingPermissions.toTypedArray())
    }

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

    private fun maybeRequestUsageAccess(): Boolean {
        if (usageAccessPromptShown || screenUsageRepository.hasUsageAccess()) {
            return false
        }
        usageAccessPromptShown = true
        startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
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
        const val homeDataPermissionRequestCode = 1001
        const val smsPermissionRequestCode = 1002
        const val smsRoleRequestCode = 1003
        const val rainRefreshIntervalMs: Long = 30 * 60 * 1000L
        const val rainRefreshDistanceThresholdMeters = 1_000f
        const val rainLocationPromptText = "LOC"
        const val statusBarMessageTimeoutMs: Long = 2_500L
        const val pixelAppearanceConfirmTimeoutMs: Long = 5_000L
        const val MEDIA_BOTTOM_BAR_GESTURE_EXCLUSION_LOGICAL_HEIGHT = 16
        const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
        const val EXTRA_OPEN_SMS_THREAD_ID = "open_sms_thread_id"
        const val EXTRA_OPEN_SMS_ADDRESS = "open_sms_address"
        const val smsIntentLogTag = "SmsIntent"
    }
}

private data class PixelAppearanceBaseline(
    val dotSizePx: Int,
    val pixelGapEnabled: Boolean,
)
