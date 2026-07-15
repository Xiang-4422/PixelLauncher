import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.purride"
version = "1.0.0"

/** 与兼容聚合模块共享、但只由 pixel-core 编译的生产源码根目录。 */
val sharedProductionSourceRoot = rootProject.file("pixel-engine/src/main/kotlin")

/** 与兼容聚合模块共享的 core 单元测试根目录。 */
val sharedTestSourceRoot = rootProject.file("pixel-engine/src/test/kotlin")

android {
    namespace = "com.purride.pixelcore.artifact"
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
            /** Android 兼容实现已移出该目录，因此这里无需脆弱的 include/exclude 过滤。 */
            kotlin.directories.clear()
            kotlin.directories.add(sharedProductionSourceRoot.resolve("com/purride/pixelcore").path)
        }
        getByName("test") {
            /** 只运行不依赖 Android adapter 的 core 测试。 */
            kotlin.directories.clear()
            kotlin.directories.add(sharedTestSourceRoot.resolve("com/purride/pixelcore").path)
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

/** 所有公开 core API 必须显式声明 visibility 和返回类型。 */
kotlin {
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    testImplementation(libs.junit)
}

/** 独立解析的 Metalava CLI classpath，不进入 pixel-core 发布 POM。 */
val metalavaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(metalavaClasspath.name, libs.metalava.cli)
}

/** 当前 core artifact 的精确生产源码集合。 */
val coreProductionSources = fileTree(sharedProductionSourceRoot.resolve("com/purride/pixelcore")) {
    include("**/*.kt", "**/*.java")
}

/** Release variant 的依赖和 Android boot classpath，供 Metalava 解析 Android Bitmap ABI。 */
val metalavaApiClasspath = objects.fileCollection()

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (variant.name == "release") {
            metalavaApiClasspath.from(variant.compileClasspath, sdkComponents.bootClasspath)
        }
    }
}

/** 传给 Metalava 的确定性源码文件清单。 */
val metalavaSourceManifest = layout.buildDirectory.file("intermediates/metalava/release/source-files.txt")

/** 按声明 package 对齐的临时源码树，避免仓库功能目录影响 Metalava。 */
val metalavaStagedSources = layout.buildDirectory.dir("intermediates/metalava/release/sources")

/** 官方 Metalava 生成的当前 pixel-core API。 */
val generatedMetalavaApi = layout.buildDirectory.file("reports/api/pixel-core.metalava-api")

/** 经评审并纳入源码控制的 pixel-core API baseline。 */
val metalavaApiBaseline = layout.projectDirectory.file("api/pixel-core.metalava-api")

/** pixel-core release class 目录，用于生成 JVM 二进制 API。 */
val releaseRuntimeClasses = layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 当前生成的 JVM 二进制 API 报告。 */
val generatedBinaryApi = layout.buildDirectory.file("reports/api/pixel-core.binary-api")

/** 经评审并纳入源码控制的 pixel-core JVM 二进制 baseline。 */
val binaryApiBaseline = layout.projectDirectory.file("api/pixel-core.binary-api")

/** pixel-core 独立 AAR、class/method 和依赖预算。 */
val releaseArtifactBudget = layout.projectDirectory.file("config/release-artifact-budget.json")

/** 复用仓库统一的 classfile/POM 预算检查器。 */
val releaseArtifactBudgetTool =
    rootProject.layout.projectDirectory.file("tools/check_pixel_artifact_budget.py")

/** 当前 pixel-core Release AAR。 */
val releaseArtifactAar = layout.buildDirectory.file("outputs/aar/pixel-core-release.aar")

/** 当前 pixel-core 发布 POM。 */
val releaseArtifactPom = layout.buildDirectory.file("publications/release/pom-default.xml")

/** Gradle 实际解析的 pixel-core 运行时 artifact 清单。 */
val releaseRuntimeDependencyManifest =
    layout.buildDirectory.file("reports/artifact-budget/release-runtime-dependencies.txt")

/** pixel-core 独立 artifact 预算机器报告。 */
val releaseArtifactBudgetReport =
    layout.buildDirectory.file("reports/artifact-budget/release-artifact-budget.json")

