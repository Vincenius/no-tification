import 'package:calm/models/entry.dart';
import 'package:calm/models/schedule.dart';
import 'package:calm/util/format.dart';
import 'package:flutter_test/flutter_test.dart';

Entry _entry(int id, String pkg, String app, String? conversation, int minute, {String text = 'hi'}) => Entry.fromMap({
      'id': id,
      'packageName': pkg,
      'appLabel': app,
      'title': conversation,
      'text': text,
      'conversationTitle': conversation,
      'isGroup': false,
      'postTime': DateTime(2026, 10, 26, 9, minute).millisecondsSinceEpoch,
      'receivedAt': DateTime(2026, 10, 26, 9, minute).millisecondsSinceEpoch,
      'urgent': false,
      'reason': 'r',
      'decisionSource': 'RULE',
      'decisionStep': 'ALWAYS_DIGEST',
      'delivered': false,
      'seen': false,
    });

void main() {
  group('DigestGroup.build', () {
    test('groups by app then conversation, newest first', () {
      final groups = DigestGroup.build([
        _entry(1, 'com.whatsapp', 'WhatsApp', 'Family', 1),
        _entry(2, 'com.instagram', 'Instagram', null, 5),
        _entry(3, 'com.whatsapp', 'WhatsApp', 'Anna', 3),
        _entry(4, 'com.whatsapp', 'WhatsApp', 'Family', 4),
      ]);

      expect(groups.map((g) => g.appLabel), ['Instagram', 'WhatsApp']);
      final whatsapp = groups[1];
      expect(whatsapp.count, 3);
      expect(whatsapp.conversations.map((c) => c.$1), ['Family', 'Anna']);
      expect(whatsapp.conversations.first.$2.map((e) => e.id), [4, 1]);
      // Without a conversation, entries fall back to the app name.
      expect(groups[0].conversations.single.$1, 'Instagram');
    });

    test('empty input', () => expect(DigestGroup.build([]), isEmpty));
  });

  test('Schedule.fromMap', () {
    final s = Schedule.fromMap({
      'mode': 'WEEKDAYS_WEEKENDS',
      'groups': ['WEEKDAYS', 'WEEKENDS'],
      'times': [
        {'id': 1, 'group': 'WEEKDAYS', 'minute': 540, 'enabled': true},
        {'id': 2, 'group': 'WEEKENDS', 'minute': 600, 'enabled': false},
      ],
      'warnings': [
        {
          'group': 'WEEKDAYS',
          'pairs': [
            [540, 550],
          ],
        },
      ],
      'upcoming': [DateTime(2026, 10, 27, 9).millisecondsSinceEpoch],
      'exactAlarms': false,
    });
    expect(s.mode, ScheduleMode.weekdaysWeekends);
    expect(s.timesIn('WEEKENDS').single.enabled, isFalse);
    expect(s.warnings['WEEKDAYS'], [(540, 550)]);
    expect(s.upcoming.single, DateTime(2026, 10, 27, 9));
    expect(s.exactAlarms, isFalse);
  });

  group('format', () {
    final now = DateTime(2026, 10, 26, 12);

    test('minutes', () {
      expect(formatMinute(0), '00:00');
      expect(formatMinute(17 * 60 + 5), '17:05');
    });

    test('relative days', () {
      expect(formatDayTime(DateTime(2026, 10, 26, 17), now: now), 'today 17:00');
      expect(formatDayTime(DateTime(2026, 10, 27, 9), now: now), 'tomorrow 09:00');
      expect(formatDay(DateTime(2026, 10, 29), now: now), 'Thu');
      expect(formatDay(DateTime(2026, 11, 12), now: now), '12.11.');
    });

    test('tomorrow stays tomorrow across a DST change', () {
      // The night of 2026-10-24 → 25 has 25 hours in Europe/Berlin.
      expect(formatDay(DateTime(2026, 10, 25, 0, 30), now: DateTime(2026, 10, 24, 23, 30)), 'tomorrow');
      expect(formatDay(DateTime(2026, 3, 29, 9), now: DateTime(2026, 3, 28, 22)), 'tomorrow');
    });
  });
}
