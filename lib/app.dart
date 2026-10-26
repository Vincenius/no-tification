import 'package:flutter/material.dart';

import 'bridge/native_bridge.dart';
import 'models/native_status.dart';
import 'screens/log_screen.dart';
import 'screens/onboarding_screen.dart';

class CalmApp extends StatelessWidget {
  const CalmApp({super.key});

  static const _seed = Color(0xFF4F6D7A);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Calm',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.light),
      darkTheme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.dark),
      home: const _RootGate(),
    );
  }
}

/// Shows onboarding until notification access is granted, then the main UI.
class _RootGate extends StatefulWidget {
  const _RootGate();

  @override
  State<_RootGate> createState() => _RootGateState();
}

class _RootGateState extends State<_RootGate> with WidgetsBindingObserver {
  NativeStatus? _status;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Permissions are granted in system settings, so re-check whenever we come back.
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final status = await NativeBridge.instance.getStatus();
    if (mounted) setState(() => _status = status);
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    if (status == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!status.notificationAccess) {
      return OnboardingScreen(status: status, onRefresh: _refresh);
    }
    return const LogScreen();
  }
}
