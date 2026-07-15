package com.purride.pixelbenchmark

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.Outputs
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.enterText
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.openDetails
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.prepareListScroll
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.runAnimation
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.scrollList
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.showOverlay
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.startScenario
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在显式授权的 Android 设备上循环真实用户旅程，并验证每轮 Host 终态资源全部归零。
 *
 * 普通 Macrobenchmark 不会启用该类；专用脚本必须同时按类过滤并传入
 * `pixel.soak.enabled=true`。正式 Goal 证据要求实际执行 30–60 分钟，短跑只用于接线验证。
 */
@Suppress("RestrictedApi")
@LargeTest
@RunWith(AndroidJUnit4::class)
class PixelDeviceSoakInstrumentedTest {
    /** 当前 instrumentation 绑定且已通过宿主序列号校验的唯一设备。 */
    private val device by lazy { BenchmarkDeviceHolder.requireAuthorizedDevice() }

    /** AndroidJUnitRunner 注入的专用 soak 参数。 */
    private val arguments by lazy { InstrumentationRegistry.getArguments() }

    /** 在任何启动、广播或输入动作前完成设备身份校验。 */
    @Before
    fun requireAuthorizedDevice() {
        BenchmarkDeviceHolder.requireAuthorizedDevice()
    }

    /** 循环六类真实 Host 旅程，采集内存趋势并输出可机器验收的 JSON。 */
    @Test
    fun runDeviceSoak() {
        // 未显式启用时跳过，防止普通 connected benchmark 意外运行半小时。
        assumeTrue("专用脚本未启用设备 soak", arguments.getString(EnabledArgument) == "true")
        /** 宿主请求的实际长跑秒数，短跑也会被明确标记为非 Goal 证据。 */
        val requestedDurationSeconds = requiredPositiveInt(DurationSecondsArgument)
        check(requestedDurationSeconds <= MaximumDurationSeconds) {
            "$DurationSecondsArgument must be <= $MaximumDurationSeconds"
        }
        /** 两次目标进程内存采样之间的秒数。 */
        val sampleIntervalSeconds = requiredPositiveInt(SampleIntervalSecondsArgument)
        /** 为每轮场景创建、销毁目标进程的官方 Macrobenchmark 操作边界。 */
        val macroScope = MacrobenchmarkScope(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            launchWithClearTask = true,
        )
        /** 单调时钟记录的长跑起点。 */
        val startedElapsedMillis = SystemClock.elapsedRealtime()
        /** 报告使用的 UTC epoch 起点。 */
        val startedEpochMillis = System.currentTimeMillis()
        /** 到达请求时长时停止新旅程的单调时钟截止点。 */
        val deadlineElapsedMillis = startedElapsedMillis + requestedDurationSeconds * 1_000L
        /** 下一次应采集目标进程内存的相对时刻。 */
        var nextMemorySampleElapsedMillis = 0L
        /** 每个稳定旅程名称对应的完成次数。 */
        val journeyCounts = SoakJourneys.associate { journey -> journey.name to 0 }.toMutableMap()
        /** 所有成功完成终态释放的 Host 周期数。 */
        var completedJourneyCycles = 0L
        /** 每轮终态诊断中观察到的最大残留资源计数。 */
        val maximumResidue = linkedMapOf<String, Long>().apply {
            TerminalZeroKeys.forEach { key -> put(key, 0L) }
        }
        /** 目标进程在终态释放后的内存趋势样本。 */
        val memorySamples = mutableListOf<PixelSoakMemorySample>()
        /** 测试过程中保留到报告的失败摘要；成功时为空。 */
        var failureMessage: String? = null

        try {
            while (SystemClock.elapsedRealtime() < deadlineElapsedMillis) {
                /** 以固定轮询顺序选择的当前真实用户旅程。 */
                val journey = SoakJourneys[(completedJourneyCycles % SoakJourneys.size).toInt()]
                macroScope.killProcess()
                macroScope.startScenario(journey.scenario, journey.sentinel)
                journey.prepare(macroScope)
                journey.execute()
                /** 目标进程广播返回的 Host 终态基础类型计数。 */
                val terminalDiagnostics = collectTerminalDiagnostics()
                assertTerminalDiagnostics(terminalDiagnostics, maximumResidue)
                completedJourneyCycles += 1L
                journeyCounts[journey.name] = journeyCounts.getValue(journey.name) + 1

                /** 当前旅程完成时相对长跑起点的毫秒数。 */
                val elapsedMillis = SystemClock.elapsedRealtime() - startedElapsedMillis
                if (memorySamples.isEmpty() || elapsedMillis >= nextMemorySampleElapsedMillis) {
                    memorySamples += collectMemorySample(elapsedMillis)
                    nextMemorySampleElapsedMillis = elapsedMillis + sampleIntervalSeconds * 1_000L
                    println(
                        "PIXEL_DEVICE_SOAK elapsedSeconds=${elapsedMillis / 1_000L} " +
                            "cycles=$completedJourneyCycles samples=${memorySamples.size}",
                    )
                }
            }
        } catch (throwable: Throwable) {
            failureMessage = throwable.stackTraceToString().take(MaximumFailureLength)
        } finally {
            /** 终止目标进程前最后一次读取真实终态内存，避免用复制值伪造终点。 */
            val finalSampleElapsedMillis = SystemClock.elapsedRealtime() - startedElapsedMillis
            if (completedJourneyCycles > 0L &&
                (memorySamples.isEmpty() ||
                    memorySamples.last().elapsedMillis < finalSampleElapsedMillis)
            ) {
                runCatching { collectMemorySample(finalSampleElapsedMillis) }
                    .onSuccess(memorySamples::add)
            }
            /** 终态报告前确保目标进程不会继续持有窗口或后台工作。 */
            runCatching { macroScope.killProcess() }
                .onFailure { throwable ->
                    if (failureMessage == null) {
                        failureMessage = throwable.stackTraceToString().take(MaximumFailureLength)
                    }
                }
        }

        /** 单调时钟记录的实际执行时长。 */
        val actualDurationMillis = SystemClock.elapsedRealtime() - startedElapsedMillis
        /** 首尾三分位中位数计算出的内存有界性结论。 */
        val heapTrend = evaluateHeapTrend(memorySamples)
        /** 无异常、完成旅程且所有终态与内存门禁通过时测试本身通过。 */
        val overallPass = failureMessage == null &&
            completedJourneyCycles > 0L &&
            maximumResidue.values.all { count -> count == 0L } &&
            heapTrend.isBounded
        /** 只有真实 30–60 分钟且样本充分的通过结果可作为 Goal 长跑证据。 */
        val qualifiesForGoal = overallPass &&
            requestedDurationSeconds in MinimumGoalDurationSeconds..MaximumDurationSeconds &&
            actualDurationMillis >= MinimumGoalDurationSeconds * 1_000L &&
            actualDurationMillis <= MaximumDurationSeconds * 1_000L + MaximumDurationOverrunMillis &&
            memorySamples.size >= MinimumGoalMemorySampleCount
        /** 完整机器报告，失败路径也必须先输出再让 JUnit 失败。 */
        val report = buildReport(
            startedEpochMillis = startedEpochMillis,
            requestedDurationSeconds = requestedDurationSeconds,
            actualDurationMillis = actualDurationMillis,
            completedJourneyCycles = completedJourneyCycles,
            journeyCounts = journeyCounts,
            maximumResidue = maximumResidue,
            memorySamples = memorySamples,
            heapTrend = heapTrend,
            overallPass = overallPass,
            qualifiesForGoal = qualifiesForGoal,
            failureMessage = failureMessage,
        )
        /** AndroidX additional test output 会把该文件确定性复制回宿主构建目录。 */
        val reportPath = Outputs.writeFile(ReportFileName, reportOnRunEndOnly = true) { file ->
            file.writeText(report.toString(2), Charsets.UTF_8)
        }
        println("PIXEL_DEVICE_SOAK_REPORT path=$reportPath")
        check(overallPass) { "Pixel device soak failed; report=$reportPath" }
    }

