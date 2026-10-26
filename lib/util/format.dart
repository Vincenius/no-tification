String _two(int n) => n.toString().padLeft(2, '0');

String formatMinute(int minuteOfDay) => '${_two(minuteOfDay ~/ 60)}:${_two(minuteOfDay % 60)}';

String formatTime(DateTime t) => '${_two(t.hour)}:${_two(t.minute)}';

const _weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/// "today", "tomorrow", "Wed" (within a week) or "12.11.".
String formatDay(DateTime t, {DateTime? now}) {
  final today = _dateOnly(now ?? DateTime.now());
  final days = _dateOnly(t).difference(today).inDays;
  if (days == 0) return 'today';
  if (days == 1) return 'tomorrow';
  if (days == -1) return 'yesterday';
  if (days > 1 && days < 7) return _weekdays[t.weekday - 1];
  return '${_two(t.day)}.${_two(t.month)}.';
}

String formatDayTime(DateTime t, {DateTime? now}) => '${formatDay(t, now: now)} ${formatTime(t)}';

/// UTC so that day differences stay whole across DST changes (local midnights can be 23 h apart).
DateTime _dateOnly(DateTime t) => DateTime.utc(t.year, t.month, t.day);
