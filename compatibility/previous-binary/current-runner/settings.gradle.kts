pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/** Isolated Maven repository containing the current engine artifact under test. */
val pixelCompatibilityRepository = providers.gradleProperty("pixelCompatibilityRepository")
    .orNull
    ?: error("Missing -PpixelCompatibilityRepository=<absolute current repository path>.")

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

rootProject.name = "PixelCurrentBinaryRunner"
include(":runner")
