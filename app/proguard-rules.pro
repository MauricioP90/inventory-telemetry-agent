# ProGuard rules for inventory-telemetry-agent

# Conservar entidades del dominio para serialización JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Mantener clases de WorkManager
-keep class androidx.work.** { *; }

# Mantener reglas de OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
