package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.Alignment
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.testRouteRequest
import com.purride.pixelui.Polygon
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Sprite
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.widgets.animated.AnimatedSprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

/**
 * 对固定 JVM 渲染场景执行轻量性能门禁，并输出可由脚本解析的属性报告。
 *
 * 这些阈值只用于阻止数量级回退，不能替代真实设备上的帧时间基线。
 */
class EnginePerformanceSmokeTest {
    /** 测量全部固定场景、写入完整报告，并在任一场景超过阈值时让测试失败。 */
    @Test
    fun renderPerformanceSmokeEnforcesThresholdsAndWritesReport() {
        // 环境变量缩放仅供门禁负向验证和受控的机器分级使用。
        val thresholdScale = readThresholdScale()
        // 报告必须在断言前完整生成，这样失败任务也能留下诊断证据。
        val samples = scenes.map { scene -> measureScene(scene, thresholdScale) }
        // 脚本传入的唯一标识用于证明报告来自当前运行而非缓存残留。
        val runId = readRunId()
        // 报告先落盘，再执行阈值断言。
        val report = writeReport(samples, thresholdScale, runId)

        assertEquals(EXPECTED_SCENE_COUNT, samples.size)
        samples.forEach { sample ->
            assertEquals(SAMPLE_BATCH_COUNT * SAMPLE_FRAMES, sample.frames)
            assertEquals(sample.width, sample.lastWidth)
            assertEquals(sample.height, sample.lastHeight)
            assertTrue("${sample.name} total nanos should be positive", sample.totalNanos > 0L)
            assertTrue("${sample.name} avg nanos should be positive", sample.averageNanos > 0L)
            assertTrue(
                "${sample.name} average ${sample.averageNanos}ns exceeded " +
                    "${sample.maxAverageNanos}ns (base=${sample.baseMaxAverageNanos}ns, scale=$thresholdScale)",
                sample.passed,
            )
        }

        assertTrue(report.exists())
        assertTrue(report.readText().contains("formatVersion=$REPORT_FORMAT_VERSION"))
    }

    /** 测量单个场景，并把基础阈值按本次运行的缩放系数换算为实际阈值。 */
    private fun measureScene(scene: PerfScene, thresholdScale: Double): PerfSample {
        // 每个场景使用独立 runtime，避免状态跨场景污染。
        val runtime = PixelUiRuntime()
        // 最后一帧宽度用于确认测量确实完成了布局和绘制。
        var lastWidth = 0
        // 最后一帧高度用于确认输出尺寸没有退化。
        var lastHeight = 0
        try {
            repeat(WARMUP_FRAMES) { frame ->
                scene.beforeFrame(frame)
                runtime.render(scene.build(), scene.width, scene.height)
            }
            // 多批次平均值用于取中位数，隔离单次 GC、JIT 或宿主调度尖峰。
            val batchTotalNanos = List(SAMPLE_BATCH_COUNT) { batch ->
                // 批次耗时覆盖场景推进、Widget 构建和 JVM 离屏渲染。
                val batchTotalNanos = measureNanoTime {
                    repeat(SAMPLE_FRAMES) { frame ->
                        // 全局帧编号让动画和受控状态在批次之间连续推进。
                        val measuredFrame = WARMUP_FRAMES + batch * SAMPLE_FRAMES + frame
                        scene.beforeFrame(measuredFrame)
                        // 本帧结果必须被读取，避免 JIT 把渲染工作当作无效结果消除。
                        val result = runtime.render(scene.build(), scene.width, scene.height)
                        lastWidth = result.buffer.width
                        lastHeight = result.buffer.height
                    }
                }
                batchTotalNanos
            }
            return PerfSample(
                name = scene.name,
                frames = SAMPLE_BATCH_COUNT * SAMPLE_FRAMES,
                width = scene.width,
                height = scene.height,
                lastWidth = lastWidth,
                lastHeight = lastHeight,
                batchTotalNanos = batchTotalNanos,
                baseMaxAverageNanos = scene.maxAverageNanos,
                maxAverageNanos = scaleThreshold(scene.maxAverageNanos, thresholdScale),
            )
        } finally {
            runtime.dispose()
        }
    }

