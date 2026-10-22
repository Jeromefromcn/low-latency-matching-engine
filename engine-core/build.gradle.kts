// Pure, single-threaded, deterministic state machine. Must not depend on Aeron, threads or clocks (CLAUDE.md).
plugins {
    id("mle.java-conventions")
}

dependencies {
    api(project(":common"))
    api(libs.agrona)
}
