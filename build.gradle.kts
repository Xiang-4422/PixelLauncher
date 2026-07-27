import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

/** 主工程唯一对外发布的 Pixel Engine SDK 子工程。 */
val pixelSdkProjectNames = setOf("pixel-engine")

/** 各发布坐标面向 Maven 消费者的稳定职责描述。 */
val pixelPublicationDescriptions = mapOf(
    "pixel-engine" to "Complete Pixel UI engine SDK for Android-hosted pixel-grid interfaces.",
)

/** 由仓库维护者审阅的发布元数据文件；许可证未确认时不得生成虚假声明。 */
val pixelReleaseMetadataFile = file("pixel-engine/config/release-metadata.properties")

/** 提供给 Pixel Engine MavenPublication 的发布元数据。 */
val pixelReleaseMetadata = Properties().apply {
    pixelReleaseMetadataFile.inputStream().use(::load)
}

/**
 * 读取必填发布元数据并拒绝空值。
 *
 * @param key properties 文件中的字段名。
 * @return 已去除首尾空白的非空字段值。
 */
fun requiredPixelReleaseMetadata(key: String): String {
    return pixelReleaseMetadata.getProperty(key)?.trim().takeUnless { value -> value.isNullOrEmpty() }
        ?: throw GradleException("Missing required Pixel release metadata: $key")
}

/** 已由真实 Git remote 验证的项目主页。 */
val pixelProjectUrl = requiredPixelReleaseMetadata("projectUrl")

/** Maven SCM 的只读连接。 */
val pixelScmConnection = requiredPixelReleaseMetadata("scmConnection")

/** Maven SCM 的开发者连接。 */
val pixelScmDeveloperConnection = requiredPixelReleaseMetadata("scmDeveloperConnection")

/** Maven SCM 的网页地址。 */
val pixelScmUrl = requiredPixelReleaseMetadata("scmUrl")

/** Maven POM 的开发者标识。 */
val pixelDeveloperId = requiredPixelReleaseMetadata("developerId")

/** Maven POM 的开发者显示名。 */
val pixelDeveloperName = requiredPixelReleaseMetadata("developerName")

/** Maven POM 的问题跟踪系统名称。 */
val pixelIssueSystem = requiredPixelReleaseMetadata("issueSystem")

/** Maven POM 的问题跟踪地址。 */
val pixelIssueUrl = requiredPixelReleaseMetadata("issueUrl")

/** 许可证决策状态；只有 CONFIRMED 才允许写入 POM。 */
val pixelLicenseStatus = requiredPixelReleaseMetadata("licenseStatus")

/** 用户确认后的许可证名称；当前可以为空。 */
val pixelLicenseName = pixelReleaseMetadata.getProperty("licenseName", "").trim()

/** 用户确认后的许可证原文地址；当前可以为空。 */
val pixelLicenseUrl = pixelReleaseMetadata.getProperty("licenseUrl", "").trim()

/** 许可证在 Maven 仓库中的分发方式。 */
val pixelLicenseDistribution = pixelReleaseMetadata.getProperty("licenseDistribution", "repo").trim()

subprojects {
    if (name in pixelSdkProjectNames) {
        /** Release 编译与运行图必须由受版本控制的 lockfile 固定。 */
        configurations.configureEach {
            if (name == "releaseCompileClasspath" || name == "releaseRuntimeClasspath") {
                resolutionStrategy.activateDependencyLocking()
            }
        }

        pluginManager.withPlugin("maven-publish") {
            /** OpenPGP 签名仅在显式发布门禁中设为必需，普通开发构建不索取私钥。 */
            pluginManager.apply("signing")
        }
    }
}

