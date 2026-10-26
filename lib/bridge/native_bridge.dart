import 'package:flutter/services.dart';

import '../models/captured_notification.dart';
import '../models/native_status.dart';

/// Typed wrapper around the platform channels exposed by `NativeBridge.kt`.
class NativeBridge {
  NativeBridge._();

  static final instance = NativeBridge._();

  static const _methods = MethodChannel('com.vincentwill.calm/native');
  static const _events = EventChannel('com.vincentwill.calm/events');

  late final Stream<Map<Object?, Object?>> _eventStream =
      _events.receiveBroadcastStream().cast<Map<Object?, Object?>>();

  Stream<CapturedNotification> get notifications => _eventStream
      .where((e) => e['type'] == 'notification')
      .map((e) => CapturedNotification.fromMap(e['data'] as Map<Object?, Object?>));

  Stream<NativeStatus> get statusChanges => _eventStream
      .where((e) => e['type'] == 'status')
      .map((e) => NativeStatus.fromMap(e['data'] as Map<Object?, Object?>));

  Future<NativeStatus> getStatus() async {
    final map = await _methods.invokeMapMethod<Object?, Object?>('getStatus');
    return map == null ? NativeStatus.unknown : NativeStatus.fromMap(map);
  }

  Future<void> openNotificationAccessSettings() =>
      _methods.invokeMethod('openNotificationAccessSettings');

  /// Newest first.
  Future<List<CapturedNotification>> getLog() async {
    final list = await _methods.invokeListMethod<Object?>('getLog') ?? const [];
    return list.map((e) => CapturedNotification.fromMap(e as Map<Object?, Object?>)).toList();
  }

  Future<void> clearLog() => _methods.invokeMethod('clearLog');

  /// Opens the original notification target, or the source app as fallback.
  Future<bool> openNotification(CapturedNotification n) async =>
      await _methods.invokeMethod<bool>(
        'openNotification',
        {'key': n.key, 'packageName': n.packageName},
      ) ??
      false;
}
