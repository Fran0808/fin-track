import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

val workspaceEnvFile = rootProject.file("../.env")
val workspacePublicUrl = if (workspaceEnvFile.exists()) {
    workspaceEnvFile.readLines().firstNotNullOfOrNull { line ->
        Regex("""^\s*APP_SERVER_PUBLIC_URL\s*=\s*(.*?)\s*$""").matchEntire(line)
            ?.groupValues?.get(1)?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
    }
} else null
val serverBaseUrl = providers.environmentVariable("APP_SERVER_PUBLIC_URL").orNull?.takeIf { it.isNotBlank() }
    ?: workspacePublicUrl
    ?: localProperties.getProperty("BASE_URL")
    ?: "http://127.0.0.1:8080/"

android {
    namespace = "com.financemanager.listener"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.financemanager.listener"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "1.3.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL", "\"$serverBaseUrl\"")
    }

    buildTypes {
        create("captureTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".capturetest"
            versionNameSuffix = "-capturetest"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "BASE_URL", "\"http://127.0.0.1:1/\"")
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    sourceSets {
        getByName("debug").kotlin.directories.add("src/standard/java")
        getByName("release").kotlin.directories.add("src/standard/java")
        maybeCreate("testDebug").kotlin.directories.add("src/testStandard/java")
        maybeCreate("testRelease").kotlin.directories.add("src/testStandard/java")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("captureTest")) {
        it.enableAndroidTest = true
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
