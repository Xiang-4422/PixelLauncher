package com.purride.pixelui.internal.host

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout

/** 为真实系统边缘返回手势提供一个无业务逻辑的全屏测试 Window。 */
class PredictiveBackTestActivity : Activity() {
    /** instrumentation test 可替换内容的全屏根容器。 */
    lateinit var rootView: FrameLayout
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = FrameLayout(this)
        setContentView(rootView)
    }
}
