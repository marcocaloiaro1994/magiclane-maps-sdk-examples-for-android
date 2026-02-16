pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "magiclane-maps-sdk-examples-for-android"

rootDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
    if (File(dir, "build.gradle.kts").exists() && dir.name != "build-support" && dir.name != "build-testing") {
        includeBuild(dir.name)
    }
}
