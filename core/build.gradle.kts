import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Versions match the FUTO fork (fork/build.gradle: Kotlin 2.1.0, kotlinx-serialization-json 1.7.1).
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "net.z33v.fukbd"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation("junit:junit:4.13.2")
    // Test oracle only (design.md A-4): the JSON Schema must agree with ProfileParser.
    testImplementation("com.networknt:json-schema-validator:1.5.6")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
