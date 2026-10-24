plugins {
    id("mle.java-conventions")
}

// sbe-tool runs at build time only; it is not on any module's compile or runtime classpath.
val sbeCodegen by configurations.creating

dependencies {
    api(libs.agrona)
    sbeCodegen(libs.sbe.tool)
}

// Codecs are generated on every build from the schema (ROADMAP 2.1, ADR 0005), never committed.
val generateSbeCodecs by tasks.registering(JavaExec::class) {
    val schema = layout.projectDirectory.file("src/main/sbe/matching-schema.xml")
    val outputDir = layout.buildDirectory.dir("generated/sources/sbe")

    inputs.file(schema).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir)

    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    classpath = sbeCodegen
    mainClass = "uk.co.real_logic.sbe.SbeTool"
    jvmArgs(AgronaJvmArgs.ALL)
    systemProperty("sbe.output.dir", outputDir.get().asFile.absolutePath)
    // A schema warning (e.g. a suspicious type or offset) fails the build, like -Werror for Java.
    systemProperty("sbe.validation.stop.on.error", "true")
    systemProperty("sbe.validation.warnings.fatal", "true")
    args(schema.asFile.absolutePath)

    // Start clean so a message removed from the schema does not leave a stale codec behind.
    doFirst { outputDir.get().asFile.deleteRecursively() }
}

sourceSets.main {
    java.srcDir(generateSbeCodecs)
}
