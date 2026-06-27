package com.purride.pixelui.internal.host

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.purride.pixelcore.PixelGridGeometry
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode

@Suppress("DEPRECATION")
internal class PixelHostAccessibilityNodeProvider(
    private val host: PixelHostView,
) : AccessibilityNodeProvider() {
    private var lastSignature: String = ""

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        return if (virtualViewId == View.NO_ID) {
            createHostNodeInfo()
        } else {
            createVirtualNodeInfo(virtualViewId)
        }
    }

    override fun findAccessibilityNodeInfosByText(
        searched: String?,
        virtualViewId: Int,
    ): MutableList<AccessibilityNodeInfo> {
        if (searched.isNullOrBlank()) return mutableListOf()
        return snapshots()
            .filter { snapshot -> snapshot.label.contains(searched, ignoreCase = true) }
            .mapNotNull { snapshot -> createVirtualNodeInfo(snapshot.virtualViewId) }
            .toMutableList()
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (virtualViewId == View.NO_ID) return host.performAccessibilityAction(action, arguments)
        if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
        val snapshot = snapshots().firstOrNull { it.virtualViewId == virtualViewId } ?: return false
        return performClick(snapshot)
    }

    fun notifySemanticsChanged() {
        val signature = snapshots().joinToString("|") { snapshot ->
            "${snapshot.virtualViewId}:${snapshot.role}:${snapshot.label}:${snapshot.enabled}:${snapshot.focused}:${snapshot.bounds}"
        }
        if (signature == lastSignature) return
        lastSignature = signature
        host.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun createHostNodeInfo(): AccessibilityNodeInfo {
        val info = AccessibilityNodeInfo.obtain(host)
        host.onInitializeAccessibilityNodeInfo(info)
        info.className = PixelHostView::class.java.name
        info.packageName = host.context.packageName
        snapshots().forEach { snapshot -> info.addChild(host, snapshot.virtualViewId) }
        return info
    }

    private fun createVirtualNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        val snapshot = snapshots().firstOrNull { it.virtualViewId == virtualViewId } ?: return null
        val info = AccessibilityNodeInfo.obtain()
        info.setSource(host, virtualViewId)
        info.setParent(host)
        info.className = snapshot.className
        info.packageName = host.context.packageName
        info.text = snapshot.label
        info.contentDescription = snapshot.label
        info.isEnabled = snapshot.enabled
        info.isFocused = snapshot.focused
        info.isFocusable = true
        info.isVisibleToUser = host.isShown
        info.isClickable = snapshot.clickable
        if (snapshot.role == PixelSemanticRole.TEXT_FIELD) {
            info.isEditable = true
        }
        if (snapshot.clickable) {
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val parentBounds = snapshot.bounds.toRect()
        info.setBoundsInParent(parentBounds)
        val screenOffset = IntArray(2)
        host.getLocationOnScreen(screenOffset)
        info.setBoundsInScreen(
            Rect(
                parentBounds.left + screenOffset[0],
                parentBounds.top + screenOffset[1],
                parentBounds.right + screenOffset[0],
                parentBounds.bottom + screenOffset[1],
            ),
        )
        return info
    }

    private fun performClick(snapshot: PixelAccessibilityNodeSnapshot): Boolean {
        if (!snapshot.enabled || !snapshot.clickable) return false
        val textInputTarget = host.resolveTextInputTarget(snapshot.centerLogicalX, snapshot.centerLogicalY)
        if (textInputTarget != null) {
            host.focusTextInput(textInputTarget)
            host.invalidate()
            return true
        }
        val clickTarget = host.resolveClickTarget(snapshot.centerLogicalX, snapshot.centerLogicalY) ?: return false
        clickTarget.onClick()
        host.performClick()
        host.invalidate()
        return true
    }

    private fun snapshots(): List<PixelAccessibilityNodeSnapshot> {
        val geometry = host.resolveGridGeometry() ?: return emptyList()
        return buildPixelAccessibilityNodeSnapshots(
            semanticsNodes = host.lastRenderResult?.semanticsNodes.orEmpty(),
            geometry = geometry,
        )
    }
}

internal data class PixelAccessibilityNodeSnapshot(
    val virtualViewId: Int,
    val label: String,
    val role: PixelSemanticRole,
    val enabled: Boolean,
    val focused: Boolean,
    val bounds: PixelAccessibilityBounds,
    val centerLogicalX: Int,
    val centerLogicalY: Int,
    val className: String,
    val clickable: Boolean,
)

internal data class PixelAccessibilityBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

internal fun buildPixelAccessibilityNodeSnapshots(
    semanticsNodes: List<PixelSemanticsNode>,
    geometry: PixelGridGeometry,
): List<PixelAccessibilityNodeSnapshot> {
    return semanticsNodes.mapIndexedNotNull { index, node ->
        val bounds = node.toAccessibilityBounds(geometry) ?: return@mapIndexedNotNull null
        PixelAccessibilityNodeSnapshot(
            virtualViewId = index + 1,
            label = node.label,
            role = node.role,
            enabled = node.enabled,
            focused = node.focused,
            bounds = bounds,
            centerLogicalX = node.left + (node.width / 2),
            centerLogicalY = node.top + (node.height / 2),
            className = node.role.androidClassName,
            clickable = node.role.isAndroidClickable,
        )
    }
}

private fun PixelSemanticsNode.toAccessibilityBounds(geometry: PixelGridGeometry): PixelAccessibilityBounds? {
    if (width <= 0 || height <= 0) return null
    val leftPx = (geometry.originX + left * geometry.cellSize).toInt()
    val topPx = (geometry.originY + top * geometry.cellSize).toInt()
    val rightPx = (geometry.originX + (left + width) * geometry.cellSize).toInt()
    val bottomPx = (geometry.originY + (top + height) * geometry.cellSize).toInt()
    if (rightPx <= leftPx || bottomPx <= topPx) return null
    return PixelAccessibilityBounds(leftPx, topPx, rightPx, bottomPx)
}

private val PixelSemanticRole.androidClassName: String
    get() = when (this) {
        PixelSemanticRole.TEXT -> "android.widget.TextView"
        PixelSemanticRole.BUTTON -> "android.widget.Button"
        PixelSemanticRole.TEXT_FIELD -> "android.widget.EditText"
        PixelSemanticRole.CHECKBOX -> "android.widget.CheckBox"
        PixelSemanticRole.SWITCH -> "android.widget.Switch"
        PixelSemanticRole.TAB -> "android.widget.Button"
        PixelSemanticRole.GENERIC -> "android.view.View"
    }

private val PixelSemanticRole.isAndroidClickable: Boolean
    get() = when (this) {
        PixelSemanticRole.BUTTON,
        PixelSemanticRole.TEXT_FIELD,
        PixelSemanticRole.CHECKBOX,
        PixelSemanticRole.SWITCH,
        PixelSemanticRole.TAB,
        -> true
        PixelSemanticRole.TEXT,
        PixelSemanticRole.GENERIC,
        -> false
    }
