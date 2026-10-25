import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release version comes from the git tag in CI (-PversionName=1.2.3); versionCode is derived from it
// so every release upgrades the previous one: 1.2.3 -> 10203.
val appVersion = (findProperty("versionName") as String?) ?: "0.1.0"
val appVersionCode = appVersion.split('.').map { it.toInt() }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Release signing: KURS_* environment variables in CI, ~/.android/kurs-release.properties locally.
// Without either the release APK is left unsigned.
val signing: Map<String, String>? = System.getenv("KURS_KEYSTORE_FILE")?.let {
    mapOf(
        "storeFile" to it,
        "storePassword" to System.getenv("KURS_KEYSTORE_PASSWORD"),
        "keyAlias" to System.getenv("KURS_KEY_ALIAS"),
        "keyPassword" to System.getenv("KURS_KEY_PASSWORD"),
    )
} ?: File(System.getProperty("user.home"), ".android/kurs-release.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }.let { props -> props.stringPropertyNames().associateWith(props::getProperty) }
}

android {
    namespace = "io.github.shpuljka.kurs"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.shpuljka.kurs"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (signing != null) create("release") {
            storeFile = file(signing.getValue("storeFile"))
            storePassword = signing.getValue("storePassword")
            keyAlias = signing.getValue("keyAlias")
            keyPassword = signing.getValue("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests.
    testImplementation(libs.org.json)
}
