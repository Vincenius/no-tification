class NativeStatus {
  const NativeStatus({
    required this.notificationAccess,
    required this.listenerConnected,
    required this.dndAccess,
    required this.urgentBypassesDnd,
    required this.postNotifications,
    required this.exactAlarms,
    required this.enabled,
    required this.nextDigestAt,
    required this.heldCount,
    required this.todayPassed,
    required this.todayHeld,
    required this.sdkInt,
    required this.aiActive,
    required this.batteryOptimizationIgnored,
    required this.silencingSuspended,
  });

  final bool notificationAccess;
  final bool listenerConnected;
  final bool dndAccess;
  final bool urgentBypassesDnd;
  final bool postNotifications;
  final bool exactAlarms;
  final bool enabled;
  final DateTime? nextDigestAt;
  final int heldCount;
  final int todayPassed;
  final int todayHeld;
  final int sdkInt;
  final bool aiActive;
  final bool batteryOptimizationIgnored;

  /// The listener was gone too long, so Calm paused its DND mode (fail open).
  final bool silencingSuspended;

  /// Without these Calm can't work at all.
  bool get requiredGranted => notificationAccess && dndAccess && postNotifications;

  /// Calm works without these, but worse.
  bool get recommendedGranted => urgentBypassesDnd && exactAlarms && batteryOptimizationIgnored;

  factory NativeStatus.fromMap(Map<Object?, Object?> map) {
    final next = map['nextDigestAt'] as int?;
    return NativeStatus(
      notificationAccess: map['notificationAccess'] as bool? ?? false,
      listenerConnected: map['listenerConnected'] as bool? ?? false,
      dndAccess: map['dndAccess'] as bool? ?? false,
      urgentBypassesDnd: map['urgentBypassesDnd'] as bool? ?? false,
      postNotifications: map['postNotifications'] as bool? ?? false,
      exactAlarms: map['exactAlarms'] as bool? ?? false,
      enabled: map['enabled'] as bool? ?? false,
      nextDigestAt: next == null ? null : DateTime.fromMillisecondsSinceEpoch(next),
      heldCount: map['heldCount'] as int? ?? 0,
      todayPassed: map['todayPassed'] as int? ?? 0,
      todayHeld: map['todayHeld'] as int? ?? 0,
      sdkInt: map['sdkInt'] as int? ?? 0,
      aiActive: map['aiActive'] as bool? ?? false,
      batteryOptimizationIgnored: map['batteryOptimizationIgnored'] as bool? ?? false,
      silencingSuspended: map['silencingSuspended'] as bool? ?? false,
    );
  }
}