    /** 读取一个必填正整数 runner 参数。 */
    private fun requiredPositiveInt(name: String): Int {
        /** runner 注入并去除首尾空白的原始参数。 */
        val rawValue = arguments.getString(name)?.trim()
        /** 解析后的正整数参数。 */
        val parsedValue = rawValue?.toIntOrNull()
        require(parsedValue != null && parsedValue > 0) { "$name must be a positive integer" }
        return parsedValue
    }

    /** 请求目标进程在主线程 dispose 当前真实 Host，并解析稳定键值结果。 */
    private fun collectTerminalDiagnostics(): Map<String, Long> {
        /** 显式组件广播的 shell 输出，不能影响其他包或设备。 */
        val output = device.executeShellCommand(
            "am broadcast -W -n $DiagnosticsReceiverComponent -a $DiagnosticsAction",
        )
        check(output.contains("result=0")) { "terminal diagnostics broadcast failed: $output" }
        /** 广播 result data 中无空格的稳定键值载荷。 */
        val wireValue = ResultDataPattern.find(output)?.groupValues?.get(1)
            ?: error("terminal diagnostics data missing: $output")
        /** 按字段名保存的全部数值结果。 */
        val diagnostics = wireValue.split(',').associate { entry ->
            /** 一个 `name=value` 诊断字段。 */
            val parts = entry.split('=', limit = 2)
            check(parts.size == 2) { "malformed terminal diagnostic: $entry" }
            parts[0] to (parts[1].toLongOrNull()
                ?: error("non-numeric terminal diagnostic: $entry"))
        }
        check(diagnostics.keys == ExpectedTerminalKeys) {
            "terminal diagnostic keys mismatch: ${diagnostics.keys}"
        }
        return diagnostics
    }

