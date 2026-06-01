package com.purride.pixellauncherv2.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.ContactsContract
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.LauncherAnimationState
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.launcher.PixelFontSize
import com.purride.pixellauncherv2.launcher.PixelFontStyle
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.system.AndroidAppLauncher
import com.purride.pixellauncherv2.system.WindowModeController
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.util.TerminalStatusProvider
import com.purride.pixellauncherv2.util.ThrottleClickHelper
import com.purride.pixellauncherv2.util.TimeTextProvider
import com.purride.pixellauncherv2.viewmodel.LauncherViewModel
import com.purride.pixellauncherv2.viewmodel.toLauncherUiState
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
    private val terminalStatusProvider = TerminalStatusProvider()

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
    private lateinit var deviceLocationRepository: DeviceLocationRepository
    private lateinit var smsController: SmsController
    private lateinit var rainForecastRepository: RainForecastRepository
    private lateinit var appLauncher: AndroidAppLauncher
    private lateinit var windowModeController: WindowModeController
    private var screenProfile: ScreenProfile = ScreenProfileFactory.create(widthPx = 1, heightPx = 1)
    private var pixelGapEnabled: Boolean = true
    private var selectedTheme: PixelTheme = PixelTheme.DAY
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

        override fun updateDrawerInputFocus() = this@MainActivity.updateDrawerInputFocus()

        override fun scheduleIdleCheck() = this@MainActivity.scheduleIdleCheck()

        override fun refreshCommunicationStatus(render: Boolean) =
            this@MainActivity.refreshCommunicationStatus(render)

        override fun requestSmsPermissions(permissions: Array<String>) =
            requestPermissions(permissions, smsPermissionRequestCode)

        override fun startSmsRoleRequest(intent: Intent) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, smsRoleRequestCode)
        }

        override fun launchSystemIntent(intent: Intent) = this@MainActivity.launchSystemIntent(intent)
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
        launcherViewModel = ViewModelProvider(this)[LauncherViewModel::class.java]
        supportActionBar?.hide()
        window.setWindowAnimations(0)

        appRepository = PackageManagerAppRepository(applicationContext)
        fontSettingsRepository = FontSettingsRepository(applicationContext)
        launcherStatsRepository = LauncherStatsRepository(applicationContext)
        deviceStatusRepository = DeviceStatusRepository(applicationContext)
        nextAlarmRepository = NextAlarmRepository(applicationContext)
        screenUsageRepository = ScreenUsageRepository(applicationContext)
        communicationStatusRepository = CommunicationStatusRepository(applicationContext)
        smsController = SmsController(
            context = applicationContext,
            backgroundExecutor = backgroundExecutor,
            mainHandler = mainHandler,
            host = smsHost,
        )
        deviceLocationRepository = DeviceLocationRepository(applicationContext)
        rainForecastRepository = RainForecastRepository()
        val appearanceSettings = fontSettingsRepository.getAppearanceSettings()
        val uiBehaviorSettings = fontSettingsRepository.getUiBehaviorSettings()
        pixelGapEnabled = appearanceSettings.pixelGapRatio > 0f
        selectedTheme = appearanceSettings.theme
        // Keep the layout glyph metrics (visibleRows / row heights) in sync with the saved font.
        GlyphStyle.configure(appearanceSettings.fontSize, appearanceSettings.fontStyle)
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedFontSize = appearanceSettings.fontSize,
            selectedFontStyle = appearanceSettings.fontStyle,
            selectedPixelShape = appearanceSettings.pixelShape,
            selectedDotSizePx = appearanceSettings.dotSizePx,
            isPixelGapEnabled = appearanceSettings.pixelGapRatio > 0f,
            pixelGapRatio = appearanceSettings.pixelGapRatio,
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
                onSettingsItemAction = ::onSettingsItemAction,
                onSettingsItemRatioChanged = ::onSettingsItemRatioChanged,
                onSettingsPreviewChanged = ::onSettingsPreviewChanged,
                onRequestSmsRole     = smsController::requestDefaultRole,
                onOpenThread         = smsController::openThread,
                onSelectSmsIndex     = smsController::selectIndex,
                onDraftChanged       = smsController::draftChanged,
                onSendDraft          = smsController::sendDraft,
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
                    LauncherMode.SMS_ROLE_PROMPT -> smsController.closeModule()
                    LauncherMode.SMS_THREADS -> smsController.closeModule()
                    LauncherMode.SMS_THREAD_DETAIL -> smsController.closeThreadDetail()
                    LauncherMode.SMS_INBOX -> smsController.closeUnreadInbox()
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
            smsController.start()
            refreshScreenUsageSummary(render = false)
            smsController.refreshSmsCapability(render = false)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // configChanges="uiMode" keeps the activity alive across light/dark toggles;
        // re-render so an AUTO theme follows the new system uiMode.
        renderCurrentFrame()
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
        smsController.stop()
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
            if (onKeyDown(event.keyCode, event)) {
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
                        smsController.moveThreadSelection(-1)
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> {
                        smsController.moveInboxSelection(-1)
                    }
                    LauncherMode.SETTINGS -> Unit
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
                        smsController.moveThreadSelection(1)
                    }
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> {
                        smsController.moveInboxSelection(1)
                    }
                    LauncherMode.SETTINGS -> Unit
                    LauncherMode.HOME -> Unit
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
                    LauncherMode.SMS_THREADS,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> smsController.moveInboxSelection(-1)
                    LauncherMode.APP_DRAWER -> Unit
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
                    LauncherMode.SMS_THREADS,
                    LauncherMode.SMS_THREAD_DETAIL -> Unit
                    LauncherMode.SMS_INBOX -> smsController.moveInboxSelection(1)
                    LauncherMode.APP_DRAWER -> Unit
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
                        smsController.openSelectedThread()
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
                        smsController.launchSelectedUnread()
                    }
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
        val uiState = state.toLauncherUiState()
        launcherRootHost.update(
            state           = uiState,
            theme           = LauncherThemes.from(applicationContext, uiState.selectedTheme.resolve(isSystemInDarkMode())),
            screenProfile   = screenProfile,
            chargeTick      = animationState.headerChargeTick,
            pixelGapEnabled = uiState.pixelGapRatio > 0f,
            pixelGapRatio   = uiState.pixelGapRatio,
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
            SettingsMenuItem.PIXEL_GAP_SIZE -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = SettingsMenuModel.nextPixelGapRatio(s.pixelGapRatio, direction) > 0f,
                newPixelGapRatio = SettingsMenuModel.nextPixelGapRatio(s.pixelGapRatio, direction),
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.PIXEL_GAP -> return
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
    }

    private fun onSettingsItemRatioChanged(item: SettingsMenuItem, ratio: Float) {
        val s = state
        when (item) {
            SettingsMenuItem.FONT_SIZE -> applyAppearance(
                fontSize = SettingsMenuModel.fontSizeAtRatio(ratio),
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.RESOLUTION -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = SettingsMenuModel.resolutionAtRatio(ratio, screenProfile),
                newPixelGapEnabled = s.isPixelGapEnabled,
                newTheme = s.selectedTheme,
            )
            SettingsMenuItem.PIXEL_GAP_SIZE -> applyAppearance(
                fontSize = s.selectedFontSize,
                fontStyle = s.selectedFontStyle,
                newPixelShape = s.selectedPixelShape,
                newDotSizePx = s.selectedDotSizePx,
                newPixelGapEnabled = ratio > 0f,
                newPixelGapRatio = ratio,
                newTheme = s.selectedTheme,
            )
            else -> Unit
        }
    }

    private fun onSettingsPreviewChanged() {
        launcherRootHost.rootView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
        smsController.openModule(forceRefresh = true, unreadOnly = false)
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

    private fun hideDrawerKeyboard() {
        val inputManager = getSystemService(InputMethodManager::class.java) ?: return
        inputManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun updateTextInputFocus() {
        val drawerWantsTextInput = state.mode == LauncherMode.APP_DRAWER && state.isDrawerSearchFocused
        if (state.mode != LauncherMode.SMS_THREAD_DETAIL && !drawerWantsTextInput) {
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
                return
            }

            SettingsMenuItem.PIXEL_GAP_SIZE -> {
                applyAppearance(
                    fontSize = state.selectedFontSize,
                    fontStyle = state.selectedFontStyle,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
                    newPixelGapEnabled = SettingsMenuModel.nextPixelGapRatio(state.pixelGapRatio, direction) > 0f,
                    newPixelGapRatio = SettingsMenuModel.nextPixelGapRatio(state.pixelGapRatio, direction),
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
        newPixelGapRatio: Float = state.pixelGapRatio,
        newTheme: PixelTheme,
    ) {
        val effectivePixelGapEnabled = newPixelGapRatio > 0f
        pixelGapEnabled = effectivePixelGapEnabled
        selectedTheme = newTheme
        // Re-sync glyph metrics before updateScreenProfile()/visibleRows() recompute the viewport.
        GlyphStyle.configure(fontSize, fontStyle)
        fontSettingsRepository.setAppearanceSettings(
            fontSize = fontSize,
            fontStyle = fontStyle,
            pixelShape = newPixelShape,
            dotSizePx = newDotSizePx,
            pixelGapEnabled = effectivePixelGapEnabled,
            pixelGapRatio = newPixelGapRatio,
            theme = newTheme,
        )
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedFontSize = fontSize,
            selectedFontStyle = fontStyle,
            selectedPixelShape = newPixelShape,
            selectedDotSizePx = newDotSizePx,
            isPixelGapEnabled = effectivePixelGapEnabled,
            pixelGapRatio = newPixelGapRatio,
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
    }
}
