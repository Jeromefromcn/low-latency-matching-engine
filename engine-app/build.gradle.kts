plugins {
    id("mle.java-conventions")
    application
}

dependencies {
    implementation(project(":common"))
    implementation(project(":engine-core"))
    implementation(project(":loadgen"))
    implementation(libs.agrona)
    implementation(libs.aeron.client)
    implementation(libs.aeron.driver)
    implementation(libs.hdrhistogram)
}

application {
    mainClass = "io.github.jeromefromcn.matching.app.SkeletonMain"
    applicationDefaultJvmArgs = AgronaJvmArgs.ALL
}
