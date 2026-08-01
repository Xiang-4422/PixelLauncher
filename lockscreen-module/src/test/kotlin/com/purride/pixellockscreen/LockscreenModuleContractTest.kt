package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
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

    /** 候选包必须启用所有已经完成精确合同和原生回退链的安全模式。 */
    @Test
    fun allSupportedCredentialTakeoversAreEnabled() {
        assertTrue(LockscreenModuleContract.READ_ONLY_HOOK_ENABLED)
        assertTrue(LockscreenModuleContract.VISUAL_TAKEOVER_ENABLED)
        assertTrue(LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED)
        assertTrue(LockscreenModuleContract.PIN_TAKEOVER_ENABLED)
        assertTrue(LockscreenModuleContract.PASSWORD_TAKEOVER_ENABLED)
        assertTrue(LockscreenModuleContract.SPECIAL_PIN_TAKEOVER_ENABLED)
    }
}
