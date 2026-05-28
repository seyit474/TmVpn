# Telo VPN ProGuard kuralları

# Xray / libv2ray
-keep class libv2ray.** { *; }
-keep class com.telo.vpn.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$serializer { *; }
-keep,includedescriptorclasses class com.telo.vpn.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# DataStore
-keep class androidx.datastore.** { *; }

# Android VpnService
-keep class * extends android.net.VpnService { *; }
