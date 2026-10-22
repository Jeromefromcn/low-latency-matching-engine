pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "low-latency-matching-engine"

include(
    "common",
    "engine-core",
    "engine-app",
    "loadgen",
    "bench-jmh",
)
