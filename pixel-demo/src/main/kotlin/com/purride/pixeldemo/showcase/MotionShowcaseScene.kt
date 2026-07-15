package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelSpringSpec
import com.purride.pixelui.Popover
import com.purride.pixelui.Row
import com.purride.pixelui.Slider
import com.purride.pixelui.Slidable
import com.purride.pixelui.SlidableAction
import com.purride.pixelui.SlidableActionPane
import com.purride.pixelui.SlidableMotion
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.IntOffset
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import kotlin.time.Duration.Companion.milliseconds

/**
 * 可交互的标准组件动效场景，集中展示主题 token 与系统动效策略的组合效果。
 */
val MotionShowcaseScene: DemoScene = ComponentExampleScene(
    id = "animation_motion_showcase",
    title = "Motion Theme Showcase",
    summary = "标准控件微状态、空间动效与 reduce-motion 策略预览",
    category = DemoCatalog.animation,
    tags = setOf("component", "animation", "motion", "accessibility", "reduce-motion"),
    apis = setOf(
        "PixelMotionTheme",
        "PixelMotionScope",
        "PixelMotionSettings",
        "OutlinedButton",
        "Switch",
        "Slider",
        "Tabs",
        "Slidable",
        "Popover",
    ),
    bodyBuilder = { env -> MotionShowcaseBody(env = env) },
)

/**
 * Demo 专用 token。较长的时长让按压、选择、settle 和浮层切换更容易被肉眼观察。
 */
