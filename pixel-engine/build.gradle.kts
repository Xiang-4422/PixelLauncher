import groovy.json.JsonSlurper
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.purride"
version = "1.0.0"

/** 仓库中唯一受审的 artifact 所有权清单。 */
val artifactOwnershipManifestFile = file("config/artifact-ownership.json")

/** 拆分期间保留冻结 package 与源码历史的共享 Kotlin 源码根。 */
val sharedProductionSourceRoot = file("src/main/kotlin")

/** 解析后的 artifact ownership JSON 根对象。 */
@Suppress("UNCHECKED_CAST")
val artifactOwnershipConfiguration =
    JsonSlurper().parse(artifactOwnershipManifestFile) as Map<String, Any?>

/** 清单中的源码归属配置。 */
@Suppress("UNCHECKED_CAST")
val artifactOwnershipRules =
    artifactOwnershipConfiguration.getValue("ownership") as Map<String, Any?>

/** 优先于目录规则的精确文件归属表。 */
@Suppress("UNCHECKED_CAST")
val exactAggregateArtifactOwners = artifactOwnershipRules.getValue("files") as Map<String, String>

/** 使用最长匹配原则的目录归属规则。 */
@Suppress("UNCHECKED_CAST")
val aggregateArtifactPathPrefixes =
    artifactOwnershipRules.getValue("pathPrefixes") as List<Map<String, String>>

/**
 * 按“精确文件优先、最长目录前缀次之”解析源码所属 artifact。
 *
 * @param relativePath 相对于共享 Kotlin 源码根的规范路径。
 * @return 清单声明的 artifact 名称；没有归属时返回 null。
 */
fun resolveAggregateArtifactOwner(relativePath: String): String? {
    exactAggregateArtifactOwners[relativePath]?.let { exactOwner -> return exactOwner }
    /** 当前路径命中的全部目录规则。 */
    val matchingRules = aggregateArtifactPathPrefixes.filter { rule ->
        /** 追加目录分隔符，防止短目录名误匹配同前缀目录。 */
        val pathPrefix = rule.getValue("path").trimEnd('/') + "/"
        relativePath.startsWith(pathPrefix)
    }
    /** 最具体的目录规则决定源码归属。 */
    return matchingRules.maxByOrNull { rule -> rule.getValue("path").length }?.get("artifact")
}

/** 已由独立依赖提供、不得再次打入聚合 AAR 的 artifact。 */
val externallyPackagedArtifacts = setOf(
    "pixel-core",
    "pixel-runtime",
    "pixel-widgets",
    "pixel-navigation",
    "pixel-android",
    "pixel-testing",
    "pixel-debug",
)

/** 聚合模块仍负责打包的生产 Kotlin 相对路径。 */
val aggregateProductionPaths = fileTree(sharedProductionSourceRoot) {
    include("**/*.kt")
}.files.map { sourceFile ->
    sourceFile.relativeTo(sharedProductionSourceRoot).invariantSeparatorsPath
}.filter { relativePath ->
    resolveAggregateArtifactOwner(relativePath) !in externallyPackagedArtifacts
}.sorted()

/** build 目录中由归属清单生成的聚合模块 Kotlin 源码根。 */
val stagedAggregateSourceRoot = layout.buildDirectory.dir("generated/artifact-sources/main/kotlin")

/** 将仍归聚合模块打包的源码确定性同步到生成源集。 */
val stageAggregateSources by tasks.registering(Sync::class) {
    inputs.file(artifactOwnershipManifestFile)
    from(sharedProductionSourceRoot) {
        include(aggregateProductionPaths)
    }
    into(stagedAggregateSourceRoot)
    includeEmptyDirs = false
}

android {
    namespace = "com.purride.pixelengine"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        aarMetadata {
            /** Kotlin 2.2 字节码需要消费者至少使用具备对应 D8/R8 支持的 AGP。 */
            minAgpVersion = "8.10.0"
            /** 发布物以 Android 36 API 编译，低版本 compileSdk 必须在解析期明确失败。 */
            minCompileSdk = 36
        }
    }

    sourceSets {
        getByName("main") {
            /** core 与 runtime 由依赖提供，聚合 AAR 只编译其余 owner 的生成源码。 */
            kotlin.directories.clear()
            kotlin.directories.add(stagedAggregateSourceRoot.get().asFile.path)
            /** Unicode Bidi Java 实现已经由 pixel-runtime 提供，聚合模块不得重复编译。 */
            java.directories.clear()
            /** Unicode 数据与许可证均由 pixel-runtime 发布，聚合 AAR 不重复打包同名资源。 */
            resources.directories.clear()
        }
        getByName("test") {
            /** 纯 core 测试由 pixel-core 独立执行，聚合模块只运行 UI 与 Android adapter 测试。 */
            kotlin.directories.clear()
            kotlin.directories.addAll(
                listOf(
                    "src/test/kotlin/com/purride/pixelui",
                    "src/test/kotlin/com/purride/pixelandroid",
                ),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

/**
 * 强制 main 源集所有公开 API 显式标注 visibility。
 *
 * 已切到 `Strict` 模式：未标 visibility 的公开/protected 成员、未声明
 * 返回类型的 public fun 都会变成编译错误，永久守住公开 API 表达明确性
 * 的红线。如需临时回退到 `Warning` 做大规模迁移，切换枚举即可。
 */
kotlin {
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    api(project(":pixel-core"))
    api(project(":pixel-runtime"))
    api(project(":pixel-widgets"))
    api(project(":pixel-navigation"))
    api(project(":pixel-android"))
    api(project(":pixel-testing"))
    api(project(":pixel-debug"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.fragment.ktx)
}

/** Isolated, resolvable classpath for the official Metalava CLI. */
val metalavaClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(metalavaClasspath.name, libs.metalava.cli)
}

/** 旧聚合坐标承诺的完整 Kotlin 与 Java API 源码并集。 */
val metalavaSourceDirectories = objects.fileCollection()

/** 全部 Kotlin 源码按原始路径扫描，避免 artifact 物理拆分制造虚假 API 差异。 */
val aggregateMetalavaKotlinSources = fileTree(sharedProductionSourceRoot) {
    include("**/*.kt", "**/*.java")
}

/** runtime 拥有的 Unicode Bidi Java 参考实现也属于旧聚合坐标 API 并集。 */
val aggregateMetalavaJavaSources = fileTree("src/main/java") {
    include("**/*.java")
}

metalavaSourceDirectories.from(aggregateMetalavaKotlinSources, aggregateMetalavaJavaSources)

/** Release compile dependencies plus the Android boot classpath used for source resolution. */
val metalavaApiClasspath = objects.fileCollection()

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (variant.name == "release") {
            metalavaApiClasspath.from(variant.compileClasspath, sdkComponents.bootClasspath)
        }
    }
}

/** 所有读取聚合生成源码的构建任务必须先完成确定性同步。 */
tasks.configureEach {
    if (
        name != stageAggregateSources.name &&
        (name.contains("Kotlin") || name.contains("SourcesJar") || name.contains("SourceJar") || name == "preBuild")
    ) {
        dependsOn(stageAggregateSources)
    }
}

/** runtime Debug classes jar，仅供聚合模块的白盒测试读取 Kotlin internal。 */
val pixelRuntimeDebugFriendJar = project(":pixel-runtime").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/** core Debug classes jar，仅供聚合模块的历史白盒测试读取 Kotlin internal。 */
val pixelCoreDebugFriendJar = project(":pixel-core").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/** widgets Debug classes jar，仅供聚合模块的历史白盒测试读取 Kotlin internal。 */
val pixelWidgetsDebugFriendJar = project(":pixel-widgets").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/** navigation Debug classes jar，仅供聚合模块的历史白盒测试读取 Kotlin internal。 */
val pixelNavigationDebugFriendJar = project(":pixel-navigation").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/** android Debug classes jar，仅供聚合模块的历史白盒测试读取 Kotlin internal。 */
val pixelAndroidDebugFriendJar = project(":pixel-android").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/** testing Debug classes jar，仅供聚合模块的历史白盒测试读取 Kotlin internal。 */
val pixelTestingDebugFriendJar = project(":pixel-testing").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/** debug Debug classes jar，仅供聚合模块的历史白盒测试读取 Kotlin internal。 */
val pixelDebugDebugFriendJar = project(":pixel-debug").layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar",
)

