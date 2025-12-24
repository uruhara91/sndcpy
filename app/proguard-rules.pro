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

# Izinkan R8 merubah access modifier (private/public) biar bisa di-inline
-allowaccessmodification

# Pindahkan semua class ke package root (biar nama package 'a.b' jadi hilang)
-repackageclasses ''

# Optimasi loop dan aritmatika (mirip -O3)
-optimizationpasses 5

# Karena sndcpy simple, kita keep entry point-nya aja
-keep class com.rom1v.sndcpy.MainActivity { *; }
