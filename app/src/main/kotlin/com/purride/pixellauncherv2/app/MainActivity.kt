package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.ContactsContract
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixellauncherv2.BuildConfig
import com.purride.pixellauncherv2.data.AppRepository
import com.purride.pixellauncherv2.data.CommunicationStatus
import com.purride.pixellauncherv2.data.CommunicationStatusRepository
import com.purride.pixellauncherv2.data.DeviceLocationRepository
import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.data.GeoPoint
import com.purride.pixellauncherv2.data.LauncherStatsRepository
import com.purride.pixellauncherv2.data.NextAlarmRepository
import com.purride.pixellauncherv2.data.PackageManagerAppRepository
import com.purride.pixellauncherv2.data.RainForecastRepository
import com.purride.pixellauncherv2.data.ScreenUsageRepository
import com.purride.pixellauncherv2.data.SmsRepository
import com.purride.pixellauncherv2.data.SmsSendRequest
import com.purride.pixellauncherv2.data.UnreadSmsRepository
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DrawerAsciiInputSanitizer
import com.purride.pixellauncherv2.launcher.DrawerContentTapAction
import com.purride.pixellauncherv2.launcher.DrawerContentTapResolver
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.LauncherCallbacks
import com.purride.pixellauncherv2.launcher.LauncherRootHost
import com.purride.pixellauncherv2.launcher.SmsLayout
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.render.LauncherAnimationState
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelFontSize
import com.purride.pixellauncherv2.render.PixelFontStyle
import com.purride.pixellauncherv2.render.ChargeIdleEffect
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.system.AndroidAppLauncher
import com.purride.pixellauncherv2.system.WindowModeController
import com.purride.pixellauncherv2.util.TerminalStatusProvider
import com.purride.pixellauncherv2.util.ThrottleClickHelper
import com.purride.pixellauncherv2.util.TimeTextProvider
import com.purride.pixellauncherv2.viewmodel.LauncherViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 启动器运行时的总编排入口。
 *
 * 它持有各类仓库，把 Android 输入转换成 [LauncherState] 变化，驱动动画 ticker，
 * 并要求 [PixelRenderer] 把当前状态重新绘制到 [PixelFrameView]。
 */
class MainActivity : AppCompatActivity() {

    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeTextProvider = TimeTextProvider()
    private val throttleClickHelper = ThrottleClickHelper()
    private val terminalStatusProvider = TerminalStatusProvider()

    // Phase 0+: ViewModel layer (runs alongside old renderer; replaces old callbacks incrementally)
    private lateinit var launcherViewModel: LauncherViewModel

    // Phase 8: unified root host (replaces Phases 3–7 individual hosts)
    private lateinit var launcherRootHost: LauncherRootHost

