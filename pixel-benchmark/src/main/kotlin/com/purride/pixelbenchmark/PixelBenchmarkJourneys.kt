package com.purride.pixelbenchmark

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.UiDevice

/** 供 Macrobenchmark 与 Baseline Profile 生成共同使用的确定性用户旅程。 */
internal object PixelBenchmarkJourneys {
    /** 测量前从独立系统 hierarchy 快照解析出的当前列表物理手势。 */
    @Volatile
    private var preparedListGesture: PreparedListGesture? = null

    /** 启动一个目标场景，并等待该场景专属的可访问性哨兵。 */
    fun MacrobenchmarkScope.startScenario(
        scenario: String,
        sentinel: String,
        frameDiagnosticsEnabled: Boolean = false,
    ) {
        // 在任何跨进程或输入动作前验证设备身份，防止 connected test 误操作其他设备。
        requireDevice()
        /** 携带稳定场景协议名的显式启动 Intent。 */
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(TargetPackage, TargetActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(ScenarioExtra, scenario)
            putExtra(FrameDiagnosticsExtra, frameDiagnosticsEnabled)
        }
        startActivityAndWait(launchIntent)
        requireDevice().waitForText(sentinel)
    }

    /** 在测量块外按基准目标的固定逻辑像素协议准备列表内物理手势坐标。 */
    fun prepareListScroll() {
        /** 已授权设备用于计算横向显示中心，不读取 API 37 会污染的虚拟节点 bounds。 */
        val device = requireDevice()
        /** 120 个逻辑像素按固定 dotSize 换算后的列表物理高度。 */
        val viewportHeight = BenchmarkListViewportHeightLogical * BenchmarkTargetDotSizePx
        /** 列表顶部逻辑偏移按固定 dotSize 换算后的屏幕纵坐标。 */
        val viewportTop = BenchmarkListViewportTopLogical * BenchmarkTargetDotSizePx
        preparedListGesture = PreparedListGesture(
            centerX = device.displayWidth / 2,
            startY = viewportTop + ((viewportHeight * 4) / 5),
            endY = viewportTop + (viewportHeight / 5),
        )
    }

    /** 在真实 Pixel 懒列表上执行多次接近整屏高度的滑动。 */
    fun scrollList() {
        /** 向 PixelHostView 注入物理指针手势的已授权设备实例。 */
        val device = requireDevice()
        /** 当前测量迭代在 setupBlock 中准备且只消费一次的手势。 */
        val gesture = preparedListGesture
            ?: error("Pixel benchmark list gesture was not prepared outside the measured block")
        preparedListGesture = null
        repeat(ListSwipeCount) {
            // 有界 shell touchscreen 在 API 24–37 投递真实指针序列，并避开 UiDevice 管道死锁。
            BenchmarkDeviceHolder.executeShellCommand(
                "input touchscreen swipe ${gesture.centerX} ${gesture.startY} " +
                    "${gesture.centerX} ${gesture.endY} $ListSwipeDurationMillis",
            )
            device.waitForIdle()
        }
        assertListScrolled()
    }

    /** 在测量块结束后证明物理手势改变了生产列表状态。 */
    @Suppress("DEPRECATION")
    private fun assertListScrolled() {
        /** 滚动事件会使 provider 发布新的远端行；该节点是行为验收而非同名 API 替代。 */
        val progressedRow = waitForAutomationNode("post-scroll row") { node ->
            node.rowIndexOrNull()?.let { rowIndex -> rowIndex >= MinimumPostScrollRow } == true
        }
        progressedRow.recycle()
    }

