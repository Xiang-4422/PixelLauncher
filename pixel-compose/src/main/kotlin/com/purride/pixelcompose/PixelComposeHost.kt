package com.purride.pixelcompose

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelSavedStateCapability
import com.purride.pixelui.RootWidgetProvider
import com.purride.pixelui.createPixelHostSetup

/** Compose 保存状态中单个 Pixel Host 允许持有的最大 key 数。 */
private const val MaxSavedStateEntryCount: Int = 64

/** 单个 Pixel Host 保存状态值允许占用的最大字节数。 */
private const val MaxSavedStateValueBytes: Int = 256 * 1024

/** 单个 Pixel Host 全部保存状态允许占用的最大字节数。 */
private const val MaxSavedStateTotalBytes: Int = 1024 * 1024

/** 保存状态 key 的最大 UTF-16 长度。 */
private const val MaxSavedStateKeyLength: Int = 160

/**
 * 在 Compose 布局中托管一个完整的 Pixel Host。
 *
 * [content] 只构建 Pixel SDK 的 retained [com.purride.pixelui.Widget] 树；本适配器不支持把任意
 * `@Composable` 内容嵌入 retained pixel render tree。生命周期、焦点、IME 与无障碍继续由真实
 * Android View 树承载，WindowInsets 会转发给 [PixelHostView]，保存状态则通过 Compose
 * `rememberSaveable` 作为缺省 capability 注入。调用方在 [engine] 中显式配置的 saved-state
 * capability 始终优先。
 *
 * @param engine 绑定到当前 Host 的 Engine 实例。
 * @param content 每次 Host 构建根树时返回 Pixel Widget 的非 Composable provider。
 * @param modifier 施加到 AndroidView 容器的 Compose Modifier。
 * @param stateKey 当同一组合位置切换业务 Host 时用于重置其保存状态的稳定键。
 * @param config 普通 Android Host 的装配配置；其中 `content` 字段会被本函数的 [content] 覆盖。
 * @param onHostReady Host 创建或重组更新后回调，便于业务接线和黑盒测试。
 */
@Composable
public fun PixelHost(
    engine: PixelEngine,
    content: RootWidgetProvider,
    modifier: Modifier = Modifier,
    stateKey: String = "pixel-host",
    config: PixelHostSetupConfig = PixelHostSetupConfig(),
    onHostReady: (PixelHostView) -> Unit = {},
) {
    require(stateKey.isNotBlank()) { "Pixel Compose Host stateKey must not be blank." }
    /** 由 Compose SavedStateRegistry 保存并在 Activity/进程恢复后重建的能力实例。 */
    val savedStateCapability = rememberSaveable(
        stateKey,
        saver = PixelComposeSavedStateCapability.Saver,
    ) {
        PixelComposeSavedStateCapability()
    }
    /** 只在原 Engine 缺失 saved-state capability 时补入 Compose 状态存储。 */
    val composeEngine = remember(engine, savedStateCapability) {
        engine.withHostServicesFallback(
            PixelHostCapabilitySet(savedState = savedStateCapability),
        )
    }
    /** 始终指向最近一次重组回调，避免 AndroidView 持有陈旧业务闭包。 */
    val currentOnHostReady = rememberUpdatedState(onHostReady)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PixelComposeHostContainer(
                context = context,
                engine = composeEngine,
                config = config,
                content = content,
                onHostReady = currentOnHostReady.value,
            )
        },
        update = { container ->
            container.update(
                engine = composeEngine,
                config = config,
                content = content,
                onHostReady = currentOnHostReady.value,
            )
        },
        onRelease = PixelComposeHostContainer::dispose,
    )
}

