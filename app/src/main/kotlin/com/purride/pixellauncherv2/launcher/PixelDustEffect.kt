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
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

internal enum class PixelDustEffectPhase {
    IDLE,
    ACTIVE,
    RESTORING,
}

internal class PixelDustShakeDetector(
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

internal class PixelDustEffectController(
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
    private var lastDispatchedElapsedNanos = -1L
    private var lastElapsedNanos = -1L
    private var restoringElapsedMs = 0L

    var phase: PixelDustEffectPhase = PixelDustEffectPhase.IDLE
        private set

    var field: PixelDustParticleField? = null
        private set

    fun isVisible(): Boolean = phase != PixelDustEffectPhase.IDLE && field != null

    fun isActive(): Boolean = phase == PixelDustEffectPhase.ACTIVE

    fun isRestoring(): Boolean = phase == PixelDustEffectPhase.RESTORING

    fun updateMotion(snapshot: DeviceMotionSnapshot) {
        lastMotion = snapshot
    }

    fun start(
        buffer: PixelBuffer,
        snapshot: DeviceMotionSnapshot,
        backgroundColor: Int = PixelColor.Black.argb,
    ): Boolean {
        val nextField = PixelDustParticleField.fromBuffer(
            buffer = buffer,
            snapshot = snapshot,
            backgroundColor = backgroundColor,
            random = randomSource,
        ) ?: return false
        lastMotion = snapshot
        field = nextField
        phase = PixelDustEffectPhase.ACTIVE
        lastDispatchedElapsedNanos = -1L
        lastElapsedNanos = -1L
        restoringElapsedMs = 0L
        ticker.start()
        onEffectStart()
        onFrame()
        return true
    }

    fun requestRestore(): Boolean {
        val target = field ?: return false
        if (phase == PixelDustEffectPhase.IDLE) return false
        if (phase == PixelDustEffectPhase.RESTORING) return true
        target.beginRestore()
        phase = PixelDustEffectPhase.RESTORING
        restoringElapsedMs = 0L
        lastElapsedNanos = -1L
        ticker.start()
        onFrame()
        return true
    }

    fun clear() {
        val wasVisible = isVisible()
        ticker.stop()
        phase = PixelDustEffectPhase.IDLE
        field = null
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
        field = null
        phase = PixelDustEffectPhase.IDLE
    }

    private fun onTick(elapsedNanos: Long) {
        val target = field ?: run {
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
            PixelDustEffectPhase.IDLE -> clear()
            PixelDustEffectPhase.ACTIVE -> {
                target.stepActive(deltaMs / 1_000f, lastMotion)
            }

            PixelDustEffectPhase.RESTORING -> {
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

internal class PixelDustParticleField private constructor(
    val width: Int,
    val height: Int,
    private val originX: IntArray,
    private val originY: IntArray,
    private val x: FloatArray,
    private val y: FloatArray,
    private val cellX: IntArray,
    private val cellY: IntArray,
    private val scatterX: FloatArray,
    private val scatterY: FloatArray,
    private val colors: IntArray,
) {
    private var restoreStartX: FloatArray = FloatArray(0)
    private var restoreStartY: FloatArray = FloatArray(0)
    private val occupiedCells = IntArray(width * height) { emptyCell }
    private val particleOrder = IntArray(colors.size) { it }
    private var simulationStep = 0
    private var hasSmoothedGravity = false
    private var smoothedGravityX = 0f
    private var smoothedGravityY = 0f

    val particleCount: Int
        get() = colors.size

    fun stepActive(deltaSeconds: Float, snapshot: DeviceMotionSnapshot) {
        if (particleCount == 0) return
        val safeDelta = deltaSeconds.coerceIn(0.001f, 0.080f)
        val gravity = smoothGravity(PixelDustMotionMapper.toScreenAcceleration(snapshot), safeDelta)
        val gravityForceSquared = forceMagnitudeSquared(gravity.x, gravity.y)
        buildOccupiedCells()
        simulationStep += 1

        repeat(substepCount(gravity)) {
            sortParticleOrder(gravity)
            for (orderIndex in particleOrder.indices) {
                val index = particleOrder[orderIndex]
                val scatterForceSquared = forceMagnitudeSquared(scatterX[index], scatterY[index])
                if (gravityForceSquared < staticFrictionForceSquared &&
                    scatterForceSquared < settledScatterForceSquared
                ) {
                    continue
                }

                val forceX = gravity.x + scatterX[index]
                val forceY = gravity.y + scatterY[index]
                if (forceMagnitudeSquared(forceX, forceY) < minMovementForceSquared) continue

                clearCell(cellX[index], cellY[index], index)
                val moved = tryMoveSandCell(index, forceX, forceY)
                claimCell(cellX[index], cellY[index], index)

                if (!moved) {
                    scatterX[index] *= blockedScatterDamping
                    scatterY[index] *= blockedScatterDamping
                }
            }
        }

        val scatterDamping = scatterDampingPerSecond.pow(safeDelta)
        for (index in colors.indices) {
            scatterX[index] *= scatterDamping
            scatterY[index] *= scatterDamping
            x[index] = cellX[index].toFloat()
            y[index] = cellY[index].toFloat()
        }
    }

    private fun smoothGravity(rawGravity: ScreenVector, deltaSeconds: Float): ScreenVector {
        if (!hasSmoothedGravity) {
            smoothedGravityX = rawGravity.x
            smoothedGravityY = rawGravity.y
            hasSmoothedGravity = true
            return rawGravity
        }
        val alpha = (deltaSeconds / (gravitySmoothingTauSeconds + deltaSeconds)).coerceIn(0f, 1f)
        smoothedGravityX += (rawGravity.x - smoothedGravityX) * alpha
        smoothedGravityY += (rawGravity.y - smoothedGravityY) * alpha
        return ScreenVector(smoothedGravityX, smoothedGravityY)
    }

    private fun buildOccupiedCells() {
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

    private fun tryMoveSandCell(index: Int, forceX: Float, forceY: Float): Boolean {
        var triedMask = 0
        repeat(candidateDx.size) {
            var bestCandidate = -1
            var bestScore = minMovementProjection
            for (candidate in candidateDx.indices) {
                val bit = 1 shl candidate
                if ((triedMask and bit) != 0) continue
                val dx = candidateDx[candidate]
                val dy = candidateDy[candidate]
                if (!isCandidateAllowedByRepose(dx, dy, forceX, forceY)) continue
                val projection = dx * forceX + dy * forceY
                if (projection <= minMovementProjection) continue
                val score = candidateScore(
                    projection = projection,
                    candidate = candidate,
                    dx = dx,
                    dy = dy,
                    forceX = forceX,
                    forceY = forceY,
                    preferredSide = preferredSide(index),
                )
                if (score > bestScore) {
                    bestScore = score
                    bestCandidate = candidate
                }
            }
            if (bestCandidate < 0) return false
            triedMask = triedMask or (1 shl bestCandidate)
            if (tryMoveBy(index, candidateDx[bestCandidate], candidateDy[bestCandidate])) return true
        }
        return false
    }

    private fun isCandidateAllowedByRepose(dx: Int, dy: Int, forceX: Float, forceY: Float): Boolean {
        val absForceX = abs(forceX)
        val absForceY = abs(forceY)
        if (dx != 0 && dy == 0 && absForceX < absForceY * pureSideMoveForceRatio) return false
        if (dy != 0 && dx == 0 && absForceY < absForceX * pureSideMoveForceRatio) return false
        return true
    }

    private fun tryMoveBy(index: Int, dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0) return false
        val nextX = cellX[index] + dx
        val nextY = cellY[index] + dy
        if (nextX !in 0 until width || nextY !in 0 until height) return false
        val cell = nextY * width + nextX
        if (occupiedCells[cell] != emptyCell) return false
        cellX[index] = nextX
        cellY[index] = nextY
        return true
    }

    private fun claimNearestEmptyCell(startX: Int, startY: Int): PixelDustCell? {
        val maxRadius = max(width, height)
        for (radius in 1..maxRadius) {
            for (dy in -radius..radius) {
                val y = startY + dy
                if (y !in 0 until height) continue
                val remaining = radius - abs(dy)
                for (offsetIndex in 0..remaining * 2) {
                    val x = startX + alternatingOffset(offsetIndex)
                    if (x !in 0 until width) continue
                    if (occupiedCells[y * width + x] == emptyCell) return PixelDustCell(x, y)
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

    private fun clearCell(cellX: Int, cellY: Int, index: Int) {
        val cell = cellY * width + cellX
        if (occupiedCells[cell] == index) {
            occupiedCells[cell] = emptyCell
        }
    }

    private fun preferredSide(index: Int): Int {
        return if (((index + simulationStep) and 1) == 0) 1 else -1
    }

    private fun candidateScore(
        projection: Float,
        candidate: Int,
        dx: Int,
        dy: Int,
        forceX: Float,
        forceY: Float,
        preferredSide: Int,
    ): Float {
        val side = directionFrom(dx * forceY - dy * forceX)
        val sideBias = when (side) {
            preferredSide -> sideTieBias
            -preferredSide -> -sideTieBias
            else -> 0f
        }
        return projection / candidateDistance[candidate] + sideBias
    }

    private fun substepCount(acceleration: ScreenVector): Int {
        val magnitude = sqrt(acceleration.x * acceleration.x + acceleration.y * acceleration.y)
        return (1 + (magnitude / forcePerSubstep).roundToInt())
            .coerceIn(minSandSubsteps, maxSandSubsteps)
    }

    private fun sortParticleOrder(acceleration: ScreenVector) {
        for (index in particleOrder.indices) {
            particleOrder[index] = index
        }
        quickSortParticleOrder(left = 0, right = particleOrder.lastIndex, acceleration = acceleration)
    }

    private fun quickSortParticleOrder(left: Int, right: Int, acceleration: ScreenVector) {
        if (left >= right) return
        var i = left
        var j = right
        val pivot = particleOrder[(left + right) ushr 1]
        while (i <= j) {
            while (compareParticleOrder(particleOrder[i], pivot, acceleration) < 0) i += 1
            while (compareParticleOrder(particleOrder[j], pivot, acceleration) > 0) j -= 1
            if (i <= j) {
                val tmp = particleOrder[i]
                particleOrder[i] = particleOrder[j]
                particleOrder[j] = tmp
                i += 1
                j -= 1
            }
        }
        if (left < j) quickSortParticleOrder(left, j, acceleration)
        if (i < right) quickSortParticleOrder(i, right, acceleration)
    }

    private fun compareParticleOrder(first: Int, second: Int, acceleration: ScreenVector): Int {
        val firstX = cellX[first]
        val firstY = cellY[first]
        val secondX = cellX[second]
        val secondY = cellY[second]
        val firstProjection = firstX * acceleration.x + firstY * acceleration.y
        val secondProjection = secondX * acceleration.x + secondY * acceleration.y
        val projectionCompare = secondProjection.compareTo(firstProjection)
        if (projectionCompare != 0) return projectionCompare
        return if (abs(acceleration.y) >= abs(acceleration.x)) {
            firstX.compareTo(secondX)
        } else {
            firstY.compareTo(secondY)
        }
    }

    fun beginRestore() {
        restoreStartX = x.copyOf()
        restoreStartY = y.copyOf()
    }

    fun applyRestore(progress: Float) {
        val startX = restoreStartX.takeIf { it.size == particleCount } ?: x
        val startY = restoreStartY.takeIf { it.size == particleCount } ?: y
        val t = easeInOut(progress.coerceIn(0f, 1f))
        for (index in colors.indices) {
            x[index] = startX[index] + (originX[index] - startX[index]) * t
            y[index] = startY[index] + (originY[index] - startY[index]) * t
            scatterX[index] = 0f
            scatterY[index] = 0f
        }
        hasSmoothedGravity = false
    }

    fun forceRestoreToOrigin() {
        for (index in colors.indices) {
            cellX[index] = originX[index]
            cellY[index] = originY[index]
            x[index] = originX[index].toFloat()
            y[index] = originY[index].toFloat()
            scatterX[index] = 0f
            scatterY[index] = 0f
        }
    }

    fun drawTo(buffer: PixelBuffer, offsetX: Int = 0, offsetY: Int = 0) {
        val targetWidth = buffer.width
        val targetHeight = buffer.height
        for (index in colors.indices) {
            val targetX = offsetX + x[index].roundToInt()
            val targetY = offsetY + y[index].roundToInt()
            if (targetX !in 0 until targetWidth || targetY !in 0 until targetHeight) continue
            val targetIndex = targetY * targetWidth + targetX
            val color = colors[index]
            buffer.pixels[targetIndex] = PixelBuffer.blendSrcOver(
                src = color,
                dst = buffer.pixels[targetIndex],
            )
        }
    }

    fun particlePosition(index: Int): Pair<Float, Float> {
        require(index in colors.indices) { "index $index out of bounds for $particleCount particles" }
        return x[index] to y[index]
    }

    fun originPosition(index: Int): Pair<Int, Int> {
        require(index in colors.indices) { "index $index out of bounds for $particleCount particles" }
        return originX[index] to originY[index]
    }

    companion object {
        fun fromBuffer(
            buffer: PixelBuffer,
            snapshot: DeviceMotionSnapshot,
            random: Random = Random.Default,
            backgroundColor: Int = PixelColor.Black.argb,
        ): PixelDustParticleField? {
            val count = buffer.pixels.count { color ->
                visibleDustColor(color, backgroundColor) != null
            }
            if (count == 0) return null

            val originX = IntArray(count)
            val originY = IntArray(count)
            val x = FloatArray(count)
            val y = FloatArray(count)
            val cellX = IntArray(count)
            val cellY = IntArray(count)
            val scatterX = FloatArray(count)
            val scatterY = FloatArray(count)
            val colors = IntArray(count)
            val centerX = (buffer.width - 1).coerceAtLeast(0) / 2f
            val centerY = (buffer.height - 1).coerceAtLeast(0) / 2f
            var particleIndex = 0

            for (py in 0 until buffer.height) {
                val row = py * buffer.width
                for (px in 0 until buffer.width) {
                    val color = visibleDustColor(buffer.pixels[row + px], backgroundColor) ?: continue
                    originX[particleIndex] = px
                    originY[particleIndex] = py
                    x[particleIndex] = px.toFloat()
                    y[particleIndex] = py.toFloat()
                    cellX[particleIndex] = px
                    cellY[particleIndex] = py
                    colors[particleIndex] = color

                    val dx = px - centerX
                    val dy = py - centerY
                    val distance = max(1f, sqrt(dx * dx + dy * dy))
                    val burst = initialBurstBase + random.nextFloat() * initialBurstRange
                    scatterX[particleIndex] = (dx / distance) * burst +
                        random.nextFloatIn(-initialJitter, initialJitter)
                    scatterY[particleIndex] = (dy / distance) * burst +
                        random.nextFloatIn(-initialJitter, initialJitter)
                    particleIndex += 1
                }
            }

            return PixelDustParticleField(
                width = buffer.width,
                height = buffer.height,
                originX = originX,
                originY = originY,
                x = x,
                y = y,
                cellX = cellX,
                cellY = cellY,
                scatterX = scatterX,
                scatterY = scatterY,
                colors = colors,
            )
        }

        private const val minSandSubsteps: Int = 1
        private const val maxSandSubsteps: Int = 5
        private const val forcePerSubstep: Float = 18f
        private const val minMovementProjection: Float = 0.15f
        private const val minMovementForceSquared: Float = 0.0625f
        private const val staticFrictionForceSquared: Float = 5.76f
        private const val settledScatterForceSquared: Float = 0.64f
        private const val gravitySmoothingTauSeconds: Float = 0.14f
        private const val pureSideMoveForceRatio: Float = 0.55f
        private const val sideTieBias: Float = 0.001f
        private const val scatterDampingPerSecond: Float = 0.18f
        private const val blockedScatterDamping: Float = 0.35f
        private const val initialBurstBase: Float = 22f
        private const val initialBurstRange: Float = 42f
        private const val initialJitter: Float = 16f
        private const val emptyCell: Int = -1
        private const val sqrtTwo: Float = 1.4142135f
        private val candidateDx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        private val candidateDy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
        private val candidateDistance = floatArrayOf(sqrtTwo, 1f, sqrtTwo, 1f, 1f, sqrtTwo, 1f, sqrtTwo)

        private fun visibleDustColor(color: Int, backgroundColor: Int): Int? {
            if (((color ushr 24) and 0xFF) == 0) return null
            val opaqueBackground = opaqueRgb(backgroundColor)
            val flattened = opaqueRgb(PixelBuffer.blendSrcOver(src = color, dst = opaqueBackground))
            return if (sameRgb(flattened, opaqueBackground)) null else flattened
        }

        private fun opaqueRgb(color: Int): Int {
            return opaqueAlphaMask or (color and rgbMask)
        }

        private fun sameRgb(first: Int, second: Int): Boolean {
            return (first and rgbMask) == (second and rgbMask)
        }

        private const val rgbMask: Int = 0x00FFFFFF
        private const val opaqueAlphaMask: Int = -0x1000000
    }
}

internal object PixelDustMotionMapper {
    fun toScreenAcceleration(snapshot: DeviceMotionSnapshot): ScreenVector {
        return ScreenVector(
            x = snapshot.screenGravityX * gravityScale,
            y = snapshot.screenGravityY * gravityScale,
        )
    }

    private const val gravityScale: Float = 4.0f
}

internal data class ScreenVector(
    val x: Float,
    val y: Float,
)

private data class PixelDustCell(
    val x: Int,
    val y: Int,
)

internal fun PixelDustEffectLayer(
    field: PixelDustParticleField,
    onTapToRestore: () -> Unit,
    key: Any? = null,
): Widget = GestureDetector(
    child = PixelDustEffectRenderWidget(field = field, key = key),
    onTap = onTapToRestore,
    onSwipeStart = {},
    onSwipeUpdate = { _ -> },
    onSwipeEnd = { _ -> },
    onSwipeLeft = {},
    onSwipeRight = {},
    key = key?.let { "$it-gesture" },
)

private class PixelDustEffectRenderWidget(
    private val field: PixelDustParticleField,
    override val key: Any?,
) : PixelLeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderPixelDustEffect(field)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderPixelDustEffect).update(field)
    }
}

private class RenderPixelDustEffect(
    private var field: PixelDustParticleField,
) : PixelRenderBox() {
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        field.drawTo(context.buffer, offsetX, offsetY)
    }

    fun update(next: PixelDustParticleField) {
        field = next
        markNeedsPaint()
    }
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
    val distance = (index + 1) / 2
    return if (index % 2 == 1) -distance else distance
}

private fun forceMagnitudeSquared(x: Float, y: Float): Float {
    return x * x + y * y
}

private fun directionFrom(value: Float): Int {
    return when {
        value > directionThreshold -> 1
        value < -directionThreshold -> -1
        else -> 0
    }
}

private const val directionThreshold: Float = 0.05f
