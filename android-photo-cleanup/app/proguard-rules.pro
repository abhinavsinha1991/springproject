# Google Photos API
-keep class com.google.api.** { *; }
-keep class com.google.photos.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keepattributes *Annotation*
-keep class com.photocleanup.api.** { *; }
-keep class com.photocleanup.model.** { *; }