/**
 * 聚合回归测试历史上覆盖完整单模块内部实现；拆分后只给测试编译开放已拆 artifact 的 friend path。
 *
 * 生产源码编译没有 friend path，所有跨 artifact 生产调用仍必须经过明确 public/internal SPI。
 */
tasks.withType<KotlinJvmCompile>().configureEach {
    if (name == "compileDebugUnitTestKotlin" || name == "compileDebugAndroidTestKotlin") {
        dependsOn(":pixel-runtime:bundleLibCompileToJarDebug")
        dependsOn(":pixel-core:bundleLibCompileToJarDebug")
        dependsOn(":pixel-widgets:bundleLibCompileToJarDebug")
        dependsOn(":pixel-navigation:bundleLibCompileToJarDebug")
        dependsOn(":pixel-android:bundleLibCompileToJarDebug")
        dependsOn(":pixel-testing:bundleLibCompileToJarDebug")
        dependsOn(":pixel-debug:bundleLibCompileToJarDebug")
        inputs.file(pixelRuntimeDebugFriendJar)
        inputs.file(pixelCoreDebugFriendJar)
        inputs.file(pixelWidgetsDebugFriendJar)
        inputs.file(pixelNavigationDebugFriendJar)
        inputs.file(pixelAndroidDebugFriendJar)
        inputs.file(pixelTestingDebugFriendJar)
        inputs.file(pixelDebugDebugFriendJar)
        compilerOptions.freeCompilerArgs.add(
            pixelRuntimeDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
        compilerOptions.freeCompilerArgs.add(
            pixelCoreDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
        compilerOptions.freeCompilerArgs.add(
            pixelWidgetsDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
        compilerOptions.freeCompilerArgs.add(
            pixelNavigationDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
        compilerOptions.freeCompilerArgs.add(
            pixelAndroidDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
        compilerOptions.freeCompilerArgs.add(
            pixelTestingDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
        compilerOptions.freeCompilerArgs.add(
            pixelDebugDebugFriendJar.map { friendJar -> "-Xfriend-paths=${friendJar.asFile.absolutePath}" },
        )
    }
}

/** Deterministic source-file manifest consumed through Metalava's @file syntax. */
val metalavaSourceManifest = layout.buildDirectory.file("intermediates/metalava/release/source-files.txt")

/** Package-aligned source tree required because repository folders intentionally differ from Kotlin packages. */
val metalavaStagedSources = layout.buildDirectory.dir("intermediates/metalava/release/sources")

/** Generated current API signature; this is reviewed before updating the tracked baseline. */
val generatedMetalavaApi = layout.buildDirectory.file("reports/api/pixel-engine.metalava-api")

/** Tracked Metalava signature that freezes the current development API surface. */
val metalavaApiBaseline = layout.projectDirectory.file("api/pixel-engine.metalava-api")

/** Standalone scanner shared by the Gradle gate and its negative tooling tests. */
val stableApiBoundaryTool = rootProject.layout.projectDirectory.file("tools/check_stable_api_boundary.py")

/** Machine-readable proof that no stable signature mentions an implementation-internal package. */
val stableApiBoundaryReport =
    layout.buildDirectory.file("reports/compatibility/stable-api-boundary.json")

/** M7-1 的 artifact 文件归属、无环依赖和平台边界清单。 */
val artifactOwnershipManifest = layout.projectDirectory.file("config/artifact-ownership.json")

/** 执行 M7-1 源码边界审计的独立工具。 */
val artifactBoundaryTool =
    rootProject.layout.projectDirectory.file("tools/check_pixel_artifact_boundaries.py")

/** M7-1 artifact 边界的机器可读验收报告。 */
val artifactBoundaryReport =
    layout.buildDirectory.file("reports/architecture/artifact-boundaries.json")

/** M6-3 固定的 Release AAR、class/method 与运行时依赖预算。 */
val releaseArtifactBudget = layout.projectDirectory.file("config/release-artifact-budget.json")

/** 独立解析 AAR classfile、POM 和解析后运行时依赖的预算检查器。 */
val releaseArtifactBudgetTool =
    rootProject.layout.projectDirectory.file("tools/check_pixel_artifact_budget.py")

/** 当前 Release AAR，由预算门禁直接检查最终发布字节。 */
val releaseArtifactAar = layout.buildDirectory.file("outputs/aar/pixel-engine-release.aar")

/** 聚合坐标传递依赖的 pixel-core Release AAR。 */
val pixelCoreReleaseArtifactAar =
    project(":pixel-core").layout.buildDirectory.file("outputs/aar/pixel-core-release.aar")

/** 聚合坐标传递依赖的 pixel-runtime Release AAR。 */
val pixelRuntimeReleaseArtifactAar =
    project(":pixel-runtime").layout.buildDirectory.file("outputs/aar/pixel-runtime-release.aar")

/** 聚合坐标传递依赖的 pixel-widgets Release AAR。 */
val pixelWidgetsReleaseArtifactAar =
    project(":pixel-widgets").layout.buildDirectory.file("outputs/aar/pixel-widgets-release.aar")

/** 聚合坐标传递依赖的 pixel-navigation Release AAR。 */
val pixelNavigationReleaseArtifactAar =
    project(":pixel-navigation").layout.buildDirectory.file("outputs/aar/pixel-navigation-release.aar")

/** 聚合坐标传递依赖的 pixel-android Release AAR。 */
val pixelAndroidReleaseArtifactAar =
    project(":pixel-android").layout.buildDirectory.file("outputs/aar/pixel-android-release.aar")

/** 聚合坐标传递依赖的 pixel-testing Release AAR。 */
val pixelTestingReleaseArtifactAar =
    project(":pixel-testing").layout.buildDirectory.file("outputs/aar/pixel-testing-release.aar")

/** 聚合坐标传递依赖的 pixel-debug Release AAR。 */
val pixelDebugReleaseArtifactAar =
    project(":pixel-debug").layout.buildDirectory.file("outputs/aar/pixel-debug-release.aar")

/** 当前 Maven Publication 生成的发布 POM。 */
val releaseArtifactPom = layout.buildDirectory.file("publications/release/pom-default.xml")

/** Gradle 实际解析出的 Release 运行时 artifact 坐标清单。 */
val releaseRuntimeDependencyManifest =
    layout.buildDirectory.file("reports/artifact-budget/release-runtime-dependencies.txt")

/** 预算门禁写出的完整机器可读结果。 */
val releaseArtifactBudgetReport =
    layout.buildDirectory.file("reports/artifact-budget/release-artifact-budget.json")

/** Standalone scanner that proves every complete M5 theme token has a runtime consumer. */
val themeTokenCoverageTool = rootProject.layout.projectDirectory.file("tools/check_theme_token_coverage.py")

/** Canonical component-token constructor used as the standard-component inventory. */
val themeComponentTokenDefinition =
    layout.projectDirectory.file("src/main/kotlin/com/purride/pixelui/theme/PixelComponentTokens.kt")

/** Production source root searched for actual component and foundation token consumption. */
val themeTokenRuntimeSourceRoot = layout.projectDirectory.dir("src/main/kotlin")

/** Machine-readable M5 token-consumption evidence retained by the verification gate. */
val themeTokenCoverageReport = layout.buildDirectory.file("reports/theme/theme-token-coverage.json")

/** Last externally released Metalava signature, intentionally absent before the first formal release. */
val releasedMetalavaApiBaseline = layout.projectDirectory.file("api/pixel-engine.released.metalava-api")

/** Machine-readable outcome that distinguishes a real compatibility pass from a pre-release skip. */
val releasedMetalavaCompatibilityReport =
    layout.buildDirectory.file("reports/api/pixel-engine.metalava-released-compatibility.properties")

/** Success marker written only after Metalava exits normally against a real released baseline. */
val releasedMetalavaCompatibilitySuccessMarker =
    layout.buildDirectory.file("intermediates/metalava/release/released-compatibility.passed")

/** Generates the canonical current signature from the AGP release variant. */
val generateMetalavaApi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates the release API signature with the official Metalava CLI."
    classpath = metalavaClasspath
    mainClass.set("com.android.tools.metalava.Driver")

    inputs.files(metalavaSourceDirectories)
        .withPropertyName("releaseSources")
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
        /** Original release sources discovered through AGP rather than hard-coded source-set paths. */
        val originalSourceFiles = metalavaSourceDirectories.asFileTree.matching {
            include("**/*.java", "**/*.kt")
        }.files.sortedBy(File::getAbsolutePath)
        if (originalSourceFiles.isEmpty()) {
            throw GradleException("Metalava received no release Kotlin or Java sources from the AGP Variant API.")
        }

        /** Disposable root whose paths match each source file's declared package. */
        val stagedSourceRoot = metalavaStagedSources.get().asFile
        /** Canonically staged files written to Metalava's deterministic @file manifest. */
        val stagedSourceFiles = stageMetalavaSources(originalSourceFiles, stagedSourceRoot)
        /** Source manifest avoids command-line length limits for the complete engine source set. */
        val sourceManifest = metalavaSourceManifest.get().asFile
        sourceManifest.parentFile.mkdirs()
        sourceManifest.writeText(
            stagedSourceFiles.joinToString(separator = "\n", postfix = "\n") { sourceFile ->
                sourceFile.absolutePath
            },
        )

        /** Signature output recreated on every executed generation to prevent stale-success results. */
        val apiOutput = generatedMetalavaApi.get().asFile
        apiOutput.parentFile.mkdirs()
        apiOutput.delete()
        setArgs(
            buildMetalavaArguments(
                sourceManifest = sourceManifest,
                sourceRoot = stagedSourceRoot,
                apiClasspath = metalavaApiClasspath.files,
                manifest = file("src/main/AndroidManifest.xml"),
                apiOutput = apiOutput,
            ),
        )
    }

    doLast {
        /** Canonical single trailing newline prevents platform/tool-specific blank-line drift. */
        val apiOutput = generatedMetalavaApi.get().asFile
        apiOutput.writeText(apiOutput.readText().trimEnd() + "\n")
    }
}

/** Fails when the official current signature differs from its reviewed tracked baseline. */
val checkMetalavaApi by tasks.registering {
    group = "verification"
    description = "Checks the generated Metalava signature against its reviewed current baseline."
    dependsOn(generateMetalavaApi)

    inputs.files(metalavaApiBaseline).withPropertyName("reviewedMetalavaApiBaseline")
    inputs.file(generatedMetalavaApi)

    doLast {
        /** Reviewed signature frozen in source control after an intentional API review. */
        val baselineFile = metalavaApiBaseline.asFile
        /** Fresh official Metalava signature produced by the generation dependency. */
        val actualFile = generatedMetalavaApi.get().asFile
        if (!baselineFile.exists()) {
            throw GradleException(
                "Missing Metalava API baseline: ${baselineFile.path}. Run :pixel-engine:generateMetalavaApi, " +
                    "review ${actualFile.path}, then add the reviewed signature as the tracked baseline.",
            )
        }
        if (baselineFile.readText() != actualFile.readText()) {
            throw GradleException(
                "pixel-engine Metalava API changed. Review ${actualFile.path} and update ${baselineFile.path} " +
                    "only for an intentional API change.",
            )
        }
    }
}

/** 将人工审阅后的聚合 Metalava 输出冻结为当前 API baseline。 */
val updateMetalavaApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(generateMetalavaApi)
    doLast {
        /** 当前生成且已完成评审的聚合源码 API。 */
        val generated = generatedMetalavaApi.get().asFile
        /** 纳入源码控制的聚合源码 API baseline。 */
        val baseline = metalavaApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

/** Rejects internal parents, parameters, returns, fields, and other stable signature references. */
val checkStableApiBoundary by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails when the official stable API signature exposes any internal package type."
    dependsOn(generateMetalavaApi)
    workingDir(rootProject.projectDir)

    inputs.file(generatedMetalavaApi)
        .withPropertyName("generatedMetalavaApi")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(stableApiBoundaryTool)
        .withPropertyName("stableApiBoundaryTool")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(stableApiBoundaryReport)

    commandLine(
        "python3",
        stableApiBoundaryTool.asFile,
        "--signature",
        generatedMetalavaApi.get().asFile,
        "--report",
        stableApiBoundaryReport.get().asFile,
    )
}

/** 阻止未归属源码、artifact 依赖环、非法反向边和 Android/Compose 泄漏。 */
val checkArtifactBoundaries by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks M7-1 artifact ownership, acyclic dependencies, and platform boundaries."
    workingDir(rootProject.projectDir)

    inputs.file(artifactOwnershipManifest)
        .withPropertyName("artifactOwnershipManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(artifactBoundaryTool)
        .withPropertyName("artifactBoundaryTool")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin"))
        .withPropertyName("artifactBoundarySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(artifactBoundaryReport)

    commandLine(
        "python3",
        artifactBoundaryTool.asFile,
        "--manifest",
        artifactOwnershipManifest.asFile,
        "--report",
        artifactBoundaryReport.get().asFile,
    )
}

/** Runs Metalava's official compatibility algorithm when a released signature exists. */
val checkMetalavaReleasedCompatibility by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks source and binary compatibility against the last released Metalava signature."
    dependsOn(generateMetalavaApi)
    classpath = metalavaClasspath
    mainClass.set("com.android.tools.metalava.Driver")

    inputs.files(metalavaSourceDirectories)
        .withPropertyName("releaseSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(metalavaApiClasspath)
        .withPropertyName("releaseCompileClasspath")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(releasedMetalavaApiBaseline).optional()

    onlyIf {
        /** Frozen release signature; its absence is expected only before the first formal release. */
        val releasedBaseline = releasedMetalavaApiBaseline.asFile
        if (!releasedBaseline.exists()) {
            logger.warn(
                "SKIPPED Metalava released compatibility: ${releasedBaseline.path} does not exist because " +
                    "pixel-engine has no frozen formal release signature yet.",
            )
            false
        } else {
            true
        }
    }

    doFirst {
        /** Marker is removed before execution so a failed process cannot inherit an old pass. */
        val successMarker = releasedMetalavaCompatibilitySuccessMarker.get().asFile
        successMarker.delete()
        /** Staged-source manifest generated by the current-signature dependency. */
        val sourceManifest = metalavaSourceManifest.get().asFile
        if (!sourceManifest.exists()) {
            throw GradleException("Missing Metalava source manifest: ${sourceManifest.path}")
        }
        setArgs(
            buildMetalavaArguments(
                sourceManifest = sourceManifest,
                sourceRoot = metalavaStagedSources.get().asFile,
                apiClasspath = metalavaApiClasspath.files,
                manifest = file("src/main/AndroidManifest.xml"),
                releasedApi = releasedMetalavaApiBaseline.asFile,
            ),
        )
    }

    doLast {
        /** Marker is written only after Metalava's JavaExec action completes without failure. */
        val successMarker = releasedMetalavaCompatibilitySuccessMarker.get().asFile
        successMarker.parentFile.mkdirs()
        successMarker.writeText("passed\n")
    }
}

/** Persists a stable compatibility status for release automation and audit evidence. */
val recordMetalavaReleasedCompatibilityStatus by tasks.registering {
    group = "verification"
    description = "Records whether released Metalava compatibility passed, failed, or had no baseline."
    outputs.file(releasedMetalavaCompatibilityReport)
    outputs.upToDateWhen { false }

    doLast {
        /** Released baseline whose absence intentionally keeps compatibility status in SKIPPED state. */
        val releasedBaseline = releasedMetalavaApiBaseline.asFile
        /** Success marker proves the Metalava process reached its post-execution action. */
        val successMarker = releasedMetalavaCompatibilitySuccessMarker.get().asFile
        /** Stable machine-readable status consumed by release automation. */
        val reportStatus = when {
            !releasedBaseline.exists() -> "SKIPPED"
            !successMarker.exists() -> "FAILED"
            else -> "PASSED"
        }
        /** Stable reason code distinguishes the compatibility outcome without parsing prose logs. */
        val reportReason = when (reportStatus) {
            "SKIPPED" -> "NO_RELEASED_BASELINE"
            "FAILED" -> "INCOMPATIBLE_API_OR_TOOL_FAILURE"
            else -> "COMPATIBLE"
        }
        /** Machine-readable report stored alongside the generated API signature. */
        val reportFile = releasedMetalavaCompatibilityReport.get().asFile
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("status=$reportStatus")
                appendLine("reason=$reportReason")
                appendLine("baseline=api/${releasedBaseline.name}")
            },
        )
    }
}

checkMetalavaReleasedCompatibility {
    finalizedBy(recordMetalavaReleasedCompatibilityStatus)
}

val publicApiBaseline = layout.projectDirectory.file("api/pixel-engine.api")
val binaryApiBaseline = layout.projectDirectory.file("api/pixel-engine.binary-api")
/** 1.0 发布要求所有显式 public/protected 声明都有有效 KDoc。 */
val kdocCoverageMinimumPercent = 100.0

tasks.register<Exec>("generatePixelGlyphPacks") {
    group = "build"
    description = "Generates the built-in TTF glyph packs with the shared converter CLI."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/generate_pixel_glyph_packs.py"))
}

val testPixelTooling by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs all repository Python tooling tests, including security and baseline gates."
    workingDir(rootProject.projectDir)
    commandLine(
        "python3",
        "-m",
        "unittest",
        "discover",
        "-s",
        "tools/tests",
        "-p",
        "test_*.py",
    )
}

/** Tracked Unicode 17 outputs verified against checksum-pinned official grapheme inputs. */
val unicodeGraphemeResourceDirectory = layout.projectDirectory.dir("src/test/resources/unicode/17.0.0")

/** Downloads checksum-pinned inputs and fails when fixed grapheme outputs or license are stale. */
val checkUnicodeGraphemeDataGeneration by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies Unicode 17 grapheme outputs against checksum-pinned official inputs."
    workingDir(rootProject.projectDir)
    inputs.file(rootProject.file("tools/generate_unicode_grapheme_data.py"))
    inputs.files(
        unicodeGraphemeResourceDirectory.file("GraphemeBreakTest.txt"),
        unicodeGraphemeResourceDirectory.file("LICENSE-UNICODE.txt"),
    )
    inputs.file(
        layout.projectDirectory.file(
            "src/main/kotlin/com/purride/pixelui/internal/text/UnicodeGraphemeData.kt",
        ),
    )
    inputs.file(layout.projectDirectory.file("src/main/resources/META-INF/LICENSE-UNICODE.txt"))
    commandLine(
        "python3",
        rootProject.file("tools/generate_unicode_grapheme_data.py"),
        "--check",
    )
}

/** Tracked Unicode 17 inputs used for offline Bidi table drift verification. */
val unicodeBidiResourceDirectory = layout.projectDirectory.dir("src/test/resources/unicode/17.0.0")

/** Fails when fixed Unicode 17 Bidi source or conformance resources differ from generated bytes. */
val checkUnicodeBidiDataGeneration by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies Unicode 17 Bidi tables and corpora from pinned tracked inputs."
    workingDir(rootProject.projectDir)
    inputs.file(rootProject.file("tools/generate_unicode_bidi_data.py"))
    inputs.files(
        unicodeBidiResourceDirectory.file("DerivedBidiClass.txt"),
        unicodeBidiResourceDirectory.file("BidiBrackets.txt"),
        unicodeBidiResourceDirectory.file("BidiMirroring.txt"),
        unicodeBidiResourceDirectory.file("BidiTest.txt"),
        unicodeBidiResourceDirectory.file("BidiCharacterTest.txt"),
    )
    inputs.file(
        layout.projectDirectory.file(
            "src/main/kotlin/com/purride/pixelui/internal/text/bidi/UnicodeBidiData.kt",
        ),
    )
    commandLine(
        "python3",
        rootProject.file("tools/generate_unicode_bidi_data.py"),
        "--input-dir",
        unicodeBidiResourceDirectory.asFile,
        "--check",
    )
}

