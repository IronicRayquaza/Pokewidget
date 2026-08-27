# Widget providers are instantiated by the framework from the manifest.
-keep class com.pokewidgets.app.widget.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pokewidgets.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.pokewidgets.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
