import 'package:flutter/services.dart';

import '../models/ai_settings.dart';
import '../models/entry.dart';
import '../models/feedback.dart';
import '../models/native_status.dart';
import '../models/rule.dart';
import '../models/schedule.dart';

/// Typed wrapper around the platform channels exposed by `NativeBridge.kt`.
class NativeBridge {
  NativeBridge._();

  static final instance = NativeBridge._();

  static const _methods = MethodChannel('com.vincentwill.calm/native');
  static const _events = EventChannel('com.vincentwill.calm/events');

  late final Stream<Map<Object?, Object?>> _eventStream =
      _events.receiveBroadcastStream().cast<Map<Object?, Object?>>();

  /// Something changed natively (new notification, digest delivered, schedule edited…).
  Stream<void> get changes => _eventStream.where((e) => e['type'] == 'changed');

  /// A notification asked to open a screen ("digest") while the app was running.
  Stream<String> get routes => _eventStream.where((e) => e['type'] == 'route').map((e) => e['data'] as String);

  Future<T?> _call<T>(String method, [Map<String, Object?>? args]) => _methods.invokeMethod<T>(method, args);

  Future<List<Map<Object?, Object?>>> _list(String method, [Map<String, Object?>? args]) async =>
      (await _methods.invokeListMethod<Object?>(method, args) ?? const []).cast<Map<Object?, Object?>>();

  // Status & permissions

  Future<NativeStatus> getStatus() async =>
      NativeStatus.fromMap(await _methods.invokeMapMethod<Object?, Object?>('getStatus') ?? const {});

  /// Returns the resulting state; false if DND access is missing.
  Future<bool> setEnabled(bool enabled) async => await _call<bool>('setEnabled', {'enabled': enabled}) ?? false;

  Future<void> openNotificationAccessSettings() => _call('openNotificationAccessSettings');
  Future<void> openDndAccessSettings() => _call('openDndAccessSettings');
  Future<void> openExactAlarmSettings() => _call('openExactAlarmSettings');
  Future<void> openUrgentChannelSettings() => _call('openUrgentChannelSettings');
  Future<void> openAppNotificationSettings() => _call('openAppNotificationSettings');
  Future<void> requestPostNotifications() => _call('requestPostNotifications');

  /// The screen the app was launched for, if any. Consumed on read.
  Future<String?> takeLaunchRoute() => _call<String>('takeLaunchRoute');

  // History & digest

  Future<List<Entry>> getHistory({int limit = 300}) async =>
      (await _list('getHistory', {'limit': limit})).map(Entry.fromMap).toList();

  Future<List<Entry>> getDigest() async => (await _list('getDigest')).map(Entry.fromMap).toList();

  Future<void> markSeen(List<int> ids) => _call('markSeen', {'ids': ids});

  /// Posts the digest summary now. Returns the number of newly released items.
  Future<int> deliverNow() async => await _call<int>('deliverNow') ?? 0;

  /// Opens the original chat, or the source app as fallback.
  Future<bool> openEntry(Entry e) async => await _call<bool>('openEntry', {'id': e.id}) ?? false;

  // Schedule

  Future<Schedule> getSchedule() async =>
      Schedule.fromMap(await _methods.invokeMapMethod<Object?, Object?>('getSchedule') ?? const {});

  Future<void> setScheduleMode(ScheduleMode mode) => _call('setScheduleMode', {'mode': mode.id});

  Future<void> addDigestTime(String group, int minute) => _call('addDigestTime', {'group': group, 'minute': minute});

  Future<void> updateDigestTime(int id, {required int minute, required bool enabled}) =>
      _call('updateDigestTime', {'id': id, 'minute': minute, 'enabled': enabled});

  Future<void> deleteDigestTime(int id) => _call('deleteDigestTime', {'id': id});

  Future<void> generateDigestTimes(String group, {required int start, required int end, required int interval}) =>
      _call('generateDigestTimes', {'group': group, 'start': start, 'end': end, 'interval': interval});

  // Rules

  Future<List<RuleItem>> getRules() async => (await _list('getRules')).map(RuleItem.fromMap).toList();

  Future<void> addRule({
    required String kind,
    required String matchType,
    required String pattern,
    String? packageName,
    required String action,
  }) =>
      _call('addRule', {
        'kind': kind,
        'matchType': matchType,
        'pattern': pattern,
        'packageName': packageName,
        'action': action,
      });

  Future<void> deleteRule(int id) => _call('deleteRule', {'id': id});

  Future<List<AppSeen>> getAppsSeen() async => (await _list('getAppsSeen')).map(AppSeen.fromMap).toList();

  // AI

  Future<AiSettings> _aiCall(String method, [Map<String, Object?>? args]) async =>
      AiSettings.fromMap(await _methods.invokeMapMethod<Object?, Object?>(method, args) ?? const {});

  Future<AiSettings> getAiSettings() => _aiCall('getAiSettings');

  /// Null or empty removes the key.
  Future<AiSettings> setApiKey(String? key) => _aiCall('setApiKey', {'key': key});

  Future<AiSettings> setAiEnabled(bool enabled) => _aiCall('setAiEnabled', {'enabled': enabled});

  /// Empty resets to the default model.
  Future<AiSettings> setAiModel(String model) => _aiCall('setAiModel', {'model': model});

  /// Runs rules and AI on a made-up message, without storing anything.
  Future<TestResult> testClassification({
    required String text,
    String? appLabel,
    String? sender,
    String? conversation,
    bool isGroup = false,
  }) async =>
      TestResult.fromMap(await _methods.invokeMapMethod<Object?, Object?>('testClassification', {
            'text': text,
            'appLabel': appLabel,
            'sender': sender,
            'conversation': conversation,
            'isGroup': isGroup,
          }) ??
          const {});

  // Feedback & stats

  Future<FeedbackOptions?> getFeedback(Entry e) async {
    final map = await _methods.invokeMapMethod<Object?, Object?>('getFeedback', {'id': e.id});
    return map == null ? null : FeedbackOptions.fromMap(map);
  }

  /// Marks the entry as wrongly decided and applies the chosen suggestion (if any).
  /// Returns the applied suggestion's label.
  Future<String?> applyFeedback(Entry e, {String? suggestion}) =>
      _call<String>('applyFeedback', {'id': e.id, 'suggestion': suggestion});

  Future<Stats> getStats({int days = 7}) async =>
      Stats.fromMap(await _methods.invokeMapMethod<Object?, Object?>('getStats', {'days': days}) ?? const {});

  // Maintenance & health

  Future<Maintenance> _maintenance(String method, [Map<String, Object?>? args]) async =>
      Maintenance.fromMap(await _methods.invokeMapMethod<Object?, Object?>(method, args) ?? const {});

  Future<Maintenance> getMaintenance() => _maintenance('getMaintenance');

  Future<Maintenance> setRetentionDays(int days) => _maintenance('setRetentionDays', {'days': days});

  /// Forces Android to rebind the notification listener.
  Future<Maintenance> reconnectListener() => _maintenance('reconnectListener');

  Future<void> openBatteryOptimizationSettings() => _call('openBatteryOptimizationSettings');
}
