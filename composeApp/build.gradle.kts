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

val envSupabaseUrl = envProps.getProperty("SUPABASE_URL", "")
val envSupabaseAnonKey = envProps.getProperty("SUPABASE_ANON_KEY", "")
val envSupabaseServiceRoleKey = envProps.getProperty("SUPABASE_SERVICE_ROLE_KEY", "")
val envGoogleWebClientId = envProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")
val envBrevoApiKey = envProps.getProperty("BREVO_API_KEY", "")
val envBrevoSenderEmail = envProps.getProperty("BREVO_SENDER_EMAIL", "noreply@syncswift.net")
val envAwsSesAccessKeyId = envProps.getProperty("AWS_SES_ACCESS_KEY_ID", "")
val envAwsSesSecretAccessKey = envProps.getProperty("AWS_SES_SECRET_ACCESS_KEY", "")
val envAwsSesRegion = envProps.getProperty("AWS_SES_REGION", "eu-central-1")
val envAwsSesSenderEmail = envProps.getProperty("AWS_SES_SENDER_EMAIL", "noreply@syncswift.net")
val envRevenueCatApiKey = envProps.getProperty("REVENUECAT_API_KEY", "")
val envAdmobBannerId = envProps.getProperty("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/6300978111")

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
        buildConfigField("String", "SUPABASE_SERVICE_ROLE_KEY", "\"$envSupabaseServiceRoleKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$envGoogleWebClientId\"")
        buildConfigField("String", "BREVO_API_KEY", "\"$envBrevoApiKey\"")
        buildConfigField("String", "BREVO_SENDER_EMAIL", "\"$envBrevoSenderEmail\"")
        buildConfigField("String", "AWS_SES_ACCESS_KEY_ID", "\"$envAwsSesAccessKeyId\"")
        buildConfigField("String", "AWS_SES_SECRET_ACCESS_KEY", "\"$envAwsSesSecretAccessKey\"")
        buildConfigField("String", "AWS_SES_REGION", "\"$envAwsSesRegion\"")
        buildConfigField("String", "AWS_SES_SENDER_EMAIL", "\"$envAwsSesSenderEmail\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$envRevenueCatApiKey\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$envAdmobBannerId\"")

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("syncdrop_release.jks")
            storePassword = "***REMOVED***"
            keyAlias = "syncdrop_key"
            keyPassword = "***REMOVED***"
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
