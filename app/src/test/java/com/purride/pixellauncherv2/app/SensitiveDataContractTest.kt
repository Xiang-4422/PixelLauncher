package com.purride.pixellauncherv2.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 验证备份排除规则和源码凭据禁入规则不会被后续改动绕过。 */
class SensitiveDataContractTest {

    /** Manifest 必须同时引用两份备份规则资源，否则排除项不会进入构建产物。 */
    @Test
    fun manifestReferencesBothBackupRuleResources() {
        // Manifest 文档用于验证备份规则资源引用。
        val manifest = parseXml(moduleRoot().resolve("src/main/AndroidManifest.xml"))
        // Application 节点承载本测试需要的备份属性。
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertTrue(
            "Manifest must reference the API 24-30 full-backup rules.",
            application.getAttribute("android:fullBackupContent") == "@xml/backup_rules",
        )
        assertTrue(
            "Manifest must reference the API 31+ data-extraction rules.",
            application.getAttribute("android:dataExtractionRules") == "@xml/data_extraction_rules",
        )
    }

    /** Android 11 及以下的 Auto Backup 必须排除本机应用清单缓存的两种存储域。 */
    @Test
    fun deviceLocalCacheIsExcludedFromFullBackup() {
        // Android 11 及以下的编译前备份规则文档。
        val backupRules = parseXml(moduleRoot().resolve("src/main/res/xml/backup_rules.xml"))

        assertDeviceLocalCacheExclusions(backupRules.documentElement)
    }

    /** Android 12+ 的云备份与设备迁移必须分别排除本机应用清单缓存。 */
    @Test
    fun deviceLocalCacheIsExcludedFromCloudBackupAndDeviceTransfer() {
        // Android 12 及以上的数据提取规则文档。
        val extractionRules = parseXml(moduleRoot().resolve("src/main/res/xml/data_extraction_rules.xml"))
        // 云备份节点必须独立存在并包含排除项。
        val cloudBackup = extractionRules.getElementsByTagName("cloud-backup").item(0) as? Element
        // 设备迁移节点必须独立存在并包含排除项。
        val deviceTransfer = extractionRules.getElementsByTagName("device-transfer").item(0) as? Element

        assertNotNull("Android 12+ rules must define cloud-backup.", cloudBackup)
        assertNotNull("Android 12+ rules must define device-transfer.", deviceTransfer)
        assertDeviceLocalCacheExclusions(checkNotNull(cloudBackup))
        assertDeviceLocalCacheExclusions(checkNotNull(deviceTransfer))
    }

    /** 被排除的偏好文件名必须与真实写入方保持一致，避免规则指向不存在的文件。 */
    @Test
    fun excludedPreferenceFileMatchesItsRuntimeWriter() {
        // 应用清单缓存仓库源码是该偏好文件的唯一写入方。
        val repositorySource = moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/data/PackageManagerAppRepository.kt")
            .readText()

        assertTrue(
            "Backup rules must exclude a preference file that the app actually writes.",
            repositorySource.contains("\"${DEVICE_LOCAL_PREFERENCES_FILE.removeSuffix(".xml")}\""),
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

    /** 验证指定备份节点同时排除凭据保护和设备保护的本机缓存偏好文件。 */
    private fun assertDeviceLocalCacheExclusions(parent: Element) {
        // 实际排除域集合只统计目标缓存文件，忽略其他规则。
        val excludedDomains = parent.getElementsByTagName("exclude")
            .let { nodes ->
                (0 until nodes.length)
                    .map { index -> nodes.item(index) as Element }
                    .filter { element -> element.getAttribute("path") == DEVICE_LOCAL_PREFERENCES_FILE }
                    .map { element -> element.getAttribute("domain") }
                    .toSet()
            }

        REQUIRED_SHARED_PREFERENCE_DOMAINS.forEach { domain ->
            assertTrue(
                "$domain must exclude $DEVICE_LOCAL_PREFERENCES_FILE under ${parent.tagName}.",
                domain in excludedDomains,
            )
        }
        assertFalse("Backup exclusion must not use an empty domain.", "" in excludedDomains)
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
        /** Android 备份 XML 中本机应用清单缓存使用的完整文件名。 */
        const val DEVICE_LOCAL_PREFERENCES_FILE: String = "app_repository_cache.xml"

        /** 同时覆盖凭据保护和设备保护 SharedPreferences 的备份域。 */
        val REQUIRED_SHARED_PREFERENCE_DOMAINS: Set<String> = setOf("sharedpref", "device_sharedpref")
    }
}
