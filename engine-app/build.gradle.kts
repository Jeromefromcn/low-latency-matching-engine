plugins {
    id("mle.java-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":engine-core"))
    implementation(libs.agrona)
    implementation(libs.aeron.client)
    implementation(libs.aeron.driver)
    implementation(libs.hdrhistogram)
}
