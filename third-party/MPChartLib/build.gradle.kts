plugins {
    alias(shared.plugins.android.library)
}

android {
    namespace = "com.github.mikephil.charting"
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

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/java")
        }
    }
}

dependencies {
    implementation(shared.androidx.annotation)
    testImplementation(shared.junit)
}
