package com.purride.pixellockscreen

/**
 * 锁定像素锁屏模块的作用域和当前里程碑能力边界。
 *
 * M5 允许在已识别 Titan 2 SystemUI 中挂载普通像素 Keyguard，但图案、PIN、
 * 密码、SIM 和其他安全模式继续使用原生 Bouncer，直到各自适配完成。
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

    /** M6 之前禁止隐藏原生 Bouncer 或接收原始凭据。 */
    const val CREDENTIAL_TAKEOVER_ENABLED: Boolean = false
}
