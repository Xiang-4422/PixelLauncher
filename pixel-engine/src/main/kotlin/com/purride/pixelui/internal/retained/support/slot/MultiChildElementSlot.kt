package com.purride.pixelui.internal

import com.purride.pixelui.Widget

/** Retains and reconciles an ordered sibling list for one parent Element. */
internal class MultiChildElementSlot {
    /** Current retained children in declarative and render order. */
    private var children = emptyList<Element>()

    /**
     * 当前 slot 内保留的 child elements。
     */
    val elements: List<Element>
        get() = children

    /**
     * 按 key 优先、索引兜底更新多 child element 列表。
     *
     * 非空 key 允许同一 parent 下的 child 在插入、删除或重排后继续持有原 element；
     * 无 key child 保留原来的按索引 reconcile 语义。重复 key 会被拒绝，避免状态归属不确定。
     */
    fun update(
        owner: BuildOwner,
        parent: Element,
        newWidgets: List<Widget>,
    ) {
        requireUniqueKeys(newWidgets)
        val usedChildren = BooleanArray(children.size)
        val nextChildren = ArrayList<Element>(newWidgets.size)
        newWidgets.forEachIndexed { index, nextWidget ->
            val current = findReusableChild(
                index = index,
                nextWidget = nextWidget,
                usedChildren = usedChildren,
            )
            owner.updateChild(
                parent = parent,
                current = current,
                newWidget = nextWidget,
            )?.let(nextChildren::add)
        }
        children.forEachIndexed { index, child ->
            if (!usedChildren[index]) {
                child.unmount()
            }
        }
        children = nextChildren
    }

    /** Finds one compatible old child without unmounting candidates needed by later keyed items. */
    private fun findReusableChild(
        index: Int,
        nextWidget: Widget,
        usedChildren: BooleanArray,
    ): Element? {
        val indexedChild = children.getOrNull(index)
        if (
            indexedChild != null &&
            !usedChildren[index] &&
            canUpdate(indexedChild.widget, nextWidget)
        ) {
            usedChildren[index] = true
            return indexedChild
        }
        val nextKey = nextWidget.key ?: return null
        val keyedIndex = children.indices.firstOrNull { candidateIndex ->
            !usedChildren[candidateIndex] &&
                children[candidateIndex].widget.key == nextKey &&
                canUpdate(children[candidateIndex].widget, nextWidget)
        } ?: return null
        usedChildren[keyedIndex] = true
        return children[keyedIndex]
    }

    /** Returns whether two widgets can share one retained element. */
    private fun canUpdate(oldWidget: Widget, newWidget: Widget): Boolean {
        return oldWidget::class == newWidget::class && oldWidget.key == newWidget.key
    }

    /** Rejects duplicate non-null keys within one multi-child sibling list. */
    private fun requireUniqueKeys(widgets: List<Widget>) {
        val seenKeys = linkedSetOf<Any>()
        widgets.forEach { widget ->
            val key = widget.key ?: return@forEach
            require(seenKeys.add(key)) {
                "Multi-child widgets must use unique sibling keys; duplicate key=$key"
            }
        }
    }

    /**
     * 遍历当前 child elements。
     */
    fun visit(visitor: (Element) -> Unit) {
        children.forEach(visitor)
    }

    /** Drops terminal sibling references after their complete snapshot has been unmounted. */
    fun clearReferences() {
        children = emptyList()
    }
}
