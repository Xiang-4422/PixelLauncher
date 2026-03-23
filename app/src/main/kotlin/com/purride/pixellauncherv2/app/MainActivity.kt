package com.purride.pixellauncherv2.app

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixellauncherv2.data.AppRepository
import com.purride.pixellauncherv2.data.CommunicationStatus
import com.purride.pixellauncherv2.data.CommunicationStatusRepository
import com.purride.pixellauncherv2.data.DeviceLocationRepository
import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.DeviceMotionRepository
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
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
import com.purride.pixellauncherv2.launcher.DrawerDirectionalSettlePolicy
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.DrawerRailDragMapper
import com.purride.pixellauncherv2.launcher.DrawerSettleTarget
import com.purride.pixellauncherv2.launcher.DrawerVerticalScrollController
import com.purride.pixellauncherv2.launcher.DrawerVerticalScrollThresholds
import com.purride.pixellauncherv2.launcher.HomeContextCard
import com.purride.pixellauncherv2.launcher.HomeLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.launcher.TextListRuntimeState
import com.purride.pixellauncherv2.launcher.TextListSupport
import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.HorizontalPageController
import com.purride.pixellauncherv2.render.HorizontalPageSnapshot
import com.purride.pixellauncherv2.render.HorizontalPageState
import com.purride.pixellauncherv2.render.IdleMaskFrame
import com.purride.pixellauncherv2.render.LauncherAnimationState
import com.purride.pixellauncherv2.render.IdleFluidState
import com.purride.pixellauncherv2.render.IdleSimulationProfile
import com.purride.pixellauncherv2.render.ChargeIdleEffect
import com.purride.pixellauncherv2.render.ChargeIdleEffectRegistry
import com.purride.pixellauncherv2.render.PixelFrameView
import com.purride.pixellauncherv2.render.PixelGlDisplayView
import com.purride.pixellauncherv2.render.PixelDisplayView
import com.purride.pixellauncherv2.render.PixelFontEngine
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelFontResolver
import com.purride.pixellauncherv2.render.PixelPalette
import com.purride.pixellauncherv2.render.RenderPerfLogger
import com.purride.pixellauncherv2.render.PixelRenderer
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.IdleFluidEngine
import com.purride.pixellauncherv2.render.IdleFluidTuning
import com.purride.pixellauncherv2.system.AndroidAppLauncher
import com.purride.pixellauncherv2.system.ScreenGravityMapper
import com.purride.pixellauncherv2.system.WindowModeController
import com.purride.pixellauncherv2.util.TerminalStatusProvider
import com.purride.pixellauncherv2.util.ThrottleClickHelper
import com.purride.pixellauncherv2.util.TimeTextProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 启动器运行时的总编排入口。
 *
 * 它持有各类仓库，把 Android 输入转换成 [LauncherState] 变化，驱动动画 ticker，
 * 并要求 [PixelRenderer] 把当前状态重新绘制到 [PixelFrameView]。
 */
class MainActivity : AppCompatActivity(), PixelFrameView.InteractionListener {

    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeTextProvider = TimeTextProvider()
    private val throttleClickHelper = ThrottleClickHelper()
    private val terminalStatusProvider = TerminalStatusProvider()

    private lateinit var appRepository: AppRepository
    private lateinit var fontSettingsRepository: FontSettingsRepository
    private lateinit var launcherStatsRepository: LauncherStatsRepository
    private lateinit var deviceStatusRepository: DeviceStatusRepository
    private lateinit var nextAlarmRepository: NextAlarmRepository
    private lateinit var screenUsageRepository: ScreenUsageRepository
    private lateinit var communicationStatusRepository: CommunicationStatusRepository
    private lateinit var deviceLocationRepository: DeviceLocationRepository
    private lateinit var rainForecastRepository: RainForecastRepository
    private lateinit var deviceMotionRepository: DeviceMotionRepository
    private lateinit var pixelFontResolver: PixelFontResolver
    private lateinit var appLauncher: AndroidAppLauncher
    private lateinit var windowModeController: WindowModeController
    private lateinit var pixelFontEngine: PixelFontEngine
    private lateinit var pixelRenderer: PixelRenderer
    private lateinit var pixelFrameView: PixelFrameView
    private lateinit var idlePhysicsThread: HandlerThread
    private lateinit var idlePhysicsHandler: Handler
    private lateinit var drawerInputProxy: EditText

    private var screenProfile: ScreenProfile = ScreenProfileFactory.create(widthPx = 1, heightPx = 1)
    private var palette: PixelPalette = PixelPalette.terminalGreen()
    private var pixelShape: PixelShape = PixelShape.SQUARE
    private var dotSizePx: Int = ScreenProfileFactory.defaultDotSizePx
    private var selectedTheme: PixelTheme = PixelTheme.GREEN_PHOSPHOR
    private var state = LauncherState()
    private var animationState = LauncherAnimationState()
    private var loadGeneration = 0
    private var launchPending = false
    private var launchRunnable: Runnable? = null
    private val idleFluidEngine = IdleFluidEngine(tuning = IdleFluidTuning.default())
    private var motionSnapshot = DeviceMotionSnapshot()
    private var idlePhysicsAccumulatorMs: Long = 0L
    private var idlePhysicsLastTickUptimeMs: Long = 0L
    private var idlePhysicsRunning = false
    private var idlePhysicsState = state.idleFluidState
    @Volatile
    private var idleGravityX: Float = 0f
    @Volatile
    private var idleGravityY: Float = DeviceMotionSnapshot.staticGravityMagnitude
    @Volatile
    private var idleBatteryLevelSnapshot: Int = state.batteryLevel
    @Volatile
    private var idleLogicalWidthSnapshot: Int = screenProfile.logicalWidth
    @Volatile
    private var idleLogicalHeightSnapshot: Int = screenProfile.logicalHeight
    @Volatile
    private var idleSimulationWidthSnapshot: Int = screenProfile.logicalWidth
    @Volatile
    private var idleSimulationHeightSnapshot: Int = screenProfile.logicalHeight
    @Volatile
    private var idleChargeIdleEffectSnapshot: ChargeIdleEffect = state.chargeIdleEffect
    private var idlePhysicsSyncedBatteryLevel: Int = Int.MIN_VALUE
    private var idlePhysicsSyncedSimulationWidth: Int = -1
    private var idlePhysicsSyncedSimulationHeight: Int = -1
    private var idleMaskSequence: Long = 0L
    private var idleChargeMaskFrame: IdleMaskFrame? = null
    private var syncingDrawerInputProxyText = false
    private var drawerRailDragLastY: Int = 0
    private var drawerRailDragAccumulatorPx: Float = 0f
    private var drawerRailPixelsPerApp: Float = 1f
    private var drawerListDragTracking = false
    private var drawerListDragConsumed = false
    private var drawerListDragStartX = 0
    private var drawerListDragStartY = 0
    private var drawerListDragLastY = 0
    private var drawerListDragLastUptimeMs: Long = 0L
    private var drawerListDragVelocityPxPerSecond: Float = 0f
    private var drawerListScrollResidualOffsetPx: Float = 0f
    private var drawerListScrollVelocityPxPerSecond: Float = 0f
    private var drawerListScrollAnimating = false
    private var drawerSettleTarget: DrawerSettleTarget? = null
    private var settingsListDragTracking = false
    private var settingsListDragConsumed = false
    private var settingsListDragStartX = 0
    private var settingsListDragStartY = 0
    private var settingsListDragLastY = 0
    private var settingsListDragLastUptimeMs: Long = 0L
    private var settingsListDragVelocityPxPerSecond: Float = 0f
    private var settingsListScrollResidualOffsetPx: Float = 0f
    private var settingsListScrollVelocityPxPerSecond: Float = 0f
    private var settingsListScrollAnimating = false
    private var settingsSettleTarget: DrawerSettleTarget? = null
    private val horizontalPageController = HorizontalPageController()
    private var horizontalPageState: HorizontalPageState = horizontalPageController.create(
        pageCount = pagerPageCount,
        currentIndex = pagerHomeIndex,
    )
    private var pagerDragTracking = false
    private var pagerDragConsumed = false
    private var pagerDragLastX = 0
    private var pagerDragLastY = 0
    private var pagerDragStartX = 0
    private var pagerDragStartY = 0
    private var pagerDragLastUptimeMs: Long = 0L
    private var pagerDragVelocityPxPerSecond: Float = 0f
    private var interactionTickerLastUptimeMs: Long = 0L
    private var usageAccessPromptShown = false
    private var homeDataPermissionPromptShown = false
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

