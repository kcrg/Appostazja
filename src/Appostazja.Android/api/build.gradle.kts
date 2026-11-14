plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // Response and Headers appear in AppostazjaApi, so clients see them too.
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.junit)
}
