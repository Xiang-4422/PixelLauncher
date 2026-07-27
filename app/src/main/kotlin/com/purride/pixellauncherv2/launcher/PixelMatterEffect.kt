package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixellauncherv2.model.DeviceMotionSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class PixelMatterEffectMode {
    SAND,
    WATER,
    SMOKE,
}

internal enum class PixelMatterEffectPhase {
    IDLE,
    ACTIVE,
    RESTORING,
}

internal class PixelMatterShakeDetector(
    private val windowNanos: Long = 900_000_000L,
    private val mediumThreshold: Float = 8.5f,
    private val hardThreshold: Float = 13.5f,
    private val requiredMediumSamples: Int = 3,
) {
    private val samples = ArrayDeque<ShakeSample>()
    private var fallbackTimestampNanos = 0L

    fun reset() {
        samples.clear()
        fallbackTimestampNanos = 0L
    }

    fun record(snapshot: DeviceMotionSnapshot): Boolean {
        val timestamp = normalizedTimestamp(snapshot.timestampNanos)
        val magnitude = snapshot.linearAccelerationMagnitude()
        samples.addLast(ShakeSample(timestampNanos = timestamp, magnitude = magnitude))
        trim(timestamp)

        val mediumCount = samples.count { it.magnitude >= mediumThreshold }
        val hasHardSample = samples.any { it.magnitude >= hardThreshold }
        if (mediumCount >= requiredMediumSamples && hasHardSample) {
            samples.clear()
            return true
        }
        return false
    }

    private fun normalizedTimestamp(timestampNanos: Long): Long {
        if (timestampNanos > 0L) return timestampNanos
        fallbackTimestampNanos += fallbackSampleStepNanos
        return fallbackTimestampNanos
    }

    private fun trim(nowNanos: Long) {
        val minTimestamp = nowNanos - windowNanos
        while (samples.isNotEmpty() && samples.first().timestampNanos < minTimestamp) {
            samples.removeFirst()
        }
    }

    private data class ShakeSample(
        val timestampNanos: Long,
        val magnitude: Float,
    )

    private companion object {
        const val fallbackSampleStepNanos: Long = 16_666_667L
    }
}

internal class PixelMatterController(
    vsync: PixelTickerProvider,
    private val onFrame: () -> Unit,
    private val onEffectStart: () -> Unit = {},
    private val onEffectClear: () -> Unit = {},
    random: Random = Random.Default,
) {
    private val randomSource = random
    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        if (shouldDispatchTick(elapsedNanos)) {
            onTick(elapsedNanos)
        }
    }

    private var lastMotion = DeviceMotionSnapshot()
    private var lastHandInput: PixelMatterHandSnapshot? = null
    private var lastDispatchedElapsedNanos = -1L
    private var lastElapsedNanos = -1L
    private var restoringElapsedMs = 0L

    var phase: PixelMatterEffectPhase = PixelMatterEffectPhase.IDLE
        private set

    var simulation: PixelMatterSimulation? = null
        private set

    fun isVisible(): Boolean = phase != PixelMatterEffectPhase.IDLE && simulation != null

    fun isActive(): Boolean = phase == PixelMatterEffectPhase.ACTIVE

    fun isRestoring(): Boolean = phase == PixelMatterEffectPhase.RESTORING

    fun updateMotion(snapshot: DeviceMotionSnapshot) {
        lastMotion = snapshot
    }

    fun updateHandInput(snapshot: PixelMatterHandSnapshot?) {
        lastHandInput = snapshot
        if (phase == PixelMatterEffectPhase.ACTIVE && snapshot != null) {
            ticker.start()
            onFrame()
        }
    }

    fun applyShakeImpulse(snapshot: DeviceMotionSnapshot): Boolean {
        val target = simulation ?: return false
        if (phase != PixelMatterEffectPhase.ACTIVE) return false
        lastMotion = snapshot
        target.applyImpulse(snapshot, randomSource)
        ticker.start()
        onFrame()
        return true
    }

    fun start(
        mode: PixelMatterEffectMode,
        buffer: PixelBuffer,
        snapshot: DeviceMotionSnapshot,
        backgroundColor: Int = PixelColor.Black.argb,
        ignoredBackgroundColors: IntArray = intArrayOf(backgroundColor),
    ): Boolean {
        val seed = PixelMatterCapture.capture(
            buffer = buffer,
            backgroundColor = backgroundColor,
            ignoredBackgroundColors = ignoredBackgroundColors,
        ) ?: return false
        val nextSimulation = PixelMatterSimulationFactory.create(
            mode = mode,
            seed = seed,
            snapshot = snapshot,
            random = randomSource,
        )
        lastMotion = snapshot
        simulation = nextSimulation
        phase = PixelMatterEffectPhase.ACTIVE
        lastHandInput = null
        lastDispatchedElapsedNanos = -1L
        lastElapsedNanos = -1L
        restoringElapsedMs = 0L
        ticker.start()
        onEffectStart()
        onFrame()
        return true
    }

    fun requestRestore(): Boolean {
        val target = simulation ?: return false
        if (phase == PixelMatterEffectPhase.IDLE) return false
        if (phase == PixelMatterEffectPhase.RESTORING) return true
        target.beginRestore()
        phase = PixelMatterEffectPhase.RESTORING
        restoringElapsedMs = 0L
        lastElapsedNanos = -1L
        ticker.start()
        onFrame()
        return true
    }

    fun clear() {
        val wasVisible = isVisible()
        ticker.stop()
        phase = PixelMatterEffectPhase.IDLE
        simulation = null
        lastHandInput = null
        lastDispatchedElapsedNanos = -1L
        lastElapsedNanos = -1L
        restoringElapsedMs = 0L
        if (wasVisible) {
            onEffectClear()
        }
        onFrame()
    }

    fun dispose() {
        ticker.dispose()
        simulation = null
        lastHandInput = null
        phase = PixelMatterEffectPhase.IDLE
    }

    private fun onTick(elapsedNanos: Long) {
        val target = simulation ?: run {
            clear()
            return
        }
        val deltaMs = when {
            lastElapsedNanos < 0L -> frameDelayMs
            elapsedNanos <= lastElapsedNanos -> frameDelayMs
            else -> ((elapsedNanos - lastElapsedNanos) / 1_000_000L).coerceIn(1L, 80L)
        }
        lastElapsedNanos = elapsedNanos

        when (phase) {
            PixelMatterEffectPhase.IDLE -> clear()
            PixelMatterEffectPhase.ACTIVE -> target.step(deltaMs / 1_000f, lastMotion, lastHandInput)
            PixelMatterEffectPhase.RESTORING -> {
                restoringElapsedMs += deltaMs
                val progress = (restoringElapsedMs.toFloat() / restoreDurationMs).coerceIn(0f, 1f)
                target.applyRestore(progress)
                if (progress >= 1f) {
                    clear()
                    return
                }
            }
        }
        onFrame()
    }

    private fun shouldDispatchTick(elapsedNanos: Long): Boolean {
        val lastDispatch = lastDispatchedElapsedNanos
        if (lastDispatch < 0L || elapsedNanos - lastDispatch >= frameIntervalNanos) {
            lastDispatchedElapsedNanos = elapsedNanos
            return true
        }
        return false
    }

    private companion object {
        const val frameIntervalNanos: Long = 33_333_333L
        const val frameDelayMs: Long = 33L
        const val restoreDurationMs: Float = 1_600f
    }
}

internal interface PixelMatterSimulation {
    val width: Int
    val height: Int
    val particleCount: Int

    fun step(
        deltaSeconds: Float,
        snapshot: DeviceMotionSnapshot,
        handInput: PixelMatterHandSnapshot? = null,
    )
    fun applyImpulse(snapshot: DeviceMotionSnapshot, random: Random)
    fun beginRestore()
    fun applyRestore(progress: Float)
    fun drawTo(buffer: PixelBuffer, offsetX: Int = 0, offsetY: Int = 0)
    fun particlePosition(index: Int): Pair<Float, Float>
    fun originPosition(index: Int): Pair<Int, Int>
    fun forceRestoreToOrigin()
}

internal data class PixelMatterSeed(
    val width: Int,
    val height: Int,
    val originX: IntArray,
    val originY: IntArray,
    val colors: IntArray,
) {
    val particleCount: Int
        get() = colors.size
}

internal object PixelMatterCapture {
    fun capture(
        buffer: PixelBuffer,
        backgroundColor: Int = PixelColor.Black.argb,
        ignoredBackgroundColors: IntArray = intArrayOf(backgroundColor),
    ): PixelMatterSeed? {
        val count = buffer.pixels.count { color ->
            visibleMatterColor(color, backgroundColor, ignoredBackgroundColors) != null
        }
        if (count == 0) return null

        val originX = IntArray(count)
        val originY = IntArray(count)
        val colors = IntArray(count)
        var index = 0
        for (y in 0 until buffer.height) {
            val row = y * buffer.width
            for (x in 0 until buffer.width) {
                val color = visibleMatterColor(
                    color = buffer.pixels[row + x],
                    backgroundColor = backgroundColor,
                    ignoredBackgroundColors = ignoredBackgroundColors,
                ) ?: continue
                originX[index] = x
                originY[index] = y
                colors[index] = color
                index += 1
            }
        }
        return PixelMatterSeed(
            width = buffer.width,
            height = buffer.height,
            originX = originX,
            originY = originY,
            colors = colors,
        )
    }