gradle.projectsEvaluated {
    pixelSdkProjectNames.sorted().forEach { sdkProjectName ->
        /** 当前需要统一配置的 SDK 子工程。 */
        val sdkProject = project(":$sdkProjectName")
        /** 子工程已由 maven-publish 创建的发布扩展。 */
        val publishing = sdkProject.extensions.getByType(PublishingExtension::class.java)
        /** 临时/远程 Maven 目标；正式 URL 与凭据只能通过受保护属性注入。 */
        val stagingRepository = publishing.repositories.maven {
            name = "PixelStaging"
            url = uri(
                providers.gradleProperty("pixelStagingRepositoryUrl")
                    .orElse(layout.buildDirectory.dir("m9-staging-repository").map { directory ->
                        directory.asFile.toURI().toString()
                    })
                    .get(),
            )
            /** 可选凭据不写入仓库或构建日志。 */
            val repositoryUsername = providers.gradleProperty("pixelRepositoryUsername").orNull
            /** 可选密码由环境变量对应的 ORG_GRADLE_PROJECT 属性注入。 */
            val repositoryPassword = providers.gradleProperty("pixelRepositoryPassword").orNull
            if (!repositoryUsername.isNullOrBlank() || !repositoryPassword.isNullOrBlank()) {
                credentials(PasswordCredentials::class) {
                    username = repositoryUsername
                    password = repositoryPassword
                }
            }
        }
        check(stagingRepository.name == "PixelStaging")

        publishing.publications.withType(MavenPublication::class.java).configureEach {
            pom {
                name.set("Pixel Engine SDK - ${sdkProject.name}")
                description.set(pixelPublicationDescriptions.getValue(sdkProject.name))
                url.set(pixelProjectUrl)
                inceptionYear.set("2026")
                developers {
                    developer {
                        id.set(pixelDeveloperId)
                        name.set(pixelDeveloperName)
                        url.set("https://github.com/$pixelDeveloperId")
                    }
                }
                scm {
                    connection.set(pixelScmConnection)
                    developerConnection.set(pixelScmDeveloperConnection)
                    url.set(pixelScmUrl)
                }
                issueManagement {
                    system.set(pixelIssueSystem)
                    url.set(pixelIssueUrl)
                }
                if (pixelLicenseStatus == "CONFIRMED") {
                    require(pixelLicenseName.isNotBlank()) { "Confirmed licenseName must not be blank." }
                    require(pixelLicenseUrl.isNotBlank()) { "Confirmed licenseUrl must not be blank." }
                    licenses {
                        license {
                            name.set(pixelLicenseName)
                            url.set(pixelLicenseUrl)
                            distribution.set(pixelLicenseDistribution)
                        }
                    }
                }
            }
        }

        /** 当前子工程的 OpenPGP 签名扩展。 */
        val signing = sdkProject.extensions.getByType(SigningExtension::class.java)
        /** CI 通过内存属性注入的 ASCII-armored 私钥。 */
        val signingKey = sdkProject.providers.gradleProperty("signingKey").orNull
        /** 内存私钥的口令；测试临时密钥也必须显式提供。 */
        val signingPassword = sdkProject.providers.gradleProperty("signingPassword").orNull
        if (!signingKey.isNullOrBlank()) {
            signing.useInMemoryPgpKeys(signingKey, signingPassword)
        }
        signing.isRequired = sdkProject.providers.gradleProperty("pixelRequireSigning")
            .map(String::toBooleanStrict)
            .getOrElse(false)
        signing.sign(publishing.publications)
    }
}

/** Release 依赖图的机读输出，供 CycloneDX SBOM 和漏洞扫描复用。 */
val pixelReleaseDependencyGraph = layout.buildDirectory.file(
    "reports/supply-chain/release-dependency-graph.json",
)

