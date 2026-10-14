# No reflection / serialisation is used by this app, so the default rules are enough.
# Keep ConsumerIrManager untouched (it is a system service, never obfuscated anyway).
-keepclassmembers class com.azahar4bd.walton.wrc03.** { *; }