/** Rejects component or foundation theme tokens that are declared but unused by production runtime code. */
val checkThemeTokenCoverage by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks every standard component and foundation theme token has a runtime consumer."
    workingDir(rootProject.projectDir)

    inputs.file(themeTokenCoverageTool)
        .withPropertyName("themeTokenCoverageTool")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(themeComponentTokenDefinition)
        .withPropertyName("themeComponentTokenDefinition")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(themeTokenRuntimeSourceRoot)
        .withPropertyName("themeTokenRuntimeSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(themeTokenCoverageReport)

    commandLine(
        "python3",
        themeTokenCoverageTool.asFile,
        "--tokens",
        themeComponentTokenDefinition.asFile,
        "--source-root",
        themeTokenRuntimeSourceRoot.asFile,
        "--report",
        themeTokenCoverageReport.get().asFile,
    )
}

val testPixelGlyphPackConverter by tasks.registering {
    group = "verification"
    description = "Compatibility alias for the historical glyph converter test task."
    dependsOn(testPixelTooling)
}

val dumpPublicApi by tasks.registering {
    group = "verification"
    description = "Writes a deterministic text dump of pixel-engine public Kotlin declarations."

    val sourceFiles = fileTree("src/main/kotlin") {
        include("**/*.kt")
    }
    inputs.files(sourceFiles)
    outputs.file(layout.buildDirectory.file("reports/api/pixel-engine.api"))

    doLast {
        val declarations = sourceFiles.files
            .sortedBy { it.relativeTo(projectDir).path }
            .flatMap { file ->
                /** 当前源码文件声明的 Kotlin package。 */
                var currentPackage = ""
                /** 当前文件的简单花括号深度，用于跳过整个内部 artifact 容器。 */
                var braceDepth = 0
                /** 正在跳过的内部容器正文深度；null 表示当前不在该容器中。 */
                var hiddenBlockDepth: Int? = null
                /** 标记已识别内部容器声明、但其左花括号仍在后续行。 */
                var hiddenContainerPending = false
                /** 标记下一个 public 声明带有内部 artifact 注解。 */
                var hideNextDeclaration = false
                /** public class/interface/object 声明的轻量识别式。 */
                val publicContainerPattern = Regex(
                    "^public\\s+(?:(?:data|enum|sealed|abstract|annotation|value)\\s+)?" +
                        "(?:class|interface|object)\\b",
                )

                buildList {
                    file.readLines().forEach { raw ->
                        /** 当前行去除首尾空白后的声明文本。 */
                        val line = raw.trim()
                        /** 当前行打开的花括号数量。 */
                        val openingBraces = raw.count { character -> character == '{' }
                        /** 当前行关闭的花括号数量。 */
                        val closingBraces = raw.count { character -> character == '}' }

                        if (hiddenBlockDepth != null) {
                            braceDepth += openingBraces - closingBraces
                            if (braceDepth < hiddenBlockDepth!!) hiddenBlockDepth = null
                            return@forEach
                        }

                        if (hiddenContainerPending) {
                            braceDepth += openingBraces - closingBraces
                            if (openingBraces > 0) {
                                hiddenBlockDepth = braceDepth
                                hiddenContainerPending = false
                            } else if (line == ")" || line.endsWith(";")) {
                                hiddenContainerPending = false
                            }
                            return@forEach
                        }

                        when {
                            line.startsWith("package ") -> {
                                currentPackage = line.removePrefix("package ").trim()
                            }
                            line.startsWith("@PixelArtifactInternalApi") -> {
                                hideNextDeclaration = true
                            }
                            hideNextDeclaration && line.startsWith("public ") -> {
                                hideNextDeclaration = false
                                if (publicContainerPattern.containsMatchIn(line)) {
                                    if (openingBraces > 0) {
                                        braceDepth += openingBraces - closingBraces
                                        hiddenBlockDepth = braceDepth
                                    } else {
                                        hiddenContainerPending = true
                                    }
                                    return@forEach
                                }
                            }
                            line.startsWith("public ") && !currentPackage.contains(".internal") -> {
                                add("$currentPackage ${line.normalizePublicApiLine()}")
                            }
                        }
                        braceDepth += openingBraces - closingBraces
                    }
                }
            }
            /** 文件物理迁移不得制造虚假的 API diff；声明文本全局排序后再冻结。 */
            .sorted()
        val output = buildString {
            appendLine("# pixel-engine public API baseline")
            declarations.forEach { appendLine(it) }
        }
        val report = layout.buildDirectory.file("reports/api/pixel-engine.api").get().asFile
        report.parentFile.mkdirs()
        report.writeText(output)
    }
}

