package com.purride.pixelui

import com.purride.pixelui.internal.host.PixelHostCapabilityScope

/** 可选 Host capability 的统一调用结果。 */
public sealed interface PixelCapabilityResult {
    /** Capability 已接受并完成请求。 */
    public object Handled : PixelCapabilityResult

    /**
     * 当前 Host 不支持请求。
     *
     * @property capability 缺失的稳定 capability 名称。
     * @property reason 面向开发者的降级说明。
     */
    public data class Unsupported(
        public val capability: String,
        public val reason: String,
    ) : PixelCapabilityResult

    /**
     * Capability 存在但执行失败。
     *
     * @property capability 失败的稳定 capability 名称。
     * @property cause 保留堆栈的原始异常。
     */
    public data class Failed(
        public val capability: String,
        public val cause: Throwable,
    ) : PixelCapabilityResult
}

/** 带返回值 capability 的统一调用结果。 */
public sealed interface PixelCapabilityValueResult<out T> {
    /**
     * Capability 已成功返回值。
     *
     * @property value 返回值；可空类型可以用 null 表达“支持但没有内容”。
     */
    public data class Value<T>(
        public val value: T,
    ) : PixelCapabilityValueResult<T>

    /**
     * 当前 Host 不支持请求。
     *
     * @property capability 缺失的稳定 capability 名称。
     * @property reason 面向开发者的降级说明。
     */
    public data class Unsupported(
        public val capability: String,
        public val reason: String,
    ) : PixelCapabilityValueResult<Nothing>

    /**
     * Capability 存在但读取失败。
     *
     * @property capability 失败的稳定 capability 名称。
     * @property cause 保留堆栈的原始异常。
     */
    public data class Failed(
        public val capability: String,
        public val cause: Throwable,
    ) : PixelCapabilityValueResult<Nothing>
}

/** 平台输入法 capability。 */
public interface PixelImeCapability {
    /** 启动一个文本输入会话。 */
    public fun showTextInput(request: PixelTextInputRequest)

    /** 同步已启动输入会话的文本与选区。 */
    public fun updateTextInput(request: PixelTextInputRequest)

    /** 隐藏并结束当前文本输入会话。 */
    public fun hideTextInput()
}

/** 平台剪贴板 capability。 */
public interface PixelClipboardCapability {
    /** 返回剪贴板纯文本；空内容或不可读取时返回 null。 */
    public fun readClipboardText(): String?

    /** 把纯文本写入剪贴板。 */
    public fun writeClipboardText(text: String)
}

/** 平台震动反馈 capability。 */
public fun interface PixelHapticCapability {
    /** 执行一次语义化震动反馈。 */
    public fun performHapticFeedback(type: PixelHapticType)
}

/** 平台返回 capability。 */
public fun interface PixelBackCapability {
    /** 请求 Host 执行一次返回动作。 */
    public fun requestBack(): PixelCapabilityResult
}

/** 平台无障碍通知 capability。 */
public fun interface PixelAccessibilityCapability {
    /** 向辅助技术发布一条语义通知。 */
    public fun announce(message: String): PixelCapabilityResult
}

/** Host 保存状态 capability。 */
public interface PixelSavedStateCapability {
    /** 恢复指定 key 的原始状态；不存在时返回 null。 */
    public fun restore(key: String): ByteArray?

    /** 保存指定 key 的原始状态。 */
    public fun save(key: String, value: ByteArray): PixelCapabilityResult

    /** 删除指定 key 的状态。 */
    public fun remove(key: String): PixelCapabilityResult
}

/** 类型安全系统动作的封闭模型。 */
public sealed interface PixelTypedSystemAction

/**
 * 请求 Host 打开 URI。
 *
 * @property uri 已经由调用方构造的完整 URI。
 */
public data class PixelOpenUriAction(
    public val uri: String,
) : PixelTypedSystemAction

