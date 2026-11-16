-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keep class com.tawasul.app.models.** { *; }
-dontwarn org.webrtc.**
-keep class org.webrtc.** { *; }
