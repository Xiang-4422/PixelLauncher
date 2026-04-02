package com.purride.pixelui

import android.content.Context
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout

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
): PixelHostSetup {
    val textInputBridge = PixelTextInputBridge(
        context = context,
        hostView = hostView,
    )
    hostView.hostBridge = textInputBridge
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
