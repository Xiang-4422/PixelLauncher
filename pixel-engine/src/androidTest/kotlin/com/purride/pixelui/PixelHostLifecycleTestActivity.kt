package com.purride.pixelui

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner

/**
 * 为 Host owner 重建与销毁验收提供真实 Activity Window。
 *
 * 该 Activity 显式实现 [LifecycleOwner]，使测试不依赖 AppCompat 或 ComponentActivity。
 */
class PixelHostLifecycleTestActivity : Activity(), LifecycleOwner {
    /** Activity 生命周期事件的可观察 registry。 */
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    /** instrumentation test 可重复移除和添加 Host 的根容器。 */
    lateinit var rootView: FrameLayout
        private set

    /** 自动发现当前 Activity ViewTree owner 的被测 Host。 */
    lateinit var hostView: PixelHostView
        private set

    /** 向 Host 暴露 Activity 的真实生命周期状态。 */
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    /** 创建带 ViewTree owner 的根容器和默认 Host。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        rootView = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@PixelHostLifecycleTestActivity)
        }
        hostView = PixelHostView(this)
        rootView.addView(
            hostView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(rootView)
    }

    /** 把 Activity start 映射到被测 ViewTree owner。 */
    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    /** 把 Activity resume 映射到被测 ViewTree owner。 */
    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** 在平台 pause 前暂停 Host 输入与动态渲染。 */
    override fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    /** 在平台 stop 前停止 Host 动态工作但保留 retained tree。 */
    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    /** 在 Window detach 前终结 owner，验证 observer 主动释放路径。 */
    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
