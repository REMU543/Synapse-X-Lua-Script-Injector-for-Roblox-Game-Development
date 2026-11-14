plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(21) }
dependencies { implementation(project(":engine")) }
application { mainClass.set("fm.tools.MainKt") }
tasks.named<JavaExec>("run") { standardInput = System.`in` }
