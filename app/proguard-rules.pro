# Keep enough metadata for Kotlin reflection / generics used by Ktor & coroutines.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Ktor and coroutines rely on reflection / service loaders; keep them intact so R8 can't
# strip the CIO engine or coroutine internals the server needs at runtime.
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# jmDNS was removed; only Ktor's optional slf4j references remain — silence them.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.**
