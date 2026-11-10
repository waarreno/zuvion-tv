# libVLC usa JNI
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.zuvion.tv.**$$serializer { *; }
-keepclassmembers class com.zuvion.tv.** {
    *** Companion;
}
-keepclasseswithmembers class com.zuvion.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