/** 请求 Host 执行系统返回。 */
public object PixelNavigateBackAction : PixelTypedSystemAction

/**
 * 请求 Host 打开应用设置。
 *
 * @property packageName 目标包名；null 表示当前应用。
 */
public data class PixelOpenAppSettingsAction(
    public val packageName: String? = null,
) : PixelTypedSystemAction

/**
 * 请求 Host 发起平台权限流程。
 *
 * @property permission 平台权限完整名称。
 */
public data class PixelRequestPermissionAction(
    public val permission: String,
) : PixelTypedSystemAction

/** 类型安全系统动作 capability。 */
public fun interface PixelSystemActionCapability {
    /** 分发系统动作并返回明确的支持、失败或降级结果。 */
    public fun dispatch(action: PixelTypedSystemAction): PixelCapabilityResult
}

/**
 * 一组彼此独立、可以按 Host 组合的 capability。
 *
 * null 表示明确不支持；调用方应返回 [PixelCapabilityResult.Unsupported]，而不是依赖空实现猜测。
 */
public data class PixelHostCapabilitySet(
    /** 可选输入法 capability。 */
    public val ime: PixelImeCapability? = null,
    /** 可选剪贴板 capability。 */
    public val clipboard: PixelClipboardCapability? = null,
    /** 可选震动 capability。 */
    public val haptic: PixelHapticCapability? = null,
    /** 可选返回 capability。 */
    public val back: PixelBackCapability? = null,
    /** 可选无障碍 capability。 */
    public val accessibility: PixelAccessibilityCapability? = null,
    /** 可选保存状态 capability。 */
    public val savedState: PixelSavedStateCapability? = null,
    /** 可选类型安全系统动作 capability。 */
    public val systemActions: PixelSystemActionCapability? = null,
) {
    /** 启动输入法；缺失 capability 时返回明确不支持结果。 */
    public fun showTextInput(request: PixelTextInputRequest): PixelCapabilityResult {
        val capability = ime ?: return unsupported("ime")
        return capabilityCall("ime") { capability.showTextInput(request) }
    }

    /** 同步输入法状态；缺失 capability 时返回明确不支持结果。 */
    public fun updateTextInput(request: PixelTextInputRequest): PixelCapabilityResult {
        val capability = ime ?: return unsupported("ime")
        return capabilityCall("ime") { capability.updateTextInput(request) }
    }

    /** 隐藏输入法；缺失 capability 时返回明确不支持结果。 */
    public fun hideTextInput(): PixelCapabilityResult {
        val capability = ime ?: return unsupported("ime")
        return capabilityCall("ime", capability::hideTextInput)
    }

    /** 读取剪贴板并区分“空内容”与“不支持”。 */
    public fun readClipboardText(): PixelCapabilityValueResult<String?> {
        val capability = clipboard ?: return unsupportedValue("clipboard")
        return runCatching { PixelCapabilityValueResult.Value(capability.readClipboardText()) }
            .getOrElse { cause ->
                PixelCapabilityValueResult.Failed(capability = "clipboard", cause = cause)
            }
    }

    /** 写入剪贴板；缺失 capability 时返回明确不支持结果。 */
    public fun writeClipboardText(text: String): PixelCapabilityResult {
        val capability = clipboard ?: return unsupported("clipboard")
        return capabilityCall("clipboard") { capability.writeClipboardText(text) }
    }

    /** 执行震动；缺失 capability 时返回明确不支持结果。 */
    public fun performHapticFeedback(type: PixelHapticType): PixelCapabilityResult {
        val capability = haptic ?: return unsupported("haptic")
        return capabilityCall("haptic") { capability.performHapticFeedback(type) }
    }

    /** 请求返回；缺失 capability 时返回明确不支持结果。 */
    public fun requestBack(): PixelCapabilityResult {
        val capability = back ?: return unsupported("back")
        return runCatching(capability::requestBack)
            .getOrElse { cause -> PixelCapabilityResult.Failed("back", cause) }
    }

    /** 发布无障碍通知；缺失 capability 时返回明确不支持结果。 */
    public fun announce(message: String): PixelCapabilityResult {
        val capability = accessibility ?: return unsupported("accessibility")
        return runCatching { capability.announce(message) }
            .getOrElse { cause -> PixelCapabilityResult.Failed("accessibility", cause) }
    }

    /** 恢复状态并区分“没有状态”与“不支持”。 */
    public fun restoreState(key: String): PixelCapabilityValueResult<ByteArray?> {
        val capability = savedState ?: return unsupportedValue("savedState")
        return runCatching { PixelCapabilityValueResult.Value(capability.restore(key)) }
            .getOrElse { cause ->
                PixelCapabilityValueResult.Failed(capability = "savedState", cause = cause)
            }
    }

    /** 保存状态；缺失 capability 时返回明确不支持结果。 */
    public fun saveState(key: String, value: ByteArray): PixelCapabilityResult {
        val capability = savedState ?: return unsupported("savedState")
        return runCatching { capability.save(key, value) }
            .getOrElse { cause -> PixelCapabilityResult.Failed("savedState", cause) }
    }

    /** 删除状态；缺失 capability 时返回明确不支持结果。 */
    public fun removeState(key: String): PixelCapabilityResult {
        val capability = savedState ?: return unsupported("savedState")
        return runCatching { capability.remove(key) }
            .getOrElse { cause -> PixelCapabilityResult.Failed("savedState", cause) }
    }

    /** 分发类型安全系统动作；缺失 capability 时返回明确不支持结果。 */
    public fun dispatchSystemAction(action: PixelTypedSystemAction): PixelCapabilityResult {
        val capability = systemActions
            ?: return PixelCapabilityResult.Unsupported(
                capability = "systemActions",
                reason = "Current Pixel Host does not provide system action capability.",
            )
        return runCatching { capability.dispatch(action) }
            .getOrElse { cause ->
                PixelCapabilityResult.Failed(capability = "systemActions", cause = cause)
            }
    }

    /** 使用 [fallback] 只补齐当前缺失的 capability。 */
    public fun withFallback(fallback: PixelHostCapabilitySet): PixelHostCapabilitySet {
        return PixelHostCapabilitySet(
            ime = ime ?: fallback.ime,
            clipboard = clipboard ?: fallback.clipboard,
            haptic = haptic ?: fallback.haptic,
            back = back ?: fallback.back,
            accessibility = accessibility ?: fallback.accessibility,
            savedState = savedState ?: fallback.savedState,
            systemActions = systemActions ?: fallback.systemActions,
        )
    }

    /** 集中提供 `PixelHostCapabilities` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 不声明任何平台能力的显式空集合。 */
        public val Empty: PixelHostCapabilitySet = PixelHostCapabilitySet()

        /** 把冻结的聚合桥接协议适配为聚焦 capability 集合。 */
        public fun fromLegacyBridge(bridge: PixelHostBridge?): PixelHostCapabilitySet {
            if (bridge == null) return Empty
            return PixelHostCapabilitySet(
                ime = LegacyImeCapability(bridge),
                clipboard = LegacyClipboardCapability(bridge),
                haptic = LegacyHapticCapability(bridge),
                systemActions = LegacySystemActionCapability(bridge),
            )
        }
    }
}

