# ── App API models (Retrofit / Gson must not be renamed) ──────────────────────
-keep class com.sspd.servicemgmt.core.network.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

-keep class androidx.navigation.** { *; }

-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
