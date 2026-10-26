class Suggestion {
  const Suggestion({required this.id, required this.label, required this.detail});

  final String id;
  final String label;
  final String detail;

  factory Suggestion.fromMap(Map<Object?, Object?> map) => Suggestion(
        id: map['id'] as String,
        label: map['label'] as String,
        detail: map['detail'] as String? ?? '',
      );
}

class FeedbackOptions {
  const FeedbackOptions({required this.feedback, required this.suggestions, this.note});

  /// SHOULD_BE_URGENT or SHOULD_NOT_BE_URGENT.
  final String feedback;
  final List<Suggestion> suggestions;

  /// Set when no rule can change the outcome.
  final String? note;

  factory FeedbackOptions.fromMap(Map<Object?, Object?> map) => FeedbackOptions(
        feedback: map['feedback'] as String,
        note: map['note'] as String?,
        suggestions: (map['suggestions'] as List<Object?>? ?? const [])
            .map((s) => Suggestion.fromMap(s as Map<Object?, Object?>))
            .toList(),
      );
}

class AppStat {
  const AppStat(this.appLabel, this.count);

  final String appLabel;
  final int count;
}

class Stats {
  const Stats({
    required this.days,
    required this.passed,
    required this.held,
    required this.bySource,
    required this.topHeld,
    required this.topPassed,
    required this.shouldHaveBeenUrgent,
    required this.shouldNotHaveBeenUrgent,
  });

  final int days;
  final int passed;
  final int held;

  /// RULE / AI / FALLBACK → count.
  final Map<String, int> bySource;
  final List<AppStat> topHeld;
  final List<AppStat> topPassed;
  final int shouldHaveBeenUrgent;
  final int shouldNotHaveBeenUrgent;

  int get total => passed + held;

  static List<AppStat> _apps(Object? list) => (list as List<Object?>? ?? const [])
      .cast<Map<Object?, Object?>>()
      .map((m) => AppStat(m['appLabel'] as String, m['count'] as int))
      .toList();

  factory Stats.fromMap(Map<Object?, Object?> map) => Stats(
        days: map['days'] as int? ?? 7,
        passed: map['passed'] as int? ?? 0,
        held: map['held'] as int? ?? 0,
        bySource: (map['bySource'] as Map<Object?, Object?>? ?? const {}).map((k, v) => MapEntry(k as String, v as int)),
        topHeld: _apps(map['topHeld']),
        topPassed: _apps(map['topPassed']),
        shouldHaveBeenUrgent: map['shouldHaveBeenUrgent'] as int? ?? 0,
        shouldNotHaveBeenUrgent: map['shouldNotHaveBeenUrgent'] as int? ?? 0,
      );
}

class Maintenance {
  const Maintenance({
    required this.retentionDays,
    this.lastCleanupAt,
    required this.listenerConnected,
    required this.notificationAccess,
    this.listenerDisconnectedSince,
    required this.silencingSuspended,
    this.lastHealthCheckAt,
    required this.batteryOptimizationIgnored,
  });

  final int retentionDays;
  final DateTime? lastCleanupAt;
  final bool listenerConnected;
  final bool notificationAccess;
  final DateTime? listenerDisconnectedSince;
  final bool silencingSuspended;
  final DateTime? lastHealthCheckAt;
  final bool batteryOptimizationIgnored;

  static DateTime? _time(Object? ms) => ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms as int);

  factory Maintenance.fromMap(Map<Object?, Object?> map) => Maintenance(
        retentionDays: map['retentionDays'] as int? ?? 7,
        lastCleanupAt: _time(map['lastCleanupAt']),
        listenerConnected: map['listenerConnected'] as bool? ?? false,
        notificationAccess: map['notificationAccess'] as bool? ?? false,
        listenerDisconnectedSince: _time(map['listenerDisconnectedSince']),
        silencingSuspended: map['silencingSuspended'] as bool? ?? false,
        lastHealthCheckAt: _time(map['lastHealthCheckAt']),
        batteryOptimizationIgnored: map['batteryOptimizationIgnored'] as bool? ?? false,
      );
}
