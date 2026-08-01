package com.purride.pixellockscreen

import android.view.View
import android.view.ViewGroup

/** 原生图案页面根视图在隐藏前的可恢复状态。 */
private data class NativePatternViewState(
    /** 被像素认证 UI 临时代替的原生图案根视图。 */
    val view: View,
    /** SystemUI 在事务激活前设置的可见性。 */
    val visibility: Int,
    /** SystemUI 在事务激活前设置的无障碍级别。 */
    val importantForAccessibility: Int,
)

/**
 * 隐藏原生图案页根视图、但保留控制器和紧急按钮对象存活的可恢复事务。
 *
 * 像素宿主加入上层 `KeyguardSecurityContainer`，避免向线性排版的 `KeyguardPatternView`
 * 追加子节点。事务不会移除或销毁任何原生认证对象。
 */
internal class NativePatternVisibilityTransaction(
    /** 可承载像素覆盖层的 Titan 2 主安全容器。 */
    private val securityContainer: ViewGroup,
    /** 位于安全容器后代层级中的原生图案页面根视图。 */
    private val patternView: View,
    /** 不属于原生快照、必须持续可见的像素宿主。 */
    private val pixelHost: View,
) {
    /** 准备阶段捕获的安全容器直属子视图身份和顺序。 */
    private var originalChildren: List<View>? = null

    /** 首次隐藏时捕获的图案根视图可恢复属性。 */
    private var activeState: NativePatternViewState? = null

    /** 在不改变视图属性的前提下捕获原生结构。 */
    fun prepare() {
        check(originalChildren == null) { "pattern_visibility_already_prepared" }
        check(pixelHost.parent == null) { "pattern_pixel_host_already_attached" }
        check(isDescendantOfSecurityContainer()) { "pattern_view_container_parent" }
        check(patternView.visibility == View.VISIBLE) { "pattern_view_not_visible" }
        /** 主安全容器在接入像素宿主前的全部直属子视图。 */
        val children = List(securityContainer.childCount) { index ->
            securityContainer.getChildAt(index)
        }
        check(children.isNotEmpty()) { "pattern_native_children_empty" }
        originalChildren = children
    }

    /** 验证 SystemUI 没有在事务期间替换、增删或重排原生页面结构。 */
    fun isStructureValid(): Boolean {
        /** 准备阶段捕获的原生直属子视图。 */
        val children = originalChildren ?: return false
        if (securityContainer.childCount != children.size + 1) {
            return false
        }
        children.forEachIndexed { index, child ->
            if (securityContainer.getChildAt(index) !== child) {
                return false
            }
        }
        return securityContainer.getChildAt(securityContainer.childCount - 1) === pixelHost &&
            isDescendantOfSecurityContainer()
    }

    /** 捕获原生图案根属性并隐藏页面；重复调用只重新施加隐藏。 */
    fun hide() {
        check(isStructureValid()) { "pattern_native_structure_changed" }
        /** 首次隐藏时保存的图案根可恢复状态。 */
        val state = activeState ?: NativePatternViewState(
            view = patternView,
            visibility = patternView.visibility,
            importantForAccessibility = patternView.importantForAccessibility,
        ).also { capturedState -> activeState = capturedState }
        state.view.visibility = View.INVISIBLE
        state.view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** 恢复首次隐藏前的全部属性，尚未隐藏时为空操作。 */
    fun restore() {
        /** 当前激活事务保存的全部状态。 */
        val state = activeState ?: return
        activeState = null
        state.view.visibility = state.visibility
        state.view.importantForAccessibility = state.importantForAccessibility
    }

    /** 判断图案页面是否仍位于当前主安全容器的后代链中。 */
    private fun isDescendantOfSecurityContainer(): Boolean {
        /** 当前待检查的图案页面或父视图。 */
        var current: View? = patternView
        while (current != null) {
            if (current === securityContainer) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }
}