    private fun visibleMatterColor(
        color: Int,
        backgroundColor: Int,
        ignoredBackgroundColors: IntArray,
    ): Int? {
        if (((color ushr 24) and 0xFF) == 0) return null
        val opaqueBackground = opaqueRgb(backgroundColor)
        val flattened = opaqueRgb(PixelBuffer.blendSrcOver(src = color, dst = opaqueBackground))
        return if (isIgnoredMatterColor(flattened, opaqueBackground, ignoredBackgroundColors)) {
            null
        } else {
            flattened
        }
    }

    private fun isIgnoredMatterColor(
        flattened: Int,
        opaqueBackground: Int,
        ignoredBackgroundColors: IntArray,
    ): Boolean {
        if (sameRgb(flattened, opaqueBackground)) return true
        for (ignoredColor in ignoredBackgroundColors) {
            if (sameRgb(flattened, opaqueRgb(ignoredColor))) return true
        }
        return false
    }
}

internal object PixelMatterSimulationFactory {
    fun create(
        mode: PixelMatterEffectMode,
        seed: PixelMatterSeed,
        snapshot: DeviceMotionSnapshot,
        random: Random = Random.Default,
    ): PixelMatterSimulation {
        return when (mode) {
            PixelMatterEffectMode.SAND -> SandGridSimulation(seed, snapshot, random)
            PixelMatterEffectMode.WATER -> WaterGridSimulation(seed, snapshot, random)
            PixelMatterEffectMode.SMOKE -> SmokeParticleSimulation(seed, snapshot, random)
        }
    }
}

private abstract class BasePixelMatterSimulation(
    seed: PixelMatterSeed,
) : PixelMatterSimulation {
    override val width: Int = seed.width
    override val height: Int = seed.height
    protected val originX: IntArray = seed.originX.copyOf()
    protected val originY: IntArray = seed.originY.copyOf()
    protected val x: FloatArray = FloatArray(seed.particleCount) { index -> originX[index].toFloat() }
    protected val y: FloatArray = FloatArray(seed.particleCount) { index -> originY[index].toFloat() }
    protected val colors: IntArray = seed.colors.copyOf()
    private var restoreStartX: FloatArray = FloatArray(0)
    private var restoreStartY: FloatArray = FloatArray(0)

    override val particleCount: Int
        get() = colors.size

    override fun beginRestore() {
        restoreStartX = x.copyOf()
        restoreStartY = y.copyOf()
    }

    override fun applyRestore(progress: Float) {
        val startX = restoreStartX.takeIf { it.size == particleCount } ?: x
        val startY = restoreStartY.takeIf { it.size == particleCount } ?: y
        val t = easeInOut(progress.coerceIn(0f, 1f))
        for (index in colors.indices) {
            x[index] = startX[index] + (originX[index] - startX[index]) * t
            y[index] = startY[index] + (originY[index] - startY[index]) * t
            clearParticleMotion(index)
        }
    }

    override fun drawTo(buffer: PixelBuffer, offsetX: Int, offsetY: Int) {
        drawColorsTo(buffer, offsetX, offsetY, colors)
    }

    protected fun drawColorsTo(
        buffer: PixelBuffer,
        offsetX: Int,
        offsetY: Int,
        drawColors: IntArray,
    ) {
        val targetWidth = buffer.width
        val targetHeight = buffer.height
        for (index in colors.indices) {
            val targetX = offsetX + x[index].roundToInt()
            val targetY = offsetY + y[index].roundToInt()
            if (targetX !in 0 until targetWidth || targetY !in 0 until targetHeight) continue
            val targetIndex = targetY * targetWidth + targetX
            buffer.pixels[targetIndex] = PixelBuffer.blendSrcOver(
                src = drawColors[index],
                dst = buffer.pixels[targetIndex],
            )
        }
    }

    override fun particlePosition(index: Int): Pair<Float, Float> {
        require(index in colors.indices) { "index $index out of bounds for $particleCount particles" }
        return x[index] to y[index]
    }

    override fun originPosition(index: Int): Pair<Int, Int> {
        require(index in colors.indices) { "index $index out of bounds for $particleCount particles" }
        return originX[index] to originY[index]
    }

    override fun forceRestoreToOrigin() {
        for (index in colors.indices) {
            x[index] = originX[index].toFloat()
            y[index] = originY[index].toFloat()
            clearParticleMotion(index)
        }
    }

    protected open fun clearParticleMotion(index: Int) = Unit
}

private abstract class GridMatterSimulation(
    seed: PixelMatterSeed,
) : BasePixelMatterSimulation(seed) {
    protected val cellX: IntArray = originX.copyOf()
    protected val cellY: IntArray = originY.copyOf()
    protected val occupiedCells = IntArray(width * height) { emptyCell }

    override fun forceRestoreToOrigin() {
        for (index in colors.indices) {
            cellX[index] = originX[index]
            cellY[index] = originY[index]
        }
        super.forceRestoreToOrigin()
        buildOccupiedCells()
    }

    protected fun buildOccupiedCells() {
        occupiedCells.fill(emptyCell)
        for (index in colors.indices) {
            cellX[index] = cellX[index].coerceIn(0, width - 1)
            cellY[index] = cellY[index].coerceIn(0, height - 1)
            if (!claimCell(cellX[index], cellY[index], index)) {
                claimNearestEmptyCell(cellX[index], cellY[index])?.let { cell ->
                    cellX[index] = cell.x
                    cellY[index] = cell.y
                    claimCell(cell.x, cell.y, index)
                }
            }
        }
    }

    protected fun syncFloatPositions() {
        for (index in colors.indices) {
            x[index] = cellX[index].toFloat()
            y[index] = cellY[index].toFloat()
        }
    }

    protected fun moveParticle(index: Int, dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0) return false
        val oldX = cellX[index]
        val oldY = cellY[index]
        val nextX = oldX + dx
        val nextY = oldY + dy
        if (nextX !in 0 until width || nextY !in 0 until height) return false
        val nextCell = nextY * width + nextX
        if (occupiedCells[nextCell] != emptyCell) return false
        occupiedCells[oldY * width + oldX] = emptyCell
        cellX[index] = nextX
        cellY[index] = nextY
        occupiedCells[nextCell] = index
        return true
    }

    protected fun swapParticles(first: Int, second: Int): Boolean {
        if (first == second) return false
        val firstX = cellX[first]
        val firstY = cellY[first]
        val secondX = cellX[second]
        val secondY = cellY[second]
        cellX[first] = secondX
        cellY[first] = secondY
        cellX[second] = firstX
        cellY[second] = firstY
        occupiedCells[firstY * width + firstX] = second
        occupiedCells[secondY * width + secondX] = first
        return true
    }

    protected fun indexAt(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return emptyCell
        return occupiedCells[y * width + x]
    }

    protected inline fun scanCells(driveX: Float, driveY: Float, block: (Int) -> Unit) {
        val yStart = if (driveY >= 0f) height - 1 else 0
        val yEnd = if (driveY >= 0f) -1 else height
        val yStep = if (driveY >= 0f) -1 else 1
        val xStart = if (driveX >= 0f) width - 1 else 0
        val xEnd = if (driveX >= 0f) -1 else width
        val xStep = if (driveX >= 0f) -1 else 1
        var yCursor = yStart
        while (yCursor != yEnd) {
            var xCursor = xStart
            while (xCursor != xEnd) {
                val index = occupiedCells[yCursor * width + xCursor]
                if (index != emptyCell && cellX[index] == xCursor && cellY[index] == yCursor) {
                    block(index)
                }
                xCursor += xStep
            }
            yCursor += yStep
        }
    }

    private fun claimNearestEmptyCell(startX: Int, startY: Int): MatterCell? {
        val maxRadius = max(width, height)
        for (radius in 1..maxRadius) {
            for (dy in -radius..radius) {
                val y = startY + dy
                if (y !in 0 until height) continue
                val remaining = radius - abs(dy)
                for (offsetIndex in 0..remaining * 2) {
                    val x = startX + alternatingOffset(offsetIndex)
                    if (x !in 0 until width) continue
                    if (occupiedCells[y * width + x] == emptyCell) return MatterCell(x, y)
                }
            }
        }
        return null
    }

    private fun claimCell(cellX: Int, cellY: Int, index: Int): Boolean {
        val cell = cellY * width + cellX
        if (occupiedCells[cell] != emptyCell) return false
        occupiedCells[cell] = index
        return true
    }
}

