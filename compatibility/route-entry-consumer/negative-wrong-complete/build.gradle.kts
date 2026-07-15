plugins {
    id("com.android.library")
}

android {
    namespace = "com.purride.pixelcompat.routeentry.negative.complete"
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
}