    /** 验证终态布尔不变量和所有资源零计数，同时更新整场长跑最大残留。 */
    private fun assertTerminalDiagnostics(
        diagnostics: Map<String, Long>,
        maximumResidue: MutableMap<String, Long>,
    ) {
        check(diagnostics.getValue("lifecycleDestroyed") == 1L)
        check(diagnostics.getValue("destroyCount") == 1L)
        check(diagnostics.getValue("frameScopeDisposed") == 1L)
        TerminalZeroKeys.forEach { key ->
            /** 当前轮次中指定资源的终态残留数量。 */
            val residue = diagnostics.getValue(key)
            maximumResidue[key] = max(maximumResidue.getValue(key), residue)
            check(residue == 0L) { "terminal resource leak: $key=$residue" }
        }
    }

    /** 从目标进程 `dumpsys meminfo` 读取终态 PSS 与 Java heap。 */
    private fun collectMemorySample(elapsedMillis: Long): PixelSoakMemorySample {
        /** 只针对基准目标包的系统内存报告。 */
        val meminfo = device.executeShellCommand(
            "dumpsys meminfo ${PixelBenchmarkJourneys.TargetPackage}",
        )
        /** Android 版本差异下优先摘要、再回退 TOTAL 表格行的 PSS。 */
        val totalPssKb = SummaryTotalPssPattern.find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: TableTotalPssPattern.find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("TOTAL PSS missing from target meminfo")
        /** 系统摘要中目标进程 Java heap 的 KB 数。 */
        val javaHeapKb = JavaHeapPattern.find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Java Heap missing from target meminfo")
        return PixelSoakMemorySample(
            elapsedMillis = elapsedMillis,
            totalPssKb = totalPssKb,
            javaHeapKb = javaHeapKb,
        )
    }

