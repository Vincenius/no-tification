import 'package:flutter/material.dart';

import 'bridge/native_bridge.dart';
import 'models/native_status.dart';
import 'screens/onboarding_screen.dart';
import 'screens/shell.dart';
import 'widgets/auto_refresh.dart';

class CalmApp extends StatelessWidget {
  const CalmApp({super.key});

  static const _seed = Color(0xFF4F6D7A);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'no-tification',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.light),
      darkTheme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.dark),
      // Digest times are 24 h everywhere in the app.
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
        child: child!,
      ),
      home: const _RootGate(),
    );
  }
}

/// Shows onboarding until the required permissions are granted (or skipped), then the main UI.
class _RootGate extends StatefulWidget {
  const _RootGate();

  @override
  State<_RootGate> createState() => _RootGateState();
}

class _RootGateState extends State<_RootGate> with AutoRefresh {
  NativeStatus? _status;
  bool _skipped = false;

  @override
  Future<void> reload() async {
    final status = await NativeBridge.instance.getStatus();
    if (mounted) setState(() => _status = status);
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    if (status == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!status.requiredGranted && !_skipped) {
      return OnboardingScreen(
        status: status,
        onRefresh: reload,
        onSkip: status.notificationAccess ? () => setState(() => _skipped = true) : null,
      );
    }
    return const Shell();
  }
}