/** 使用官方 Metalava 为独立 pixel-core 生成稳定源码 API。 */
val generateMetalavaApi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates the independent pixel-core API with the official Metalava CLI."
    classpath = metalavaClasspath
    mainClass.set("com.android.tools.metalava.Driver")

    inputs.files(coreProductionSources)
        .withPropertyName("coreProductionSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(metalavaApiClasspath)
        .withPropertyName("releaseCompileClasspath")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("src/main/AndroidManifest.xml")
        .withPropertyName("mainManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(metalavaSourceManifest)
    outputs.dir(metalavaStagedSources)
    outputs.file(generatedMetalavaApi)

    doFirst {
        /** 按仓库路径排序后的 core 源文件，确保跨机器输出一致。 */
        val sourceFiles = coreProductionSources.files.sortedBy(File::getAbsolutePath)
        if (sourceFiles.isEmpty()) {
            throw GradleException("Metalava received no pixel-core production sources.")
        }
        /** 按源码 package 重建临时路径，兼容仓库的功能分组目录。 */
        val stagedSourceRoot = metalavaStagedSources.get().asFile
        /** 已对齐 package 且稳定排序的临时源码。 */
        val stagedSourceFiles = stageCoreMetalavaSources(sourceFiles, stagedSourceRoot)
        /** 使用 @file 语法避免源码数量增长后超过命令行长度上限。 */
        val sourceManifest = metalavaSourceManifest.get().asFile
        sourceManifest.parentFile.mkdirs()
        sourceManifest.writeText(
            stagedSourceFiles.joinToString(separator = "\n", postfix = "\n", transform = File::getAbsolutePath),
        )
        /** 只包含真实存在文件的稳定 classpath。 */
        val classpathValue = metalavaApiClasspath.files
            .filter(File::exists)
            .sortedBy(File::getAbsolutePath)
            .joinToString(File.pathSeparator, transform = File::getAbsolutePath)
        /** 每次执行前删除旧输出，避免失败后误用陈旧报告。 */
        val apiOutput = generatedMetalavaApi.get().asFile
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
                "--stub-packages",
                "com.purride.pixelcore.*${File.pathSeparator}-com.purride.pixelcore.internal.*",
                "--format",
                "v4",
                "--api",
                apiOutput.absolutePath,
            ),
        )
    }

    doLast {
        /** 单一尾换行避免不同工具版本产生无意义 diff。 */
        val apiOutput = generatedMetalavaApi.get().asFile
        apiOutput.writeText(apiOutput.readText().trimEnd() + "\n")
    }
}

