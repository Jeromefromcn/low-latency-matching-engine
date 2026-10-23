// Shared Java settings for every module: Java 25 toolchain, strict compilation, JUnit Platform tests,
// palantir-java-format via Spotless (spotlessCheck is part of `check`, so `build` fails on unformatted code).

plugins {
    `java-library`
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "io.github.jeromefromcn.matching"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

spotless {
    java {
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.findVersion("java").get().requiredVersion.toInt()
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("jqwik").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(AgronaJvmArgs.ALL)
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
