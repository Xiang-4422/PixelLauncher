import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.purride"
version = "1.0.0"

/** Pixel Engine 单模块持有的完整 Kotlin 生产源码根。 */
val sharedProductionSourceRoot = file("src/main/kotlin")

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
    api(libs.androidx.lifecycle.runtime.ktx)
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

/** 单一发布坐标承诺的完整 Kotlin 与 Java API 源码集合。 */
val metalavaSourceDirectories = objects.fileCollection()

/** 全部 Kotlin 源码按原始路径扫描，避免目录整理制造虚假 API 差异。 */
val aggregateMetalavaKotlinSources = fileTree(sharedProductionSourceRoot) {
    include("**/*.kt", "**/*.java")
}

/** Unicode Bidi Java 参考实现也属于当前单一坐标的源码集合。 */
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

/** 当前 Debug AAR；desktop classes.jar 导出任务的输入（outputs/aar 是 AGP 的公开输出位置）。 */
val debugArtifactAar = layout.buildDirectory.file("outputs/aar/pixel-engine-debug.aar")

/**
 * 把 debug AAR 内的 classes.jar 解包到本模块自有的稳定输出路径
 * `build/outputs/desktop-classes/classes.jar`。
 *
 * showcase-desktop 只消费本任务的输出，不触碰 AGP intermediates 内部布局；
 * AGP 升级若改变 AAR 打包细节，本任务是唯一需要跟进的位置。
 */
val exportDebugClassesJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Exports the debug AAR classes.jar to a stable path for the desktop host."
    dependsOn("assembleDebug")
    from({ zipTree(debugArtifactAar) }) {
        include("classes.jar")
    }
    into(layout.buildDirectory.dir("outputs/desktop-classes"))
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

/** M6-3 固定的 Release AAR、class/method 与运行时依赖预算。 */
val releaseArtifactBudget = layout.projectDirectory.file("config/release-artifact-budget.json")

/** 独立解析 AAR classfile、POM 和解析后运行时依赖的预算检查器。 */
val releaseArtifactBudgetTool =
    rootProject.layout.projectDirectory.file("tools/check_pixel_artifact_budget.py")

/** 当前 Release AAR，由预算门禁直接检查最终发布字节。 */
val releaseArtifactAar = layout.buildDirectory.file("outputs/aar/pixel-engine-release.aar")

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

/** 工程模块契约、历史架构文本与生产 Kotlin 函数规模检查器。 */
val architectureGovernanceTool = rootProject.layout.projectDirectory.file("tools/check_pixel_architecture.py")

/** 生产 Kotlin 函数规模预算。 */
val architectureBudget = layout.projectDirectory.file("config/architecture-budget.json")

/** 架构治理检查输出的机器可读报告。 */
val architectureGovernanceReport =
    layout.buildDirectory.file("reports/architecture/architecture-governance.json")

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

