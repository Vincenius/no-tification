class CapturedMessage {
  const CapturedMessage({this.sender, required this.text, required this.timestamp});

  final String? sender;
  final String text;
  final DateTime timestamp;

  factory CapturedMessage.fromMap(Map<Object?, Object?> map) => CapturedMessage(
        sender: map['sender'] as String?,
        text: map['text'] as String,
        timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
      );
}

class CapturedNotification {
  const CapturedNotification({
    required this.id,
    required this.key,
    required this.packageName,
    required this.appLabel,
    this.title,
    this.text,
    this.conversationTitle,
    required this.isGroupConversation,
    this.category,
    required this.postTime,
    required this.messages,
    required this.hasContentIntent,
  });

  final int id;
  final String key;
  final String packageName;
  final String appLabel;
  final String? title;
  final String? text;
  final String? conversationTitle;
  final bool isGroupConversation;
  final String? category;
  final DateTime postTime;
  final List<CapturedMessage> messages;
  final bool hasContentIntent;

  factory CapturedNotification.fromMap(Map<Object?, Object?> map) => CapturedNotification(
        id: map['id'] as int,
        key: map['key'] as String,
        packageName: map['packageName'] as String,
        appLabel: map['appLabel'] as String,
        title: map['title'] as String?,
        text: map['text'] as String?,
        conversationTitle: map['conversationTitle'] as String?,
        isGroupConversation: map['isGroupConversation'] as bool? ?? false,
        category: map['category'] as String?,
        postTime: DateTime.fromMillisecondsSinceEpoch(map['postTime'] as int),
        messages: (map['messages'] as List<Object?>? ?? const [])
            .map((m) => CapturedMessage.fromMap(m as Map<Object?, Object?>))
            .toList(),
        hasContentIntent: map['hasContentIntent'] as bool? ?? false,
      );
}
