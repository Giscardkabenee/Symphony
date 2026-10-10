# Keep the app's own classes as they are (services, widget, settings read by name).
-keep class com.symphony.music.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
