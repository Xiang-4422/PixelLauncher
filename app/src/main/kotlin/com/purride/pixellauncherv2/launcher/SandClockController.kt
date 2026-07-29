package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixellauncherv2.model.DeviceMotionSnapshot
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
import kotlin.random.Random

/**
 * 沙钟编排：驱动 [SandClockModel] 相位机，复用 PixelMatter 的沙粒物理与捕获管线。
 *
 * - DISPLAY：粒子静止在数字原位，**ticker 停止**——待机页首要省电，静止不烧帧。
 * - COLLAPSE：SAND 物理接管，数字塌成沙堆；真实姿态数据可用时沙随倾斜滑动，
 *   否则用合成的"竖屏向下"重力（IDLE 不常开传感器，省电优先）。
 * - REFORM：以最新时间重建种子，粒子散布在场地上方后插值落位成形。
 *
 * 与 [PixelMatterController] 同构但独立：那边是"整屏内容临时变沙"的一次性特效，
 * 这边是常驻的时钟呈现，生命周期与相位语义都不同，塞进同一个控制器只会互相绊脚。
 */
internal class SandClockController(
    vsync: PixelTickerProvider,
    private val onFrame: () -> Unit,
    random: Random = Random.Default,
) {
    private val randomSource = random
    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        if (shouldDispatchTick(elapsedNanos)) {
            onTick(elapsedNanos)
        }
    }

    var simulation: PixelMatterSimulation? = null
        private set

    /**
     * 渲染层的重绘钩子（渲染对象在 paint 时懒注册自己的 markNeedsPaint）。
     *
     * 引擎的 paint 是保留式的：没有被标脏的渲染对象不会重画。仿真自驱动的动画帧
     * 不经过 widget diff，若只 postInvalidate 而不标脏，粒子在物理里动、画面却
     * 停在上一帧——真机上表现为坍塌/成形全程不可见，数字只在整分状态更新时跳变。
     */
    var repaintHook: (() -> Unit)? = null

    private var phase = SandClockModel.Phase.DISPLAY
    private var phaseElapsedMs = 0L
    private var lastElapsedNanos = -1L
    private var lastDispatchedElapsedNanos = -1L

    /** 当前粒子摆出的时间文本。 */
    private var displayedText = ""

    /** 最近一次 sync 收到的时间文本；坍塌结束后用它做新种子，迟到的分钟不丢。 */
    private var latestText = ""

    /** 最近一次真实姿态；从未收到时保持 null，坍塌用合成重力。 */
    private var lastMotion: DeviceMotionSnapshot? = null

    private var seedBuilder: ((String) -> PixelBuffer?)? = null

    fun isVisible(): Boolean = simulation != null

    /** 姿态数据顺带喂入（PixelMatter 传感器开启期间免费获得倾斜跟随）。 */
    fun updateMotion(snapshot: DeviceMotionSnapshot) {
        lastMotion = snapshot
    }

    /**
     * 宿主状态同步：进入/驻留待机页时调用。
     * 首次进入直接落沙成形；此后 DISPLAY 相位里时间变化触发坍塌重组。
     */
    fun sync(timeText: String, seedBuilder: (String) -> PixelBuffer?) {
        this.seedBuilder = seedBuilder
        latestText = SandClockModel.clockText(timeText)
        if (simulation == null) {
            startReform()
            return
        }
        if (phase == SandClockModel.Phase.DISPLAY && latestText != displayedText) {
            beginCollapse()
        }
    }

    /** 手动触发一次坍塌重组（点按彩蛋）；动画进行中忽略。 */
    fun requestCollapse() {
        if (simulation != null && phase == SandClockModel.Phase.DISPLAY) {
            beginCollapse()
        }
    }

    /** 离开待机页：停帧并释放粒子。 */
    fun clear() {
        ticker.stop()
        simulation = null
        phase = SandClockModel.Phase.DISPLAY
        phaseElapsedMs = 0L
        lastElapsedNanos = -1L
        lastDispatchedElapsedNanos = -1L
        displayedText = ""
        seedBuilder = null
    }

    fun dispose() {
        clear()
        ticker.dispose()
    }

    private fun beginCollapse() {
        phase = SandClockModel.Phase.COLLAPSE
        phaseElapsedMs = 0L
        lastElapsedNanos = -1L
        ticker.start()
        requestRepaint()
    }

    /** 以 [latestText] 重建种子并进入落沙成形；种子不可用时保持现状。 */
    private fun startReform(): Boolean {
        val buffer = seedBuilder?.invoke(latestText) ?: return false
        val seed = PixelMatterCapture.capture(buffer) ?: return false
        val next = PixelMatterSimulationFactory.create(
            mode = PixelMatterEffectMode.SAND,
            seed = seed,
            snapshot = collapseMotion(),
            random = randomSource,
        )
        next.scatterAboveField(randomSource)
        next.beginRestore()
        simulation = next
        displayedText = latestText
        phase = SandClockModel.Phase.REFORM
        phaseElapsedMs = 0L
        lastElapsedNanos = -1L
        ticker.start()
        requestRepaint()
        return true
    }

    private fun onTick(elapsedNanos: Long) {
        val target = simulation ?: run {
            ticker.stop()
            return
        }
        val deltaMs = when {
            lastElapsedNanos < 0L -> FRAME_DELAY_MS
            elapsedNanos <= lastElapsedNanos -> FRAME_DELAY_MS
            else -> ((elapsedNanos - lastElapsedNanos) / 1_000_000L).coerceIn(1L, 80L)
        }
        lastElapsedNanos = elapsedNanos
        phaseElapsedMs += deltaMs

        when (phase) {
            SandClockModel.Phase.DISPLAY -> {
                ticker.stop()
                return
            }
            SandClockModel.Phase.COLLAPSE -> {
                target.step(deltaMs / 1_000f, collapseMotion(), null)
                val next = SandClockModel.nextPhase(phase, phaseElapsedMs, minuteChanged = false)
                if (next == SandClockModel.Phase.REFORM && !startReform()) {
                    // 种子暂不可用：停在沙堆态，等下一次 sync 再试。
                    phase = SandClockModel.Phase.DISPLAY
                    displayedText = ""
                    ticker.stop()
                    return
                }
            }
            SandClockModel.Phase.REFORM -> {
                target.applyRestore(SandClockModel.reformProgress(phaseElapsedMs))
                val next = SandClockModel.nextPhase(phase, phaseElapsedMs, minuteChanged = false)
                if (next == SandClockModel.Phase.DISPLAY) {
                    // 对齐原位并重建格子占用，下一次坍塌的物理从干净状态出发。
                    target.forceRestoreToOrigin()
                    phase = SandClockModel.Phase.DISPLAY
                    // 成形期间时间又变了：立刻再塌一次，追上真实时间。
                    if (latestText != displayedText) {
                        beginCollapse()
                        return
                    }
                    ticker.stop()
                    requestRepaint()
                    return
                }
            }
        }
        requestRepaint()
    }

    /** 标脏渲染对象并请求一帧：两者缺一动画都不可见。 */
    private fun requestRepaint() {
        repaintHook?.invoke()
        onFrame()
    }

    /**
     * 坍塌重力。真实姿态只有在**屏面内分量足够**时才可用：手机平放在桌上时
     * 重力几乎全在 Z 轴，屏面内接近零向量，沙塌不下去——数字纹丝不动，坍塌
     * 相位空转后原样成形。此时回落合成的"竖屏向下"；立着或倾斜时用真实姿态，
     * 沙随倾斜方向滑动。
     */
    private fun collapseMotion(): DeviceMotionSnapshot {
        val motion = lastMotion
        if (motion != null) {
            val planar = kotlin.math.hypot(motion.screenGravityX, motion.screenGravityY)
            if (planar >= MIN_PLANAR_GRAVITY) {
                return motion
            }
        }
        return DeviceMotionSnapshot(screenGravityY = SYNTHETIC_GRAVITY)
    }

    private fun shouldDispatchTick(elapsedNanos: Long): Boolean {
        val lastDispatch = lastDispatchedElapsedNanos
        if (lastDispatch < 0L || elapsedNanos - lastDispatch >= FRAME_INTERVAL_NANOS) {
            lastDispatchedElapsedNanos = elapsedNanos
            return true
        }
        return false
    }

    private companion object {
        const val FRAME_INTERVAL_NANOS = 33_333_333L
        const val FRAME_DELAY_MS = 33L

        /** 合成重力，量级与真实重力一致（m/s²）。 */
        const val SYNTHETIC_GRAVITY = 9.81f

        /** 屏面内重力低于此值（m/s²）视为"平放"，改用合成重力。 */
        const val MIN_PLANAR_GRAVITY = 2f
    }
}

