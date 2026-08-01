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
    /** 包含所有待定位原生节点的 SystemUI 窗口。 */
    private val shadeWindow: ViewGroup,
) {
    /** 当次激活事务捕获的原生视图属性。 */
    private var activeStates: List<NativeKeyguardViewState>? = null

    /** 启动前一次性验证所有必需节点都存在，不改变任何属性。 */
    fun prepare() {
        resolveViews()
    }

    /** 捕获当前原生状态并隐藏普通锁屏元素；重复调用只重新施加隐藏。 */
    fun hide() {
        /** 首次隐藏时捕获的可恢复属性。 */
        val states = activeStates ?: resolveViews().map { view ->
            NativeKeyguardViewState(
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

    /** 通过精确 SystemUI 资源 entry 定位必需节点，并收集当前实际存在的可选节点。 */
    @SuppressLint("DiscouragedApi")
    private fun resolveViews(): List<View> {
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
        )

        /** 普通锁屏接管允许触及的完整显隐白名单。 */
        val NATIVE_VIEW_RESOURCE_NAMES: List<String> =
            REQUIRED_NATIVE_VIEW_RESOURCE_NAMES + OPTIONAL_NATIVE_VIEW_RESOURCE_NAMES
    }
}
