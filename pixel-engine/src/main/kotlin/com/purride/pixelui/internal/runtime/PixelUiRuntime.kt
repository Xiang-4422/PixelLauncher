package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelui.Widget

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
internal class PixelUiRuntime(
    onVisualUpdate: () -> Unit = { },
) {
    private val bufferPool: PixelBufferPool = PixelBufferPool()
    private val elementTreeRenderer: ElementTreeRenderer = PipelineElementTreeRenderer(bufferPool = bufferPool)
    private val buildRuntime: ElementTreeBuildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = UnsupportedWidgetAdapter,
    )

    fun render(request: WidgetRenderRequest): PixelRenderResult {
        val root = buildRuntime.resolveElementTree(request.root)
        return elementTreeRenderer.render(
            request = ElementTreeRenderRequest(
                root = root,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
            ),
        )
    }

    fun render(root: Widget, logicalWidth: Int, logicalHeight: Int): PixelRenderResult {
        return render(
            request = WidgetRenderRequest(
                root = root,
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
            ),
        )
    }

    fun dispose() {
        elementTreeRenderer.dispose()
        buildRuntime.dispose()
        bufferPool.clear()
    }

    /**
     * 把当前 retained element tree 序列化成可读的 ASCII 缩进字符串。
     *
     * 给运行时调试用：调用 [PixelHostView.dumpElementTree] 时会下沉到这里。
     * 若还没渲染过一帧，返回简单的 "<no root>"。
     */
    fun dumpElementTree(): String {
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

    fun dumpRenderTree(): String {
        return (elementTreeRenderer as? PipelineElementTreeRenderer)?.dumpRenderTree()
            ?: "<render diagnostics unavailable>"
    }

    fun hasPendingBuild(): Boolean {
        return buildRuntime.collectDiagnostics().dirtyQueueDiagnostics.pendingElementCount > 0
    }

    fun collectWidgets(): List<Widget> {
        return buildRuntime.collectWidgets()
    }

    fun collectInspectorNodeAssociations(): Map<RenderObject, InspectorNodeAssociation> {
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
}

internal data class InspectorNodeAssociation(
    val elementPath: String?,
    val renderPath: String,
)
