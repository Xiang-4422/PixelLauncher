package com.purride.pixelui

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelTextRasterizer
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
data class PixelHostSetup(
    val rootView: FrameLayout,
    val hostView: PixelHostView,
    val textInputBridge: PixelTextInputBridge,
)

/**
 * 默认宿主配置。
 *
 * 这层把业务页面最常见的宿主初始化项收成一个对象：
 * profile、调色板、文本栅格器、主题和根组件提供器都可以一次性带进来。
 */
data class PixelHostSetupConfig(
    val profilePreference: PixelHostProfilePreference? = null,
    val palette: PixelPalette? = null,
    val textRasterizer: PixelTextRasterizer? = null,
    val themeData: ThemeData? = null,
    val textDirection: TextDirection = TextDirection.LTR,
    val content: RootWidgetProvider? = null,
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
 * 2. 连接默认的 `PixelTextInputBridge`
 * 3. 返回已经装好宿主视图和隐藏输入框的根容器
 */
fun createPixelHostSetup(
    context: Context,
    hostView: PixelHostView = PixelHostView(context),
    config: PixelHostSetupConfig = PixelHostSetupConfig(),
): PixelHostSetup {
    val textInputBridge = PixelTextInputBridge(
        context = context,
        hostView = hostView,
    )
    hostView.hostBridge = textInputBridge
    config.profilePreference?.let { hostView.profilePreference = it }
    config.palette?.let { hostView.setPalette(it) }
    config.textRasterizer?.let { hostView.textRasterizer = it }
    hostView.themeData = config.themeData
    hostView.textDirection = config.textDirection
    hostView.pagerGesturePolicy = config.pagerGesturePolicy
    hostView.nestedScrollPolicy = config.nestedScrollPolicy
    hostView.scrollPhysics = config.scrollPhysics
    hostView.frameScheduler = config.frameScheduler
    config.content?.let { hostView.setContent(it) }
    val rootView = FrameLayout(context).apply {
        addView(
            hostView,
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
        addView(
            textInputBridge.inputView,
            FrameLayout.LayoutParams(1, WRAP_CONTENT),
        )
    }
    return PixelHostSetup(
        rootView = rootView,
        hostView = hostView,
        textInputBridge = textInputBridge,
    )
}
