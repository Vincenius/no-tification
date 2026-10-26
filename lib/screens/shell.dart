import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import 'digest_screen.dart';
import 'home_screen.dart';
import 'log_screen.dart';
import 'rules_screen.dart';

/// Bottom navigation between the main screens. Tabs stay alive so they keep their scroll position.
class Shell extends StatefulWidget {
  const Shell({super.key});

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  static const _digestTab = 1;

  int _tab = 0;
  StreamSubscription<String>? _routes;

  @override
  void initState() {
    super.initState();
    _routes = NativeBridge.instance.routes.listen(_openRoute);
    NativeBridge.instance.takeLaunchRoute().then((r) {
      if (r != null) _openRoute(r);
    });
  }

  @override
  void dispose() {
    _routes?.cancel();
    super.dispose();
  }

  void _openRoute(String route) {
    if (!mounted) return;
    Navigator.of(context).popUntil((r) => r.isFirst);
    if (route == 'digest') setState(() => _tab = _digestTab);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _tab,
        children: [
          HomeScreen(onOpenDigest: () => setState(() => _tab = _digestTab)),
          const DigestScreen(),
          const LogScreen(),
          const RulesScreen(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.inbox_outlined), selectedIcon: Icon(Icons.inbox), label: 'Digest'),
          NavigationDestination(icon: Icon(Icons.history), label: 'Log'),
          NavigationDestination(icon: Icon(Icons.rule_outlined), selectedIcon: Icon(Icons.rule), label: 'Rules'),
        ],
      ),
    );
  }
}
