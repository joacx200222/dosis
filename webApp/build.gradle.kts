import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("dosisWeb")
        browser {
            commonWebpackConfig { outputFileName = "dosisWeb.js" }
        }
        binaries.executable()
    }

    sourceSets {
        // Serve data/generated/interactions.tsv next to index.html.
        wasmJsMain.get().resources.srcDir(rootProject.file("data/generated"))
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
