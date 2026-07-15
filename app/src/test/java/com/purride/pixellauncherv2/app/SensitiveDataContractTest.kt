package com.purride.pixellauncherv2.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 验证敏感数据清理、备份排除和源码凭据禁入规则不会被后续改动绕过。 */
class SensitiveDataContractTest {

    /** 应用启动后的第一个自定义动作必须是清除旧版敏感偏好。 */
    @Test
    fun applicationStartupClearsLegacySensitiveDataFirst() {
        // Application 源码用于锁定清理调用的精确启动顺序。
        val applicationSource = moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/PixelLauncherApp.kt")
            .readText()
        // 父类初始化调用是允许出现在清理之前的唯一语句。
        val superCall = "super.onCreate()"
        // 安全清理调用必须是首个自定义启动动作。
        val cleanupCall = "LegacySensitiveDataCleaner.clear(applicationContext)"
        // 父类调用位置用于确定允许的启动边界。
        val superCallIndex = applicationSource.indexOf(superCall)
        // 清理调用位置用于断言顺序和存在性。
        val cleanupCallIndex = applicationSource.indexOf(cleanupCall)

        assertTrue(
            "Application.onCreate must call its superclass before cleanup.",
            superCallIndex >= 0,
        )
        assertTrue(
            "Application.onCreate must synchronously clear legacy sensitive data.",
            cleanupCallIndex > superCallIndex,
        )
        // 两个调用之间的源码必须为空，防止其他组件先读取旧数据。
        val statementsBetweenStartupAndCleanup = applicationSource
            .substring(superCallIndex + superCall.length, cleanupCallIndex)
            .trim()
        assertTrue(
            "Legacy cleanup must be the first custom application startup action.",
            statementsBetweenStartupAndCleanup.isEmpty(),
        )
    }

    /** Android 11 及以下的 Auto Backup 必须排除旧偏好文件的两种存储域。 */
    @Test
    fun legacyPreferencesAreExcludedFromFullBackup() {
        // Android 11 及以下的编译前备份规则文档。
        val backupRules = parseXml(moduleRoot().resolve("src/main/res/xml/backup_rules.xml"))

        assertLegacyPreferenceExclusions(backupRules.documentElement)
    }

    /** Android 12+ 的云备份与设备迁移必须分别排除旧偏好文件。 */
    @Test
    fun legacyPreferencesAreExcludedFromCloudBackupAndDeviceTransfer() {
        // Android 12 及以上的数据提取规则文档。
        val extractionRules = parseXml(moduleRoot().resolve("src/main/res/xml/data_extraction_rules.xml"))
        // 云备份节点必须独立存在并包含排除项。
        val cloudBackup = extractionRules.getElementsByTagName("cloud-backup").item(0) as? Element
        // 设备迁移节点必须独立存在并包含排除项。
        val deviceTransfer = extractionRules.getElementsByTagName("device-transfer").item(0) as? Element

        assertNotNull("Android 12+ rules must define cloud-backup.", cloudBackup)
        assertNotNull("Android 12+ rules must define device-transfer.", deviceTransfer)
        assertLegacyPreferenceExclusions(checkNotNull(cloudBackup))
        assertLegacyPreferenceExclusions(checkNotNull(deviceTransfer))
    }

    /** 历史备份恢复必须由受限模式 BackupAgent 在首次 Activity 启动前再次清理。 */
    @Test
    fun backupAgentClearsHistoricalRestoreBeforeNormalApplicationStartup() {
        // Manifest 文档用于验证恢复代理及文件型备份模式声明。
        val manifest = parseXml(moduleRoot().resolve("src/main/AndroidManifest.xml"))
        // Application 节点承载本测试需要的备份属性。
        val application = manifest.getElementsByTagName("application").item(0) as Element
        // 代理源码用于锁定默认完整备份和恢复完成清理调用。
        val agentSource = moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/data/LegacySensitiveDataBackupAgent.kt")
            .readText()

        assertTrue(
            "Manifest must declare the historical sensitive-data BackupAgent.",
            application.getAttribute("android:backupAgent") == ".data.LegacySensitiveDataBackupAgent",
        )
        assertTrue(
            "BackupAgent must retain file-based Auto Backup.",
            application.getAttribute("android:fullBackupOnly") == "true",
        )
        assertTrue(
            "BackupAgent must keep the platform XML-backed full backup implementation.",
            agentSource.contains("super.onFullBackup(data)"),
        )
        assertTrue(
            "BackupAgent must erase historical preferences when restore completes.",
            agentSource.contains("override fun onRestoreFinished()") &&
                agentSource.contains("LegacySensitiveDataCleaner.clear(this)"),
        )
    }

    /** 应用主源码和文本资源中不得再出现可识别的长凭据字符串。 */
    @Test
    fun mainSourcesContainNoCredentialShapedValues() {
        // 可搜索扩展名限定为可能承载源码或配置凭据的文本文件。
        val searchableExtensions = setOf("kt", "kts", "xml", "json", "properties")
        // 凭据形状只识别足够长的 sk- 字符串，避免普通文本误报。
        val credentialPattern = Regex("(?i)\\bsk-[a-z0-9]{20,}\\b")
        // 命中文件集合必须为空，失败消息会列出精确路径。
        val contaminatedFiles = moduleRoot()
            .resolve("src/main")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension in searchableExtensions }
            .filter { file -> credentialPattern.containsMatchIn(file.readText()) }
            .map(File::getPath)
            .toList()

        assertTrue(
            "Credential-shaped values found in main sources: $contaminatedFiles",
            contaminatedFiles.isEmpty(),
        )
    }

    /** 验证指定备份节点同时排除凭据保护和设备保护的旧偏好文件。 */
    private fun assertLegacyPreferenceExclusions(parent: Element) {
        // 实际排除域集合只统计目标历史文件，忽略其他规则。
        val excludedDomains = parent.getElementsByTagName("exclude")
            .let { nodes ->
                (0 until nodes.length)
                    .map { index -> nodes.item(index) as Element }
                    .filter { element -> element.getAttribute("path") == LEGACY_PREFERENCES_FILE }
                    .map { element -> element.getAttribute("domain") }
                    .toSet()
            }

        REQUIRED_SHARED_PREFERENCE_DOMAINS.forEach { domain ->
            assertTrue(
                "$domain must exclude $LEGACY_PREFERENCES_FILE under ${parent.tagName}.",
                domain in excludedDomains,
            )
        }
        assertFalse("Legacy preferences exclusion must not use an empty domain.", "" in excludedDomains)
    }

    /** 使用禁用外部实体的解析器读取仓库内受信任的 XML 契约。 */
    private fun parseXml(file: File): Document {
        // XML 工厂禁用 DOCTYPE，避免测试解析外部实体。
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(file)
    }

    /** 定位测试运行时的 app 模块根目录。 */
    private fun moduleRoot(): File {
        // 工作目录兼容从仓库根或 app 模块直接启动 Gradle 测试。
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
    }

    private companion object {
        /** Android 备份 XML 中旧偏好文件使用的完整文件名。 */
        const val LEGACY_PREFERENCES_FILE: String = "pixel_launcher_ai_prefs.xml"

        /** 同时覆盖凭据保护和设备保护 SharedPreferences 的备份域。 */
        val REQUIRED_SHARED_PREFERENCE_DOMAINS: Set<String> = setOf("sharedpref", "device_sharedpref")
    }
}
