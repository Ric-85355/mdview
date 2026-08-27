// build.gradle.kts — created 2026-08-26, version 0.1.0.
// Purpose: declare Android-wide build plugins without applying them at root.
// Algorithm: pin compatible Android, Kotlin, and Compose compiler plugins.

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