tasks.register("checkPublicApi") {
    group = "verification"
    description = "Checks the tracked pixel-engine public API baseline."
    dependsOn(dumpPublicApi)

    inputs.file(publicApiBaseline)
    inputs.file(layout.buildDirectory.file("reports/api/pixel-engine.api"))

    doLast {
        val baselineFile = publicApiBaseline.asFile
        val actualFile = layout.buildDirectory.file("reports/api/pixel-engine.api").get().asFile
        if (!baselineFile.exists()) {
            throw GradleException("Missing API baseline: ${baselineFile.path}. Run :pixel-engine:dumpPublicApi and review the report.")
        }
        val expected = baselineFile.readText()
        val actual = actualFile.readText()
        if (expected != actual) {
            throw GradleException(
                "pixel-engine public API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 将人工审阅后的轻量公开声明报告冻结为当前 baseline。 */
val updatePublicApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(dumpPublicApi)
    doLast {
        /** 当前生成且已完成评审的轻量 API 报告。 */
        val generated = layout.buildDirectory.file("reports/api/pixel-engine.api").get().asFile
        /** 纳入源码控制的轻量 API baseline。 */
        val baseline = publicApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

val releaseRuntimeClasses = layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-core 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelCoreReleaseRuntimeClasses = project(":pixel-core").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-runtime 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelRuntimeReleaseRuntimeClasses = project(":pixel-runtime").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-widgets 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelWidgetsReleaseRuntimeClasses = project(":pixel-widgets").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-navigation 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelNavigationReleaseRuntimeClasses = project(":pixel-navigation").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-android 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelAndroidReleaseRuntimeClasses = project(":pixel-android").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-testing 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelTestingReleaseRuntimeClasses = project(":pixel-testing").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

/** 独立 pixel-debug 的 release class 目录，参与旧聚合坐标的 JVM ABI 并集。 */
val pixelDebugReleaseRuntimeClasses = project(":pixel-debug").layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

val dumpBinaryApi by tasks.registering {
    group = "verification"
    description = "Writes a deterministic javap dump of pixel-engine release binary API."
    dependsOn("bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-core:bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-runtime:bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-widgets:bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-navigation:bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-android:bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-testing:bundleLibRuntimeToDirRelease")
    dependsOn(":pixel-debug:bundleLibRuntimeToDirRelease")
    dependsOn(generateMetalavaApi)

    inputs.dir(releaseRuntimeClasses)
    inputs.dir(pixelCoreReleaseRuntimeClasses)
    inputs.dir(pixelRuntimeReleaseRuntimeClasses)
    inputs.dir(pixelWidgetsReleaseRuntimeClasses)
    inputs.dir(pixelNavigationReleaseRuntimeClasses)
    inputs.dir(pixelAndroidReleaseRuntimeClasses)
    inputs.dir(pixelTestingReleaseRuntimeClasses)
    inputs.dir(pixelDebugReleaseRuntimeClasses)
    inputs.file(generatedMetalavaApi)
    outputs.file(layout.buildDirectory.file("reports/api/pixel-engine.binary-api"))

    doLast {
        /** 聚合实现及三个已拆独立 artifact 的 release class 目录。 */
        val classDirectories = listOf(
            releaseRuntimeClasses.get().asFile,
            pixelCoreReleaseRuntimeClasses.get().asFile,
            pixelRuntimeReleaseRuntimeClasses.get().asFile,
            pixelWidgetsReleaseRuntimeClasses.get().asFile,
            pixelNavigationReleaseRuntimeClasses.get().asFile,
            pixelAndroidReleaseRuntimeClasses.get().asFile,
            pixelTestingReleaseRuntimeClasses.get().asFile,
            pixelDebugReleaseRuntimeClasses.get().asFile,
        )
        classDirectories.forEach { classDirectory ->
            if (!classDirectory.exists()) {
                throw GradleException("Missing release runtime classes at ${classDirectory.path}")
            }
        }
        /** Metadata-aware public class set emitted by the official source API tool. */
        val publishedClasses = generatedMetalavaApi.get().asFile.readMetalavaBinaryClassNames()
        /** JDK javap executable used to retain JVM synthetic/default-method ABI coverage. */
        val javap = File(System.getProperty("java.home"), "bin/javap").absolutePath
        /** Stable JVM classes plus DefaultImpls generated for stable public interfaces. */
        val classNames = classDirectories
            .asSequence()
            .flatMap { classDirectory ->
                classDirectory.walkTopDown()
                    .filter { file -> file.isFile && file.extension == "class" }
                    .map { file ->
                        file.relativeTo(classDirectory).invariantSeparatorsPath.removeSuffix(".class")
                    }
            }
            .filter { name -> name.isPublishedBinaryArtifactCandidate() }
            .filter { name ->
                name in publishedClasses ||
                    name.endsWith("\$DefaultImpls") && name.substringBeforeLast('$') in publishedClasses
            }
            .map { name -> name.replace('/', '.') }
            .distinct()
            .sorted()
            .toList()

        /** javap 同时解析聚合及已拆 artifact class，保留所有跨 artifact 参数类型。 */
        val aggregateClasspath = classDirectories.joinToString(File.pathSeparator, transform = File::getAbsolutePath)

        val output = buildString {
            appendLine("# pixel-engine binary API baseline")
            classNames.forEach { className ->
                /** 当前 JVM 类在三个 artifact class 目录中的实际文件。 */
                val classFile = classDirectories
                    .asSequence()
                    .map { classDirectory -> File(classDirectory, className.replace('.', '/') + ".class") }
                    .firstOrNull(File::isFile)
                    ?: throw GradleException("Missing class file for published API class $className")
                /** 当前类是否引用了 artifact 内部 API 标记。 */
                val containsInternalArtifactApi = classFile.readBytes()
                    .toString(Charsets.ISO_8859_1)
                    .contains("PixelArtifactInternalApi")
                /** 由 classfile 注解证明不属于稳定 ABI 的 JVM member 名称。 */
                val hiddenArtifactMemberNames = if (containsInternalArtifactApi) {
                    /** javap verbose 输出只在含内部标记的少量类上执行。 */
                    val verboseResult = providers.exec {
                        commandLine(javap, "-classpath", aggregateClasspath, "-public", "-v", className)
                    }
                    verboseResult.standardOutput.asText.get().pixelArtifactInternalBinaryMemberNames()
                } else {
                    emptySet()
                }
                /** 常规 javap 输出保留可读且稳定的 ABI 文本格式。 */
                val result = providers.exec {
                    commandLine(javap, "-classpath", aggregateClasspath, "-public", className)
                }
                result.standardOutput.asText.get()
                    .normalizeBinaryApiDump(hiddenArtifactMemberNames)
                    .takeIf { dump -> dump.isPublicBinaryApiDump() }
                    ?.let { dump -> appendLine(dump) }
            }
        }
        val report = layout.buildDirectory.file("reports/api/pixel-engine.binary-api").get().asFile
        report.parentFile.mkdirs()
        report.writeText(output)
    }
}

tasks.register("checkBinaryApi") {
    group = "verification"
    description = "Checks the tracked pixel-engine binary API baseline."
    dependsOn(dumpBinaryApi)

    inputs.file(binaryApiBaseline)
    inputs.file(layout.buildDirectory.file("reports/api/pixel-engine.binary-api"))

    doLast {
        val baselineFile = binaryApiBaseline.asFile
        val actualFile = layout.buildDirectory.file("reports/api/pixel-engine.binary-api").get().asFile
        if (!baselineFile.exists()) {
            throw GradleException("Missing binary API baseline: ${baselineFile.path}. Run :pixel-engine:dumpBinaryApi and review the report.")
        }
        val expected = baselineFile.readText()
        val actual = actualFile.readText()
        if (expected != actual) {
            throw GradleException(
                "pixel-engine binary API changed. Review ${actualFile.path} and update ${baselineFile.path} intentionally.",
            )
        }
    }
}

/** 将人工审阅后的聚合 javap 输出冻结为当前二进制 baseline。 */
val updateBinaryApiBaseline by tasks.registering {
    group = "verification"
    dependsOn(dumpBinaryApi)
    doLast {
        /** 当前生成且已完成评审的聚合二进制 API。 */
        val generated = layout.buildDirectory.file("reports/api/pixel-engine.binary-api").get().asFile
        /** 纳入源码控制的聚合二进制 baseline。 */
        val baseline = binaryApiBaseline.asFile
        baseline.parentFile.mkdirs()
        generated.copyTo(baseline, overwrite = true)
    }
}

/** 使用可测试的词法扫描器校验完整 public/protected KDoc，而不是依赖单行正则。 */
val checkKdocCoverage by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks every explicit public/protected Kotlin declaration has valid KDoc."

    /** 共享源码根仍包含拆分后九个 artifact 的完整公开源码并集。 */
    val sourceFiles = fileTree(sharedProductionSourceRoot) {
        include("**/*.kt")
    }
    /** 机器可读报告会列出全部缺失和无效声明，不能只保留样例。 */
    val reportFile = layout.buildDirectory.file("reports/kdoc/kdoc-coverage.json")
    inputs.files(sourceFiles)
    inputs.file(rootProject.file("tools/check_kdoc_coverage.py"))
    outputs.file(reportFile)
    workingDir(rootProject.projectDir)
    commandLine(
        "python3",
        rootProject.file("tools/check_kdoc_coverage.py"),
        "--source",
        sharedProductionSourceRoot,
        "--report",
        reportFile.get().asFile,
        "--minimum-percent",
        kdocCoverageMinimumPercent.toString(),
    )
}

/** 把 Gradle 实际解析出的 Release 运行时 artifact 固定为排序、去重的坐标清单。 */
val writeReleaseRuntimeDependencyManifest by tasks.registering {
    group = "verification"
    description = "Writes the exact resolved Release runtime dependency coordinates."

    /** 受检的 Release 运行时 classpath。 */
    val releaseRuntimeClasspath = configurations.named("releaseRuntimeClasspath")
    /**
     * 只读取组件图坐标，不强制 Gradle 9 在 AAR 的 classes/resources 等次级 artifact 间做无关选择。
     */
    val resolvedCoordinateProvider = providers.provider {
        /** 组件版本索引用于把实际入选 artifact 映射回 Maven identity。 */
        val incoming = releaseRuntimeClasspath.get().incoming
        /** 只保留真正进入运行时文件集合的 artifact，排除无文件 BOM/metadata 节点。 */
        val componentVersions = incoming.resolutionResult.allComponents.associate { component ->
            component.id to component.moduleVersion
        }
        /** 明确选择 Android runtime classes，消除 AGP 9 次级 artifact 歧义。 */
        val runtimeArtifacts = incoming.artifactView {
            attributes.attribute(
                org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                "android-classes-jar",
            )
        }.artifacts.artifacts
        runtimeArtifacts
            .mapNotNull { artifact ->
                /** 当前实际 artifact 对应的 group:name:version。 */
                val moduleVersion = componentVersions[artifact.id.componentIdentifier] ?: return@mapNotNull null
                "${moduleVersion.group}:${moduleVersion.name}:${moduleVersion.version}"
            }
            .distinct()
            .sorted()
    }
    inputs.property("releaseRuntimeCoordinates", resolvedCoordinateProvider)
    outputs.file(releaseRuntimeDependencyManifest)

    doLast {
        /** 去除重复组件后的 group:name:version 坐标。 */
        val coordinates = resolvedCoordinateProvider.get()
        /** 最终清单路径，使用单一尾换行保持跨环境稳定。 */
        val manifestFile = releaseRuntimeDependencyManifest.get().asFile
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(coordinates.joinToString(separator = "\n", postfix = "\n"))
    }
}

/** 对最终 AAR、class/method 数量、发布 POM 和完整运行时依赖图执行 M6-3 预算门禁。 */
val checkReleaseArtifactBudget by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails when the Release SDK exceeds its reviewed artifact or dependency budget."
    dependsOn("assembleRelease", "generatePomFileForReleasePublication")
    dependsOn(":pixel-core:assembleRelease")
    dependsOn(":pixel-runtime:assembleRelease")
    dependsOn(":pixel-widgets:assembleRelease")
    dependsOn(":pixel-navigation:assembleRelease")
    dependsOn(":pixel-android:assembleRelease")
    dependsOn(":pixel-testing:assembleRelease")
    dependsOn(":pixel-debug:assembleRelease")
    dependsOn(writeReleaseRuntimeDependencyManifest)
    workingDir(rootProject.projectDir)

    inputs.file(releaseArtifactAar)
    inputs.file(pixelCoreReleaseArtifactAar)
    inputs.file(pixelRuntimeReleaseArtifactAar)
    inputs.file(pixelWidgetsReleaseArtifactAar)
    inputs.file(pixelNavigationReleaseArtifactAar)
    inputs.file(pixelAndroidReleaseArtifactAar)
    inputs.file(pixelTestingReleaseArtifactAar)
    inputs.file(pixelDebugReleaseArtifactAar)
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
        "--dependency-aar",
        pixelCoreReleaseArtifactAar.get().asFile,
        "--dependency-aar",
        pixelRuntimeReleaseArtifactAar.get().asFile,
        "--dependency-aar",
        pixelWidgetsReleaseArtifactAar.get().asFile,
        "--dependency-aar",
        pixelNavigationReleaseArtifactAar.get().asFile,
        "--dependency-aar",
        pixelAndroidReleaseArtifactAar.get().asFile,
        "--dependency-aar",
        pixelTestingReleaseArtifactAar.get().asFile,
        "--dependency-aar",
        pixelDebugReleaseArtifactAar.get().asFile,
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
    dependsOn("checkPublicApi")
    dependsOn("checkBinaryApi")
    dependsOn(checkMetalavaApi)
    dependsOn(checkMetalavaReleasedCompatibility)
    dependsOn(checkStableApiBoundary)
    dependsOn(checkArtifactBoundaries)
    dependsOn(checkKdocCoverage)
    dependsOn(checkThemeTokenCoverage)
    dependsOn(checkUnicodeGraphemeDataGeneration)
    dependsOn(checkUnicodeBidiDataGeneration)
    dependsOn(checkReleaseArtifactBudget)
    dependsOn(testPixelTooling)
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
            artifactId = "pixel-engine"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("pixel-engine")
                description.set("Pixel UI engine for Android-hosted pixel-grid interfaces.")
            }
        }
    }
}

