# Keep WebView & app classes from R8 stripping
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.exambrowser.kotlin.** { *; }
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**
