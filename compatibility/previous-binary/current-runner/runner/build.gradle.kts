import java.security.MessageDigest
import java.util.jar.JarInputStream
import java.util.zip.ZipFile

plugins {
    id("com.android.library")
}

android {
    namespace = "com.purride.pixelcompat.runner"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
    }
}

/** Current engine version resolved from the isolated Maven repository. */
val pixelEngineVersion = providers.gradleProperty("pixelEngineVersion").orElse("1.0.0")

/** Old consumer AAR compiled by the separate frozen-baseline build. */
val legacyConsumerAar = providers.gradleProperty("legacyConsumerAar")
    .map(::file)
    .orNull
    ?: error("Missing -PlegacyConsumerAar=<absolute old consumer AAR path>.")

/** Frozen old engine AAR that must never appear on this runner's runtime classpath. */
val baselineEngineAar = providers.gradleProperty("baselineEngineAar")
    .map(::file)
    .orNull
    ?: error("Missing -PbaselineEngineAar=<absolute frozen engine AAR path>.")

/** Current producer AAR whose digest must match the resolved Maven artifact. */
val currentEngineAar = providers.gradleProperty("currentEngineAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentEngineAar=<absolute current engine AAR path>.")

dependencies {
    implementation("com.purride:pixel-engine:${pixelEngineVersion.get()}")
    testImplementation(files(legacyConsumerAar))
    testImplementation("junit:junit:4.13.2")
}

/** Machine-readable proof of the runner's current-only engine classpath. */
val runtimeClasspathReport = providers.gradleProperty("runtimeClasspathReport")
    .map(::file)
    .orElse(
        rootProject.layout.buildDirectory
            .file("reports/compatibility/runtime-classpath.json")
            .map { report -> report.asFile },
    )

/** Verifies the old consumer is isolated from both embedded and runtime copies of the old engine. */
val verifyCurrentRunnerClasspath by tasks.registering {
    group = "verification"
    description = "Verifies the runner uses the old consumer binary with the current unified SDK AAR."

    inputs.file(legacyConsumerAar)
    inputs.file(baselineEngineAar)
    inputs.file(currentEngineAar)
    outputs.file(runtimeClasspathReport)

    doLast {
        /** SHA-256 of the immutable baseline that must be absent at runtime. */
        val baselineHash = baselineEngineAar.sha256()
        /** SHA-256 of the newly produced current engine artifact. */
        val expectedCurrentHash = currentEngineAar.sha256()
        if (baselineHash == expectedCurrentHash) {
            throw GradleException(
                "Current engine AAR is byte-identical to the baseline; the internal rename probe did not run.",
            )
        }
        /** Main debug runtime artifacts supplying the engine beneath the unit-test runner. */
        val runtimeArtifacts = configurations.getByName("debugRuntimeClasspath")
            .resolvedConfiguration
            .resolvedArtifacts
        /** Exactly one current com.purride:pixel-engine artifact is required. */
        val engineArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-engine"
        }
        if (engineArtifacts.size != 1) {
            throw GradleException("Expected one current engine runtime artifact, found ${engineArtifacts.size}.")
        }
        /** Digest of the current engine artifact resolved through Maven metadata. */
        val resolvedCurrentHash = engineArtifacts.single().file.sha256()
        if (resolvedCurrentHash != expectedCurrentHash) {
            throw GradleException(
                "Resolved current engine hash $resolvedCurrentHash does not match producer $expectedCurrentHash.",
            )
        }
        if (runtimeArtifacts.any { artifact -> artifact.file.sha256() == baselineHash }) {
            throw GradleException("Frozen old engine AAR leaked onto the current runner runtime classpath.")
        }
        legacyConsumerAar.assertContainsNoEngineClasses()
        /** Persistent report consumed by the aggregate compatibility gate. */
        val reportFile = runtimeClasspathReport.get()
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            """{"status":"passed","currentEngineSha256":"$resolvedCurrentHash","baselineAbsent":true,"legacyConsumerEmbeddedEngine":false,"runtimeArtifacts":${runtimeArtifacts.size}}\n""",
        )
    }
}

tasks.configureEach {
    if (name == "testDebugUnitTest") {
        dependsOn(verifyCurrentRunnerClasspath)
    }
}

/** Rejects any engine classes accidentally bundled into this old consumer AAR. */
fun File.assertContainsNoEngineClasses() {
    ZipFile(this).use { aar: ZipFile ->
        /** Nested classes JAR entry expected in an Android library artifact. */
        val classesJarEntry = aar.getEntry("classes.jar")
            ?: throw GradleException("Legacy consumer AAR has no classes.jar: $path")
        aar.getInputStream(classesJarEntry).use { classesJarInput ->
            JarInputStream(classesJarInput).use { classesJar: JarInputStream ->
                /** Engine package prefixes that the old consumer must never embed. */
                val forbiddenPrefixes = listOf("com/purride/pixelui/", "com/purride/pixelcore/")
                while (true) {
                    /** Next class or resource stored in the nested consumer JAR. */
                    val entry = classesJar.nextJarEntry ?: break
                    if (forbiddenPrefixes.any(entry.name::startsWith)) {
                        throw GradleException("Legacy consumer embeds engine class ${entry.name}.")
                    }
                }
            }
        }
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
