package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelui.PixelFocusOwner
import com.purride.pixelui.PixelFocusOwnerScope
import com.purride.pixelui.Widget
import com.purride.pixelui.services.PixelClock
import com.purride.pixelui.services.PixelErrorContext
import com.purride.pixelui.services.PixelErrorEvent
import com.purride.pixelui.services.PixelErrorPhase
import com.purride.pixelui.services.PixelErrorRecoveryResult
import com.purride.pixelui.services.PixelErrorReporter
import com.purride.pixelui.services.PixelLogEvent
import com.purride.pixelui.services.PixelLogLevel
import com.purride.pixelui.services.PixelLogger

/**
 * pixel-engine UI layer 对宿主层暴露的内部运行时。
 *
 * 渲染一帧的调用链：
 * ```
 * PixelUiRuntime.render
 *   -> ElementTreeBuildRuntime.resolveElementTree
 *   -> ElementTreeRenderer.render
 * ```
 */
public class PixelUiRuntime(
    /** retained 状态在帧外变化时请求 Host 更新的回调。 */
    onVisualUpdate: () -> Unit = { },
) {
    /** 当前 runtime 使用的时钟，默认不依赖 Android 服务。 */
    private var clock: PixelClock = PixelClock.System

    /** 当前 runtime 使用的结构化错误接收器。 */
    private var errorReporter: PixelErrorReporter = PixelErrorReporter.None

    /** 当前 runtime 使用的结构化日志接收器。 */
    private var logger: PixelLogger = PixelLogger.None

    /** 使用完整可注入服务创建 runtime，同时保留历史单参数构造器。 */
    public constructor(
        onVisualUpdate: () -> Unit,
        clock: PixelClock,
        errorReporter: PixelErrorReporter,
        logger: PixelLogger,
    ) : this(onVisualUpdate) {
        this.clock = clock
        this.errorReporter = errorReporter
        this.logger = logger
    }

    /** Focus state owned exclusively by this retained runtime. */
    /** runtime 独占的具体焦点树所有者，不跨 artifact 暴露。 */
    private val focusOwnerState: PixelFocusOwner = PixelFocusOwner()

    /** 供平台适配层提交规范化输入、但不暴露焦点树所有权的最小 SPI。 */
    public val focusOwner: PixelFocusDispatcher
        get() = focusOwnerState

    /** Reusable buffer storage owned by this runtime. */
    private val bufferPool: PixelBufferPool = PixelBufferPool()

    /** Retained render pipeline paired one-to-one with this runtime. */
    private val elementTreeRenderer: PipelineElementTreeRenderer =
        PipelineElementTreeRenderer(bufferPool = bufferPool)

    /** Retained Element builder that resolves declarative widget updates. */
    private val buildRuntime: ElementTreeBuildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = UnsupportedWidgetAdapter,
        onRecoveredBuildError = { error, widgetType ->
            reportError(
                cause = error,
                phase = PixelErrorPhase.BUILD,
                recoveryResult = PixelErrorRecoveryResult.RECOVERED,
                widgetType = widgetType,
            )
        },
    )

    /** 执行 `PixelUiRuntime` 的 `render` 渲染或命中阶段。
 *
 * Resolves and paints one request under this runtime's focus-owner boundary.
 */
    public fun render(
        request: WidgetRenderRequest,
        framePhaseSink: PixelFramePhaseSink? = null,
    ): PixelRenderResult {
        /** Primitive rebuild counter before reconciliation, sampled only for enabled diagnostics. */
        val rebuiltBefore = framePhaseSink?.let { buildRuntime.cumulativeRebuiltElementCount() }
        /** Caller-visible Element root resolved under this runtime's focus-owner boundary. */
        val root = try {
            framePhaseSink?.beginBuild()
            buildRuntime.resolveElementTree(
                PixelFocusOwnerScope(
                    owner = focusOwnerState,
                    child = request.root,
                    key = RuntimeFocusOwnerKey,
                ),
            )
        } catch (error: Throwable) {
            reportError(
                cause = error,
                phase = PixelErrorPhase.BUILD,
                recoveryResult = PixelErrorRecoveryResult.NOT_ATTEMPTED,
                widgetType = request.root.javaClass.name,
            )
            throw error
        } finally {
            framePhaseSink?.endBuild()
            if (rebuiltBefore != null) {
                /** Number of Elements rebuilt by this reconciliation, saturated to public Int. */
                val rebuiltDelta = (
                    buildRuntime.cumulativeRebuiltElementCount() - rebuiltBefore
                    ).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                framePhaseSink?.recordBuildWork(rebuiltDelta)
            }
        }
        /** Buffer-pool counters before layout/paint and any render-error recovery attempt. */
        val bufferStatsBefore = framePhaseSink?.let { bufferPool.stats() }
        return try {
            renderElementTreeWithRecovery(
                root = root,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
                framePhaseSink = framePhaseSink,
            )
        } finally {
            if (bufferStatsBefore != null) {
                /** Buffer-pool counters after all normal or recovered render work. */
                val bufferStatsAfter = bufferPool.stats()
                framePhaseSink?.recordBufferPoolActivity(
                    hitCount = (bufferStatsAfter.hits - bufferStatsBefore.hits).coerceAtLeast(0L),
                    missCount = (bufferStatsAfter.misses - bufferStatsBefore.misses).coerceAtLeast(0L),
                )
            }
        }
    }

    /** 执行 `PixelUiRuntime` 的 `render` 渲染或命中阶段。
 *
 * Convenience overload for callers that already hold logical dimensions.
 */
    public fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
        framePhaseSink: PixelFramePhaseSink? = null,
    ): PixelRenderResult {
        return render(
            request = WidgetRenderRequest(
                root = root,
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
            ),
            framePhaseSink = framePhaseSink,
        )
    }

    /** 从 `PixelUiRuntime` 释放 `dispose` 内容并收敛相关所有权。
 *
 * Releases render, Element, buffer, and focus resources at the runtime terminal boundary.
 */
    public fun dispose() {
        /** Runtime-terminal collector prevents one subsystem from retaining every later owner. */
        val failures = TeardownFailureCollector()
        failures.capture { elementTreeRenderer.dispose() }
        failures.capture { buildRuntime.dispose() }
        failures.capture { focusOwnerState.dispose() }
        failures.capture { bufferPool.clear() }
        failures.throwIfAny()
    }

    /**
     * 把当前 retained element tree 序列化成可读的 ASCII 缩进字符串。
     *
     * 给运行时调试用：调用 [PixelHostView.dumpElementTree] 时会下沉到这里。
     * 若还没渲染过一帧，返回简单的 "<no root>"。
     */
    public fun dumpElementTree(): String {
        val diagnostics = buildRuntime.collectDiagnostics()
        if (!diagnostics.hasRoot || diagnostics.elementDiagnostics.isEmpty()) {
            return "<no root>"
        }
        return buildString {
            for (node in diagnostics.elementDiagnostics) {
                repeat(node.depth) { append("  ") }
                append(node.name)
                append(" [widget=")
                append(node.widgetName)
                node.renderObjectName?.let {
                    append(" render=")
                    append(it)
                }
                if (node.isDirty) append(" *dirty*")
                if (node.listenedObjectCount > 0) {
                    append(" listens=")
                    append(node.listenedObjectCount)
                }
                append("]\n")
            }
        }.trimEnd()
    }

    /** 收集 `PixelUiRuntime` 的 `dumpRenderTree` 调试快照，不改变生产树的布局或生命周期。 */
    public fun dumpRenderTree(): String {
        return elementTreeRenderer.dumpRenderTree()
    }

    /** 判断 `PixelUiRuntime` 是否满足 `hasPendingBuild` 对应条件，不改变当前状态。 */
    public fun hasPendingBuild(): Boolean {
        return buildRuntime.collectDiagnostics().dirtyQueueDiagnostics.pendingElementCount > 0
    }

    /** 收集 `PixelUiRuntime` 的 `collectWidgets` 调试快照，不改变生产树的布局或生命周期。 */
    public fun collectWidgets(): List<Widget> {
        return buildRuntime.collectWidgets()
    }

    /** 收集 `PixelUiRuntime` 的 `collectInspectorNodeAssociations` 调试快照，不改变生产树的布局或生命周期。 */
    public fun collectInspectorNodeAssociations(): Map<RenderObject, InspectorNodeAssociation> {
        val elementNodes = buildRuntime.collectDiagnostics().elementDiagnostics
        val renderNodes = (elementTreeRenderer as? PipelineElementTreeRenderer)
            ?.collectRenderDiagnostics()
            .orEmpty()
        val elementPaths = elementNodes.mapNotNull { node ->
            node.renderObject?.let { renderObject -> renderObject to node.path }
        }.toMap()
        return renderNodes.associate { node ->
            node.renderObject to InspectorNodeAssociation(
                elementPath = elementPaths[node.renderObject],
                renderPath = node.path,
            )
        }
    }

    /**
 * 执行 `PixelUiRuntime` 的 `collectResourceDiagnostics` 公开行为；具体参数、返回和副作用见下文。
 *
     * Captures non-owning lifecycle counters for deterministic retained-runtime leak tests.
     *
     * The snapshot contains only primitive counts and does not expose or retain Element,
     * RenderObject, listener, or callback instances.
     */
    public fun collectResourceDiagnostics(): PixelUiRuntimeResourceDiagnostics {
        // Retained build diagnostics expose the complete currently mounted Element tree.
        val buildDiagnostics = buildRuntime.collectDiagnostics()
        // Only render objects still attached to a live pipeline owner count as retained runtime work.
        val attachedRenderObjectCount = elementTreeRenderer
            .collectRenderDiagnostics()
            .count { node -> node.renderObject.isAttachedToPipeline }
        return PixelUiRuntimeResourceDiagnostics(
            hasRetainedElementRoot = buildDiagnostics.hasRoot,
            retainedElementCount = buildDiagnostics.elementDiagnostics.size,
            retainedListenableDependencyCount = buildDiagnostics.elementDiagnostics.sumOf { node ->
                node.listenedObjectCount
            },
            pendingDirtyElementCount =
                buildDiagnostics.dirtyQueueDiagnostics.pendingElementCount,
            attachedRenderObjectCount = attachedRenderObjectCount,
        )
    }

    private fun renderElementTreeWithRecovery(
        root: Element?,
        logicalWidth: Int,
        logicalHeight: Int,
        framePhaseSink: PixelFramePhaseSink?,
    ): PixelRenderResult {
        val renderRequest = ElementTreeRenderRequest(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
        return try {
            elementTreeRenderer.renderWithDiagnostics(
                request = renderRequest,
                framePhaseSink = framePhaseSink,
            )
        } catch (error: Throwable) {
            /** 出错根节点类型只以字符串进入事件，避免 reporter 延长 retained tree 生命周期。 */
            val widgetType = root?.widget?.javaClass?.name
            /** 最近 ErrorBoundary 的恢复过程也可能因 teardown 或 fallback 构造失败而抛错。 */
            val recoveredRoot = try {
                buildRuntime.recoverFromRenderError(error)
            } catch (recoveryError: Throwable) {
                reportError(
                    cause = error,
                    phase = PixelErrorPhase.RENDER,
                    recoveryResult = PixelErrorRecoveryResult.RECOVERY_FAILED,
                    widgetType = widgetType,
                    attributes = mapOf(
                        "recoveryCause" to recoveryError.javaClass.name,
                    ),
                )
                recoveryError.addSuppressed(error)
                throw recoveryError
            }
            if (recoveredRoot == null) {
                reportError(
                    cause = error,
                    phase = PixelErrorPhase.RENDER,
                    recoveryResult = PixelErrorRecoveryResult.NO_BOUNDARY,
                    widgetType = widgetType,
                )
                throw error
            }
            val recoveredRequest = renderRequest.copy(root = recoveredRoot)
            try {
                val recoveredResult = elementTreeRenderer.renderWithDiagnostics(
                    request = recoveredRequest,
                    framePhaseSink = framePhaseSink,
                )
                reportError(
                    cause = error,
                    phase = PixelErrorPhase.RENDER,
                    recoveryResult = PixelErrorRecoveryResult.RECOVERED,
                    widgetType = widgetType,
                )
                recoveredResult
            } catch (fallbackError: Throwable) {
                reportError(
                    cause = fallbackError,
                    phase = PixelErrorPhase.FALLBACK_RENDER,
                    recoveryResult = PixelErrorRecoveryResult.FALLBACK_FAILED,
                    widgetType = recoveredRoot.widget.javaClass.name,
                    attributes = mapOf(
                        "originalCause" to error.javaClass.name,
                    ),
                )
                fallbackError.addSuppressed(error)
                throw fallbackError
            }
        }
    }

    /**
     * 安全发布结构化错误；consumer reporter/logger 的异常不会覆盖原始引擎异常。
     */
    private fun reportError(
        cause: Throwable,
        phase: PixelErrorPhase,
        recoveryResult: PixelErrorRecoveryResult,
        widgetType: String?,
        attributes: Map<String, String> = emptyMap(),
    ) {
        /** 时钟异常不应阻止错误上报，零值表示无法取得时间。 */
        val timestampNanos = runCatching(clock::nanoTime).getOrDefault(0L)
        /** 不持有 Widget/Element/RenderObject 实例的事件快照。 */
        val event = PixelErrorEvent(
            cause = cause,
            phase = phase,
            context = PixelErrorContext(widgetType = widgetType),
            recoveryResult = recoveryResult,
            timestampNanos = timestampNanos,
            attributes = attributes,
        )
        /** reporter 是隔离边界，消费方异常不能改变渲染控制流。 */
        val reporterFailure = runCatching { errorReporter.report(event) }.exceptionOrNull()
        /** 同一事件也进入可选结构化日志，便于没有 reporter 后端时诊断。 */
        runCatching {
            logger.log(
                PixelLogEvent(
                    level = PixelLogLevel.ERROR,
                    category = "pixel.runtime.error",
                    message = "Pixel runtime ${phase.name.lowercase()} failure",
                    attributes = attributes + ("recovery" to recoveryResult.name),
                    cause = cause,
                ),
            )
        }
        if (reporterFailure != null) {
            runCatching {
                logger.log(
                    PixelLogEvent(
                        level = PixelLogLevel.WARNING,
                        category = "pixel.runtime.error-reporter",
                        message = "PixelErrorReporter threw while consuming an event",
                        cause = reporterFailure,
                    ),
                )
            }
        }
    }

    /** Stable Element identity for the per-runtime focus-owner boundary. */
    private companion object {
        /** Key shared across frames of this runtime, while owner identity remains instance-local. */
        const val RuntimeFocusOwnerKey: String = "pixel-runtime-focus-owner"
    }
}

/** 定义 `InspectorNodeAssociation` 在 `PixelUiRuntime` 中承担的数据或执行职责，并保持公开不变量稳定。 */
public data class InspectorNodeAssociation(
    val elementPath: String?,
    val renderPath: String,
)

/**
 * 定义 `PixelUiRuntimeResourceDiagnostics` 在 `PixelUiRuntime` 中承担的数据与行为边界。
 *
 * Primitive-only resource snapshot for one retained [PixelUiRuntime].
 *
 * @property hasRetainedElementRoot Whether a mounted root Element remains owned by the runtime.
 * @property retainedElementCount Number of currently retained Element diagnostics nodes.
 * @property retainedListenableDependencyCount Number of live Element-to-Listenable dependencies.
 * @property pendingDirtyElementCount Number of Elements still waiting for a build pass.
 * @property attachedRenderObjectCount Number of RenderObjects still attached to a pipeline owner.
 */
public data class PixelUiRuntimeResourceDiagnostics(
    val hasRetainedElementRoot: Boolean,
    val retainedElementCount: Int,
    val retainedListenableDependencyCount: Int,
    val pendingDirtyElementCount: Int,
    val attachedRenderObjectCount: Int,
)
