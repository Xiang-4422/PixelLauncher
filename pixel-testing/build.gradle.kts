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

/** 当前 pixel-testing 唯一拥有的生产 Kotlin 相对路径。 */
val testingProductionPaths = fileTree(sharedProductionSourceRoot) {
    include("**/*.kt")
}.files.map { sourceFile ->
    sourceFile.relativeTo(sharedProductionSourceRoot).invariantSeparatorsPath
}.filter { relativePath ->
    resolveArtifactOwner(relativePath) == project.name
}.sorted()

/** build 目录中的 package 对齐源码根，避免复制或移动冻结 package 的源文件。 */
val stagedTestingSourceRoot = layout.buildDirectory.dir("generated/artifact-sources/main/kotlin")

/** 把受审 owner 精确选择的 testing 源码同步到本 artifact 的生成源集。 */
val stageTestingSources by tasks.registering(Sync::class) {
    inputs.file(artifactOwnershipManifestFile)
    from(sharedProductionSourceRoot) {
        include(testingProductionPaths)
    }
    into(stagedTestingSourceRoot)
    includeEmptyDirs = false
}

android {
    namespace = "com.purride.pixeltesting.artifact"
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
            /** 只编译 ownership 清单归属 testing 的生成源码。 */
            kotlin.directories.clear()
            kotlin.directories.add(stagedTestingSourceRoot.get().asFile.path)
            /** testing artifact 没有独立 Java 生产源码。 */
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

/** testing 的公开 API 必须继续遵守聚合模块的显式可见性约束。 */
kotlin {
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    api(project(":pixel-core"))
    api(project(":pixel-runtime"))
    api(project(":pixel-widgets"))
    api(project(":pixel-navigation"))
    testImplementation(libs.junit)
}

/** Metalava CLI 的独立解析配置，不进入 pixel-testing 发布 POM。 */
val testingMetalavaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(testingMetalavaClasspath.name, libs.metalava.cli)
}

/** ownership 清单选中的全部 testing 生产源码。 */
val testingApiProductionSources = objects.fileCollection().from(
    testingProductionPaths.map(sharedProductionSourceRoot::resolve),
)

/** Release 变体依赖和 Android boot classpath，供 Metalava 完整解析源码签名。 */
val testingMetalavaApiClasspath = objects.fileCollection()

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (variant.name == "release") {
            testingMetalavaApiClasspath.from(variant.compileClasspath, sdkComponents.bootClasspath)
        }
    }
}

/** 传给 Metalava 的确定性 testing 源文件清单。 */
val testingMetalavaSourceManifest =
    layout.buildDirectory.file("intermediates/metalava/release/source-files.txt")

/** 按声明 package 对齐的 testing 临时源码树。 */
val testingMetalavaStagedSources = layout.buildDirectory.dir("intermediates/metalava/release/sources")

/** 官方 Metalava 生成的当前 pixel-testing API。 */
val generatedTestingMetalavaApi = layout.buildDirectory.file("reports/api/pixel-testing.metalava-api")

/** 经评审并纳入源码控制的 pixel-testing API baseline。 */
val testingMetalavaApiBaseline = layout.projectDirectory.file("api/pixel-testing.metalava-api")

/** pixel-testing Release class 目录，用于生成 JVM 二进制 API。 */
val testingReleaseClasses = layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 当前生成的 pixel-testing JVM 二进制 API 报告。 */
val generatedTestingBinaryApi = layout.buildDirectory.file("reports/api/pixel-testing.binary-api")

/** 经评审并纳入源码控制的 pixel-testing JVM 二进制 baseline。 */
val testingBinaryApiBaseline = layout.projectDirectory.file("api/pixel-testing.binary-api")

/** pixel-testing 独立 AAR、class/method 和依赖预算。 */
val testingReleaseArtifactBudget = layout.projectDirectory.file("config/release-artifact-budget.json")

/** 复用仓库统一的 classfile/POM 预算检查器。 */
val testingReleaseArtifactBudgetTool =
    rootProject.layout.projectDirectory.file("tools/check_pixel_artifact_budget.py")

/** 当前 pixel-testing Release AAR。 */
val testingReleaseArtifactAar = layout.buildDirectory.file("outputs/aar/pixel-testing-release.aar")

