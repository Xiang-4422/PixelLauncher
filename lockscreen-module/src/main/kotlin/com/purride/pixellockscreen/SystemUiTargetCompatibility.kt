package com.purride.pixellockscreen

import android.os.Build
import io.github.libxposed.api.XposedModuleInterface

/** 一次 SystemUI 加载的可比较环境快照，不持有 Context 或系统视图。 */
internal data class SystemUiTargetEnvironment(
    /** Xposed 回调中的实际包名。 */
    val packageName: String,
    /** Xposed 模块加载时的实际进程名。 */
    val processName: String,
    /** 运行系统的 Android SDK 整数。 */
    val sdkInt: Int,
    /** 运行系统的完整 Build Fingerprint。 */
    val buildFingerprint: String,
    /** 目标 SystemUI 主 APK 的加载路径。 */
    val sourceDir: String,
    /** 当前包是否为该进程首个主包。 */
    val isFirstPackage: Boolean,
)

/** 兼容性门禁的结果，拒绝时只保留不含隐私的原因代码。 */
internal sealed interface SystemUiCompatibilityDecision {
    /** 完整命中唯一支持的设备合同。 */
    data object Supported : SystemUiCompatibilityDecision

    /** 未命中目标合同，模块必须保持原生 Keyguard。 */
    data class Rejected(
        /** 用于定位合同差异的稳定代码，不记录完整设备数据。 */
        val reasonCode: String,
    ) : SystemUiCompatibilityDecision
}

/** Titan 2 EEA V01.00.10 的唯一允许合同。 */
internal object Titan2SystemUiTarget {
    /** 已完成 SystemUI 结构侦察的 Android API。 */
    const val SDK_INT: Int = 35

    /** 已验证的完整构建指纹，禁止使用前缀或模糊匹配。 */
    const val BUILD_FINGERPRINT: String =
        "Unihertz/Titan_2_EEA/Titan_2:15/AP3A.240905.015.A2/V01.00.10:user/release-keys"

    /** 已拉取并分析的 MTK SystemUI 主 APK 路径。 */
    const val SYSTEM_UI_SOURCE_DIR: String = "/system_ext/priv-app/MtkSystemUI/MtkSystemUI.apk"

    /**
     * 对所有影响反射签名的维度做精确匹配。
     *
     * @param environment 从 Modern Xposed 加载回调生成的只读环境。
     * @return 只有所有维度命中时才返回 [SystemUiCompatibilityDecision.Supported]。
     */
    fun evaluate(environment: SystemUiTargetEnvironment): SystemUiCompatibilityDecision {
        if (environment.packageName != LockscreenModuleContract.SYSTEM_UI_PACKAGE) {
            return SystemUiCompatibilityDecision.Rejected("package")
        }
        if (environment.processName != LockscreenModuleContract.SYSTEM_UI_PROCESS) {
            return SystemUiCompatibilityDecision.Rejected("process")
        }
        if (!environment.isFirstPackage) {
            return SystemUiCompatibilityDecision.Rejected("secondary_package")
        }
        if (environment.sdkInt != SDK_INT) {
            return SystemUiCompatibilityDecision.Rejected("sdk")
        }
        if (environment.buildFingerprint != BUILD_FINGERPRINT) {
            return SystemUiCompatibilityDecision.Rejected("fingerprint")
        }
        if (environment.sourceDir != SYSTEM_UI_SOURCE_DIR) {
            return SystemUiCompatibilityDecision.Rejected("source_dir")
        }
        return SystemUiCompatibilityDecision.Supported
    }

    /**
     * 从 Xposed 包就绪回调创建不持有框架对象的快照。
     *
     * @param processName 模块加载阶段记录的进程名。
     * @param param SystemUI 包类加载器就绪参数。
     * @return 可由纯 JVM 逻辑比较的目标环境。
     */
    fun environmentOf(
        processName: String,
        param: XposedModuleInterface.PackageReadyParam,
    ): SystemUiTargetEnvironment = SystemUiTargetEnvironment(
        packageName = param.packageName,
        processName = processName,
        sdkInt = Build.VERSION.SDK_INT,
        buildFingerprint = Build.FINGERPRINT,
        sourceDir = param.applicationInfo.sourceDir.orEmpty(),
        isFirstPackage = param.isFirstPackage,
    )
}