    /** 使用首尾三分位中位数判断 PSS 是否持续增长超过固定与比例双重预算。 */
    private fun evaluateHeapTrend(samples: List<PixelSoakMemorySample>): PixelSoakHeapTrend {
        if (samples.size < MinimumHeapTrendSampleCount) {
            return PixelSoakHeapTrend(
                firstMedianPssKb = samples.firstOrNull()?.totalPssKb ?: 0L,
                lastMedianPssKb = samples.lastOrNull()?.totalPssKb ?: 0L,
                growthPssKb = 0L,
                allowedGrowthPssKb = ShortRunGrowthBudgetKb,
                isBounded = samples.isNotEmpty(),
            )
        }
        /** 首尾各使用的三分位样本数量，至少为一。 */
        val segmentSize = max(1, samples.size / 3)
        /** 起始三分位 PSS 的稳定中位数。 */
        val firstMedian = median(samples.take(segmentSize).map(PixelSoakMemorySample::totalPssKb))
        /** 末尾三分位 PSS 的稳定中位数。 */
        val lastMedian = median(samples.takeLast(segmentSize).map(PixelSoakMemorySample::totalPssKb))
        /** 末尾相对起始中位数的有符号增长量。 */
        val growth = lastMedian - firstMedian
        /** 允许固定 8MiB 或起始 PSS 20% 中的较大值，吸收 GC/系统页噪声。 */
        val allowedGrowth = max(LongRunGrowthBudgetKb, firstMedian / GrowthBudgetDivisor)
        return PixelSoakHeapTrend(
            firstMedianPssKb = firstMedian,
            lastMedianPssKb = lastMedian,
            growthPssKb = growth,
            allowedGrowthPssKb = allowedGrowth,
            isBounded = growth <= allowedGrowth,
        )
    }

