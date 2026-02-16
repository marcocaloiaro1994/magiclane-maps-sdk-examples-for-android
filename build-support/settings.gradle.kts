@file:Suppress("UnstableApiUsage")

// This will find your gemSdkLocalMavenPath in ~/.gradle/gradle.properties
val gemSdkLocalMavenPath: String? by settings

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val localMavenPath = gemSdkLocalMavenPath
        ?: System.getenv("GEM_SDK_LOCAL_MAVEN_PATH").takeIf { !it.isNullOrBlank() }

    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        if (!localMavenPath.isNullOrBlank()) {
            maven { url = uri(localMavenPath) }
        } else {
            maven {
                url = uri("https://developer.magiclane.com/packages/android")
            }
        }
    }

    versionCatalogs {
        create("shared") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSupport"
include(":convention")