/**
 * 沙钟渲染层：画当前仿真的粒子，点按触发一次坍塌重组。
 *
 * 不复用 [PixelMatterEffectLayer]——它没有每帧标脏的通道（matter 特效作为
 * buildRoot 顶层有自己的重建路径），嵌在 IDLE 页 Stack 里必须由渲染对象向
 * 控制器注册 markNeedsPaint 钩子，仿真帧才能上屏。
 */
internal fun SandClockLayer(
    controller: SandClockController,
    key: Any? = null,
): Widget = GestureDetector(
    child = SandClockRenderWidget(controller = controller, key = key),
    onTap = { controller.requestCollapse() },
    key = key?.let { "$it-gesture" },
)

private class SandClockRenderWidget(
    private val controller: SandClockController,
    override val key: Any?,
) : PixelLeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderSandClock(controller)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderSandClock).update(controller)
    }
}

private class RenderSandClock(
    private var controller: SandClockController,
) : PixelRenderBox() {
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        // 懒注册：本对象被换掉后钩子会在下一次 paint 时被新对象覆盖；
        // 指向已卸载对象的 markNeedsPaint 是空操作（owner 为 null），无需显式注销。
        controller.repaintHook = { markNeedsPaint() }
        controller.simulation?.drawTo(context.buffer, offsetX, offsetY)
    }

    fun update(next: SandClockController) {
        controller = next
        markNeedsPaint()
    }
}
