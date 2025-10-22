# ProGuard rules for vehicle-history-android module

# Keep all public API classes
-keep class com.obddroid.vehiclehistory.** { *; }
-keep class com.obddroid.vehicle.** { *; }

# Keep model classes for JSON parsing
-keepclassmembers class com.obddroid.vehicle.AutoCheckReport** {
    *;
}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.stream.** { *; }
-keep class sun.misc.Unsafe { *; }