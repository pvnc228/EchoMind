# --- kotlinx.serialization ---
-keepclassmembers class com.echomind.** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.echomind.**$$serializer { *; }
-keepclassmembers class com.echomind.** {
    *** Companion;
}

# --- Dagger Hilt ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# --- Retrofit + OkHttp ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- ExoPlayer ---
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Encrypted SharedPreferences / Security ---
-keep class androidx.security.crypto.** { *; }

# --- General ---
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
