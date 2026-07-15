package com.purride.pixelui.services

/**
 * 引擎使用的时钟服务。
 *
 * 单调时间用于帧推进和耗时统计，墙上时间只用于需要真实时间语义的业务事件。
 */
public interface PixelClock {
    /** 返回不受系统时间校准影响的毫秒时间。 */
    public fun uptimeMillis(): Long

    /** 返回与 [uptimeMillis] 同为单调语义的纳秒时间。 */
    public fun nanoTime(): Long

    /** 返回 Unix epoch 毫秒时间。 */
    public fun currentTimeMillis(): Long

    /** 集中提供 `PixelRuntimeServices` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 不依赖 Android 系统服务的默认 JVM 时钟。 */
        public val System: PixelClock = SystemPixelClock
    }
}

/** 默认使用 JVM 时间源的无状态时钟。 */
private object SystemPixelClock : PixelClock {
    /** 使用纳秒单调时间换算运行毫秒。 */
    override fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L

    /** 直接返回 JVM 单调纳秒时间。 */
    override fun nanoTime(): Long = System.nanoTime()

    /** 直接返回 JVM 墙上时间。 */
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/** 日志事件的严重级别。 */
public enum class PixelLogLevel {
    /** 仅用于细粒度诊断。 */
    DEBUG,

    /** 正常生命周期或状态信息。 */
    INFO,

    /** 可恢复但值得关注的问题。 */
    WARNING,

    /** 已影响当前操作的错误。 */
    ERROR,
}

/**
 * 结构化日志事件。
 *
 * @property level 事件严重级别。
 * @property category 稳定的日志分类。
 * @property message 面向开发者的说明。
 * @property attributes 不包含敏感数据的结构化属性。
 * @property cause 可选的原始异常。
 */
public data class PixelLogEvent(
    public val level: PixelLogLevel,
    public val category: String,
    public val message: String,
    public val attributes: Map<String, String> = emptyMap(),
    public val cause: Throwable? = null,
)

/** 接收结构化引擎日志的可注入服务。 */
public fun interface PixelLogger {
    /** 消费一条日志事件；实现不应把异常抛回渲染链路。 */
    public fun log(event: PixelLogEvent)

    /** 集中提供 `PixelRuntimeServices` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 默认静默实现，避免 SDK 在消费者未配置时主动刷屏。 */
        public val None: PixelLogger = PixelLogger { }
    }
}

/** 发生错误时所处的稳定引擎阶段。 */
public enum class PixelErrorPhase {
    /** retained widget 构建或 reconcile 阶段。 */
    BUILD,

    /** layout、paint 或渲染树提交阶段。 */
    RENDER,

    /** ErrorBoundary 后备内容的再次渲染阶段。 */
    FALLBACK_RENDER,

    /** Host 或平台能力调用阶段。 */
    HOST,

    /** 资源解析、读取或缓存阶段。 */
    RESOURCE,
}

/** 引擎尝试恢复错误后的明确结果。 */
public enum class PixelErrorRecoveryResult {
    /** 尚未执行任何恢复。 */
    NOT_ATTEMPTED,

    /** 最近的 ErrorBoundary 已完成恢复。 */
    RECOVERED,

    /** 当前树没有可处理该错误的 ErrorBoundary。 */
    NO_BOUNDARY,

    /** 恢复过程自身失败。 */
    RECOVERY_FAILED,

    /** 后备内容在再次渲染时失败。 */
    FALLBACK_FAILED,
}

/**
 * 不持有 Widget 或 RenderObject 实例的错误上下文。
 *
 * @property widgetType 最接近错误位置的 Widget 类型名。
 * @property elementPath retained element 的稳定诊断路径。
 * @property renderPath render object 的稳定诊断路径。
 * @property hostTag 调用方提供的 Host 标识。
 */
public data class PixelErrorContext(
    public val widgetType: String? = null,
    public val elementPath: String? = null,
    public val renderPath: String? = null,
    public val hostTag: String? = null,
)

/**
 * 保留 cause、阶段、上下文和恢复结果的结构化错误事件。
 *
 * @property cause 原始异常，不会被字符串化丢失堆栈。
 * @property phase 错误发生阶段。
 * @property context 不持有运行时对象的定位信息。
 * @property recoveryResult 恢复结果。
 * @property timestampNanos Engine 时钟提供的单调时间。
 * @property attributes 额外的稳定诊断属性。
 */
public data class PixelErrorEvent(
    public val cause: Throwable,
    public val phase: PixelErrorPhase,
    public val context: PixelErrorContext = PixelErrorContext(),
    public val recoveryResult: PixelErrorRecoveryResult = PixelErrorRecoveryResult.NOT_ATTEMPTED,
    public val timestampNanos: Long = 0L,
    public val attributes: Map<String, String> = emptyMap(),
)

/** 接收结构化引擎错误的可注入服务。 */
public fun interface PixelErrorReporter {
    /** 上报一条错误事件；实现不应把异常抛回引擎。 */
    public fun report(event: PixelErrorEvent)

    /** 集中提供 `PixelRuntimeServices` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 默认静默实现，保持未配置消费者的历史行为。 */
        public val None: PixelErrorReporter = PixelErrorReporter { }
    }
}
