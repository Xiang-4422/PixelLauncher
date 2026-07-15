plugins {
    id("com.android.library")
}

android {
    namespace = "com.purride.pixelcompat.external"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
    }
}

/** Exact engine version published into the isolated compatibility repository. */
val pixelEngineVersion = providers.gradleProperty("pixelEngineVersion")
    .orElse("1.0.0")

dependencies {
    implementation("com.purride:pixel-engine:${pixelEngineVersion.get()}")
    testImplementation("junit:junit:4.13.2")
}

/** Byte sequence that must never occur in an external consumer compiled against the stable SPI. */
val forbiddenInternalReference = "com/purride/pixelui/internal/".toByteArray(Charsets.UTF_8)

/** Machine-readable evidence produced after scanning the external consumer's compiled classes. */
val bytecodeReport = providers.gradleProperty("pixelCompatibilityReport")
    .map(::file)
    .orElse(
        rootProject.layout.buildDirectory
            .file("reports/compatibility/external-spi-bytecode.json")
            .map { report -> report.asFile },
    )

/**
 * Checks constant-pool and descriptor bytes for accidental references to pixel-engine internals.
 *
 * A JVM class file stores referenced internal names as UTF-8 constants, so an exact byte scan covers
 * parent classes, interfaces, fields, methods, annotations, and generic signature attributes without
 * depending on the engine source tree.
 */
val checkNoInternalBytecodeReferences by tasks.registering {
    group = "verification"
    description = "Rejects compiled external SPI classes that reference pixel-engine internal packages."
    dependsOn("compileDebugKotlin")

    /** Compiled main classes discovered below the AGP build directory after compilation. */
    val compiledClasses = fileTree(
        layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
    ) {
        include("**/*.class")
        exclude("**/*Test*.class")
    }
    inputs.files(compiledClasses)
        .withPropertyName("externalConsumerClasses")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(bytecodeReport)

    doLast {
        /** Sorted class files make both failure messages and reports deterministic. */
        val classFiles = compiledClasses.files.sortedBy(File::getAbsolutePath)
        if (classFiles.isEmpty()) {
            throw GradleException("No external consumer class files were found after compileDebugKotlin.")
        }
        /** Relative paths of classes whose constant pools reference an internal engine package. */
        val violations = classFiles.filter { classFile ->
            classFile.readBytes().containsSubsequence(forbiddenInternalReference)
        }.map { classFile ->
            classFile.relativeTo(layout.buildDirectory.get().asFile).invariantSeparatorsPath
        }
        /** JSON-safe quoted violation list for the persistent verification report. */
        val violationJson = violations.joinToString(separator = ",") { path ->
            "\"${path.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        /** Persistent machine-readable report consumed by the repository release gate. */
        val reportFile = bytecodeReport.get()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            """{"status":"${if (violations.isEmpty()) "passed" else "failed"}","classesScanned":${classFiles.size},"violations":[$violationJson]}\n""",
        )
        if (violations.isNotEmpty()) {
            throw GradleException(
                "External SPI bytecode references pixel-engine internals: ${violations.joinToString()}. " +
                    "See ${reportFile.path}.",
            )
        }
    }
}

/** Returns true when this byte array contains [needle] as one contiguous sequence. */
fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
    if (needle.isEmpty()) {
        return true
    }
    if (needle.size > size) {
        return false
    }
    for (startIndex in 0..(size - needle.size)) {
        /** Whether every byte at this candidate offset still matches. */
        var matches = true
        for (needleIndex in needle.indices) {
            if (this[startIndex + needleIndex] != needle[needleIndex]) {
                matches = false
                break
            }
        }
        if (matches) {
            return true
        }
    }
    return false
}

tasks.named("check") {
    dependsOn(checkNoInternalBytecodeReferences)
}
