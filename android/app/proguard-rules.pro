# Reglas de ProGuard/R8 para Semáforos MX (MVP).
# Minify está desactivado en el MVP; estas reglas son la base para v1.
-keep class org.osmdroid.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <methods>;
}
