import groovy.json.JsonSlurper
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.purride"
version = "1.0.0"

/** 仓库中唯一受审的 artifact 所有权清单。 */
val artifactOwnershipManifestFile = rootProject.file("pixel-engine/config/artifact-ownership.json")

/** 拆分期间保留冻结 package 与源码历史的共享 Kotlin 源码根。 */
val sharedProductionSourceRoot = rootProject.file("pixel-engine/src/main/kotlin")

/** 解析后的 ownership JSON 根对象。 */
@Suppress("UNCHECKED_CAST")
val artifactOwnershipManifest = JsonSlurper().parse(artifactOwnershipManifestFile) as Map<String, Any?>

/** 清单中的 ownership 配置。 */
@Suppress("UNCHECKED_CAST")
val artifactOwnership = artifactOwnershipManifest.getValue("ownership") as Map<String, Any?>

/** 精确文件覆盖规则，优先于目录前缀。 */
@Suppress("UNCHECKED_CAST")
val exactArtifactOwners = artifactOwnership.getValue("files") as Map<String, String>

/** 目录前缀规则，匹配时使用最长前缀。 */
@Suppress("UNCHECKED_CAST")
val artifactPathPrefixes = artifactOwnership.getValue("pathPrefixes") as List<Map<String, String>>

/** 按清单的“精确文件优先、最长目录前缀次之”规则解析源码 owner。 */
fun resolveArtifactOwner(relativePath: String): String? {
    exactArtifactOwners[relativePath]?.let { exactOwner -> return exactOwner }
    /** 当前文件命中的全部目录规则。 */
    val matchingRules = artifactPathPrefixes.filter { rule ->
        /** 规范化为目录形式，防止短路径误匹配同前缀目录。 */
        val pathPrefix = rule.getValue("path").trimEnd('/') + "/"
        relativePath.startsWith(pathPrefix)
    }
    /** 最具体目录规则决定 owner；边界门禁负责拒绝同长度歧义。 */
    return matchingRules.maxByOrNull { rule -> rule.getValue("path").length }?.get("artifact")
}

/** 当前 pixel-widgets 唯一拥有的生产 Kotlin 相对路径。 */
val widgetProductionPaths = fileTree(sharedProductionSourceRoot) {
    include("**/*.kt")
}.files.map { sourceFile ->
    sourceFile.relativeTo(sharedProductionSourceRoot).invariantSeparatorsPath
}.filter { relativePath ->
    resolveArtifactOwner(relativePath) == project.name
}.sorted()

/** build 目录中的 package 对齐源码根，避免复制或移动冻结 package 的源文件。 */
val stagedWidgetSourceRoot = layout.buildDirectory.dir("generated/artifact-sources/main/kotlin")

/** 把受审 owner 精确选择的 widget 源码同步到本 artifact 的生成源集。 */
val stageWidgetSources by tasks.registering(Sync::class) {
    inputs.file(artifactOwnershipManifestFile)
    from(sharedProductionSourceRoot) {
        include(widgetProductionPaths)
    }
    into(stagedWidgetSourceRoot)
    includeEmptyDirs = false
}

