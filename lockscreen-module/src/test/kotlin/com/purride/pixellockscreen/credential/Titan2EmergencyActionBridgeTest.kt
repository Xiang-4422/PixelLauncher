package com.purride.pixellockscreen.credential

import com.android.keyguard.EmergencyButton
import com.android.keyguard.EmergencyButtonController
import com.android.keyguard.KeyguardPatternViewController
import com.android.keyguard.KeyguardPinViewController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Titan 2 原生紧急操作桥的失效关闭测试。 */
class Titan2EmergencyActionBridgeTest {
    /** 可用原生按钮应由 ROM 自己的点击链处理。 */
    @Test
    fun requestUsesNativeButtonClickChain() {
        /** 完整可用的测试夹具。 */
        val fixture = Fixture()
        /** 绑定后的原生紧急操作桥。 */
        val bridge = fixture.bind()

        bridge.requestEmergencyAction()

        assertEquals(1, fixture.button.clickCount)
    }

    /** PIN 控制器继承的紧急字段也必须复用同一原生点击链。 */
    @Test
    fun pinControllerUsesInheritedNativeButtonClickChain() {
        /** 完整可用的测试夹具。 */
        val fixture = Fixture()
        /** 模拟 Titan 2 PIN 控制器。 */
        val pinController = KeyguardPinViewController(fixture.emergencyController)
        /** 绑定后的原生紧急操作桥。 */
        val bridge = Titan2EmergencyActionBridge.bind(
            credentialController = pinController,
            credentialMode = Titan2CredentialMode.PIN,
            classLoader = javaClass.classLoader!!,
        )

        bridge.requestEmergencyAction()

        assertEquals(1, fixture.button.clickCount)
    }

    /** 缺失监听器、不可见、禁用或未挂载按钮均不得进入接管。 */
    @Test
    fun bindRejectsUnavailableNativeButton() {
        /** 每种不可用状态的配置动作。 */
        val unavailableStates: List<(EmergencyButton) -> Unit> = listOf(
            { button -> button.attachedToWindow = false },
            { button -> button.enabled = false },
            { button -> button.visibilityState = 8 },
            { button -> button.clickListenerPresent = false },
        )

        unavailableStates.forEach { configure ->
            /** 当前不可用状态使用的独立夹具。 */
            val fixture = Fixture()
            configure(fixture.button)
            assertThrows(IllegalStateException::class.java) { fixture.bind() }
            assertEquals(0, fixture.button.clickCount)
        }
    }

    /** 控制器替换原生紧急对象后旧桥必须拒绝点击。 */
    @Test
    fun requestRejectsStaleControllerBinding() {
        /** 完整可用的测试夹具。 */
        val fixture = Fixture()
        /** 绑定后的旧桥。 */
        val bridge = fixture.bind()
        fixture.patternController.mEmergencyButtonController = EmergencyButtonController(
            EmergencyButton(),
        )

        assertThrows(IllegalStateException::class.java) { bridge.requestEmergencyAction() }
        assertEquals(0, fixture.button.clickCount)
    }

    /** 释放后的桥不得再次启动原生紧急流程。 */
    @Test
    fun disposeRejectsFutureRequests() {
        /** 完整可用的测试夹具。 */
        val fixture = Fixture()
        /** 绑定后的原生紧急操作桥。 */
        val bridge = fixture.bind()
        bridge.dispose()

        assertThrows(IllegalStateException::class.java) { bridge.requestEmergencyAction() }
        assertEquals(0, fixture.button.clickCount)
    }

    /** 为每个测试提供互不共享状态的 Titan 2 控制器对象。 */
    private class Fixture {
        /** 原生紧急按钮。 */
        val button: EmergencyButton = EmergencyButton()

        /** 原生紧急按钮控制器。 */
        val emergencyController: EmergencyButtonController = EmergencyButtonController(button)

        /** 原生图案认证控制器。 */
        val patternController: KeyguardPatternViewController = KeyguardPatternViewController(
            emergencyController,
        )

        /** 使用测试类加载器创建精确反射绑定。 */
        fun bind(): Titan2EmergencyActionBridge = Titan2EmergencyActionBridge.bind(
            credentialController = patternController,
            credentialMode = Titan2CredentialMode.PATTERN,
            classLoader = javaClass.classLoader!!,
        )
    }
}
