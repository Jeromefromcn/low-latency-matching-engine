plugins {
    id("mle.java-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.agrona)
    implementation(libs.hdrhistogram)
}
