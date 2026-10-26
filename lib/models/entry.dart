/// One stored notification (or chat message) with its decision.
class Entry {
  const Entry({
    required this.id,
    required this.packageName,
    required this.appLabel,
    this.title,
    required this.text,
    this.conversationTitle,
    this.sender,
    required this.isGroup,
    this.category,
    required this.postTime,
    required this.receivedAt,
    required this.urgent,
    required this.reason,
    required this.decisionSource,
    required this.decisionStep,
    required this.delivered,
    required this.seen,
    this.feedback,
  });

  final int id;
  final String packageName;
  final String appLabel;
  final String? title;
  final String text;
  final String? conversationTitle;
  final String? sender;
  final bool isGroup;
  final String? category;
  final DateTime postTime;
  final DateTime receivedAt;
  final bool urgent;
  final String reason;

  /// RULE, AI or FALLBACK.
  final String decisionSource;

  /// ALWAYS_URGENT, CUSTOM_RULE, ALWAYS_DIGEST, AI or FALLBACK.
  final String decisionStep;
  final bool delivered;
  final bool seen;

  /// SHOULD_BE_URGENT / SHOULD_NOT_BE_URGENT once the user corrected the decision.
  final String? feedback;

  /// Who or what this is about, for grouping and display.
  String get conversation => conversationTitle ?? title ?? appLabel;

  String get displayText => isGroup && sender != null ? '$sender: $text' : text;

  factory Entry.fromMap(Map<Object?, Object?> map) => Entry(
        id: map['id'] as int,
        packageName: map['packageName'] as String,
        appLabel: map['appLabel'] as String,
        title: map['title'] as String?,
        text: map['text'] as String? ?? '',
        conversationTitle: map['conversationTitle'] as String?,
        sender: map['sender'] as String?,
        isGroup: map['isGroup'] as bool? ?? false,
        category: map['category'] as String?,
        postTime: DateTime.fromMillisecondsSinceEpoch(map['postTime'] as int),
        receivedAt: DateTime.fromMillisecondsSinceEpoch(map['receivedAt'] as int),
        urgent: map['urgent'] as bool? ?? true,
        reason: map['reason'] as String? ?? '',
        decisionSource: map['decisionSource'] as String? ?? 'FALLBACK',
        decisionStep: map['decisionStep'] as String? ?? 'FALLBACK',
        delivered: map['delivered'] as bool? ?? false,
        seen: map['seen'] as bool? ?? false,
        feedback: map['feedback'] as String?,
      );
}

/// Digest grouping: app → conversation → entries, everything newest first.
class DigestGroup {
  DigestGroup(this.appLabel, this.conversations);

  final String appLabel;
  final List<(String, List<Entry>)> conversations;

  int get count => conversations.fold(0, (sum, c) => sum + c.$2.length);

  static List<DigestGroup> build(List<Entry> entries) {
    final sorted = [...entries]..sort((a, b) => b.postTime.compareTo(a.postTime));
    final apps = <String, Map<String, List<Entry>>>{};
    for (final e in sorted) {
      apps.putIfAbsent(e.packageName, () => {}).putIfAbsent(e.conversation, () => []).add(e);
    }
    // Map iteration keeps insertion order, i.e. by each app's / conversation's newest entry.
    return [
      for (final convs in apps.values)
        DigestGroup(
          convs.values.first.first.appLabel,
          [for (final c in convs.entries) (c.key, c.value)],
        ),
    ];
  }
}
