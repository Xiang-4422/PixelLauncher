package com.purride.pixellockscreen

import java.lang.reflect.Field

/**
 * 只读解析 Titan 2 `KeyguardViewMediator` 维护的真实锁屏显示状态。
 *
 * View 的 visibility 与 alpha 会在熄屏、亮屏和 Bouncer 转场中短暂变化，不能作为 Keyguard
 * 是否仍在显示的真值；本适配器只读取 SystemUI 已维护的 `mShowing` 与 `mOccluded`。
 */
internal class Titan2KeyguardVisibilityAdapter private constructor(
    /** 当前 SystemUI 进程中的原生 KeyguardViewMediator。 */
    private val mediator: Any,
    /** 原生 Keyguard 是否仍由系统声明为显示中的字段。 */
    private val showingField: Field,
    /** 原生 Keyguard 是否被其他系统界面遮挡的字段。 */
    private val occludedField: Field,
) {
    /** 返回普通锁屏是否应显示；被外部界面遮挡时暂停像素视觉接管。 */
    fun isOrdinaryKeyguardVisible(): Boolean =
        showingField.getBoolean(mediator) && !occludedField.getBoolean(mediator)

    internal companion object {
        /** Titan 2 目标构建中 Keyguard 状态协调器的精确类名。 */
        private const val MEDIATOR_CLASS: String =
            "com.android.systemui.keyguard.KeyguardViewMediator"

        /** SystemUI 表示 Keyguard 正在显示的字段名。 */
        private const val SHOWING_FIELD: String = "mShowing"

        /** SystemUI 表示 Keyguard 被其他系统界面遮挡的字段名。 */
        private const val OCCLUDED_FIELD: String = "mOccluded"

        /** 按 Titan 2 精确类与布尔字段合同创建只读适配器。 */
        fun bind(mediator: Any): Titan2KeyguardVisibilityAdapter {
            check(mediator.javaClass.name == MEDIATOR_CLASS) { "keyguard_mediator_type" }
            /** 读取并验证一个原生布尔状态字段。 */
            fun booleanField(name: String): Field = mediator.javaClass.getDeclaredField(name).apply {
                check(type == Boolean::class.javaPrimitiveType) { "keyguard_mediator_field_type:$name" }
                isAccessible = true
            }
            return Titan2KeyguardVisibilityAdapter(
                mediator = mediator,
                showingField = booleanField(SHOWING_FIELD),
                occludedField = booleanField(OCCLUDED_FIELD),
            )
        }
    }
}
