class AiSettings {
  const AiSettings({
    required this.hasKey,
    this.keyHint,
    required this.enabled,
    required this.model,
    required this.defaultModel,
    required this.active,
  });

  final bool hasKey;

  /// Last characters of the stored key; the key itself never reaches Flutter.
  final String? keyHint;
  final bool enabled;
  final String model;
  final String defaultModel;

  /// Enabled and a key is stored.
  final bool active;

  factory AiSettings.fromMap(Map<Object?, Object?> map) => AiSettings(
        hasKey: map['hasKey'] as bool? ?? false,
        keyHint: map['keyHint'] as String?,
        enabled: map['enabled'] as bool? ?? true,
        model: map['model'] as String? ?? '',
        defaultModel: map['defaultModel'] as String? ?? '',
        active: map['active'] as bool? ?? false,
      );
}

class TestResult {
  const TestResult({
    required this.urgent,
    required this.reason,
    required this.decisionSource,
    required this.decisionStep,
    required this.millis,
  });

  final bool urgent;
  final String reason;
  final String decisionSource;
  final String decisionStep;
  final int millis;

  factory TestResult.fromMap(Map<Object?, Object?> map) => TestResult(
        urgent: map['urgent'] as bool? ?? true,
        reason: map['reason'] as String? ?? '',
        decisionSource: map['decisionSource'] as String? ?? 'FALLBACK',
        decisionStep: map['decisionStep'] as String? ?? 'FALLBACK',
        millis: map['millis'] as int? ?? 0,
      );
}
