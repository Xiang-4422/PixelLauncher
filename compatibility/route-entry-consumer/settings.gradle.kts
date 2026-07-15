pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/** Absolute isolated Maven repository populated by the compatibility script. */
val pixelCompatibilityRepository = providers.gradleProperty("pixelCompatibilityRepository")
    .orNull
    ?: error("Missing -PpixelCompatibilityRepository=<absolute repository path>.")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "PixelCompatibilityRepository"
                    url = uri(pixelCompatibilityRepository)
                }
            }
            filter {
                includeGroup("com.purride")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "PixelRouteEntryConsumerCompatibility"
include(
    ":positive",
    ":negative-wrong-argument",
    ":negative-wrong-complete",
    ":negative-wrong-replace",
)
