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

/** 当前独立 core AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentCoreAar = providers.gradleProperty("currentCoreAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentCoreAar=<absolute current core AAR path>.")

/** 当前独立 runtime AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentRuntimeAar = providers.gradleProperty("currentRuntimeAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentRuntimeAar=<absolute current runtime AAR path>.")

/** 当前独立 widgets AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentWidgetsAar = providers.gradleProperty("currentWidgetsAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentWidgetsAar=<absolute current widgets AAR path>.")

/** 当前独立 navigation AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentNavigationAar = providers.gradleProperty("currentNavigationAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentNavigationAar=<absolute current navigation AAR path>.")

/** 当前独立 android AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentAndroidAar = providers.gradleProperty("currentAndroidAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentAndroidAar=<absolute current android AAR path>.")

/** 当前独立 testing AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentTestingAar = providers.gradleProperty("currentTestingAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentTestingAar=<absolute current testing AAR path>.")

/** 当前独立 debug AAR，其摘要必须与聚合 POM 传递解析到的产物一致。 */
val currentDebugAar = providers.gradleProperty("currentDebugAar")
    .map(::file)
    .orNull
    ?: error("Missing -PcurrentDebugAar=<absolute current debug AAR path>.")

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
    description = "Verifies the runner uses the old consumer binary with the current split SDK AARs."

    inputs.file(legacyConsumerAar)
    inputs.file(baselineEngineAar)
    inputs.file(currentEngineAar)
    inputs.file(currentCoreAar)
    inputs.file(currentRuntimeAar)
    inputs.file(currentWidgetsAar)
    inputs.file(currentNavigationAar)
    inputs.file(currentAndroidAar)
    inputs.file(currentTestingAar)
    inputs.file(currentDebugAar)
    outputs.file(runtimeClasspathReport)

    doLast {
        /** SHA-256 of the immutable baseline that must be absent at runtime. */
        val baselineHash = baselineEngineAar.sha256()
        /** SHA-256 of the newly produced current engine artifact. */
        val expectedCurrentHash = currentEngineAar.sha256()
        /** 当前独立 core 生产 AAR 的 SHA-256。 */
        val expectedCoreHash = currentCoreAar.sha256()
        /** 当前独立 runtime 生产 AAR 的 SHA-256。 */
        val expectedRuntimeHash = currentRuntimeAar.sha256()
        /** 当前独立 widgets 生产 AAR 的 SHA-256。 */
        val expectedWidgetsHash = currentWidgetsAar.sha256()
        /** 当前独立 navigation 生产 AAR 的 SHA-256。 */
        val expectedNavigationHash = currentNavigationAar.sha256()
        /** 当前独立 android 生产 AAR 的 SHA-256。 */
        val expectedAndroidHash = currentAndroidAar.sha256()
        /** 当前独立 testing 生产 AAR 的 SHA-256。 */
        val expectedTestingHash = currentTestingAar.sha256()
        /** 当前独立 debug 生产 AAR 的 SHA-256。 */
        val expectedDebugHash = currentDebugAar.sha256()
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
        /** 聚合 POM 必须且只能传递一个当前 pixel-core artifact。 */
        val coreArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-core"
        }
        if (coreArtifacts.size != 1) {
            throw GradleException("Expected one current core runtime artifact, found ${coreArtifacts.size}.")
        }
        /** 聚合 POM 必须且只能传递一个当前 pixel-runtime artifact。 */
        val pixelRuntimeArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-runtime"
        }
        if (pixelRuntimeArtifacts.size != 1) {
            throw GradleException(
                "Expected one current pixel-runtime artifact, found ${pixelRuntimeArtifacts.size}.",
            )
        }
        /** 聚合 POM 必须且只能传递一个当前 pixel-widgets artifact。 */
        val pixelWidgetsArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-widgets"
        }
        if (pixelWidgetsArtifacts.size != 1) {
            throw GradleException(
                "Expected one current pixel-widgets artifact, found ${pixelWidgetsArtifacts.size}.",
            )
        }
        /** 聚合 POM 必须且只能传递一个当前 pixel-navigation artifact。 */
        val pixelNavigationArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-navigation"
        }
        if (pixelNavigationArtifacts.size != 1) {
            throw GradleException(
                "Expected one current pixel-navigation artifact, found ${pixelNavigationArtifacts.size}.",
            )
        }
        /** 聚合 POM 必须且只能传递一个当前 pixel-android artifact。 */
        val pixelAndroidArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-android"
        }
        if (pixelAndroidArtifacts.size != 1) {
            throw GradleException(
                "Expected one current pixel-android artifact, found ${pixelAndroidArtifacts.size}.",
            )
        }
        /** 聚合 POM 必须且只能传递一个当前 pixel-testing artifact。 */
        val pixelTestingArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-testing"
        }
        if (pixelTestingArtifacts.size != 1) {
            throw GradleException(
                "Expected one current pixel-testing artifact, found ${pixelTestingArtifacts.size}.",
            )
        }
        /** 聚合 POM 必须且只能传递一个当前 pixel-debug artifact。 */
        val pixelDebugArtifacts = runtimeArtifacts.filter { artifact ->
            artifact.moduleVersion.id.group == "com.purride" && artifact.name == "pixel-debug"
        }
        if (pixelDebugArtifacts.size != 1) {
            throw GradleException(
                "Expected one current pixel-debug artifact, found ${pixelDebugArtifacts.size}.",
            )
        }
        /** Digest of the current engine artifact resolved through Maven metadata. */
        val resolvedCurrentHash = engineArtifacts.single().file.sha256()
        if (resolvedCurrentHash != expectedCurrentHash) {
            throw GradleException(
                "Resolved current engine hash $resolvedCurrentHash does not match producer $expectedCurrentHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 core AAR 摘要。 */
        val resolvedCoreHash = coreArtifacts.single().file.sha256()
        if (resolvedCoreHash != expectedCoreHash) {
            throw GradleException(
                "Resolved current core hash $resolvedCoreHash does not match producer $expectedCoreHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 runtime AAR 摘要。 */
        val resolvedRuntimeHash = pixelRuntimeArtifacts.single().file.sha256()
        if (resolvedRuntimeHash != expectedRuntimeHash) {
            throw GradleException(
                "Resolved current runtime hash $resolvedRuntimeHash does not match producer $expectedRuntimeHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 widgets AAR 摘要。 */
        val resolvedWidgetsHash = pixelWidgetsArtifacts.single().file.sha256()
        if (resolvedWidgetsHash != expectedWidgetsHash) {
            throw GradleException(
                "Resolved current widgets hash $resolvedWidgetsHash does not match producer $expectedWidgetsHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 navigation AAR 摘要。 */
        val resolvedNavigationHash = pixelNavigationArtifacts.single().file.sha256()
        if (resolvedNavigationHash != expectedNavigationHash) {
            throw GradleException(
                "Resolved current navigation hash $resolvedNavigationHash does not match producer " +
                    "$expectedNavigationHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 android AAR 摘要。 */
        val resolvedAndroidHash = pixelAndroidArtifacts.single().file.sha256()
        if (resolvedAndroidHash != expectedAndroidHash) {
            throw GradleException(
                "Resolved current android hash $resolvedAndroidHash does not match producer $expectedAndroidHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 testing AAR 摘要。 */
        val resolvedTestingHash = pixelTestingArtifacts.single().file.sha256()
        if (resolvedTestingHash != expectedTestingHash) {
            throw GradleException(
                "Resolved current testing hash $resolvedTestingHash does not match producer $expectedTestingHash.",
            )
        }
        /** 通过 Maven 元数据解析到的独立 debug AAR 摘要。 */
        val resolvedDebugHash = pixelDebugArtifacts.single().file.sha256()
        if (resolvedDebugHash != expectedDebugHash) {
            throw GradleException(
                "Resolved current debug hash $resolvedDebugHash does not match producer $expectedDebugHash.",
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
            """{"status":"passed","currentEngineSha256":"$resolvedCurrentHash","currentCoreSha256":"$resolvedCoreHash","currentRuntimeSha256":"$resolvedRuntimeHash","currentWidgetsSha256":"$resolvedWidgetsHash","currentNavigationSha256":"$resolvedNavigationHash","currentAndroidSha256":"$resolvedAndroidHash","currentTestingSha256":"$resolvedTestingHash","currentDebugSha256":"$resolvedDebugHash","baselineAbsent":true,"legacyConsumerEmbeddedEngine":false,"runtimeArtifacts":${runtimeArtifacts.size}}\n""",
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
