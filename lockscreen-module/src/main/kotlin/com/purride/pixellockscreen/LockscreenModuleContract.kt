package com.purride.pixellockscreen

/**
 * 锁定像素锁屏模块的作用域和当前里程碑能力边界。
 *
 * M5 允许在已识别 Titan 2 SystemUI 中挂载普通像素 Keyguard。M6 启用已经完成可清零输入、
 * 系统校验、原生回调、紧急操作、首帧门禁和完整回退事务的图案模式。PIN 生命周期 Hook
 * 已就绪但在最终批量验收前保持关闭；密码运行时也在完成全量门禁前保持关闭，SIM 和其他
 * 特殊安全模式继续使用原生 Bouncer。
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

    /** M7 PIN 生命周期与回退链就绪，但设备运行时验收前禁止实际安装 Hook。 */
    const val PIN_TAKEOVER_ENABLED: Boolean = false

    /** M8 密码输入连接与回退链就绪，但最终批量验收前禁止实际安装 Hook。 */
    const val PASSWORD_TAKEOVER_ENABLED: Boolean = false

    /** M9 SIM/PUK/ME 与 AntiTheft 原生转发链就绪，最终批量验收前保持关闭。 */
    const val SPECIAL_PIN_TAKEOVER_ENABLED: Boolean = false
}
