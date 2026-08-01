package com.purride.pixellockscreen

import android.view.View
import android.view.ViewGroup

/** 一个原生图案页面直属子视图在隐藏前的可恢复状态。 */
private data class NativePatternChildState(
    /** 被像素认证 UI 临时代替的原生子视图。 */
    val view: View,
    /** SystemUI 在事务激活前设置的可见性。 */
    val visibility: Int,
    /** SystemUI 在事务激活前设置的无障碍级别。 */
    val importantForAccessibility: Int,
)

/**
 * 隐藏原生图案页直属内容、但保留控制器和紧急按钮对象存活的可恢复事务。
 *
 * 像素宿主作为同一原生根视图的最后一个子节点，因此页面切换、转场和窗口生命周期仍由
 * SystemUI 控制；事务不会移除或销毁任何原生认证对象。
 */
internal class NativePatternVisibilityTransaction(
    /** Titan 2 原生图案页面根视图。 */
    private val patternView: ViewGroup,
    /** 不属于原生快照、必须持续可见的像素宿主。 */
    private val pixelHost: View,
) {
    /** 准备阶段捕获的原生直属子视图身份和顺序。 */
    private var originalChildren: List<View>? = null

    /** 首次隐藏时捕获的可恢复属性。 */
    private var activeStates: List<NativePatternChildState>? = null

    /** 在不改变视图属性的前提下捕获原生结构。 */
    fun prepare() {
        check(originalChildren == null) { "pattern_visibility_already_prepared" }
        check(pixelHost.parent == null) { "pattern_pixel_host_already_attached" }
        /** 当前页面在接入像素宿主前的全部直属子视图。 */
        val children = List(patternView.childCount) { index -> patternView.getChildAt(index) }
        check(children.isNotEmpty()) { "pattern_native_children_empty" }
        originalChildren = children
    }

    /** 验证 SystemUI 没有在事务期间替换、增删或重排原生页面结构。 */
    fun isStructureValid(): Boolean {
        /** 准备阶段捕获的原生直属子视图。 */
        val children = originalChildren ?: return false
        if (patternView.childCount != children.size + 1) {
            return false
        }
        children.forEachIndexed { index, child ->
            if (patternView.getChildAt(index) !== child) {
                return false
            }
        }
        return patternView.getChildAt(patternView.childCount - 1) === pixelHost
    }

    /** 捕获原生属性并隐藏原生内容；重复调用只重新施加隐藏。 */
    fun hide() {
        check(isStructureValid()) { "pattern_native_structure_changed" }
        /** 首次隐藏时保存的全部可恢复状态。 */
        val states = activeStates ?: requireNotNull(originalChildren).map { view ->
            NativePatternChildState(
                view = view,
                visibility = view.visibility,
                importantForAccessibility = view.importantForAccessibility,
            )
        }.also { capturedStates -> activeStates = capturedStates }
        states.forEach { state ->
            state.view.visibility = View.INVISIBLE
            state.view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    /** 恢复首次隐藏前的全部属性，尚未隐藏时为空操作。 */
    fun restore() {
        /** 当前激活事务保存的全部状态。 */
        val states = activeStates ?: return
        activeStates = null
        states.forEach { state ->
            state.view.visibility = state.visibility
            state.view.importantForAccessibility = state.importantForAccessibility
        }
    }
}
