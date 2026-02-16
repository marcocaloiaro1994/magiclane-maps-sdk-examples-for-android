@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("shared") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "third-party"
include(":MPChartLib")
