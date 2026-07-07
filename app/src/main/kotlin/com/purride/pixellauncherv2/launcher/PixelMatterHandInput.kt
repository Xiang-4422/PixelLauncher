package com.purride.pixellauncherv2.launcher

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal enum class PixelMatterHandGesture(
    val label: String,
) {
    NONE("NO HAND"),
    PINCH("PINCH"),
    OPEN_PALM("OPEN"),
    CLOSED_FIST("FIST"),
    POINTING_UP("POINT"),
    VICTORY("VICTORY"),
    THUMB_UP("THUMB UP"),
    THUMB_DOWN("THUMB DOWN"),
    I_LOVE_YOU("LOVE"),
}

internal data class PixelMatterHandPoint(
    val x: Float,
    val y: Float,
)

internal data class PixelMatterHandSnapshot(
    val timestampMs: Long,
    val indexTip: PixelMatterHandPoint,
    val thumbTip: PixelMatterHandPoint,
    val middleTip: PixelMatterHandPoint = indexTip,
    val ringTip: PixelMatterHandPoint = indexTip,
    val pinkyTip: PixelMatterHandPoint = indexTip,
    val palmCenter: PixelMatterHandPoint,
    val pinchCenter: PixelMatterHandPoint,
    val isPinching: Boolean,
    val palmVelocity: PixelMatterHandPoint,
    val confidence: Float,
    val gesture: PixelMatterHandGesture = if (isPinching) PixelMatterHandGesture.PINCH else PixelMatterHandGesture.OPEN_PALM,
    val openAmount: Float = 1f,
    val fingerCount: Int = 5,
    val rotationRadians: Float = 0f,
) {
    fun isFresh(nowMs: Long, timeoutMs: Long = staleTimeoutMs): Boolean {
        return nowMs - timestampMs <= timeoutMs
    }

    companion object {
        const val staleTimeoutMs: Long = 250L
    }
}

internal object PixelMatterHandInputMapper {
    fun fromNormalizedLandmarks(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
        timestampMs: Long,
        logicalWidth: Int,
        logicalHeight: Int,
        confidence: Float,
        previous: PixelMatterHandSnapshot? = null,
        mirrorX: Boolean = true,
    ): PixelMatterHandSnapshot? {
        if (normalizedX.size < requiredLandmarkCount || normalizedY.size < requiredLandmarkCount) {
            return null
        }
        if (confidence < minConfidence) return null

        val maxX = (logicalWidth - 1).coerceAtLeast(0).toFloat()
        val maxY = (logicalHeight - 1).coerceAtLeast(0).toFloat()
        fun point(index: Int): PixelMatterHandPoint {
            val sourceX = normalizedX[index].coerceIn(0f, 1f)
            val mappedX = if (mirrorX) 1f - sourceX else sourceX
            val mappedY = normalizedY[index].coerceIn(0f, 1f)
            return PixelMatterHandPoint(
                x = mappedX * maxX,
                y = mappedY * maxY,
            )
        }

        val rawIndex = point(indexTipLandmark)
        val rawThumb = point(thumbTipLandmark)
        val rawMiddle = point(middleTipLandmark)
        val rawRing = point(ringTipLandmark)
        val rawPinky = point(pinkyTipLandmark)
        val rawPalm = averagePalm(::point)
        val rawPinch = midpoint(rawIndex, rawThumb)
        val normalizedPinchDistance = distance(
            normalizedX[indexTipLandmark],
            normalizedY[indexTipLandmark],
            normalizedX[thumbTipLandmark],
            normalizedY[thumbTipLandmark],
        )

        val previousFresh = previous?.takeIf { timestampMs - it.timestampMs in 1L..PixelMatterHandSnapshot.staleTimeoutMs }
        val indexTip = smooth(previousFresh?.indexTip, rawIndex)
        val thumbTip = smooth(previousFresh?.thumbTip, rawThumb)
        val middleTip = smooth(previousFresh?.middleTip, rawMiddle)
        val ringTip = smooth(previousFresh?.ringTip, rawRing)
        val pinkyTip = smooth(previousFresh?.pinkyTip, rawPinky)
        val palmCenter = smooth(previousFresh?.palmCenter, rawPalm)
        val pinchCenter = smooth(previousFresh?.pinchCenter, rawPinch)
        val classification = classifyGesture(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            pinchDistance = normalizedPinchDistance,
        )
        val palmVelocity = previousFresh?.let { old ->
            val deltaSeconds = ((timestampMs - old.timestampMs).coerceAtLeast(1L)).toFloat() / 1_000f
            PixelMatterHandPoint(
                x = ((palmCenter.x - old.palmCenter.x) / deltaSeconds).coerceIn(-maxPalmVelocity, maxPalmVelocity),
                y = ((palmCenter.y - old.palmCenter.y) / deltaSeconds).coerceIn(-maxPalmVelocity, maxPalmVelocity),
            )
        } ?: PixelMatterHandPoint(0f, 0f)

        return PixelMatterHandSnapshot(
            timestampMs = timestampMs,
            indexTip = indexTip,
            thumbTip = thumbTip,
            middleTip = middleTip,
            ringTip = ringTip,
            pinkyTip = pinkyTip,
            palmCenter = palmCenter,
            pinchCenter = pinchCenter,
            isPinching = classification.gesture == PixelMatterHandGesture.PINCH,
            palmVelocity = palmVelocity,
            confidence = confidence.coerceIn(0f, 1f),
            gesture = classification.gesture,
            openAmount = classification.openAmount,
            fingerCount = classification.fingerCount,
            rotationRadians = classification.rotationRadians,
        )
    }

