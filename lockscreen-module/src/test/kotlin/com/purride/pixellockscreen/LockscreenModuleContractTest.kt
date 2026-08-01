package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定当前唯一作用域、普通锁屏接管与设备凭据认证边界。 */
class LockscreenModuleContractTest {
    /** 模块只能注入 SystemUI 主进程，禁止扩大到系统框架或普通应用。 */
    @Test
    fun targetPackageAndProcessRemainSystemUiOnly() {
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PACKAGE)
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PROCESS)
    }

    /** 图案接管保持启用，PIN 在生命周期代码完成后仍须等待设备门禁。 */
    @Test
    fun onlyRuntimeVerifiedCredentialTakeoverIsEnabled() {
        assertTrue(LockscreenModuleContract.READ_ONLY_HOOK_ENABLED)
        assertTrue(LockscreenModuleContract.VISUAL_TAKEOVER_ENABLED)
        assertTrue(LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED)
        assertFalse(LockscreenModuleContract.PIN_TAKEOVER_ENABLED)
    }
}
