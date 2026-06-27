# Keep Jakarta / Angus Mail providers and the activation framework. These rely on
# reflection and service metadata that R8 would otherwise strip.
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-keep class org.eclipse.angus.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class com.sun.activation.** { *; }
-dontwarn jakarta.mail.**
-dontwarn org.eclipse.angus.mail.**

# Keep Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**
