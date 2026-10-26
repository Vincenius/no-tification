import 'package:calm/models/entry.dart';
import 'package:calm/models/feedback.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('FeedbackOptions with suggestions and note', () {
    final o = FeedbackOptions.fromMap({
      'feedback': 'SHOULD_NOT_BE_URGENT',
      'note': null,
      'suggestions': [
        {'id': 'mute-group', 'label': 'Always hold "Nachbarn"', 'detail': 'Mute this group'},
      ],
    });
    expect(o.feedback, 'SHOULD_NOT_BE_URGENT');
    expect(o.suggestions.single.id, 'mute-group');
    expect(o.note, isNull);
  });

  test('Stats parses sources and top apps', () {
    final s = Stats.fromMap({
      'days': 7,
      'passed': 12,
      'held': 30,
      'bySource': {'RULE': 20, 'AI': 18, 'FALLBACK': 4},
      'topHeld': [
        {'appLabel': 'Instagram', 'count': 14},
      ],
      'topPassed': const [],
      'shouldHaveBeenUrgent': 1,
      'shouldNotHaveBeenUrgent': 2,
    });
    expect(s.total, 42);
    expect(s.bySource['AI'], 18);
    expect(s.topHeld.single.appLabel, 'Instagram');
  });

  test('Maintenance with missing timestamps', () {
    final m = Maintenance.fromMap({'retentionDays': 14, 'listenerConnected': true, 'notificationAccess': true});
    expect(m.retentionDays, 14);
    expect(m.lastCleanupAt, isNull);
    expect(m.silencingSuspended, isFalse);
  });

  test('Entry carries feedback', () {
    final e = Entry.fromMap({
      'id': 1, 'packageName': 'p', 'appLabel': 'P', 'text': 't', 'postTime': 0, 'receivedAt': 0,
      'feedback': 'SHOULD_BE_URGENT',
    });
    expect(e.feedback, 'SHOULD_BE_URGENT');
  });
}
