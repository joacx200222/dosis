plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.dosis.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Must match the phone app's applicationId for the Wear Data Layer.
        applicationId = "app.dosis"
        minSdk = libs.versions.minSdkWear.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    buildFeatures { compose = true }
    // The interaction file built by tools/build_interactions.py, shipped as assets/interactions.tsv.
    sourceSets["main"].assets.srcDir(rootProject.file("data/generated"))
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
}
