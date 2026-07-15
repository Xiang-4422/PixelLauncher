pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PixelLauncherV2"
include(":app")
include(":app-backup-fixture")
include(":pixel-engine")
include(":pixel-core")
include(":pixel-runtime")
include(":pixel-widgets")
include(":pixel-navigation")
include(":pixel-android")
include(":pixel-testing")
include(":pixel-debug")
include(":pixel-compose")
include(":pixel-compose-sample")
include(":pixel-demo")
include(":pixel-benchmark-target")
include(":pixel-benchmark")
include(":pixel-microbenchmark")
 
