package com.purride.pixelcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Focus
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelHostServices
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Semantics
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget

/** 为 Compose wrapper 的重建、输入与 saved-state 验收提供真实 Activity。 */
class PixelComposeInteropTestActivity : ComponentActivity() {
    /** 当前 Activity 中由 Compose 创建的真实 Pixel Host。 */
    lateinit var hostView: PixelHostView
        private set

    /** 当前 Activity/Host 收到的精确文本输入负载。 */
    val receivedTextInputs: MutableList<String> = mutableListOf()

    /** 当前 Activity 创建序号，用于区分同实例重建与 saved-state 恢复。 */
    private val creationIndex: Int = nextCreationIndex()

    /** 创建 Compose 根并安装带 capability 探针的 Pixel retained 树。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** 每次 Activity 重建都创建新的 Engine，验证状态不依赖进程内 Engine 身份。 */
        val engine = PixelEngine.Builder().build()
        setContent {
            PixelHost(
                engine = engine,
                modifier = Modifier.fillMaxSize(),
                stateKey = "compose-interop-host",
                content = {
                    PixelComposeInteropProbeWidget(
                        creationIndex = creationIndex,
                        onTextInput = receivedTextInputs::add,
                    )
                },
                onHostReady = { host -> hostView = host },
            )
        }
    }

    /** 保存测试 Activity 的创建序号，并支持每个测试显式清零。 */
    companion object {
        /** 进程内最近分配的 Activity 创建序号。 */
        private var creationCount: Int = 0

        /** 返回新的单调 Activity 创建序号。 */
        @Synchronized
        private fun nextCreationIndex(): Int {
            creationCount += 1
            return creationCount
        }

        /** 为独立 instrumentation 用例重置创建序号。 */
        @Synchronized
        fun resetCreationCount() {
            creationCount = 0
        }
    }
}

/** 从 Pixel inherited scopes 读取 Compose wrapper 传递结果的 retained 探针。 */
private class PixelComposeInteropProbeWidget(
    /** 当前 Activity 创建序号。 */
    private val creationIndex: Int,
    /** 精确文本输入的测试接收器。 */
    private val onTextInput: (String) -> Unit,
) : StatelessWidget(key = "compose-interop-probe") {
    /** 读取密度与 saved-state capability，并返回带焦点和语义的可渲染节点。 */
    override fun build(context: BuildContext): Widget {
        /** Compose AndroidView Context 传递到 Host 的完整平台能力。 */
        val hostCapabilities = HostCapabilities.of(context)
        PixelComposeInteropProbe.density = hostCapabilities.density

        /** Engine-first、Compose saved-state fallback 后的有效 Host 服务。 */
        val hostServices = PixelHostServices.of(context)
        /** 当前 Host 已恢复的测试负载。 */
        val restored = hostServices.restoreState(PixelComposeInteropProbe.StateKey)
        if (creationIndex > 1 && restored is com.purride.pixelui.PixelCapabilityValueResult.Value) {
            PixelComposeInteropProbe.restoredPayload = restored.value?.copyOf()
        }
        if (restored is com.purride.pixelui.PixelCapabilityValueResult.Value && restored.value == null) {
            PixelComposeInteropProbe.initialSaveResult = hostServices.saveState(
                PixelComposeInteropProbe.StateKey,
                PixelComposeInteropProbe.StatePayload,
            )
        }

        return Focus(
            autofocus = true,
            key = "compose-interop-focus",
            onTextInput = { event ->
                onTextInput(event.text)
                true
            },
            child = Semantics(
                label = PixelComposeInteropProbe.AccessibilityLabel,
                role = PixelSemanticRole.TEXT,
                excludeDescendants = true,
                child = Text("COMPOSE"),
            ),
        )
    }
}

/** 跨 Activity 重建保留的 Compose wrapper 黑盒探针值。 */
internal object PixelComposeInteropProbe {
    /** 保存状态使用的稳定业务 key。 */
    const val StateKey: String = "interop-state"

    /** 无障碍树预期暴露的稳定标签。 */
    const val AccessibilityLabel: String = "Compose Pixel Host"

    /** 首次构建写入、重建后必须恢复的字节负载。 */
    val StatePayload: ByteArray = byteArrayOf(7, 14, 21, 28)

    /** Pixel Host 观察到的 Android density。 */
    var density: Float? = null

    /** 首次保存 capability 返回结果。 */
    var initialSaveResult: PixelCapabilityResult? = null

    /** 第二个 Activity 实例通过 rememberSaveable 恢复的负载。 */
    var restoredPayload: ByteArray? = null

    /** 清除上一个 instrumentation 用例留下的进程内探针。 */
    fun reset() {
        density = null
        initialSaveResult = null
        restoredPayload = null
    }
}
