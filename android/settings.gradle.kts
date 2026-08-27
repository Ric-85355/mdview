// settings.gradle.kts — created 2026-08-26, version 0.1.0.
// Purpose: configure the independent mdview Android Gradle build.
// Algorithm: resolve Android/Kotlin plugins and include the single app module.

pluginManagement {
    repositories {
        google()
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

rootProject.name = "mdview-android"
include(":app")
