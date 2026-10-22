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
val envSupabaseServiceRoleKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY") ?: envProps.getProperty("SUPABASE_SERVICE_ROLE_KEY", "")
val envGoogleWebClientId = System.getenv("GOOGLE_WEB_CLIENT_ID") ?: envProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")
val envBrevoApiKey = System.getenv("BREVO_API_KEY") ?: envProps.getProperty("BREVO_API_KEY", "")
val envBrevoSenderEmail = System.getenv("BREVO_SENDER_EMAIL") ?: envProps.getProperty("BREVO_SENDER_EMAIL", "noreply@syncswift.net")
val envAwsSesAccessKeyId = System.getenv("AWS_SES_ACCESS_KEY_ID") ?: envProps.getProperty("AWS_SES_ACCESS_KEY_ID", "")
val envAwsSesSecretAccessKey = System.getenv("AWS_SES_SECRET_ACCESS_KEY") ?: envProps.getProperty("AWS_SES_SECRET_ACCESS_KEY", "")
val envAwsSesRegion = System.getenv("AWS_SES_REGION") ?: envProps.getProperty("AWS_SES_REGION", "eu-central-1")
val envAwsSesSenderEmail = System.getenv("AWS_SES_SENDER_EMAIL") ?: envProps.getProperty("AWS_SES_SENDER_EMAIL", "noreply@syncswift.net")
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

// Desktop has no BuildConfig; generate an equivalent Kotlin object from .env.local
val desktopConfigDir = layout.buildDirectory.dir("generated/desktopConfig/kotlin")
val generateDesktopConfig by tasks.registering {
    val outDir = desktopConfigDir
    val values = mapOf(
        "SUPABASE_URL" to envSupabaseUrl,
        "SUPABASE_ANON_KEY" to envSupabaseAnonKey,
        "SUPABASE_SERVICE_ROLE_KEY" to envSupabaseServiceRoleKey,
        "GOOGLE_WEB_CLIENT_ID" to envGoogleWebClientId,
        "BREVO_API_KEY" to envBrevoApiKey,
        "BREVO_SENDER_EMAIL" to envBrevoSenderEmail,
        "AWS_SES_ACCESS_KEY_ID" to envAwsSesAccessKeyId,
        "AWS_SES_SECRET_ACCESS_KEY" to envAwsSesSecretAccessKey,
        "AWS_SES_REGION" to envAwsSesRegion,
        "AWS_SES_SENDER_EMAIL" to envAwsSesSenderEmail,
        "REVENUECAT_API_KEY" to envRevenueCatApiKey,
        "TURN_URL" to envTurnUrl,
        "TURN_USERNAME" to envTurnUsername,
        "TURN_CREDENTIAL" to envTurnCredential,
        "VERSION_NAME" to appVersionName
    )
    inputs.properties(values)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("net/syncswift/DesktopBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        val body = values.entries.joinToString("\n") { (k, v) ->
            val escaped = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\${'$'}")
            "    const val $k: String = \"$escaped\""
        }
        file.writeText("package net.syncswift\n\n// Auto-generated from .env.local. Do not edit.\ninternal object DesktopBuildConfig {\n$body\n}\n")
    }
}

kotlin {
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
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
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        desktopMain.kotlin.srcDir(desktopConfigDir)
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
            implementation("org.json:json:20240303")
            implementation("org.jmdns:jmdns:3.5.12")
            implementation("com.google.zxing:core:3.5.3")
            implementation("com.github.kwhat:jnativehook:2.2.2")
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

compose.desktop {
    application {
        mainClass = "net.syncswift.MainKt"
        jvmArgs += listOf("-Dsun.java2d.uiScale.enabled=true", "-Dapple.awt.application.appearance=system")
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "SyncSwift"
            packageVersion = appVersionName
            description = "Seamless cross-device file sharing & universal clipboard"
            vendor = "SyncSwift"
            copyright = "© 2026 SyncSwift. All rights reserved."
            modules("java.net.http", "java.naming", "java.prefs", "jdk.crypto.ec", "jdk.unsupported", "java.desktop")
            windows {
                iconFile.set(rootDir.resolve("desktop-icons/icon.ico"))
                menuGroup = "SyncSwift"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6E0C4C2A-8B8B-4C4F-9C59-5D3A1B7F2E10"
            }
            macOS {
                iconFile.set(rootDir.resolve("desktop-icons/icon.icns"))
                bundleID = "net.syncswift.desktop"
                dockName = "SyncSwift"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>SyncSwift discovers your nearby devices on the local network to transfer files.</string>
                        <key>NSBonjourServices</key>
                        <array><string>_syncswift._tcp</string></array>
                        <key>LSUIElement</key>
                        <false/>
                    """.trimIndent()
                }
            }
        }
    }
}

tasks.matching { it.name == "compileKotlinDesktop" }.configureEach { dependsOn(generateDesktopConfig) }

compose.resources {
    packageOfResClass = "net.syncswift.resources"
}
