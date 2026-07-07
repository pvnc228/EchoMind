# Keep data classes for serialization
-keepclassmembers class com.echomind.** {
    @kotlinx.serialization.Serializable *;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
