# CLIPR ProGuard / R8 Configuration
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.clipr.app.**$$serializer { *; }
-keepclassmembers class com.clipr.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.clipr.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 Transformer & Effects
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# ML Kit Face Detection
-keep class com.google.mlkit.** { *; }
