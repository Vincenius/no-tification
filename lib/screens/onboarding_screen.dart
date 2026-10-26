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
    this.available = true,
    this.unavailableHint,
  });

  final String title;
  final String explanation;
  final bool granted;
  final bool required;

  /// False while an earlier step it depends on is missing.
  final bool available;
  final String? unavailableHint;
  final Future<void> Function() onGrant;
}

List<PermissionStep> permissionSteps(NativeStatus status, {required VoidCallback onPostNotificationsTapped}) {
  final bridge = NativeBridge.instance;
  return [
    PermissionStep(
      title: 'Notification access',
      explanation: 'no-tification reads incoming notifications to decide which ones are urgent. Everything '
          'stays on this phone. Android shows a strong warning here. That is expected for this kind of app.',
      granted: status.notificationAccess,
      onGrant: bridge.openNotificationAccessSettings,
    ),
    PermissionStep(
      title: 'Post notifications',
      explanation: 'no-tification shows urgent notifications itself, plus one summary at each digest time.',
      granted: status.postNotifications,
      onGrant: () async => onPostNotificationsTapped(),
    ),
    PermissionStep(
      title: 'Do Not Disturb access',
      explanation: 'Android plays the sound before no-tification can react. So while no-tification is on, it runs its own '
          'Do Not Disturb mode: calls, alarms and no-tification itself still come through, everything else is '
          'quiet until no-tification decides. Turning no-tification off restores your previous setup.',
      granted: status.dndAccess,
      onGrant: bridge.openDndAccessSettings,
    ),
    PermissionStep(
      title: 'Let no-tification through Do Not Disturb',
      explanation: 'Urgent notifications are shown on no-tification\'s "Urgent" channel, which must be allowed to '
          'override Do Not Disturb. If the switch below shows as not granted, turn on "Override Do Not '
          'Disturb" for the Urgent channel.',
      granted: status.urgentBypassesDnd,
      required: false,
      available: status.dndAccess,
      unavailableHint: 'Grant Do Not Disturb access first.',
      onGrant: bridge.openUrgentChannelSettings,
    ),
    PermissionStep(
      title: 'Battery optimization',
      explanation: 'Some phones stop background apps to save battery. Exempting no-tification keeps it listening '
          'and its digests on time. no-tification only does work when a notification arrives.',
      granted: status.batteryOptimizationIgnored,
      required: false,
      onGrant: bridge.openBatteryOptimizationSettings,
    ),
    PermissionStep(
      title: 'Exact alarms',
      explanation: 'Delivers digests exactly on time. Without it, Android may deliver them up to 10 minutes late.',
      granted: status.exactAlarms,
      required: false,
      onGrant: bridge.openExactAlarmSettings,
    ),
  ];
}

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.status, required this.onRefresh, this.onSkip, this.onDone});

  final NativeStatus status;
  final Future<void> Function() onRefresh;

  /// Shown as "Skip for now" when non-null.
  final VoidCallback? onSkip;

  /// Shown as a close button when opened from the home screen.
  final VoidCallback? onDone;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  bool _askedPostNotifications = false;

  /// Android only shows the runtime dialog a limited number of times; after that, go to settings.
  void _onPostNotifications() {
    if (_askedPostNotifications || widget.status.sdkInt < 33) {
      NativeBridge.instance.openAppNotificationSettings();
    } else {
      NativeBridge.instance.requestPostNotifications();
      _askedPostNotifications = true;
    }
  }

  @override
  Widget build(BuildContext context) {
    final steps = permissionSteps(widget.status, onPostNotificationsTapped: _onPostNotifications);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: widget.onDone == null
          ? null
          : AppBar(title: const Text('Setup'), leading: CloseButton(onPressed: widget.onDone)),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: widget.onRefresh,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 32, 16, 24),
            children: [
              if (widget.onDone == null) ...[
                Text('Welcome to no-tification', style: theme.textTheme.headlineMedium),
                const SizedBox(height: 8),
                Text(
                  'Urgent notifications come through right away. Everything else waits for your next digest. '
                  'no-tification needs a few permissions to do that.',
                  style: theme.textTheme.bodyLarge,
                ),
                const SizedBox(height: 24),
              ],
              for (final (i, step) in steps.indexed) _StepCard(index: i + 1, step: step),
              const SizedBox(height: 8),
              Row(
                children: [
                  TextButton.icon(
                    onPressed: widget.onRefresh,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Check again'),
                  ),
                  const Spacer(),
                  if (widget.onSkip != null) TextButton(onPressed: widget.onSkip, child: const Text('Skip for now')),
                ],
              ),
            ],
          ),
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
                Expanded(child: Text(step.title, style: Theme.of(context).textTheme.titleMedium)),
                if (!step.required)
                  Text('Recommended', style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.outline)),
              ],
            ),
            const SizedBox(height: 8),
            Text(step.explanation),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: switch ((step.granted, step.available)) {
                (true, _) => Text('Granted', style: TextStyle(color: scheme.primary)),
                (false, false) => Text(step.unavailableHint ?? '', style: TextStyle(color: scheme.outline)),
                (false, true) => FilledButton(onPressed: step.onGrant, child: const Text('Grant')),
              },
            ),
          ],
        ),
      ),
    );
  }
}
