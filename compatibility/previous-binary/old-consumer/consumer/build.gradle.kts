import java.security.MessageDigest

plugins {
    id("com.android.library")
}

android {
    namespace = "com.purride.pixelcompat.legacy"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
    }
}

/** Frozen engine AAR used exclusively to compile the legacy consumer binary. */
val baselineEngineAar = providers.gradleProperty("baselineEngineAar")
    .map(::file)
    .orNull
    ?: error("Missing -PbaselineEngineAar=<absolute frozen AAR path>.")

/** Reviewed SHA-256 file stored beside the frozen engine AAR. */
val baselineHashFile = providers.gradleProperty("baselineHashFile")
    .map(::file)
    .orNull
    ?: error("Missing -PbaselineHashFile=<absolute SHA-256 path>.")

dependencies {
    compileOnly(files(baselineEngineAar))
}

/** Machine-readable proof that only the frozen AAR supplied the engine compile API. */
val provenanceReport = providers.gradleProperty("oldConsumerProvenanceReport")
    .map(::file)
    .orElse(
        rootProject.layout.buildDirectory
            .file("reports/compatibility/old-consumer-provenance.json")
            .map { report -> report.asFile },
    )

/** Verifies the old consumer compile classpath and immutable baseline artifact hash. */
val verifyOldConsumerCompileClasspath by tasks.registering {
    group = "verification"
    description = "Verifies the old consumer compiles against only the reviewed frozen engine AAR."

    inputs.file(baselineEngineAar)
    inputs.file(baselineHashFile)
    outputs.file(provenanceReport)

    doLast {
        /** Reviewed digest parsed from the adjacent checksum file. */
        val expectedHash = baselineHashFile.readText().trim().substringBefore(' ')
        /** Digest recomputed from the exact baseline bytes used by Gradle. */
        val actualHash = baselineEngineAar.sha256()
        if (actualHash != expectedHash) {
            throw GradleException("Frozen engine AAR hash mismatch: expected $expectedHash, got $actualHash.")
        }
        /** Canonical compile classpath used by the debug variant. */
        val compileFiles = configurations.getByName("debugCompileClasspath")
            .files
            .map(File::getCanonicalFile)
        /** Canonical frozen artifact path required on that classpath. */
        val canonicalBaseline = baselineEngineAar.canonicalFile
        if (canonicalBaseline !in compileFiles) {
            throw GradleException("Frozen engine AAR is absent from the old consumer compile classpath.")
        }
        /** Unexpected engine artifacts would make the claimed baseline ambiguous. */
        val competingEngineAars = compileFiles.filter { file ->
            file.extension == "aar" && file.name.contains("pixel-engine") && file != canonicalBaseline
        }
        if (competingEngineAars.isNotEmpty()) {
            throw GradleException("Unexpected engine AARs on old compile classpath: $competingEngineAars")
        }
        /** Persistent provenance report used by the aggregate compatibility gate. */
        val reportFile = provenanceReport.get()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            """{"status":"passed","baselineSha256":"$actualHash","engineAarCount":1,"compileClasspathEntries":${compileFiles.size}}\n""",
        )
    }
}

tasks.configureEach {
    if (name == "assembleRelease") {
        dependsOn(verifyOldConsumerCompileClasspath)
    }
}

/** Computes the lower-case SHA-256 digest for this file. */
fun File.sha256(): String {
    /** SHA-256 digest implementation provided by the JDK. */
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        /** Reusable read buffer avoiding a full artifact copy in memory. */
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            /** Number of bytes read during this iteration. */
            val count = input.read(buffer)
            if (count < 0) {
                break
            }
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
