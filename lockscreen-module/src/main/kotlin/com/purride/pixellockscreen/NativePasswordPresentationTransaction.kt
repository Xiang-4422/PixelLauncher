package com.purride.pixellockscreen

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.EditText

/** 密码页中一个被像素层代替的原生视图分支及其可恢复状态。 */
private data class NativePasswordBranchState(
    /** 需要临时隐藏的原生分支根视图。 */
    val view: View,
    /** 准备阶段记录的直接父对象。 */
    val parent: ViewParent?,
    /** SystemUI 在接管前设置的可见性。 */
    val visibility: Int,
    /** SystemUI 在接管前设置的无障碍级别。 */
    val importantForAccessibility: Int,
)

/** 原生密码输入连接在透明化前的全部可恢复绘制属性。 */
private data class NativePasswordEntryState(
    /** SystemUI 原生密码输入连接。 */
    val entry: EditText,
    /** 原生文字颜色状态表。 */
    val textColors: ColorStateList,
    /** 原生提示文字颜色状态表。 */
    val hintTextColors: ColorStateList,
    /** 原生链接文字颜色状态表。 */
    val linkTextColors: ColorStateList,
    /** 原生输入框背景对象。 */
    val background: Drawable?,
    /** 原生光标可见状态。 */
    val cursorVisible: Boolean,
    /** 原生选择高亮颜色。 */
    val highlightColor: Int,
)

/** 原生密码根视图在像素接管前的可恢复无障碍状态。 */
private data class NativePasswordRootState(
    /** 必须持续保持可见的密码根视图。 */
    val root: ViewGroup,
    /** 接管前的无障碍级别。 */
    val importantForAccessibility: Int,
)

/**
 * 在保留原生 `EditText` 输入连接的前提下替换密码页全部可见内容的可恢复事务。
 *
 * SystemUI 的密码恢复、暂停和 IME 动画会调用隐藏 API `isVisibleToUser()`，因此事务绝不修改
 * 密码根视图、输入框或其祖先链的可见性和 alpha。像素首帧完成后，只隐藏输入框祖先链之外
 * 的分支，并把输入框文字、提示、背景、光标和选择色透明化；恢复时逐项还原原值。
 */
