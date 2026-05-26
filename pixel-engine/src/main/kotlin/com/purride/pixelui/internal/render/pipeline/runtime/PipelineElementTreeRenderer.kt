package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool

/**
 * 新渲染管线对 retained element tree 的渲染入口。
 */
internal class PipelineElementTreeRenderer private constructor(
    private val owner: PipelineOwner,
) : ElementTreeRenderer {
    constructor(bufferPool: PixelBufferPool = PixelBufferPool()) : this(
        owner = PipelineOwner(bufferPool = bufferPool),
    )

    fun canRender(request: ElementTreeRenderRequest): Boolean = inspect(request).supported

    fun inspect(request: ElementTreeRenderRequest): PipelineCapabilityReport {
        return if (request.root.findPipelineRenderRoot() != null) {
            PipelineCapabilityReport.supported()
        } else {
            PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.MISSING_RENDER_OBJECT_ROOT)
        }
    }

    fun renderOrNull(request: ElementTreeRenderRequest): PixelRenderResult? {
        val renderRoot = request.root.findPipelineRenderRoot() ?: return null
        owner.attachRoot(renderRoot)
        return owner.render(
            logicalWidth = request.logicalWidth,
            logicalHeight = request.logicalHeight,
        )
    }

    override fun render(request: ElementTreeRenderRequest): PixelRenderResult {
        return renderOrNull(request)
            ?: error("当前 element tree 还不能完整走新渲染管线。")
    }

    override fun dispose() {
        owner.dispose()
    }

    fun dumpRenderTree(): String {
        val diagnostics = owner.collectDiagnostics()
        if (diagnostics.isEmpty()) return "<no render root>"
        return buildString {
            for (node in diagnostics) {
                repeat(node.depth) { append("  ") }
                append(node.name)
                node.size?.let { size ->
                    append(" [")
                    append(size.width)
                    append("x")
                    append(size.height)
                    append("]")
                }
                append("\n")
            }
        }.trimEnd()
    }

    private fun Element?.findPipelineRenderRoot(): RenderBox? {
        this ?: return null
        return findRenderObject() as? RenderBox
    }
}

internal data class PipelineCapabilityReport(
    val supported: Boolean,
    val reason: PipelineUnsupportedReason? = null,
) {
    companion object {
        fun supported() = PipelineCapabilityReport(supported = true)
        fun unsupported(reason: PipelineUnsupportedReason) = PipelineCapabilityReport(supported = false, reason = reason)
    }
}

internal enum class PipelineUnsupportedReason {
    MISSING_RENDER_OBJECT_ROOT,
}