/** 为无返回值 capability 执行统一的异常隔离。 */
private inline fun capabilityCall(
    capability: String,
    block: () -> Unit,
): PixelCapabilityResult {
    return runCatching {
        block()
        PixelCapabilityResult.Handled
    }.getOrElse { cause -> PixelCapabilityResult.Failed(capability, cause) }
}

/** 构造无返回值 capability 的稳定不支持结果。 */
private fun unsupported(capability: String): PixelCapabilityResult.Unsupported {
    return PixelCapabilityResult.Unsupported(
        capability = capability,
        reason = "Current Pixel Host does not provide $capability capability.",
    )
}

/** 构造带返回值 capability 的稳定不支持结果。 */
private fun unsupportedValue(capability: String): PixelCapabilityValueResult.Unsupported {
    return PixelCapabilityValueResult.Unsupported(
        capability = capability,
        reason = "Current Pixel Host does not provide $capability capability.",
    )
}

/** 把冻结桥接的输入法方法适配到聚焦 capability。 */
private class LegacyImeCapability(
    /** 接收旧输入法调用的桥接实例。 */
    private val bridge: PixelHostBridge,
) : PixelImeCapability {
    /** 转发显示输入法请求。 */
    override fun showTextInput(request: PixelTextInputRequest) {
        bridge.showTextInput(request)
    }

    /** 转发输入状态同步请求。 */
    override fun updateTextInput(request: PixelTextInputRequest) {
        bridge.updateTextInput(request)
    }

    /** 转发隐藏输入法请求。 */
    override fun hideTextInput() {
        bridge.hideTextInput()
    }
}

