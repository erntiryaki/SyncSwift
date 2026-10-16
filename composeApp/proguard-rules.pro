# Proguard & R8 Rules for SyncDrop

# Keep annotations & signatures for Kotlin reflection/coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep app entry points
-keep class net.syncswift.MainActivity { *; }

# Keep data models used in localization and state without aggressive optimization
-keep class net.syncswift.Copy { *; }
-keep class net.syncswift.localization.** { *; }
-keep class net.syncswift.AppLanguage { *; }
-keep class net.syncswift.IdentityProvider { *; }
-keep class net.syncswift.AuthSession { *; }
-keep class net.syncswift.GoogleUserData { *; }

# Google Play Services Auth & One Tap
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }

# Coroutines internal handlers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Strip debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Don't warn on optional dependencies
-dontwarn androidx.credentials.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.libraries.identity.googleid.**
-dontwarn kotlinx.coroutines.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.barcodescanner.**
-dontwarn com.google.android.gms.code.scanner.**
-keep class net.syncswift.ui.components.CustomQrScannerActivity { *; }

# Firebase & FCM Push Notifications
-keep class com.google.firebase.** { *; }
-keep class net.syncswift.fcm.** { *; }

