package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 Titan 2 兼容门禁只接受完整精确合同。 */
class SystemUiTargetCompatibilityTest {
    /** 已侦察 Titan 2 SystemUI 的标准环境。 */
    private val supportedEnvironment = SystemUiTargetEnvironment(
        packageName = "com.android.systemui",
        processName = "com.android.systemui",
        sdkInt = 35,
        buildFingerprint = Titan2SystemUiTarget.BUILD_FINGERPRINT,
        sourceDir = "/system_ext/priv-app/MtkSystemUI/MtkSystemUI.apk",
        isFirstPackage = true,
    )

    /** 完整合同必须通过门禁。 */
    @Test
    fun exactTitanTwoContractIsSupported() {
        assertEquals(
            SystemUiCompatibilityDecision.Supported,
            Titan2SystemUiTarget.evaluate(supportedEnvironment),
        )
    }

    /** 任何非 SystemUI 包都必须被拒绝。 */
    @Test
    fun differentPackageIsRejected() {
        assertRejected("package", supportedEnvironment.copy(packageName = "android"))
    }

    /** SystemUI 次要进程不得安装 Keyguard Hook。 */
    @Test
    fun secondaryProcessIsRejected() {
        assertRejected("process", supportedEnvironment.copy(processName = "com.android.systemui:screenshot"))
    }

    /** 同一进程后续加载的次要包不得重复安装 Hook。 */
    @Test
    fun secondaryPackageCallbackIsRejected() {
        assertRejected("secondary_package", supportedEnvironment.copy(isFirstPackage = false))
    }

    /** Android API 变化可能改变内部签名，必须显式新增适配。 */
    @Test
    fun differentSdkIsRejected() {
        assertRejected("sdk", supportedEnvironment.copy(sdkInt = 36))
    }

    /** OTA 后即使 API 未变也必须重新完成 SystemUI 结构侦察。 */
    @Test
    fun differentFingerprintIsRejected() {
        assertRejected(
            "fingerprint",
            supportedEnvironment.copy(buildFingerprint = "Unihertz/Titan_2_EEA/unknown"),
        )
    }

    /** SystemUI APK 路径改变代表分包或 ROM 结构变化，禁止继续注入。 */
    @Test
    fun differentSystemUiSourceIsRejected() {
        assertRejected(
            "source_dir",
            supportedEnvironment.copy(sourceDir = "/system/priv-app/SystemUI/SystemUI.apk"),
        )
    }

    /** 验证拒绝结果包含预期的稳定诊断代码。 */
    private fun assertRejected(reasonCode: String, environment: SystemUiTargetEnvironment) {
        assertEquals(
            SystemUiCompatibilityDecision.Rejected(reasonCode),
            Titan2SystemUiTarget.evaluate(environment),
        )
    }
}
