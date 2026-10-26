import 'package:calm/models/captured_notification.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses a MessagingStyle notification map from the native side', () {
    final n = CapturedNotification.fromMap({
      'id': 7,
      'key': '0|com.whatsapp|1|null|10123',
      'packageName': 'com.whatsapp',
      'appLabel': 'WhatsApp',
      'title': 'Family',
      'text': '2 new messages',
      'conversationTitle': 'Family',
      'isGroupConversation': true,
      'category': 'msg',
      'postTime': 1761470000000,
      'messages': [
        {'sender': 'Anna', 'text': 'Hi', 'timestamp': 1761469990000},
        {'sender': null, 'text': 'Dinner at 6?', 'timestamp': 1761470000000},
      ],
      'hasContentIntent': true,
    });

    expect(n.appLabel, 'WhatsApp');
    expect(n.isGroupConversation, isTrue);
    expect(n.messages, hasLength(2));
    expect(n.messages.first.sender, 'Anna');
    expect(n.messages.last.sender, isNull);
    expect(n.postTime.millisecondsSinceEpoch, 1761470000000);
  });

  test('tolerates missing optional fields', () {
    final n = CapturedNotification.fromMap({
      'id': 1,
      'key': 'k',
      'packageName': 'p',
      'appLabel': 'P',
      'postTime': 0,
    });
    expect(n.messages, isEmpty);
    expect(n.isGroupConversation, isFalse);
    expect(n.hasContentIntent, isFalse);
  });
}
