# Keep WebView & app classes from R8 stripping
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.exambrowser.redodo.app.** { *; }
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**

# Keep launcher icon resources
-keep class **.R$mipmap { *; }
-keep class **.R$drawable { *; }
