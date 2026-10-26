import 'dart:async';

import 'package:flutter/widgets.dart';

import '../bridge/native_bridge.dart';

/// Calls [reload] on start, whenever the native side reports a change, and when the app resumes
/// (permissions are granted in system settings). Bursts of changes are coalesced.
mixin AutoRefresh<T extends StatefulWidget> on State<T> {
  StreamSubscription<void>? _changes;
  AppLifecycleListener? _lifecycle;
  Timer? _debounce;

  Future<void> reload();

  @override
  void initState() {
    super.initState();
    _changes = NativeBridge.instance.changes.listen((_) => _schedule());
    _lifecycle = AppLifecycleListener(onResume: _schedule);
    reload();
  }

  void _schedule() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 250), () {
      if (mounted) reload();
    });
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _changes?.cancel();
    _lifecycle?.dispose();
    super.dispose();
  }
}
