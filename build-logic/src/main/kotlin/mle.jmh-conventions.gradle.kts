// JMH via the me.champeau.jmh plugin (ADR 0003).

plugins {
    id("mle.java-conventions")
    id("me.champeau.jmh")
}

val libs = the<VersionCatalogsExtension>().named("libs")

jmh {
    jmhVersion = libs.findVersion("jmh").get().requiredVersion
    resultFormat = "JSON"
    jvmArgsAppend.addAll(AgronaJvmArgs.ALL)
}

// Benchmarks are compiled by every `build` so they cannot rot between JMH runs.
tasks.named("assemble") {
    dependsOn("jmhClasses")
}
