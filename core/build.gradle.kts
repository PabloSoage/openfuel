// Plain Kotlin/JVM: everything that can be wrong without anyone noticing —
// parsing, brand matching, tax maths, discounts — lives here, so it is tested
// on the host in seconds, with no device and no emulator.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.AMAZON)
    }
}

dependencies {
    // Tree API only (Json.parseToJsonElement): the official API's keys have
    // accents, spaces and parentheses, so nothing is mapped to data classes and
    // the serialization compiler plugin is not needed. Exposed as `api` because
    // the app builds its GeoJSON with the same builders.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
