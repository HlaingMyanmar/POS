import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(props::load)
}

fun releaseKeystoreFile(): File {
    val raw = localProps.getProperty("KEYSTORE_PATH", "../sspd-release.keystore")
        .trim()
        .replace('\\', '/')
    val candidate = File(raw)
    val resolved = if (candidate.isAbsolute) candidate else rootProject.file(raw)
    if (resolved.isFile) return resolved
    val withExt = File(resolved.path + ".keystore")
    if (withExt.isFile) return withExt
    if (resolved.isDirectory) {
        val nested = resolved.listFiles()?.firstOrNull { f ->
            f.isFile && (f.extension.equals("keystore", true) || f.extension.equals("jks", true))
        }
        if (nested != null) return nested
    }
    return resolved
}

android {
    namespace  = "com.sspd.servicemgmt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sspd.technician"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.1"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "DEFAULT_BASE_URL", "\"http://118.27.151.89\"")
        buildConfigField("String", "APP_DISPLAY_NAME", "\"SSPD Technician\"")
        buildConfigField("boolean", "TECHNICIAN_ONLY", "true")
    }

    signingConfigs {
        create("release") {
            storeFile     = releaseKeystoreFile()
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "21101998")
            keyAlias      = localProps.getProperty("KEY_ALIAS", "sspd")
            keyPassword   = localProps.getProperty("KEY_PASSWORD", "21101998")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable        = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs   { useLegacyPackaging = false }
    }

}

fun requireReleaseSigning() {
    val keystore = releaseKeystoreFile()
    val storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
    val keyPassword = localProps.getProperty("KEY_PASSWORD", "")
    val alias = localProps.getProperty("KEY_ALIAS", "sspd")
    require(keystore.isFile) {
        "Release keystore not found: ${keystore.absolutePath}. Set KEYSTORE_PATH in technician-app/local.properties."
    }
    require(storePassword.isNotBlank() && keyPassword.isNotBlank() && alias.isNotBlank()) {
        "KEYSTORE_PASSWORD, KEY_PASSWORD, and KEY_ALIAS must be set in technician-app/local.properties."
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst { requireReleaseSigning() }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