    private lateinit var appRepository: AppRepository
    private lateinit var fontSettingsRepository: FontSettingsRepository
    private lateinit var launcherStatsRepository: LauncherStatsRepository
    private lateinit var deviceStatusRepository: DeviceStatusRepository
    private lateinit var nextAlarmRepository: NextAlarmRepository
    private lateinit var screenUsageRepository: ScreenUsageRepository
    private lateinit var communicationStatusRepository: CommunicationStatusRepository
    private lateinit var unreadSmsRepository: UnreadSmsRepository
    private lateinit var smsRepository: SmsRepository
    private lateinit var deviceLocationRepository: DeviceLocationRepository
    private lateinit var rainForecastRepository: RainForecastRepository
    private lateinit var appLauncher: AndroidAppLauncher
    private lateinit var windowModeController: WindowModeController
    private var screenProfile: ScreenProfile = ScreenProfileFactory.create(widthPx = 1, heightPx = 1)
    private var pixelGapEnabled: Boolean = true
    private var selectedTheme: PixelTheme = PixelTheme.GREEN_PHOSPHOR
    private var state = LauncherState()
    private var animationState = LauncherAnimationState()
    private var loadGeneration = 0
    private var launchPending = false
    private var launchRunnable: Runnable? = null
    private var usageAccessPromptShown = false
    private var homeDataPermissionPromptShown = false
    private var smsRolePromptDismissedThisSession = false
    private var smsThreadsUnreadOnly = true
    private var rainRefreshInFlight = false
    private var lastRainRefreshElapsedRealtimeMs: Long = 0L
    private var lastRainLocation: GeoPoint? = null
    private var lastSuccessfulRainHintText: String = ""

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
        if (!canEnterIdle()) {
            return@Runnable
        }
        val idleForMs = SystemClock.uptimeMillis() - state.lastInteractionUptimeMs
        if (idleForMs >= IDLE_TIMEOUT_MS) {
            state = LauncherStateTransitions.showIdle(state)
            renderCurrentFrame()
        } else {
            scheduleIdleCheck()
        }
    }

    /**
     * 启动整个 launcher 运行时，恢复设置，接线仓库与渲染链路，并完成首次渲染。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.setWindowAnimations(0)

        appRepository = PackageManagerAppRepository(applicationContext)
        fontSettingsRepository = FontSettingsRepository(applicationContext)
        launcherStatsRepository = LauncherStatsRepository(applicationContext)
        deviceStatusRepository = DeviceStatusRepository(applicationContext)
        nextAlarmRepository = NextAlarmRepository(applicationContext)
        screenUsageRepository = ScreenUsageRepository(applicationContext)
        communicationStatusRepository = CommunicationStatusRepository(applicationContext)
        unreadSmsRepository = UnreadSmsRepository(applicationContext)
        smsRepository = SmsRepository(applicationContext)
        deviceLocationRepository = DeviceLocationRepository(applicationContext)
        rainForecastRepository = RainForecastRepository()
        val appearanceSettings = fontSettingsRepository.getAppearanceSettings()
        val uiBehaviorSettings = fontSettingsRepository.getUiBehaviorSettings()
        pixelGapEnabled = appearanceSettings.pixelGapEnabled
        selectedTheme = appearanceSettings.theme
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedFontSize = appearanceSettings.fontSize,
            selectedFontStyle = appearanceSettings.fontStyle,
            selectedPixelShape = appearanceSettings.pixelShape,
            selectedDotSizePx = appearanceSettings.dotSizePx,
            isPixelGapEnabled = appearanceSettings.pixelGapEnabled,
            selectedTheme = appearanceSettings.theme,
        )
        state = LauncherStateTransitions.updateUiBehavior(
            state = state,
            drawerListAlignment = uiBehaviorSettings.drawerListAlignment,
            isIdlePageEnabled = uiBehaviorSettings.isIdlePageEnabled,
            openDrawerInSearchMode = uiBehaviorSettings.openDrawerInSearchMode,
            chargeIdleEffect = uiBehaviorSettings.chargeIdleEffect,
        )
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
        )
        // Phase 8: unified root host (single host for all 9 modes)
        launcherRootHost = LauncherRootHost(
            context = this,
            callbacks = LauncherCallbacks(
                onOpenContacts       = ::onHomeOpenContacts,
                onOpenSms            = ::onHomeOpenSms,
                onDrawerQueryChanged = ::onPixelEngineDrawerQueryChanged,
                onDrawerSubmitSearch = ::onPixelEngineDrawerSubmitSearch,
                onDrawerAppPressed   = ::onPixelEngineDrawerAppPressed,
                onDrawerShowIndex    = ::onPixelEngineDrawerShowIndex,
                onSettingsItemAction = ::onSettingsItemAction,
                onRequestSmsRole     = ::onSmsRequestRole,
                onOpenThread         = ::onSmsOpenThread,
                onSelectSmsIndex     = ::onSmsSelectIndex,
                onDraftChanged       = ::onSmsDraftChanged,
                onSendDraft          = ::onSmsSendDraft,
                onMainPageChanged    = ::onMainPageChanged,
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
                when (state.mode) {
                    LauncherMode.SETTINGS -> closeSettingsMenu()
                    LauncherMode.SMS_ROLE_PROMPT -> closeSmsModule()
                    LauncherMode.SMS_THREADS -> closeSmsModule()
                    LauncherMode.SMS_THREAD_DETAIL -> closeSmsThreadDetail()
                    LauncherMode.SMS_INBOX -> closeUnreadSmsInbox()
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        state = if (state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()) {
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
            }
        }

        // Phase 0+: create ViewModel and observe its StateFlow alongside the old renderer.
        launcherViewModel = ViewModelProvider(this)[LauncherViewModel::class.java]
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcherViewModel.state.collect { vmState ->
                    Log.d(
                        TAG_VM,
                        "state: battery=${vmState.batteryLevel}% charging=${vmState.isCharging}" +
                            " missedCalls=${vmState.missedCallCount} unreadSms=${vmState.unreadSmsCount}" +
                            " theme=${vmState.selectedTheme}",
                    )
                }
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
        windowModeController.hideSystemBars()
        startClockTicker()
        deviceStatusRepository.start(::onDeviceStatusChanged)
        nextAlarmRepository.start(::onNextAlarmChanged)
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
            smsRepository.start(::onSmsProviderChanged)
            refreshScreenUsageSummary(render = false)
            refreshSmsCapability(render = false)
            refreshRainHint(force = true, render = false)
            maybeRequestHomeDataPermissions()
        }
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
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
        hideDrawerKeyboard()
        resetDrawerVerticalGesture()
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.removeCallbacks(idleRunnable)
        launchRunnable?.let(mainHandler::removeCallbacks)
        launchRunnable = null
        launchPending = false
        deviceStatusRepository.stop()
        nextAlarmRepository.stop()
        communicationStatusRepository.stop()
        smsRepository.stop()
        suppressActivityAnimations()
        super.onPause()
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
                refreshCommunicationStatus(render = false)
                refreshRainHint(force = true, render = true)
            }

            smsPermissionRequestCode -> {
                refreshSmsCapability(render = false)
                refreshSmsThreads(render = false)
                maybeRequestDefaultSmsRole()
                renderCurrentFrame()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != smsRoleRequestCode) {
            return
        }
        refreshSmsCapability(render = false)
        smsRolePromptDismissedThisSession = !smsRepository.isDefaultSmsApp()
        openSmsModule(forceRefresh = true, unreadOnly = false)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                navigateHomeFromHardwareKey()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 统一处理硬件按键导航，包括 pager、设置页、抽屉搜索和应用启动。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (launchPending || animationState.bootSequence != null) {
            return true
        }
        if (wakeIfIdle()) {
            return true
        }
        recordInteraction()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                when (state.mode) {
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        moveSelection(-1)
                    }
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        moveSmsThreadSelection(-1)
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> {
                        moveSmsSelection(-1)
                    }
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        moveSettingsSelection(-1)
                    }
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.HOME,
                    LauncherMode.IDLE,
                    LauncherMode.SMS_ROLE_PROMPT -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (state.mode) {
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        moveSelection(1)
                    }
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        moveSmsThreadSelection(1)
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> {
                        moveSmsSelection(1)
                    }
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        moveSettingsSelection(1)
                    }
                    LauncherMode.HOME -> showAppDrawer()
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE,
                    LauncherMode.SMS_ROLE_PROMPT -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        changeSettingValue(-1)
                    }
                    LauncherMode.HOME -> Unit
                    LauncherMode.SMS_ROLE_PROMPT,
                    LauncherMode.SMS_THREADS,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> moveSmsSelection(-1)
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        pageDrawer(-1)
                    }
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        changeSettingValue(1)
                    }
                    LauncherMode.HOME -> Unit
                    LauncherMode.SMS_ROLE_PROMPT,
                    LauncherMode.SMS_THREADS,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> moveSmsSelection(1)
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        pageDrawer(1)
                    }
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        activateSelectedSetting()
                    }
                    LauncherMode.SMS_ROLE_PROMPT -> ensureSmsReadAccessAndRole()
                    LauncherMode.SMS_THREADS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        openSelectedSmsThread()
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> {
                        if (state.smsDraftText.isBlank()) {
                            Unit // engine TextField handles SMS draft focus
                        } else {
                            sendSmsDraft()
                        }
                    }
                    LauncherMode.SMS_INBOX -> {
                        settleSettingsMotionBeforeExplicitAction()
                        launchSelectedUnreadSms()
                    }
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
                    LauncherMode.HOME -> showAppDrawer()
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        if (state.drawerQuery.isNotBlank()) {
                            launchAppAtIndex(0)
                        } else {
                            launchSelectedApp()
                        }
                    }
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            else -> if (handleDrawerTextInput(keyCode, event)) true else super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateHomeFromHardwareKey() {
        if (launchPending || animationState.bootSequence != null) {
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
        val cachedApps = appRepository.loadCachedLaunchableApps()
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
            val apps = appRepository.loadLaunchableApps()
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

    private fun moveSelection(delta: Int) {
        state = LauncherStateTransitions.moveSelection(
            state = state,
            delta = delta,
            visibleRows = visibleRows(),
        )
        renderCurrentFrame()
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
    private fun launchApp(selectedApp: AppEntry) {
        if (!throttleClickHelper.canClick() || launchPending) {
            return
        }
        settleDrawerMotionBeforeExplicitAction()
        launchPending = true
        animationState = animationState.startLaunchShutter()
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
    }

    /**
     * 收集当前渲染输入并提交一帧完整像素画面到显示视图。
     */
    private fun renderCurrentFrame() {
        if (!::launcherRootHost.isInitialized) return
        launcherRootHost.update(
            state           = launcherViewModel.state.value,
            theme           = launcherViewModel.currentTheme.value,
            screenProfile   = screenProfile,
            chargeTick      = animationState.headerChargeTick,
            pixelGapEnabled = pixelGapEnabled,
        )
    }

    /** 主页 Pager 手势翻页回调 — 同步旧 state + ViewModel state 的 mode 字段。 */
    private fun onMainPageChanged(mode: LauncherMode) {
        if (state.mode == mode) return
        state = state.copy(mode = mode)
        launcherViewModel.update { copy(mode = mode) }
        renderCurrentFrame()
        updateTextInputFocus()
    }

    // End Phase 8 ─────────────────────────────────────────────────────────────

    // ── SMS callbacks (called from LauncherCallbacks) ─────────────────────────

    private fun onSmsRequestRole() {
        maybeRequestDefaultSmsRole()
    }

    private fun onSmsOpenThread(threadId: Long, address: String) {
        openSmsThread(threadId = threadId, address = address)
    }

    private fun onSmsSelectIndex(index: Int) {
        state = state.copy(smsSelectedIndex = index)
    }

    private fun onSmsDraftChanged(text: String) {
        state = LauncherStateTransitions.updateSmsDraftText(state = state, smsDraftText = text)
    }

    private fun onSmsSendDraft() {
        sendSmsDraft()
    }

    // ── SETTINGS callback (called from LauncherCallbacks) ─────────────────────

    /**
     * SETTINGS 屏幕行动作回调。
     *
     * 同时更新旧 [state] + [launcherViewModel]，确保两条渲染路径的数据保持同步。
     */
    private fun onSettingsItemAction(item: SettingsMenuItem, direction: Int) {
        val s = state
        when (item) {
            SettingsMenuItem.FONT_SIZE -> applyAppearance(
                fontSize = SettingsMenuModel.nextFontSize(s.selectedFontSize, direction),
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.FONT_STYLE -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = SettingsMenuModel.nextFontStyle(s.selectedFontStyle, direction),
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.RESOLUTION -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = SettingsMenuModel.nextResolution(s.selectedDotSizePx, direction, screenProfile),
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.PIXEL_GAP -> {
                val nextGap = SettingsMenuModel.toggle(s.isPixelGapEnabled)
                applyAppearance(
                    fontSize = s.selectedFontSize,
                    fontStyle = s.selectedFontStyle,
                    newPixelShape = if (nextGap) s.selectedPixelShape else PixelShape.SQUARE,
                    newDotSizePx = s.selectedDotSizePx,
                    newPixelGapEnabled = nextGap,
                    newTheme = s.selectedTheme,
                )
            }
            SettingsMenuItem.STYLE -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = s.selectedFontStyle,
                newPixelShape = SettingsMenuModel.nextStyle(s.selectedPixelShape, direction),
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.THEME -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = SettingsMenuModel.nextTheme(s.selectedTheme, direction),
            )
            SettingsMenuItem.APP_LIST_ALIGNMENT -> applyUiBehavior(
                drawerListAlignment = SettingsMenuModel.nextDrawerListAlignment(s.drawerListAlignment, direction),
                isIdlePageEnabled = s.isIdlePageEnabled,
                openDrawerInSearchMode = s.openDrawerInSearchMode,
                chargeIdleEffect = s.chargeIdleEffect,
            )
            SettingsMenuItem.DRAWER_AUTO_SEARCH -> applyUiBehavior(
                drawerListAlignment = s.drawerListAlignment,
                isIdlePageEnabled = s.isIdlePageEnabled,
                openDrawerInSearchMode = SettingsMenuModel.toggle(s.openDrawerInSearchMode),
                chargeIdleEffect = s.chargeIdleEffect,
            )
            else -> return  // IDLE_PAGE, ADVANCED: no-op in new settings screen
        }
        // Sync appearance fields to ViewModel so the settings screen reflects changes immediately
        val updated = state  // applyAppearance / applyUiBehavior already updated state
        launcherViewModel.update {
            copy(
                selectedFontSize = updated.selectedFontSize,
                selectedFontStyle = updated.selectedFontStyle,
                selectedPixelShape = updated.selectedPixelShape,
                selectedDotSizePx = updated.selectedDotSizePx,
                isPixelGapEnabled = updated.isPixelGapEnabled,
                selectedTheme = updated.selectedTheme,
                drawerListAlignment = updated.drawerListAlignment,
                openDrawerInSearchMode = updated.openDrawerInSearchMode,
            )
        }
    }

    // ── HOME callbacks (called from LauncherCallbacks) ────────────────────────

    /** CONTACT 按钮：打开通讯录（先尝试原生通讯录 app，fallback 到通讯录内容 URI）。 */
    private fun onHomeOpenContacts() {
        launchFirstAvailableIntent(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = ContactsContract.Contacts.CONTENT_URI
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /** SMS 按钮：进入短信模块。 */
    private fun onHomeOpenSms() {
        openSmsModule(forceRefresh = true, unreadOnly = false)
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

    private fun updateScreenProfile(widthPx: Int, heightPx: Int): Boolean {
        val newProfile = ScreenProfileFactory.create(
            widthPx = widthPx,
            heightPx = heightPx,
            dotSizePx = state.selectedDotSizePx,
            pixelShape = state.selectedPixelShape,
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
        renderCurrentFrame()
        return true
    }

    private fun visibleRows(): Int {
        return AppListLayout.metrics(screenProfile).textList.viewport.visibleRows
    }

    private fun settingsVisibleRows(): Int {
        return SettingsMenuLayout.metrics(screenProfile).textList.viewport.visibleRows
    }

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
            animationState = animationState.startDrawerReveal()
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
        if (filteredQuery.isNotBlank() && currentDrawerApps().size == 1) {
            launchSelectedApp()
            return
        }
        renderCurrentFrame()
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

    private fun onPixelEngineDrawerShowIndex(index: Int) {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return
        }
        recordInteraction()
        settleDrawerMotionBeforeExplicitAction()
        state = LauncherStateTransitions.selectIndex(
            state = state,
            index = index,
            visibleRows = visibleRows(),
        )
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
    }

    private fun hideDrawerKeyboard() {
        val inputManager = getSystemService(InputMethodManager::class.java) ?: return
        inputManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun updateTextInputFocus() {
        if (state.mode != LauncherMode.SMS_THREAD_DETAIL) {
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

    private fun openSmsModule(forceRefresh: Boolean = false, unreadOnly: Boolean = true) {
        smsThreadsUnreadOnly = unreadOnly
        refreshSmsCapability(render = false)
        if (forceRefresh) {
            refreshSmsThreads(render = false, unreadOnly = smsThreadsUnreadOnly)
        }
        state = if (
            state.smsPermissionState != SmsPermissionState.MISSING &&
            (state.isDefaultSmsApp || smsRolePromptDismissedThisSession)
        ) {
            LauncherStateTransitions.showSmsThreads(
                state = state,
                visibleRows = smsThreadsVisibleRows(),
            )
        } else {
            LauncherStateTransitions.showSmsRolePrompt(state)
        }
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun closeSmsModule() {
        smsThreadsUnreadOnly = true
        state = LauncherStateTransitions.hideSmsThreads(state)
        renderCurrentFrame()
        updateTextInputFocus()
        scheduleIdleCheck()
    }

    private fun openSelectedSmsThread() {
        val thread = state.smsThreads.getOrNull(state.smsThreadSelectedIndex) ?: return
        openSmsThread(
            threadId = thread.threadId,
            address = thread.address,
        )
    }

    private fun openSmsThread(
        threadId: Long?,
        address: String,
        prefilledDraft: String = "",
    ) {
        Log.d(
            smsIntentLogTag,
            "openSmsThread threadId=$threadId address=$address draftLength=${prefilledDraft.length} beforeMode=${state.mode}",
        )
        state = LauncherStateTransitions.showSmsThreadDetail(
            state = LauncherStateTransitions.updateSmsDraftText(
                state = state,
                smsDraftText = prefilledDraft,
            ),
            threadId = threadId,
            address = address,
        )
        Log.d(
            smsIntentLogTag,
            "openSmsThread afterMode=${state.mode} currentThread=${state.smsCurrentThreadId} address=${state.smsCurrentAddress}",
        )
        renderCurrentFrame()
        updateTextInputFocus()
        refreshSmsThreadDetail(
            threadId = threadId,
            fallbackAddress = address,
            render = true,
        )
        if (threadId != null) {
            backgroundExecutor.execute {
                smsRepository.markThreadRead(threadId)
                refreshSmsThreads(render = false, unreadOnly = smsThreadsUnreadOnly)
                refreshCommunicationStatus(render = false)
            }
        }
    }

    private fun closeSmsThreadDetail() {
        state = LauncherStateTransitions.hideSmsThreadDetail(state)
        renderCurrentFrame()
        updateTextInputFocus()
    }

    private fun refreshSmsCapability(render: Boolean) {
        state = LauncherStateTransitions.updateSmsCapability(
            state = state,
            isDefaultSmsApp = smsRepository.isDefaultSmsApp(),
            smsPermissionState = smsRepository.permissionState(),
        )
        if (state.isDefaultSmsApp) {
            smsRolePromptDismissedThisSession = false
        }
        if (render) {
            renderCurrentFrame()
        }
    }

    private fun refreshSmsThreads(render: Boolean, unreadOnly: Boolean = smsThreadsUnreadOnly) {
        backgroundExecutor.execute {
            val threads = smsRepository.readThreads().let { allThreads ->
                if (unreadOnly) allThreads.filter { it.unreadCount > 0 } else allThreads
            }
            mainHandler.post {
                if (isDestroyed || isFinishing) {
                    return@post
                }
                state = LauncherStateTransitions.updateSmsThreads(
                    state = state,
                    threads = threads,
                    visibleRows = smsThreadsVisibleRows(),
                )
                if (render) {
                    renderCurrentFrame()
                }
            }
        }
    }

    private fun refreshSmsThreadDetail(
        threadId: Long?,
        fallbackAddress: String,
        render: Boolean,
    ) {
        backgroundExecutor.execute {
            val messages = threadId?.let(smsRepository::readThreadMessages).orEmpty()
            val resolvedAddress = messages.lastOrNull()?.address?.takeIf { it.isNotBlank() } ?: fallbackAddress
            mainHandler.post {
                if (isDestroyed || isFinishing) {
                    return@post
                }
                state = LauncherStateTransitions.updateSmsMessages(
                    state = state,
                    threadId = threadId,
                    address = resolvedAddress,
                    messages = messages,
                )
                if (render) {
                    renderCurrentFrame()
                }
            }
        }
    }

    private fun onSmsProviderChanged() {
        refreshSmsCapability(render = false)
        refreshSmsThreads(
            render = state.mode == LauncherMode.SMS_THREADS || state.mode == LauncherMode.SMS_ROLE_PROMPT,
            unreadOnly = smsThreadsUnreadOnly,
        )
        if (state.mode == LauncherMode.SMS_THREAD_DETAIL) {
            refreshSmsThreadDetail(
                threadId = state.smsCurrentThreadId,
                fallbackAddress = state.smsCurrentAddress,
                render = true,
            )
        }
    }

    private fun moveSmsThreadSelection(delta: Int) {
        state = LauncherStateTransitions.moveSmsThreadSelection(
            state = state,
            delta = delta,
            visibleRows = smsThreadsVisibleRows(),
        )
        renderCurrentFrame()
    }

    private fun smsThreadsVisibleRows(): Int {
        return SmsLayout.threadListMetrics(screenProfile).textList.viewport.visibleRows
    }

    private fun maybeRequestDefaultSmsRole() {
        if (smsRepository.isDefaultSmsApp()) {
            state = LauncherStateTransitions.showSmsThreads(
                state = state,
                visibleRows = smsThreadsVisibleRows(),
            )
            renderCurrentFrame()
            return
        }
        val intent = smsRepository.buildDefaultSmsRoleIntent() ?: return
        @Suppress("DEPRECATION")
        startActivityForResult(intent, smsRoleRequestCode)
    }

    private fun ensureSmsReadAccessAndRole() {
        val missingPermissions = buildList {
            if (!smsRepository.hasReadSmsPermission()) add(Manifest.permission.READ_SMS)
            if (!smsRepository.hasSendSmsPermission()) add(Manifest.permission.SEND_SMS)
            if (!smsRepository.hasReceiveSmsPermission()) add(Manifest.permission.RECEIVE_SMS)
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), smsPermissionRequestCode)
            return
        }
        maybeRequestDefaultSmsRole()
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
                refreshSmsCapability(render = false)
                openSmsThread(
                    threadId = openThreadId ?: smsRepository.findThreadForAddress(resolvedAddress)?.threadId,
                    address = resolvedAddress,
                    prefilledDraft = draftBody,
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

    private fun sendSmsDraft() {
        val address = state.smsCurrentAddress.trim()
        val draft = state.smsDraftText.trim()
        if (address.isBlank() || draft.isBlank()) {
            return
        }
        if (state.smsPermissionState != SmsPermissionState.READY) {
            ensureSmsReadAccessAndRole()
            return
        }
        backgroundExecutor.execute {
            val result = smsRepository.sendMessage(
                SmsSendRequest(
                    address = address,
                    body = draft,
                    threadId = state.smsCurrentThreadId,
                ),
            )
            mainHandler.post {
                if (isDestroyed || isFinishing) {
                    return@post
                }
                result.onSuccess { sentEntry ->
                    val nextMessages = state.smsMessages + sentEntry
                    state = LauncherStateTransitions.updateSmsMessages(
                        state = LauncherStateTransitions.updateSmsDraftText(
                            state = state,
                            smsDraftText = "",
                        ),
                        threadId = sentEntry.threadId.takeIf { it > 0L } ?: state.smsCurrentThreadId,
                        address = sentEntry.address,
                        messages = nextMessages,
                    )
                    renderCurrentFrame()
                    refreshSmsThreads(render = false)
                    refreshCommunicationStatus(render = false)
                }
            }
        }
    }

    private fun openUnreadSmsInbox() {
        state = LauncherStateTransitions.updateUnreadSmsEntries(
            state = state,
            entries = unreadSmsRepository.readUnreadMessages(),
            visibleRows = smsInboxVisibleRows(),
        )
        state = LauncherStateTransitions.showUnreadSmsInbox(
            state = state,
            visibleRows = smsInboxVisibleRows(),
        )
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun closeUnreadSmsInbox() {
        state = LauncherStateTransitions.hideUnreadSmsInbox(state)
        renderCurrentFrame()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun moveSmsSelection(delta: Int) {
        state = LauncherStateTransitions.moveSmsSelection(
            state = state,
            delta = delta,
            visibleRows = smsInboxVisibleRows(),
        )
        renderCurrentFrame()
    }

    private fun launchSelectedUnreadSms() {
        val entry = state.unreadSmsEntries.getOrNull(state.smsSelectedIndex) ?: return
        launchSystemIntent(
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:${Uri.encode(entry.address)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun moveSettingsSelection(delta: Int) {
        state = LauncherStateTransitions.moveSettingsSelection(
            state = state,
            delta = delta,
            visibleRows = settingsVisibleRows(),
        )
        renderCurrentFrame()
    }

    private fun activateSelectedSetting() {
        activateSettingItem(SettingsMenuModel.selectedItem(state))
    }

    private fun activateSettingItem(item: SettingsMenuItem) {
        when (item) {
            SettingsMenuItem.FONT_SIZE -> changeSettingValue(1)
            SettingsMenuItem.FONT_STYLE -> changeSettingValue(1)
            SettingsMenuItem.RESOLUTION -> changeSettingValue(1)
            SettingsMenuItem.PIXEL_GAP -> changeSettingValue(1)
            SettingsMenuItem.STYLE -> changeSettingValue(1)
            SettingsMenuItem.THEME -> changeSettingValue(1)
            SettingsMenuItem.APP_LIST_ALIGNMENT -> changeSettingValue(1)
            SettingsMenuItem.IDLE_PAGE -> changeSettingValue(1)
            SettingsMenuItem.DRAWER_AUTO_SEARCH -> changeSettingValue(1)
            SettingsMenuItem.ADVANCED -> Unit
        }
    }

    private fun changeSettingValue(direction: Int) {
        when (SettingsMenuModel.selectedItem(state)) {
            SettingsMenuItem.FONT_SIZE -> {
                val nextFontSize = SettingsMenuModel.nextFontSize(state.selectedFontSize, direction)
                applyAppearance(
                    fontSize = nextFontSize,
                    fontStyle = state.selectedFontStyle,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
                    newPixelGapEnabled = state.isPixelGapEnabled,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.FONT_STYLE -> {
                val nextFontStyle = SettingsMenuModel.nextFontStyle(state.selectedFontStyle, direction)
                applyAppearance(
                    fontSize = state.selectedFontSize,
                    fontStyle = nextFontStyle,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
                    newPixelGapEnabled = state.isPixelGapEnabled,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.RESOLUTION -> {
                val nextDotSizePx = SettingsMenuModel.nextResolution(
                    current = state.selectedDotSizePx,
                    direction = direction,
                    screenProfile = screenProfile,
                )
                applyAppearance(
                    fontSize = state.selectedFontSize,
                    fontStyle = state.selectedFontStyle,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = nextDotSizePx,
                    newPixelGapEnabled = state.isPixelGapEnabled,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.PIXEL_GAP -> {
                val nextPixelGapEnabled = SettingsMenuModel.toggle(state.isPixelGapEnabled)
                applyAppearance(
                    fontSize = state.selectedFontSize,
                    fontStyle = state.selectedFontStyle,
                    newPixelShape = if (nextPixelGapEnabled) state.selectedPixelShape else PixelShape.SQUARE,
                    newDotSizePx = state.selectedDotSizePx,
                    newPixelGapEnabled = nextPixelGapEnabled,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.STYLE -> {
                val nextPixelShape = SettingsMenuModel.nextStyle(state.selectedPixelShape, direction)
                applyAppearance(
                    fontSize = state.selectedFontSize,
                    fontStyle = state.selectedFontStyle,
                    newPixelShape = nextPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
                    newPixelGapEnabled = state.isPixelGapEnabled,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.THEME -> {
                val nextTheme = SettingsMenuModel.nextTheme(state.selectedTheme, direction)
                applyAppearance(
                    fontSize = state.selectedFontSize,
                    fontStyle = state.selectedFontStyle,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
                    newPixelGapEnabled = state.isPixelGapEnabled,
                    newTheme = nextTheme,
                )
            }

            SettingsMenuItem.APP_LIST_ALIGNMENT -> {
                applyUiBehavior(
                    drawerListAlignment = SettingsMenuModel.nextDrawerListAlignment(state.drawerListAlignment, direction),
                    isIdlePageEnabled = state.isIdlePageEnabled,
                    openDrawerInSearchMode = state.openDrawerInSearchMode,
                    chargeIdleEffect = state.chargeIdleEffect,
                )
            }

            SettingsMenuItem.IDLE_PAGE -> {
                applyUiBehavior(
                    drawerListAlignment = state.drawerListAlignment,
                    isIdlePageEnabled = SettingsMenuModel.toggle(state.isIdlePageEnabled),
                    openDrawerInSearchMode = state.openDrawerInSearchMode,
                    chargeIdleEffect = state.chargeIdleEffect,
                )
            }

            SettingsMenuItem.DRAWER_AUTO_SEARCH -> {
                applyUiBehavior(
                    drawerListAlignment = state.drawerListAlignment,
                    isIdlePageEnabled = state.isIdlePageEnabled,
                    openDrawerInSearchMode = SettingsMenuModel.toggle(state.openDrawerInSearchMode),
                    chargeIdleEffect = state.chargeIdleEffect,
                )
            }

            SettingsMenuItem.ADVANCED -> Unit
        }
    }

    private fun pageDrawer(direction: Int) {
        state = LauncherStateTransitions.pageSelection(
            state = state,
            direction = direction,
            visibleRows = visibleRows(),
        )
        renderCurrentFrame()
    }

    private fun smsInboxVisibleRows(): Int {
        return SettingsMenuLayout.largeTextMetrics(screenProfile).visibleRows
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
        return animationState.hasActiveAnimations || shouldAnimateHeaderCharge()
    }

    private fun shouldAnimateHeaderCharge(): Boolean {
        if (!state.isCharging || state.batteryLevel >= 100) {
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
            LauncherMode.DIAGNOSTICS -> true

            LauncherMode.IDLE -> false
        }
    }

    private fun scrollSmsDetailBy(deltaPx: Int) = Unit

    private fun applyAppearance(
        fontSize: PixelFontSize,
        fontStyle: PixelFontStyle,
        newPixelShape: PixelShape,
        newDotSizePx: Int,
        newPixelGapEnabled: Boolean,
        newTheme: PixelTheme,
    ) {
        pixelGapEnabled = newPixelGapEnabled
        selectedTheme = newTheme
        fontSettingsRepository.setAppearanceSettings(
            fontSize = fontSize,
            fontStyle = fontStyle,
            pixelShape = newPixelShape,
            dotSizePx = newDotSizePx,
            pixelGapEnabled = newPixelGapEnabled,
            theme = newTheme,
        )
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedFontSize = fontSize,
            selectedFontStyle = fontStyle,
            selectedPixelShape = newPixelShape,
            selectedDotSizePx = newDotSizePx,
            isPixelGapEnabled = newPixelGapEnabled,
            selectedTheme = newTheme,
        )

        val widthPx = launcherRootHost.rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val heightPx = launcherRootHost.rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val screenProfileChanged = updateScreenProfile(widthPx, heightPx)
        refreshDerivedUiState(render = !screenProfileChanged)
    }

    private fun applyUiBehavior(
        drawerListAlignment: DrawerListAlignment,
        isIdlePageEnabled: Boolean,
        openDrawerInSearchMode: Boolean,
        chargeIdleEffect: ChargeIdleEffect,
    ) {
        fontSettingsRepository.setUiBehaviorSettings(
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
        )
        state = LauncherStateTransitions.updateUiBehavior(
            state = state,
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
        )
        if (!isIdlePageEnabled && state.mode == LauncherMode.IDLE) {
            wakeFromIdle()
            return
        }
        refreshDerivedUiState(render = true)
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun onDeviceStatusChanged(deviceStatus: DeviceStatus) {
        state = LauncherStateTransitions.updateDeviceStatus(state, deviceStatus)
        refreshDerivedUiState(render = true)
        startAnimationTickerIfNeeded()
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

    private fun refreshScreenUsageSummary(render: Boolean) {
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
                if (render) {
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

    private fun refreshRainHint(force: Boolean, render: Boolean) {
        if (!force) {
            val elapsedSinceLastRefresh = SystemClock.elapsedRealtime() - lastRainRefreshElapsedRealtimeMs
            if (lastRainRefreshElapsedRealtimeMs > 0L && elapsedSinceLastRefresh < rainRefreshIntervalMs) {
                return
            }
        }
        if (rainRefreshInFlight) {
            return
        }
        if (!deviceLocationRepository.hasLocationPermission()) {
            lastRainRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
            applyRainHintText(rainLocationPromptText, render = render)
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
                return@requestBestLocation
            }

            val nowElapsedRealtime = SystemClock.elapsedRealtime()
            val shouldFetch = force ||
                lastRainRefreshElapsedRealtimeMs <= 0L ||
                (nowElapsedRealtime - lastRainRefreshElapsedRealtimeMs) >= rainRefreshIntervalMs ||
                lastRainLocation?.distanceToMeters(location)?.let { it >= rainRefreshDistanceThresholdMeters } != false
            if (!shouldFetch) {
                rainRefreshInFlight = false
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
                    }
                }
            }
        }
    }

    private fun applyRainHintText(rainHintText: String, render: Boolean) {
        state = LauncherStateTransitions.updateRainHintText(
            state = state,
            rainHintText = rainHintText,
        )
        if (render) {
            refreshDerivedUiState(render = true)
        } else {
            renderCurrentFrame()
        }
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
        }.distinct()
        if (missingPermissions.isEmpty()) {
            return
        }
        homeDataPermissionPromptShown = true
        requestPermissions(
            missingPermissions.toTypedArray(),
            homeDataPermissionRequestCode,
        )
    }

    private fun maybeRequestUsageAccess(): Boolean {
        if (BuildConfig.DEBUG) {
            return false
        }
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
        state = LauncherStateTransitions.updateTerminalStatus(
            state = state,
            terminalStatusText = terminalStatusProvider.buildStatus(state),
        )
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

    private fun canEnterIdle(): Boolean {
        return state.isIdlePageEnabled &&
            !launchPending &&
            (state.mode == LauncherMode.HOME || state.mode == LauncherMode.APP_DRAWER)
    }

    private fun scheduleIdleCheck() {
        mainHandler.removeCallbacks(idleRunnable)
        if (canEnterIdle()) {
            val idleForMs = SystemClock.uptimeMillis() - state.lastInteractionUptimeMs
            val delay = (IDLE_TIMEOUT_MS - idleForMs).coerceAtLeast(0L)
            mainHandler.postDelayed(idleRunnable, delay)
        }
    }

    @Suppress("DEPRECATION")
    private fun suppressActivityAnimations() {
        overridePendingTransition(0, 0)
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 25_000L
        const val LOW_BATTERY_THRESHOLD = 15
        const val homeDataPermissionRequestCode = 1001
        const val smsPermissionRequestCode = 1002
        const val smsRoleRequestCode = 1003
        const val rainRefreshIntervalMs: Long = 30 * 60 * 1000L
        const val rainRefreshDistanceThresholdMeters = 1_000f
        const val rainLocationPromptText = "LOC"
        const val EXTRA_OPEN_SMS_THREAD_ID = "open_sms_thread_id"
        const val EXTRA_OPEN_SMS_ADDRESS = "open_sms_address"
        const val smsIntentLogTag = "SmsIntent"
        const val TAG_VM = "MainActivity/VM"
    }
}
