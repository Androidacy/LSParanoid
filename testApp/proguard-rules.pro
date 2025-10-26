# ProGuard rules for testApp

# Keep test activity for instrumentation
-keep class com.androidacy.lsparanoid.testapp.MainActivity {
    public <init>(...);
    public <methods>;
}

# Keep test utility class
-keep class com.androidacy.lsparanoid.testapp.StringUtils {
    public static <methods>;
}

# Standard Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Fix for missing ErrorProne annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# Note: LSParanoid's consumer rules from core module will be automatically
# applied to keep the Deobfuscator infrastructure intact
