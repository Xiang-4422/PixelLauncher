package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 锁定 M5 只能隐藏普通 Keyguard 元素，不得触及任何认证容器。 */
class NativeKeyguardVisibilityContractTest {
    /** 可回退事务的目标必须与已侦察的普通锁屏节点完全一致。 */
    @Test
    fun ordinaryKeyguardTargetSetRemainsExplicit() {
        assertEquals(
            setOf(
                "keyguard_status_view",
                "keyguard_header",
                "keyguard_bottom_area",
                "shared_notification_container",
                "device_entry_icon_view",
                "lock_icon_view",
            ),
            NativeKeyguardVisibilityTransaction.NATIVE_VIEW_RESOURCE_NAMES.toSet(),
        )
        assertEquals(
            setOf(
                "keyguard_status_view",
                "keyguard_header",
                "keyguard_bottom_area",
                "shared_notification_container",
            ),
            NativeKeyguardVisibilityTransaction.REQUIRED_NATIVE_VIEW_RESOURCE_NAMES.toSet(),
        )
        assertEquals(
            setOf("device_entry_icon_view", "lock_icon_view"),
            NativeKeyguardVisibilityTransaction.OPTIONAL_NATIVE_VIEW_RESOURCE_NAMES.toSet(),
        )
    }

    /** M6 之前任何目标都不得包含 Bouncer、security、SIM 或紧急关键字。 */
    @Test
    fun credentialAndEmergencyViewsAreExcluded() {
        /** 不得出现在 M5 普通显隐事务中的安全关键字。 */
        val forbiddenTokens = listOf("bouncer", "security", "password", "pattern", "pin", "sim", "emergency")
        assertFalse(
            NativeKeyguardVisibilityTransaction.NATIVE_VIEW_RESOURCE_NAMES.any { resourceName ->
                forbiddenTokens.any(resourceName::contains)
            },
        )
    }

    /** 像素宿主必须占用前景遮罩的原位置，从而保持在普通内容之上、系统遮罩之下。 */
    @Test
    fun pixelHostUsesForegroundScrimIndex() {
        assertEquals(1, pixelHostInsertionIndex(anchorIndex = 1, childCount = 2))
        assertEquals(8, pixelHostInsertionIndex(anchorIndex = 8, childCount = 13))
    }
}
