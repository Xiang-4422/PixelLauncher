package com.purride.pixelui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.purride.pixelcore.PixelColor

/** 为真实 Fragment view lifecycle 与多 Host 验收提供 AndroidX Activity。 */
class PixelHostFragmentTestActivity : FragmentActivity() {
    /** Fragment 或多个 Host 共用的已 attach 根容器。 */
    lateinit var rootView: FrameLayout
        private set

    /** 创建具有稳定 View id 的 Fragment 容器。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = FrameLayout(this).apply {
            id = View.generateViewId()
        }
        setContentView(rootView)
    }
}

/** 使用真实 [viewLifecycleOwner] 托管一个 [PixelHostView] 的测试 Fragment。 */
internal class PixelHostViewLifecycleFragment : Fragment() {
    /** 每次 Fragment view 重建时记录 retained State 身份的测试探针。 */
    lateinit var tracker: FragmentLifecycleProbeTracker

    /** 当前 Fragment view 对应的 Host；`onDestroyView` 后清空。 */
    private var hostView: PixelHostView? = null

    /** 创建新的 Host view；不手动绑定 owner，让 Host 自动发现 Fragment view owner。 */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return PixelHostView(requireContext()).also { host ->
            host.setContent { FragmentLifecycleProbeWidget(tracker) }
            hostView = host
        }
    }

    /** 清除 Fragment 对旧 Host 的强引用，但不手动调用 destroy。 */
    override fun onDestroyView() {
        hostView = null
        super.onDestroyView()
    }

    /** 返回当前 view lifecycle 对应的 Host，view 尚未创建或已销毁时失败。 */
    fun requireHostView(): PixelHostView {
        return checkNotNull(hostView) { "Fragment Host view is not currently created" }
    }
}

/** 记录 Fragment view 每一代 retained State 及总释放次数。 */
internal class FragmentLifecycleProbeTracker {
    /** 按 view 创建顺序保存的独立 State 实例。 */
    val states: MutableList<FragmentLifecycleProbeState> = mutableListOf()

    /** 所有 State 收到 terminal dispose 的累计次数。 */
    var totalDisposeCount: Int = 0
}

/** Fragment Host 内用于观察 view 重建边界的 retained widget。 */
internal class FragmentLifecycleProbeWidget(
    /** 接收 State 身份与释放事件的共享探针。 */
    val tracker: FragmentLifecycleProbeTracker,
) : StatefulWidget() {
    /** 为每一代 Fragment view 创建新的 State。 */
    override fun createState(): State<out StatefulWidget> = FragmentLifecycleProbeState()
}

/** 记录单个 Fragment view 对应 State 的创建与唯一 dispose。 */
internal class FragmentLifecycleProbeState : State<FragmentLifecycleProbeWidget>() {
    /** 当前 State 收到 terminal dispose 的次数。 */
    var disposeCount: Int = 0
        private set

    /** 暴露本代 State 身份供 instrumentation 验证不会跨 view 复用。 */
    override fun initState() {
        widget.tracker.states += this
    }

    /** 构建一个最小固定像素节点以挂载 retained tree。 */
    override fun build(context: BuildContext): Widget {
        return Container(
            width = 4,
            height = 4,
            fillColor = PixelColor.White,
            borderColor = null,
        )
    }

    /** 记录当前 view generation 的 terminal dispose。 */
    override fun dispose() {
        disposeCount += 1
        widget.tracker.totalDisposeCount += 1
    }
}
