package com.purride.pixellockscreen

/**
 * 锁定像素锁屏模块的作用域和当前里程碑能力边界。
 *
 * 当前候选版只在已识别 Titan 2 SystemUI 中接管普通 Keyguard，以及已经完成首帧门禁、
 * 原生安全后端转发和可恢复事务的图案、PIN、密码、SIM/PUK/ME 与 AntiTheft 页面。
 * 未适配的管理员外部安全 Surface 和任一合同失配场景继续保留原生界面。
 */
internal object LockscreenModuleContract {
    /** Modern Xposed 模块唯一允许声明的目标包。 */
    const val SYSTEM_UI_PACKAGE: String = "com.android.systemui"

    /** SystemUI 的主进程名，禁止向次要进程安装锁屏 Hook。 */
    const val SYSTEM_UI_PROCESS: String = "com.android.systemui"

    /** M4 允许在精确命中目标合同后安装只读签名探测。 */
    const val READ_ONLY_HOOK_ENABLED: Boolean = true

    /** M5 在像素首帧和恢复事务就绪后启用普通锁屏接管。 */
    const val VISUAL_TAKEOVER_ENABLED: Boolean = true

    /** M6 仅对 Titan 2 已验证的图案模式安装完整认证页面 Hook。 */
    const val PATTERN_TAKEOVER_ENABLED: Boolean = true

    /** M7 对 Titan 2 精确 PIN 控制器启用像素认证页面 Hook。 */
    const val PIN_TAKEOVER_ENABLED: Boolean = true

    /** M8 对 Titan 2 精确密码控制器启用保留原生输入连接的像素页面 Hook。 */
    const val PASSWORD_TAKEOVER_ENABLED: Boolean = true

    /** M9 对 Titan 2 SIM/PUK/ME 与 AntiTheft 原生控制器启用像素转发页面 Hook。 */
    const val SPECIAL_PIN_TAKEOVER_ENABLED: Boolean = true
}
