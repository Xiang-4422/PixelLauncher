package com.purride.pixellockscreen

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup

/** 一个原生锁屏视图在当次隐藏事务前的可恢复属性。 */
private data class NativeKeyguardViewState(
    /** 被像素 UI 临时代替的原生视图。 */
    val view: View,
    /** 原生 SystemUI 在事务开始时设置的可见性。 */
    val visibility: Int,
    /** 原生 SystemUI 在事务开始时设置的无障碍级别。 */
    val importantForAccessibility: Int,
)

/**
 * 只在像素 Keyguard 可见时隐藏普通原生锁屏元素，并可原子恢复事务前状态。
 *
 * 原生 Bouncer、紧急容器、指纹认证表面和窗口根节点不属于本事务，不得被隐藏或删除。
 */
internal class NativeKeyguardVisibilityTransaction(
    /** 只承载普通锁屏视觉内容的 Titan 2 Keyguard 根容器。 */
    private val keyguardRoot: ViewGroup,
    /** 包含所有待定位原生节点的 SystemUI 窗口。 */
    private val shadeWindow: ViewGroup,
    /** 当前事务必须排除并保持可见的像素锁屏宿主。 */
    private val pixelHost: View,
) {
    /** 当次激活事务捕获的原生视图属性。 */
    private var activeStates: MutableList<NativeKeyguardViewState>? = null

    /** 启动前一次性验证所有必需节点都存在，不改变任何属性。 */
    fun prepare() {
        check(keyguardRoot.parent === shadeWindow) { "keyguard_root_parent_changed" }
        check(pixelHost.parent == null) { "pixel_host_already_attached" }
        resolveAnchoredViews()
    }

    /** 捕获当前原生状态并隐藏普通锁屏元素；重复调用只重新施加隐藏。 */
    fun hide() {
        /** 首次接管解析全部锚点，后续帧只检查根容器是否追加了普通锁屏子节点。 */
        val states = activeStates ?: mutableListOf<NativeKeyguardViewState>().also { newStates ->
            captureNewStates(resolveViews(resolveAnchoredViews()), newStates)
            activeStates = newStates
        }
        captureNewStates(resolveRootChildren(), states)
        states.forEach { state ->
            state.view.visibility = View.INVISIBLE
            state.view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    /** 为尚未进入事务的节点记录一次可恢复属性。 */
    private fun captureNewStates(
        views: List<View>,
        states: MutableList<NativeKeyguardViewState>,
    ) {
        views.forEach { view ->
            if (states.none { state -> state.view === view }) {
                states += NativeKeyguardViewState(
                    view = view,
                    visibility = view.visibility,
                    importantForAccessibility = view.importantForAccessibility,
                )
            }
        }
    }

    /** 恢复当次事务捕获的原生属性，未激活时为空操作。 */
    fun restore() {
        /** 当次事务捕获的全部状态。 */
        val states = activeStates ?: return
        activeStates = null
        states.forEach { state ->
            state.view.visibility = state.visibility
            state.view.importantForAccessibility = state.importantForAccessibility
        }
    }

    /**
     * 收集普通 Keyguard 根容器的全部原有直接子分支，以及位于根容器外的已知普通锁屏节点。
     *
     * Bouncer 已由 [Titan2SystemUiProbe] 证明是 [shadeWindow] 的更高层同级节点，不会进入本集合；
     * 像素宿主始终显式排除，从而在透明壁纸上只留下像素 UI。
     */
    private fun resolveViews(anchoredViews: List<View>): List<View> {
        /** 位于 KeyguardRootView 外部、仍需单独隐藏的通知或设备入口节点。 */
        val externalAnchors = anchoredViews.filterNot { view ->
            view === keyguardRoot || view.isDescendantOf(keyguardRoot)
        }
        return (resolveRootChildren() + externalAnchors)
            .distinctBy { view -> System.identityHashCode(view) }
    }

    /** 收集当前根容器中除像素宿主之外的普通锁屏直接子分支。 */
    private fun resolveRootChildren(): List<View> {
        /** KeyguardRootView 当前承载的普通原生锁屏分支。 */
        return buildList {
            repeat(keyguardRoot.childCount) { index ->
                /** 当前根容器中的一个直接子节点。 */
                val child = keyguardRoot.getChildAt(index)
                if (child !== pixelHost) add(child)
            }
        }
    }

    /** 通过精确 SystemUI 资源 entry 定位必需节点，并收集当前实际存在的可选节点。 */
    @SuppressLint("DiscouragedApi")
    private fun resolveAnchoredViews(): List<View> {
        /** 所有 Titan 2 普通锁屏都必须提供的原生节点。 */
        val requiredViews = REQUIRED_NATIVE_VIEW_RESOURCE_NAMES.map { resourceName ->
            /** 当前必需节点在 SystemUI 资源表中的 ID。 */
            val resourceId = resolveResourceId(resourceName)
            check(resourceId != 0) { "native_resource_missing:$resourceName" }
            shadeWindow.findViewById<View>(resourceId)
                ?: error("native_view_missing:$resourceName")
        }
        /** 只在对应硬件或 ROM 功能启用时才进入当前窗口层级的原生节点。 */
        val optionalViews = OPTIONAL_NATIVE_VIEW_RESOURCE_NAMES.mapNotNull { resourceName ->
            /** 当前可选节点在 SystemUI 资源表中的 ID，不存在时保持为空。 */
            val resourceId = resolveResourceId(resourceName)
            if (resourceId == 0) null else shadeWindow.findViewById<View>(resourceId)
        }
        return (requiredViews + optionalViews).distinctBy(View::getId)
    }

    /** 判断当前视图是否位于指定祖先容器内部。 */
    private fun View.isDescendantOf(ancestor: ViewGroup): Boolean {
        /** 沿父链向上检查的当前节点。 */
        var currentParent = parent
        while (currentParent is View) {
            if (currentParent === ancestor) return true
            currentParent = currentParent.parent
        }
        return false
    }

    /** 按固定 SystemUI 包名解析一个资源 entry。 */
    private fun resolveResourceId(resourceName: String): Int = shadeWindow.resources.getIdentifier(
        resourceName,
        "id",
        LockscreenModuleContract.SYSTEM_UI_PACKAGE,
    )

    internal companion object {
        /**
         * M5 需要替代的普通 Keyguard 元素；列表故意不含 Bouncer 和安全表面。
         */
        val REQUIRED_NATIVE_VIEW_RESOURCE_NAMES: List<String> = listOf(
            "keyguard_status_view",
            "keyguard_header",
            "keyguard_bottom_area",
            "shared_notification_container",
        )

        /** 资源可能存在但只在对应硬件能力启用时加入窗口的普通 Keyguard 元素。 */
        val OPTIONAL_NATIVE_VIEW_RESOURCE_NAMES: List<String> = listOf(
            "device_entry_icon_view",
            "lock_icon_view",
        )

        /** 普通锁屏接管允许触及的完整显隐白名单。 */
        val NATIVE_VIEW_RESOURCE_NAMES: List<String> =
            REQUIRED_NATIVE_VIEW_RESOURCE_NAMES + OPTIONAL_NATIVE_VIEW_RESOURCE_NAMES
    }
}
