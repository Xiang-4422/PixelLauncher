package com.purride.pixelui

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.gesture.NestedScrollGesturePolicy
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.PixelScrollPhysics

/**
 * 宿主级装配结果。
 *
 * 这层把 `PixelHostView`、默认文本输入桥接和根容器收在一起，
 * 让业务页面不需要每次都手写同一套 `FrameLayout + hidden EditText` 样板。
 */
public data class PixelHostSetup(
    val rootView: FrameLayout,
    val hostView: PixelHostView,
    val textInputBridge: PixelTextInputBridge,
    val backDispatcher: PixelBackDispatcher,
) {
    /**
     * 显式释放默认宿主装配持有的输入桥接和渲染 runtime。
     *
     * 普通 View detach 只会暂停并保留 retained tree；Activity/Fragment 或自定义宿主应在
     * owner 终态或明确销毁视图时调用它。
     */
    public fun dispose() {
        textInputBridge.hideTextInput()
        hostView.dispose()
    }
}

/**
 * 默认宿主配置。
 *
 * 引擎只做一件事：把 widget 树渲染成 ARGB 像素网格。
 * 屏幕外框颜色通过 [bezelColor] 控制；不再有 palette / themeData / colorMode 概念。
 */
public data class PixelHostSetupConfig(
    /**
     * 逻辑屏幕解析策略；null 表示保留 Host 当前策略。
     *
     * 这是 setup 层唯一的 profile 配置入口，固定网格与自适应网格都通过它表达。
     */
    val profilePolicy: PixelHostProfilePolicy? = null,
    val bezelColor: PixelColor = PixelColor.Black,
    val textRasterizer: PixelTextRasterizer? = null,
    /** 完整 Host 环境快照覆盖；null 表示跟随 Android 平台配置。 */
    val capabilitiesOverride: HostCapabilitiesData? = null,
    val content: RootWidgetProvider? = null,
    /**
     * 宿主 back 事件调度器。默认 setup 会用它包一层 [PixelBackHost]。
     */
    val backDispatcher: PixelBackDispatcher = PixelBackDispatcher(),
    /**
     * widget back 栈未消费时的 app fallback。
     */
    val onUnhandledBack: (() -> Boolean)? = null,
    /**
     * 分页拖动启动策略。默认按主轴位移 > touchSlop 且 > 次轴位移 * 1.2 启动。
     * 业务可继承 [PagerGesturePolicy] 重写 shouldStartDrag 提供更激进/保守策略。
     */
    val pagerGesturePolicy: PagerGesturePolicy = PagerGesturePolicy.Default,
    /**
     * Pager + List 嵌套时的手势仲裁策略。
     */
    val nestedScrollPolicy: NestedScrollGesturePolicy = NestedScrollGesturePolicy.Default,
    /**
     * 列表/单子节点 ScrollView 的滚动物理参数。
     */
    val scrollPhysics: PixelScrollPhysics = PixelScrollPhysics.Default,
    /**
     * 帧调度器。默认走 Android Choreographer；测试或替代宿主可注入
     * `ManualFrameScheduler` 或自定义实现。
     */
    val frameScheduler: PixelFrameScheduler = PixelFrameScheduler.Default,
)

/**
 * 创建默认宿主装配。
 *
 * 当前会完成三件事：
 * 1. 创建或接收一个 `PixelHostView`
 * 2. 把默认 Android 编辑器的 IME、剪贴板、震动能力补进 [engine] 的 typed capability 集合
 * 3. 返回已经装好宿主视图和隐藏输入框的根容器
 *
 * 平台能力只作为 fallback 补缺：调用方在 [engine] 上显式声明的同名 capability 始终优先。
 */
public fun createPixelHostSetup(
    context: Context,
    engine: PixelEngine = PixelEngine.Builder().build(),
    hostView: PixelHostView = PixelHostView(context),
    config: PixelHostSetupConfig = PixelHostSetupConfig(),
): PixelHostSetup {
    val textInputBridge = PixelTextInputBridge(
        context = context,
        hostView = hostView,
    )
    /** 默认 Android 宿主按 SDK 契约提供的平台能力。 */
    val platformCapabilities = PixelHostCapabilitySet(
        ime = textInputBridge,
        clipboard = textInputBridge,
        haptic = textInputBridge,
    )
    hostView.engine = engine.withHostServicesFallback(platformCapabilities)
    config.profilePolicy?.let { hostView.profilePolicy = it }
    config.textRasterizer?.let { hostView.textRasterizer = it }
    hostView.bezelColor = config.bezelColor
    config.capabilitiesOverride?.let { snapshot -> hostView.capabilitiesOverride = snapshot }
    hostView.pagerGesturePolicy = config.pagerGesturePolicy
    hostView.nestedScrollPolicy = config.nestedScrollPolicy
    hostView.scrollPhysics = config.scrollPhysics
    hostView.frameScheduler = config.frameScheduler
    hostView.backDispatcher = config.backDispatcher
    hostView.onUnhandledBack = config.onUnhandledBack
    config.content?.let { provider ->
        hostView.setContent {
            PixelBackHost(
                dispatcher = config.backDispatcher,
                child = provider(),
            )
        }
    }
    val rootView = FrameLayout(context).apply {
        addView(
            hostView,
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
        addView(
            textInputBridge.inputView,
            // 隐藏编辑器不展示文本；固定 1×1 可保留可见焦点锚点，并跳过 WRAP_CONTENT 字体测量。
            FrameLayout.LayoutParams(1, 1),
        )
    }
    return PixelHostSetup(
        rootView = rootView,
        hostView = hostView,
        textInputBridge = textInputBridge,
        backDispatcher = config.backDispatcher,
    )
}