internal class NativePasswordPresentationTransaction(
    /** 可承载像素覆盖层的 Titan 2 主安全容器。 */
    private val securityContainer: ViewGroup,
    /** 必须持续可见以维持 IME 合同的原生密码根视图。 */
    private val passwordView: ViewGroup,
    /** 唯一保留的原生密码输入连接。 */
    private val passwordEntry: EditText,
    /** 不属于原生快照、必须持续可见的像素宿主。 */
    private val pixelHost: View,
) {
    /** 准备阶段捕获的安全容器直属子视图身份和顺序。 */
    private var originalContainerChildren: List<View>? = null

    /** 从输入框到密码根视图的稳定祖先链。 */
    private var passwordEntryPath: List<View>? = null

    /** 首次隐藏时捕获的全部原生非输入分支。 */
    private var activeBranches: List<NativePasswordBranchState>? = null

    /** 首次隐藏时捕获的原生输入框绘制状态。 */
    private var activeEntryState: NativePasswordEntryState? = null

    /** 首次隐藏时捕获的密码根无障碍状态。 */
    private var activeRootState: NativePasswordRootState? = null

    /** 在不改变任何视图属性的前提下捕获容器和输入祖先结构。 */
    fun prepare() {
        check(originalContainerChildren == null) { "password_presentation_already_prepared" }
        check(pixelHost.parent == null) { "password_pixel_host_already_attached" }
        check(passwordView.isDescendantOf(securityContainer)) {
            "password_view_container_parent"
        }
        check(passwordEntry.isDescendantOf(passwordView)) { "password_entry_root_parent" }
        check(passwordView.visibility == View.VISIBLE) { "password_view_not_visible" }
        check(passwordEntry.visibility == View.VISIBLE) { "password_entry_not_visible" }
        check(passwordView.alpha > MINIMUM_VISIBLE_ALPHA) { "password_view_alpha" }
        check(passwordEntry.alpha > MINIMUM_VISIBLE_ALPHA) { "password_entry_alpha" }
        /** 主安全容器在接入像素宿主前的直属子视图。 */
        val children = List(securityContainer.childCount) { index ->
            securityContainer.getChildAt(index)
        }
        check(children.isNotEmpty()) { "password_native_children_empty" }
        originalContainerChildren = children
        passwordEntryPath = buildEntryPath()
    }

    /** 验证容器子节点、密码根和输入祖先链均未在事务期间被 SystemUI 替换。 */
    fun isStructureValid(): Boolean {
        /** 准备阶段捕获的原生容器子节点。 */
        val children = originalContainerChildren ?: return false
        /** 准备阶段捕获的输入祖先链。 */
        val entryPath = passwordEntryPath ?: return false
        if (securityContainer.childCount != children.size + 1) {
            return false
        }
        children.forEachIndexed { index, child ->
            if (securityContainer.getChildAt(index) !== child) {
                return false
            }
        }
        if (securityContainer.getChildAt(securityContainer.childCount - 1) !== pixelHost) {
            return false
        }
        if (!passwordView.isDescendantOf(securityContainer)) {
            return false
        }
        return buildEntryPathOrNull() == entryPath &&
            activeBranches.orEmpty().all { state -> state.view.parent === state.parent }
    }

    /** 首次捕获原生绘制状态并持续施加透明展示；不会改变输入连接可见性或 alpha。 */
    fun hide() {
        check(isStructureValid()) { "password_native_structure_changed" }
        if (activeBranches == null) {
            activeBranches = collectHiddenBranches().map { view ->
                NativePasswordBranchState(
                    view = view,
                    parent = view.parent,
                    visibility = view.visibility,
                    importantForAccessibility = view.importantForAccessibility,
                )
            }
            activeEntryState = NativePasswordEntryState(
                entry = passwordEntry,
                textColors = passwordEntry.textColors,
                hintTextColors = passwordEntry.hintTextColors,
                linkTextColors = passwordEntry.linkTextColors,
                background = passwordEntry.background,
                cursorVisible = passwordEntry.isCursorVisible,
                highlightColor = passwordEntry.highlightColor,
            )
            activeRootState = NativePasswordRootState(
                root = passwordView,
                importantForAccessibility = passwordView.importantForAccessibility,
            )
        }
        activeBranches.orEmpty().forEach { state ->
            state.view.visibility = View.INVISIBLE
            state.view.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        passwordView.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        passwordEntry.setTextColor(Color.TRANSPARENT)
        passwordEntry.setHintTextColor(Color.TRANSPARENT)
        passwordEntry.setLinkTextColor(Color.TRANSPARENT)
        passwordEntry.background = null
        passwordEntry.isCursorVisible = false
        passwordEntry.highlightColor = Color.TRANSPARENT
    }

    /** 恢复首次隐藏前的全部分支、输入框绘制和无障碍属性。 */
    fun restore() {
        /** 当前激活事务保存的非输入分支。 */
        val branches = activeBranches
        /** 当前激活事务保存的输入框状态。 */
        val entryState = activeEntryState
        /** 当前激活事务保存的密码根状态。 */
        val rootState = activeRootState
        activeBranches = null
        activeEntryState = null
        activeRootState = null
        branches.orEmpty().forEach { state ->
            state.view.visibility = state.visibility
            state.view.importantForAccessibility = state.importantForAccessibility
        }
        if (entryState != null) {
            entryState.entry.setTextColor(entryState.textColors)
            entryState.entry.setHintTextColor(entryState.hintTextColors)
            entryState.entry.setLinkTextColor(entryState.linkTextColors)
            entryState.entry.background = entryState.background
            entryState.entry.isCursorVisible = entryState.cursorVisible
            entryState.entry.highlightColor = entryState.highlightColor
        }
        if (rootState != null) {
            rootState.root.importantForAccessibility = rootState.importantForAccessibility
        }
    }

    /** 收集输入祖先链之外的最上层分支，避免重复隐藏同一棵子树。 */
    private fun collectHiddenBranches(): List<View> {
        /** 当前稳定输入祖先链的身份集合。 */
        val path = passwordEntryPath.orEmpty().toSet()
        /** 所有应由像素层替代的原生分支。 */
        val branches = mutableListOf<View>()
        /** 沿输入祖先链逐级检查同级分支。 */
        var parent: ViewGroup = passwordView
        while (true) {
            /** 当前祖先节点的路径内直接子节点。 */
            val pathChild = (0 until parent.childCount)
                .map(parent::getChildAt)
                .singleOrNull(path::contains)
                ?: error("password_entry_path_child")
            repeat(parent.childCount) { index ->
                /** 当前待分类的直接子视图。 */
                val child = parent.getChildAt(index)
                if (child !== pathChild) {
                    branches += child
                }
            }
            if (pathChild === passwordEntry) {
                return branches
            }
            parent = pathChild as? ViewGroup ?: error("password_entry_path_group")
        }
    }

    /** 构建从原生密码输入框到密码根视图的身份链。 */
    private fun buildEntryPath(): List<View> =
        buildEntryPathOrNull() ?: error("password_entry_path")

    /** 结构异常时返回空，避免验证阶段抛出并跳过原生恢复。 */
    private fun buildEntryPathOrNull(): List<View>? {
        /** 从输入框向上收集的视图链。 */
        val reversePath = mutableListOf<View>()
        /** 当前待检查的输入框或祖先。 */
        var current: View? = passwordEntry
        while (current != null) {
            reversePath += current
            if (current === passwordView) {
                return reversePath.reversed()
            }
            current = current.parent as? View
        }
        return null
    }

    /** 判断当前视图是否位于指定祖先的后代链中。 */
    private fun View.isDescendantOf(ancestor: View): Boolean {
        /** 当前待检查的视图或父视图。 */
        var current: View? = this
        while (current != null) {
            if (current === ancestor) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private companion object {
        /** 根视图或输入框 alpha 低于该值时拒绝依赖其 IME 可见性合同。 */
        const val MINIMUM_VISIBLE_ALPHA: Float = 0.01f
    }
}