android {
    namespace = "com.purride.pixelwidgets.artifact"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        aarMetadata {
            /** Kotlin 2.2 字节码需要消费者至少使用具备对应 D8/R8 支持的 AGP。 */
            minAgpVersion = "8.10.0"
            /** 发布物以 Android 36 API 编译，低版本 compileSdk 必须在解析期明确失败。 */
            minCompileSdk = 36
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"

    sourceSets {
        getByName("main") {
            /** 只编译 ownership 清单归属 widgets 的生成源码。 */
            kotlin.directories.clear()
            kotlin.directories.add(stagedWidgetSourceRoot.get().asFile.path)
            /** widgets artifact 没有独立 Java 生产源码。 */
            java.directories.clear()
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

/** widgets 的公开 API 必须继续遵守聚合模块的显式可见性约束。 */
kotlin {
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    api(project(":pixel-core"))
    api(project(":pixel-runtime"))
    testImplementation(libs.junit)
}

/** Metalava CLI 的独立解析配置，不进入 pixel-widgets 发布 POM。 */
val widgetsMetalavaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(widgetsMetalavaClasspath.name, libs.metalava.cli)
}

/** ownership 清单选中的全部 widgets 生产源码。 */
val widgetsApiProductionSources = objects.fileCollection().from(
    widgetProductionPaths.map(sharedProductionSourceRoot::resolve),
)

/** Release 变体依赖和 Android boot classpath，供 Metalava 完整解析源码签名。 */
val widgetsMetalavaApiClasspath = objects.fileCollection()

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (variant.name == "release") {
            widgetsMetalavaApiClasspath.from(variant.compileClasspath, sdkComponents.bootClasspath)
        }
    }
}

/** 传给 Metalava 的确定性 widgets 源文件清单。 */
val widgetsMetalavaSourceManifest =
    layout.buildDirectory.file("intermediates/metalava/release/source-files.txt")

/** 按声明 package 对齐的 widgets 临时源码树。 */
val widgetsMetalavaStagedSources = layout.buildDirectory.dir("intermediates/metalava/release/sources")

/** 官方 Metalava 生成的当前 pixel-widgets API。 */
val generatedWidgetsMetalavaApi = layout.buildDirectory.file("reports/api/pixel-widgets.metalava-api")

/** 经评审并纳入源码控制的 pixel-widgets API baseline。 */
val widgetsMetalavaApiBaseline = layout.projectDirectory.file("api/pixel-widgets.metalava-api")

/** pixel-widgets Release class 目录，用于生成 JVM 二进制 API。 */
val widgetsReleaseClasses = layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 当前生成的 pixel-widgets JVM 二进制 API 报告。 */
val generatedWidgetsBinaryApi = layout.buildDirectory.file("reports/api/pixel-widgets.binary-api")

/** 经评审并纳入源码控制的 pixel-widgets JVM 二进制 baseline。 */
val widgetsBinaryApiBaseline = layout.projectDirectory.file("api/pixel-widgets.binary-api")

/** pixel-widgets 独立 AAR、class/method 和依赖预算。 */
val widgetsReleaseArtifactBudget = layout.projectDirectory.file("config/release-artifact-budget.json")

/** 复用仓库统一的 classfile/POM 预算检查器。 */
val widgetsReleaseArtifactBudgetTool =
    rootProject.layout.projectDirectory.file("tools/check_pixel_artifact_budget.py")

/** 当前 pixel-widgets Release AAR。 */
val widgetsReleaseArtifactAar = layout.buildDirectory.file("outputs/aar/pixel-widgets-release.aar")

/** 当前 pixel-widgets 发布 POM。 */
val widgetsReleaseArtifactPom = layout.buildDirectory.file("publications/release/pom-default.xml")

/** Gradle 实际解析的 pixel-widgets 运行时 artifact 清单。 */
val widgetsReleaseDependencyManifest =
    layout.buildDirectory.file("reports/artifact-budget/release-runtime-dependencies.txt")

/** pixel-widgets 独立 artifact 预算机器报告。 */
val widgetsReleaseArtifactBudgetReport =
    layout.buildDirectory.file("reports/artifact-budget/release-artifact-budget.json")

/** 使用官方 Metalava 为独立 pixel-widgets 生成稳定源码 API。 */
val generateWidgetsMetalavaApi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates the independent pixel-widgets API with the official Metalava CLI."
    classpath = widgetsMetalavaClasspath
    mainClass.set("com.android.tools.metalava.Driver")

    inputs.files(widgetsApiProductionSources)
        .withPropertyName("widgetsProductionSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(widgetsMetalavaApiClasspath)
        .withPropertyName("releaseCompileClasspath")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("src/main/AndroidManifest.xml")
        .withPropertyName("mainManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(widgetsMetalavaSourceManifest)
    outputs.dir(widgetsMetalavaStagedSources)
    outputs.file(generatedWidgetsMetalavaApi)

    doFirst {
        /** 按仓库路径排序的 widgets Kotlin 源文件。 */
        val sourceFiles = widgetsApiProductionSources.files.sortedBy(File::getAbsolutePath)
        if (sourceFiles.isEmpty()) {
            throw GradleException("Metalava received no pixel-widgets production sources.")
        }
        /** 按声明 package 重建的临时源码根。 */
        val stagedSourceRoot = widgetsMetalavaStagedSources.get().asFile
        /** 已对齐 package 且稳定排序的临时源码文件。 */
        val stagedSourceFiles = stageWidgetsMetalavaSources(sourceFiles, stagedSourceRoot)
        /** 避免完整 widgets 源文件列表超过命令行长度的 @file 清单。 */
        val sourceManifest = widgetsMetalavaSourceManifest.get().asFile
        sourceManifest.parentFile.mkdirs()
        sourceManifest.writeText(
            stagedSourceFiles.joinToString(separator = "\n", postfix = "\n", transform = File::getAbsolutePath),
        )
        /** 仅包含真实存在文件的稳定解析 classpath。 */
        val classpathValue = widgetsMetalavaApiClasspath.files
            .filter(File::exists)
            .sortedBy(File::getAbsolutePath)
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath)
        /** 每次执行前删除旧签名，防止失败后误用陈旧结果。 */
        val apiOutput = generatedWidgetsMetalavaApi.get().asFile
        apiOutput.parentFile.mkdirs()
        apiOutput.delete()
        setArgs(
            listOf(
                "--no-color",
                "--quiet",
                "main",
                "--source-path",
                stagedSourceRoot.absolutePath,
                "--source-files",
                "@${sourceManifest.absolutePath}",
                "--classpath",
                classpathValue,
                "--manifest",
                file("src/main/AndroidManifest.xml").absolutePath,
                "--java-source",
                "11",
                "--kotlin-source",
                "2.2",
                "--Xuse-k2-uast",
                "--warnings-as-errors",
                "--ignore-comments",
                "--hide-annotation",
                "com.purride.pixelui.internal.PixelArtifactInternalApi",
                "--stub-packages",
                "com.purride.*${File.pathSeparator}-com.purride.pixelui.internal.*",
                "--format",
                "v4",
                "--api",
                apiOutput.absolutePath,
            ),
        )
    }

    doLast {
        /** 单一尾换行避免工具环境差异产生无意义 diff。 */
        val apiOutput = generatedWidgetsMetalavaApi.get().asFile
        apiOutput.writeText(apiOutput.readText().trimEnd() + "\n")
    }
}

/** 比较独立 pixel-widgets 官方 API 与已评审 baseline。 */
val checkWidgetsMetalavaApi by tasks.registering {
    group = "verification"
    description = "Checks the independent pixel-widgets Metalava API baseline."
    dependsOn(generateWidgetsMetalavaApi)
    inputs.file(widgetsMetalavaApiBaseline)
    inputs.file(generatedWidgetsMetalavaApi)

    doLast {
        /** 受源码控制的评审 baseline。 */
        val baselineFile = widgetsMetalavaApiBaseline.asFile
        /** 当前源码重新生成的官方签名。 */
        val actualFile = generatedWidgetsMetalavaApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing pixel-widgets API baseline: ${baselineFile.path}. " +
                    "Run :pixel-widgets:generateWidgetsMetalavaApi and review ${actualFile.path}.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-widgets API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 为 Metalava 认定的 widgets 公开类型生成确定性 javap 二进制签名。 */
val dumpWidgetsBinaryApi by tasks.registering {
    group = "verification"
    description = "Writes the independent pixel-widgets JVM binary API dump."
    dependsOn("bundleLibRuntimeToDirRelease")
    dependsOn(generateWidgetsMetalavaApi)
    inputs.dir(widgetsReleaseClasses)
    inputs.file(generatedWidgetsMetalavaApi)
    outputs.file(generatedWidgetsBinaryApi)

    doLast {
        /** 当前 Release 变体的解包 class 目录。 */
        val classDirectory = widgetsReleaseClasses.get().asFile
        if (!classDirectory.exists()) {
            throw GradleException("Missing pixel-widgets release classes at ${classDirectory.path}")
        }
        /** Metalava 根据 Kotlin metadata 认定的稳定公开类型。 */
        val publishedClasses = generatedWidgetsMetalavaApi.get().asFile.readWidgetsMetalavaBinaryClassNames()
        /** 当前 JDK 的 javap，用于保留真实 JVM descriptor。 */
        val javap = File(System.getProperty("java.home"), "bin/javap").absolutePath
        /** widgets 公开类型及其 DefaultImpls 的稳定排序列表。 */
        val classNames = classDirectory.walkTopDown()
            .filter { classFile -> classFile.isFile && classFile.extension == "class" }
            .map { classFile -> classFile.relativeTo(classDirectory).invariantSeparatorsPath.removeSuffix(".class") }
            .filter { className ->
                className in publishedClasses ||
                    className.endsWith("\$DefaultImpls") && className.substringBeforeLast('$') in publishedClasses
            }
            .map { className -> className.replace('/', '.') }
            .sorted()
            .toList()
        /** 完整 widgets 二进制签名文本。 */
        val output = buildString {
            appendLine("# pixel-widgets binary API baseline")
            classNames.forEach { className ->
                /** 当前稳定类型对应的 classfile。 */
                val classFile = File(classDirectory, className.replace('.', '/') + ".class")
                /** 由 classfile 标记确认应排除的兄弟 artifact 内部 member。 */
                val hiddenMemberNames = if (
                    classFile.readBytes().toString(Charsets.ISO_8859_1).contains("PixelArtifactInternalApi")
                ) {
                    /** 仅为带内部标记的类读取 verbose 注解。 */
                    val verboseResult = providers.exec {
                        commandLine(javap, "-classpath", classDirectory.path, "-public", "-v", className)
                    }
                    verboseResult.standardOutput.asText.get().widgetsInternalBinaryMemberNames()
                } else {
                    emptySet()
                }
                /** 单个 class 的公开 JVM 结构。 */
                val result = providers.exec {
                    commandLine(javap, "-classpath", classDirectory.path, "-public", className)
                }
                appendLine(result.standardOutput.asText.get().normalizeWidgetsBinaryApiDump(hiddenMemberNames))
            }
        }
        /** 当前生成报告路径。 */
        val report = generatedWidgetsBinaryApi.get().asFile
        report.parentFile.mkdirs()
        report.writeText(output)
    }
}

/** 比较独立 pixel-widgets JVM 二进制 API 与已评审 baseline。 */
val checkWidgetsBinaryApi by tasks.registering {
    group = "verification"
    description = "Checks the independent pixel-widgets JVM binary API baseline."
    dependsOn(dumpWidgetsBinaryApi)
    inputs.file(widgetsBinaryApiBaseline)
    inputs.file(generatedWidgetsBinaryApi)

    doLast {
        /** 受源码控制的二进制 baseline。 */
        val baselineFile = widgetsBinaryApiBaseline.asFile
        /** 当前 Release class 重新生成的签名。 */
        val actualFile = generatedWidgetsBinaryApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing pixel-widgets binary API baseline: ${baselineFile.path}. " +
                    "Run :pixel-widgets:dumpWidgetsBinaryApi and review ${actualFile.path}.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-widgets binary API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 把 pixel-widgets 实际运行时 class artifact 固定为排序坐标清单。 */
val writeWidgetsReleaseDependencyManifest by tasks.registering {
    group = "verification"
    description = "Writes the exact pixel-widgets Release runtime dependency coordinates."
    /** 受检的独立 widgets Release 运行时配置。 */
    val releaseRuntimeClasspath = configurations.named("releaseRuntimeClasspath")
    /** 实际 class artifact 坐标 provider，同时作为任务输入避免陈旧成功。 */
    val resolvedCoordinateProvider = providers.provider {
        /** incoming 同时提供组件版本和明确类型的 class artifact。 */
        val incoming = releaseRuntimeClasspath.get().incoming
        /** 每个解析组件的发布版本 identity。 */
        val componentVersions = incoming.resolutionResult.allComponents.associate { component ->
            component.id to component.moduleVersion
        }
        /** AGP 9 下显式选择 Android classes jar，避免次级 artifact 歧义。 */
        val runtimeArtifacts = incoming.artifactView {
            attributes.attribute(
                org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                "android-classes-jar",
            )
        }.artifacts.artifacts
        runtimeArtifacts.mapNotNull { artifact ->
            /** 当前 artifact 对应的 Maven 坐标。 */
            val moduleVersion = componentVersions[artifact.id.componentIdentifier] ?: return@mapNotNull null
            "${moduleVersion.group}:${moduleVersion.name}:${moduleVersion.version}"
        }.distinct().sorted()
    }
    inputs.property("releaseRuntimeCoordinates", resolvedCoordinateProvider)
    outputs.file(widgetsReleaseDependencyManifest)

    doLast {
        /** 最终坐标清单使用单一尾换行。 */
        val manifestFile = widgetsReleaseDependencyManifest.get().asFile
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(resolvedCoordinateProvider.get().joinToString("\n", postfix = "\n"))
    }
}

/** 检查 pixel-widgets 自身 AAR、POM 和运行时依赖预算。 */
val checkWidgetsReleaseArtifactBudget by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the independent pixel-widgets artifact and dependency budget."
    dependsOn("assembleRelease", "generatePomFileForReleasePublication")
    dependsOn(writeWidgetsReleaseDependencyManifest)
    workingDir(rootProject.projectDir)
    inputs.file(widgetsReleaseArtifactAar)
    inputs.file(widgetsReleaseArtifactPom)
    inputs.file(widgetsReleaseDependencyManifest)
    inputs.file(widgetsReleaseArtifactBudget)
    inputs.file(widgetsReleaseArtifactBudgetTool)
    outputs.file(widgetsReleaseArtifactBudgetReport)

    commandLine(
        "python3",
        widgetsReleaseArtifactBudgetTool.asFile,
        "--aar",
        widgetsReleaseArtifactAar.get().asFile,
        "--pom",
        widgetsReleaseArtifactPom.get().asFile,
        "--runtime-dependencies",
        widgetsReleaseDependencyManifest.get().asFile,
        "--budget",
        widgetsReleaseArtifactBudget.asFile,
        "--report",
        widgetsReleaseArtifactBudgetReport.get().asFile,
    )
}

/** 将已人工审阅的当前 Metalava 输出冻结为 widgets baseline。 */
val updateWidgetsMetalavaApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(generateWidgetsMetalavaApi)
    doLast {
        /** 当前生成且已审阅的 widgets API。 */
        val generated = generatedWidgetsMetalavaApi.get().asFile
        /** 纳入源码控制的 widgets API baseline。 */
        val baseline = widgetsMetalavaApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

/** 将已人工审阅的当前 javap 输出冻结为 widgets binary baseline。 */
val updateWidgetsBinaryApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(dumpWidgetsBinaryApi)
    doLast {
        /** 当前生成且已审阅的 widgets JVM API。 */
        val generated = generatedWidgetsBinaryApi.get().asFile
        /** 纳入源码控制的 widgets JVM baseline。 */
        val baseline = widgetsBinaryApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

/** 所有读取生成源码的 Android/Kotlin 任务必须先完成确定性同步。 */
tasks.configureEach {
    if (
        name != stageWidgetSources.name &&
        (name.contains("Kotlin") || name.contains("SourcesJar") || name.contains("SourceJar") || name == "preBuild")
    ) {
        dependsOn(stageWidgetSources)
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "pixel-widgets"
                pom {
                    name.set("pixel-widgets")
                    description.set("Pixel SDK standard widgets, themes, overlays, scrolling, and motion components.")
                }
            }
        }
        repositories {
            maven {
                name = "Compatibility"
                url = rootProject.layout.buildDirectory.dir("compatibility-repository").get().asFile.toURI()
            }
        }
    }
}

/** 从 Metalava v4 签名提取 widgets 公开 class 的 JVM 相对路径。 */
fun File.readWidgetsMetalavaBinaryClassNames(): Set<String> {
    /** 遍历签名时的当前 package。 */
    var currentPackage = ""
    /** package block 的稳定匹配式。 */
    val packagePattern = Regex("^package ([A-Za-z_][A-Za-z0-9_.]*) \\{$")
    /** class、interface、enum 和 annotation 声明的匹配式。 */
    val classPattern = Regex(
        "\\b(?:class|interface|enum|@interface)\\s+([A-Za-z_][A-Za-z0-9_.]*(?:<[^>]+>)?)",
    )
    /** 返回的 JVM class 相对路径集合。 */
    val classes = linkedSetOf<String>()
    forEachLine { line ->
        packagePattern.matchEntire(line)?.let { match ->
            currentPackage = match.groupValues[1]
            return@forEachLine
        }
        if (currentPackage.isEmpty() || !line.startsWith("  ")) return@forEachLine
        /** 去除泛型参数后的源码 class 名称。 */
        val sourceName = classPattern.find(line)?.groupValues?.get(1)?.substringBefore('<')
            ?: return@forEachLine
        classes += currentPackage.replace('.', '/') + "/" + sourceName.replace('.', '$')
    }
    return classes
}

/** 从 javap verbose 输出提取带 artifact 内部标记的 widgets JVM member。 */
fun String.widgetsInternalBinaryMemberNames(): Set<String> {
    /** javap 当前正在描述的 public member 头。 */
    var currentMemberHeader: String? = null
    /** 最终应从 widgets 稳定 ABI 中排除的 JVM 名称。 */
    val hiddenNames = mutableSetOf<String>()
    lineSequence().forEach { rawLine ->
        /** 当前行去除缩进后的 javap 文本。 */
        val trimmedLine = rawLine.trim()
        if (rawLine.startsWith("  public ") && trimmedLine.endsWith(';')) {
            currentMemberHeader = trimmedLine
        }
        if (trimmedLine == "com.purride.pixelui.internal.PixelArtifactInternalApi") {
            /** 注解所属 member 的 JVM 名称。 */
            val annotatedName = currentMemberHeader?.widgetsBinaryMemberName() ?: return@forEach
            hiddenNames += annotatedName
            if (annotatedName.endsWith("\$annotations")) {
                /** Kotlin property 注解合成方法对应的真实 getter 名。 */
                val getterName = annotatedName.removeSuffix("\$annotations")
                hiddenNames += getterName
                if (getterName.startsWith("get") && getterName.length > 3) {
                    hiddenNames += "set" + getterName.removePrefix("get")
                } else if (getterName.startsWith("is") && getterName.length > 2) {
                    hiddenNames += "set" + getterName.removePrefix("is")
                }
            }
        }
    }
    return hiddenNames
}

/** 返回一行 javap member 头中的 JVM 方法名；非方法行返回 null。 */
fun String.widgetsBinaryMemberName(): String? {
    /** 参数列表之前的返回类型与方法名部分。 */
    val prefix = substringBefore('(', missingDelimiterValue = "")
    return prefix.takeIf(String::isNotEmpty)?.substringAfterLast(' ')
}

/** 规范化 widgets javap 输出并排除兄弟 artifact 内部 SPI。 */
fun String.normalizeWidgetsBinaryApiDump(hiddenMemberNames: Set<String>): String {
    return lineSequence()
        .filterNot { line -> line.startsWith("Compiled from ") }
        /** Kotlin internal module 后缀不属于稳定消费者 ABI。 */
        .filterNot { line -> Regex("\\\$pixel_[A-Za-z0-9_]+").containsMatchIn(line) }
        /** 注解标记的 JVM public sibling SPI 不属于稳定消费者 ABI。 */
        .filterNot { line -> line.trim().widgetsBinaryMemberName() in hiddenMemberNames }
        /** Kotlin 编译器为私有实现生成的 package 访问桥不属于消费者 ABI。 */
        .filterNot { line -> line.contains(" access\$") }
        .map(String::trimEnd)
        .joinToString("\n")
        .trim()
}

/** 把 widgets 源码复制到与声明 package 一致的临时目录。 */
fun stageWidgetsMetalavaSources(sourceFiles: List<File>, destinationRoot: File): List<File> {
    if (destinationRoot.exists() && !destinationRoot.deleteRecursively()) {
        throw GradleException("Unable to clear pixel-widgets Metalava staging directory: ${destinationRoot.path}")
    }
    if (!destinationRoot.mkdirs() && !destinationRoot.isDirectory) {
        throw GradleException("Unable to create pixel-widgets Metalava staging directory: ${destinationRoot.path}")
    }
    /** Kotlin package 声明匹配式。 */
    val packagePattern = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\b")
    /** 目标文件到原文件的映射，用于拒绝同包同名碰撞。 */
    val destinationOwners = mutableMapOf<File, File>()
    /** 最终返回给 @file 清单的临时源码。 */
    val stagedFiles = mutableListOf<File>()
    sourceFiles.forEach { sourceFile ->
        /** 当前源文件声明的 package。 */
        val packageName = packagePattern.find(sourceFile.readText())?.groupValues?.get(1)
            ?: throw GradleException("Missing package declaration in ${sourceFile.path}")
        /** Metalava package 对齐后的目标文件。 */
        val destinationFile = File(destinationRoot, packageName.replace('.', '/') + "/${sourceFile.name}")
        destinationOwners.put(destinationFile, sourceFile)?.let { previousOwner ->
            throw GradleException(
                "Duplicate pixel-widgets Metalava destination ${destinationFile.path}: " +
                    "${previousOwner.path} and ${sourceFile.path}",
            )
        }
        destinationFile.parentFile.mkdirs()
        sourceFile.copyTo(destinationFile, overwrite = false)
        stagedFiles += destinationFile
    }
    return stagedFiles.sortedBy(File::getAbsolutePath)
}

tasks.named("check") {
    dependsOn(checkWidgetsMetalavaApi)
    dependsOn(checkWidgetsBinaryApi)
    dependsOn(checkWidgetsReleaseArtifactBudget)
}
