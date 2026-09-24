import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tryniecki.appostazja"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        debug {
            val apiBaseUrl = configuredApiBaseUrl ?: "http://10.0.2.2:5000"
            buildConfigField("String", "APPOSTAZJA_API_BASE_URL", "\"$apiBaseUrl\"")
            manifestPlaceholders["allowCleartextTraffic"] = "true"
        }
        release {
            val apiBaseUrl = configuredApiBaseUrl ?: "https://appostazja-api.invalid"
            buildConfigField("String", "APPOSTAZJA_API_BASE_URL", "\"$apiBaseUrl\"")
            manifestPlaceholders["allowCleartextTraffic"] = "false"
            proguardFiles("proguard-rules.pro")
            optimization {
                enable = true
            }
        }
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

dependencies {
    implementation(project(":api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    implementation(libs.tabler.icons.outline.android)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