/** 解析九个 SDK 的 Release runtime 图并保存为无 Gradle 内部对象的稳定 JSON。 */
tasks.register("writePixelReleaseDependencyGraph") {
    group = "verification"
    description = "Writes the resolved Pixel SDK release dependency graph for SBOM generation."
    /** sdkCoordinates 把九个项目的最终版本纳入任务身份，禁止版本切换后复用旧依赖图。 */
    val sdkCoordinates = providers.provider {
        pixelSdkProjectNames.sorted().map { sdkProjectName ->
            /** sdkProject 是当前坐标对应、已完成配置的发布子工程。 */
            val sdkProject = project(":$sdkProjectName")
            "${sdkProject.group}:${sdkProject.name}:${sdkProject.version}"
        }
    }
    inputs.property("pixelSdkCoordinates", sdkCoordinates)
    outputs.file(pixelReleaseDependencyGraph)
    doLast {
        /** 以 package URL 为键去重的组件表。 */
        val components = linkedMapOf<String, Map<String, String>>()
        /** 以父 package URL 为键聚合的依赖边。 */
        val dependencies = linkedMapOf<String, MutableSet<String>>()

        /**
         * 将 Gradle 组件标识转换为 Maven package URL。
         *
         * @param identifier Gradle 解析结果中的组件标识。
         * @return 可写入 SBOM 的 package URL；未知组件返回 null。
         */
        fun componentReference(identifier: Any): String? {
            return when (identifier) {
                is ModuleComponentIdentifier -> {
                    /** 外部 Maven 组件的 package URL。 */
                    val reference = "pkg:maven/${identifier.group}/${identifier.module}@${identifier.version}"
                    components.putIfAbsent(
                        reference,
                        mapOf(
                            "group" to identifier.group,
                            "name" to identifier.module,
                            "version" to identifier.version,
                            "purl" to reference,
                            "scope" to "required",
                        ),
                    )
                    reference
                }

                is ProjectComponentIdentifier -> {
                    /** 当前工程依赖所指向的 SDK/宿主子工程。 */
                    val dependencyProject = project(identifier.projectPath)
                    /** 工程组件使用其最终 Maven GAV 构造 package URL。 */
                    val reference =
                        "pkg:maven/${dependencyProject.group}/${dependencyProject.name}@${dependencyProject.version}"
                    components.putIfAbsent(
                        reference,
                        mapOf(
                            "group" to dependencyProject.group.toString(),
                            "name" to dependencyProject.name,
                            "version" to dependencyProject.version.toString(),
                            "purl" to reference,
                            "scope" to "required",
                        ),
                    )
                    reference
                }

                else -> null
            }
        }

        pixelSdkProjectNames.sorted().forEach { sdkProjectName ->
            /** 当前 SDK 子工程的 Release 编译与运行配置。 */
            val releaseConfigurations = listOf(
                project(":$sdkProjectName").configurations.getByName("releaseCompileClasspath"),
                project(":$sdkProjectName").configurations.getByName("releaseRuntimeClasspath"),
            )
            /** 只解析编译图组件，避免直接请求 Android 多 artifact variant 造成歧义。 */
            releaseConfigurations.first().incoming.resolutionResult.allComponents.size
            /** Gradle 已选版本和完整传递边集合。 */
            val resolutionResult = releaseConfigurations.last().incoming.resolutionResult
            resolutionResult.allComponents.forEach { component ->
                componentReference(component.id)
            }
            resolutionResult.allDependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
                /** 已解析依赖边的父组件。 */
                val fromReference = componentReference(dependency.from.id) ?: return@forEach
                /** 已解析依赖边的子组件。 */
                val toReference = componentReference(dependency.selected.id) ?: return@forEach
                dependencies.getOrPut(fromReference, ::sortedSetOf).add(toReference)
            }
        }

        /** 不包含时间戳的稳定依赖图文档。 */
        val graph = mapOf(
            "schemaVersion" to 1,
            "components" to components.values.sortedBy { component -> component.getValue("purl") },
            "dependencies" to dependencies.toSortedMap().map { (reference, children) ->
                mapOf("ref" to reference, "dependsOn" to children.toList())
            },
        )
        /** 原子替换前的临时 JSON 文件。 */
        val temporaryOutput = pixelReleaseDependencyGraph.get().asFile.resolveSibling(
            pixelReleaseDependencyGraph.get().asFile.name + ".tmp",
        )
        temporaryOutput.parentFile.mkdirs()
        temporaryOutput.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(graph)) + "\n")
        temporaryOutput.renameTo(pixelReleaseDependencyGraph.get().asFile)
    }
}

tasks.register<Exec>("pixelReleaseCheck") {
    group = "verification"
    description = "Runs the local pixel-engine release validation gate."
    commandLine("bash", "tools/pixel-release-check.sh")
}
