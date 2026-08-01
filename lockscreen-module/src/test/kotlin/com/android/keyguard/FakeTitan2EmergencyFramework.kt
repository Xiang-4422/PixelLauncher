package com.android.keyguard

import com.android.systemui.util.ViewController

/** 测试中模拟 Android 原生紧急按钮。 */
class EmergencyButton {
    /** 按钮是否仍挂载。 */
    var attachedToWindow: Boolean = true

    /** 按钮是否启用。 */
    var enabled: Boolean = true

    /** 按钮自身可见状态。 */
    var visibilityState: Int = 0

    /** 按钮是否已经安装原生点击监听器。 */
    var clickListenerPresent: Boolean = true

    /** 原生点击被成功调用的次数。 */
    var clickCount: Int = 0

    /** 返回按钮是否仍挂载。 */
    fun isAttachedToWindow(): Boolean = attachedToWindow

    /** 返回按钮是否启用。 */
    fun isEnabled(): Boolean = enabled

    /** 返回按钮自身可见状态。 */
    fun getVisibility(): Int = visibilityState

    /** 返回是否已经安装原生点击监听器。 */
    fun hasOnClickListeners(): Boolean = clickListenerPresent

    /** 模拟 Android View 把点击交给原生监听器。 */
    fun performClick(): Boolean {
        if (!clickListenerPresent) {
            return false
        }
        clickCount += 1
        return true
    }
}

/** 测试中模拟 Titan 2 原生紧急按钮控制器。 */
class EmergencyButtonController(
    /** 控制器持有的原生按钮。 */
    button: EmergencyButton,
) : ViewController(button)

/** 测试中模拟 Titan 2 设备凭据控制器公共继承层。 */
open class FakeCredentialViewController(
    /** 凭据页持有的原生紧急按钮控制器。 */
    var mEmergencyButtonController: EmergencyButtonController,
)

/** 测试中模拟 Titan 2 图案认证控制器。 */
class KeyguardPatternViewController(
    /** 图案页继承的原生紧急按钮控制器。 */
    emergencyButtonController: EmergencyButtonController,
) : FakeCredentialViewController(emergencyButtonController)

/** 测试中模拟 Titan 2 PIN 认证控制器。 */
class KeyguardPinViewController(
    /** PIN 页继承的原生紧急按钮控制器。 */
    emergencyButtonController: EmergencyButtonController,
) : FakeCredentialViewController(emergencyButtonController)

/** 测试中模拟 Titan 2 密码认证控制器。 */
class KeyguardPasswordViewController(
    /** 密码页继承的原生紧急按钮控制器。 */
    emergencyButtonController: EmergencyButtonController,
) : FakeCredentialViewController(emergencyButtonController)