    private val interactionTicker = object : Runnable {
        override fun run() {
            if (!shouldRunInteractionTicker()) {
                interactionTickerLastUptimeMs = 0L
                return
            }
            val now = SystemClock.uptimeMillis()
            val deltaMs = if (interactionTickerLastUptimeMs <= 0L) {
                interactionFrameDelayMs
            } else {
                (now - interactionTickerLastUptimeMs).coerceIn(1L, interactionMaxFrameDeltaMs)
            }
            interactionTickerLastUptimeMs = now

            if (shouldAnimateDrawerListScroll()) {
                stepDrawerVerticalListAnimation(deltaMs)
            }
            if (shouldAnimateSettingsListScroll()) {
                stepSettingsVerticalListAnimation(deltaMs)
            }
            val wasPagerSettling = horizontalPageState.isSettling
            horizontalPageState = horizontalPageController.step(
                state = horizontalPageState,
                deltaMs = deltaMs,
            )
            if (wasPagerSettling && !horizontalPageState.isSettling) {
                applyPagerSettledMode(horizontalPageState.currentIndex)
            }
            renderCurrentFrame()
            if (shouldRunInteractionTicker()) {
                mainHandler.postDelayed(this, interactionFrameDelayMs)
            } else {
                interactionTickerLastUptimeMs = 0L
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
            syncIdleFluidWithBattery()
            startIdlePhysics()
            renderCurrentFrame()
        } else {
            scheduleIdleCheck()
        }
    }

    private val idlePhysicsTicker = object : Runnable {
        override fun run() {
            if (!idlePhysicsRunning) {
                return
            }
            val now = SystemClock.uptimeMillis()
            if (idlePhysicsLastTickUptimeMs <= 0L) {
                idlePhysicsLastTickUptimeMs = now
            }
            val frameDelta = (now - idlePhysicsLastTickUptimeMs).coerceIn(0L, idleMaxAccumulationMs)
            idlePhysicsLastTickUptimeMs = now
            idlePhysicsAccumulatorMs = (idlePhysicsAccumulatorMs + frameDelta)
                .coerceAtMost(idleFixedStepMs * idleMaxCatchUpSteps)

            var steps = 0
            RenderPerfLogger.measure("idle.physics.tick") {
                while (idlePhysicsAccumulatorMs >= idleFixedStepMs && steps < idleMaxCatchUpSteps) {
                    val stepNow = now - idlePhysicsAccumulatorMs + idleFixedStepMs
                    if (shouldUseChargeIdleEffectOnPhysics()) {
                        stepChargeIdleEffectOnPhysics(stepNow)
                    } else {
                        stepIdleFluidOnPhysics(stepNow)
                    }
                    idlePhysicsAccumulatorMs -= idleFixedStepMs
                    steps += 1
                }
            }
            if (steps > 1) {
                RenderPerfLogger.record("idle.physics.catchup", 0L)
            }
            if (steps >= idleMaxCatchUpSteps && idlePhysicsAccumulatorMs >= idleFixedStepMs) {
                RenderPerfLogger.record("idle.physics.catchup.saturated", 0L)
                idlePhysicsAccumulatorMs = 0L
            }
            if (steps > 0) {
                if (usesGlIdleComposite()) {
                    RenderPerfLogger.measure("idle.mask.submit") {
                        pixelFrameView.submitIdleMask(currentIdleDynamicMaskFrame())
                    }
                } else {
                    dispatchIdleStateToMain(currentIdleRenderState())
                }
            }
            if (idlePhysicsRunning) {
                idlePhysicsHandler.postDelayed(this, idleTickerDelayMs)
            }
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
        deviceLocationRepository = DeviceLocationRepository(applicationContext)
        rainForecastRepository = RainForecastRepository()
        deviceMotionRepository = DeviceMotionRepository(applicationContext)
        pixelFontResolver = PixelFontResolver(applicationContext)
        val appearanceSettings = fontSettingsRepository.getAppearanceSettings()
        val uiBehaviorSettings = fontSettingsRepository.getUiBehaviorSettings()
        val resolvedPixelFont = pixelFontResolver.createFont(appearanceSettings.fontId)
        pixelFontEngine = resolvedPixelFont.engine
        pixelShape = appearanceSettings.pixelShape
        dotSizePx = appearanceSettings.dotSizePx
        selectedTheme = appearanceSettings.theme
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedFontId = appearanceSettings.fontId,
            selectedPixelShape = appearanceSettings.pixelShape,
            selectedDotSizePx = appearanceSettings.dotSizePx,
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

        pixelRenderer = PixelRenderer(pixelFontEngine)
        appLauncher = AndroidAppLauncher(this)
        windowModeController = WindowModeController(window)
        windowModeController.hideSystemBars()
        idlePhysicsThread = HandlerThread("IdlePhysics")
        idlePhysicsThread.start()
        idlePhysicsHandler = Handler(idlePhysicsThread.looper)
        updatePalette()
        val metrics = resources.displayMetrics
        screenProfile = ScreenProfileFactory.create(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            dotSizePx = dotSizePx,
            pixelShape = pixelShape,
        )
        syncIdleFluidWithBattery()
        pixelFrameView = createPixelFrameView().apply {
            interactionListener = this@MainActivity
            setPalette(palette)
        }
        pixelFrameView.asView().apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        drawerInputProxy = EditText(this).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START)
            alpha = 0f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
                if (source == null) {
                    return@InputFilter null
                }
                val filtered = DrawerAsciiInputSanitizer.filter(source.subSequence(start, end).toString())
                if (filtered.length == end - start) {
                    null
                } else {
                    filtered
                }
            })
            isFocusable = true
            isFocusableInTouchMode = true
            setText(state.drawerQuery)
            setSelection(text?.length ?: 0)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || state.mode != LauncherMode.APP_DRAWER) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_SPACE -> onKeyDown(keyCode, event)

                    else -> false
                }
            }
            setOnEditorActionListener { _, _, event ->
                if (state.mode != LauncherMode.APP_DRAWER || state.drawerQuery.isBlank()) {
                    return@setOnEditorActionListener false
                }
                val isEnterAction = event == null ||
                    event.action == KeyEvent.ACTION_DOWN &&
                    (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                if (!isEnterAction) {
                    return@setOnEditorActionListener false
                }
                launchAppAtIndex(0)
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (syncingDrawerInputProxyText || state.mode != LauncherMode.APP_DRAWER) {
                        return
                    }
                    val query = s?.toString().orEmpty()
                    val filteredQuery = DrawerAsciiInputSanitizer.filter(query)
                    if (filteredQuery != query) {
                        syncingDrawerInputProxyText = true
                        setText(filteredQuery)
                        setSelection(filteredQuery.length)
                        syncingDrawerInputProxyText = false
                        return
                    }
                    if (query == state.drawerQuery) {
                        return
                    }
                    recordInteraction()
                    val focusedState = if (state.isDrawerSearchFocused && !state.isDrawerRailSliding) {
                        state
                    } else {
                        state.copy(
                            isDrawerSearchFocused = true,
                            isDrawerRailSliding = false,
                        )
                    }
                    settleDrawerMotionBeforeExplicitAction()
                    state = LauncherStateTransitions.updateDrawerQuery(
                        state = focusedState,
                        query = query,
                        visibleRows = visibleRows(),
                    )
                    if (query.isNotBlank() && currentDrawerApps().size == 1) {
                        launchSelectedApp()
                        return
                    }
                    renderCurrentFrame()
                    startAnimationTickerIfNeeded()
                }
            })
        }
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(
                pixelFrameView.asView(),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(drawerInputProxy)
        }
        setContentView(rootContainer)
        updateDrawerInputFocus()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (state.mode) {
                    LauncherMode.SETTINGS -> closeSettingsMenu()
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
        pixelFrameView.asView().addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newWidth = right - left
            val newHeight = bottom - top
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop

            if (newWidth > 0 && newHeight > 0 && (newWidth != oldWidth || newHeight != oldHeight)) {
                updateScreenProfile(newWidth, newHeight)
            }
        }

        renderCurrentFrame()
        suppressActivityAnimations()
    }

    /**
     * 在回到前台时重启监听器，并刷新设备状态、Home 数据和天气等前台专属工作。
     */
    override fun onResume() {
        super.onResume()
        if (::pixelFrameView.isInitialized) {
            pixelFrameView.onHostResume()
        }
        windowModeController.hideSystemBars()
        startClockTicker()
        deviceStatusRepository.start(::onDeviceStatusChanged)
        nextAlarmRepository.start(::onNextAlarmChanged)
        resetDrawerVerticalGesture()
        state = LauncherStateTransitions.showHome(state)
        state = LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis())
        refreshDerivedUiState(render = false)
        val launchedUsageAccessSettings = maybeRequestUsageAccess()
        if (!launchedUsageAccessSettings) {
            communicationStatusRepository.start(::onCommunicationStatusChanged)
            refreshScreenUsageSummary(render = false)
            refreshRainHint(force = true, render = false)
            maybeRequestHomeDataPermissions()
        }
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        updateDrawerInputFocus()
        scheduleIdleCheck()
        loadApps()
        suppressActivityAnimations()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowModeController.hideSystemBars()
            updateDrawerInputFocus()
        }
    }

    /**
     * 在进入后台时停止前台监听器和不应继续运行的临时 UI 工作。
     */
    override fun onPause() {
        hideDrawerKeyboard()
        resetPagerDragTracking()
        resetDrawerVerticalGesture()
        if (::drawerInputProxy.isInitialized) {
            drawerInputProxy.clearFocus()
        }
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.removeCallbacks(interactionTicker)
        mainHandler.removeCallbacks(idleRunnable)
        interactionTickerLastUptimeMs = 0L
        launchRunnable?.let(mainHandler::removeCallbacks)
        launchRunnable = null
        launchPending = false
        deviceStatusRepository.stop()
        nextAlarmRepository.stop()
        communicationStatusRepository.stop()
        stopIdlePhysics()
        suppressActivityAnimations()
        if (::pixelFrameView.isInitialized) {
            pixelFrameView.onHostPause()
        }
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
        if (requestCode != homeDataPermissionRequestCode) {
            return
        }
        communicationStatusRepository.start(::onCommunicationStatusChanged)
        refreshCommunicationStatus(render = false)
        refreshRainHint(force = true, render = true)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (::idlePhysicsHandler.isInitialized) {
            idlePhysicsHandler.removeCallbacksAndMessages(null)
        }
        if (::idlePhysicsThread.isInitialized) {
            idlePhysicsThread.quitSafely()
        }
        backgroundExecutor.shutdownNow()
        pixelFontEngine.clearCache()
        super.onDestroy()
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
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        moveSettingsSelection(-1)
                    }
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.HOME,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (state.mode) {
                    LauncherMode.APP_DRAWER -> {
                        settleDrawerMotionBeforeExplicitAction()
                        moveSelection(1)
                    }
                    LauncherMode.SETTINGS -> {
                        settleSettingsMotionBeforeExplicitAction()
                        moveSettingsSelection(1)
                    }
                    LauncherMode.HOME -> showAppDrawer()
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
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

    /**
     * 把逻辑点击事件路由到当前页面，依赖布局层 hit-test，而不是 Android 原生控件树。
     */
    override fun onLogicalTap(x: Int, y: Int) {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
        when (state.mode) {
            LauncherMode.APP_DRAWER -> {
                val tappedHeader = AppListLayout.hitTestDrawerHeaderSearchArea(
                    screenProfile = screenProfile,
                    logicalX = x,
                    logicalY = y,
                )
                if (tappedHeader) {
                    settleDrawerMotionBeforeExplicitAction()
                    if (!state.isDrawerSearchFocused) {
                        state = state.copy(
                            isDrawerSearchFocused = true,
                            isDrawerRailSliding = false,
                        )
                        renderCurrentFrame()
                        startAnimationTickerIfNeeded()
                        updateDrawerInputFocus()
                    }
                    return
                }
                val canTapResultList = !state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()
                val tappedIndex = if (canTapResultList) {
                    AppListLayout.hitTestAppIndex(
                        screenProfile = screenProfile,
                        state = state,
                        logicalX = x,
                        logicalY = y,
                        drawerListScrollOffsetPx = drawerListScrollResidualOffsetPx.toInt(),
                    )
                } else {
                    null
                }
                val decision = DrawerContentTapResolver.resolve(state, tappedIndex)
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

            LauncherMode.SETTINGS -> {
                val rows = SettingsMenuModel.rows(state)
                val tappedRow = SettingsMenuLayout.hitTestRow(
                    screenProfile = screenProfile,
                    logicalX = x,
                    logicalY = y,
                    rowCount = rows.size,
                    listStartIndex = state.settingsListStartIndex,
                    scrollOffsetPx = settingsListScrollResidualOffsetPx.toInt(),
                ) ?: return

                settleSettingsMotionBeforeExplicitAction()
                state = LauncherStateTransitions.selectSettingsIndex(
                    state = state,
                    index = tappedRow,
                    visibleRows = settingsVisibleRows(),
                )
                activateSelectedSetting()
            }

            LauncherMode.HOME -> {
                Unit
            }

            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE -> Unit
        }
    }

    /**
     * 根据当前命中的交互区域，启动抽屉、设置页或横向 pager 的拖动流程。
     */
    override fun onLogicalDragStart(x: Int, y: Int): Boolean {
        if (launchPending || animationState.bootSequence != null) {
            return false
        }
        if (wakeIfIdle()) {
            return true
        }
        resetPagerDragTracking()
        resetDrawerVerticalDragTracking()
        resetSettingsVerticalDragTracking()

        if (state.mode == LauncherMode.APP_DRAWER) {
            val railHit = !state.isDrawerSearchFocused &&
                AppListLayout.hitTestIndexRailLetter(
                    screenProfile = screenProfile,
                    logicalX = x,
                    logicalY = y,
                ) != null
            if (railHit) {
                recordInteraction()
                settleDrawerMotionBeforeExplicitAction()
                state = state.copy(isDrawerRailSliding = true)
                drawerRailDragLastY = y
                drawerRailDragAccumulatorPx = 0f
                drawerRailPixelsPerApp = resolveRailPixelsPerApp(currentDrawerApps().size)
                renderCurrentFrame()
                startAnimationTickerIfNeeded()
                updateDrawerInputFocus()
                return true
            }
        }

        if (state.mode == LauncherMode.APP_DRAWER &&
            shouldShowDrawerScrollableList() &&
            isPointInDrawerScrollableListArea(x, y)
        ) {
            drawerListDragTracking = true
            drawerListDragConsumed = false
            drawerListDragStartX = x
            drawerListDragStartY = y
            drawerListDragLastY = y
            drawerListDragLastUptimeMs = SystemClock.uptimeMillis()
            drawerListDragVelocityPxPerSecond = 0f
            stopDrawerVerticalListAnimation(resetOffset = false)
        }

        if (state.mode == LauncherMode.SETTINGS &&
            shouldShowSettingsScrollableList() &&
            isPointInSettingsScrollableListArea(x, y)
        ) {
            settingsListDragTracking = true
            settingsListDragConsumed = false
            settingsListDragStartX = x
            settingsListDragStartY = y
            settingsListDragLastY = y
            settingsListDragLastUptimeMs = SystemClock.uptimeMillis()
            settingsListDragVelocityPxPerSecond = 0f
            stopSettingsVerticalListAnimation(resetOffset = false)
        }

        if (canHandlePagerNavigation()) {
            pagerDragTracking = true
            pagerDragConsumed = false
            pagerDragStartX = x
            pagerDragStartY = y
            pagerDragLastX = x
            pagerDragLastY = y
            pagerDragLastUptimeMs = SystemClock.uptimeMillis()
            pagerDragVelocityPxPerSecond = 0f
        }
        return false
    }

    /**
     * 推进当前拖动目标，把拖动位移转换成列表滚动、rail 快速定位或横向分页移动。
     */
    override fun onLogicalDragMove(x: Int, y: Int): Boolean {
        if (state.mode == LauncherMode.APP_DRAWER && state.isDrawerRailSliding) {
            recordInteraction()
            val deltaY = y - drawerRailDragLastY
            drawerRailDragLastY = y
            if (deltaY == 0) {
                return true
            }
            val dragResult = DrawerRailDragMapper.consumeDrag(
                accumulatedPx = drawerRailDragAccumulatorPx,
                deltaPx = deltaY.toFloat(),
                pixelsPerApp = drawerRailPixelsPerApp,
            )
            drawerRailDragAccumulatorPx = dragResult.accumulatedPx

            var moved = false
            val stepDirection = when {
                dragResult.stepDelta > 0 -> 1
                dragResult.stepDelta < 0 -> -1
                else -> 0
            }
            repeat(kotlin.math.abs(dragResult.stepDelta)) {
                state = LauncherStateTransitions.moveSelection(
                    state = state,
                    delta = stepDirection,
                    visibleRows = visibleRows(),
                )
                moved = true
            }
            if (moved) {
                renderCurrentFrame()
                startAnimationTickerIfNeeded()
            }
            return true
        }

        if (drawerListDragTracking) {
            val totalDx = x - drawerListDragStartX
            val totalDy = y - drawerListDragStartY
            if (!drawerListDragConsumed) {
                if (kotlin.math.abs(totalDy) >= drawerListDragStartThresholdPx &&
                    kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) * drawerListDragAxisBias
                ) {
                    drawerListDragConsumed = true
                    resetPagerDragTracking()
                }
            }

            if (drawerListDragConsumed) {
                recordInteraction()
                val now = SystemClock.uptimeMillis()
                val deltaY = y - drawerListDragLastY
                val elapsedMs = (now - drawerListDragLastUptimeMs).coerceAtLeast(1L)
                drawerListDragLastY = y
                drawerListDragLastUptimeMs = now
                if (deltaY != 0) {
                    if (isOutwardBoundaryDelta(deltaY.toFloat())) {
                        val hadMotion = drawerListScrollResidualOffsetPx != 0f ||
                            drawerListScrollVelocityPxPerSecond != 0f ||
                            drawerListScrollAnimating
                        stopDrawerVerticalListAnimation(resetOffset = true)
                        drawerListDragVelocityPxPerSecond = 0f
                        if (hadMotion) {
                            renderCurrentFrame()
                            startAnimationTickerIfNeeded()
                        }
                        return true
                    }
                    val thresholds = drawerListScrollThresholds()
                    val dragResult = DrawerVerticalScrollController.consumeDrag(
                        residualOffsetPx = drawerListScrollResidualOffsetPx,
                        deltaPx = deltaY.toFloat(),
                        thresholds = thresholds,
                    )
                    drawerListScrollResidualOffsetPx = dragResult.residualOffsetPx
                    drawerListDragVelocityPxPerSecond = (deltaY.toFloat() * 1000f) / elapsedMs.toFloat()
                    drawerSettleTarget = null
                    clampDrawerMotionAtListBounds()
                    if (dragResult.stepDelta != 0) {
                        applyDrawerVerticalStepDelta(dragResult.stepDelta)
                    } else {
                        renderCurrentFrame()
                        startAnimationTickerIfNeeded()
                    }
                }
                return true
            }
        }

        if (settingsListDragTracking) {
            val totalDx = x - settingsListDragStartX
            val totalDy = y - settingsListDragStartY
            if (!settingsListDragConsumed) {
                if (kotlin.math.abs(totalDy) >= drawerListDragStartThresholdPx &&
                    kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) * drawerListDragAxisBias
                ) {
                    settingsListDragConsumed = true
                    resetPagerDragTracking()
                }
            }

            if (settingsListDragConsumed) {
                recordInteraction()
                val now = SystemClock.uptimeMillis()
                val deltaY = y - settingsListDragLastY
                val elapsedMs = (now - settingsListDragLastUptimeMs).coerceAtLeast(1L)
                settingsListDragLastY = y
                settingsListDragLastUptimeMs = now
                if (deltaY != 0) {
                    if (isOutwardSettingsBoundaryDelta(deltaY.toFloat())) {
                        val hadMotion = settingsListScrollResidualOffsetPx != 0f ||
                            settingsListScrollVelocityPxPerSecond != 0f ||
                            settingsListScrollAnimating
                        stopSettingsVerticalListAnimation(resetOffset = true)
                        settingsListDragVelocityPxPerSecond = 0f
                        if (hadMotion) {
                            renderCurrentFrame()
                            startAnimationTickerIfNeeded()
                        }
                        return true
                    }
                    val thresholds = settingsListScrollThresholds()
                    val dragResult = DrawerVerticalScrollController.consumeDrag(
                        residualOffsetPx = settingsListScrollResidualOffsetPx,
                        deltaPx = deltaY.toFloat(),
                        thresholds = thresholds,
                    )
                    settingsListScrollResidualOffsetPx = dragResult.residualOffsetPx
                    settingsListDragVelocityPxPerSecond = (deltaY.toFloat() * 1000f) / elapsedMs.toFloat()
                    settingsSettleTarget = null
                    clampSettingsMotionAtListBounds()
                    if (dragResult.stepDelta != 0) {
                        applySettingsVerticalStepDelta(dragResult.stepDelta)
                    } else {
                        renderCurrentFrame()
                        startAnimationTickerIfNeeded()
                    }
                }
                return true
            }
        }

        if (!pagerDragTracking || !canHandlePagerNavigation()) {
            return false
        }

        val now = SystemClock.uptimeMillis()
        val deltaX = x - pagerDragLastX
        val deltaY = y - pagerDragLastY
        pagerDragLastX = x
        pagerDragLastY = y

        if (!pagerDragConsumed) {
            val totalDx = x - pagerDragStartX
            val totalDy = y - pagerDragStartY
            if (kotlin.math.abs(totalDx) < pagerDragStartThresholdPx) {
                return false
            }
            if (kotlin.math.abs(totalDx) <= kotlin.math.abs(totalDy) * pagerDragAxisBias) {
                return false
            }
            val currentPageIndex = pagerIndexForMode(state.mode) ?: return false
            if (state.mode == LauncherMode.APP_DRAWER) {
                settleDrawerMotionBeforeExplicitAction()
            } else if (state.mode == LauncherMode.SETTINGS) {
                settleSettingsMotionBeforeExplicitAction()
            }
            horizontalPageState = horizontalPageController.syncToIndex(
                state = horizontalPageState,
                targetIndex = currentPageIndex,
            )
            horizontalPageState = horizontalPageController.startDrag(horizontalPageState)
            pagerDragConsumed = true
        }
        if (!pagerDragConsumed) {
            return false
        }

        recordInteraction()
        horizontalPageState = horizontalPageController.dragBy(
            state = horizontalPageState,
            deltaPx = deltaX.toFloat(),
            pageWidth = screenProfile.logicalWidth,
        )
        val elapsedMs = (now - pagerDragLastUptimeMs).coerceAtLeast(1L)
        if (deltaX != 0) {
            pagerDragVelocityPxPerSecond = (deltaX.toFloat() * 1000f) / elapsedMs.toFloat()
        }
        pagerDragLastUptimeMs = now
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        return true
    }

    /**
     * 结束当前拖动；如果手势已被消费，则把残余速度交给共享 settle 逻辑继续处理。
     */
    override fun onLogicalDragEnd(x: Int, y: Int, cancelled: Boolean): Boolean {
        if (state.mode == LauncherMode.APP_DRAWER && state.isDrawerRailSliding) {
            recordInteraction()
            state = state.copy(isDrawerRailSliding = false)
            drawerRailDragLastY = y
            drawerRailDragAccumulatorPx = 0f
            drawerRailPixelsPerApp = 1f
            renderCurrentFrame()
            startAnimationTickerIfNeeded()
            updateDrawerInputFocus()
            return true
        }

        if (drawerListDragTracking) {
            val consumed = drawerListDragConsumed
            if (consumed) {
                recordInteraction()
                val releaseVelocity = if (cancelled) 0f else drawerListDragVelocityPxPerSecond
                val releaseState = DrawerVerticalScrollController.release(
                    residualOffsetPx = drawerListScrollResidualOffsetPx,
                    velocityPxPerSecond = releaseVelocity,
                    thresholds = drawerListScrollThresholds(),
                )
                drawerListScrollResidualOffsetPx = releaseState.residualOffsetPx
                drawerListScrollVelocityPxPerSecond = releaseState.nextVelocityPxPerSecond
                drawerSettleTarget = releaseState.settleTarget
                drawerListScrollAnimating = releaseState.isAnimating
                clampDrawerMotionAtListBounds()
                renderCurrentFrame()
                startAnimationTickerIfNeeded()
            }
            resetDrawerVerticalDragTracking()
            if (!consumed) {
                drawerSettleTarget = null
            }
            if (consumed) {
                return true
            }
        }

        if (settingsListDragTracking) {
            val consumed = settingsListDragConsumed
            if (consumed) {
                recordInteraction()
                val releaseVelocity = if (cancelled) 0f else settingsListDragVelocityPxPerSecond
                val releaseState = DrawerVerticalScrollController.release(
                    residualOffsetPx = settingsListScrollResidualOffsetPx,
                    velocityPxPerSecond = releaseVelocity,
                    thresholds = settingsListScrollThresholds(),
                )
                settingsListScrollResidualOffsetPx = releaseState.residualOffsetPx
                settingsListScrollVelocityPxPerSecond = releaseState.nextVelocityPxPerSecond
                settingsSettleTarget = releaseState.settleTarget
                settingsListScrollAnimating = releaseState.isAnimating
                clampSettingsMotionAtListBounds()
                renderCurrentFrame()
                startAnimationTickerIfNeeded()
            }
            resetSettingsVerticalDragTracking()
            if (!consumed) {
                settingsSettleTarget = null
            }
            if (consumed) {
                return true
            }
        }

        if (!pagerDragTracking) {
            return false
        }
        val consumed = pagerDragConsumed
        if (consumed) {
            recordInteraction()
            horizontalPageState = horizontalPageController.endDrag(
                state = horizontalPageState,
                pageWidth = screenProfile.logicalWidth,
                velocityPxPerSecond = if (cancelled) 0f else pagerDragVelocityPxPerSecond,
            )
            renderCurrentFrame()
            startAnimationTickerIfNeeded()
        }
        resetPagerDragTracking()
        return consumed
    }

    override fun onSwipeUp() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
    }

    override fun onSwipeDown() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
    }

    override fun onSwipeLeft() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
        if (state.mode == LauncherMode.APP_DRAWER) {
            settleDrawerMotionBeforeExplicitAction()
        }
        if (animatePagerBy(deltaPages = -1)) {
            return
        }
        when (state.mode) {
            LauncherMode.DIAGNOSTICS,
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
            LauncherMode.IDLE -> Unit
        }
    }

    override fun onSwipeRight() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
        if (state.mode == LauncherMode.APP_DRAWER) {
            settleDrawerMotionBeforeExplicitAction()
        }
        if (animatePagerBy(deltaPages = 1)) {
            return
        }
        when (state.mode) {
            LauncherMode.DIAGNOSTICS -> closeDiagnostics()
            LauncherMode.SETTINGS,
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE -> Unit
        }
    }

    /**
     * 在后台线程加载可启动应用，并在结果返回后刷新派生 UI 状态。
     */
    private fun loadApps() {
        val generation = ++loadGeneration
        state = state.copy(isLoading = true)
        renderCurrentFrame()

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
        if (usesGlIdleComposite()) {
            RenderPerfLogger.measure("main.render.idleStatic.total") {
                renderIdleStaticFrame()
            }
            return
        }
        RenderPerfLogger.measure("main.render.full.${state.mode.name}.total") {
            syncPagerIndexToMode()
            val pagerSnapshot = activePagerSnapshot()
            val pixelBuffer = RenderPerfLogger.measure("main.render.full.${state.mode.name}.compose") {
                pixelRenderer.render(
                    state = state,
                    screenProfile = screenProfile,
                    animationState = animationState,
                    pagerSnapshot = pagerSnapshot,
                    drawerListScrollOffsetPx = if (state.mode == LauncherMode.APP_DRAWER) {
                        drawerListScrollResidualOffsetPx.toInt()
                    } else {
                        0
                    },
                    settingsListScrollOffsetPx = if (state.mode == LauncherMode.SETTINGS) {
                        settingsListScrollResidualOffsetPx.toInt()
                    } else {
                        0
                    },
                )
            }
            RenderPerfLogger.measure("main.render.full.${state.mode.name}.submit") {
                pixelFrameView.submitFrame(
                    pixelBuffer = pixelBuffer,
                    screenProfile = screenProfile,
                    palette = palette,
                )
            }
        }
    }

    private fun renderIdleStaticFrame() {
        val pixelBuffer = RenderPerfLogger.measure("main.render.idleStatic.compose") {
            pixelRenderer.renderIdleStatic(
                state = state,
                screenProfile = screenProfile,
            )
        }
        RenderPerfLogger.measure("main.render.idleStatic.submit") {
            pixelFrameView.submitFrame(
                pixelBuffer = pixelBuffer,
                screenProfile = screenProfile,
                palette = palette,
            )
        }
    }

    private fun usesGlIdleComposite(): Boolean {
        return state.mode == LauncherMode.IDLE && pixelFrameView is PixelGlDisplayView
    }

    private fun createPixelFrameView(): PixelFrameView {
        val activityManager = getSystemService(ActivityManager::class.java)
        val supportsGles2 = (activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= REQUIRED_GLES_VERSION
        if (!supportsGles2) {
            RenderPerfLogger.mark("frameView", "backend=canvas reason=no_gles2")
            return PixelDisplayView(this)
        }
        return runCatching {
            RenderPerfLogger.mark("frameView", "backend=gl")
            PixelGlDisplayView(this)
        }.getOrElse {
            RenderPerfLogger.mark("frameView", "backend=canvas reason=gl_init_failed")
            PixelDisplayView(this)
        }
    }

    private fun updateScreenProfile(widthPx: Int, heightPx: Int): Boolean {
        val newProfile = ScreenProfileFactory.create(
            widthPx = widthPx,
            heightPx = heightPx,
            dotSizePx = dotSizePx,
            pixelShape = pixelShape,
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
        syncIdleFluidWithBattery()
        renderCurrentFrame()
        return true
    }

    private fun visibleRows(): Int {
        return AppListLayout.metrics(screenProfile).textList.viewport.visibleRows
    }

    private fun settingsVisibleRows(): Int {
        return SettingsMenuLayout.metrics(screenProfile).textList.viewport.visibleRows
    }

    private fun syncPagerIndexToMode() {
        val modeIndex = pagerIndexForMode(state.mode) ?: return
        if (horizontalPageController.isActive(horizontalPageState)) {
            return
        }
        horizontalPageState = horizontalPageController.syncToIndex(
            state = horizontalPageState,
            targetIndex = modeIndex,
        )
    }

    private fun activePagerSnapshot(): HorizontalPageSnapshot? {
        if (!horizontalPageController.isActive(horizontalPageState)) {
            return null
        }
        return horizontalPageController.snapshot(horizontalPageState)
    }

    private fun pagerIndexForMode(mode: LauncherMode): Int? {
        return when (mode) {
            LauncherMode.SETTINGS -> pagerSettingsIndex
            LauncherMode.HOME -> pagerHomeIndex
            LauncherMode.APP_DRAWER -> pagerAppsIndex
            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE -> null
        }
    }

    private fun pagerModeForIndex(index: Int): LauncherMode? {
        return when (index) {
            pagerSettingsIndex -> LauncherMode.SETTINGS
            pagerHomeIndex -> LauncherMode.HOME
            pagerAppsIndex -> LauncherMode.APP_DRAWER
            else -> null
        }
    }

    private fun isPagerMode(mode: LauncherMode): Boolean {
        return pagerIndexForMode(mode) != null
    }

    private fun canHandlePagerNavigation(): Boolean {
        if (!isPagerMode(state.mode)) {
            return false
        }
        if (state.mode == LauncherMode.APP_DRAWER && state.isDrawerRailSliding) {
            return false
        }
        return true
    }

    /**
     * 在横向 pager settle 完成后，应用对应页面模式并同步页面相关状态。
     */
    private fun applyPagerSettledMode(pageIndex: Int) {
        val targetMode = pagerModeForIndex(pageIndex) ?: return
        if (state.mode == targetMode) {
            return
        }
        settleDrawerMotionBeforeExplicitAction()
        if (targetMode != LauncherMode.SETTINGS) {
            stopSettingsVerticalListAnimation(resetOffset = true)
        }
        val drawerRows = AppListLayout.metrics(screenProfile).visibleRows
        state = when (targetMode) {
            LauncherMode.HOME -> LauncherStateTransitions.showHome(state)
            LauncherMode.SETTINGS -> LauncherStateTransitions.showSettings(
                state = state,
                visibleRows = settingsVisibleRows(),
            )
            LauncherMode.APP_DRAWER -> LauncherStateTransitions.showAppDrawer(
                state = LauncherStateTransitions.clearDrawerQuery(
                    state = state,
                    visibleRows = drawerRows,
                ),
                visibleRows = drawerRows,
            ).copy(
                isDrawerSearchFocused = state.openDrawerInSearchMode,
                isDrawerRailSliding = false,
            )

            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE -> state
        }
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    /**
     * 执行一次编程式横向分页切换，并先取消与之冲突的抽屉或设置页纵向滚动。
     */
    private fun animatePagerBy(deltaPages: Int): Boolean {
        if (!canHandlePagerNavigation()) {
            return false
        }
        if (state.mode == LauncherMode.APP_DRAWER) {
            settleDrawerMotionBeforeExplicitAction()
        } else if (state.mode == LauncherMode.SETTINGS) {
            settleSettingsMotionBeforeExplicitAction()
        }
        val currentIndex = pagerIndexForMode(state.mode) ?: return false
        horizontalPageState = horizontalPageController.syncToIndex(
            state = horizontalPageState,
            targetIndex = currentIndex,
        )
        horizontalPageState = horizontalPageController.startDrag(horizontalPageState)
        horizontalPageState = horizontalPageController.dragBy(
            state = horizontalPageState,
            deltaPx = if (deltaPages > 0) screenProfile.logicalWidth.toFloat() else -screenProfile.logicalWidth.toFloat(),
            pageWidth = screenProfile.logicalWidth,
        )
        horizontalPageState = horizontalPageController.endDrag(
            state = horizontalPageState,
            pageWidth = screenProfile.logicalWidth,
            velocityPxPerSecond = 0f,
        )
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        return true
    }

    private fun resetPagerDragTracking() {
        pagerDragTracking = false
        pagerDragConsumed = false
        pagerDragVelocityPxPerSecond = 0f
        pagerDragLastUptimeMs = 0L
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
        stopIdlePhysics()
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

    private fun selectByRailLetter(letterIndex: Int) {
        val keepRailSliding = state.isDrawerRailSliding
        state = LauncherStateTransitions.selectByLetterIndex(
            state = state,
            letterIndex = letterIndex,
            visibleRows = visibleRows(),
        ).copy(isDrawerRailSliding = keepRailSliding)
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        updateDrawerInputFocus()
    }

    private fun resolveRailPixelsPerApp(appCount: Int): Float {
        val safeCount = appCount.coerceAtLeast(1)
        val railHeight = AppListLayout.metrics(screenProfile).railHeight.coerceAtLeast(1)
        return (railHeight.toFloat() / safeCount.toFloat()).coerceAtLeast(1f)
    }

    private fun syncDrawerInputProxyText() {
        if (!::drawerInputProxy.isInitialized) {
            return
        }
        val targetQuery = state.drawerQuery
        val currentText = drawerInputProxy.text?.toString().orEmpty()
        if (currentText == targetQuery) {
            return
        }
        syncingDrawerInputProxyText = true
        drawerInputProxy.setText(targetQuery)
        drawerInputProxy.setSelection(targetQuery.length)
        syncingDrawerInputProxyText = false
    }

    private fun showDrawerKeyboard() {
        if (!::drawerInputProxy.isInitialized) {
            return
        }
        drawerInputProxy.post {
            if (state.mode != LauncherMode.APP_DRAWER || !state.isDrawerSearchFocused) {
                return@post
            }
            if (!drawerInputProxy.hasFocus()) {
                drawerInputProxy.requestFocus()
            }
            drawerInputProxy.setSelection(drawerInputProxy.text?.length ?: 0)
            val inputManager = getSystemService(InputMethodManager::class.java) ?: return@post
            inputManager.showSoftInput(drawerInputProxy, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideDrawerKeyboard() {
        if (!::drawerInputProxy.isInitialized) {
            return
        }
        val inputManager = getSystemService(InputMethodManager::class.java) ?: return
        inputManager.hideSoftInputFromWindow(drawerInputProxy.windowToken, 0)
    }

    private fun updateDrawerInputFocus() {
        if (!::drawerInputProxy.isInitialized || !::pixelFrameView.isInitialized) {
            return
        }
        if (state.mode == LauncherMode.APP_DRAWER && state.isDrawerSearchFocused) {
            syncDrawerInputProxyText()
            showDrawerKeyboard()
        } else {
            hideDrawerKeyboard()
            if (drawerInputProxy.hasFocus()) {
                drawerInputProxy.clearFocus()
            }
            pixelFrameView.asView().requestFocus()
        }
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
                syncDrawerInputProxyText()
                renderCurrentFrame()
                return true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                settleDrawerMotionBeforeExplicitAction()
                state = LauncherStateTransitions.exitDrawerSearch(
                    state = state,
                    visibleRows = visibleRows(),
                )
                syncDrawerInputProxyText()
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
        syncDrawerInputProxyText()
        renderCurrentFrame()
        return true
    }

    private fun openSettingsMenu() {
        settleDrawerMotionBeforeExplicitAction()
        stopSettingsVerticalListAnimation(resetOffset = true)
        state = LauncherStateTransitions.showSettings(
            state = state,
            visibleRows = settingsVisibleRows(),
        )
        stopIdlePhysics()
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
            SettingsMenuItem.FONT -> changeSettingValue(1)
            SettingsMenuItem.RESOLUTION -> changeSettingValue(1)
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
            SettingsMenuItem.FONT -> {
                val nextFontId = SettingsMenuModel.nextFont(state.selectedFontId, direction)
                applyAppearance(
                    fontId = nextFontId,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
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
                    fontId = state.selectedFontId,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = nextDotSizePx,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.STYLE -> {
                val nextPixelShape = SettingsMenuModel.nextStyle(state.selectedPixelShape, direction)
                applyAppearance(
                    fontId = state.selectedFontId,
                    newPixelShape = nextPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
                    newTheme = state.selectedTheme,
                )
            }

            SettingsMenuItem.THEME -> {
                val nextTheme = SettingsMenuModel.nextTheme(state.selectedTheme, direction)
                applyAppearance(
                    fontId = state.selectedFontId,
                    newPixelShape = state.selectedPixelShape,
                    newDotSizePx = state.selectedDotSizePx,
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

    private fun shouldShowDrawerScrollableList(): Boolean {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return false
        }
        return TextListSupport.hasScrollableContent(
            rowCount = currentDrawerApps().size,
            viewport = AppListLayout.metrics(screenProfile).textList.viewport,
        )
    }

    private fun isPointInDrawerScrollableListArea(x: Int, y: Int): Boolean {
        if (x !in 0 until screenProfile.logicalWidth) {
            return false
        }
        val metrics = AppListLayout.metrics(screenProfile)
        if (!state.isDrawerSearchFocused && x >= metrics.hiddenRailLeft) {
            return false
        }
        if (y < metrics.listStartY) {
            return false
        }
        return y < metrics.listStartY + metrics.railHeight
    }


    private fun drawerListScrollThresholds(): DrawerVerticalScrollThresholds {
        val rowHeight = AppListLayout.metrics(screenProfile).rowHeight.toFloat()
        return DrawerVerticalScrollThresholds(
            upwardStepPx = rowHeight,
            downwardStepPx = rowHeight,
        )
    }

    private fun settingsListScrollThresholds(): DrawerVerticalScrollThresholds {
        val rowHeight = SettingsMenuLayout.metrics(screenProfile).rowHeight.toFloat()
        return DrawerVerticalScrollThresholds(
            upwardStepPx = rowHeight,
            downwardStepPx = rowHeight,
        )
    }

    private fun settingsMaxStartIndex(): Int {
        return (SettingsMenuModel.rows(state).size - settingsVisibleRows()).coerceAtLeast(0)
    }

    private fun canScrollSettingsInDirection(direction: Int): Boolean {
        return when {
            direction > 0 -> state.settingsListStartIndex < settingsMaxStartIndex()
            direction < 0 -> state.settingsListStartIndex > 0
            else -> false
        }
    }

    private fun shouldShowSettingsScrollableList(): Boolean {
        if (state.mode != LauncherMode.SETTINGS) {
            return false
        }
        return TextListSupport.hasScrollableContent(
            rowCount = SettingsMenuModel.rows(state).size,
            viewport = SettingsMenuLayout.metrics(screenProfile).textList.viewport,
        )
    }

    private fun isPointInSettingsScrollableListArea(x: Int, y: Int): Boolean {
        if (x !in 0 until screenProfile.logicalWidth) {
            return false
        }
        val metrics = SettingsMenuLayout.metrics(screenProfile)
        return y in metrics.firstRowY until metrics.panelBottom
    }

    private fun isSettingsAtListStart(): Boolean {
        return state.settingsListStartIndex <= 0
    }

    private fun isSettingsAtListEnd(): Boolean {
        return state.settingsListStartIndex >= settingsMaxStartIndex()
    }

    private fun isOutwardSettingsBoundaryDelta(deltaY: Float): Boolean {
        if (deltaY == 0f || !shouldShowSettingsScrollableList()) {
            return false
        }
        return (deltaY > 0f && isSettingsAtListStart()) ||
            (deltaY < 0f && isSettingsAtListEnd())
    }

    private fun clampSettingsMotionAtListBounds() {
        if (!shouldShowSettingsScrollableList()) {
            stopSettingsVerticalListAnimation(resetOffset = true)
            return
        }
        val atStart = isSettingsAtListStart()
        val atEnd = isSettingsAtListEnd()
        val outwardResidual =
            (settingsListScrollResidualOffsetPx > drawerBoundaryEpsilonPx && atStart) ||
                (settingsListScrollResidualOffsetPx < -drawerBoundaryEpsilonPx && atEnd)
        val outwardVelocity =
            (settingsListScrollVelocityPxPerSecond > drawerBoundaryVelocityEpsilonPx && atStart) ||
                (settingsListScrollVelocityPxPerSecond < -drawerBoundaryVelocityEpsilonPx && atEnd)
        val invalidSettleTarget = settingsSettleTarget?.let { target -> !canScrollSettingsInDirection(target.direction) } == true
        if (outwardResidual || outwardVelocity || invalidSettleTarget) {
            stopSettingsVerticalListAnimation(resetOffset = true)
            return
        }
        if (!settingsListScrollAnimating && kotlin.math.abs(settingsListScrollResidualOffsetPx) <= drawerBoundaryEpsilonPx) {
            settingsListScrollResidualOffsetPx = 0f
        }
    }

    private fun applySettingsVerticalStepDelta(stepDelta: Int, renderAfter: Boolean = true) {
        if (stepDelta == 0) {
            return
        }
        var remaining = stepDelta
        while (remaining != 0) {
            val direction = if (remaining > 0) 1 else -1
            val previousListStartIndex = state.settingsListStartIndex
            state = LauncherStateTransitions.scrollSettingsWindow(
                state = state,
                delta = direction,
                visibleRows = settingsVisibleRows(),
            )
            if (state.settingsListStartIndex == previousListStartIndex) {
                settingsListScrollResidualOffsetPx = 0f
                settingsListScrollVelocityPxPerSecond = 0f
                settingsListScrollAnimating = false
                break
            }
            remaining -= direction
        }
        if (renderAfter) {
            renderCurrentFrame()
            startAnimationTickerIfNeeded()
        }
    }

    private fun stepSettingsVerticalListAnimation(deltaMs: Long) {
        clampSettingsMotionAtListBounds()
        if (!settingsListScrollAnimating) {
            return
        }
        val animationStep = DrawerVerticalScrollController.stepAnimation(
            residualOffsetPx = settingsListScrollResidualOffsetPx,
            velocityPxPerSecond = settingsListScrollVelocityPxPerSecond,
            thresholds = settingsListScrollThresholds(),
            deltaMs = deltaMs,
            settleTarget = settingsSettleTarget,
        )
        settingsListScrollResidualOffsetPx = animationStep.residualOffsetPx
        settingsListScrollVelocityPxPerSecond = animationStep.nextVelocityPxPerSecond
        settingsSettleTarget = animationStep.settleTarget
        settingsListScrollAnimating = animationStep.isAnimating
        if (animationStep.stepDelta != 0) {
            applySettingsVerticalStepDelta(animationStep.stepDelta, renderAfter = false)
        }
        clampSettingsMotionAtListBounds()
    }

    private fun stopSettingsVerticalListAnimation(resetOffset: Boolean) {
        settingsListScrollAnimating = false
        settingsListScrollVelocityPxPerSecond = 0f
        settingsSettleTarget = null
        if (resetOffset) {
            settingsListScrollResidualOffsetPx = 0f
        }
    }

    private fun resetSettingsVerticalDragTracking() {
        settingsListDragTracking = false
        settingsListDragConsumed = false
        settingsListDragVelocityPxPerSecond = 0f
        settingsListDragLastUptimeMs = 0L
    }

    private fun settleSettingsMotionBeforeExplicitAction() {
        val settledState = TextListSupport.settleBeforeExplicitAction(
            TextListRuntimeState(
                selectedIndex = state.settingsSelectedIndex,
                listStartIndex = state.settingsListStartIndex,
                residualOffsetPx = settingsListScrollResidualOffsetPx,
                velocityPxPerSecond = settingsListScrollVelocityPxPerSecond,
                settleTarget = settingsSettleTarget,
                isDragging = settingsListDragTracking || settingsListDragConsumed,
                isAnimating = settingsListScrollAnimating,
            ),
        )
        resetSettingsVerticalDragTracking()
        settingsListScrollAnimating = settledState.isAnimating
        settingsListScrollResidualOffsetPx = settledState.residualOffsetPx
        settingsListScrollVelocityPxPerSecond = settledState.velocityPxPerSecond
        settingsSettleTarget = null
    }

    private fun drawerListLastIndex(): Int {
        return currentDrawerApps().lastIndex
    }

    private fun canMoveInDirection(direction: Int): Boolean {
        return DrawerDirectionalSettlePolicy.canAdvance(
            currentIndex = state.selectedIndex,
            lastIndex = drawerListLastIndex(),
            direction = direction,
        )
    }

    private fun isDrawerAtListStart(): Boolean {
        val apps = currentDrawerApps()
        if (apps.isEmpty()) {
            return true
        }
        return state.selectedIndex <= 0
    }

    private fun isDrawerAtListEnd(): Boolean {
        val apps = currentDrawerApps()
        if (apps.isEmpty()) {
            return true
        }
        return state.selectedIndex >= apps.lastIndex
    }

    private fun isOutwardBoundaryDelta(deltaY: Float): Boolean {
        if (deltaY == 0f || !shouldShowDrawerScrollableList()) {
            return false
        }
        return (deltaY > 0f && isDrawerAtListStart()) ||
            (deltaY < 0f && isDrawerAtListEnd())
    }

    private fun clampDrawerMotionAtListBounds() {
        if (!shouldShowDrawerScrollableList()) {
            stopDrawerVerticalListAnimation(resetOffset = true)
            return
        }
        val atStart = isDrawerAtListStart()
        val atEnd = isDrawerAtListEnd()
        val outwardResidual =
            (drawerListScrollResidualOffsetPx > drawerBoundaryEpsilonPx && atStart) ||
                (drawerListScrollResidualOffsetPx < -drawerBoundaryEpsilonPx && atEnd)
        val outwardVelocity =
            (drawerListScrollVelocityPxPerSecond > drawerBoundaryVelocityEpsilonPx && atStart) ||
                (drawerListScrollVelocityPxPerSecond < -drawerBoundaryVelocityEpsilonPx && atEnd)
        val invalidSettleTarget = drawerSettleTarget?.let { !canMoveInDirection(it.direction) } == true
        if (outwardResidual || outwardVelocity || invalidSettleTarget) {
            stopDrawerVerticalListAnimation(resetOffset = true)
            return
        }
        if (!drawerListScrollAnimating && kotlin.math.abs(drawerListScrollResidualOffsetPx) <= drawerBoundaryEpsilonPx) {
            drawerListScrollResidualOffsetPx = 0f
        }
    }

    private fun applyDrawerVerticalStepDelta(stepDelta: Int, renderAfter: Boolean = true) {
        if (stepDelta == 0) {
            return
        }
        var remaining = stepDelta
        while (remaining != 0) {
            val direction = if (remaining > 0) 1 else -1
            val previousSelectedIndex = state.selectedIndex
            state = LauncherStateTransitions.moveSelection(
                state = state,
                delta = direction,
                visibleRows = visibleRows(),
            )
            if (state.selectedIndex == previousSelectedIndex) {
                drawerListScrollResidualOffsetPx = 0f
                drawerListScrollVelocityPxPerSecond = 0f
                drawerListScrollAnimating = false
                break
            }
            remaining -= direction
        }
        if (renderAfter) {
            renderCurrentFrame()
            startAnimationTickerIfNeeded()
        }
    }

    private fun stepDrawerVerticalListAnimation(deltaMs: Long) {
        clampDrawerMotionAtListBounds()
        if (!drawerListScrollAnimating) {
            return
        }
        val animationStep = DrawerVerticalScrollController.stepAnimation(
            residualOffsetPx = drawerListScrollResidualOffsetPx,
            velocityPxPerSecond = drawerListScrollVelocityPxPerSecond,
            thresholds = drawerListScrollThresholds(),
            deltaMs = deltaMs,
            settleTarget = drawerSettleTarget,
        )
        drawerListScrollResidualOffsetPx = animationStep.residualOffsetPx
        drawerListScrollVelocityPxPerSecond = animationStep.nextVelocityPxPerSecond
        drawerSettleTarget = animationStep.settleTarget
        drawerListScrollAnimating = animationStep.isAnimating
        if (animationStep.stepDelta != 0) {
            applyDrawerVerticalStepDelta(animationStep.stepDelta, renderAfter = false)
        }
        clampDrawerMotionAtListBounds()
    }

    private fun stopDrawerVerticalListAnimation(resetOffset: Boolean) {
        drawerListScrollAnimating = false
        drawerListScrollVelocityPxPerSecond = 0f
        drawerSettleTarget = null
        if (resetOffset) {
            drawerListScrollResidualOffsetPx = 0f
        }
    }

    private fun resetDrawerVerticalDragTracking() {
        drawerListDragTracking = false
        drawerListDragConsumed = false
        drawerListDragVelocityPxPerSecond = 0f
        drawerListDragLastUptimeMs = 0L
    }

    private fun resetDrawerVerticalGesture() {
        resetDrawerVerticalDragTracking()
        stopDrawerVerticalListAnimation(resetOffset = true)
    }

    private fun settleDrawerMotionBeforeExplicitAction() {
        val settledState = TextListSupport.settleBeforeExplicitAction(
            TextListRuntimeState(
                selectedIndex = state.selectedIndex,
                listStartIndex = state.listStartIndex,
                residualOffsetPx = drawerListScrollResidualOffsetPx,
                velocityPxPerSecond = drawerListScrollVelocityPxPerSecond,
                settleTarget = drawerSettleTarget,
                isDragging = drawerListDragTracking || drawerListDragConsumed,
                isAnimating = drawerListScrollAnimating,
            ),
        )
        resetDrawerVerticalDragTracking()
        drawerListScrollAnimating = settledState.isAnimating
        drawerListScrollResidualOffsetPx = settledState.residualOffsetPx
        drawerListScrollVelocityPxPerSecond = settledState.velocityPxPerSecond
        drawerSettleTarget = null
    }

    private fun startClockTicker() {
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.post(clockTicker)
    }

    private fun startDecorationTicker() {
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.postDelayed(animationTicker, LauncherAnimationState.frameDelayMs)
    }

    private fun startInteractionTicker() {
        if (interactionTickerLastUptimeMs <= 0L) {
            interactionTickerLastUptimeMs = SystemClock.uptimeMillis()
        }
        mainHandler.removeCallbacks(interactionTicker)
        mainHandler.postDelayed(interactionTicker, interactionFrameDelayMs)
    }

    private fun startAnimationTickerIfNeeded() {
        if (shouldRunDecorationTicker()) {
            startDecorationTicker()
        } else {
            mainHandler.removeCallbacks(animationTicker)
        }
        if (shouldRunInteractionTicker()) {
            startInteractionTicker()
        } else {
            mainHandler.removeCallbacks(interactionTicker)
            interactionTickerLastUptimeMs = 0L
        }
    }

    private fun shouldRunDecorationTicker(): Boolean {
        return animationState.hasActiveAnimations ||
            shouldAnimateHeaderCharge() ||
            shouldAnimateHomeMarquee() ||
            shouldAnimateDrawerCursor()
    }

    private fun shouldRunInteractionTicker(): Boolean {
        return shouldAnimateDrawerListScroll() ||
            shouldAnimateSettingsListScroll() ||
            horizontalPageState.isSettling
    }

    private fun shouldAnimateDrawerCursor(): Boolean {
        return state.mode == LauncherMode.APP_DRAWER && state.isDrawerSearchFocused
    }

    private fun shouldAnimateDrawerListScroll(): Boolean {
        return state.mode == LauncherMode.APP_DRAWER && drawerListScrollAnimating
    }

    private fun shouldAnimateSettingsListScroll(): Boolean {
        return state.mode == LauncherMode.SETTINGS && settingsListScrollAnimating
    }

    private fun shouldAnimateHomeMarquee(): Boolean {
        if (state.mode != LauncherMode.HOME || state.homeContextCard != HomeContextCard.QUOTE) {
            return false
        }
        val quote = state.quoteText.ifBlank { return false }
        val homeLayout = HomeLayout.metrics(screenProfile)
        val quoteWidth = pixelFontEngine.measureText(quote, GlyphStyle.UI_SMALL_10)
        return quoteWidth > homeLayout.innerWidth
    }

    private fun shouldAnimateHeaderCharge(): Boolean {
        if (!state.isCharging || state.batteryLevel >= 100) {
            return false
        }
        return when (state.mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS -> true

            LauncherMode.IDLE -> false
        }
    }

    private fun applyAppearance(
        fontId: PixelFontId,
        newPixelShape: PixelShape,
        newDotSizePx: Int,
        newTheme: PixelTheme,
    ) {
        if (::pixelFontEngine.isInitialized) {
            pixelFontEngine.clearCache()
        }

        val resolvedPixelFont = pixelFontResolver.createFont(fontId)
        pixelFontEngine = resolvedPixelFont.engine
        pixelRenderer = PixelRenderer(pixelFontEngine)
        pixelShape = newPixelShape
        dotSizePx = newDotSizePx
        selectedTheme = newTheme
        fontSettingsRepository.setAppearanceSettings(fontId, newPixelShape, newDotSizePx, newTheme)
        state = LauncherStateTransitions.updateAppearance(
            state = state,
            selectedFontId = fontId,
            selectedPixelShape = newPixelShape,
            selectedDotSizePx = newDotSizePx,
            selectedTheme = newTheme,
        )

        val displayView = pixelFrameView.asView()
        val widthPx = displayView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val heightPx = displayView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
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
        idleChargeIdleEffectSnapshot = chargeIdleEffect
        if (!isIdlePageEnabled && state.mode == LauncherMode.IDLE) {
            wakeFromIdle()
            return
        }
        refreshDerivedUiState(render = true)
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun onDeviceStatusChanged(deviceStatus: DeviceStatus) {
        val wasCharging = state.isCharging
        state = LauncherStateTransitions.updateDeviceStatus(state, deviceStatus)
        syncIdleFluidWithBattery()
        if (state.mode == LauncherMode.IDLE && wasCharging != state.isCharging) {
            requestIdleChargeEffectRefresh()
        }
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
                    rainForecastRepository.fetchRainHint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    )
                }.onSuccess { rainHint ->
                    mainHandler.post {
                        if (isDestroyed || isFinishing) {
                            rainRefreshInFlight = false
                            return@post
                        }
                        lastRainRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
                        lastRainLocation = location
                        lastSuccessfulRainHintText = rainHint.orEmpty()
                        rainRefreshInFlight = false
                        applyRainHintText(rainHint.orEmpty(), render = render)
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
        updatePalette()
        if (render) {
            renderCurrentFrame()
        }
    }

    private fun updatePalette() {
        palette = PixelPalette.fromTheme(
            theme = selectedTheme,
            isLowBattery = state.batteryLevel <= LOW_BATTERY_THRESHOLD,
        )
        if (::pixelFrameView.isInitialized) {
            pixelFrameView.setPalette(palette)
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
        stopIdlePhysics()
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

    private fun enterIdleChargePreview() {
        if (!state.isIdlePageEnabled) {
            return
        }
        if (state.mode != LauncherMode.IDLE) {
            state = state.copy(
                mode = LauncherMode.IDLE,
                returnMode = state.mode,
            )
        }
        syncIdleFluidWithBattery()
        startIdlePhysics()
        renderCurrentFrame()
        updateDrawerInputFocus()
    }

    private fun scheduleIdleCheck() {
        mainHandler.removeCallbacks(idleRunnable)
        if (canEnterIdle()) {
            val idleForMs = SystemClock.uptimeMillis() - state.lastInteractionUptimeMs
            val delay = (IDLE_TIMEOUT_MS - idleForMs).coerceAtLeast(0L)
            mainHandler.postDelayed(idleRunnable, delay)
        }
    }

    private fun startIdlePhysics() {
        if (state.mode != LauncherMode.IDLE || !::idlePhysicsHandler.isInitialized) {
            return
        }
        val simulationProfile = currentIdleSimulationProfile()
        idleBatteryLevelSnapshot = state.batteryLevel
        idleLogicalWidthSnapshot = screenProfile.logicalWidth
        idleLogicalHeightSnapshot = screenProfile.logicalHeight
        idleSimulationWidthSnapshot = simulationProfile.width
        idleSimulationHeightSnapshot = simulationProfile.height
        idleChargeIdleEffectSnapshot = state.chargeIdleEffect
        idleGravityX = motionSnapshot.gravityX
        idleGravityY = motionSnapshot.gravityY
        idlePhysicsRunning = true
        idlePhysicsSyncedBatteryLevel = Int.MIN_VALUE
        idlePhysicsSyncedSimulationWidth = -1
        idlePhysicsSyncedSimulationHeight = -1
        idlePhysicsState = state.idleFluidState
        idleChargeMaskFrame = null
        idlePhysicsAccumulatorMs = idleFixedStepMs
        idlePhysicsLastTickUptimeMs = SystemClock.uptimeMillis()
        idlePhysicsHandler.removeCallbacksAndMessages(null)
        if (::pixelFrameView.isInitialized) {
            pixelFrameView.setIdleContinuousRendering(usesGlIdleComposite())
            RenderPerfLogger.mark(
                "idle.renderMode",
                "continuous=${usesGlIdleComposite()} backend=${pixelFrameView.javaClass.simpleName}",
            )
        }
        idlePhysicsHandler.post(idlePhysicsTicker)
        deviceMotionRepository.start(::onMotionSnapshot)
    }

    private fun stopIdlePhysics() {
        idlePhysicsRunning = false
        deviceMotionRepository.stop()
        if (::idlePhysicsHandler.isInitialized) {
            idlePhysicsHandler.removeCallbacksAndMessages(null)
        }
        idlePhysicsAccumulatorMs = 0L
        idlePhysicsLastTickUptimeMs = 0L
        idlePhysicsSyncedBatteryLevel = Int.MIN_VALUE
        idlePhysicsSyncedSimulationWidth = -1
        idlePhysicsSyncedSimulationHeight = -1
        idleChargeMaskFrame = null
        if (::pixelFrameView.isInitialized) {
            pixelFrameView.submitIdleMask(null)
            pixelFrameView.setIdleContinuousRendering(false)
            RenderPerfLogger.mark(
                "idle.renderMode",
                "continuous=false backend=${pixelFrameView.javaClass.simpleName}",
            )
        }
    }

    private fun onMotionSnapshot(snapshot: DeviceMotionSnapshot) {
        val (screenGravityX, screenGravityY) = ScreenGravityMapper.mapToScreen(
            rawGravityX = snapshot.gravityX,
            rawGravityY = snapshot.gravityY,
            rawGravityZ = snapshot.gravityZ,
            rotation = display?.rotation ?: 0,
        )
        val (screenLinearAccelX, screenLinearAccelY) = ScreenGravityMapper.mapToScreen(
            rawGravityX = snapshot.linearAccelX,
            rawGravityY = snapshot.linearAccelY,
            rawGravityZ = snapshot.linearAccelZ,
            rotation = display?.rotation ?: 0,
        )
        val now = SystemClock.uptimeMillis()
        motionSnapshot = snapshot.copy(
            gravityX = screenGravityX,
            gravityY = screenGravityY,
            linearAccelX = screenLinearAccelX,
            linearAccelY = screenLinearAccelY,
        )
        idleGravityX = screenGravityX
        idleGravityY = screenGravityY
        if (idlePhysicsRunning && ::idlePhysicsHandler.isInitialized) {
            idlePhysicsHandler.post {
                if (!idlePhysicsRunning) {
                    return@post
                }
                idlePhysicsState = idleFluidEngine.applyDisturbance(
                    state = idlePhysicsState,
                    accelX = screenLinearAccelX,
                    accelY = screenLinearAccelY,
                    nowUptimeMs = now,
                )
            }
        }
    }

    private fun stepIdleFluidOnPhysics(nowUptimeMs: Long) {
        if (!idlePhysicsRunning) {
            return
        }
        idleChargeMaskFrame = null
        val batteryLevel = idleBatteryLevelSnapshot
        val simulationWidth = idleSimulationWidthSnapshot.coerceAtLeast(1)
        val simulationHeight = idleSimulationHeightSnapshot.coerceAtLeast(1)
        if (idlePhysicsSyncedBatteryLevel != batteryLevel ||
            idlePhysicsSyncedSimulationWidth != simulationWidth ||
            idlePhysicsSyncedSimulationHeight != simulationHeight
        ) {
            idlePhysicsState = RenderPerfLogger.measure("idle.physics.syncToBattery") {
                idleFluidEngine.syncToBattery(
                    state = idlePhysicsState,
                    batteryLevel = batteryLevel,
                    simulationWidth = simulationWidth,
                    simulationHeight = simulationHeight,
                    gravityX = idleGravityX,
                    gravityY = idleGravityY,
                    nowUptimeMs = nowUptimeMs,
                )
            }
            idlePhysicsSyncedBatteryLevel = batteryLevel
            idlePhysicsSyncedSimulationWidth = simulationWidth
            idlePhysicsSyncedSimulationHeight = simulationHeight
        }
        idlePhysicsState = RenderPerfLogger.measure("idle.physics.step") {
            idleFluidEngine.step(
                state = idlePhysicsState,
                simulationWidth = simulationWidth,
                simulationHeight = simulationHeight,
                gravityX = idleGravityX,
                gravityY = idleGravityY,
                deltaSeconds = idleFixedStepSeconds,
                nowUptimeMs = nowUptimeMs,
            )
        }
    }

    private fun stepChargeIdleEffectOnPhysics(nowUptimeMs: Long) {
        idleChargeMaskFrame = buildChargeIdleMaskFrame(nowUptimeMs)
    }

    private fun buildIdleMaskFrame(physicsState: IdleFluidState): IdleMaskFrame {
        val frame = RenderPerfLogger.measure("idle.mask.encode") {
            idleMaskSequence += 1L
            val sourceMask = physicsState.litMask
            val encodedMask = ByteArray(sourceMask.size)
            sourceMask.forEachIndexed { index, isLit ->
                encodedMask[index] = if (isLit) 0x7F else 0x00
            }
            IdleMaskFrame(
                sequence = idleMaskSequence,
                width = physicsState.width.coerceAtLeast(1),
                height = physicsState.height.coerceAtLeast(1),
                mask = encodedMask,
            )
        }
        return pixelRenderer.carveIdleTimeCutout(
            frame = frame,
            currentTimeText = state.currentTimeText,
            screenProfile = screenProfile,
        )
    }

    private fun dispatchIdleStateToMain(physicsState: IdleFluidState) {
        val renderState = physicsState.copy(
            coverageField = FloatArray(0),
            litMask = physicsState.litMask.copyOf(),
        )
        mainHandler.post {
            if (!idlePhysicsRunning || state.mode != LauncherMode.IDLE) {
                return@post
            }
            state = state.copy(idleFluidState = renderState)
            renderCurrentFrame()
        }
    }

    private fun buildChargeIdleMaskFrame(nowUptimeMs: Long): IdleMaskFrame? {
        idleMaskSequence += 1L
        val frame = ChargeIdleEffectRegistry.rendererFor(idleChargeIdleEffectSnapshot).render(
            width = idleLogicalWidthSnapshot.coerceAtLeast(1),
            height = idleLogicalHeightSnapshot.coerceAtLeast(1),
            batteryLevel = idleBatteryLevelSnapshot,
            isCharging = true,
            gravityX = idleGravityX,
            gravityY = idleGravityY,
            nowUptimeMs = nowUptimeMs,
            sequence = idleMaskSequence,
        ) ?: return null
        return pixelRenderer.carveIdleTimeCutout(
            frame = frame,
            currentTimeText = state.currentTimeText,
            screenProfile = screenProfile,
        )
    }

    private fun currentIdleDynamicMaskFrame(): IdleMaskFrame? {
        return if (shouldUseChargeIdleEffectOnPhysics()) {
            idleChargeMaskFrame
        } else {
            buildIdleMaskFrame(idlePhysicsState)
        }
    }

    private fun currentIdleRenderState(): IdleFluidState {
        if (!shouldUseChargeIdleEffectOnPhysics()) {
            return idlePhysicsState
        }
        val frame = idleChargeMaskFrame ?: return IdleFluidState()
        return IdleFluidState(
            width = frame.width,
            height = frame.height,
            litMask = BooleanArray(frame.mask.size) { index -> frame.mask[index].toInt() != 0 },
        )
    }

    private fun syncIdleFluidWithBattery() {
        val simulationProfile = currentIdleSimulationProfile()
        idleBatteryLevelSnapshot = state.batteryLevel
        idleLogicalWidthSnapshot = screenProfile.logicalWidth
        idleLogicalHeightSnapshot = screenProfile.logicalHeight
        idleSimulationWidthSnapshot = simulationProfile.width
        idleSimulationHeightSnapshot = simulationProfile.height
        idleChargeIdleEffectSnapshot = state.chargeIdleEffect
        if (idlePhysicsRunning && ::idlePhysicsHandler.isInitialized) {
            idlePhysicsHandler.post {
                idlePhysicsSyncedBatteryLevel = Int.MIN_VALUE
                idlePhysicsSyncedSimulationWidth = -1
                idlePhysicsSyncedSimulationHeight = -1
            }
            return
        }
        state = state.copy(
            idleFluidState = idleFluidEngine.syncToBattery(
                state = state.idleFluidState,
                batteryLevel = state.batteryLevel,
                simulationWidth = simulationProfile.width,
                simulationHeight = simulationProfile.height,
                gravityX = motionSnapshot.gravityX,
                gravityY = motionSnapshot.gravityY,
                nowUptimeMs = SystemClock.uptimeMillis(),
            ),
        )
        idlePhysicsState = state.idleFluidState
    }

    private fun shouldUseChargeIdleEffectOnPhysics(): Boolean {
        return false
    }

    private fun requestIdleChargeEffectRefresh() {
        if (!idlePhysicsRunning || !::idlePhysicsHandler.isInitialized || state.mode != LauncherMode.IDLE) {
            return
        }
        idleBatteryLevelSnapshot = state.batteryLevel
        idleLogicalWidthSnapshot = screenProfile.logicalWidth
        idleLogicalHeightSnapshot = screenProfile.logicalHeight
        idleChargeIdleEffectSnapshot = state.chargeIdleEffect
        idlePhysicsHandler.post {
            if (!idlePhysicsRunning || state.mode != LauncherMode.IDLE) {
                return@post
            }
            if (usesGlIdleComposite()) {
                pixelFrameView.submitIdleMask(buildIdleMaskFrame(idlePhysicsState))
            } else {
                dispatchIdleStateToMain(idlePhysicsState)
            }
        }
    }

    private fun currentIdleSimulationProfile(): IdleSimulationProfile {
        return IdleSimulationProfile.fromLogicalSize(
            logicalWidth = screenProfile.logicalWidth,
            logicalHeight = screenProfile.logicalHeight,
        )
    }

    @Suppress("DEPRECATION")
    private fun suppressActivityAnimations() {
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val REQUIRED_GLES_VERSION = 0x20000
        const val IDLE_TIMEOUT_MS = 25_000L
        const val LOW_BATTERY_THRESHOLD = 15
        const val pagerSettingsIndex = 0
        const val pagerHomeIndex = 1
        const val pagerAppsIndex = 2
        const val pagerPageCount = 3
        const val pagerDragStartThresholdPx = 2
        const val pagerDragAxisBias = 1.1f
        const val drawerListDragStartThresholdPx = 2
        const val drawerListDragAxisBias = 1.1f
        const val drawerListFlingStartVelocityPxPerSecond = 24f
        const val drawerBoundaryEpsilonPx = 0.25f
        const val drawerBoundaryVelocityEpsilonPx = 0.01f
        const val interactionFrameDelayMs: Long = 16L
        const val interactionMaxFrameDeltaMs: Long = 64L
        const val idleFixedStepMs: Long = 16L
        const val idleFixedStepSeconds: Float = idleFixedStepMs / 1000f
        const val idleTickerDelayMs: Long = 16L
        const val idleMaxCatchUpSteps: Int = 2
        const val idleMaxAccumulationMs: Long = idleFixedStepMs * idleMaxCatchUpSteps
        const val homeDataPermissionRequestCode = 1001
        const val rainRefreshIntervalMs: Long = 30 * 60 * 1000L
        const val rainRefreshDistanceThresholdMeters = 1_000f
        const val rainLocationPromptText = "LOC"
    }
}
