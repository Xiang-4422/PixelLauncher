package com.android.systemui.keyguard

/** 测试中模拟 Titan 2 KeyguardViewMediator 的最小显示状态字段合同。 */
class KeyguardViewMediator(
    /** Keyguard 是否由系统声明为显示中。 */
    @JvmField var mShowing: Boolean,
    /** Keyguard 是否被其他系统界面遮挡。 */
    @JvmField var mOccluded: Boolean,
)
