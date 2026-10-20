plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.fintrack.capture.emitter"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.fintrack.capture.emitter"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Match the lifecycle version already resolved by the main app's Compose stack.
    androidTestImplementation("androidx.lifecycle:lifecycle-common:2.9.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}
