package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.internal.InspectorNodeAssociation
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.RenderObject

internal fun PixelRenderResult.toInspectorTargetSnapshots(
    nodeAssociations: Map<RenderObject, InspectorNodeAssociation>,
): List<PixelInspectorTargetSnapshot> {
    return buildList {
        clickTargets.forEachIndexed { index, target ->
            add(target.bounds.toInspectorSnapshot(PixelInspectorTargetKind.CLICK, "#$index", target.source, nodeAssociations))
        }
        pagerTargets.forEachIndexed { index, target ->
            val axis = if (target.axis == PixelAxis.HORIZONTAL) "H" else "V"
            val active = target.controller.isActive(target.state)
            add(
                target.bounds.toInspectorSnapshot(
                    PixelInspectorTargetKind.PAGER,
                    "#$index axis=$axis active=${flag(active)}",
                    target.source,
                    nodeAssociations,
                ),
            )
        }
        listTargets.forEachIndexed { index, target ->
            val active = target.controller.isActive(target.state)
            add(
                target.bounds.toInspectorSnapshot(
                    kind = PixelInspectorTargetKind.LIST,
                    detail = "#$index active=${flag(active)} content=${target.contentHeightPx} viewport=${target.viewportHeightPx}",
                    source = target.source,
                    nodeAssociations = nodeAssociations,
                ),
            )
        }
        scrollbarTargets.forEachIndexed { index, target ->
            add(
                target.bounds.toInspectorSnapshot(
                    kind = PixelInspectorTargetKind.SCROLLBAR,
                    detail = "#$index thumb=${target.thumbBounds.shortLabel()}",
                    source = target.source,
                    nodeAssociations = nodeAssociations,
                ),
            )
        }
        refreshTargets.forEachIndexed { index, target ->
            add(
                target.bounds.toInspectorSnapshot(
                    kind = PixelInspectorTargetKind.REFRESH,
                    detail = "#$index enabled=${flag(target.enabled)} threshold=${target.thresholdPx}",
                    source = target.source,
                    nodeAssociations = nodeAssociations,
                ),
            )
        }
        textInputTargets.forEachIndexed { index, target ->
            add(
                target.bounds.toInspectorSnapshot(
                    kind = PixelInspectorTargetKind.TEXT_INPUT,
                    detail = "#$index readOnly=${flag(target.readOnly)} action=${target.action.name}",
                    source = target.source,
                    nodeAssociations = nodeAssociations,
                ),
            )
        }
        sliderTargets.forEachIndexed { index, target ->
            add(target.bounds.toInspectorSnapshot(PixelInspectorTargetKind.SLIDER, "#$index", target.source, nodeAssociations))
        }
        semanticsNodes.forEachIndexed { index, node ->
            val association = semanticsTargets.getOrNull(index)?.source?.let(nodeAssociations::get)
            add(
                PixelInspectorTargetSnapshot(
                    kind = PixelInspectorTargetKind.SEMANTICS,
                    left = node.left,
                    top = node.top,
                    width = node.width,
                    height = node.height,
                    detail = "#$index ${node.role.name} enabled=${flag(node.enabled)} focused=${flag(node.focused)} label=${node.label}",
                    elementPath = association?.elementPath,
                    renderPath = association?.renderPath,
                ),
            )
        }
    }
}

private fun PixelRect.toInspectorSnapshot(
    kind: PixelInspectorTargetKind,
    detail: String,
    source: RenderObject?,
    nodeAssociations: Map<RenderObject, InspectorNodeAssociation>,
): PixelInspectorTargetSnapshot {
    val association = source?.let(nodeAssociations::get)
    return PixelInspectorTargetSnapshot(
        kind = kind,
        left = left,
        top = top,
        width = width,
        height = height,
        detail = detail,
        elementPath = association?.elementPath,
        renderPath = association?.renderPath,
    )
}

private fun PixelRect.shortLabel(): String = "${left},${top},${width}x${height}"

private fun flag(value: Boolean): Int = if (value) 1 else 0
