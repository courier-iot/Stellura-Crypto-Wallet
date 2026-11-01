# Wallet Core uses JNI + protobuf-lite; keep its classes intact.
-keep class wallet.core.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
