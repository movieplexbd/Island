# Dynamic Island ProGuard rules

# Keep service classes
-keep class com.dynamicisland.service.** { *; }
-keep class com.dynamicisland.receiver.** { *; }

# Keep model classes
-keep class com.dynamicisland.model.** { *; }

# Compose runtime
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