/** 把冻结桥接的剪贴板方法适配到聚焦 capability。 */
private class LegacyClipboardCapability(
    /** 接收旧剪贴板调用的桥接实例。 */
    private val bridge: PixelHostBridge,
) : PixelClipboardCapability {
    /** 转发剪贴板读取。 */
    override fun readClipboardText(): String? = bridge.readClipboardText()

    /** 转发剪贴板写入。 */
    override fun writeClipboardText(text: String) {
        bridge.writeClipboardText(text)
    }
}

/** 把冻结桥接的震动方法适配到聚焦 capability。 */
private class LegacyHapticCapability(
    /** 接收旧震动调用的桥接实例。 */
    private val bridge: PixelHostBridge,
) : PixelHapticCapability {
    /** 转发语义化震动请求。 */
    override fun performHapticFeedback(type: PixelHapticType) {
        bridge.performHapticFeedback(type)
    }
}

/** 把类型安全动作转换到旧桥接协议的兼容适配器。 */
private class LegacySystemActionCapability(
    /** 接收旧 [PixelSystemAction] 的桥接实例。 */
    private val bridge: PixelHostBridge,
) : PixelSystemActionCapability {
    /** 转换动作后调用旧桥接，并把成功调用标记为已处理。 */
    override fun dispatch(action: PixelTypedSystemAction): PixelCapabilityResult {
        bridge.dispatchSystemAction(action.toLegacySystemAction())
        return PixelCapabilityResult.Handled
    }
}

/** 把封闭动作模型映射为冻结字符串协议，仅用于旧 Host 兼容层。 */
private fun PixelTypedSystemAction.toLegacySystemAction(): PixelSystemAction {
    return when (this) {
        is PixelOpenUriAction -> PixelSystemAction(type = "open_uri", payload = uri)
        PixelNavigateBackAction -> PixelSystemAction(type = "navigate_back")
        is PixelOpenAppSettingsAction -> PixelSystemAction(
            type = "open_app_settings",
            payload = packageName,
        )
        is PixelRequestPermissionAction -> PixelSystemAction(
            type = "request_permission",
            payload = permission,
        )
    }
}

/** widget 树读取当前聚焦 Host capability 的入口。 */
public object PixelHostServices {
    /** 返回最近的 capability 集合；没有 Host 时返回显式空集合。 */
    public fun of(context: BuildContext): PixelHostCapabilitySet {
        return context.dependOnInheritedWidgetOfExactType<PixelHostCapabilityScope>()?.capabilities
            ?: PixelHostCapabilitySet.Empty
    }

    /** 分发类型安全系统动作；缺失 capability 时返回文档化不支持结果。 */
    public fun dispatchSystemAction(
        context: BuildContext,
        action: PixelTypedSystemAction,
    ): PixelCapabilityResult {
        return of(context).dispatchSystemAction(action)
    }
}
