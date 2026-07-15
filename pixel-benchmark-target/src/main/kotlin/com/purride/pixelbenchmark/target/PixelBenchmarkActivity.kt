package com.purride.pixelbenchmark.target

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.widgets.animated.AnimatedContainer
import kotlin.time.Duration.Companion.milliseconds

/**
 * 仅供 Macrobenchmark 与 Baseline Profile 测试使用的近似 Release 消费者 Activity。
 *
 * 所有场景都通过 SDK 公共接口构建，因此基准结果覆盖真实 Android Host、retained runtime、
 * layout、paint、Canvas 提交、输入和可访问性路径。
 */
class PixelBenchmarkActivity : AppCompatActivity() {
    /** 终态生命周期由当前 Activity 实例持有的 Host 配置。 */
    private lateinit var hostSetup: PixelHostSetup

    /** 创建请求的确定性基准场景，并挂载生产 Host。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** 跨进程基准 Intent 提供的场景名称。 */
        val scenarioName = intent.getStringExtra(ScenarioExtra)
        /** 严格解析后的场景；未知名称按约定回退到启动页面。 */
        val scenario = BenchmarkScenario.fromWireName(scenarioName)
        /** 只由独立归因基准显式开启的 Host 分阶段 trace 开关。 */
        val frameDiagnosticsEnabled = intent.getBooleanExtra(FrameDiagnosticsExtra, false)
        hostSetup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                profilePreference = PixelHostProfilePreference(dotSizePx = LogicalPixelSize),
            ),
        )
        /** 每次进程启动只选择一次的根 Widget，用于隔离各次测量。 */
        val benchmarkRoot = scenario.createRoot(hostSetup)
        hostSetup.hostView.frameDiagnosticsEnabled = frameDiagnosticsEnabled
        hostSetup.hostView.setContent { benchmarkRoot }
        setContentView(hostSetup.rootView)
        // 注册当前真实 Host，供显式长跑诊断广播在主线程执行终态释放和资源计数。
        PixelBenchmarkHostRegistry.register(hostSetup)
    }

    /** 在 Activity 终态边界释放 Host、ticker 和文本输入所有权。 */
    override fun onDestroy() {
        if (::hostSetup.isInitialized) {
            PixelBenchmarkHostRegistry.disposeAndUnregister(hostSetup)
        }
        super.onDestroy()
    }

    /** 与外部基准模块共享的稳定协议常量。 */
    private companion object {
        /** 用于选择一个确定性关键用户旅程的 Intent extra。 */
        const val ScenarioExtra: String = "pixel.benchmark.SCENARIO"

        /** 仅供独立 Perfetto 归因测试开启 Host 分阶段 trace 的 Intent extra。 */
        const val FrameDiagnosticsExtra: String = "pixel.benchmark.FRAME_DIAGNOSTICS"

        /** 基准目标中每个引擎逻辑像素对应的物理像素数。 */
        const val LogicalPixelSize: Int = 4
    }
}

/** 可通过 Activity Intent 契约寻址的确定性基准页面。 */
private enum class BenchmarkScenario(
    /** 跨进程基准接受的稳定小写协议值。 */
    val wireName: String,
) {
    /** 用于冷、热启动测量的最小启动页面。 */
    Startup("startup"),

    /** 用于重复手势驱动帧测量的大型懒列表。 */
    ListScroll("list_scroll"),

    /** 生产 TextField 与 Android 输入桥接页面。 */
    TextInput("text_input"),

    /** 由 Host ticker 驱动的 retained 隐式动画页面。 */
    Animation("animation"),

    /** 真实 Navigator push 转场页面。 */
    PageTransition("page_transition"),

    /** 真实模态 Overlay 组合页面。 */
    Overlay("overlay");

    /** 为当前场景构建公共 SDK Widget 树。 */
    fun createRoot(setup: PixelHostSetup): Widget {
        return when (this) {
            Startup -> benchmarkFrame("PIXEL BENCHMARK READY", Text("STARTUP READY"))
            ListScroll -> BenchmarkListWidget()
            TextInput -> BenchmarkTextInputWidget()
            Animation -> BenchmarkAnimationWidget(setup)
            PageTransition -> benchmarkNavigation(setup)
            Overlay -> BenchmarkOverlayWidget()
        }
    }

    /** 场景解析器通过回退启动页来拒绝意外别名。 */
    companion object {
        /** 将可空 Intent 值解析为一个已知基准页面。 */
        fun fromWireName(value: String?): BenchmarkScenario {
            return entries.firstOrNull { scenario -> scenario.wireName == value } ?: Startup
        }
    }
}

