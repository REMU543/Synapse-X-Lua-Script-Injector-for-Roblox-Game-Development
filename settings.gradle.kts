pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "football-manager-android"
include(":core", ":engine", ":tools")

// The Android app is only included when an Android SDK is available,
// so the pure-Kotlin engine can always be built and tested on any JVM.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasAndroidSdk) include(":app")
