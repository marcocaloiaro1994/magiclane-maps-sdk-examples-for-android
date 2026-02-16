import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.magiclane.sdk.examples.testing"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        minSdk = shared.versions.minSdkVersion.get().toInt()
    }

    buildTypes {
        release {
            isJniDebuggable = false
            isShrinkResources = false
            isMinifyEnabled = false
        }

        create("profile") {
            initWith(getByName("debug"))
        }

        debug {
            isJniDebuggable = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(shared.androidx.core.ktx)

    api(shared.junit)
    api(shared.androidx.test.core.ktx)
    api(shared.androidx.test.runner)
    api(shared.androidx.test.rules)
    api(shared.androidx.junit)

    val localPropFile = File(rootProject.projectDir, "../local.properties")
    val localProp = if (localPropFile.exists()) {
        Properties().apply { localPropFile.inputStream().use { load(it) } }
    } else null
    val gemSDKPath = localProp?.getProperty("aarPath.dir", null)

    if (!gemSDKPath.isNullOrEmpty()) {
        compileOnly(fileTree(mapOf("dir" to gemSDKPath, "include" to listOf("*.jar", "*.aar"))))
        configurations.forEach { it.exclude("com.magiclane", "maps-kotlin") }
    } else {
        val libsDir = File(rootProject.projectDir, "app/libs")
        val regex = Regex("MAGICLANE-(ADAS|MAPS)-SDK-.*\\.aar")
        val aarFile = libsDir.listFiles()?.find { it.name.matches(regex) }
        if (aarFile != null) {
            compileOnly(fileTree(mapOf("dir" to libsDir.absolutePath, "include" to listOf("*.jar", "*.aar"))))
            configurations.forEach { it.exclude("com.magiclane", "maps-kotlin") }
        } else {
            compileOnly(shared.magiclane.maps.kotlin)
        }
    }
}