/** 在一个基准主体外添加稳定标题、内边距和边框。 */
private fun benchmarkFrame(title: String, body: Widget): Widget {
    return Container(
        padding = EdgeInsets.all(3),
        borderColor = PixelColor.White,
        child = Column(
            children = listOf(Text(title), body),
            spacing = 3,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

/** 持有 5,000 行懒列表基准所需的 controller/state 对。 */
private class BenchmarkListWidget : StatefulWidget() {
    /** 为当前迭代的所有手势帧创建一个 retained 列表状态。 */
    override fun createState(): State<out StatefulWidget> = BenchmarkListState()
}

/** 在 Host 重建期间保留滚动偏移和懒列表 controller。 */
private class BenchmarkListState : State<BenchmarkListWidget>() {
    /** 由 Android 指针手势驱动的公共列表 controller。 */
    private val listController: PixelListController = PixelListController()

    /** 每次滚动旅程都会改变偏移的可变列表状态。 */
    private val listState = listController.create()

    /** 构建足够大的定高懒列表，避免采样期间到达末尾。 */
    override fun build(context: BuildContext): Widget {
        return benchmarkFrame(
            title = "LIST SCROLL BENCHMARK",
            body = Container(
                height = 120,
                borderColor = PixelColor.White,
                child = ListViewBuilder(
                    itemCount = BenchmarkRowCount,
                    state = listState,
                    controller = listController,
                    itemExtent = BenchmarkRowExtent,
                    cacheExtent = BenchmarkCacheExtent,
                    itemBuilder = { index -> Text("ROW ${index.toString().padStart(4, '0')}") },
                ),
            ),
        )
    }

    /** 稳定的懒列表工作负载常量。 */
    private companion object {
        /** 以虚拟方式保留而非提前构造的总行数。 */
        const val BenchmarkRowCount: Int = 5_000

        /** 定高快速路径中每一行的逻辑高度。 */
        const val BenchmarkRowExtent: Int = 8

        /** 视口上下方保留的离屏行数。 */
        const val BenchmarkCacheExtent: Int = 3
    }
}

/** 持有 Android IME/可访问性测量所需的生产编辑状态与 controller。 */
private class BenchmarkTextInputWidget : StatefulWidget() {
    /** 每次基准进程启动创建一个 retained 编辑会话。 */
    override fun createState(): State<out StatefulWidget> = BenchmarkTextInputState()
}

/** 在测量输入动作时保持文本、选区、组合区和焦点稳定。 */
private class BenchmarkTextInputState : State<BenchmarkTextInputWidget>() {
    /** 接收可访问性与 InputConnection 命令的 SDK 编辑 controller。 */
    private val editingController: TextEditingController = TextEditingController()

    /** 用于覆盖段落、光标与选区布局的初始多行文本。 */
    private val editingState: PixelTextFieldState = PixelTextFieldState(
        initialText = "ALPHA BRAVO\nABC אבג 123\nCAFÉ 👨‍👩‍👧‍👦",
    )

    /** 构建带稳定语义标签的生产 TextField，供 UiAutomator 定位。 */
    override fun build(context: BuildContext): Widget {
        return benchmarkFrame(
            title = "TEXT INPUT BENCHMARK",
            body = TextField(
                state = editingState,
                controller = editingController,
                placeholder = "TYPE HERE",
                semanticLabel = "BENCHMARK INPUT",
                minLines = 4,
                maxLines = 4,
            ),
        )
    }
}

/** 由可访问按钮控制的 retained 隐式动画页面。 */
private class BenchmarkAnimationWidget(
    /** 提供生产渲染所用精确 ticker 的 Host 配置。 */
    private val setup: PixelHostSetup,
) : StatefulWidget() {
    /** 创建用于启动每次测量动画的 retained 切换状态。 */
    override fun createState(): State<out StatefulWidget> = BenchmarkAnimationState()

    /** 所有动画子节点共用的生产 Host ticker。 */
    val vsync
        get() = setup.hostView.tickerProvider
}

/** 交替切换几何属性，确保每次按钮激活都产生完整 ticker 驱动动画。 */
private class BenchmarkAnimationState : State<BenchmarkAnimationWidget>() {
    /** 当前目标几何状态，每次外部基准动作切换一次。 */
    private var expanded: Boolean = false

    /** 构建由 FrameTimingMetric 测量的按钮与多个动画表面。 */
    override fun build(context: BuildContext): Widget {
        return benchmarkFrame(
            title = "ANIMATION BENCHMARK",
            body = Column(
                children = listOf(
                    OutlinedButton(
                        text = "ANIMATE",
                        onPressed = { setState { expanded = !expanded } },
                    ),
                    AnimatedContainer(
                        duration = AnimationDuration,
                        vsync = widget.vsync,
                        width = if (expanded) 96 else 32,
                        height = if (expanded) 24 else 10,
                        padding = EdgeInsets.all(if (expanded) 5 else 1),
                        borderColor = if (expanded) PixelColor.White else CompactBorderColor,
                        child = Text(if (expanded) "EXPANDED" else "COMPACT"),
                    ),
                ),
                spacing = 4,
                crossAxisAlignment = CrossAxisAlignment.START,
            ),
        )
    }

    /** 足以产生多个测量帧的稳定动画时长配置。 */
    private companion object {
        /** 几何、内边距和边框插值共用的时长。 */
        val AnimationDuration = 700.milliseconds

        /** 紧凑动画端点使用的低饱和不透明边框色。 */
        val CompactBorderColor: PixelColor = PixelColor(0xFF808080.toInt())
    }
}

/** 构建真实 Navigator，其根按钮执行被测动画 push。 */
private fun benchmarkNavigation(setup: PixelHostSetup): Widget {
    /** 被测 push 转场后展示的目标路由。 */
    val detailsRoute = PixelRoute(
        name = "benchmark-details",
        transition = PixelRouteTransition.SlideHorizontal,
        builder = { benchmarkFrame("PAGE TRANSITION BENCHMARK", Text("DETAILS READY")) },
    )
    /** 按钮会解析最近生产 Navigator 状态的根路由。 */
    val rootRoute = PixelRoute(
        name = "benchmark-root",
        transition = PixelRouteTransition.None,
        builder = { context ->
            benchmarkFrame(
                title = "PAGE TRANSITION BENCHMARK",
                body = OutlinedButton(
                    text = "OPEN DETAILS",
                    onPressed = { PixelNavigator.of(context).push(detailsRoute) },
                ),
            )
        },
    )
    return PixelNavigator(
        initialRoute = rootRoute,
        vsync = setup.hostView.tickerProvider,
        transitionDuration = NavigationDuration,
    )
}

/** 被测 Navigator push 使用的转场时长。 */
private val NavigationDuration = 700.milliseconds

/** 持有生产 Overlay controller，并暴露一个确定性模态动作。 */
private class BenchmarkOverlayWidget : StatefulWidget() {
    /** 创建 retained controller，使已展示路由在父节点重建时保持。 */
    override fun createState(): State<out StatefulWidget> = BenchmarkOverlayState()
}

/** 通过 PixelOverlayHost 展示真实模态 Dialog 以测量组合开销。 */
private class BenchmarkOverlayState : State<BenchmarkOverlayWidget>() {
    /** 负责保留活动展示和移除 Overlay 展示的路由 controller。 */
    private val overlayController: PixelOverlayController = PixelOverlayController()

    /** 在匹配的生产 Overlay Host 下构建路由触发器。 */
    override fun build(context: BuildContext): Widget {
        /** 模态 barrier 下方用于验证真实 Overlay 组合的内容。 */
        val content = benchmarkFrame(
            title = "OVERLAY BENCHMARK",
            body = OutlinedButton(
                text = "SHOW OVERLAY",
                onPressed = ::showOverlay,
            ),
        )
        return PixelOverlayHost(controller = overlayController, child = content)
    }

    /** 打开一个标题作为基准完成哨兵的标准 Dialog。 */
    private fun showOverlay() {
        overlayController.showDialog(
            title = Text("OVERLAY VISIBLE"),
            content = Text("MODAL CONTENT"),
        )
    }
}
