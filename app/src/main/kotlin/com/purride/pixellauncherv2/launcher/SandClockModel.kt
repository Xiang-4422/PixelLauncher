package com.purride.pixellauncherv2.launcher

/**
 * 沙钟的相位机：待机页时间数字由沙粒堆成，分钟变化时旧数字坍塌、新数字落沙成形。
 *
 * ```
 * DISPLAY ──分钟变化──▶ COLLAPSE ──计时到──▶ REFORM ──计时到──▶ DISPLAY
 *    ▲                （沙粒物理接管，        （新种子散布在场地上方，
 *    └──────────────── 数字塌成沙堆）          插值落下堆成新数字）
 * ```
 *
 * 纯逻辑、无 Android 依赖；帧驱动与仿真编排由控制器承担。
 */
object SandClockModel {

    /** 沙钟相位。 */
    enum class Phase {
        /** 静止显示：粒子停在数字原位。 */
        DISPLAY,

        /** 坍塌：沙粒物理接管，数字塌成沙堆。 */
        COLLAPSE,

        /** 成形：新数字的粒子从场地上方落到各自原位。 */
        REFORM,
    }

    /** 坍塌时长：太短看不清塌落过程，太长会侵占下一分钟。 */
    const val COLLAPSE_DURATION_MS = 2_200L

    /** 成形时长：与引擎既有 restore 节奏（1.6s）同量级，稍快让数字尽早可读。 */
    const val REFORM_DURATION_MS = 1_400L

    /**
     * 相位推进决策。[minuteChanged] 为本帧时间文本是否发生变化；
     * 只有 DISPLAY 相位响应分钟变化——坍塌/成形进行中不打断（动画完成时
     * 控制器会用最新时间做种子，迟到的分钟不会丢）。
     */
    fun nextPhase(phase: Phase, elapsedInPhaseMs: Long, minuteChanged: Boolean): Phase = when (phase) {
        Phase.DISPLAY -> if (minuteChanged) Phase.COLLAPSE else Phase.DISPLAY
        Phase.COLLAPSE -> if (elapsedInPhaseMs >= COLLAPSE_DURATION_MS) Phase.REFORM else Phase.COLLAPSE
        Phase.REFORM -> if (elapsedInPhaseMs >= REFORM_DURATION_MS) Phase.DISPLAY else Phase.REFORM
    }

    /** 成形插值进度 [0,1]，交给仿真的 applyRestore。 */
    fun reformProgress(elapsedInPhaseMs: Long): Float =
        (elapsedInPhaseMs.toFloat() / REFORM_DURATION_MS).coerceIn(0f, 1f)

    /** 展示文本：只接受非空时间，异常输入回落占位，避免种子渲染空串。 */
    fun clockText(timeText: String): String = timeText.trim().ifEmpty { "--:--" }
}
