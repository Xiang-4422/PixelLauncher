// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register<Exec>("pixelReleaseCheck") {
    group = "verification"
    description = "Runs the local pixel-engine release validation gate."
    commandLine("bash", "tools/pixel-release-check.sh")
}
