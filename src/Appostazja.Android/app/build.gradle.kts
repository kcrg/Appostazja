import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val configuredApiBaseUrl = providers.gradleProperty("APPOSTAZJA_API_BASE_URL").orNull
val mapsApiKey = providers.gradleProperty("MAPS_API_KEY").orNull
    ?: localProperties.getProperty("MAPS_API_KEY")
    ?: System.getenv("MAPS_API_KEY")
    ?: ""

android {
    namespace = "com.tryniecki.appostazja"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.tryniecki.appostazja"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        // The same key is used for the Street View Static API (enable it in Google Cloud Console).
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildTypes {
        debug {
            val apiBaseUrl = configuredApiBaseUrl ?: "http://127.0.0.1:5000"
            buildConfigField("String", "APPOSTAZJA_API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            val apiBaseUrl = configuredApiBaseUrl ?: "https://api.appostazja.tryniecki.eu"
            buildConfigField("String", "APPOSTAZJA_API_BASE_URL", "\"$apiBaseUrl\"")
            optimization {
                enable = true
            }
        }
    }

    androidResources {
        localeFilters += "pl"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    finalizeDsl { extension ->
        // The profile plugin's legacy minification flags do not clear inherited AGP optimization.
        extension.buildTypes.findByName("nonMinifiedRelease")?.optimization {
            enable = false
        }
    }
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        val url = configuredApiBaseUrl ?: "http://127.0.0.1:5000"
        requireNotNull(variant.buildConfigFields)
            .put("APPOSTAZJA_API_BASE_URL", BuildConfigField("String", "\"$url\"", null))
    }
}

baselineProfile {
    automaticGenerationDuringBuild = providers.gradleProperty("APPOSTAZJA_GENERATE_BASELINE_PROFILE")
        .map(String::toBooleanStrict)
        .getOrElse(true)
    saveInSrc = true
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    baselineProfile(project(":baseline-profile"))
    implementation(project(":api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    // Newer than the version maps-compose depends on.
    implementation(libs.play.services.maps)
    implementation(libs.okhttp)
    implementation(libs.tabler.icons.outline.android)
    implementation(libs.vico.compose)
    implementation(libs.androidx.pdf.compose)
    implementation(libs.androidx.pdf.document.service)
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.bundled.jvm)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