/** 当前 pixel-testing 发布 POM。 */
val testingReleaseArtifactPom = layout.buildDirectory.file("publications/release/pom-default.xml")

/** Gradle 实际解析的 pixel-testing 运行时 artifact 清单。 */
val testingReleaseDependencyManifest =
    layout.buildDirectory.file("reports/artifact-budget/release-runtime-dependencies.txt")

/** pixel-testing 独立 artifact 预算机器报告。 */
val testingReleaseArtifactBudgetReport =
    layout.buildDirectory.file("reports/artifact-budget/release-artifact-budget.json")

/** 使用官方 Metalava 为独立 pixel-testing 生成稳定源码 API。 */
val generateTestingMetalavaApi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates the independent pixel-testing API with the official Metalava CLI."
    classpath = testingMetalavaClasspath
    mainClass.set("com.android.tools.metalava.Driver")

    inputs.files(testingApiProductionSources)
        .withPropertyName("testingProductionSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(testingMetalavaApiClasspath)
        .withPropertyName("releaseCompileClasspath")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("src/main/AndroidManifest.xml")
        .withPropertyName("mainManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(testingMetalavaSourceManifest)
    outputs.dir(testingMetalavaStagedSources)
    outputs.file(generatedTestingMetalavaApi)

    doFirst {
        /** 按仓库路径排序的 testing Kotlin 源文件。 */
        val sourceFiles = testingApiProductionSources.files.sortedBy(File::getAbsolutePath)
        if (sourceFiles.isEmpty()) {
            throw GradleException("Metalava received no pixel-testing production sources.")
        }
        /** 按声明 package 重建的临时源码根。 */
        val stagedSourceRoot = testingMetalavaStagedSources.get().asFile
        /** 已对齐 package 且稳定排序的临时源码文件。 */
        val stagedSourceFiles = stageTestingMetalavaSources(sourceFiles, stagedSourceRoot)
        /** 避免完整 testing 源文件列表超过命令行长度的 @file 清单。 */
        val sourceManifest = testingMetalavaSourceManifest.get().asFile
        sourceManifest.parentFile.mkdirs()
        sourceManifest.writeText(
            stagedSourceFiles.joinToString(separator = "\n", postfix = "\n", transform = File::getAbsolutePath),
        )
        /** 仅包含真实存在文件的稳定解析 classpath。 */
        val classpathValue = testingMetalavaApiClasspath.files
            .filter(File::exists)
            .sortedBy(File::getAbsolutePath)
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath)
        /** 每次执行前删除旧签名，防止失败后误用陈旧结果。 */
        val apiOutput = generatedTestingMetalavaApi.get().asFile
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
        val apiOutput = generatedTestingMetalavaApi.get().asFile
        apiOutput.writeText(apiOutput.readText().trimEnd() + "\n")
    }
}

/** 比较独立 pixel-testing 官方 API 与已评审 baseline。 */
val checkTestingMetalavaApi by tasks.registering {
    group = "verification"
    description = "Checks the independent pixel-testing Metalava API baseline."
    dependsOn(generateTestingMetalavaApi)
    inputs.file(testingMetalavaApiBaseline)
    inputs.file(generatedTestingMetalavaApi)

    doLast {
        /** 受源码控制的评审 baseline。 */
        val baselineFile = testingMetalavaApiBaseline.asFile
        /** 当前源码重新生成的官方签名。 */
        val actualFile = generatedTestingMetalavaApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing pixel-testing API baseline: ${baselineFile.path}. " +
                    "Run :pixel-testing:generateTestingMetalavaApi and review ${actualFile.path}.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-testing API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 为 Metalava 认定的 testing 公开类型生成确定性 javap 二进制签名。 */
val dumpTestingBinaryApi by tasks.registering {
    group = "verification"
    description = "Writes the independent pixel-testing JVM binary API dump."
    dependsOn("bundleLibRuntimeToDirRelease")
    dependsOn(generateTestingMetalavaApi)
    inputs.dir(testingReleaseClasses)
    inputs.file(generatedTestingMetalavaApi)
    outputs.file(generatedTestingBinaryApi)

    doLast {
        /** 当前 Release 变体的解包 class 目录。 */
        val classDirectory = testingReleaseClasses.get().asFile
        if (!classDirectory.exists()) {
            throw GradleException("Missing pixel-testing release classes at ${classDirectory.path}")
        }
        /** Metalava 根据 Kotlin metadata 认定的稳定公开类型。 */
        val publishedClasses = generatedTestingMetalavaApi.get().asFile.readTestingMetalavaBinaryClassNames()
        /** 当前 JDK 的 javap，用于保留真实 JVM descriptor。 */
        val javap = File(System.getProperty("java.home"), "bin/javap").absolutePath
        /** testing 公开类型及其 DefaultImpls 的稳定排序列表。 */
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
        /** 完整 testing 二进制签名文本。 */
        val output = buildString {
            appendLine("# pixel-testing binary API baseline")
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
                    verboseResult.standardOutput.asText.get().testingInternalBinaryMemberNames()
                } else {
                    emptySet()
                }
                /** 单个 class 的公开 JVM 结构。 */
                val result = providers.exec {
                    commandLine(javap, "-classpath", classDirectory.path, "-public", className)
                }
                appendLine(result.standardOutput.asText.get().normalizeTestingBinaryApiDump(hiddenMemberNames))
            }
        }
        /** 当前生成报告路径。 */
        val report = generatedTestingBinaryApi.get().asFile
        report.parentFile.mkdirs()
        report.writeText(output)
    }
}