    /**
     * 写入 Java-properties 风格的稳定报告；每一项均使用无空格的 `key=value` 字段。
     */
    private fun writeReport(samples: List<PerfSample>, thresholdScale: Double, runId: String): File {
        // 报告路径与 shell 门禁约定一致，供 CI 直接归档。
        val report = File("build/reports/perf/pixel-engine-render-smoke.txt")
        report.parentFile?.mkdirs()
        report.writeText(
            buildString {
                appendLine("formatVersion=$REPORT_FORMAT_VERSION")
                appendLine("runId=$runId")
                appendLine("thresholdScale=$thresholdScale")
                appendLine("warmupFrames=$WARMUP_FRAMES")
                appendLine("sampleFrames=$SAMPLE_FRAMES")
                appendLine("sampleBatches=$SAMPLE_BATCH_COUNT")
                appendLine("sceneCount=${samples.size}")
                appendLine("javaRuntimeVersion=${readSystemProperty("java.runtime.version")}")
                appendLine("javaVmName=${readSystemProperty("java.vm.name")}")
                appendLine("osName=${readSystemProperty("os.name")}")
                appendLine("osArch=${readSystemProperty("os.arch")}")
                samples.forEach { sample ->
                    // 场景名来自受控常量，可安全用作属性键的一部分。
                    val prefix = "scene.${sample.name}"
                    appendLine("$prefix.frames=${sample.frames}")
                    appendLine("$prefix.width=${sample.width}")
                    appendLine("$prefix.height=${sample.height}")
                    appendLine("$prefix.totalNanos=${sample.totalNanos}")
                    appendLine("$prefix.averageNanos=${sample.averageNanos}")
                    appendLine("$prefix.batchAverageNanos=${sample.batchAverageNanos.joinToString(",")}")
                    appendLine("$prefix.averageMillis=${formatMillis(sample.averageNanos)}")
                    appendLine("$prefix.baseMaxAverageNanos=${sample.baseMaxAverageNanos}")
                    appendLine("$prefix.maxAverageNanos=${sample.maxAverageNanos}")
                    appendLine("$prefix.pass=${sample.passed}")
                }
                appendLine("overallPass=${samples.all(PerfSample::passed)}")
            },
        )
        return report
    }

    /** 读取门禁阈值缩放系数；缺省为 1，非法或非正值会直接拒绝运行。 */
    private fun readThresholdScale(): Double {
        // 原始环境变量只在专用负向验证或受控基线环境中设置。
        val rawScale = System.getenv(THRESHOLD_SCALE_ENV)?.trim()
        if (rawScale.isNullOrEmpty()) return 1.0
        // 显式校验有限正数，避免 NaN/Infinity 让比较静默失真。
        val parsedScale = rawScale.toDoubleOrNull()
        require(parsedScale != null && parsedScale.isFinite() && parsedScale > 0.0) {
            "$THRESHOLD_SCALE_ENV must be a finite number greater than zero, but was '$rawScale'."
        }
        return parsedScale
    }

    /** 读取当前脚本运行标识；直接从 Gradle 运行测试时使用可识别的缺省值。 */
    private fun readRunId(): String {
        // 禁止换行和等号，确保运行标识不会破坏 properties 报告结构。
        val configuredRunId = System.getenv(RUN_ID_ENV)?.trim()
        if (configuredRunId.isNullOrEmpty()) return DIRECT_RUN_ID
        require(REPORT_VALUE_REGEX.matches(configuredRunId)) {
            "$RUN_ID_ENV contains unsupported characters."
        }
        return configuredRunId
    }

    /** 读取并净化 JVM/宿主系统属性，使环境证据不能破坏 properties 报告结构。 */
    private fun readSystemProperty(propertyName: String): String {
        // 缺失属性使用稳定占位符；换行和等号统一替换，保持一行一个字段。
        val propertyValue = System.getProperty(propertyName)?.trim().orEmpty().ifEmpty { "unknown" }
        return propertyValue.replace(REPORT_PROPERTY_UNSAFE_REGEX, "_")
    }

    /** 把基础纳秒阈值按正数系数缩放，极小系数仍保留 1ns 以支持确定性负向验证。 */
    private fun scaleThreshold(baseNanos: Long, thresholdScale: Double): Long {
        // roundToLong 让小数缩放行为固定，最小值保证阈值字段始终为正数。
        val scaledNanos = (baseNanos.toDouble() * thresholdScale).roundToLong()
        return scaledNanos.coerceAtLeast(1L)
    }

    /** 以固定小数点格式输出毫秒，便于人读且不受系统 Locale 影响。 */
    private fun formatMillis(nanos: Long): String {
        return String.format(Locale.US, "%.3f", nanos / NANOS_PER_MILLISECOND.toDouble())
    }

