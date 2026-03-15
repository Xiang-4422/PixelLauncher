package com.purride.pixellauncherv2.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
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
import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.DeviceMotionRepository
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.data.LauncherStatsRepository
import com.purride.pixellauncherv2.data.PackageManagerAppRepository
import com.purride.pixellauncherv2.launcher.AppListLayout
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DrawerContentTapAction
import com.purride.pixellauncherv2.launcher.DrawerContentTapResolver
import com.purride.pixellauncherv2.launcher.DrawerRailDragMapper
import com.purride.pixellauncherv2.launcher.HomeContextCard
import com.purride.pixellauncherv2.launcher.HomeLayout
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.HorizontalPageController
import com.purride.pixellauncherv2.render.HorizontalPageSnapshot
import com.purride.pixellauncherv2.render.HorizontalPageState
import com.purride.pixellauncherv2.render.LauncherAnimationState
import com.purride.pixellauncherv2.render.PixelDisplayView
import com.purride.pixellauncherv2.render.PixelFontEngine
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelFontResolver
import com.purride.pixellauncherv2.render.PixelPalette
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

class MainActivity : AppCompatActivity(), PixelDisplayView.InteractionListener {

    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeTextProvider = TimeTextProvider()
    private val throttleClickHelper = ThrottleClickHelper()
    private val terminalStatusProvider = TerminalStatusProvider()

    private lateinit var appRepository: AppRepository
    private lateinit var fontSettingsRepository: FontSettingsRepository
    private lateinit var launcherStatsRepository: LauncherStatsRepository
    private lateinit var deviceStatusRepository: DeviceStatusRepository
    private lateinit var deviceMotionRepository: DeviceMotionRepository
    private lateinit var pixelFontResolver: PixelFontResolver
    private lateinit var appLauncher: AndroidAppLauncher
    private lateinit var windowModeController: WindowModeController
    private lateinit var pixelFontEngine: PixelFontEngine
    private lateinit var pixelRenderer: PixelRenderer
    private lateinit var pixelDisplayView: PixelDisplayView
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
    private var syncingDrawerInputProxyText = false
    private var drawerRailDragLastY: Int = 0
    private var drawerRailDragAccumulatorPx: Float = 0f
    private var drawerRailPixelsPerApp: Float = 1f
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

    private val clockTicker = object : Runnable {
        override fun run() {
            state = LauncherStateTransitions.updateTime(
                state = state,
                currentTimeText = timeTextProvider.currentTimeText(),
                currentDateText = timeTextProvider.currentDateText(),
                currentWeekdayText = timeTextProvider.currentWeekdayText(),
            )
            refreshDerivedUiState(render = true)
            mainHandler.postDelayed(this, timeTextProvider.millisUntilNextMinute())
        }
    }

