import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// API keys live in local.properties, which is git-ignored. See README —
// shipping keys inside an APK is fine for a personal build and NOT fine for
// a public release; that needs a backend proxy.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String = localProps.getProperty(name) ?: ""

// Release signing. Locally these come from keystore.properties (git-ignored);
// in CI they come from environment variables fed by GitHub secrets.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

val hasReleaseKey = signing("storeFile", "KEYSTORE_FILE") != null

android {
    namespace = "com.vitals.app"
    // 36 is required by androidx.health.connect:connect-client:1.1.0, which
    // also sets the AGP 8.9.1+ floor. targetSdk stays at 35 deliberately —
    // compiling against newer APIs is independent of opting into Android 16's
    // runtime behaviour changes.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vitals.app"
        // Health Connect needs API 28; the MediaStore RELATIVE_PATH column
        // this app queries needs API 29.
        minSdk = 29
        targetSdk = 35

        // An update install is only accepted if versionCode is >= the installed
        // one, so CI derives it from the build number. Locally it stays 1.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        buildConfigField("String", "OPENAI_API_KEY", "\"${secret("OPENAI_API_KEY")}\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${secret("ANTHROPIC_API_KEY")}\"")
        buildConfigField("String", "USDA_API_KEY", "\"${secret("USDA_API_KEY")}\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(signing("storeFile", "KEYSTORE_FILE")!!)
                storePassword = signing("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "KEY_ALIAS")
                keyPassword = signing("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Left off deliberately. R8 breakage in a release build you've
            // sideloaded onto a phone is painful to diagnose, and APK size
            // doesn't matter for a personal app. Turn it on when you have a
            // reason to.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }

        debug {
            // Signed with the SAME key as release when one is configured, so a
            // build from Android Studio can update an installed release build in
            // place. Different keys mean the install is refused and the only way
            // out is uninstalling — which destroys the food log database.
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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

// Kotlin 2.3 turned the old android.kotlinOptions block into a hard error.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Health Connect — the read side.
    implementation("androidx.health.connect:connect-client:1.1.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Voice-note pipeline.
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val room = "2.8.4"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
