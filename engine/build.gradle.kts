plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
kotlin { jvmToolchain(21) }
dependencies {
    api(project(":core"))
    testImplementation(kotlin("test"))
}
tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
    // The long-term stress test runs only when requested: ./gradlew :engine:test -Pstress
    if (!project.hasProperty("stress")) exclude("**/StressTest*")
    if (project.hasProperty("sweep")) systemProperty("sweep", "1")
}
