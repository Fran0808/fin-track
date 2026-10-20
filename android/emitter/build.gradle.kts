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
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}
