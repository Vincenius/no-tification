import 'package:calm/models/ai_settings.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('AiSettings never needs the key itself', () {
    final s = AiSettings.fromMap({
      'hasKey': true,
      'keyHint': 'a1B2',
      'enabled': true,
      'model': 'claude-haiku-5-5',
      'defaultModel': 'claude-haiku-5-5',
      'active': true,
    });
    expect(s.hasKey, isTrue);
    expect(s.keyHint, 'a1B2');
    expect(s.active, isTrue);
  });

  test('TestResult defaults fail open', () {
    final r = TestResult.fromMap(const {});
    expect(r.urgent, isTrue);
    expect(r.decisionSource, 'FALLBACK');
  });
}
