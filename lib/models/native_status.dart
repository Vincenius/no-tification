class NativeStatus {
  const NativeStatus({
    required this.notificationAccess,
    required this.listenerConnected,
    required this.ignoredCount,
  });

  static const unknown =
      NativeStatus(notificationAccess: false, listenerConnected: false, ignoredCount: 0);

  final bool notificationAccess;
  final bool listenerConnected;
  final int ignoredCount;

  factory NativeStatus.fromMap(Map<Object?, Object?> map) => NativeStatus(
        notificationAccess: map['notificationAccess'] as bool? ?? false,
        listenerConnected: map['listenerConnected'] as bool? ?? false,
        ignoredCount: map['ignoredCount'] as int? ?? 0,
      );
}
