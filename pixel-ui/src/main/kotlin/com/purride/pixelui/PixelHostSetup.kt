package com.purride.pixelui

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelTextRasterizer

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
 * profile、调色板、文本栅格器、主题和页面内容都可以一次性带进来。
 */
data class PixelHostSetupConfig(
    val profilePreference: PixelHostProfilePreference? = null,
    val palette: PixelPalette? = null,
    val textRasterizer: PixelTextRasterizer? = null,
    val themeData: ThemeData? = null,
    val textDirection: TextDirection = TextDirection.LTR,
    val content: (() -> Widget)? = null,
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
