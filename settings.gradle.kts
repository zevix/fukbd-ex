// fukbd-ex standalone build: the pure-Kotlin :core only (design.md D-2).
// The Android parts are built through the FUTO fork with -Pfukbd.ex.dir.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "fukbd-ex"
include(":core")