    /** 通过可编辑可访问性契约替换基准文本框内容。 */
    @Suppress("DEPRECATION")
    fun enterText() {
        /** 名称不依赖当前多行文本值的可编辑节点。 */
        val input = waitForAutomationNode("editable benchmark input") { node ->
            node.isEditable &&
                (node.matchesLabel("BENCHMARK INPUT") || node.matchesLabel("ALPHA BRAVO"))
        }
        try {
            check(input.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Pixel benchmark input rejected ACTION_CLICK"
            }
            /** 通过 Android 标准 ACTION_SET_TEXT 提交的 Unicode/Bidi 文本参数。 */
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    BenchmarkInputText,
                )
            }
            check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                "Pixel benchmark input rejected ACTION_SET_TEXT"
            }
        } finally {
            input.recycle()
        }
        /**
         * 只有新值与折叠选区从下一份生产语义快照可见时，才能证明 ACTION_SET_TEXT 对应的
         * retained render 已提交；禁止使用全局 idle 把后续 500ms 光标闪烁混入输入动作分布。
         */
        val committedInput = waitForAutomationNode("committed benchmark input") { node ->
            node.isEditable &&
                node.text?.toString() == BenchmarkInputText &&
                node.textSelectionStart == BenchmarkInputText.length &&
                node.textSelectionEnd == BenchmarkInputText.length
        }
        committedInput.recycle()
    }

    /** 启动 retained 隐式动画，并让采集窗口覆盖完整动画。 */
    fun runAnimation() {
        requireDevice().clickText("ANIMATE")
        Thread.sleep(AnimationObservationMillis)
    }

    /** push 一个真实 Navigator 路由，并等待目标页哨兵。 */
    fun openDetails() {
        /** 执行可访问路由动作并等待完成的设备实例。 */
        val device = requireDevice()
        device.clickText("OPEN DETAILS")
        device.waitForText("DETAILS READY")
    }

    /** 展示生产模态 Overlay，并等待其可访问标题。 */
    fun showOverlay() {
        /** 触发路由并等待模态完成的设备实例。 */
        val device = requireDevice()
        device.clickText("SHOW OVERLAY")
        device.waitForText("OVERLAY VISIBLE")
    }

    /** 获取当前 instrumentation 设备以执行跨进程交互。 */
    private fun requireDevice(): UiDevice = BenchmarkDeviceHolder.requireAuthorizedDevice()

    /** 点击一个精确文本节点，超时则以场景专属错误失败。 */
    @Suppress("DEPRECATION")
    private fun UiDevice.clickText(text: String) {
        /** 与稳定基准标签精确匹配的可访问目标。 */
        val target = waitForAutomationNode("target '$text'") { node -> node.matchesLabel(text) }
        try {
            check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Pixel benchmark target '$text' rejected ACTION_CLICK"
            }
        } finally {
            target.recycle()
        }
    }

    /** 等待精确或包含指定文本的哨兵变为可访问。 */
    @Suppress("DEPRECATION")
    private fun UiDevice.waitForText(text: String) {
        /** 精确或包含文本是否在确定性超时前出现。 */
        val sentinel = waitForAutomationNode("sentinel '$text'") { node -> node.matchesLabel(text) }
        sentinel.recycle()
    }

    /** 在统一超时内轮询 UiAutomation 活动窗口，返回第一个满足条件的节点。 */
    @Suppress("DEPRECATION")
    private fun waitForAutomationNode(
        description: String,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        /** 所有轮询共享的单调时钟截止点。 */
        val deadlineMillis = SystemClock.uptimeMillis() + UiTimeoutMillis
        do {
            findAutomationNode(predicate)?.let { node -> return node }
            SystemClock.sleep(UiPollIntervalMillis)
        } while (SystemClock.uptimeMillis() < deadlineMillis)
        error(
            "Pixel benchmark $description was not found; " +
                "currentPackage=${requireDevice().currentPackageName} " +
                "automationLabels=${currentAutomationLabels()}",
        )
    }

    /** 对活动窗口执行一次广度优先查询，并把未返回的节点全部回收。 */
    @Suppress("DEPRECATION")
    private fun findAutomationNode(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        /** 当前活动窗口根节点；窗口尚未发布时本轮查询无匹配。 */
        val root = BenchmarkDeviceHolder.uiAutomation.rootInActiveWindow ?: return null
        /** 按广度优先顺序等待查询的 Android 可访问性节点。 */
        val pendingNodes = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (pendingNodes.isNotEmpty()) {
            /** 当前读取并在子节点入队后回收的节点。 */
            val node = pendingNodes.removeFirst()
            // API 36/37 会在同一 UiAutomation 会话复用虚拟节点缓存；读取 bounds/属性前强制刷新。
            node.refresh()
            if (node.packageName?.toString() == TargetPackage && predicate(node)) {
                pendingNodes.forEach(AccessibilityNodeInfo::recycle)
                return node
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pendingNodes::addLast) }
            node.recycle()
        }
        return null
    }

    /** 返回节点文本或内容描述是否精确等于或包含指定稳定标签。 */
    private fun AccessibilityNodeInfo.matchesLabel(expected: String): Boolean {
        return text?.toString()?.contains(expected) == true ||
            contentDescription?.toString()?.contains(expected) == true
    }

    /** 从稳定的 `ROW 0000` 文本格式解析列表行号，非行节点返回空。 */
    private fun AccessibilityNodeInfo.rowIndexOrNull(): Int? {
        /** 当前节点公开的可见文本。 */
        val visibleText = text?.toString() ?: return null
        if (!visibleText.startsWith(RowLabelPrefix)) return null
        return visibleText.removePrefix(RowLabelPrefix).toIntOrNull()
    }

    /** 收集活动窗口的精简标签，用于超时失败时保留可复核诊断。 */
    @Suppress("DEPRECATION")
    private fun currentAutomationLabels(): List<String> {
        /** 当前活动窗口根节点；窗口尚未发布时返回空诊断。 */
        val root = BenchmarkDeviceHolder.uiAutomation.rootInActiveWindow ?: return emptyList()
        /** 按广度优先顺序等待遍历的 Android 可访问性节点。 */
        val pendingNodes = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        /** 从文本和内容描述收集的非空可读标签。 */
        val labels = linkedSetOf<String>()
        while (pendingNodes.isNotEmpty() && labels.size < FailureLabelLimit) {
            /** 当前读取并在子节点入队后回收的节点。 */
            val node = pendingNodes.removeFirst()
            // 失败快照同样刷新，避免把上一 Activity 的旧标签误写入诊断。
            node.refresh()
            node.text?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
            node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
            repeat(node.childCount) { index -> node.getChild(index)?.let(pendingNodes::addLast) }
            node.recycle()
        }
        pendingNodes.forEach(AccessibilityNodeInfo::recycle)
        return labels.toList()
    }

    /** 由 `com.android.test` 模块安装的目标包名。 */
    const val TargetPackage: String = "com.purride.pixelbenchmark.target"

    /** 接收场景 Intent 的完整导出 Activity 名。 */
    private const val TargetActivity: String = "$TargetPackage.PixelBenchmarkActivity"

    /** 不建立编译期依赖而与目标共享的 Intent extra 键。 */
    private const val ScenarioExtra: String = "pixel.benchmark.SCENARIO"

    /** 不建立编译期依赖而与目标共享的分阶段 trace Intent extra 键。 */
    private const val FrameDiagnosticsExtra: String = "pixel.benchmark.FRAME_DIAGNOSTICS"

    /** 等待可访问性哨兵或动作的最长时间。 */
    private const val UiTimeoutMillis: Long = 10_000L

    /** 新帧或路由发布期间两次可访问性树轮询之间的延迟。 */
    private const val UiPollIntervalMillis: Long = 50L

    /** 复制到失败旅程消息中的最大可见标签数量。 */
    private const val FailureLabelLimit: Int = 20

    /** 单次测量迭代中的大幅列表手势次数。 */
    private const val ListSwipeCount: Int = 5

    /** 每次 shell input 物理 swipe 的持续时间，避免瞬时事件被系统手势识别丢弃。 */
    private const val ListSwipeDurationMillis: Int = 200

    /** 五次物理手势后必须至少出现的行号，防止空 trace 或未命中手势假绿。 */
    private const val MinimumPostScrollRow: Int = 20

    /** 列表可访问性行文本使用的稳定前缀。 */
    private const val RowLabelPrefix: String = "ROW "

    /** 基准目标显式配置的一个逻辑像素对应物理像素数。 */
    private const val BenchmarkTargetDotSizePx: Int = 4

    /** 基准列表视口相对 Host 顶部的固定逻辑像素偏移。 */
    private const val BenchmarkListViewportTopLogical: Int = 13

    /** 基准目标声明的列表视口固定逻辑高度。 */
    private const val BenchmarkListViewportHeightLogical: Int = 120

    /** 覆盖 ASCII、组合标记、Emoji 与混合 Bidi 编辑的文本载荷。 */
    private const val BenchmarkInputText: String = "PIXEL SDK Café 👨‍👩‍👧‍👦 ABC אבג 123"

    /** 测量块内保留的时长，用于确保 700ms 动画完整结束。 */
    private const val AnimationObservationMillis: Long = 850L
}

/** 测量前固定且完全位于当前列表视口内的一次纵向物理手势。 */
private data class PreparedListGesture(
    /** 手势起止点共用的列表水平中心坐标。 */
    val centerX: Int,
    /** 手势位于列表视口下部的起点纵坐标。 */
    val startY: Int,
    /** 手势位于列表视口上部的终点纵坐标。 */
    val endY: Int,
)