private class SandGridSimulation(
    seed: PixelMatterSeed,
    snapshot: DeviceMotionSnapshot,
    random: Random,
) : GridMatterSimulation(seed) {
    private val vx = FloatArray(particleCount)
    private val vy = FloatArray(particleCount)
    private val moveBudgetX = FloatArray(particleCount)
    private val moveBudgetY = FloatArray(particleCount)
    private val frictionSeed = FloatArray(particleCount)
    private var simulationStep = 0
    private var impulseX = 0f
    private var impulseY = 0f
    private var impulseRemainingSeconds = 0f
    private var fluidizedRemainingSeconds = sandInitialFluidizedSeconds

    init {
        val centerX = (width - 1).coerceAtLeast(0) / 2f
        val centerY = (height - 1).coerceAtLeast(0) / 2f
        val initialImpulse = PixelMatterMotionMapper.toScreenLinearImpulse(snapshot)
        for (index in 0 until particleCount) {
            val dx = originX[index] - centerX
            val dy = originY[index] - centerY
            val distance = max(1f, sqrt(dx * dx + dy * dy))
            val burst = sandInitialBurstBase + random.nextFloat() * sandInitialBurstRange
            vx[index] = ((dx / distance) * burst +
                random.nextFloatIn(-sandInitialJitter, sandInitialJitter) +
                initialImpulse.x * sandInitialImpulseVelocityScale)
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            vy[index] = ((dy / distance) * burst +
                random.nextFloatIn(-sandInitialJitter, sandInitialJitter) +
                initialImpulse.y * sandInitialImpulseVelocityScale)
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            frictionSeed[index] = random.nextFloatIn(sandMinFrictionSeed, sandMaxFrictionSeed)
        }
    }

    override fun step(
        deltaSeconds: Float,
        snapshot: DeviceMotionSnapshot,
        handInput: PixelMatterHandSnapshot?,
    ) {
        if (particleCount == 0) return
        val safeDelta = deltaSeconds.coerceIn(0.001f, 0.080f)
        val gravity = PixelMatterMotionMapper.toScreenAcceleration(snapshot)
        buildOccupiedCells()
        val steps = physicsStepCount(safeDelta)
        val stepSeconds = safeDelta / steps.toFloat()
        repeat(steps) {
            simulationStep += 1
            val impulse = consumeImpulseAcceleration(stepSeconds)
            val fluid = consumeFluidizedStrength(stepSeconds)
            integrateSand(stepSeconds, gravity, impulse, fluid, handInput)
            relaxMargolusBlocks(gravity, impulse, fluid)
            scanSand(gravity, impulse, fluid, handInput)
        }
        syncFloatPositions()
    }

    override fun applyImpulse(snapshot: DeviceMotionSnapshot, random: Random) {
        val impulse = PixelMatterMotionMapper.toScreenLinearImpulse(snapshot)
        val safeX = impulse.x.coerceIn(-sandMaxImpulseAcceleration, sandMaxImpulseAcceleration)
        val safeY = impulse.y.coerceIn(-sandMaxImpulseAcceleration, sandMaxImpulseAcceleration)
        impulseX = (impulseX + safeX).coerceIn(-sandMaxImpulseAcceleration, sandMaxImpulseAcceleration)
        impulseY = (impulseY + safeY).coerceIn(-sandMaxImpulseAcceleration, sandMaxImpulseAcceleration)
        impulseRemainingSeconds = sandImpulseSeconds
        fluidizedRemainingSeconds = max(fluidizedRemainingSeconds, sandRuntimeFluidizedSeconds)
        for (index in 0 until particleCount) {
            vx[index] = (vx[index] + safeX * sandDirectImpulseVelocityScale +
                random.nextFloatIn(-sandRuntimeImpulseJitter, sandRuntimeImpulseJitter))
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            vy[index] = (vy[index] + safeY * sandDirectImpulseVelocityScale +
                random.nextFloatIn(-sandRuntimeImpulseJitter, sandRuntimeImpulseJitter))
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
        }
    }

    override fun forceRestoreToOrigin() {
        super.forceRestoreToOrigin()
        fluidizedRemainingSeconds = 0f
        impulseRemainingSeconds = 0f
        impulseX = 0f
        impulseY = 0f
    }

    override fun clearParticleMotion(index: Int) {
        vx[index] = 0f
        vy[index] = 0f
        moveBudgetX[index] = 0f
        moveBudgetY[index] = 0f
    }

    private fun integrateSand(
        deltaSeconds: Float,
        gravity: MatterVector,
        impulse: MatterVector,
        fluidizedStrength: Float,
        handInput: PixelMatterHandSnapshot?,
    ) {
        val damping = lerp(sandVelocityDampingPerSecond, sandFluidVelocityDampingPerSecond, fluidizedStrength)
            .pow(deltaSeconds)
        val handRadius = matterHandRadius(width, height, sandHandRadiusRatio)
        for (index in 0 until particleCount) {
            val handForce = PixelMatterHandForces.forceAt(
                x = cellX[index].toFloat(),
                y = cellY[index].toFloat(),
                input = handInput,
                radius = handRadius,
                repelStrength = sandHandRepelAcceleration,
                attractStrength = sandHandAttractAcceleration,
                windStrength = sandHandWindAcceleration,
            )
            val localFluidizedStrength = max(fluidizedStrength, handForce.strength * sandHandFluidizedScale)
            val stir = sandFluidStirAcceleration * localFluidizedStrength
            val accelX = gravity.x * sandGravityAccelerationScale + impulse.x +
                handForce.x +
                signedNoise(index, simulationStep * 31 + 7) * stir
            val accelY = gravity.y * sandGravityAccelerationScale + impulse.y +
                handForce.y +
                signedNoise(index, simulationStep * 31 + 19) * stir
            vx[index] = ((vx[index] + accelX * deltaSeconds * frictionSeed[index]) * damping)
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            vy[index] = ((vy[index] + accelY * deltaSeconds * frictionSeed[index]) * damping)
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            moveBudgetX[index] = (moveBudgetX[index] + vx[index] * deltaSeconds +
                signedNoise(index, simulationStep * 43 + 11) * sandFluidBudgetJitter * fluidizedStrength)
                .coerceIn(-sandMaxMoveBudget, sandMaxMoveBudget)
            moveBudgetY[index] = (moveBudgetY[index] + vy[index] * deltaSeconds +
                signedNoise(index, simulationStep * 43 + 23) * sandFluidBudgetJitter * fluidizedStrength)
                .coerceIn(-sandMaxMoveBudget, sandMaxMoveBudget)
        }
    }

    private fun relaxMargolusBlocks(
        gravity: MatterVector,
        impulse: MatterVector,
        fluidizedStrength: Float,
    ) {
        val phase = simulationStep and 3
        val offsetX = phase and 1
        val offsetY = (phase ushr 1) and 1
        val driveX = gravity.x * sandBlockGravityDrive + impulse.x * sandBlockImpulseDrive
        val driveY = gravity.y * sandBlockGravityDrive + impulse.y * sandBlockImpulseDrive
        var yCursor = offsetY
        while (yCursor < height - 1) {
            var xCursor = offsetX
            while (xCursor < width - 1) {
                relaxBlock(xCursor, yCursor, driveX, driveY, fluidizedStrength)
                xCursor += 2
            }
            yCursor += 2
        }
    }

    private fun relaxBlock(
        blockX: Int,
        blockY: Int,
        driveX: Float,
        driveY: Float,
        fluidizedStrength: Float,
    ) {
        for (local in 0..3) {
            val px = blockX + (local and 1)
            val py = blockY + (local ushr 1)
            val index = indexAt(px, py)
            if (index == emptyCell) continue
            var bestDx = 0
            var bestDy = 0
            var bestScore = sandBlockProjection
            for (targetLocal in 0..3) {
                val tx = blockX + (targetLocal and 1)
                val ty = blockY + (targetLocal ushr 1)
                if (tx == px && ty == py) continue
                if (indexAt(tx, ty) != emptyCell) continue
                val dx = tx - px
                val dy = ty - py
                val score = dx * driveX + dy * driveY +
                    signedNoise(index, simulationStep * 53 + targetLocal) * sandBlockNoise * fluidizedStrength
                if (score > bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
            if (bestDx != 0 || bestDy != 0) {
                moveParticle(index, bestDx, bestDy)
            }
        }
    }

    private fun scanSand(
        gravity: MatterVector,
        impulse: MatterVector,
        fluidizedStrength: Float,
        handInput: PixelMatterHandSnapshot?,
    ) {
        val driveBaseX = gravity.x * sandGravityDrive + impulse.x * sandImpulseDrive
        val driveBaseY = gravity.y * sandGravityDrive + impulse.y * sandImpulseDrive
        val handRadius = matterHandRadius(width, height, sandHandRadiusRatio)
        scanCells(driveBaseX, driveBaseY) { index ->
            val handForce = PixelMatterHandForces.forceAt(
                x = cellX[index].toFloat(),
                y = cellY[index].toFloat(),
                input = handInput,
                radius = handRadius,
                repelStrength = sandHandRepelDrive,
                attractStrength = sandHandAttractDrive,
                windStrength = sandHandWindDrive,
            )
            val localFluidizedStrength = max(fluidizedStrength, handForce.strength * sandHandFluidizedScale)
            val minProjection = lerp(sandMoveProjection, sandFluidMoveProjection, localFluidizedStrength)
            val driveX = driveBaseX + moveBudgetX[index] + vx[index] * sandVelocityDrive +
                handForce.x +
                signedNoise(index, simulationStep * 59 + 3) * sandFluidDriveNoise * localFluidizedStrength
            val driveY = driveBaseY + moveBudgetY[index] + vy[index] * sandVelocityDrive +
                handForce.y +
                signedNoise(index, simulationStep * 59 + 17) * sandFluidDriveNoise * localFluidizedStrength
            val moved = tryMoveBest(
                index = index,
                driveX = driveX,
                driveY = driveY,
                gravity = gravity,
                minProjection = minProjection,
                sideRatio = lerp(sandSideMoveRatio, sandFluidSideMoveRatio, localFluidizedStrength),
                noise = sandCandidateNoise * localFluidizedStrength,
            )
            if (moved) {
                moveBudgetX[index] *= sandMoveBudgetDamping
                moveBudgetY[index] *= sandMoveBudgetDamping
                vx[index] *= lerp(sandSlideVelocityDamping, sandFluidSlideVelocityDamping, localFluidizedStrength)
                vy[index] *= lerp(sandSlideVelocityDamping, sandFluidSlideVelocityDamping, localFluidizedStrength)
            } else {
                transferPressure(index, gravity, localFluidizedStrength)
                vx[index] *= lerp(sandBlockedVelocityDamping, sandFluidBlockedVelocityDamping, localFluidizedStrength)
                vy[index] *= lerp(sandBlockedVelocityDamping, sandFluidBlockedVelocityDamping, localFluidizedStrength)
                moveBudgetX[index] *= lerp(sandBlockedBudgetDamping, sandFluidBlockedBudgetDamping, localFluidizedStrength)
                moveBudgetY[index] *= lerp(sandBlockedBudgetDamping, sandFluidBlockedBudgetDamping, localFluidizedStrength)
            }
        }
    }

    private fun tryMoveBest(
        index: Int,
        driveX: Float,
        driveY: Float,
        gravity: MatterVector,
        minProjection: Float,
        sideRatio: Float,
        noise: Float,
    ): Boolean {
        var triedMask = 0
        repeat(candidateDx.size) {
            var bestCandidate = -1
            var bestScore = minProjection
            for (candidate in candidateDx.indices) {
                val bit = 1 shl candidate
                if ((triedMask and bit) != 0) continue
                val dx = candidateDx[candidate]
                val dy = candidateDy[candidate]
                if (!isMoveAllowedByRepose(dx, dy, driveX + gravity.x * sandReposeGravityBias, driveY + gravity.y * sandReposeGravityBias, sideRatio)) {
                    continue
                }
                val projection = dx * driveX + dy * driveY
                if (projection <= minProjection) continue
                val gravityProjection = dx * gravity.x + dy * gravity.y
                val score = (projection + gravityProjection * sandGravityCandidateBias) /
                    candidateDistance[candidate] +
                    signedNoise(index, simulationStep * 109 + candidate * 13) * noise
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = candidate
                }
            }
            if (bestCandidate < 0) return false
            triedMask = triedMask or (1 shl bestCandidate)
            if (moveParticle(index, candidateDx[bestCandidate], candidateDy[bestCandidate])) return true
        }
        return false
    }

    private fun transferPressure(index: Int, gravity: MatterVector, fluidizedStrength: Float) {
        if (fluidizedStrength <= 0f) return
        val currentX = cellX[index]
        val currentY = cellY[index]
        for (candidate in candidateDx.indices) {
            val dx = candidateDx[candidate]
            val dy = candidateDy[candidate]
            val neighbor = indexAt(currentX + dx, currentY + dy)
            if (neighbor == emptyCell) continue
            val projection = dx * gravity.x + dy * gravity.y
            if (projection < sandPressureMinProjection) continue
            val transfer = fluidizedStrength / candidateDistance[candidate]
            vx[neighbor] = (vx[neighbor] + (gravity.x * sandPressureGravityTransfer - dy * sandPressureSideTransfer) * transfer)
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            vy[neighbor] = (vy[neighbor] + (gravity.y * sandPressureGravityTransfer + dx * sandPressureSideTransfer) * transfer)
                .coerceIn(-sandTerminalVelocity, sandTerminalVelocity)
            moveBudgetX[neighbor] = (moveBudgetX[neighbor] + dx * sandPressureBudgetTransfer * transfer)
                .coerceIn(-sandMaxMoveBudget, sandMaxMoveBudget)
            moveBudgetY[neighbor] = (moveBudgetY[neighbor] + dy * sandPressureBudgetTransfer * transfer)
                .coerceIn(-sandMaxMoveBudget, sandMaxMoveBudget)
        }
    }

    private fun consumeImpulseAcceleration(deltaSeconds: Float): MatterVector {
        if (impulseRemainingSeconds <= 0f) {
            impulseX = 0f
            impulseY = 0f
            return MatterVector(0f, 0f)
        }
        val factor = (impulseRemainingSeconds / sandImpulseSeconds).coerceIn(0f, 1f)
        val result = MatterVector(impulseX * factor, impulseY * factor)
        impulseRemainingSeconds = (impulseRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
        if (impulseRemainingSeconds <= 0f) {
            impulseX = 0f
            impulseY = 0f
        }
        return result
    }

    private fun consumeFluidizedStrength(deltaSeconds: Float): Float {
        if (fluidizedRemainingSeconds <= 0f) return 0f
        val strength = (fluidizedRemainingSeconds / sandFluidizedFadeSeconds).coerceIn(0f, 1f)
        fluidizedRemainingSeconds = (fluidizedRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
        return strength * strength
    }
}

private class WaterGridSimulation(
    seed: PixelMatterSeed,
    snapshot: DeviceMotionSnapshot,
    random: Random,
) : GridMatterSimulation(seed) {
    private val vx = FloatArray(particleCount)
    private val vy = FloatArray(particleCount)
    private var simulationStep = 0
    private var impulseX = 0f
    private var impulseY = 0f
    private var impulseRemainingSeconds = 0f

    init {
        val initialImpulse = PixelMatterMotionMapper.toScreenLinearImpulse(snapshot)
        for (index in 0 until particleCount) {
            vx[index] = (initialImpulse.x * waterInitialImpulseVelocityScale +
                random.nextFloatIn(-waterInitialJitter, waterInitialJitter))
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
            vy[index] = (initialImpulse.y * waterInitialImpulseVelocityScale +
                random.nextFloatIn(-waterInitialJitter, waterInitialJitter))
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
        }
    }

    override fun step(
        deltaSeconds: Float,
        snapshot: DeviceMotionSnapshot,
        handInput: PixelMatterHandSnapshot?,
    ) {
        if (particleCount == 0) return
        val safeDelta = deltaSeconds.coerceIn(0.001f, 0.080f)
        val gravity = PixelMatterMotionMapper.toScreenAcceleration(snapshot)
        buildOccupiedCells()
        val steps = physicsStepCount(safeDelta)
        val stepSeconds = safeDelta / steps.toFloat()
        repeat(steps) {
            simulationStep += 1
            val impulse = consumeImpulseAcceleration(stepSeconds)
            val driveX = gravity.x * waterGravityDrive + impulse.x * waterImpulseDrive
            val driveY = gravity.y * waterGravityDrive + impulse.y * waterImpulseDrive
            integrateWater(stepSeconds, driveX, driveY, handInput)
            scanWater(driveX, driveY, handInput)
        }
        syncFloatPositions()
    }

    override fun applyImpulse(snapshot: DeviceMotionSnapshot, random: Random) {
        val impulse = PixelMatterMotionMapper.toScreenLinearImpulse(snapshot)
        val safeX = impulse.x.coerceIn(-waterMaxImpulseAcceleration, waterMaxImpulseAcceleration)
        val safeY = impulse.y.coerceIn(-waterMaxImpulseAcceleration, waterMaxImpulseAcceleration)
        impulseX = (impulseX + safeX).coerceIn(-waterMaxImpulseAcceleration, waterMaxImpulseAcceleration)
        impulseY = (impulseY + safeY).coerceIn(-waterMaxImpulseAcceleration, waterMaxImpulseAcceleration)
        impulseRemainingSeconds = waterImpulseSeconds
        for (index in 0 until particleCount) {
            vx[index] = (vx[index] + safeX * waterDirectImpulseVelocityScale +
                random.nextFloatIn(-waterRuntimeJitter, waterRuntimeJitter))
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
            vy[index] = (vy[index] + safeY * waterDirectImpulseVelocityScale +
                random.nextFloatIn(-waterRuntimeJitter, waterRuntimeJitter))
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
        }
    }

    override fun clearParticleMotion(index: Int) {
        vx[index] = 0f
        vy[index] = 0f
    }

    private fun integrateWater(
        deltaSeconds: Float,
        driveX: Float,
        driveY: Float,
        handInput: PixelMatterHandSnapshot?,
    ) {
        val damping = waterVelocityDampingPerSecond.pow(deltaSeconds)
        val handRadius = matterHandRadius(width, height, waterHandRadiusRatio)
        for (index in 0 until particleCount) {
            val handForce = PixelMatterHandForces.forceAt(
                x = cellX[index].toFloat(),
                y = cellY[index].toFloat(),
                input = handInput,
                radius = handRadius,
                repelStrength = waterHandRepelAcceleration,
                attractStrength = waterHandAttractAcceleration,
                windStrength = waterHandWindAcceleration,
            )
            vx[index] = ((vx[index] + (driveX + handForce.x) * deltaSeconds) * damping)
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
            vy[index] = ((vy[index] + (driveY + handForce.y) * deltaSeconds) * damping)
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
        }
    }

    private fun scanWater(
        driveX: Float,
        driveY: Float,
        handInput: PixelMatterHandSnapshot?,
    ) {
        val handRadius = matterHandRadius(width, height, waterHandRadiusRatio)
        scanCells(driveX, driveY) { index ->
            val handForce = PixelMatterHandForces.forceAt(
                x = cellX[index].toFloat(),
                y = cellY[index].toFloat(),
                input = handInput,
                radius = handRadius,
                repelStrength = waterHandRepelDrive,
                attractStrength = waterHandAttractDrive,
                windStrength = waterHandWindDrive,
            )
            val flowX = driveX + vx[index] * waterVelocityDrive +
                handForce.x +
                signedNoise(index, simulationStep * 37 + 5) * waterFlowNoise
            val flowY = driveY + vy[index] * waterVelocityDrive + handForce.y
            if (tryMoveWater(index, flowX, flowY)) {
                vx[index] *= waterSlideVelocityDamping
                vy[index] *= waterSlideVelocityDamping
            } else {
                pushWaterPressure(index, flowX, flowY)
                vx[index] *= waterBlockedVelocityDamping
                vy[index] *= waterBlockedVelocityDamping
            }
        }
    }

    private fun tryMoveWater(index: Int, driveX: Float, driveY: Float): Boolean {
        if (tryProjectedMove(index, driveX, driveY, waterPrimaryProjection, allowSide = true)) return true
        val sideSign = if (signedNoise(index, simulationStep * 41 + 11) >= 0f) 1 else -1
        val sideX = directionFrom(driveX).takeIf { it != 0 } ?: sideSign
        if (trySpread(index, sideX)) return true
        return trySpread(index, -sideX)
    }

    private fun tryProjectedMove(
        index: Int,
        driveX: Float,
        driveY: Float,
        minProjection: Float,
        allowSide: Boolean,
    ): Boolean {
        var triedMask = 0
        repeat(candidateDx.size) {
            var bestCandidate = -1
            var bestScore = minProjection
            for (candidate in candidateDx.indices) {
                val bit = 1 shl candidate
                if ((triedMask and bit) != 0) continue
                val dx = candidateDx[candidate]
                val dy = candidateDy[candidate]
                if (!allowSide && (dx == 0 || dy == 0)) continue
                val projection = dx * driveX + dy * driveY
                if (projection <= minProjection) continue
                val score = projection / candidateDistance[candidate]
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = candidate
                }
            }
            if (bestCandidate < 0) return false
            triedMask = triedMask or (1 shl bestCandidate)
            if (moveParticle(index, candidateDx[bestCandidate], candidateDy[bestCandidate])) return true
        }
        return false
    }

    private fun trySpread(index: Int, sideX: Int): Boolean {
        val maxDistance = waterSpreadDistance.coerceAtMost(width)
        for (distance in 1..maxDistance) {
            val x = cellX[index] + sideX * distance
            val y = cellY[index]
            if (x !in 0 until width) return false
            if (indexAt(x, y) != emptyCell) return false
            if (distance == 1) return moveParticle(index, sideX, 0)
        }
        return false
    }

    private fun pushWaterPressure(index: Int, driveX: Float, driveY: Float) {
        val currentX = cellX[index]
        val currentY = cellY[index]
        for (candidate in candidateDx.indices) {
            val dx = candidateDx[candidate]
            val dy = candidateDy[candidate]
            val neighbor = indexAt(currentX + dx, currentY + dy)
            if (neighbor == emptyCell) continue
            val projection = dx * driveX + dy * driveY
            if (projection < waterPressureMinProjection) continue
            vx[neighbor] = (vx[neighbor] + dx * waterPressureVelocityTransfer)
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
            vy[neighbor] = (vy[neighbor] + dy * waterPressureVelocityTransfer)
                .coerceIn(-waterTerminalVelocity, waterTerminalVelocity)
        }
    }

    private fun consumeImpulseAcceleration(deltaSeconds: Float): MatterVector {
        if (impulseRemainingSeconds <= 0f) {
            impulseX = 0f
            impulseY = 0f
            return MatterVector(0f, 0f)
        }
        val factor = (impulseRemainingSeconds / waterImpulseSeconds).coerceIn(0f, 1f)
        val result = MatterVector(impulseX * factor, impulseY * factor)
        impulseRemainingSeconds = (impulseRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
        if (impulseRemainingSeconds <= 0f) {
            impulseX = 0f
            impulseY = 0f
        }
        return result
    }
}

private class SmokeParticleSimulation(
    seed: PixelMatterSeed,
    snapshot: DeviceMotionSnapshot,
    random: Random,
) : BasePixelMatterSimulation(seed) {
    private val vx = FloatArray(particleCount)
    private val vy = FloatArray(particleCount)
    private val drawColors = IntArray(particleCount)
    private val densityCells = IntArray(width * height)
    private val densitySumX = FloatArray(width * height)
    private val densitySumY = FloatArray(width * height)
    private var simulationStep = 0
    private var scratchPressureX = 0f
    private var scratchPressureY = 0f

    init {
        val centerX = (width - 1).coerceAtLeast(0) / 2f
        val centerY = (height - 1).coerceAtLeast(0) / 2f
        for (index in 0 until particleCount) {
            val dx = originX[index] - centerX
            val dy = originY[index] - centerY
            val distance = max(1f, sqrt(dx * dx + dy * dy))
            vx[index] = ((dx / distance) * smokeInitialBurst +
                random.nextFloatIn(-smokeInitialJitter, smokeInitialJitter))
                .coerceIn(-smokeTerminalVelocity, smokeTerminalVelocity)
            vy[index] = ((dy / distance) * smokeInitialBurst +
                random.nextFloatIn(-smokeInitialJitter, smokeInitialJitter))
                .coerceIn(-smokeTerminalVelocity, smokeTerminalVelocity)
            drawColors[index] = smokeColor(colors[index])
        }
    }

    override fun step(
        deltaSeconds: Float,
        snapshot: DeviceMotionSnapshot,
        handInput: PixelMatterHandSnapshot?,
    ) {
        if (particleCount == 0) return
        val safeDelta = deltaSeconds.coerceIn(0.001f, 0.080f)
        val damping = smokeVelocityDampingPerSecond.pow(safeDelta)
        simulationStep += 1
        buildSmokeDensity()
        val smokeCenterX = smokeCenterX()
        val smokeCenterY = smokeCenterY()
        for (index in 0 until particleCount) {
            updateSmokeDensityPressure(index)
            val handForce = smokeGestureForce(index, handInput, smokeCenterX, smokeCenterY)
            val curlX = signedNoise(index, simulationStep * 67 + 3) * smokeTurbulence
            val curlY = signedNoise(index, simulationStep * 67 + 19) * smokeTurbulence
            val accelX = scratchPressureX + handForce.x + curlX * (1f + handForce.strength * smokeHandTurbulenceBoost)
            val accelY = scratchPressureY + handForce.y + curlY * (1f + handForce.strength * smokeHandTurbulenceBoost)
            vx[index] = ((vx[index] + accelX * safeDelta) * damping)
                .coerceIn(-smokeTerminalVelocity, smokeTerminalVelocity)
            vy[index] = ((vy[index] + accelY * safeDelta) * damping)
                .coerceIn(-smokeTerminalVelocity, smokeTerminalVelocity)
            advanceSmokeParticle(index, x[index] + vx[index] * safeDelta, y[index] + vy[index] * safeDelta)
        }
    }

    override fun applyImpulse(snapshot: DeviceMotionSnapshot, random: Random) {
        for (index in 0 until particleCount) {
            vx[index] = (vx[index] + random.nextFloatIn(-smokeRuntimeJitter, smokeRuntimeJitter))
                .coerceIn(-smokeTerminalVelocity, smokeTerminalVelocity)
            vy[index] = (vy[index] + random.nextFloatIn(-smokeRuntimeJitter, smokeRuntimeJitter))
                .coerceIn(-smokeTerminalVelocity, smokeTerminalVelocity)
        }
    }

    override fun drawTo(buffer: PixelBuffer, offsetX: Int, offsetY: Int) {
        drawColorsTo(buffer, offsetX, offsetY, drawColors)
    }

    override fun clearParticleMotion(index: Int) {
        vx[index] = 0f
        vy[index] = 0f
    }

    private fun smokeColor(color: Int): Int {
        return (smokeAlpha shl 24) or (color and rgbMask)
    }

    private fun smokeCenterX(): Float {
        if (particleCount == 0) return 0f
        var sum = 0f
        for (index in 0 until particleCount) {
            sum += x[index]
        }
        return sum / particleCount.toFloat()
    }

    private fun smokeCenterY(): Float {
        if (particleCount == 0) return 0f
        var sum = 0f
        for (index in 0 until particleCount) {
            sum += y[index]
        }
        return sum / particleCount.toFloat()
    }

    private fun smokeGestureForce(
        index: Int,
        handInput: PixelMatterHandSnapshot?,
        smokeCenterX: Float,
        smokeCenterY: Float,
    ): MatterHandForce {
        val hand = handInput ?: return MatterHandForce.Zero
        val confidence = hand.confidence.coerceIn(0f, 1f)
        return when (hand.gesture) {
            PixelMatterHandGesture.PINCH ->
                smokePinchForce(index, hand, confidence)
            PixelMatterHandGesture.CLOSED_FIST ->
                smokeFistForce(index, hand, confidence)
            PixelMatterHandGesture.POINTING_UP ->
                smokePointForce(index, hand, confidence)
            PixelMatterHandGesture.VICTORY ->
                smokeVictoryForce(index, hand, confidence)
            PixelMatterHandGesture.THUMB_UP ->
                smokeThumbForce(hand, confidence, directionY = -1f)
            PixelMatterHandGesture.THUMB_DOWN ->
                smokeThumbForce(hand, confidence, directionY = 1f)
            PixelMatterHandGesture.I_LOVE_YOU ->
                smokeLoveForce(index, hand, confidence)
            PixelMatterHandGesture.OPEN_PALM,
            PixelMatterHandGesture.NONE ->
                smokeOpenForce(index, hand, confidence, smokeCenterX, smokeCenterY)
        }
    }

    private fun smokePinchForce(
        index: Int,
        hand: PixelMatterHandSnapshot,
        confidence: Float,
    ): MatterHandForce {
        val dx = hand.pinchCenter.x - x[index]
        val dy = hand.pinchCenter.y - y[index]
        val distance = max(0.35f, sqrt(dx * dx + dy * dy))
        val screenDistance = max(1f, max(width, height).toFloat())
        val distanceBoost = (distance / screenDistance).coerceIn(0.10f, 1f)
        val attract = smokePinchGlobalAttractAcceleration * (0.45f + distanceBoost) * confidence
        return MatterHandForce(
            x = dx / distance * attract + hand.palmVelocity.x * smokePinchDragAcceleration * confidence,
            y = dy / distance * attract + hand.palmVelocity.y * smokePinchDragAcceleration * confidence,
            strength = confidence,
        )
    }

    private fun smokeFistForce(
        index: Int,
        hand: PixelMatterHandSnapshot,
        confidence: Float,
    ): MatterHandForce {
        val dx = hand.palmCenter.x - x[index]
        val dy = hand.palmCenter.y - y[index]
        val distance = max(0.35f, sqrt(dx * dx + dy * dy))
        return MatterHandForce(
            x = dx / distance * smokeFistCrushAcceleration * confidence +
                hand.palmVelocity.x * smokeFistDragAcceleration * confidence,
            y = dy / distance * smokeFistCrushAcceleration * confidence +
                hand.palmVelocity.y * smokeFistDragAcceleration * confidence,
            strength = confidence,
        )
    }

    private fun smokePointForce(
        index: Int,
        hand: PixelMatterHandSnapshot,
        confidence: Float,
    ): MatterHandForce {
        val dx = x[index] - hand.indexTip.x
        val dy = y[index] - hand.indexTip.y
        val distance = max(0.35f, sqrt(dx * dx + dy * dy))
        val radius = matterHandRadius(width, height, smokePointRadiusRatio)
        val falloff = (1f - distance / radius).coerceIn(0f, 1f)
        if (falloff <= 0f) {
            return MatterHandForce(
                x = hand.palmVelocity.x * smokePointFarWindAcceleration * confidence,
                y = hand.palmVelocity.y * smokePointFarWindAcceleration * confidence,
                strength = confidence * 0.25f,
            )
        }
        val tangentX = -dy / distance
        val tangentY = dx / distance
        val radial = smokePointRepelAcceleration * falloff * falloff * confidence
        val swirl = smokePointSwirlAcceleration * falloff * confidence
        return MatterHandForce(
            x = dx / distance * radial + tangentX * swirl +
                hand.palmVelocity.x * smokePointLocalWindAcceleration * falloff * confidence,
            y = dy / distance * radial + tangentY * swirl +
                hand.palmVelocity.y * smokePointLocalWindAcceleration * falloff * confidence,
            strength = falloff * confidence,
        )
    }

    private fun smokeVictoryForce(
        index: Int,
        hand: PixelMatterHandSnapshot,
        confidence: Float,
    ): MatterHandForce {
        val target = if ((index and 1) == 0) hand.indexTip else hand.middleTip
        val dx = target.x - x[index]
        val dy = target.y - y[index]
        val distance = max(0.35f, sqrt(dx * dx + dy * dy))
        return MatterHandForce(
            x = dx / distance * smokeVictorySplitAcceleration * confidence +
                hand.palmVelocity.x * smokeVictoryWindAcceleration * confidence,
            y = dy / distance * smokeVictorySplitAcceleration * confidence +
                hand.palmVelocity.y * smokeVictoryWindAcceleration * confidence,
            strength = confidence,
        )
    }

    private fun smokeThumbForce(
        hand: PixelMatterHandSnapshot,
        confidence: Float,
        directionY: Float,
    ): MatterHandForce {
        return MatterHandForce(
            x = hand.palmVelocity.x * smokeThumbWindAcceleration * confidence,
            y = directionY * smokeThumbDirectionalAcceleration * confidence +
                hand.palmVelocity.y * smokeThumbWindAcceleration * confidence,
            strength = confidence,
        )
    }

    private fun smokeLoveForce(
        index: Int,
        hand: PixelMatterHandSnapshot,
        confidence: Float,
    ): MatterHandForce {
        val dx = x[index] - hand.palmCenter.x
        val dy = y[index] - hand.palmCenter.y
        val distance = max(0.35f, sqrt(dx * dx + dy * dy))
        val tangentSign = if (sin(hand.rotationRadians) >= 0f) 1f else -1f
        val tangentX = -dy / distance * tangentSign
        val tangentY = dx / distance * tangentSign
        return MatterHandForce(
            x = dx / distance * smokeLoveBurstAcceleration * confidence +
                tangentX * smokeLoveSwirlAcceleration * confidence,
            y = dy / distance * smokeLoveBurstAcceleration * confidence +
                tangentY * smokeLoveSwirlAcceleration * confidence,
            strength = confidence,
        )
    }

    private fun smokeOpenForce(
        index: Int,
        hand: PixelMatterHandSnapshot,
        confidence: Float,
        smokeCenterX: Float,
        smokeCenterY: Float,
    ): MatterHandForce {
        val cloudDx = x[index] - smokeCenterX
        val cloudDy = y[index] - smokeCenterY
        val cloudDistance = max(0.35f, sqrt(cloudDx * cloudDx + cloudDy * cloudDy))
        val palmDx = x[index] - hand.palmCenter.x
        val palmDy = y[index] - hand.palmCenter.y
        val palmDistance = max(0.35f, sqrt(palmDx * palmDx + palmDy * palmDy))
        val palmRadius = matterHandRadius(width, height, smokeOpenPalmRadiusRatio)
        val palmFalloff = (1f - (palmDistance / palmRadius)).coerceIn(0f, 1f)
        val speed = sqrt(
            hand.palmVelocity.x * hand.palmVelocity.x +
                hand.palmVelocity.y * hand.palmVelocity.y,
        )
        val waveStrength = (speed / smokeWaveReferenceVelocity).coerceIn(0f, 1f)
        val expand = smokeOpenGlobalDisperseAcceleration * confidence
        val localRepel = smokeOpenPalmRepelAcceleration * palmFalloff * palmFalloff * confidence
        return MatterHandForce(
            x = cloudDx / cloudDistance * expand +
                palmDx / palmDistance * localRepel +
                hand.palmVelocity.x * smokeOpenWaveWindAcceleration * confidence,
            y = cloudDy / cloudDistance * expand +
                palmDy / palmDistance * localRepel +
                hand.palmVelocity.y * smokeOpenWaveWindAcceleration * confidence,
            strength = max(confidence * 0.45f, waveStrength),
        )
    }

    private fun buildSmokeDensity() {
        densityCells.fill(0)
        densitySumX.fill(0f)
        densitySumY.fill(0f)
        if (width <= 0 || height <= 0) return
        for (index in 0 until particleCount) {
            val cellX = x[index].roundToInt().coerceIn(0, width - 1)
            val cellY = y[index].roundToInt().coerceIn(0, height - 1)
            val cell = cellY * width + cellX
            densityCells[cell] = (densityCells[cell] + 1).coerceAtMost(smokeDensityCap)
            densitySumX[cell] += x[index]
            densitySumY[cell] += y[index]
        }
    }

    private fun updateSmokeDensityPressure(index: Int) {
        scratchPressureX = 0f
        scratchPressureY = 0f
        if (width <= 0 || height <= 0) return
        val centerX = x[index].roundToInt().coerceIn(0, width - 1)
        val centerY = y[index].roundToInt().coerceIn(0, height - 1)
        val centerCell = centerY * width + centerX
        val centerDensity = densityCells[centerCell]
        for (dy in -1..1) {
            val neighborY = centerY + dy
            if (neighborY !in 0 until height) continue
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val neighborX = centerX + dx
                if (neighborX !in 0 until width) continue
                val density = densityCells[neighborY * width + neighborX]
                if (density <= smokeTargetCellDensity) continue
                val excess = (density - smokeTargetCellDensity).toFloat()
                val distance = if (dx != 0 && dy != 0) sqrtTwo else 1f
                scratchPressureX -= dx * excess / distance
                scratchPressureY -= dy * excess / distance
            }
        }
        if (centerDensity > smokeTargetCellDensity) {
            val excess = (centerDensity - smokeTargetCellDensity).toFloat()
            val averageX = densitySumX[centerCell] / centerDensity.toFloat()
            val averageY = densitySumY[centerCell] / centerDensity.toFloat()
            var awayX = x[index] - averageX
            var awayY = y[index] - averageY
            val distanceSquared = awayX * awayX + awayY * awayY
            if (distanceSquared < smokeMinCentroidDistanceSquared) {
                awayX = signedNoise(index, simulationStep * 79 + 5)
                awayY = signedNoise(index, simulationStep * 79 + 17)
            } else {
                val inverseDistance = 1f / sqrt(distanceSquared)
                awayX *= inverseDistance
                awayY *= inverseDistance
            }
            scratchPressureX += awayX * excess * smokeSameCellPressureScale
            scratchPressureY += awayY * excess * smokeSameCellPressureScale
        }
        scratchPressureX *= smokeDensityPressureScale
        scratchPressureY *= smokeDensityPressureScale
    }

    private fun advanceSmokeParticle(
        index: Int,
        nextX: Float,
        nextY: Float,
    ) {
        val maxX = (width - 1).coerceAtLeast(0).toFloat()
        val maxY = (height - 1).coerceAtLeast(0).toFloat()
        var targetX = nextX
        var targetY = nextY
        if (targetX < 0f) {
            targetX = smokeBoundaryInset(index).coerceAtMost(maxX)
            vx[index] = abs(vx[index]) * smokeBoundaryNormalReturn
            vy[index] += smokeBoundaryTangentVelocity(index, verticalBoundary = false)
        } else if (targetX > maxX) {
            targetX = (maxX - smokeBoundaryInset(index)).coerceAtLeast(0f)
            vx[index] = -abs(vx[index]) * smokeBoundaryNormalReturn
            vy[index] += smokeBoundaryTangentVelocity(index, verticalBoundary = false)
        }
        if (targetY < 0f) {
            targetY = smokeBoundaryInset(index).coerceAtMost(maxY)
            vy[index] = abs(vy[index]) * smokeBoundaryNormalReturn
            vx[index] += smokeBoundaryTangentVelocity(index, verticalBoundary = true)
        } else if (targetY > maxY) {
            targetY = (maxY - smokeBoundaryInset(index)).coerceAtLeast(0f)
            vy[index] = -abs(vy[index]) * smokeBoundaryNormalReturn
            vx[index] += smokeBoundaryTangentVelocity(index, verticalBoundary = true)
        }
        x[index] = targetX.coerceIn(0f, maxX)
        y[index] = targetY.coerceIn(0f, maxY)
    }

    private fun smokeBoundaryTangentVelocity(
        index: Int,
        verticalBoundary: Boolean,
    ): Float {
        return signedNoise(index, simulationStep * 89 + if (verticalBoundary) 7 else 23) * smokeBoundaryTangentJitter
    }

    private fun smokeBoundaryInset(index: Int): Float {
        val normalized = (signedNoise(index, simulationStep * 97 + 31) + 1f) * 0.5f
        return smokeBoundaryMinInset + normalized * (smokeBoundaryMaxInset - smokeBoundaryMinInset)
    }
}

internal object PixelMatterMotionMapper {
    fun toScreenAcceleration(snapshot: DeviceMotionSnapshot): MatterVector {
        return MatterVector(
            x = snapshot.screenGravityX * gravityScale,
            y = snapshot.screenGravityY * gravityScale,
        )
    }

    fun toScreenLinearImpulse(snapshot: DeviceMotionSnapshot): MatterVector {
        return MatterVector(
            x = snapshot.screenLinearAccelX * linearImpulseScale,
            y = snapshot.screenLinearAccelY * linearImpulseScale,
        )
    }

    private const val gravityScale: Float = 4.0f
    private const val linearImpulseScale: Float = 7.5f
}

internal data class MatterVector(
    val x: Float,
    val y: Float,
)

private data class MatterCell(
    val x: Int,
    val y: Int,
)

internal fun PixelMatterEffectLayer(
    simulation: PixelMatterSimulation,
    onTapToRestore: () -> Unit,
    key: Any? = null,
): Widget = GestureDetector(
    child = PixelMatterEffectRenderWidget(simulation = simulation, key = key),
    onTap = onTapToRestore,
    onSwipeStart = {},
    onSwipeUpdate = { _ -> },
    onSwipeEnd = { _ -> },
    onSwipeLeft = {},
    onSwipeRight = {},
    key = key?.let { "$it-gesture" },
)

private class PixelMatterEffectRenderWidget(
    private val simulation: PixelMatterSimulation,
    override val key: Any?,
) : PixelLeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderPixelMatterEffect(simulation)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderPixelMatterEffect).update(simulation)
    }
}

