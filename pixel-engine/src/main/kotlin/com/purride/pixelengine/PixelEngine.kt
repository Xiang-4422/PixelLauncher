package com.purride.pixelengine

import com.purride.pixelcore.PixelResourceCache
import com.purride.pixelcore.PixelResourceKind
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.animation.PixelTickerProviderFactory
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.services.PixelClock
import com.purride.pixelui.services.PixelErrorReporter
import com.purride.pixelui.services.PixelLogger

/**
 * 资源解析请求。
 *
 * @property key 应用定义的稳定资源键。
 * @property kind 资源缓存与解码类型。
 */
public data class PixelResourceRequest(
    public val key: String,
    public val kind: PixelResourceKind,
) {
    init {
        require(key.isNotBlank()) { "Pixel resource key must not be blank" }
    }
}

/** 资源解析器返回的封闭结果。 */
public sealed interface PixelResourceResolution {
    /**
     * 已把逻辑 key 解析为规范资源标识。
     *
     * @property canonicalKey 可安全传给 loader/cache 的规范键。
     */
    public data class Resolved(
        public val canonicalKey: String,
    ) : PixelResourceResolution {
        init {
            require(canonicalKey.isNotBlank()) { "Canonical resource key must not be blank" }
        }
    }

    /**
     * 资源键合法但当前来源中不存在。
     *
     * @property key 未找到的原始键。
     */
    public data class Missing(
        public val key: String,
    ) : PixelResourceResolution

    /**
     * 当前解析器不支持该资源类型。
     *
     * @property kind 不支持的资源类型。
     * @property reason 文档化降级原因。
     */
    public data class Unsupported(
        public val kind: PixelResourceKind,
        public val reason: String,
    ) : PixelResourceResolution
}

/** 把逻辑资源键解析为 loader/cache 规范键的可注入服务。 */
public fun interface PixelResourceResolver {
    /** 解析一个资源请求并返回明确结果。 */
    public fun resolve(request: PixelResourceRequest): PixelResourceResolution

    /** 集中提供 `PixelEngine` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 默认保持调用方 key，不依赖文件系统或 Android AssetManager。 */
        public val Identity: PixelResourceResolver = PixelResourceResolver { request ->
            PixelResourceResolution.Resolved(request.key)
        }
    }
}

/**
 * 一个 [PixelEngine] 实例持有的完整可注入服务集合。
 *
 * 该对象不可变；不同 Engine 默认拥有不同资源缓存，Host 只读取绑定 Engine 的服务。
 */
public class PixelEngineServices internal constructor(
    /** 单调与墙上时间来源。 */
    public val clock: PixelClock,
    /** 平台或测试提供的上游帧调度器。 */
    public val frameScheduler: PixelFrameScheduler,
    /** 为每个 Host 创建独立 ticker provider 的工厂。 */
    public val tickerProviderFactory: PixelTickerProviderFactory,
    /** 结构化错误接收器。 */
    public val errorReporter: PixelErrorReporter,
    /** 逻辑资源键解析器。 */
    public val resourceResolver: PixelResourceResolver,
    /** 当前 Engine 独占或显式共享的资源缓存。 */
    public val resourceCache: PixelResourceCache,
    /** 结构化日志接收器。 */
    public val logger: PixelLogger,
    /** 可选的完整 Host 环境覆盖；null 表示跟随平台。 */
    public val hostCapabilities: HostCapabilitiesData?,
    /** Engine 提供、并由每个 Host 与本地桥接合并的聚焦 capability。 */
    public val hostServices: PixelHostCapabilitySet,
)

/**
 * 可独立配置并绑定到一个或多个 Host 的像素引擎实例。
 *
 * Engine 自身不可变；通过 [Builder] 注入 fake service 后，测试不需要 Android 系统服务。
 * 多个 Engine 只有在调用方显式传入同一服务对象时才共享状态。
 */