/** 持有普通 Android Host setup，并把 Compose View 事件桥接给它的内部容器。 */
internal class PixelComposeHostContainer(
    context: Context,
    engine: PixelEngine,
    config: PixelHostSetupConfig,
    content: RootWidgetProvider,
    onHostReady: (PixelHostView) -> Unit,
) : FrameLayout(context) {
    /** 当前容器唯一拥有且必须在 onRelease 终结的 Host 装配。 */
    private val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        engine = engine,
        config = config.copy(content = content),
    )

    /** 上次安装到 retained Host 的根 provider 引用。 */
    private var installedContent: RootWidgetProvider = content

    /** 上次用于根 `PixelBackHost` 的 dispatcher 引用。 */
    private var installedBackDispatcher = config.backDispatcher

    /** 防止 Compose 多次 onRelease 导致重复终结同一 retained runtime。 */
    private var disposed: Boolean = false

    init {
        addView(setup.rootView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        onHostReady(setup.hostView)
    }

    /** 把最近一次重组的 Engine、owner、配置与 Pixel 根树同步到现有 Host。 */
    fun update(
        engine: PixelEngine,
        config: PixelHostSetupConfig,
        content: RootWidgetProvider,
        onHostReady: (PixelHostView) -> Unit,
    ) {
        if (disposed) return
        /** 当前复用的真实 Android Pixel Host。 */
        val host = setup.hostView
        host.bindEngine(engine)
        host.profilePreference = config.profilePreference
        host.bezelColor = config.bezelColor
        config.textRasterizer?.let { rasterizer -> host.textRasterizer = rasterizer }
        host.textDirection = config.textDirection
        host.pagerGesturePolicy = config.pagerGesturePolicy
        host.nestedScrollPolicy = config.nestedScrollPolicy
        host.scrollPhysics = config.scrollPhysics
        host.backDispatcher = config.backDispatcher
        host.onUnhandledBack = config.onUnhandledBack
        if (installedContent !== content || installedBackDispatcher !== config.backDispatcher) {
            installedContent = content
            installedBackDispatcher = config.backDispatcher
            host.setContent {
                PixelBackHost(
                    dispatcher = config.backDispatcher,
                    child = content(),
                )
            }
        }
        onHostReady(host)
    }

    /** 显式把 Compose AndroidView 收到的 WindowInsets 转发给真实 Pixel Host。 */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        setup.hostView.dispatchApplyWindowInsets(insets)
        return insets
    }

    /** 配置或 density 改变时让 Host 重新读取平台能力并请求最新 WindowInsets。 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setup.hostView.requestApplyInsets()
    }

    /** 终结输入桥、retained runtime 与全部 Host 私有资源。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        setup.dispose()
        removeAllViews()
    }
}

/** 由 Compose rememberSaveable 持有的有界、防御性复制状态 capability。 */
internal class PixelComposeSavedStateCapability private constructor(
    /** 当前 Host 独占的 key 到原始状态映射。 */
    private val values: MutableMap<String, ByteArray>,
) : PixelSavedStateCapability {
    /** 创建空的 Compose Host 状态存储。 */
    constructor() : this(linkedMapOf())

    /** 返回指定 key 的防御性副本；不存在时返回 null。 */
    override fun restore(key: String): ByteArray? {
        validateKey(key)
        return synchronized(values) { values[key]?.copyOf() }
    }

    /** 保存防御性副本，并拒绝超过单值、条目或总量上限的状态。 */
    override fun save(key: String, value: ByteArray): PixelCapabilityResult {
        return runCatching {
            validateKey(key)
            require(value.size <= MaxSavedStateValueBytes) {
                "Pixel Compose saved state value exceeds $MaxSavedStateValueBytes bytes."
            }
            synchronized(values) {
                /** 替换旧值后预计占用的总字节数。 */
                val projectedBytes = values.values.sumOf(ByteArray::size) -
                    (values[key]?.size ?: 0) + value.size
                require(key in values || values.size < MaxSavedStateEntryCount) {
                    "Pixel Compose saved state exceeds $MaxSavedStateEntryCount entries."
                }
                require(projectedBytes <= MaxSavedStateTotalBytes) {
                    "Pixel Compose saved state exceeds $MaxSavedStateTotalBytes total bytes."
                }
                values[key] = value.copyOf()
            }
            PixelCapabilityResult.Handled
        }.getOrElse { cause ->
            PixelCapabilityResult.Failed(capability = "savedState", cause = cause)
        }
    }

    /** 删除指定 key；缺失 key 也视为幂等成功。 */
    override fun remove(key: String): PixelCapabilityResult {
        return runCatching {
            validateKey(key)
            synchronized(values) { values.remove(key) }
            PixelCapabilityResult.Handled
        }.getOrElse { cause ->
            PixelCapabilityResult.Failed(capability = "savedState", cause = cause)
        }
    }

    /** 把当前映射编码为只含 String 与 ByteArray 的 Android Bundle。 */
    private fun toBundle(): Bundle {
        return Bundle().apply {
            synchronized(values) {
                values.toSortedMap().forEach { (key, value) -> putByteArray(key, value.copyOf()) }
            }
        }
    }

    /** 检查业务 key 能安全进入有界 Bundle。 */
    private fun validateKey(key: String) {
        require(key.isNotBlank()) { "Pixel Compose saved state key must not be blank." }
        require(key.length <= MaxSavedStateKeyLength) {
            "Pixel Compose saved state key exceeds $MaxSavedStateKeyLength UTF-16 code units."
        }
    }

    /** 保存与恢复 capability 的稳定 Compose Saver。 */
    internal companion object {
        /** 由 Compose SavedStateRegistry 调用的 Bundle 编解码器。 */
        val Saver: Saver<PixelComposeSavedStateCapability, Bundle> = Saver(
            save = { capability -> capability.toBundle() },
            restore = { bundle ->
                /** 从 Bundle 读取并防御性复制后的稳定有序映射。 */
                val restoredValues = linkedMapOf<String, ByteArray>()
                bundle.keySet().sorted().forEach { key ->
                    bundle.getByteArray(key)?.let { value -> restoredValues[key] = value.copyOf() }
                }
                PixelComposeSavedStateCapability(restoredValues)
            },
        )
    }
}
