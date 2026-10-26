import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/native_status.dart';

/// One permission the app needs, with a plain-language reason.
class PermissionStep {
  const PermissionStep({
    required this.title,
    required this.explanation,
    required this.granted,
    required this.onGrant,
    this.required = true,
  });

  final String title;
  final String explanation;
  final bool granted;
  final bool required;
  final Future<void> Function() onGrant;
}

class OnboardingScreen extends StatelessWidget {
  const OnboardingScreen({super.key, required this.status, required this.onRefresh});

  final NativeStatus status;
  final Future<void> Function() onRefresh;

  List<PermissionStep> get _steps => [
        PermissionStep(
          title: 'Notification access',
          explanation: 'Calm reads incoming notifications so it can decide which ones are urgent. '
              'Everything stays on this phone. Android will show a strong warning, which is '
              'expected for this kind of app.',
          granted: status.notificationAccess,
          onGrant: NativeBridge.instance.openNotificationAccessSettings,
        ),
      ];

  @override
  Widget build(BuildContext context) {
    final steps = _steps;
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
          children: [
            Text('Welcome to Calm', style: theme.textTheme.headlineMedium),
            const SizedBox(height: 8),
            Text(
              'Urgent notifications come through right away. Everything else waits for your '
              'next digest. Calm needs a few permissions to do that.',
              style: theme.textTheme.bodyLarge,
            ),
            const SizedBox(height: 24),
            for (final (i, step) in steps.indexed) _StepCard(index: i + 1, step: step),
            const SizedBox(height: 16),
            TextButton.icon(
              onPressed: onRefresh,
              icon: const Icon(Icons.refresh),
              label: const Text('Check again'),
            ),
          ],
        ),
      ),
    );
  }
}

class _StepCard extends StatelessWidget {
  const _StepCard({required this.index, required this.step});

  final int index;
  final PermissionStep step;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 14,
                  backgroundColor: step.granted ? scheme.primary : scheme.surfaceContainerHighest,
                  foregroundColor: step.granted ? scheme.onPrimary : scheme.onSurfaceVariant,
                  child: step.granted
                      ? const Icon(Icons.check, size: 16)
                      : Text('$index', style: const TextStyle(fontSize: 13)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(step.title, style: Theme.of(context).textTheme.titleMedium),
                ),
                if (!step.required) const Chip(label: Text('Optional')),
              ],
            ),
            const SizedBox(height: 8),
            Text(step.explanation),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: step.granted
                  ? Text('Granted', style: TextStyle(color: scheme.primary))
                  : FilledButton(onPressed: step.onGrant, child: const Text('Grant')),
            ),
          ],
        ),
      ),
    );
  }
}