public class PixelEngine private constructor(
    /** 当前 Engine 的完整服务集合。 */
    public val services: PixelEngineServices,
    /** 当前 Engine 对外可见的完整主题 token；未配置时返回稳定默认值。 */
    public val theme: PixelThemeTokens,
) {
    /** 仅在调用方显式配置主题时注入 Host；类体字段不改变已冻结私有构造器的 JVM 描述符。 */
    internal var themeOverride: PixelThemeTokens? = null
        private set

    /**
     * 返回一个共享现有服务、但用 [fallback] 补齐缺失 Host capability 的 Engine。
     *
     * 调用方已配置的 capability 始终优先；cache、clock、scheduler 与其他服务保持原引用，
     * 因而该方法只建立新的不可变装配边界，不会复制或偷偷分叉有状态服务。
     */
    public fun withHostServicesFallback(fallback: PixelHostCapabilitySet): PixelEngine {
        /** 只补缺、不覆盖调用方显式能力的合并结果。 */
        val mergedHostServices = services.hostServices.withFallback(fallback)
        if (mergedHostServices == services.hostServices) return this
        /** 共享原服务引用、仅替换 Host capability 集合的新服务快照。 */
        val mergedServices = PixelEngineServices(
            clock = services.clock,
            frameScheduler = services.frameScheduler,
            tickerProviderFactory = services.tickerProviderFactory,
            errorReporter = services.errorReporter,
            resourceResolver = services.resourceResolver,
            resourceCache = services.resourceCache,
            logger = services.logger,
            hostCapabilities = services.hostCapabilities,
            hostServices = mergedHostServices,
        )
        /** 保留显式主题标记，避免默认主题被误装成新的根作用域。 */
        val mergedEngine = PixelEngine(services = mergedServices, theme = theme)
        mergedEngine.themeOverride = themeOverride
        return mergedEngine
    }

    /** 构建隔离 [PixelEngine] 实例的可变装配器。 */
    public class Builder {
        /** 可选自定义时钟；null 使用平台无关默认值。 */
        private var clock: PixelClock = PixelClock.System

        /** 可选帧调度器；null 在 [build] 时解析 Android 默认实现。 */
        private var frameScheduler: PixelFrameScheduler? = null

        /** 每个 Host 的 ticker provider 工厂。 */
        private var tickerProviderFactory: PixelTickerProviderFactory =
            PixelTickerProviderFactory.Default

        /** 结构化错误接收器。 */
        private var errorReporter: PixelErrorReporter = PixelErrorReporter.None

        /** 逻辑资源键解析器。 */
        private var resourceResolver: PixelResourceResolver = PixelResourceResolver.Identity

        /** 显式资源缓存；null 表示每次 [build] 创建独立缓存。 */
        private var resourceCache: PixelResourceCache? = null

        /** 结构化日志接收器。 */
        private var logger: PixelLogger = PixelLogger.None

        /** 可选完整 Host 环境覆盖。 */
        private var hostCapabilities: HostCapabilitiesData? = null

        /** 可组合的 Host capability 集合。 */
        private var hostServices: PixelHostCapabilitySet = PixelHostCapabilitySet.Empty

        /** 显式注入 widget 根树的主题 token；null 保留旧 Host 的无主题作用域语义。 */
        private var theme: PixelThemeTokens? = null

        /** 设置时钟服务。 */
        public fun clock(clock: PixelClock): Builder = apply {
            this.clock = clock
        }

        /** 设置上游帧调度器。 */
        public fun frameScheduler(frameScheduler: PixelFrameScheduler): Builder = apply {
            this.frameScheduler = frameScheduler
        }

        /** 设置 Host 私有 ticker provider 工厂。 */
        public fun tickerProviderFactory(factory: PixelTickerProviderFactory): Builder = apply {
            tickerProviderFactory = factory
        }

        /** 设置结构化错误接收器。 */
        public fun errorReporter(errorReporter: PixelErrorReporter): Builder = apply {
            this.errorReporter = errorReporter
        }

        /** 设置资源键解析器。 */
        public fun resourceResolver(resourceResolver: PixelResourceResolver): Builder = apply {
            this.resourceResolver = resourceResolver
        }

        /** 设置资源缓存；同一缓存可由调用方显式共享给多个 Engine。 */
        public fun resourceCache(resourceCache: PixelResourceCache): Builder = apply {
            this.resourceCache = resourceCache
        }

        /** 设置结构化日志接收器。 */
        public fun logger(logger: PixelLogger): Builder = apply {
            this.logger = logger
        }

        /** 设置完整 Host 环境覆盖；传入 null 可恢复平台自动能力。 */
        public fun hostCapabilities(hostCapabilities: HostCapabilitiesData?): Builder = apply {
            this.hostCapabilities = hostCapabilities
        }

        /** 设置聚焦 Host capability 集合。 */
        public fun hostServices(hostServices: PixelHostCapabilitySet): Builder = apply {
            this.hostServices = hostServices
        }

        /** 设置当前 Engine 的主题 token。 */
        public fun theme(theme: PixelThemeTokens): Builder = apply {
            this.theme = theme
        }

        /** 创建不可变 Engine；未显式设置的缓存不会在不同 build 结果间共享。 */
        public fun build(): PixelEngine {
            /** 延迟解析默认调度器，使纯 JVM 测试可先注入 fake scheduler。 */
            val resolvedFrameScheduler = frameScheduler ?: PixelFrameScheduler.Default
            /** 默认每个 Engine 创建一个独立缓存。 */
            val resolvedResourceCache = resourceCache ?: PixelResourceCache()
            /** 对外始终暴露完整主题，但只有显式值才进入继承树。 */
            val resolvedTheme = theme ?: PixelThemeTokens.Default
            /** 先使用冻结的双参数构造器创建实例，再记录不进入公共 API 的显式主题标记。 */
            val engine = PixelEngine(
                services = PixelEngineServices(
                    clock = clock,
                    frameScheduler = resolvedFrameScheduler,
                    tickerProviderFactory = tickerProviderFactory,
                    errorReporter = errorReporter,
                    resourceResolver = resourceResolver,
                    resourceCache = resolvedResourceCache,
                    logger = logger,
                    hostCapabilities = hostCapabilities,
                    hostServices = hostServices,
                ),
                theme = resolvedTheme,
            )
            engine.themeOverride = theme
            return engine
        }
    }
}
