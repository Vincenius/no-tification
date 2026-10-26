/// Mirrors `RuleKind`, `MatchType` and `RuleAction` in Kotlin by name.
class RuleItem {
  const RuleItem({
    required this.id,
    required this.kind,
    required this.matchType,
    required this.pattern,
    this.packageName,
    required this.action,
    required this.description,
  });

  final int id;

  /// VIP, CUSTOM, DIGEST_APP or MUTED_GROUP.
  final String kind;

  /// APP, CONVERSATION, SENDER or KEYWORD.
  final String matchType;
  final String pattern;
  final String? packageName;

  /// URGENT or DIGEST.
  final String action;
  final String description;

  factory RuleItem.fromMap(Map<Object?, Object?> map) => RuleItem(
        id: map['id'] as int,
        kind: map['kind'] as String,
        matchType: map['matchType'] as String,
        pattern: map['pattern'] as String,
        packageName: map['packageName'] as String?,
        action: map['action'] as String,
        description: map['description'] as String? ?? '',
      );
}

class AppSeen {
  const AppSeen({required this.packageName, required this.appLabel, required this.count});

  final String packageName;
  final String appLabel;
  final int count;

  factory AppSeen.fromMap(Map<Object?, Object?> map) => AppSeen(
        packageName: map['packageName'] as String,
        appLabel: map['appLabel'] as String,
        count: map['count'] as int? ?? 0,
      );
}
