enum ScheduleMode {
  everyDay('EVERY_DAY', 'Every day'),
  weekdaysWeekends('WEEKDAYS_WEEKENDS', 'Weekdays / weekends'),
  perDay('PER_DAY', 'Per day');

  const ScheduleMode(this.id, this.label);

  final String id;
  final String label;

  static ScheduleMode fromId(String id) => values.firstWhere((m) => m.id == id, orElse: () => everyDay);
}

const groupLabels = {
  'ALL': 'Every day',
  'WEEKDAYS': 'Mon–Fri',
  'WEEKENDS': 'Sat–Sun',
  'MON': 'Mon',
  'TUE': 'Tue',
  'WED': 'Wed',
  'THU': 'Thu',
  'FRI': 'Fri',
  'SAT': 'Sat',
  'SUN': 'Sun',
};

class DigestTimeItem {
  const DigestTimeItem({required this.id, required this.group, required this.minute, required this.enabled});

  final int id;
  final String group;

  /// Minute of day, 0–1439.
  final int minute;
  final bool enabled;

  factory DigestTimeItem.fromMap(Map<Object?, Object?> map) => DigestTimeItem(
        id: map['id'] as int,
        group: map['group'] as String,
        minute: map['minute'] as int,
        enabled: map['enabled'] as bool? ?? true,
      );
}

class Schedule {
  const Schedule({
    required this.mode,
    required this.groups,
    required this.times,
    required this.warnings,
    required this.upcoming,
    required this.exactAlarms,
  });

  final ScheduleMode mode;

  /// Groups of the current mode, in display order.
  final List<String> groups;
  final List<DigestTimeItem> times;

  /// Group → pairs of times (minutes) closer than 15 minutes.
  final Map<String, List<(int, int)>> warnings;
  final List<DateTime> upcoming;
  final bool exactAlarms;

  List<DigestTimeItem> timesIn(String group) => times.where((t) => t.group == group).toList();

  factory Schedule.fromMap(Map<Object?, Object?> map) => Schedule(
        mode: ScheduleMode.fromId(map['mode'] as String),
        groups: (map['groups'] as List<Object?>).cast<String>(),
        times: (map['times'] as List<Object?>).map((t) => DigestTimeItem.fromMap(t as Map<Object?, Object?>)).toList(),
        warnings: {
          for (final w in (map['warnings'] as List<Object?>).cast<Map<Object?, Object?>>())
            w['group'] as String: [
              for (final p in (w['pairs'] as List<Object?>).cast<List<Object?>>()) (p[0] as int, p[1] as int),
            ],
        },
        upcoming: (map['upcoming'] as List<Object?>)
            .map((ms) => DateTime.fromMillisecondsSinceEpoch(ms as int))
            .toList(),
        exactAlarms: map['exactAlarms'] as bool? ?? true,
      );
}
