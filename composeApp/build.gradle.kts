import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("com.google.gms.google-services")
}

val envFile = rootProject.file(".env.local")
val envProps = Properties()
if (envFile.exists()) {
    envProps.load(FileInputStream(envFile))
}

val envSupabaseUrl = System.getenv("SUPABASE_URL") ?: envProps.getProperty("SUPABASE_URL", "")
val envSupabaseAnonKey = System.getenv("SUPABASE_ANON_KEY") ?: envProps.getProperty("SUPABASE_ANON_KEY", "")
val envGoogleWebClientId = System.getenv("GOOGLE_WEB_CLIENT_ID") ?: envProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")
val envRevenueCatApiKey = System.getenv("REVENUECAT_API_KEY") ?: envProps.getProperty("REVENUECAT_API_KEY", "")
val envAdmobBannerId = System.getenv("ADMOB_BANNER_ID") ?: envProps.getProperty("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/6300978111")
val envKeystoreFile = System.getenv("KEYSTORE_FILE") ?: envProps.getProperty("KEYSTORE_FILE", "syncdrop_release.jks")
val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: envProps.getProperty("KEYSTORE_PASSWORD", "")
val envKeyAlias = System.getenv("KEY_ALIAS") ?: envProps.getProperty("KEY_ALIAS", "syncdrop_key")
val envKeyPassword = System.getenv("KEY_PASSWORD") ?: envProps.getProperty("KEY_PASSWORD", "")
val envTurnUrl = System.getenv("TURN_URL") ?: envProps.getProperty("TURN_URL", "")
val envTurnUsername = System.getenv("TURN_USERNAME") ?: envProps.getProperty("TURN_USERNAME", "")
val envTurnCredential = System.getenv("TURN_CREDENTIAL") ?: envProps.getProperty("TURN_CREDENTIAL", "")

val appVersionName = "1.0.0"

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation("androidx.credentials:credentials:1.3.0")
            implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
            implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
            implementation("com.google.android.gms:play-services-auth:21.2.0")
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-messaging")
            implementation("com.google.zxing:core:3.5.3")
            implementation("com.journeyapps:zxing-android-embedded:4.3.0")
            implementation("com.revenuecat.purchases:purchases:8.12.2")
            implementation("com.google.android.gms:play-services-ads:23.6.0")
            implementation("androidx.biometric:biometric:1.2.0-alpha05")
        }
    }
}

android {
    namespace = "net.syncswift"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "net.syncswift"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"$envSupabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$envSupabaseAnonKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$envGoogleWebClientId\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$envRevenueCatApiKey\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$envAdmobBannerId\"")

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file(envKeystoreFile)
            storePassword = envKeystorePassword
            keyAlias = envKeyAlias
            keyPassword = envKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",
                "**/*.kotlin_builtins",
                "**/*.kotlin_metadata",
                "kotlin-tooling-metadata.json"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "net.syncswift.resources"
}