/** 比较独立 pixel-testing JVM 二进制 API 与已评审 baseline。 */
val checkTestingBinaryApi by tasks.registering {
    group = "verification"
    description = "Checks the independent pixel-testing JVM binary API baseline."
    dependsOn(dumpTestingBinaryApi)
    inputs.file(testingBinaryApiBaseline)
    inputs.file(generatedTestingBinaryApi)

    doLast {
        /** 受源码控制的二进制 baseline。 */
        val baselineFile = testingBinaryApiBaseline.asFile
        /** 当前 Release class 重新生成的签名。 */
        val actualFile = generatedTestingBinaryApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing pixel-testing binary API baseline: ${baselineFile.path}. " +
                    "Run :pixel-testing:dumpTestingBinaryApi and review ${actualFile.path}.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-testing binary API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 把 pixel-testing 实际运行时 class artifact 固定为排序坐标清单。 */
val writeTestingReleaseDependencyManifest by tasks.registering {
    group = "verification"
    description = "Writes the exact pixel-testing Release runtime dependency coordinates."
    /** 受检的独立 testing Release 运行时配置。 */
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
    outputs.file(testingReleaseDependencyManifest)

    doLast {
        /** 最终坐标清单使用单一尾换行。 */
        val manifestFile = testingReleaseDependencyManifest.get().asFile
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(resolvedCoordinateProvider.get().joinToString("\n", postfix = "\n"))
    }
}

/** 检查 pixel-testing 自身 AAR、POM 和运行时依赖预算。 */
val checkTestingReleaseArtifactBudget by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the independent pixel-testing artifact and dependency budget."
    dependsOn("assembleRelease", "generatePomFileForReleasePublication")
    dependsOn(writeTestingReleaseDependencyManifest)
    workingDir(rootProject.projectDir)
    inputs.file(testingReleaseArtifactAar)
    inputs.file(testingReleaseArtifactPom)
    inputs.file(testingReleaseDependencyManifest)
    inputs.file(testingReleaseArtifactBudget)
    inputs.file(testingReleaseArtifactBudgetTool)
    outputs.file(testingReleaseArtifactBudgetReport)

    commandLine(
        "python3",
        testingReleaseArtifactBudgetTool.asFile,
        "--aar",
        testingReleaseArtifactAar.get().asFile,
        "--pom",
        testingReleaseArtifactPom.get().asFile,
        "--runtime-dependencies",
        testingReleaseDependencyManifest.get().asFile,
        "--budget",
        testingReleaseArtifactBudget.asFile,
        "--report",
        testingReleaseArtifactBudgetReport.get().asFile,
    )
}

/** 将已人工审阅的当前 Metalava 输出冻结为 testing baseline。 */
val updateTestingMetalavaApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(generateTestingMetalavaApi)
    doLast {
        /** 当前生成且已审阅的 testing API。 */
        val generated = generatedTestingMetalavaApi.get().asFile
        /** 纳入源码控制的 testing API baseline。 */
        val baseline = testingMetalavaApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

/** 将已人工审阅的当前 javap 输出冻结为 testing binary baseline。 */
val updateTestingBinaryApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(dumpTestingBinaryApi)
    doLast {
        /** 当前生成且已审阅的 testing JVM API。 */
        val generated = generatedTestingBinaryApi.get().asFile
        /** 纳入源码控制的 testing JVM baseline。 */
        val baseline = testingBinaryApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

/** 所有读取生成源码的 Android/Kotlin 任务必须先完成确定性同步。 */
tasks.configureEach {
    if (
        name != stageTestingSources.name &&
        (name.contains("Kotlin") || name.contains("SourcesJar") || name.contains("SourceJar") || name == "preBuild")
    ) {
        dependsOn(stageTestingSources)
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "pixel-testing"
                pom {
                    name.set("pixel-testing")
                    description.set("Pixel SDK deterministic widget testing, input driving, finders, and assertions.")
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

/** 从 Metalava v4 签名提取 testing 公开 class 的 JVM 相对路径。 */
fun File.readTestingMetalavaBinaryClassNames(): Set<String> {
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

/** 从 javap verbose 输出提取带 artifact 内部标记的 testing JVM member。 */
fun String.testingInternalBinaryMemberNames(): Set<String> {
    /** javap 当前正在描述的 public member 头。 */
    var currentMemberHeader: String? = null
    /** 最终应从 testing 稳定 ABI 中排除的 JVM 名称。 */
    val hiddenNames = mutableSetOf<String>()
    lineSequence().forEach { rawLine ->
        /** 当前行去除缩进后的 javap 文本。 */
        val trimmedLine = rawLine.trim()
        if (rawLine.startsWith("  public ") && trimmedLine.endsWith(';')) {
            currentMemberHeader = trimmedLine
        }
        if (trimmedLine == "com.purride.pixelui.internal.PixelArtifactInternalApi") {
            /** 注解所属 member 的 JVM 名称。 */
            val annotatedName = currentMemberHeader?.testingBinaryMemberName() ?: return@forEach
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
fun String.testingBinaryMemberName(): String? {
    /** 参数列表之前的返回类型与方法名部分。 */
    val prefix = substringBefore('(', missingDelimiterValue = "")
    return prefix.takeIf(String::isNotEmpty)?.substringAfterLast(' ')
}

/** 规范化 testing javap 输出并排除兄弟 artifact 内部 SPI。 */
fun String.normalizeTestingBinaryApiDump(hiddenMemberNames: Set<String>): String {
    return lineSequence()
        .filterNot { line -> line.startsWith("Compiled from ") }
        /** Kotlin internal module 后缀不属于稳定消费者 ABI。 */
        .filterNot { line -> Regex("\\\$pixel_[A-Za-z0-9_]+").containsMatchIn(line) }
        /** 注解标记的 JVM public sibling SPI 不属于稳定消费者 ABI。 */
        .filterNot { line -> line.trim().testingBinaryMemberName() in hiddenMemberNames }
        /** Kotlin 编译器为私有实现生成的 package 访问桥不属于消费者 ABI。 */
        .filterNot { line -> line.contains(" access\$") }
        .map(String::trimEnd)
        .joinToString("\n")
        .trim()
}

/** 把 testing 源码复制到与声明 package 一致的临时目录。 */
fun stageTestingMetalavaSources(sourceFiles: List<File>, destinationRoot: File): List<File> {
    if (destinationRoot.exists() && !destinationRoot.deleteRecursively()) {
        throw GradleException("Unable to clear pixel-testing Metalava staging directory: ${destinationRoot.path}")
    }
    if (!destinationRoot.mkdirs() && !destinationRoot.isDirectory) {
        throw GradleException("Unable to create pixel-testing Metalava staging directory: ${destinationRoot.path}")
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
                "Duplicate pixel-testing Metalava destination ${destinationFile.path}: " +
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
    dependsOn(checkTestingMetalavaApi)
    dependsOn(checkTestingBinaryApi)
    dependsOn(checkTestingReleaseArtifactBudget)
}
