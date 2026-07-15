pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/** Isolated repository containing only the pixel-engine artifact published for this verification run. */
val pixelCompatibilityRepository = providers.gradleProperty("pixelCompatibilityRepository")
    .orNull
    ?: error("Missing -PpixelCompatibilityRepository=<absolute repository path>.")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "PixelCompatibilityRepository"
            url = uri(pixelCompatibilityRepository)
            content {
                includeGroup("com.purride")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "PixelExternalRenderSpi"
include(":consumer")
