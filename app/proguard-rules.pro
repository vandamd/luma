# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Convex mobile pulls in JNA, which resolves native peer fields by exact name.
# If R8 renames or strips these classes, release startup crashes before the
# launcher can render.
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# Convex mobile's generated UniFFI/JNA bridge also relies on reflective field
# layout, so its model classes need to stay intact in release builds.
-keep class dev.convex.android.** { *; }
-keep interface dev.convex.android.** { *; }