    /**
     * 单个固定性能场景的构造方式和未缩放阈值。
     *
     * @property name 报告中的稳定场景标识。
     * @property width 离屏逻辑宽度。
     * @property height 离屏逻辑高度。
     * @property maxAverageNanos 默认缩放系数下允许的最大平均帧耗时。
     * @property beforeFrame 每帧渲染前推进受控状态的回调。
     * @property build 创建本帧 Widget 树的回调。
     */
    private data class PerfScene(
        val name: String,
        val width: Int,
        val height: Int,
        val maxAverageNanos: Long,
        val beforeFrame: (Int) -> Unit = { },
        val build: () -> Widget,
    )

    /**
     * 单个场景的实测结果及本次运行采用的阈值。
     *
     * @property name 稳定场景标识。
     * @property frames 实际采样帧数。
     * @property width 期望输出宽度。
     * @property height 期望输出高度。
     * @property lastWidth 最后一帧实际输出宽度。
     * @property lastHeight 最后一帧实际输出高度。
     * @property batchTotalNanos 各独立采样批次全部帧的精确总耗时。
     * @property baseMaxAverageNanos 未缩放的宽松最大平均耗时。
     * @property maxAverageNanos 本次运行实际采用的最大平均耗时。
     */
    private data class PerfSample(
        val name: String,
        val frames: Int,
        val width: Int,
        val height: Int,
        val lastWidth: Int,
        val lastHeight: Int,
        val batchTotalNanos: List<Long>,
        val baseMaxAverageNanos: Long,
        val maxAverageNanos: Long,
    ) {
        /** 当前场景全部批次和采样帧的总耗时。 */
        val totalNanos: Long
            get() = batchTotalNanos.sum()

        /** 当前场景各独立采样批次的平均帧耗时。 */
        val batchAverageNanos: List<Long>
            get() = batchTotalNanos.map { batchNanos -> batchNanos / SAMPLE_FRAMES }

        /** 当前场景各批次平均帧耗时的中位数。 */
        val averageNanos: Long
            get() {
                // 固定奇数批次保证中位数总是一个真实批次值，无需插值或取整。
                val sortedAverages = batchAverageNanos.sorted()
                return sortedAverages[sortedAverages.size / 2]
            }

        /** 当前场景是否满足缩放后的性能阈值。 */
        val passed: Boolean
            get() = averageNanos <= maxAverageNanos
    }

    /** 六个 JVM smoke 场景及其用于捕获数量级回退的宽松平均耗时上限。 */
    private val scenes: List<PerfScene> = listOf(
        PerfScene(
            name = "list_scroll",
            width = 96,
            height = 64,
            maxAverageNanos = 50L * NANOS_PER_MILLISECOND,
        ) {
            // 列表控制器和状态固定起点，确保每帧工作量可重复。
            val controller = PixelListController()
            val state = controller.create(initialScrollOffsetPx = 24f)
            Scrollbar(
                state = state,
                width = 2,
                child = GridViewBuilder(
                    itemCount = 120,
                    itemBuilder = { index ->
                        Container(
                            child = Text("${index % 10}"),
                            borderColor = PixelColor.White,
                            alignment = Alignment.CENTER,
                        )
                    },
                    cellWidth = 12,
                    cellHeight = 7,
                    spacing = 1,
                    runSpacing = 1,
                    state = state,
                    controller = controller,
                ),
            )
        },
        PerfScene(
            name = "text_input",
            width = 96,
            height = 32,
            maxAverageNanos = 50L * NANOS_PER_MILLISECOND,
        ) {
            // 文本控制器提供与真实输入路径一致的编辑接口。
            val controller = TextEditingController()
            // 固定多行选择与 composing 区间，覆盖布局和选区绘制。
            val state = PixelTextFieldState(
                initialText = "ALPHA\nBRAVO CHARLIE\nDELTA",
                selectionStart = 6,
                selectionEnd = 19,
            )
            controller.updateComposition(state, compositionStart = 20, compositionEnd = 25)
            controller.focus(state)
            TextField(
                state = state,
                controller = controller,
                minLines = 3,
                maxLines = 3,
            )
        },
        run {
            // 手动 scheduler 保证动画时间线不依赖宿主墙钟。
            val scheduler = ManualFrameScheduler()
            // ticker provider 使用上述可重复时间源推进动画。
            val vsync = PixelTickerProvider(scheduler)
            PerfScene(
                name = "animation",
                width = 24,
                height = 8,
                maxAverageNanos = 25L * NANOS_PER_MILLISECOND,
                beforeFrame = { frame -> scheduler.advanceFrame((frame.toLong() + 1L) * 83_333_333L) },
            ) {
                AnimatedSprite(sheet = sampleSpriteSheet(), fps = 12, vsync = vsync)
            }
        },
        PerfScene(
            name = "graphics_primitives",
            width = 96,
            height = 24,
            maxAverageNanos = 40L * NANOS_PER_MILLISECOND,
        ) {
            Row(
                children = listOf(
                    Sprite(sheet = sampleSpriteSheet(), frameIndex = 1),
                    Polygon(
                        points = listOf(PixelPoint(0, 12), PixelPoint(12, 0), PixelPoint(24, 12)),
                        color = PixelColor.White,
                    ),
                    ProgressBar(progress = 0.67f, width = 36),
                ),
                spacing = 4,
            )
        },
        PerfScene(
            name = "page_transition",
            width = 96,
            height = 40,
            maxAverageNanos = 40L * NANOS_PER_MILLISECOND,
        ) {
            PixelNavigator(
                initialRequest = testRouteRequest(
                    name = "home",
                    builder = {
                        AppScaffold(
                            title = Text("PERF"),
                            body = Column(
                                children = listOf(
                                    Text("HOME"),
                                    SizedBox(height = 2),
                                    OutlinedButton("OPEN", onPressed = {}),
                                ),
                                spacing = 1,
                            ),
                            bottomBar = Text("READY"),
                        )
                    },
                ),
                vsync = PixelTickerProvider(ManualFrameScheduler()),
            )
        },
        run {
            // 预先入队 snackbar，使所有采样帧稳定覆盖 overlay 合成路径。
            val overlay = PixelOverlayController()
            overlay.showSnackbar(
                message = "QUEUED",
                action = OutlinedButton("UNDO", onPressed = {}),
            )
            PerfScene(
                name = "overlay",
                width = 108,
                height = 46,
                maxAverageNanos = 40L * NANOS_PER_MILLISECOND,
            ) {
                PixelOverlayHost(
                    controller = overlay,
                    child = Text("HOME"),
                )
            }
        },
    )