/**
 * Copies release sources into a package-aligned build directory that Metalava can parse reliably.
 *
 * The repository groups files by feature below their declared package. Kotlin accepts that layout,
 * while Metalava validates package-to-path alignment, so the verification task creates a disposable
 * canonical view without changing production sources.
 *
 * @param sourceFiles original release Kotlin and Java source files.
 * @param destinationRoot disposable package-aligned output directory.
 * @return staged files sorted by their canonical relative paths.
 */
fun stageMetalavaSources(sourceFiles: List<File>, destinationRoot: File): List<File> {
    if (destinationRoot.exists() && !destinationRoot.deleteRecursively()) {
        throw GradleException("Unable to clear stale Metalava source staging directory: ${destinationRoot.path}")
    }
    if (!destinationRoot.mkdirs() && !destinationRoot.isDirectory) {
        throw GradleException("Unable to create Metalava source staging directory: ${destinationRoot.path}")
    }

    /** Kotlin/Java package declaration captured without relying on repository folder layout. */
    val packagePattern = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\b")
    /** Destination ownership map used to reject ambiguous same-package filename collisions. */
    val destinationOwners = mutableMapOf<File, File>()
    /** Canonical staged source list returned to the @file manifest writer. */
    val stagedFiles = mutableListOf<File>()
    sourceFiles.forEach { sourceFile ->
        val packageName = packagePattern.find(sourceFile.readText())?.groupValues?.get(1)
            ?: throw GradleException("Missing package declaration in Metalava source: ${sourceFile.path}")
        val packageDirectory = File(destinationRoot, packageName.replace('.', File.separatorChar))
        val destination = File(packageDirectory, sourceFile.name)
        val existingOwner = destinationOwners.putIfAbsent(destination, sourceFile)
        if (existingOwner != null) {
            throw GradleException(
                "Metalava staging collision for ${destination.path}: ${existingOwner.path} and ${sourceFile.path}",
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

/**
 * Builds one deterministic Metalava invocation for either signature generation or release compatibility.
 *
 * @param sourceManifest newline-delimited absolute source paths passed through Metalava's @file syntax.
 * @param sourceRoot package-aligned source root passed to Metalava for package discovery.
 * @param apiClasspath release compile dependencies and the Android boot classpath.
 * @param manifest library manifest used to resolve Android API metadata.
 * @param apiOutput optional generated current signature destination.
 * @param releasedApi optional previously released signature used for compatibility checking.
 */
fun buildMetalavaArguments(
    sourceManifest: File,
    sourceRoot: File,
    apiClasspath: Set<File>,
    manifest: File,
    apiOutput: File? = null,
    releasedApi: File? = null,
): List<String> {
    require(apiOutput != null || releasedApi != null) {
        "Metalava requires either an API output or a released compatibility baseline."
    }

    /** Stable classpath value independent of dependency resolution iteration order. */
    val classpath = apiClasspath
        .filter(File::exists)
        .sortedBy(File::getAbsolutePath)
        .joinToString(File.pathSeparator, transform = File::getAbsolutePath)
    /** Shared arguments keep generation and compatibility checks on exactly the same API surface. */
    val arguments = mutableListOf(
        "--no-color",
        "--quiet",
        "main",
        "--source-path",
        sourceRoot.absolutePath,
        "--source-files",
        "@${sourceManifest.absolutePath}",
        "--classpath",
        classpath,
        "--manifest",
        manifest.absolutePath,
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
        "com.purride.*${File.pathSeparator}-com.purride.pixelcore.internal.*" +
            "${File.pathSeparator}-com.purride.pixelui.internal.*",
        "--format",
        "v4",
    )
    if (apiOutput != null) {
        arguments += listOf("--api", apiOutput.absolutePath)
    }
    if (releasedApi != null) {
        arguments += listOf(
            "--check-compatibility:api:released",
            releasedApi.absolutePath,
            "--error-message:compatibility:released",
            "pixel-engine changed incompatibly with its last released Metalava API signature.",
        )
    }
    return arguments
}

fun String.normalizePublicApiLine(): String {
    return replace(Regex("/\\*\\*.*?\\*/\\s*"), "")
        /* KDoc 属于文档而不是源码签名，主构造参数的行内注释不得制造 API diff。 */
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*\\{\\s*$"), "")
        .replace(Regex(" = (Internal\\w+|com\\.purride\\.pixelui\\.internal\\.\\w+)$"), "")
        .trim()
}

/** Returns true when this class file belongs to a package eligible for published ABI inspection. */
fun String.isPublishedBinaryArtifactCandidate(): Boolean {
    if (!startsWith("com/purride/pixelcore/") &&
        !startsWith("com/purride/pixelengine/") &&
        !startsWith("com/purride/pixelui/")
    ) {
        return false
    }
    if (contains("/internal/")) return false
    if (contains("PixelTester\$TestGestureTarget")) return false
    if (endsWith("/BuildConfig") || contains("/R$") || endsWith("/R")) return false
    if (substringAfterLast('/').contains("\$WhenMappings")) return false
    if (Regex("\\$\\d+").containsMatchIn(this)) return false
    return true
}

/**
 * Extracts JVM class paths for every class-like declaration in a Metalava v4 signature.
 *
 * Metalava reads Kotlin metadata, so this set excludes Kotlin `internal` classes even when their JVM
 * access flag is public. Nested source names are converted to the `$` separator used by class files.
 */
fun File.readMetalavaBinaryClassNames(): Set<String> {
    /** Current package block while traversing the deterministic signature file. */
    var currentPackage = ""
    /** Package declaration recognized at the start of each Metalava block. */
    val packagePattern = Regex("^package ([A-Za-z_][A-Za-z0-9_.]*) \\{$")
    /** Class, value class, enum, annotation, or interface name on a declaration line. */
    val classPattern = Regex(
        "\\b(?:class|interface|enum|@interface)\\s+([A-Za-z_][A-Za-z0-9_.]*(?:<[^>]+>)?)",
    )
    /** Published class paths returned to the binary ABI dumper. */
    val classes = linkedSetOf<String>()
    forEachLine { line ->
        packagePattern.matchEntire(line)?.let { match ->
            currentPackage = match.groupValues[1]
            return@forEachLine
        }
        if (currentPackage.isEmpty() || !line.startsWith("  ")) {
            return@forEachLine
        }
        /** Source-level class name without generic type parameters. */
        val sourceName = classPattern.find(line)?.groupValues?.get(1)?.substringBefore('<')
            ?: return@forEachLine
        classes += currentPackage.replace('.', '/') + "/" + sourceName.replace('.', '$')
    }
    return classes
}

/**
 * 从 javap verbose 输出提取带 `PixelArtifactInternalApi` 的 JVM member 名称。
 *
 * Kotlin property 注解落在 `getX$annotations` 合成方法上，因此同时排除同名 getter 和
 * 可能存在的 setter；这让二进制门禁与 Metalava 的隐藏注解语义保持一致。
 */
fun String.pixelArtifactInternalBinaryMemberNames(): Set<String> {
    /** javap 当前正在描述的 public member 头。 */
    var currentMemberHeader: String? = null
    /** 已由 classfile RuntimeInvisibleAnnotations 证明为内部契约的 JVM 名称。 */
    val hiddenNames = mutableSetOf<String>()
    lineSequence().forEach { rawLine ->
        /** 保留缩进用于区分 member 头与方法体细节。 */
        val trimmedLine = rawLine.trim()
        if (rawLine.startsWith("  public ") && trimmedLine.endsWith(';')) {
            currentMemberHeader = trimmedLine
        }
        if (trimmedLine == "com.purride.pixelui.internal.PixelArtifactInternalApi") {
            /** 当前注解所属 member 的 JVM 方法名。 */
            val annotatedName = currentMemberHeader?.binaryApiMemberName() ?: return@forEach
            hiddenNames += annotatedName
            if (annotatedName.endsWith("\$annotations")) {
                /** Kotlin property 注解合成方法去除固定后缀后的 getter 名。 */
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
fun String.binaryApiMemberName(): String? {
    /** 方法参数列表之前的返回类型与方法名部分。 */
    val prefix = substringBefore('(', missingDelimiterValue = "")
    return prefix.takeIf(String::isNotEmpty)?.substringAfterLast(' ')
}

/** 规范化 javap 输出，并排除明确标记为兄弟 artifact 内部契约的 member。 */
fun String.normalizeBinaryApiDump(hiddenArtifactMemberNames: Set<String> = emptySet()): String {
    return lineSequence()
        .map { line -> line.trimEnd() }
        .filterNot { line -> line.startsWith("Compiled from ") }
        .filterNot { line -> line.isBlank() }
        /** Kotlin internal 成员按 module 名改写后仍不是稳定 Java/Kotlin API。 */
        .filterNot { line -> Regex("\\\$pixel_[A-Za-z0-9_]+").containsMatchIn(line) }
        /** 拆分后 JVM public、但经注解声明不属于消费者稳定 ABI 的兄弟 artifact SPI。 */
        .filterNot { line -> line.trim().binaryApiMemberName() in hiddenArtifactMemberNames }
        .filterNot { line -> line.contains(" access\$") }
        .joinToString(separator = "\n")
}

fun String.isPublicBinaryApiDump(): Boolean {
    val lines = lineSequence().toList()
    val first = lines.firstOrNull() ?: return false
    /** 即使稳定公开类型没有成员，其 class/interface 的存在本身仍属于二进制 ABI。 */
    return first.startsWith("public ")
}
