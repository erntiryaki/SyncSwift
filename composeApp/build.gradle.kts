import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
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
        }
    }
}

android {
    namespace = "com.getsyncdrop.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.getsyncdrop.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"$envSupabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$envSupabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_SERVICE_ROLE_KEY", "\"$envSupabaseServiceRoleKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$envGoogleWebClientId\"")
        buildConfigField("String", "BREVO_API_KEY", "\"$envBrevoApiKey\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