    /** 创建动画和图形场景共享的确定性双帧 sprite sheet。 */
    private fun sampleSpriteSheet(): PixelSpriteSheet {
        // 三个固定前景色组成可见且可重复的像素图案。
        val red = PixelColor.fromRgb(220, 60, 60).argb
        val yellow = PixelColor.fromRgb(230, 200, 60).argb
        val blue = PixelColor.fromRgb(60, 100, 220).argb
        // 8x4 backing array 水平容纳两个 4x4 帧。
        val pixels = IntArray(8 * 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) pixels[y * 8 + x] = if ((x + y) % 2 == 0) red else yellow
            for (x in 4 until 8) pixels[y * 8 + x] = if (x == 4 || y == 0 || x == 7 || y == 3) {
                blue
            } else {
                PixelColor.White.argb
            }
        }
        return PixelSpriteSheet(
            bitmap = PixelBitmap(width = 8, height = 4, pixels = pixels),
            frames = listOf(
                PixelBitmapRegion(left = 0, top = 0, width = 4, height = 4),
                PixelBitmapRegion(left = 4, top = 0, width = 4, height = 4),
            ),
        )
    }

    /** 性能门禁的稳定格式、采样规模、环境变量名和单位常量。 */
    private companion object {
        /** 机器可解析报告格式版本。 */
        const val REPORT_FORMAT_VERSION = 2

        /** 固定场景数量，防止场景被静默删除后门禁仍通过。 */
        const val EXPECTED_SCENE_COUNT = 6

        /** 正式采样前用于触发类加载和 JIT 的帧数。 */
        const val WARMUP_FRAMES = 10

        /** 每个场景用于计算平均耗时的固定帧数。 */
        const val SAMPLE_FRAMES = 20

        /** 每个场景独立测量的奇数批次数量，用中位数抑制宿主调度尖峰。 */
        const val SAMPLE_BATCH_COUNT = 7

        /** 一毫秒包含的纳秒数。 */
        const val NANOS_PER_MILLISECOND = 1_000_000L

        /** 可选阈值缩放环境变量，负向测试会把它设为极小正数。 */
        const val THRESHOLD_SCALE_ENV = "PIXEL_PERF_THRESHOLD_SCALE"

        /** shell 门禁传入的唯一运行标识环境变量。 */
        const val RUN_ID_ENV = "PIXEL_PERF_RUN_ID"

        /** 非脚本直接运行测试时使用的报告运行标识。 */
        const val DIRECT_RUN_ID = "direct"

        /** 运行标识允许的安全字符集合。 */
        val REPORT_VALUE_REGEX: Regex = Regex("[A-Za-z0-9._:-]+")

        /** 系统属性中会破坏一行一字段格式的字符集合。 */
        val REPORT_PROPERTY_UNSAFE_REGEX: Regex = Regex("[\\r\\n=]+")
    }
}