private class RenderPixelMatterEffect(
    private var simulation: PixelMatterSimulation,
) : PixelRenderBox() {
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        simulation.drawTo(context.buffer, offsetX, offsetY)
    }

    fun update(next: PixelMatterSimulation) {
        simulation = next
        markNeedsPaint()
    }
}

internal fun nextPixelMatterEffectMode(
    current: PixelMatterEffectMode,
    direction: Int,
): PixelMatterEffectMode {
    val entries = PixelMatterEffectMode.entries
    val index = entries.indexOf(current).takeIf { it >= 0 } ?: 0
    return entries[wrapIndex(index + direction, entries.size)]
}

internal fun pixelMatterEffectModeLabel(mode: PixelMatterEffectMode): String = mode.name

private fun isMoveAllowedByRepose(
    dx: Int,
    dy: Int,
    forceX: Float,
    forceY: Float,
    sideRatio: Float,
): Boolean {
    val absForceX = abs(forceX)
    val absForceY = abs(forceY)
    if (dx != 0 && dy == 0 && absForceX < absForceY * sideRatio) return false
    if (dy != 0 && dx == 0 && absForceY < absForceX * sideRatio) return false
    return true
}

private fun physicsStepCount(deltaSeconds: Float): Int {
    return (deltaSeconds / fixedPhysicsStepSeconds).roundToInt()
        .coerceIn(minPhysicsSteps, maxPhysicsSteps)
}