    private fun classifyGesture(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
        pinchDistance: Float,
    ): HandGestureClassification {
        if (pinchDistance <= pinchDistanceThreshold) {
            return HandGestureClassification(
                gesture = PixelMatterHandGesture.PINCH,
                openAmount = 0.15f,
                fingerCount = 2,
                rotationRadians = palmRotation(normalizedX, normalizedY),
            )
        }
        val thumbExtended = isThumbExtended(normalizedX, normalizedY)
        val indexExtended = isFingerExtended(normalizedX, normalizedY, indexTipLandmark, indexPipLandmark)
        val middleExtended = isFingerExtended(normalizedX, normalizedY, middleTipLandmark, middlePipLandmark)
        val ringExtended = isFingerExtended(normalizedX, normalizedY, ringTipLandmark, ringPipLandmark)
        val pinkyExtended = isFingerExtended(normalizedX, normalizedY, pinkyTipLandmark, pinkyPipLandmark)
        val fingerCount = listOf(
            thumbExtended,
            indexExtended,
            middleExtended,
            ringExtended,
            pinkyExtended,
        ).count { it }
        val openAmount = (fingerCount.toFloat() / 5f).coerceIn(0f, 1f)
        val gesture = when {
            thumbExtended && indexExtended && pinkyExtended && !middleExtended && !ringExtended ->
                PixelMatterHandGesture.I_LOVE_YOU
            thumbExtended && !indexExtended && !middleExtended && !ringExtended && !pinkyExtended ->
                thumbDirectionGesture(normalizedX, normalizedY)
            indexExtended && middleExtended && !ringExtended && !pinkyExtended ->
                PixelMatterHandGesture.VICTORY
            indexExtended && !middleExtended && !ringExtended && !pinkyExtended ->
                PixelMatterHandGesture.POINTING_UP
            fingerCount <= 1 ->
                PixelMatterHandGesture.CLOSED_FIST
            indexExtended && middleExtended && ringExtended && pinkyExtended ->
                PixelMatterHandGesture.OPEN_PALM
            else ->
                PixelMatterHandGesture.OPEN_PALM
        }
        return HandGestureClassification(
            gesture = gesture,
            openAmount = openAmount,
            fingerCount = fingerCount,
            rotationRadians = palmRotation(normalizedX, normalizedY),
        )
    }

    private fun isFingerExtended(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
        tip: Int,
        pip: Int,
    ): Boolean {
        val palm = normalizedPalmCenter(normalizedX, normalizedY)
        val palmScale = palmScale(normalizedX, normalizedY)
        val tipDistance = distance(normalizedX[tip], normalizedY[tip], palm.x, palm.y)
        val pipDistance = distance(normalizedX[pip], normalizedY[pip], palm.x, palm.y)
        return tipDistance > pipDistance + palmScale * fingerExtensionMargin
    }

    private fun isThumbExtended(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
    ): Boolean {
        val palm = normalizedPalmCenter(normalizedX, normalizedY)
        val palmScale = palmScale(normalizedX, normalizedY)
        val tipDistance = distance(normalizedX[thumbTipLandmark], normalizedY[thumbTipLandmark], palm.x, palm.y)
        val ipDistance = distance(normalizedX[thumbIpLandmark], normalizedY[thumbIpLandmark], palm.x, palm.y)
        return tipDistance > ipDistance + palmScale * thumbExtensionMargin
    }

    private fun thumbDirectionGesture(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
    ): PixelMatterHandGesture {
        val palm = normalizedPalmCenter(normalizedX, normalizedY)
        val deltaY = normalizedY[thumbTipLandmark] - palm.y
        return if (deltaY < 0f) {
            PixelMatterHandGesture.THUMB_UP
        } else {
            PixelMatterHandGesture.THUMB_DOWN
        }
    }

    private fun palmScale(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
    ): Float {
        return distance(
            normalizedX[wristLandmark],
            normalizedY[wristLandmark],
            normalizedX[middleMcpLandmark],
            normalizedY[middleMcpLandmark],
        ).coerceAtLeast(minPalmScale)
    }

    private fun palmRotation(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
    ): Float {
        val dx = normalizedX[pinkyMcpLandmark] - normalizedX[indexMcpLandmark]
        val dy = normalizedY[pinkyMcpLandmark] - normalizedY[indexMcpLandmark]
        return kotlin.math.atan2(dy, dx)
    }