/** 比较独立 pixel-core 官方 API 与已评审 baseline。 */
val checkMetalavaApi by tasks.registering {
    group = "verification"
    description = "Checks the independent pixel-core Metalava API baseline."
    dependsOn(generateMetalavaApi)

    inputs.file(metalavaApiBaseline)
    inputs.file(generatedMetalavaApi)

    doLast {
        /** 受源码控制的评审 baseline。 */
        val baselineFile = metalavaApiBaseline.asFile
        /** 当前源码重新生成的官方签名。 */
        val actualFile = generatedMetalavaApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing pixel-core API baseline: ${baselineFile.path}. " +
                    "Run :pixel-core:generateMetalavaApi and review ${actualFile.path}.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-core API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 为 Metalava 认定的公开类型生成确定性 javap 二进制签名。 */
val dumpBinaryApi by tasks.registering {
    group = "verification"
    description = "Writes the independent pixel-core JVM binary API dump."
    dependsOn("bundleLibRuntimeToDirRelease")
    dependsOn(generateMetalavaApi)

    inputs.dir(releaseRuntimeClasses)
    inputs.file(generatedMetalavaApi)
    outputs.file(generatedBinaryApi)

    doLast {
        /** 当前 release 变体的解包 class 目录。 */
        val classDirectory = releaseRuntimeClasses.get().asFile
        if (!classDirectory.exists()) {
            throw GradleException("Missing pixel-core release classes at ${classDirectory.path}")
        }
        /** Metalava 根据 Kotlin metadata 认定的稳定公开类型。 */
        val publishedClasses = generatedMetalavaApi.get().asFile.readMetalavaBinaryClassNames()
        /** 当前 JDK 的 javap，用于保留真实 JVM descriptor 和 synthetic default ABI。 */
        val javap = File(System.getProperty("java.home"), "bin/javap").absolutePath
        /** 公开类型及其 DefaultImpls 的稳定排序列表。 */
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
        /** 完整二进制签名文本。 */
        val output = buildString {
            appendLine("# pixel-core binary API baseline")
            classNames.forEach { className ->
                /** 单个 class 的公开 JVM 结构。 */
                val result = providers.exec {
                    commandLine(javap, "-classpath", classDirectory.path, "-public", className)
                }
                appendLine(result.standardOutput.asText.get().normalizeBinaryApiDump())
            }
        }
        /** 当前生成报告路径。 */
        val report = generatedBinaryApi.get().asFile
        report.parentFile.mkdirs()
        report.writeText(output)
    }
}

/** 比较独立 pixel-core JVM 二进制 API 与已评审 baseline。 */
val checkBinaryApi by tasks.registering {
    group = "verification"
    description = "Checks the independent pixel-core JVM binary API baseline."
    dependsOn(dumpBinaryApi)

    inputs.file(binaryApiBaseline)
    inputs.file(generatedBinaryApi)

    doLast {
        /** 受源码控制的二进制 baseline。 */
        val baselineFile = binaryApiBaseline.asFile
        /** 当前 release class 重新生成的签名。 */
        val actualFile = generatedBinaryApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing pixel-core binary API baseline: ${baselineFile.path}. " +
                    "Run :pixel-core:dumpBinaryApi and review ${actualFile.path}.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-core binary API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 把 pixel-core 实际运行时 class artifact 固定为排序坐标清单。 */
val writeReleaseRuntimeDependencyManifest by tasks.registering {
    group = "verification"
    description = "Writes the exact pixel-core Release runtime dependency coordinates."

    /** 受检的独立 core Release 运行时配置。 */
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
    outputs.file(releaseRuntimeDependencyManifest)

    doLast {
        /** 最终坐标清单使用单一尾换行。 */
        val manifestFile = releaseRuntimeDependencyManifest.get().asFile
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(resolvedCoordinateProvider.get().joinToString("\n", postfix = "\n"))
    }
}

/** 检查 pixel-core 自身 AAR、POM 和运行时依赖预算。 */
val checkReleaseArtifactBudget by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the independent pixel-core artifact and dependency budget."
    dependsOn("assembleRelease", "generatePomFileForReleasePublication")
    dependsOn(writeReleaseRuntimeDependencyManifest)
    workingDir(rootProject.projectDir)

    inputs.file(releaseArtifactAar)
    inputs.file(releaseArtifactPom)
    inputs.file(releaseRuntimeDependencyManifest)
    inputs.file(releaseArtifactBudget)
    inputs.file(releaseArtifactBudgetTool)
    outputs.file(releaseArtifactBudgetReport)

    commandLine(
        "python3",
        releaseArtifactBudgetTool.asFile,
        "--aar",
        releaseArtifactAar.get().asFile,
        "--pom",
        releaseArtifactPom.get().asFile,
        "--runtime-dependencies",
        releaseRuntimeDependencyManifest.get().asFile,
        "--budget",
        releaseArtifactBudget.asFile,
        "--report",
        releaseArtifactBudgetReport.get().asFile,
    )
}

tasks.named("check") {
    dependsOn(checkMetalavaApi)
    dependsOn(checkBinaryApi)
    dependsOn(checkReleaseArtifactBudget)
}

publishing {
    repositories {
        maven {
            name = "Compatibility"
            url = uri(rootProject.layout.buildDirectory.dir("compatibility-repository").get().asFile)
        }
    }

    publications {
        create<MavenPublication>("release") {
            groupId = "com.purride"
            artifactId = "pixel-core"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("pixel-core")
                description.set("Pixel buffer, color, font, bitmap, sprite, and resource data primitives.")
            }
        }
    }
}

/** 从 Metalava v4 签名提取公开 class 的 JVM 相对路径。 */
fun File.readMetalavaBinaryClassNames(): Set<String> {
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

/** 规范化 javap 中的绝对 class 路径、空行和工具噪声。 */
fun String.normalizeBinaryApiDump(): String {
    return lineSequence()
        .filterNot { line -> line.startsWith("Compiled from ") }
        /** Kotlin internal 成员的 module 后缀不属于稳定消费者 ABI。 */
        .filterNot { line -> Regex("\\\$pixel_[A-Za-z0-9_]+").containsMatchIn(line) }
        .map(String::trimEnd)
        .joinToString("\n")
        .trim()
}

/**
 * 把 core 源码复制到与声明 package 一致的临时目录，供 Metalava 确定性解析。
 */
fun stageCoreMetalavaSources(sourceFiles: List<File>, destinationRoot: File): List<File> {
    if (destinationRoot.exists() && !destinationRoot.deleteRecursively()) {
        throw GradleException("Unable to clear pixel-core Metalava staging directory: ${destinationRoot.path}")
    }
    if (!destinationRoot.mkdirs() && !destinationRoot.isDirectory) {
        throw GradleException("Unable to create pixel-core Metalava staging directory: ${destinationRoot.path}")
    }
    /** package 声明匹配式，不依赖文件当前功能目录。 */
    val packagePattern = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\b")
    /** 目标文件到原文件的映射，用于拒绝同包同名碰撞。 */
    val destinationOwners = mutableMapOf<File, File>()
    /** 最终返回给 @file 清单的临时源码。 */
    val stagedFiles = mutableListOf<File>()
    sourceFiles.forEach { sourceFile ->
        /** 当前源文件声明的 Kotlin/Java package。 */
        val packageName = packagePattern.find(sourceFile.readText())?.groupValues?.get(1)
            ?: throw GradleException("Missing package declaration in pixel-core source: ${sourceFile.path}")
        /** 与声明 package 对齐的临时目录。 */
        val packageDirectory = File(destinationRoot, packageName.replace('.', File.separatorChar))
        /** 保留原文件名的临时目标。 */
        val destination = File(packageDirectory, sourceFile.name)
        /** 已占用相同目标的源文件；非空表示源码布局发生碰撞。 */
        val existingOwner = destinationOwners.putIfAbsent(destination, sourceFile)
        if (existingOwner != null) {
            throw GradleException(
                "pixel-core Metalava staging collision: ${existingOwner.path} and ${sourceFile.path}",
            )
        }
        if (!packageDirectory.mkdirs() && !packageDirectory.isDirectory) {
            throw GradleException("Unable to create Metalava package directory: ${packageDirectory.path}")
        }
        sourceFile.copyTo(destination, overwrite = false)
        stagedFiles += destination
    }
    return stagedFiles.sortedBy { stagedFile -> stagedFile.relativeTo(destinationRoot).invariantSeparatorsPath }
}
