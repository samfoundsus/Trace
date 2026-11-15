# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room Database & Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# DataStore Preferences & Protobuf
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-dontwarn androidx.datastore.preferences.protobuf.**

# Jetpack Compose & Lifecycle ViewModels
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# CameraX (Camera2 implementation is loaded reflectively by CameraX)
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class androidx.concurrent.futures.** { *; }

# ARCore (JNI & reflection entry points required for Pinned Mode)
-keep class com.google.ar.core.** { *; }
-keep @com.google.ar.core.annotations.UsedByNative class *
-keepclassmembers class * {
    @com.google.ar.core.annotations.UsedByNative *;
}
-keep @com.google.ar.core.annotations.UsedByReflection class *
-keepclassmembers class * {
    @com.google.ar.core.annotations.UsedByReflection *;
}
-dontwarn com.google.ar.core.**

# Foreground Service & Application Entry Points
-keep class com.example.MainActivity { *; }
-keep class com.example.service.TimeLapseRecordingService { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.domain.model.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
