package com.purride.pixellauncherv2.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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
import com.purride.pixellauncherv2.launcher.AppDrawerIndexModel
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import com.purride.pixellauncherv2.launcher.SettingsMenuItem
import com.purride.pixellauncherv2.launcher.SettingsMenuLayout
import com.purride.pixellauncherv2.launcher.SettingsMenuModel
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

    private val clockTicker = object : Runnable {
        override fun run() {
            state = LauncherStateTransitions.updateTime(
                state = state,
                currentTimeText = timeTextProvider.currentTimeText(),
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
            animationState = animationState.nextFrame()
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
        state = LauncherStateTransitions.updateTime(state, timeTextProvider.currentTimeText())
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

        setContentView(pixelDisplayView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (state.mode) {
                    LauncherMode.SETTINGS -> closeSettingsMenu()
                    LauncherMode.DIAGNOSTICS -> closeDiagnostics()
                    LauncherMode.APP_DRAWER -> {
                        state = LauncherStateTransitions.showHome(state)
                        renderCurrentFrame()
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
        animationState = animationState.startBootSequence()
        refreshDerivedUiState(render = false)
        renderCurrentFrame()
        startAnimationTickerIfNeeded()
        scheduleIdleCheck()
        loadApps()
        suppressActivityAnimations()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowModeController.hideSystemBars()
        }
    }

    override fun onPause() {
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

            else -> super.onKeyDown(keyCode, event)
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
                val drawerIndexModel = AppDrawerIndexModel.create(
                    apps = state.apps,
                    visibleRows = visibleRows(),
                    selectedIndex = state.selectedIndex,
                )
                val tappedPageIndex = AppListLayout.hitTestIndexRailPage(
                    screenProfile = screenProfile,
                    logicalX = x,
                    logicalY = y,
                    pageCount = drawerIndexModel.pageCount,
                )
                if (tappedPageIndex != null) {
                    state = LauncherStateTransitions.selectDrawerPage(
                        state = state,
                        pageIndex = tappedPageIndex,
                        visibleRows = visibleRows(),
                    )
                    renderCurrentFrame()
                    return
                }

                val tappedIndex = AppListLayout.hitTestAppIndex(
                    screenProfile = screenProfile,
                    state = state,
                    logicalX = x,
                    logicalY = y,
                ) ?: return

                if (tappedIndex == state.selectedIndex) {
                    launchSelectedApp()
                    return
                }

                state = LauncherStateTransitions.selectIndex(
                    state = state,
                    index = tappedIndex,
                    visibleRows = visibleRows(),
                )
                renderCurrentFrame()
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

    override fun onSwipeUp() {
        if (launchPending || animationState.bootSequence != null) {
            return
        }
        if (wakeIfIdle()) {
            return
        }
        recordInteraction()
        when (state.mode) {
            LauncherMode.APP_DRAWER -> pageDrawer(1)
            LauncherMode.HOME -> showAppDrawer()
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
            if (state.drawerPageIndex == 0) {
                state = LauncherStateTransitions.showHome(state)
                renderCurrentFrame()
            } else {
                pageDrawer(-1)
            }
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
        when (state.mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER -> openSettingsMenu()
            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS,
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
        when (state.mode) {
            LauncherMode.SETTINGS -> closeSettingsMenu()
            LauncherMode.DIAGNOSTICS -> closeDiagnostics()
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
        val selectedApp = state.apps.getOrNull(state.selectedIndex) ?: return
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
                suppressActivityAnimations()
            } else {
                renderCurrentFrame()
            }
        }
        launchRunnable = pendingRunnable
        mainHandler.postDelayed(pendingRunnable, LauncherAnimationState.launchShutterDurationMs)
    }

    private fun renderCurrentFrame() {
        val pixelBuffer = pixelRenderer.render(
            state = state,
            screenProfile = screenProfile,
            animationState = animationState,
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

    private fun visibleRows(): Int = AppListLayout.metrics(screenProfile).visibleRows

    private fun showAppDrawer() {
        val previousMode = state.mode
        state = LauncherStateTransitions.showAppDrawer(
            state = state,
            visibleRows = visibleRows(),
        )
        stopIdlePhysics()
        if (previousMode != LauncherMode.APP_DRAWER) {
            animationState = animationState.startDrawerReveal()
            startAnimationTickerIfNeeded()
        }
        renderCurrentFrame()
        scheduleIdleCheck()
    }

    private fun openSettingsMenu() {
        state = LauncherStateTransitions.showSettings(state)
        stopIdlePhysics()
        renderCurrentFrame()
        scheduleIdleCheck()
    }

    private fun closeSettingsMenu() {
        state = LauncherStateTransitions.hideSettings(state)
        renderCurrentFrame()
        scheduleIdleCheck()
    }

    private fun openDiagnostics() {
        state = LauncherStateTransitions.showDiagnostics(state)
        renderCurrentFrame()
    }

    private fun closeDiagnostics() {
        state = LauncherStateTransitions.hideDiagnostics(state)
        renderCurrentFrame()
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
            SettingsMenuItem.INFO -> openDiagnostics()
            SettingsMenuItem.CLOSE -> closeSettingsMenu()
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

            SettingsMenuItem.INFO -> {
                if (direction >= 0) {
                    openDiagnostics()
                }
            }

            SettingsMenuItem.CLOSE -> {
                if (direction >= 0) {
                    closeSettingsMenu()
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
        const val idleFixedStepMs: Long = 16L
        const val idleFixedStepSeconds: Float = idleFixedStepMs / 1000f
        const val idleTickerDelayMs: Long = 16L
        const val idleMaxCatchUpSteps: Int = 4
        const val idleMaxAccumulationMs: Long = idleFixedStepMs * idleMaxCatchUpSteps
    }
}
