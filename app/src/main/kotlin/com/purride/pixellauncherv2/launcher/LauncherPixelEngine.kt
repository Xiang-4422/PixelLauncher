package com.purride.pixellauncherv2.launcher

import android.util.Log
import android.view.HapticFeedbackConstants
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.PixelHapticCapability
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.services.PixelErrorEvent
import com.purride.pixelui.services.PixelErrorReporter
import com.purride.pixelui.services.PixelLogEvent
import com.purride.pixelui.services.PixelLogLevel
import com.purride.pixelui.services.PixelLogger

/**
 * 为 Launcher 创建具备结构化诊断和聚焦 Host 能力的新版 [PixelEngine] 实例。
 *
 * 每个 [LauncherRootHost] 都通过本工厂取得独立 Engine，因此资源缓存、帧服务和错误通道不会
 * 隐式泄漏到其他 Host。输入法与剪贴板仍由标准 Android Host setup 按 SDK 契约补齐。
 */
internal object LauncherPixelEngineFactory {

    /** Launcher 写入 Android 日志时使用的稳定标签。 */
    private const val ENGINE_LOG_TAG = "PixelEngine"

    /** 创建并返回只服务于指定 [hostView] 的不可变 Engine。 */
    fun create(hostView: PixelHostView): PixelEngine {
        return PixelEngine.Builder()
            .logger(LauncherAndroidPixelLogger)
            .errorReporter(LauncherAndroidPixelErrorReporter)
            .hostServices(createHostServices(hostView))
            .build()
    }

    /** 创建 Launcher 明确拥有的平台能力集合。 */
    private fun createHostServices(hostView: PixelHostView): PixelHostCapabilitySet {
        /** 把稳定 Pixel 震动语义映射到 Android View 能力的实现。 */
        val hapticCapability = PixelHapticCapability { type ->
            /** 当前语义对应的 Android 震动常量。 */
            val feedbackConstant = when (type) {
                PixelHapticType.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
                PixelHapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            }
            hostView.performHapticFeedback(feedbackConstant)
        }
        return PixelHostCapabilitySet(haptic = hapticCapability)
    }

    /** 把 Engine 结构化日志稳定转发到 Android 日志系统。 */
    private object LauncherAndroidPixelLogger : PixelLogger {
        /** 按事件级别写入日志，并保留非敏感结构化属性和原始异常。 */
        override fun log(event: PixelLogEvent) {
            /** Android Log 对应的优先级。 */
            val priority = when (event.level) {
                PixelLogLevel.DEBUG -> Log.DEBUG
                PixelLogLevel.INFO -> Log.INFO
                PixelLogLevel.WARNING -> Log.WARN
                PixelLogLevel.ERROR -> Log.ERROR
            }
            /** 经过稳定排序的结构化属性文本。 */
            val attributes = event.attributes.toSortedMap().entries.joinToString(
                separator = ",",
                prefix = if (event.attributes.isEmpty()) "" else " {",
                postfix = if (event.attributes.isEmpty()) "" else "}",
            ) { (key, value) -> "$key=$value" }
            Log.println(
                priority,
                ENGINE_LOG_TAG,
                "[${event.category}] ${event.message}$attributes",
            )
            event.cause?.let { cause ->
                Log.println(priority, ENGINE_LOG_TAG, Log.getStackTraceString(cause))
            }
        }
    }

    /** 把 Engine 结构化错误保留完整上下文后写入 Android 错误日志。 */
    private object LauncherAndroidPixelErrorReporter : PixelErrorReporter {
        /** 上报原始异常、阶段、恢复结果和可用的 Host 定位信息。 */
        override fun report(event: PixelErrorEvent) {
            /** 不包含业务敏感值的稳定错误摘要。 */
            val message = buildString {
                append("phase=")
                append(event.phase.name)
                append(" recovery=")
                append(event.recoveryResult.name)
                event.context.widgetType?.let { widgetType ->
                    append(" widget=")
                    append(widgetType)
                }
                event.context.hostTag?.let { hostTag ->
                    append(" host=")
                    append(hostTag)
                }
            }
            Log.e(ENGINE_LOG_TAG, message, event.cause)
        }
    }
}
