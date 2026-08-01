package com.purride.pixellockscreen

import android.annotation.SuppressLint
import android.view.ViewGroup

/** Titan 2 SystemUI 视图合同的只读探测结果。 */
internal data class Titan2SystemUiProbeResult(
    /** Keyguard 根视图的实际类名。 */
    val keyguardRootClassName: String,
    /** 通知遮罩窗口根视图的实际类名。 */
    val shadeWindowClassName: String,
    /** 原生 Bouncer 容器的实际类名。 */
    val bouncerContainerClassName: String,
)

/** M5 挂载和回退所需的已验证 SystemUI 视图引用。 */
internal data class Titan2SystemUiBinding(
    /** 自动跟随 Keyguard 可见性的像素宿主父视图。 */
    val keyguardRoot: ViewGroup,
    /** 用于定位普通原生锁屏元素和监听每帧的 SystemUI 窗口。 */
    val shadeWindow: ViewGroup,
    /** 必须始终保留在像素宿主上方的原生凭据容器。 */
    val bouncerContainer: ViewGroup,
) {
    /** 转换为不持有 View 的只读诊断结果。 */
    fun toProbeResult(): Titan2SystemUiProbeResult = Titan2SystemUiProbeResult(
        keyguardRootClassName = keyguardRoot.javaClass.name,
        shadeWindowClassName = shadeWindow.javaClass.name,
        bouncerContainerClassName = bouncerContainer.javaClass.name,
    )
}

/**
 * 只读验证 Titan 2 的 Keyguard 根视图、窗口宿主和原生 Bouncer 容器。
 *
 * 本类不保存、添加、删除或修改任何 SystemUI 视图，用于在 M5 之前证明挂载合同。
 */
internal object Titan2SystemUiProbe {
    /** `KeyguardViewConfigurator` 中持有 Keyguard 根视图的字段名。 */
    private const val KEYGUARD_ROOT_FIELD: String = "keyguardRootView"

    /** `KeyguardViewConfigurator` 中持有整个遮罩窗口的字段名。 */
    private const val SHADE_WINDOW_FIELD: String = "notificationShadeWindowView"

    /** SystemUI 资源中原生 Keyguard 根视图的稳定 entry 名。 */
    private const val KEYGUARD_ROOT_RESOURCE: String = "keyguard_root_view"

    /** SystemUI 资源中原生认证容器的稳定 entry 名。 */
    private const val BOUNCER_CONTAINER_RESOURCE: String = "keyguard_bouncer_container"

    /**
     * 验证实例字段、视图类型、资源 ID 和父子关系。
     *
     * @param configurator 已完成原生 `start()` 的 KeyguardViewConfigurator 实例。
     * @return 不持有视图引用的诊断摘要。
     */
    @SuppressLint("DiscouragedApi")
    fun inspect(configurator: Any): Titan2SystemUiProbeResult {
        return bind(configurator).toProbeResult()
    }

    /**
     * 读取并验证 M5 运行时所需的视图引用。
     *
     * @param configurator 已完成原生 `start()` 的 KeyguardViewConfigurator 实例。
     * @return 通过类型、资源和父子关系检查的挂载绑定。
     */
    @SuppressLint("DiscouragedApi")
    fun bind(configurator: Any): Titan2SystemUiBinding {
        /** 反射获取的 Keyguard 根视图。 */
        val keyguardRoot = readField(configurator, KEYGUARD_ROOT_FIELD) as? ViewGroup
            ?: error("keyguard_root_type")
        /** 反射获取的通知遮罩窗口。 */
        val shadeWindow = readField(configurator, SHADE_WINDOW_FIELD) as? ViewGroup
            ?: error("shade_window_type")
        check(keyguardRoot.parent === shadeWindow) { "keyguard_root_parent" }
        check(resourceEntryName(keyguardRoot) == KEYGUARD_ROOT_RESOURCE) { "keyguard_root_resource" }

        /** 原生 Bouncer 容器的 SystemUI 资源 ID。 */
        val bouncerContainerId = shadeWindow.resources.getIdentifier(
            BOUNCER_CONTAINER_RESOURCE,
            "id",
            LockscreenModuleContract.SYSTEM_UI_PACKAGE,
        )
        check(bouncerContainerId != 0) { "bouncer_resource_missing" }
        /** 必须在同一遮罩窗口内保留的原生 Bouncer 容器。 */
        val bouncerContainer = shadeWindow.findViewById<ViewGroup>(bouncerContainerId)
            ?: error("bouncer_view_missing")
        check(bouncerContainer.parent === shadeWindow) { "bouncer_parent" }
        check(shadeWindow.indexOfChild(bouncerContainer) > shadeWindow.indexOfChild(keyguardRoot)) {
            "bouncer_z_order"
        }

        return Titan2SystemUiBinding(
            keyguardRoot = keyguardRoot,
            shadeWindow = shadeWindow,
            bouncerContainer = bouncerContainer,
        )
    }

    /** 沿类继承链读取私有字段，不改变字段值。 */
    private fun readField(instance: Any, fieldName: String): Any? {
        /** 当前待检查的类，允许厂商子类把字段保留在父类。 */
        var currentClass: Class<*>? = instance.javaClass
        while (currentClass != null) {
            try {
                /** 当前类中同名的声明字段。 */
                val field = currentClass.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(instance)
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        error("field_missing:$fieldName")
    }

    /** 安全读取视图资源 entry 名，无资源 ID 时返回空字符串。 */
    private fun resourceEntryName(viewGroup: ViewGroup): String = runCatching {
        viewGroup.resources.getResourceEntryName(viewGroup.id)
    }.getOrDefault("")
}
