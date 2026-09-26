import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

if (file("google-services.json").exists() || file("src/google-services.json").exists() || file("src/debug/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(props::load)
}

fun signingProp(name: String, default: String = ""): String {
    val env = System.getenv(name)?.trim().orEmpty()
    if (env.isNotEmpty()) return env
    return localProps.getProperty(name, default)?.trim().orEmpty()
}

fun releaseKeystoreFile(): File {
    val raw = signingProp("KEYSTORE_PATH").replace('\\', '/')
    if (raw.isBlank()) {
        return File(System.getProperty("user.home"), ".sspd/missing-release-keystore")
    }
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

fun loadKeyStore(file: File, password: CharArray): KeyStore {
    val errors = mutableListOf<Exception>()
    for (type in listOf("JKS", "PKCS12")) {
        try {
            val ks = KeyStore.getInstance(type)
            file.inputStream().use { ks.load(it, password) }
            return ks
        } catch (e: Exception) {
            errors += e
        }
    }
    throw IllegalStateException(
        "Unable to load release keystore ${file.absolutePath}",
        errors.lastOrNull()
    )
}

fun isAndroidDebugCertificate(cert: X509Certificate, alias: String): Boolean {
    val subject = cert.subjectX500Principal.name
    return alias.equals("androiddebugkey", ignoreCase = true) ||
        subject.contains("CN=Android Debug", ignoreCase = true)
}

fun requireReleaseSigning() {
    val keystore = releaseKeystoreFile()
    val storePassword = signingProp("KEYSTORE_PASSWORD")
    val keyPassword = signingProp("KEY_PASSWORD")
    val alias = signingProp("KEY_ALIAS", "sspd")
    val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
    require(keystore.isFile) {
        "Release keystore not found: ${keystore.absolutePath}. Set KEYSTORE_PATH in customer-app/local.properties or as a CI env var."
    }
    require(storePassword.isNotBlank() && keyPassword.isNotBlank() && alias.isNotBlank()) {
        "KEYSTORE_PASSWORD, KEY_PASSWORD, and KEY_ALIAS must be set in customer-app/local.properties or as CI env vars."
    }
    require(keystore.canonicalFile != debugKeystore.canonicalFile && !keystore.name.equals("debug.keystore", true)) {
        "Release must not use the Android Debug keystore (${keystore.absolutePath})."
    }
    val ks = loadKeyStore(keystore, storePassword.toCharArray())
    val cert = ks.getCertificate(alias) as? X509Certificate
        ?: error("Alias '$alias' was not found in ${keystore.absolutePath}")
    require(!isAndroidDebugCertificate(cert, alias)) {
        "Release is signed with the Android Debug certificate (${cert.subjectX500Principal.name}). Use a dedicated production keystore."
    }
}

android {
    sourceSets.getByName("main").java.srcDir("src/fcm/java")
    namespace  = "com.sspd.servicemgmt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sspd.customer"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 12
        versionName   = "1.0.12"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://sspdmyanmar.com\"")
        buildConfigField("String", "APP_DISPLAY_NAME", "\"SSPD Customer\"")
        val googleWebClientId = signingProp("GOOGLE_WEB_CLIENT_ID")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        create("release") {
            storeFile     = releaseKeystoreFile()
            storePassword = signingProp("KEYSTORE_PASSWORD")
            keyAlias      = signingProp("KEY_ALIAS", "sspd")
            keyPassword   = signingProp("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = false
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures {
        compose     = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst { requireReleaseSigning() }
}

afterEvaluate {
    val releaseSigning = android.buildTypes.getByName("release").signingConfig
    require(releaseSigning != null && releaseSigning.name != "debug") {
        "Customer production builds must use signingConfigs.release, not the Android Debug signer."
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
