# kotlinx.serialization: keep generated serializers of TANY API models.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ma.tany.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ma.tany.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class ma.tany.**$$serializer { *; }
-keepnames class ma.tany.core.model.** { *; }

# Retrofit service interfaces (suspend functions + generic signatures).
-keep,allowobfuscation,allowshrinking interface ma.tany.core.network.TanyCollectApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Type-safe navigation routes are @Serializable.
-keep @kotlinx.serialization.Serializable class ma.tany.collect.navigation.** { *; }
