# Shizuku entry points are resolved reflectively.
-keep class rikka.shizuku.** { *; }
-keep class icu.nd4y.netswitcher.engine.** { *; }

# Hidden framework interfaces reached by name.
-keep class com.android.internal.telephony.ISub { *; }
-keep class com.android.internal.telephony.ISub$Stub { *; }

-dontwarn android.**
-dontwarn com.android.internal.**