/** 阻止超长生产函数、模块契约漂移及已删除模块重新进入治理文本。 */
val checkArchitectureGovernance by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks Pixel Engine function-size budgets, module contracts, and governance text."
    workingDir(rootProject.projectDir)

    inputs.file(architectureGovernanceTool)
        .withPropertyName("architectureGovernanceTool")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(architectureBudget)
        .withPropertyName("architectureBudget")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(sharedProductionSourceRoot)
        .withPropertyName("productionKotlinSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        rootProject.file("build.gradle.kts"),
        rootProject.file("settings.gradle.kts"),
        rootProject.file("README.md"),
        rootProject.file("mkdocs.yml"),
        rootProject.fileTree(".github/workflows") { include("**/*.yml", "**/*.yaml") },
        rootProject.fileTree("docs") { include("**/*.md") },
        rootProject.fileTree("tools") {
            include("**/*.py", "**/*.sh")
            exclude("**/__pycache__/**")
        },
        layout.projectDirectory.file("README.md"),
        layout.projectDirectory.file("build.gradle.kts"),
        layout.projectDirectory.file("consumer-rules.pro"),
        layout.projectDirectory.dir("docs"),
    ).withPropertyName("architectureGovernanceText")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(architectureGovernanceReport)

    commandLine(
        "python3",
        architectureGovernanceTool.asFile,
        "--root",
        rootProject.projectDir,
        "--budget",
        architectureBudget.asFile,
        "--report",
        architectureGovernanceReport.get().asFile,
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

val releaseRuntimeClasses = layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

val dumpBinaryApi by tasks.registering {
    group = "verification"
    description = "Writes a deterministic javap dump of pixel-engine release binary API."
    dependsOn("bundleLibRuntimeToDirRelease")
    dependsOn(generateMetalavaApi)

    inputs.dir(releaseRuntimeClasses)
    inputs.file(generatedMetalavaApi)
    outputs.file(layout.buildDirectory.file("reports/api/pixel-engine.binary-api"))

    doLast {
        /** 单模块 Release 变体产生的完整运行时 class 目录。 */
        val classDirectories = listOf(releaseRuntimeClasses.get().asFile)
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

        /** javap 通过单一 classpath 解析完整 Pixel Engine API。 */
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

/**
 * 一条命令更新全部 API 基线（Metalava 源码签名 + javap 二进制 ABI）。
 *
 * 只降低操作成本，不改变门禁语义：check 仍逐字节比对已提交的基线文件，
 * 生成结果必须经人工评审并提交才会生效；本任务不会也不得接入 check。
 */
tasks.register("updateApiBaselines") {
    group = "verification"
    description = "Regenerates both reviewed API baselines (Metalava + binary) in one command."
    dependsOn(updateMetalavaApiBaseline)
    dependsOn(updateBinaryApiBaseline)
}

/** 使用可测试的词法扫描器校验完整 public/protected KDoc，而不是依赖单行正则。 */
val checkKdocCoverage by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks every explicit public/protected Kotlin declaration has valid KDoc."

    /** 单模块源码根包含 Pixel Engine 的完整公开源码集合。 */
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
    dependsOn("checkBinaryApi")
    dependsOn(checkMetalavaApi)
    dependsOn(checkStableApiBoundary)
    dependsOn(checkKdocCoverage)
    dependsOn(checkThemeTokenCoverage)
    dependsOn(checkUnicodeGraphemeDataGeneration)
    dependsOn(checkUnicodeBidiDataGeneration)
    dependsOn(checkReleaseArtifactBudget)
    dependsOn(testPixelTooling)
    dependsOn(checkArchitectureGovernance)
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

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("releaseRuntimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
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
 * Builds one deterministic Metalava invocation that generates the current API signature.
 *
 * @param sourceManifest newline-delimited absolute source paths passed through Metalava's @file syntax.
 * @param sourceRoot package-aligned source root passed to Metalava for package discovery.
 * @param apiClasspath release compile dependencies and the Android boot classpath.
 * @param manifest library manifest used to resolve Android API metadata.
 * @param apiOutput generated current signature destination.
 */
fun buildMetalavaArguments(
    sourceManifest: File,
    sourceRoot: File,
    apiClasspath: Set<File>,
    manifest: File,
    apiOutput: File,
): List<String> {
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
    arguments += listOf("--api", apiOutput.absolutePath)
    return arguments
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

/** 规范化 javap 输出，并排除明确标记为发布物内部契约的 member。 */
fun String.normalizeBinaryApiDump(hiddenArtifactMemberNames: Set<String> = emptySet()): String {
    return lineSequence()
        .map { line -> line.trimEnd() }
        .filterNot { line -> line.startsWith("Compiled from ") }
        .filterNot { line -> line.isBlank() }
        /** Kotlin internal 成员按 module 名改写后仍不是稳定 Java/Kotlin API。 */
        .filterNot { line -> Regex("\\\$pixel_[A-Za-z0-9_]+").containsMatchIn(line) }
        /** JVM public、但经注解声明不属于消费者稳定 ABI 的发布物内部 SPI。 */
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