    private fun normalizedPalmCenter(
        normalizedX: FloatArray,
        normalizedY: FloatArray,
    ): NormalizedPoint {
        var sumX = 0f
        var sumY = 0f
        for (index in palmLandmarks) {
            sumX += normalizedX[index]
            sumY += normalizedY[index]
        }
        return NormalizedPoint(
            x = sumX / palmLandmarks.size.toFloat(),
            y = sumY / palmLandmarks.size.toFloat(),
        )
    }

    private fun averagePalm(pointAt: (Int) -> PixelMatterHandPoint): PixelMatterHandPoint {
        var sumX = 0f
        var sumY = 0f
        for (index in palmLandmarks) {
            val point = pointAt(index)
            sumX += point.x
            sumY += point.y
        }
        return PixelMatterHandPoint(
            x = sumX / palmLandmarks.size.toFloat(),
            y = sumY / palmLandmarks.size.toFloat(),
        )
    }

    private fun smooth(
        previous: PixelMatterHandPoint?,
        current: PixelMatterHandPoint,
    ): PixelMatterHandPoint {
        if (previous == null) return current
        return PixelMatterHandPoint(
            x = previous.x + (current.x - previous.x) * smoothingAlpha,
            y = previous.y + (current.y - previous.y) * smoothingAlpha,
        )
    }

    private fun midpoint(
        first: PixelMatterHandPoint,
        second: PixelMatterHandPoint,
    ): PixelMatterHandPoint = PixelMatterHandPoint(
        x = (first.x + second.x) * 0.5f,
        y = (first.y + second.y) * 0.5f,
    )

    private fun distance(
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
    ): Float {
        val dx = firstX - secondX
        val dy = firstY - secondY
        return sqrt(dx * dx + dy * dy)
    }

    private const val requiredLandmarkCount: Int = 21
    private const val wristLandmark: Int = 0
    private const val thumbIpLandmark: Int = 3
    private const val thumbTipLandmark: Int = 4
    private const val indexMcpLandmark: Int = 5
    private const val indexPipLandmark: Int = 6
    private const val indexTipLandmark: Int = 8
    private const val middleMcpLandmark: Int = 9
    private const val middlePipLandmark: Int = 10
    private const val middleTipLandmark: Int = 12
    private const val ringMcpLandmark: Int = 13
    private const val ringPipLandmark: Int = 14
    private const val ringTipLandmark: Int = 16
    private const val pinkyMcpLandmark: Int = 17
    private const val pinkyPipLandmark: Int = 18
    private const val pinkyTipLandmark: Int = 20
    private val palmLandmarks = intArrayOf(
        wristLandmark,
        indexMcpLandmark,
        middleMcpLandmark,
        ringMcpLandmark,
        pinkyMcpLandmark,
    )
    private const val minConfidence: Float = 0.35f
    private const val smoothingAlpha: Float = 0.45f
    private const val pinchDistanceThreshold: Float = 0.075f
    private const val fingerExtensionMargin: Float = 0.10f
    private const val thumbExtensionMargin: Float = 0.08f
    private const val minPalmScale: Float = 0.06f
    private const val maxPalmVelocity: Float = 180f
}

private data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

private data class HandGestureClassification(
    val gesture: PixelMatterHandGesture,
    val openAmount: Float,
    val fingerCount: Int,
    val rotationRadians: Float,
)

internal object PixelMatterHandForces {
    fun forceAt(
        x: Float,
        y: Float,
        input: PixelMatterHandSnapshot?,
        radius: Float,
        repelStrength: Float,
        attractStrength: Float,
        windStrength: Float,
    ): MatterHandForce {
        val hand = input ?: return MatterHandForce.Zero
        if (radius <= 0f) return MatterHandForce.Zero
        val center = if (hand.isPinching) hand.pinchCenter else hand.indexTip
        var dx = x - center.x
        var dy = y - center.y
        val distanceSquared = dx * dx + dy * dy
        val radiusSquared = radius * radius
        if (distanceSquared > radiusSquared) return MatterHandForce.Zero
        val distance = max(0.001f, sqrt(distanceSquared))
        val falloff = 1f - min(1f, distance / radius)
        val strength = falloff * falloff * hand.confidence.coerceIn(0f, 1f)
        dx /= distance
        dy /= distance
        val directionalStrength = if (hand.isPinching) -attractStrength else repelStrength
        return MatterHandForce(
            x = dx * directionalStrength * strength + hand.palmVelocity.x * windStrength * strength,
            y = dy * directionalStrength * strength + hand.palmVelocity.y * windStrength * strength,
            strength = strength,
        )
    }
}

internal data class MatterHandForce(
    val x: Float,
    val y: Float,
    val strength: Float,
) {
    companion object {
        val Zero = MatterHandForce(0f, 0f, 0f)
    }
}
