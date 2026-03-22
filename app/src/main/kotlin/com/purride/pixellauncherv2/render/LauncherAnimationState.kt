package com.purride.pixellauncherv2.render

data class LauncherAnimationState(
    val bootSequence: BootSequenceAnimation? = null,
    val drawerReveal: DrawerRevealAnimation? = null,
    val launchShutter: LaunchShutterAnimation? = null,
    val headerChargeTick: Int = 0,
) {
    val hasActiveAnimations: Boolean
        get() = bootSequence != null || drawerReveal != null || launchShutter != null

    fun startBootSequence(): LauncherAnimationState {
        return copy(
            bootSequence = BootSequenceAnimation(),
            drawerReveal = null,
            launchShutter = null,
        )
    }

    fun startDrawerReveal(): LauncherAnimationState {
        return copy(drawerReveal = DrawerRevealAnimation())
    }

    fun startLaunchShutter(): LauncherAnimationState {
        return copy(launchShutter = LaunchShutterAnimation())
    }

    fun nextFrame(): LauncherAnimationState {
        return copy(
            bootSequence = bootSequence?.nextFrame()?.takeUnless { it.isComplete },
            drawerReveal = drawerReveal?.nextFrame()?.takeUnless { it.isComplete },
            launchShutter = launchShutter?.nextFrame()?.takeUnless { it.isComplete },
            headerChargeTick = headerChargeTick + 1,
        )
    }

    companion object {
        const val frameDelayMs: Long = 60L
        val launchShutterDurationMs: Long =
            frameDelayMs * (LaunchShutterAnimation.totalFrames + LaunchShutterAnimation.holdFrames)
    }
}

enum class BootSequenceStage {
    SCAN,
    CHECK,
    READY,
}

data class BootSequenceAnimation(
    val frameIndex: Int = 0,
) {
    val isComplete: Boolean
        get() = frameIndex >= totalFrames - 1

    val stage: BootSequenceStage
        get() = when {
            frameIndex < scanFrames -> BootSequenceStage.SCAN
            frameIndex < scanFrames + checkFrames -> BootSequenceStage.CHECK
            else -> BootSequenceStage.READY
        }

    val revealProgress: Float
        get() = ((frameIndex + 1).toFloat() / scanFrames.toFloat()).coerceIn(0f, 1f)

    val checkLinesVisible: Int
        get() = ((frameIndex - scanFrames) + 1).coerceIn(0, checkFrames)

    val readyVisible: Boolean
        get() = frameIndex % 2 == 0

    fun nextFrame(): BootSequenceAnimation = copy(frameIndex = frameIndex + 1)

    companion object {
        const val scanFrames: Int = 8
        const val checkFrames: Int = 6
        const val readyFrames: Int = 4
        const val totalFrames: Int = scanFrames + checkFrames + readyFrames
    }
}

data class DrawerRevealAnimation(
    val frameIndex: Int = 0,
) {
    val isComplete: Boolean
        get() = frameIndex >= totalFrames - 1

    val revealProgress: Float
        get() = ((frameIndex + 1).toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)

    fun nextFrame(): DrawerRevealAnimation = copy(frameIndex = frameIndex + 1)

    companion object {
        const val totalFrames: Int = 5
    }
}

data class LaunchShutterAnimation(
    val frameIndex: Int = 0,
) {
    val isComplete: Boolean
        get() = frameIndex >= totalFrames + holdFrames - 1

    val closeProgress: Float
        get() = ((minOf(frameIndex + 1, totalFrames)).toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)

    fun nextFrame(): LaunchShutterAnimation = copy(frameIndex = frameIndex + 1)

    companion object {
        const val totalFrames: Int = 3
        const val holdFrames: Int = 1
    }
}
