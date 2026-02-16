import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

repositories {
	google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(shared.build.android.gradle)
    implementation(shared.build.kotlin)
    implementation(shared.detekt.gradle)
    implementation(shared.ktlint.gradle)
}

gradlePlugin {
    plugins {
        register("AndroidKtlint") {
            id = "com.magiclane.sdk.examples.gradle.ktlint"
            implementationClass = "GradleKtlintConventionPlugin"
        }

        register("AndroidDetekt") {
            id = "com.magiclane.sdk.examples.gradle.detekt"
            implementationClass = "DetektConventionPlugin"
        }

        register("MagicLaneExamplesApplication") {
            id = "com.magiclane.sdk.examples.gradle.application"
            implementationClass = "ApplicationModulePlugin"
        }

        register("MagicLaneExamplesLibrary") {
            id = "com.magiclane.sdk.examples.gradle.library"
            implementationClass = "LibraryModulePlugin"
        }
    }
}