private fun matterHandRadius(width: Int, height: Int, ratio: Float): Float {
    return max(3f, min(width, height).toFloat() * ratio)
}

private fun DeviceMotionSnapshot.linearAccelerationMagnitude(): Float {
    return sqrt(
        linearAccelX * linearAccelX +
            linearAccelY * linearAccelY +
            linearAccelZ * linearAccelZ,
    )
}

private fun Random.nextFloatIn(min: Float, max: Float): Float {
    return min + (max - min) * nextFloat()
}

private fun easeInOut(t: Float): Float {
    return if (t < 0.5f) {
        4f * t * t * t
    } else {
        val f = -2f * t + 2f
        1f - (f * f * f) / 2f
    }
}

private fun alternatingOffset(index: Int): Int {
    if (index == 0) return 0
    val step = (index + 1) / 2
    return if (index % 2 == 1) step else -step
}

private fun directionFrom(value: Float): Int {
    return when {
        value > 0f -> 1
        value < 0f -> -1
        else -> 0
    }
}

private fun lerp(start: Float, end: Float, amount: Float): Float {
    return start + (end - start) * amount.coerceIn(0f, 1f)
}

private fun signedNoise(index: Int, salt: Int): Float {
    var value = index * 0x1F1F1F1F + salt * 0x45D9F3B
    value = (value xor (value ushr 16)) * 0x7FEB352D
    value = (value xor (value ushr 15)) * 0x846CA68B.toInt()
    value = value xor (value ushr 16)
    return ((value and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF.toFloat()) - 1f
}

private fun opaqueRgb(color: Int): Int {
    return opaqueAlphaMask or (color and rgbMask)
}

private fun sameRgb(first: Int, second: Int): Boolean {
    return (first and rgbMask) == (second and rgbMask)
}

private fun wrapIndex(index: Int, size: Int): Int {
    if (size <= 0) return 0
    val mod = index % size
    return if (mod < 0) mod + size else mod
}

private const val minPhysicsSteps: Int = 1
private const val maxPhysicsSteps: Int = 4
private const val fixedPhysicsStepSeconds: Float = 1f / 60f
private const val emptyCell: Int = -1
private const val sqrtTwo: Float = 1.4142135f
private val candidateDx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
private val candidateDy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
private val candidateDistance = floatArrayOf(sqrtTwo, 1f, sqrtTwo, 1f, 1f, sqrtTwo, 1f, sqrtTwo)

private const val sandGravityAccelerationScale: Float = 0.82f
private const val sandVelocityDampingPerSecond: Float = 0.42f
private const val sandFluidVelocityDampingPerSecond: Float = 0.72f
private const val sandTerminalVelocity: Float = 34f
private const val sandInitialFluidizedSeconds: Float = 0.95f
private const val sandRuntimeFluidizedSeconds: Float = 0.82f
private const val sandFluidizedFadeSeconds: Float = 0.82f
private const val sandImpulseSeconds: Float = 0.180f
private const val sandMaxImpulseAcceleration: Float = 190f
private const val sandDirectImpulseVelocityScale: Float = 0.055f
private const val sandRuntimeImpulseJitter: Float = 2.6f
private const val sandMinFrictionSeed: Float = 0.88f
private const val sandMaxFrictionSeed: Float = 1.12f
private const val sandInitialImpulseVelocityScale: Float = 0.85f
private const val sandInitialBurstBase: Float = 22f
private const val sandInitialBurstRange: Float = 42f
private const val sandInitialJitter: Float = 16f
private const val sandMaxMoveBudget: Float = 2.25f
private const val sandFluidStirAcceleration: Float = 38f
private const val sandFluidBudgetJitter: Float = 0.16f
private const val sandBlockGravityDrive: Float = 0.10f
private const val sandBlockImpulseDrive: Float = 0.020f
private const val sandBlockProjection: Float = -0.04f
private const val sandBlockNoise: Float = 0.11f
private const val sandGravityDrive: Float = 0.16f
private const val sandImpulseDrive: Float = 0.018f
private const val sandVelocityDrive: Float = 0.035f
private const val sandMoveProjection: Float = 0.20f
private const val sandFluidMoveProjection: Float = -0.10f
private const val sandFluidDriveNoise: Float = 0.32f
private const val sandSideMoveRatio: Float = 0.55f
private const val sandFluidSideMoveRatio: Float = 0.12f
private const val sandCandidateNoise: Float = 0.055f
private const val sandReposeGravityBias: Float = 0.08f
private const val sandGravityCandidateBias: Float = 0.015f
private const val sandMoveBudgetDamping: Float = 0.42f
private const val sandSlideVelocityDamping: Float = 0.90f
private const val sandFluidSlideVelocityDamping: Float = 0.98f
private const val sandBlockedVelocityDamping: Float = 0.32f
private const val sandFluidBlockedVelocityDamping: Float = 0.70f
private const val sandBlockedBudgetDamping: Float = 0.20f
private const val sandFluidBlockedBudgetDamping: Float = 0.62f
private const val sandPressureMinProjection: Float = -0.25f
private const val sandPressureGravityTransfer: Float = 0.020f
private const val sandPressureSideTransfer: Float = 0.36f
private const val sandPressureBudgetTransfer: Float = 0.18f
private const val sandHandRadiusRatio: Float = 0.34f
private const val sandHandRepelAcceleration: Float = 140f
private const val sandHandAttractAcceleration: Float = 72f
private const val sandHandWindAcceleration: Float = 0.36f
private const val sandHandRepelDrive: Float = 1.45f
private const val sandHandAttractDrive: Float = 0.90f
private const val sandHandWindDrive: Float = 0.0080f
private const val sandHandFluidizedScale: Float = 1.0f

private const val waterVelocityDampingPerSecond: Float = 0.62f
private const val waterTerminalVelocity: Float = 42f
private const val waterInitialImpulseVelocityScale: Float = 0.55f
private const val waterInitialJitter: Float = 5f
private const val waterGravityDrive: Float = 0.25f
private const val waterImpulseDrive: Float = 0.018f
private const val waterVelocityDrive: Float = 0.040f
private const val waterFlowNoise: Float = 0.10f
private const val waterPrimaryProjection: Float = 0.08f
private const val waterSlideVelocityDamping: Float = 0.94f
private const val waterBlockedVelocityDamping: Float = 0.62f
private const val waterMaxImpulseAcceleration: Float = 170f
private const val waterDirectImpulseVelocityScale: Float = 0.045f
private const val waterRuntimeJitter: Float = 1.8f
private const val waterImpulseSeconds: Float = 0.22f
private const val waterSpreadDistance: Int = 4
private const val waterPressureMinProjection: Float = -0.12f
private const val waterPressureVelocityTransfer: Float = 0.20f
private const val waterHandRadiusRatio: Float = 0.38f
private const val waterHandRepelAcceleration: Float = 150f
private const val waterHandAttractAcceleration: Float = 110f
private const val waterHandWindAcceleration: Float = 0.45f
private const val waterHandRepelDrive: Float = 1.30f
private const val waterHandAttractDrive: Float = 1.0f
private const val waterHandWindDrive: Float = 0.0090f

private const val smokeVelocityDampingPerSecond: Float = 0.52f
private const val smokeTerminalVelocity: Float = 160f
private const val smokeInitialBurst: Float = 9f
private const val smokeInitialJitter: Float = 5f
private const val smokeTurbulence: Float = 2.4f
private const val smokeDensityPressureScale: Float = 3.6f
private const val smokeSameCellPressureScale: Float = 1.45f
private const val smokeMinCentroidDistanceSquared: Float = 0.015625f
private const val smokeTargetCellDensity: Int = 1
private const val smokeDensityCap: Int = 8
private const val smokeBoundaryNormalReturn: Float = 0.18f
private const val smokeBoundaryMinInset: Float = 0.75f
private const val smokeBoundaryMaxInset: Float = 2.20f
private const val smokeBoundaryTangentJitter: Float = 7.5f
private const val smokeRuntimeJitter: Float = 3f
private const val smokeAlpha: Int = 176
private const val smokePinchGlobalAttractAcceleration: Float = 820f
private const val smokePinchDragAcceleration: Float = 2.6f
private const val smokeFistCrushAcceleration: Float = 980f
private const val smokeFistDragAcceleration: Float = 1.35f
private const val smokePointRadiusRatio: Float = 0.38f
private const val smokePointRepelAcceleration: Float = 230f
private const val smokePointSwirlAcceleration: Float = 360f
private const val smokePointLocalWindAcceleration: Float = 1.25f
private const val smokePointFarWindAcceleration: Float = 0.18f
private const val smokeVictorySplitAcceleration: Float = 520f
private const val smokeVictoryWindAcceleration: Float = 1.15f
private const val smokeThumbDirectionalAcceleration: Float = 360f
private const val smokeThumbWindAcceleration: Float = 1.0f
private const val smokeLoveBurstAcceleration: Float = 440f
private const val smokeLoveSwirlAcceleration: Float = 520f
private const val smokeOpenGlobalDisperseAcceleration: Float = 92f
private const val smokeOpenPalmRadiusRatio: Float = 0.72f
private const val smokeOpenPalmRepelAcceleration: Float = 145f
private const val smokeOpenWaveWindAcceleration: Float = 1.35f
private const val smokeWaveReferenceVelocity: Float = 34f
private const val smokeHandTurbulenceBoost: Float = 2.4f

private const val rgbMask: Int = 0x00FFFFFF
private const val opaqueAlphaMask: Int = -0x1000000