    /** 返回排序长整数集合的中位数，偶数集合取中间两项整数均值。 */
    private fun median(values: List<Long>): Long {
        require(values.isNotEmpty())
        /** 用于稳定统计且不改变原样本顺序的副本。 */
        val sortedValues = values.sorted()
        /** 排序集合中间位置。 */
        val middleIndex = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middleIndex]
        } else {
            (sortedValues[middleIndex - 1] + sortedValues[middleIndex]) / 2L
        }
    }

    /** 构造包含设备身份、旅程、终态资源和内存趋势的稳定 JSON 报告。 */
    private fun buildReport(
        startedEpochMillis: Long,
        requestedDurationSeconds: Int,
        actualDurationMillis: Long,
        completedJourneyCycles: Long,
        journeyCounts: Map<String, Int>,
        maximumResidue: Map<String, Long>,
        memorySamples: List<PixelSoakMemorySample>,
        heapTrend: PixelSoakHeapTrend,
        overallPass: Boolean,
        qualifiesForGoal: Boolean,
        failureMessage: String?,
    ): JSONObject {
        /** 当前设备硬件序列号，用于与宿主授权链复核。 */
        val hardwareSerial = device.executeShellCommand("getprop ro.serialno").trim()
        /** 当前系统显示报告中解析出的刷新率；无法解析时为零且不冒充代表设备证据。 */
        val refreshRateHz = DisplayRefreshPattern
            .find(device.executeShellCommand("dumpsys display"))
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: 0.0
        /** 设备身份和系统能力对象。 */
        val deviceJson = JSONObject()
            .put("hardwareSerial", hardwareSerial)
            .put("isEmulator", device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("model", Build.MODEL)
            .put("refreshRateHz", refreshRateHz)
        /** 每个旅程完成次数的确定性对象。 */
        val journeyJson = JSONObject().apply {
            SoakJourneys.forEach { journey -> put(journey.name, journeyCounts.getValue(journey.name)) }
        }
        /** 每类终态残留最大值对象。 */
        val residueJson = JSONObject().apply {
            maximumResidue.forEach { (key, value) -> put(key, value) }
        }
        /** 按采样顺序保留的目标进程内存数组。 */
        val memoryJson = JSONArray().apply {
            memorySamples.forEach { sample ->
                put(
                    JSONObject()
                        .put("elapsedMillis", sample.elapsedMillis)
                        .put("totalPssKb", sample.totalPssKb)
                        .put("javaHeapKb", sample.javaHeapKb),
                )
            }
        }
        /** 首尾中位数与增长预算组成的 heap 门禁对象。 */
        val heapTrendJson = JSONObject()
            .put("firstMedianPssKb", heapTrend.firstMedianPssKb)
            .put("lastMedianPssKb", heapTrend.lastMedianPssKb)
            .put("growthPssKb", heapTrend.growthPssKb)
            .put("allowedGrowthPssKb", heapTrend.allowedGrowthPssKb)
            .put("bounded", heapTrend.isBounded)
        return JSONObject()
            .put("schemaVersion", ReportSchemaVersion)
            .put("status", if (overallPass) "pass" else "fail")
            .put("qualifiesForGoal", qualifiesForGoal)
            .put("startedEpochMillis", startedEpochMillis)
            .put("requestedDurationSeconds", requestedDurationSeconds)
            .put("actualDurationMillis", actualDurationMillis)
            .put("completedJourneyCycles", completedJourneyCycles)
            .put("terminalDiagnosticsChecks", completedJourneyCycles)
            .put("device", deviceJson)
            .put("journeys", journeyJson)
            .put("maximumTerminalResidue", residueJson)
            .put("memorySamples", memoryJson)
            .put("heapTrend", heapTrendJson)
            .put("failure", failureMessage ?: JSONObject.NULL)
    }

    /** 专用 runner 参数、报告协议与门禁预算。 */
    private companion object {
        /** 防止普通 connected benchmark 意外进入长跑的显式开关。 */
        const val EnabledArgument: String = "pixel.soak.enabled"

        /** 请求长跑秒数的 runner 参数。 */
        const val DurationSecondsArgument: String = "pixel.soak.durationSeconds"

        /** 内存采样间隔秒数的 runner 参数。 */
        const val SampleIntervalSecondsArgument: String = "pixel.soak.sampleIntervalSeconds"

        /** Goal 接受的最短真实设备长跑秒数。 */
        const val MinimumGoalDurationSeconds: Int = 30 * 60

        /** Goal 接受且脚本允许的最长真实设备长跑秒数。 */
        const val MaximumDurationSeconds: Int = 60 * 60

        /** 最后一轮旅程允许超过 60 分钟截止点的最大收尾时间。 */
        const val MaximumDurationOverrunMillis: Long = 30_000L

        /** 正式长跑至少需要的内存样本数。 */
        const val MinimumGoalMemorySampleCount: Int = 10

        /** 计算首尾三分位趋势所需的最少样本数。 */
        const val MinimumHeapTrendSampleCount: Int = 3

        /** PSS 长跑允许的固定增长预算，单位 KB。 */
        const val LongRunGrowthBudgetKb: Long = 8L * 1_024L

        /** 少于三个样本的接线短跑使用的非 Goal 固定预算。 */
        const val ShortRunGrowthBudgetKb: Long = LongRunGrowthBudgetKb

        /** 以起始 PSS 的五分之一作为比例增长预算。 */
        const val GrowthBudgetDivisor: Long = 5L

        /** 报告中保留的失败堆栈最大字符数。 */
        const val MaximumFailureLength: Int = 16_000

        /** 长跑 additional output 的稳定文件名。 */
        const val ReportFileName: String = "pixel-device-soak-report.json"

        /** 当前机器报告结构版本。 */
        const val ReportSchemaVersion: Int = 1

        /** 目标应用中只用于 benchmark 的显式诊断 Receiver。 */
        const val DiagnosticsReceiverComponent: String =
            "com.purride.pixelbenchmark.target/.PixelBenchmarkDiagnosticsReceiver"

        /** Receiver 接受的唯一终态诊断 action。 */
        const val DiagnosticsAction: String =
            "com.purride.pixelbenchmark.target.action.COLLECT_TERMINAL_DIAGNOSTICS"

        /** 从 `am broadcast` 输出提取双引号 result data 的表达式。 */
        val ResultDataPattern: Regex = Regex("data=\"([^\"]+)\"")

        /** 从 Android 摘要行读取 TOTAL PSS 的表达式。 */
        val SummaryTotalPssPattern: Regex = Regex("TOTAL PSS:\\s*(\\d+)")

        /** 从 Android 兼容表格 TOTAL 行读取 PSS 的表达式。 */
        val TableTotalPssPattern: Regex = Regex("(?m)^\\s*TOTAL\\s+(\\d+)")

        /** 从 Android 摘要行读取 Java Heap 的表达式。 */
        val JavaHeapPattern: Regex = Regex("Java Heap:\\s*(\\d+)")

        /** 从显示服务文本读取当前活动刷新率的兼容表达式。 */
        val DisplayRefreshPattern: Regex = Regex(
            "(?:renderFrameRate\\s+|mRefreshRate=|refreshRate=)(\\d+(?:\\.\\d+)?)",
        )

        /** 每轮必须精确出现的全部终态字段。 */
        val ExpectedTerminalKeys: Set<String> = linkedSetOf(
            "lifecycleDestroyed",
            "destroyCount",
            "frameScopeDisposed",
            "pendingCallbacks",
            "frameListeners",
            "activeTickers",
            "liveTickers",
            "sourceFramePending",
            "retainedElementRoot",
            "retainedRenderRoot",
            "retainedTargets",
            "pendingBuild",
            "focusedTextInput",
            "activePagers",
            "activeLists",
        )

        /** 每轮必须为零且进入全程最大值统计的资源字段。 */
        val TerminalZeroKeys: Set<String> = linkedSetOf(
            "pendingCallbacks",
            "frameListeners",
            "activeTickers",
            "liveTickers",
            "sourceFramePending",
            "retainedElementRoot",
            "retainedRenderRoot",
            "retainedTargets",
            "pendingBuild",
            "focusedTextInput",
            "activePagers",
            "activeLists",
        )

        /** 固定轮询的六类真实 SDK 用户旅程。 */
        val SoakJourneys: List<PixelSoakJourney> = listOf(
            PixelSoakJourney("startup", "startup", "STARTUP READY"),
            PixelSoakJourney(
                name = "listScroll",
                scenario = "list_scroll",
                sentinel = "ROW 0000",
                prepare = { prepareListScroll() },
                execute = { scrollList() },
            ),
            PixelSoakJourney(
                name = "textInput",
                scenario = "text_input",
                sentinel = "BENCHMARK INPUT",
                execute = { enterText() },
            ),
            PixelSoakJourney(
                name = "animation",
                scenario = "animation",
                sentinel = "ANIMATE",
                execute = { runAnimation() },
            ),
            PixelSoakJourney(
                name = "pageTransition",
                scenario = "page_transition",
                sentinel = "OPEN DETAILS",
                execute = { openDetails() },
            ),
            PixelSoakJourney(
                name = "overlay",
                scenario = "overlay",
                sentinel = "SHOW OVERLAY",
                execute = { showOverlay() },
            ),
        )
    }
}

