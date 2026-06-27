import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.purride"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "com.purride.pixelengine"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
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
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}

val publicApiBaseline = layout.projectDirectory.file("api/pixel-engine.api")
val binaryApiBaseline = layout.projectDirectory.file("api/pixel-engine.binary-api")
val kdocCoverageMinimumPercent = 35.0

tasks.register<Exec>("generatePixelGlyphPacks") {
    group = "build"
    description = "Generates the built-in TTF glyph packs with the shared converter CLI."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/generate_pixel_glyph_packs.py"))
}

val testPixelGlyphPackConverter by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the BDF/TTF glyph pack converter unit tests."
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
                var currentPackage = ""
                file.readLines()
                    .mapNotNull { raw ->
                        val line = raw.trim()
                        when {
                            line.startsWith("package ") -> {
                                currentPackage = line.removePrefix("package ").trim()
                                null
                            }
                            line.startsWith("public ") -> "$currentPackage ${line.normalizePublicApiLine()}"
                            else -> null
                        }
                    }
            }
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

val releaseRuntimeClasses = layout.buildDirectory.dir(
    "intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease",
)

val dumpBinaryApi by tasks.registering {
    group = "verification"
    description = "Writes a deterministic javap dump of pixel-engine release binary API."
    dependsOn("bundleLibRuntimeToDirRelease")

    inputs.dir(releaseRuntimeClasses)
    outputs.file(layout.buildDirectory.file("reports/api/pixel-engine.binary-api"))

    doLast {
        val classDir = releaseRuntimeClasses.get().asFile
        if (!classDir.exists()) {
            throw GradleException("Missing release runtime classes at ${classDir.path}")
        }
        val javap = File(System.getProperty("java.home"), "bin/javap").absolutePath
        val classNames = classDir
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "class" }
            .map { file -> file.relativeTo(classDir).invariantSeparatorsPath.removeSuffix(".class") }
            .filter { name -> name.isPublishedBinaryApiClass() }
            .map { name -> name.replace('/', '.') }
            .sorted()
            .toList()

        val output = buildString {
            appendLine("# pixel-engine binary API baseline")
            classNames.forEach { className ->
                val result = providers.exec {
                    commandLine(javap, "-classpath", classDir.path, "-public", className)
                }
                result.standardOutput.asText.get()
                    .normalizeBinaryApiDump()
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

tasks.register("checkKdocCoverage") {
    group = "verification"
    description = "Checks public KDoc coverage does not regress below the tracked release floor."

    val sourceFiles = fileTree("src/main/kotlin") {
        include("**/*.kt")
    }
    inputs.files(sourceFiles)
    outputs.file(layout.buildDirectory.file("reports/kdoc/kdoc-coverage.txt"))

    doLast {
        val result = collectKdocCoverage(sourceFiles.files)
        val report = layout.buildDirectory.file("reports/kdoc/kdoc-coverage.txt").get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("publicDeclarations=${result.total}")
                appendLine("documentedDeclarations=${result.documented}")
                appendLine("coveragePercent=${"%.2f".format(result.percent)}")
                appendLine("minimumPercent=${"%.2f".format(kdocCoverageMinimumPercent)}")
                if (result.missing.isNotEmpty()) {
                    appendLine()
                    appendLine("missingKdocSample:")
                    result.missing.take(40).forEach { missing ->
                        appendLine("- $missing")
                    }
                }
            },
        )
        if (result.percent < kdocCoverageMinimumPercent) {
            throw GradleException(
                "pixel-engine public KDoc coverage ${"%.2f".format(result.percent)}% is below " +
                    "${"%.2f".format(kdocCoverageMinimumPercent)}%. See ${report.path}.",
            )
        }
    }
}

tasks.named("check") {
    dependsOn("checkPublicApi")
    dependsOn("checkBinaryApi")
    dependsOn("checkKdocCoverage")
    dependsOn(testPixelGlyphPackConverter)
}

publishing {
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
                url.set("https://github.com/purride/pixel-engine")
                licenses {
                    license {
                        name.set("Proprietary")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("purride")
                        name.set("Purride")
                    }
                }
            }
        }
    }
}

fun String.normalizePublicApiLine(): String {
    return replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*\\{\\s*$"), "")
        .trim()
}

fun String.isPublishedBinaryApiClass(): Boolean {
    if (!startsWith("com/purride/pixelcore/") &&
        !startsWith("com/purride/pixelengine/") &&
        !startsWith("com/purride/pixelui/")
    ) {
        return false
    }
    if (contains("/internal/")) return false
    if (endsWith("/BuildConfig") || contains("/R$") || endsWith("/R")) return false
    if (substringAfterLast('/').contains("\$WhenMappings")) return false
    if (Regex("\\$\\d+").containsMatchIn(this)) return false
    return true
}

fun String.normalizeBinaryApiDump(): String {
    return lineSequence()
        .map { line -> line.trimEnd() }
        .filterNot { line -> line.startsWith("Compiled from ") }
        .filterNot { line -> line.isBlank() }
        .filterNot { line -> line.contains("\$pixel_engine") }
        .filterNot { line -> line.contains(" access\$") }
        .joinToString(separator = "\n")
}

fun String.isPublicBinaryApiDump(): Boolean {
    val lines = lineSequence().toList()
    val first = lines.firstOrNull() ?: return false
    if (!first.startsWith("public ")) return false
    return lines.drop(1).any { line -> line.trimStart().startsWith("public ") }
}

data class KdocCoverageResult(
    val total: Int,
    val documented: Int,
    val missing: List<String>,
) {
    val percent: Double
        get() = if (total == 0) 100.0 else documented * 100.0 / total
}

fun collectKdocCoverage(files: Set<File>): KdocCoverageResult {
    var total = 0
    var documented = 0
    val missing = mutableListOf<String>()
    files.sortedBy { file -> file.relativeTo(projectDir).path }.forEach { file ->
        val lines = file.readLines()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isPublicKdocCandidate()) {
                total += 1
                if (lines.hasKdocBefore(index)) {
                    documented += 1
                } else {
                    missing += "${file.relativeTo(projectDir).path}:${index + 1}: $line"
                }
            }
        }
    }
    return KdocCoverageResult(total = total, documented = documented, missing = missing)
}

fun String.isPublicKdocCandidate(): Boolean {
    if (!startsWith("public ")) return false
    if (startsWith("public constructor")) return false
    return Regex("\\b(class|interface|object|fun|val|var|typealias)\\b").containsMatchIn(this)
}

fun List<String>.hasKdocBefore(index: Int): Boolean {
    var cursor = index - 1
    while (cursor >= 0 && this[cursor].isBlank()) cursor -= 1
    if (cursor < 0) return false
    val previous = this[cursor].trim()
    if (previous.startsWith("/**") && previous.endsWith("*/")) return true
    if (previous != "*/") return false
    cursor -= 1
    while (cursor >= 0) {
        val line = this[cursor].trim()
        if (line.startsWith("/**")) return true
        if (!line.startsWith("*")) return false
        cursor -= 1
    }
    return false
}
