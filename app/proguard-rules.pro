# Keep Jakarta / Angus Mail providers and the activation framework. These rely on
# reflection and service metadata that R8 would otherwise strip.
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-keep class org.eclipse.angus.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class com.sun.activation.** { *; }
-dontwarn jakarta.mail.**
-dontwarn org.eclipse.angus.mail.**

# Ktor + kotlinx.serialization (the embedded backend) — keep serializers and the CIO engine.
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class info.socrtwo.quillbox.**$$serializer { *; }
-keepclassmembers class info.socrtwo.quillbox.** { *** Companion; }
-keepclasseswithmembers class info.socrtwo.quillbox.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# The JavaScript bridge is called by name from the web UI.
-keepclassmembers class info.socrtwo.quillbox.MainActivity$Bridge { public *; }