/** 一条可重复启动、准备并执行的真实 benchmark 用户旅程。 */
private data class PixelSoakJourney(
    /** 报告中使用的稳定旅程名称。 */
    val name: String,
    /** 目标 Activity Intent 接受的场景协议值。 */
    val scenario: String,
    /** 证明生产 Host 已渲染完成的可访问性文本。 */
    val sentinel: String,
    /** 旅程动作前在测量外准备的坐标或状态。 */
    val prepare: MacrobenchmarkScope.() -> Unit = {},
    /** 对真实 Host 执行的输入、动画、导航或 Overlay 动作。 */
    val execute: () -> Unit = {},
)

/** 目标进程完成一轮 Host 释放后的系统内存样本。 */
private data class PixelSoakMemorySample(
    /** 相对长跑开始的单调毫秒数。 */
    val elapsedMillis: Long,
    /** `dumpsys meminfo` 报告的目标进程总 PSS，单位 KB。 */
    val totalPssKb: Long,
    /** `dumpsys meminfo` 报告的目标进程 Java heap，单位 KB。 */
    val javaHeapKb: Long,
)

/** 首尾三分位 PSS 中位数与允许增长预算组成的有界性结论。 */
private data class PixelSoakHeapTrend(
    /** 起始三分位的 PSS 中位数，单位 KB。 */
    val firstMedianPssKb: Long,
    /** 末尾三分位的 PSS 中位数，单位 KB。 */
    val lastMedianPssKb: Long,
    /** 末尾减起始的有符号 PSS 增长，单位 KB。 */
    val growthPssKb: Long,
    /** 固定与比例预算中较大的允许增长，单位 KB。 */
    val allowedGrowthPssKb: Long,
    /** 实际增长是否没有超过允许预算。 */
    val isBounded: Boolean,
)