    private val animationTicker = object : Runnable {
        override fun run() {
            if (!shouldRunAnimationTicker()) {
                return
            }
            val wasPagerSettling = horizontalPageState.isSettling
            horizontalPageState = horizontalPageController.step(
                state = horizontalPageState,
                deltaMs = LauncherAnimationState.frameDelayMs,
            )
            animationState = animationState.nextFrame()
            if (wasPagerSettling && !horizontalPageState.isSettling) {
                applyPagerSettledMode(horizontalPageState.currentIndex)
            }
            renderCurrentFrame()
            if (shouldRunAnimationTicker()) {
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
            syncIdleFluidWithBattery()
            startIdlePhysics()
            renderCurrentFrame()
        } else {
            scheduleIdleCheck()
        }
    }

    private val idlePhysicsTicker = object : Runnable {
        override fun run() {
            if (state.mode != LauncherMode.IDLE) {
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
            while (idlePhysicsAccumulatorMs >= idleFixedStepMs && steps < idleMaxCatchUpSteps) {
                val stepNow = now - idlePhysicsAccumulatorMs + idleFixedStepMs
                stepIdleFluid(stepNow)
                idlePhysicsAccumulatorMs -= idleFixedStepMs
                steps += 1
            }
            if (steps > 0) {
                renderCurrentFrame()
            }
            mainHandler.postDelayed(this, idleTickerDelayMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.setWindowAnimations(0)

        appRepository = PackageManagerAppRepository(applicationContext)
        fontSettingsRepository = FontSettingsRepository(applicationContext)
        launcherStatsRepository = LauncherStatsRepository(applicationContext)
        deviceStatusRepository = DeviceStatusRepository(applicationContext)
        deviceMotionRepository = DeviceMotionRepository(applicationContext)
        pixelFontResolver = PixelFontResolver(applicationContext)
        val appearanceSettings = fontSettingsRepository.getAppearanceSettings()
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
        updatePalette()
        val metrics = resources.displayMetrics
        screenProfile = ScreenProfileFactory.create(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            dotSizePx = dotSizePx,
            pixelShape = pixelShape,
        )
        syncIdleFluidWithBattery()
        pixelDisplayView = PixelDisplayView(this).apply {
            interactionListener = this@MainActivity
            setPalette(palette)
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
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (syncingDrawerInputProxyText || state.mode != LauncherMode.APP_DRAWER) {
                        return
                    }
                    val query = s?.toString().orEmpty()
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
                    state = LauncherStateTransitions.updateDrawerQuery(
                        state = focusedState,
                        query = query,
                        visibleRows = visibleRows(),
                    )
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
                pixelDisplayView,
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
        pixelDisplayView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
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

    override fun onResume() {
        super.onResume()
        windowModeController.hideSystemBars()
        startClockTicker()
        deviceStatusRepository.start(::onDeviceStatusChanged)
        state = LauncherStateTransitions.showHome(state)
        state = LauncherStateTransitions.recordInteraction(state, SystemClock.uptimeMillis())
        refreshDerivedUiState(render = false)
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

    override fun onPause() {
        hideDrawerKeyboard()
        resetPagerDragTracking()
        if (::drawerInputProxy.isInitialized) {
            drawerInputProxy.clearFocus()
        }
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.removeCallbacks(idlePhysicsTicker)
        mainHandler.removeCallbacks(idleRunnable)
        launchRunnable?.let(mainHandler::removeCallbacks)
        launchRunnable = null
        launchPending = false
        deviceStatusRepository.stop()
        stopIdlePhysics()
        suppressActivityAnimations()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        backgroundExecutor.shutdownNow()
        pixelFontEngine.clearCache()
        super.onDestroy()
    }

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
                    LauncherMode.APP_DRAWER -> moveSelection(-1)
                    LauncherMode.SETTINGS -> moveSettingsSelection(-1)
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.HOME,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (state.mode) {
                    LauncherMode.APP_DRAWER -> moveSelection(1)
                    LauncherMode.SETTINGS -> moveSettingsSelection(1)
                    LauncherMode.HOME -> showAppDrawer()
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> changeSettingValue(-1)
                    LauncherMode.HOME -> Unit
                    LauncherMode.APP_DRAWER -> pageDrawer(-1)
                    LauncherMode.DIAGNOSTICS,
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (state.mode) {
                    LauncherMode.SETTINGS -> changeSettingValue(1)
                    LauncherMode.HOME -> Unit
                    LauncherMode.APP_DRAWER -> pageDrawer(1)
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
                    LauncherMode.SETTINGS -> activateSelectedSetting()
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
                    LauncherMode.HOME -> showAppDrawer()
                    LauncherMode.APP_DRAWER -> launchSelectedApp()
                    LauncherMode.IDLE -> Unit
                }
                true
            }

            else -> if (handleDrawerTextInput(keyCode, event)) true else super.onKeyDown(keyCode, event)
        }
    }

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
                val tappedSearchBox = AppListLayout.hitTestSearchBox(
                    screenProfile = screenProfile,
                    logicalX = x,
                    logicalY = y,
                )
                if (tappedSearchBox) {
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
                val tappedLetterIndex = AppListLayout.hitTestIndexRailLetter(
                    screenProfile = screenProfile,
                    logicalX = x,
                    logicalY = y,
                )
                if (tappedLetterIndex != null) {
                    if (state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()) {
                        state = LauncherStateTransitions.exitDrawerSearch(
                            state = state,
                            visibleRows = visibleRows(),
                        )
                    }
                    selectByRailLetter(tappedLetterIndex)
                    return
                }
                val canTapResultList = !state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()
                val tappedIndex = if (canTapResultList) {
                    AppListLayout.hitTestAppIndex(
                        screenProfile = screenProfile,
                        state = state,
                        logicalX = x,
                        logicalY = y,
                    )
                } else {
                    null
                }
                val decision = DrawerContentTapResolver.resolve(state, tappedIndex)
                when (decision.action) {
                    DrawerContentTapAction.LAUNCH_SELECTED -> launchSelectedApp()

                    DrawerContentTapAction.SELECT_INDEX -> {
                        val targetIndex = decision.targetIndex ?: return
                        state = LauncherStateTransitions.selectIndex(
                            state = state,
                            index = targetIndex,
                            visibleRows = visibleRows(),
                        )
                        renderCurrentFrame()
                    }

                    DrawerContentTapAction.EXIT_SEARCH -> {
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
                ) ?: return

                if (tappedRow == state.settingsSelectedIndex) {
                    activateSelectedSetting()
                } else {
                    state = LauncherStateTransitions.selectSettingsIndex(state, tappedRow)
                    renderCurrentFrame()
                }
            }

            LauncherMode.HOME -> {
                Unit
            }

            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE -> Unit
        }
    }

    override fun onLogicalDragStart(x: Int, y: Int): Boolean {
        if (launchPending || animationState.bootSequence != null) {
            return false
        }
        if (wakeIfIdle()) {
            return true
        }
        resetPagerDragTracking()

        if (state.mode == LauncherMode.APP_DRAWER) {
            val railHit = AppListLayout.hitTestIndexRailLetter(
                screenProfile = screenProfile,
                logicalX = x,
                logicalY = y,
            ) != null
            if (railHit) {
                recordInteraction()
                if (state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()) {
                    state = LauncherStateTransitions.exitDrawerSearch(
                        state = state,
                        visibleRows = visibleRows(),
                    )
                }
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

        if (!canHandlePagerNavigation()) {
            return false
        }
        pagerDragTracking = true
        pagerDragConsumed = false
        pagerDragStartX = x
        pagerDragStartY = y
        pagerDragLastX = x
        pagerDragLastY = y
        pagerDragLastUptimeMs = SystemClock.uptimeMillis()
        pagerDragVelocityPxPerSecond = 0f
        return false
    }

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
        when (state.mode) {
            LauncherMode.APP_DRAWER -> {
                if (!state.isDrawerSearchFocused) {
                    pageDrawer(1)
                }
            }
            LauncherMode.HOME -> Unit
            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE -> Unit
        }
    }

    override fun onSwipeDown() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
        if (state.mode == LauncherMode.APP_DRAWER) {
            if (!state.isDrawerSearchFocused) {
                pageDrawer(-1)
            }
            return
        }
    }

    override fun onSwipeLeft() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
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
        if (!throttleClickHelper.canClick() || launchPending) {
            return
        }
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

    private fun renderCurrentFrame() {
        syncPagerIndexToMode()
        val pagerSnapshot = activePagerSnapshot()
        val pixelBuffer = pixelRenderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = animationState,
            pagerSnapshot = pagerSnapshot,
        )
        pixelDisplayView.submitFrame(
            pixelBuffer = pixelBuffer,
            screenProfile = screenProfile,
            palette = palette,
        )
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
        syncIdleFluidWithBattery()
        renderCurrentFrame()
        return true
    }

    private fun visibleRows(): Int {
        val metrics = AppListLayout.metrics(screenProfile)
        return if (state.mode == LauncherMode.APP_DRAWER && !state.isDrawerSearchFocused) {
            AppListLayout.centeredVisibleRows(screenProfile)
        } else {
            metrics.visibleRows
        }
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
        if (state.mode == LauncherMode.APP_DRAWER && state.isDrawerSearchFocused) {
            return false
        }
        if (state.mode == LauncherMode.APP_DRAWER && state.isDrawerRailSliding) {
            return false
        }
        return true
    }

    private fun applyPagerSettledMode(pageIndex: Int) {
        val targetMode = pagerModeForIndex(pageIndex) ?: return
        if (state.mode == targetMode) {
            return
        }
        val drawerRows = AppListLayout.centeredVisibleRows(screenProfile)
        state = when (targetMode) {
            LauncherMode.HOME -> LauncherStateTransitions.showHome(state)
            LauncherMode.SETTINGS -> LauncherStateTransitions.showSettings(state)
            LauncherMode.APP_DRAWER -> LauncherStateTransitions.showAppDrawer(
                state = LauncherStateTransitions.clearDrawerQuery(
                    state = state,
                    visibleRows = drawerRows,
                ),
                visibleRows = drawerRows,
            ).copy(
                isDrawerSearchFocused = false,
                isDrawerRailSliding = false,
            )

            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE -> state
        }
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun animatePagerBy(deltaPages: Int): Boolean {
        if (!canHandlePagerNavigation()) {
            return false
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

    private fun showAppDrawer() {
        val previousMode = state.mode
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
            isDrawerSearchFocused = false,
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
        if (!::drawerInputProxy.isInitialized || !::pixelDisplayView.isInitialized) {
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
            pixelDisplayView.requestFocus()
        }
    }

    private fun handleDrawerTextInput(keyCode: Int, event: KeyEvent?): Boolean {
        if (state.mode != LauncherMode.APP_DRAWER) {
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL -> {
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
        if (!state.isDrawerSearchFocused) {
            state = state.copy(
                isDrawerSearchFocused = true,
                isDrawerRailSliding = false,
            )
            updateDrawerInputFocus()
        }

        state = LauncherStateTransitions.appendDrawerQuery(
            state = state,
            text = inputChar.toString(),
            visibleRows = visibleRows(),
        )
        syncDrawerInputProxyText()
        renderCurrentFrame()
        return true
    }

    private fun openSettingsMenu() {
        state = LauncherStateTransitions.showSettings(state)
        stopIdlePhysics()
        renderCurrentFrame()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun closeSettingsMenu() {
        state = LauncherStateTransitions.hideSettings(state)
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        updateDrawerInputFocus()
        scheduleIdleCheck()
    }

    private fun openDiagnostics() {
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
        )
        renderCurrentFrame()
    }

    private fun activateSelectedSetting() {
        when (SettingsMenuModel.selectedItem(state)) {
            SettingsMenuItem.FONT -> changeSettingValue(1)
            SettingsMenuItem.RESOLUTION -> changeSettingValue(1)
            SettingsMenuItem.STYLE -> changeSettingValue(1)
            SettingsMenuItem.THEME -> changeSettingValue(1)
            SettingsMenuItem.ADVANCED -> openDiagnostics()
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
                val nextDotSizePx = SettingsMenuModel.nextResolution(state.selectedDotSizePx, direction)
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

            SettingsMenuItem.ADVANCED -> {
                if (direction >= 0) {
                    openDiagnostics()
                }
            }
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

    private fun startClockTicker() {
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.post(clockTicker)
    }

    private fun startAnimationTicker() {
        mainHandler.removeCallbacks(animationTicker)
        mainHandler.postDelayed(animationTicker, LauncherAnimationState.frameDelayMs)
    }

    private fun startAnimationTickerIfNeeded() {
        if (shouldRunAnimationTicker()) {
            startAnimationTicker()
        } else {
            mainHandler.removeCallbacks(animationTicker)
        }
    }

    private fun shouldRunAnimationTicker(): Boolean {
        return animationState.hasActiveAnimations ||
            shouldAnimateHeaderCharge() ||
            shouldAnimateHomeMarquee() ||
            shouldAnimateDrawerCursor() ||
            horizontalPageState.isSettling
    }

    private fun shouldAnimateDrawerCursor(): Boolean {
        return state.mode == LauncherMode.APP_DRAWER && state.isDrawerSearchFocused
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

        val widthPx = pixelDisplayView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val heightPx = pixelDisplayView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val screenProfileChanged = updateScreenProfile(widthPx, heightPx)
        refreshDerivedUiState(render = !screenProfileChanged)
    }

    private fun onDeviceStatusChanged(deviceStatus: DeviceStatus) {
        state = LauncherStateTransitions.updateDeviceStatus(state, deviceStatus)
        syncIdleFluidWithBattery()
        refreshDerivedUiState(render = true)
        startAnimationTickerIfNeeded()
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
        if (::pixelDisplayView.isInitialized) {
            pixelDisplayView.setPalette(palette)
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
        return !launchPending && (state.mode == LauncherMode.HOME || state.mode == LauncherMode.APP_DRAWER)
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
        if (state.mode != LauncherMode.IDLE) {
            return
        }
        syncIdleFluidWithBattery()
        idlePhysicsAccumulatorMs = idleFixedStepMs
        idlePhysicsLastTickUptimeMs = SystemClock.uptimeMillis()
        deviceMotionRepository.start(::onMotionSnapshot)
        mainHandler.removeCallbacks(idlePhysicsTicker)
        mainHandler.post(idlePhysicsTicker)
    }

    private fun stopIdlePhysics() {
        deviceMotionRepository.stop()
        mainHandler.removeCallbacks(idlePhysicsTicker)
        idlePhysicsAccumulatorMs = 0L
        idlePhysicsLastTickUptimeMs = 0L
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

        if (state.mode == LauncherMode.IDLE) {
            state = state.copy(
                idleFluidState = idleFluidEngine.applyDisturbance(
                    state = state.idleFluidState,
                    accelX = screenLinearAccelX,
                    accelY = screenLinearAccelY,
                    nowUptimeMs = now,
                ),
            )
        }
    }

    private fun stepIdleFluid(nowUptimeMs: Long) {
        if (state.mode != LauncherMode.IDLE) {
            return
        }
        state = state.copy(
            idleFluidState = idleFluidEngine.step(
                state = state.idleFluidState,
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
                gravityX = motionSnapshot.gravityX,
                gravityY = motionSnapshot.gravityY,
                deltaSeconds = idleFixedStepSeconds,
                nowUptimeMs = nowUptimeMs,
            ),
        )
    }

    private fun syncIdleFluidWithBattery() {
        state = state.copy(
            idleFluidState = idleFluidEngine.syncToBattery(
                state = state.idleFluidState,
                batteryLevel = state.batteryLevel,
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
                gravityX = motionSnapshot.gravityX,
                gravityY = motionSnapshot.gravityY,
                nowUptimeMs = SystemClock.uptimeMillis(),
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun suppressActivityAnimations() {
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 25_000L
        const val LOW_BATTERY_THRESHOLD = 15
        const val pagerSettingsIndex = 0
        const val pagerHomeIndex = 1
        const val pagerAppsIndex = 2
        const val pagerPageCount = 3
        const val pagerDragStartThresholdPx = 2
        const val pagerDragAxisBias = 1.1f
        const val idleFixedStepMs: Long = 16L
        const val idleFixedStepSeconds: Float = idleFixedStepMs / 1000f
        const val idleTickerDelayMs: Long = 16L
        const val idleMaxCatchUpSteps: Int = 4
        const val idleMaxAccumulationMs: Long = idleFixedStepMs * idleMaxCatchUpSteps
    }
}
