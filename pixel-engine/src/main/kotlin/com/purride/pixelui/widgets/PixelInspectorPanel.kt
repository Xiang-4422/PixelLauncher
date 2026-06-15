package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 交互式 inspector 面板。
 *
 * 面板接收一次 [PixelInspectorSnapshot]，默认展示概览信息；点击顶部 tab 可在
 * element tree、render tree 和 semantics tree 之间切换。它适合放在 debug scene、
 * overlay drawer 或崩溃诊断页里，不应在每帧无条件重建大型树字符串。
 */
public fun PixelInspectorPanel(
    snapshot: PixelInspectorSnapshot,
    key: Any? = null,
    maxTreeLines: Int = 12,
    selectedTarget: PixelInspectorTargetSnapshot? = null,
): Widget {
    require(maxTreeLines > 0) { "maxTreeLines must be > 0" }
    return PixelInspectorPanelWidget(
        snapshot = snapshot,
        maxTreeLines = maxTreeLines,
        selectedTarget = selectedTarget,
        key = key,
    )
}

private enum class PixelInspectorPanelTab(
    val label: String,
) {
    Overview("INFO"),
    Element("ELEMENT"),
    Render("RENDER"),
    Semantics("SEM"),
    Targets("TARGETS"),
}

private class PixelInspectorPanelWidget(
    val snapshot: PixelInspectorSnapshot,
    val maxTreeLines: Int,
    val selectedTarget: PixelInspectorTargetSnapshot?,
    key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PixelInspectorPanelState()
}

private class PixelInspectorPanelState : State<PixelInspectorPanelWidget>() {
    private var selectedTab: PixelInspectorPanelTab = PixelInspectorPanelTab.Overview

    override fun build(context: BuildContext): Widget {
        val snapshot = widget.snapshot
        val tabs = PixelInspectorPanelTab.entries
        return Container(
            padding = EdgeInsets.all(2),
            fillColor = PixelColor.fromRgb(8, 8, 8),
            borderColor = PixelColor.fromRgb(96, 96, 96),
            child = Column(
                crossAxisAlignment = CrossAxisAlignment.START,
                spacing = 2,
                children = listOf(
                    Text("INSPECTOR", style = headerStyle),
                    Wrap(
                        spacing = 1,
                        runSpacing = 1,
                        children = tabs.map { tab ->
                            OutlinedButton(
                                text = tab.label,
                                onPressed = {
                                    setState { selectedTab = tab }
                                },
                                borderColor = if (tab == selectedTab) selectedBorderColor else panelBorderColor,
                            )
                        },
                    ),
                    buildPanelBody(snapshot),
                ),
            ),
            key = widget.key,
        )
    }

    private fun buildPanelBody(snapshot: PixelInspectorSnapshot): Widget {
        return when (selectedTab) {
            PixelInspectorPanelTab.Overview -> buildOverview(snapshot)
            PixelInspectorPanelTab.Element -> buildTree("ELEMENT TREE", snapshot.elementTree)
            PixelInspectorPanelTab.Render -> buildTree("RENDER TREE", snapshot.renderTree)
            PixelInspectorPanelTab.Semantics -> buildTree("SEMANTICS TREE", snapshot.semanticsTree)
            PixelInspectorPanelTab.Targets -> buildTargets(snapshot.targetSnapshots, widget.selectedTarget)
        }
    }

    private fun buildOverview(snapshot: PixelInspectorSnapshot): Widget {
        val targets = snapshot.targetCounts
        val lines = buildList {
            snapshot.frameStats?.let { stats ->
                add("FPS ${stats.fpsAvg.toInt()}  MS ${stats.deltaMs}  PAINT ${stats.paintTimeNanos / 1_000_000}M")
            }
            snapshot.allocationSample?.let { sample ->
                add("MEM ${formatInspectorKilobytes(sample.usedHeapBytes)}K/${formatInspectorKilobytes(sample.maxHeapBytes)}K")
            }
            add("TARGET C${targets.click} P${targets.pager} L${targets.list} T${targets.textInput}")
            add("TARGET S${targets.slider} SB${targets.scrollbar} R${targets.refresh} SEM${targets.semantics}")
            add("STATE PEND ${flag(snapshot.hasPendingBuild)} FOCUS ${flag(snapshot.focusedTextInput)}")
            add("ACTIVE P${snapshot.activePagerCount} L${snapshot.activeListCount} SL${flag(snapshot.activeSlider)}")
            add("ACTIVE SB${flag(snapshot.activeScrollbar)} RF${flag(snapshot.activeRefresh)}")
        }
        return inspectorLines(lines)
    }

    private fun buildTargets(
        targets: List<PixelInspectorTargetSnapshot>,
        selectedTarget: PixelInspectorTargetSnapshot?,
    ): Widget {
        val shown = targets.take(widget.maxTreeLines)
        val overflow = targets.size - shown.size
        val children = buildList {
            add(Text("TARGET BOUNDS", style = bodyStyle))
            if (shown.isEmpty()) add(Text("<empty>", style = bodyStyle))
            shown.forEach { target ->
                val line = "${target.kind.name} ${target.left},${target.top} " +
                    "${target.width}x${target.height} ${target.detail}".trimEnd()
                add(Text(line, style = if (target == selectedTarget) selectedTargetStyle else bodyStyle))
            }
            if (overflow > 0) add(Text("... +$overflow", style = bodyStyle))
        }
        return Column(
            crossAxisAlignment = CrossAxisAlignment.START,
            spacing = 1,
            children = children,
        )
    }

    private fun buildTree(title: String, tree: String): Widget {
        val allLines = if (tree.isBlank()) listOf("<empty>") else tree.lines()
        val shown = allLines.take(widget.maxTreeLines)
        val overflow = allLines.size - shown.size
        val lines = buildList {
            add(title)
            addAll(shown)
            if (overflow > 0) add("... +$overflow")
        }
        return inspectorLines(lines)
    }

    private fun inspectorLines(lines: List<String>): Widget {
        return Column(
            crossAxisAlignment = CrossAxisAlignment.START,
            spacing = 1,
            children = lines.map { line ->
                Text(line, style = bodyStyle, maxLines = 1, overflow = TextOverflow.CLIP)
            },
        )
    }

    private fun flag(value: Boolean): Int = if (value) 1 else 0
}

private val headerStyle = TextStyle(color = PixelColor.fromRgb(200, 255, 64))
private val bodyStyle = TextStyle(color = PixelColor.fromRgb(180, 180, 180))
private val selectedTargetStyle = TextStyle(color = PixelColor.fromRgb(200, 255, 64))
private val selectedBorderColor = PixelColor.fromRgb(80, 180, 110)
private val panelBorderColor = PixelColor.fromRgb(96, 96, 96)

private fun formatInspectorKilobytes(bytes: Long): Long {
    return (bytes.coerceAtLeast(0L) + 1023L) / 1024L
}
