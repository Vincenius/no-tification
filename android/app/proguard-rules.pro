# Defense in depth for "no notification content in logcat": SafeLog is already a no-op in release
# builds, and this also removes verbose/debug/info logging from libraries.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