private val MotionShowcaseTheme: PixelMotionThemeData = PixelMotionThemeData(
    feedback = PixelMotionSpec(
        duration = 180.milliseconds,
        curve = Curves.EaseOut,
        transition = PixelMotionTransitionPreset.Scale,
        role = PixelMotionRole.Feedback,
    ),
    selection = PixelMotionSpec(
        duration = 280.milliseconds,
        curve = Curves.EaseInOut,
        delay = 30.milliseconds,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Selection,
    ),
    slidableSettle = PixelMotionSpec(
        duration = 420.milliseconds,
        curve = Curves.EaseOut,
        transition = PixelMotionTransitionPreset.SlideHorizontal,
        spring = PixelSpringSpec(stiffness = 360f, dampingRatio = 0.82f),
        role = PixelMotionRole.Spatial,
    ),
    popoverEnter = PixelMotionSpec(
        duration = 320.milliseconds,
        curve = Curves.EaseOut,
        delay = 50.milliseconds,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
    popoverExit = PixelMotionSpec(
        duration = 240.milliseconds,
        curve = Curves.EaseIn,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
)

/**
 * 场景内可切换的 Host 动效策略。
 *
 * @property buttonLabel 模式选择按钮显示的短标签。
 * @property settings 注入预览子树的系统动效设置。
 */
private enum class MotionPreviewMode(
    val buttonLabel: String,
    val settings: PixelMotionSettings,
) {
    /** 使用主题原始时长和完整运动通道。 */
    NORMAL(
        buttonLabel = "NORMAL",
        settings = PixelMotionSettings.Default,
    ),

    /** 启用 role-aware reduce-motion：微状态同步完成，空间动效缩短为淡入淡出。 */
    REDUCE_MOTION(
        buttonLabel = "REDUCE",
        settings = PixelMotionSettings(reduceMotion = true),
    ),

    /** 模拟 Android animator duration scale 为 0，所有终态同步提交。 */
    SCALE_ZERO(
        buttonLabel = "SCALE 0",
        settings = PixelMotionSettings(animatorDurationScale = 0f),
    ),
}

/**
 * 持有 showcase 的受控业务状态；所有动画时钟均来自 [DemoEnv.vsync]。
 *
 * @property env Demo Host 环境与统一 ticker provider。
 * @property key retained widget identity。
 */
private class MotionShowcaseBody(
    val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** 创建可交互预览的 retained 状态。 */
    override fun createState(): State<out StatefulWidget> = MotionShowcaseState()
}

/** 管理模式、选择值、Popover 显隐和交互结果文本。 */
private class MotionShowcaseState : State<MotionShowcaseBody>() {
    /** 当前注入预览子树的系统动效策略。 */
    private var previewMode: MotionPreviewMode = MotionPreviewMode.NORMAL

    /** 主按钮被完整点击的次数。 */
    private var buttonPressCount: Int = 0

    /** 显式驱动主按钮 focus 微状态的焦点节点。 */
    private val buttonFocusNode: FocusNode = FocusNode(debugLabel = "motion-showcase-button")

    /** Switch 的受控逻辑值。 */
    private var switchChecked: Boolean = false

    /** Slider 的受控逻辑值。 */
    private var sliderValue: Float = 0.28f

    /** Tabs 的受控选中下标。 */
    private var selectedTab: Int = 0

    /** Popover 的受控展开状态。 */
    private var popoverExpanded: Boolean = false

    /** Slidable action 或点击反馈的最新说明。 */
    private var slidableStatus: String = "左右拖动以观察 settle"

    /**
     * 构建模式切换器和唯一的主题/时钟预览作用域。
     *
     * 模式按钮刻意位于局部 [PixelMotionScope] 外，保证任何预览策略下都能继续切换模式。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(buttonFocusNode)
        return Column(
            children = listOf(
                modeSelector(),
                PixelMotionTheme(
                    data = MotionShowcaseTheme,
                    child = PixelMotionScope(
                        vsync = widget.env.vsync,
                        settings = previewMode.settings,
                        child = previewContent(),
                        key = "motion-showcase-scope",
                    ),
                    key = "motion-showcase-theme",
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    /** 构建 normal、reduce-motion 和 scale=0 三态切换器。 */
    private fun modeSelector(): Widget {
        val modeButtons: List<Widget> = MotionPreviewMode.entries.map { mode ->
            OutlinedButton(
                text = mode.buttonLabel,
                onPressed = { selectMode(mode) },
                borderColor = if (mode == previewMode) Accent else Muted,
                key = "motion-mode-${mode.name}",
            )
        }
        val policyText: String = when (previewMode) {
            MotionPreviewMode.NORMAL -> "完整 token 时长；按住或悬停按钮观察反馈"
            MotionPreviewMode.REDUCE_MOTION -> "微状态即时完成；空间动效缩短为 fade"
            MotionPreviewMode.SCALE_ZERO -> "animator scale=0；所有终态同步提交"
        }
        return samplePanel(
            title = "Motion policy",
            color = Accent,
            child = Column(
                children = listOf(
                    Text("MOTION POLICY", style = TextStyle(color = Accent)),
                    Row(children = modeButtons, spacing = 2),
                    Text(policyText, style = TextStyle(color = Muted)),
                    Text("host tickers=${widget.env.vsync.activeTickerCount}", style = TextStyle(color = Yellow)),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** 将新策略注入同一个 retained 预览子树。 */
    private fun selectMode(mode: MotionPreviewMode) {
        if (previewMode == mode) return
        setState { previewMode = mode }
    }

    /** 按照反馈、选择和空间动效分区构建全部可交互样例。 */
    private fun previewContent(): Widget {
        return Column(
            children = listOf(
                buttonPanel(),
                selectionPanel(),
                slidablePanel(),
                popoverPanel(),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    /** 展示主按钮真实 pressed、hover、focus 三种微状态。 */
    private fun buttonPanel(): Widget {
        val focusStatus: String = if (buttonFocusNode.isFocused) "FOCUSED" else "NOT FOCUSED"
        return samplePanel(
            title = "Button feedback",
            color = Cyan,
            child = Column(
                children = listOf(
                    Text("BUTTON · PRESSED / HOVER / FOCUS", style = TextStyle(color = Cyan)),
                    Row(
                        children = listOf(
                            Focus(
                                node = buttonFocusNode,
                                child = OutlinedButton(
                                    text = "PRESS / HOVER",
                                    onPressed = {
                                        buttonPressCount += 1
                                        setState { Unit }
                                    },
                                    borderColor = Cyan,
                                    key = "motion-feedback-button",
                                ),
                                key = "motion-feedback-focus",
                            ),
                            OutlinedButton(
                                text = "FOCUS",
                                onPressed = {
                                    buttonFocusNode.requestFocus()
                                    setState { Unit }
                                },
                                borderColor = Yellow,
                                key = "motion-focus-request",
                            ),
                            OutlinedButton(
                                text = "CLEAR",
                                onPressed = {
                                    buttonFocusNode.unfocus()
                                    setState { Unit }
                                },
                                borderColor = Muted,
                                key = "motion-focus-clear",
                            ),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                    Text("$focusStatus · clicks=$buttonPressCount", style = TextStyle(color = Muted)),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** 展示 Switch、Slider 与 Tabs 的受控 selection 动画和即时逻辑状态。 */
    private fun selectionPanel(): Widget {
        val sliderPercent: Int = (sliderValue * 100f).toInt()
        return samplePanel(
            title = "Selection motion",
            color = Green,
            child = Column(
                children = listOf(
                    Text("SELECTION · SWITCH / SLIDER / TABS", style = TextStyle(color = Green)),
                    Row(
                        children = listOf(
                            Switch(
                                checked = switchChecked,
                                onChanged = { checked -> setState { switchChecked = checked } },
                                activeColor = Green,
                                key = "motion-switch",
                            ),
                            Text(if (switchChecked) "ON" else "OFF", style = TextStyle(color = Green)),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                    Slider(
                        value = sliderValue,
                        onDrag = { value -> setState { sliderValue = value } },
                        onRelease = { value -> setState { sliderValue = value } },
                        activeColor = Accent,
                        key = "motion-slider",
                    ),
                    Row(
                        children = listOf(
                            Text("$sliderPercent%", style = TextStyle(color = Accent)),
                            OutlinedButton(
                                text = "JUMP",
                                onPressed = {
                                    setState { sliderValue = if (sliderValue < 0.5f) 0.82f else 0.18f }
                                },
                                borderColor = Accent,
                                key = "motion-slider-jump",
                            ),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                    Tabs(
                        labels = listOf("ONE", "TWO", "THREE"),
                        selectedIndex = selectedTab,
                        onSelected = { index -> setState { selectedTab = index } },
                        key = "motion-tabs",
                    ),
                ),
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** 展示直接拖动与主题 spring settle 共用同一个 Host ticker。 */
    private fun slidablePanel(): Widget {
        val archivePane: SlidableActionPane = SlidableActionPane(
            children = listOf(
                SlidableAction(
                    label = "PIN",
                    backgroundColor = Blue,
                    foregroundColor = PixelColor.Black,
                    onPressed = { setState { slidableStatus = "PIN action" } },
                    key = "motion-slidable-pin",
                ),
            ),
            extentRatio = 0.32f,
            motion = SlidableMotion.DRAWER,
        )
        val deletePane: SlidableActionPane = SlidableActionPane(
            children = listOf(
                SlidableAction(
                    label = "MORE",
                    backgroundColor = Purple,
                    foregroundColor = PixelColor.Black,
                    onPressed = { setState { slidableStatus = "MORE action" } },
                    key = "motion-slidable-more",
                ),
            ),
            extentRatio = 0.32f,
            motion = SlidableMotion.SCROLL,
        )
        return samplePanel(
            title = "Slidable settle",
            color = Blue,
            child = Column(
                children = listOf(
                    Text("SLIDABLE · DRAG THEN RELEASE", style = TextStyle(color = Blue)),
                    Container(
                        width = 132,
                        height = 20,
                        child = Slidable(
                            startActionPane = archivePane,
                            endActionPane = deletePane,
                            onTap = { setState { slidableStatus = "row tapped" } },
                            child = Container(
                                height = 20,
                                padding = EdgeInsets.symmetric(horizontal = 3, vertical = 2),
                                fillColor = PixelColor.fromRgb(24, 34, 48),
                                borderColor = Blue,
                                child = Text("SWIPE THIS ROW", style = TextStyle(color = Blue)),
                            ),
                            key = "motion-slidable",
                        ),
                    ),
                    Text(slidableStatus, style = TextStyle(color = Muted)),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** 展示受控 Popover 的 enter、interruptible exit 与即时模式。 */
    private fun popoverPanel(): Widget {
        return samplePanel(
            title = "Popover transition",
            color = Pink,
            child = Column(
                children = listOf(
                    Text("POPOVER · ENTER / EXIT", style = TextStyle(color = Pink)),
                    Container(
                        width = 132,
                        height = 48,
                        child = Popover(
                            anchor = OutlinedButton(
                                text = if (popoverExpanded) "CLOSE POPOVER" else "OPEN POPOVER",
                                onPressed = { setState { popoverExpanded = !popoverExpanded } },
                                borderColor = Pink,
                                key = "motion-popover-anchor",
                            ),
                            content = Container(
                                width = 88,
                                padding = EdgeInsets.all(3),
                                fillColor = PixelColor.fromRgb(34, 14, 24),
                                borderColor = Pink,
                                child = Text("THEMED FADE", style = TextStyle(color = Pink)),
                                key = "motion-popover-content",
                            ),
                            expanded = popoverExpanded,
                            contentOffset = IntOffset(0, 16),
                            key = "motion-popover",
                        ),
                    ),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
        )
    }

    /** 清理全局焦点管理器对已离场 Demo 节点的引用。 */
    override fun dispose() {
        buttonFocusNode.unfocus()
    }
}
