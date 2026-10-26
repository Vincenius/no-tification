import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/feedback.dart';
import '../models/native_status.dart';
import '../util/format.dart';
import '../widgets/auto_refresh.dart';
import 'onboarding_screen.dart';
import 'schedule_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.onOpenDigest});

  final VoidCallback onOpenDigest;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with AutoRefresh {
  final _bridge = NativeBridge.instance;
  NativeStatus? _status;
  Stats? _stats;
  bool _busy = false;

  @override
  Future<void> reload() async {
    final (status, stats) = await (_bridge.getStatus(), _bridge.getStats()).wait;
    if (mounted) {
      setState(() {
        _status = status;
        _stats = stats;
      });
    }
  }

  Future<void> _toggle(bool on) async {
    setState(() => _busy = true);
    final result = await _bridge.setEnabled(on);
    if (on && !result && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Calm needs Do Not Disturb access to turn on.')),
      );
    }
    await reload();
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _deliverNow() async {
    final released = await _bridge.deliverNow();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(released == 0 ? 'Nothing new to deliver.' : 'Released $released held notifications.'),
    ));
  }

  void _openSetup() => Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (context) => _SetupPage(onDone: () => Navigator.of(context).pop()),
      ));

  void _openSchedule() =>
      Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const ScheduleScreen()));

  @override
  Widget build(BuildContext context) {
    final status = _status;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Calm'),
        actions: [
          IconButton(
            onPressed: () async {
              await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const SettingsScreen()));
              reload();
            },
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Settings',
          ),
        ],
      ),
      body: status == null
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: reload,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                children: [
                  _MasterSwitch(status: status, busy: _busy, onChanged: _toggle),
                  if (status.enabled && status.silencingSuspended)
                    _Banner(
                      icon: Icons.notifications_paused_outlined,
                      text: 'Silencing is paused: Calm lost its connection to your notifications, so it turned '
                          'Do Not Disturb off to be safe. It resumes on its own once reconnected. Tap to reconnect now.',
                      error: true,
                      onTap: () async {
                        await _bridge.reconnectListener();
                        await reload();
                      },
                    ),
                  if (!status.listenerConnected && status.notificationAccess)
                    _Banner(
                      icon: Icons.link_off,
                      text: 'The notification listener is not connected. If this persists, turn notification access off and on again.',
                      error: true,
                      onTap: _bridge.openNotificationAccessSettings,
                    ),
                  if (!status.requiredGranted || !status.recommendedGranted)
                    _Banner(
                      icon: Icons.checklist,
                      text: status.requiredGranted
                          ? 'Some recommended permissions are missing.'
                          : 'Setup is incomplete. Calm can\'t hold notifications yet.',
                      error: !status.requiredGranted,
                      onTap: _openSetup,
                    ),
                  const SizedBox(height: 8),
                  _NextDigestCard(status: status, onTap: _openSchedule),
                  const SizedBox(height: 8),
                  _HeldCard(status: status, onOpen: widget.onOpenDigest, onDeliverNow: _deliverNow),
                  const SizedBox(height: 8),
                  _TodayCard(status: status),
                  if (_stats != null && _stats!.total > 0) ...[
                    const SizedBox(height: 8),
                    _WeekCard(stats: _stats!),
                  ],
                ],
              ),
            ),
    );
  }
}

class _MasterSwitch extends StatelessWidget {
  const _MasterSwitch({required this.status, required this.busy, required this.onChanged});

  final NativeStatus status;
  final bool busy;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final canEnable = status.notificationAccess && status.dndAccess;
    return Card(
      color: status.enabled ? scheme.primaryContainer : null,
      child: SwitchListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        title: Text(status.enabled ? 'Calm is on' : 'Calm is off', style: Theme.of(context).textTheme.titleLarge),
        subtitle: Text(status.enabled
            ? 'Only urgent notifications come through. Everything else waits for the digest.\n'
                '${status.aiActive ? 'AI judges messages no rule covers.' : 'Rules only: messages no rule covers come through.'}'
            : canEnable
                ? 'Notifications behave as usual.'
                : 'Finish setup to turn Calm on.'),
        value: status.enabled,
        onChanged: busy || (!canEnable && !status.enabled) ? null : onChanged,
      ),
    );
  }
}

class _NextDigestCard extends StatelessWidget {
  const _NextDigestCard({required this.status, required this.onTap});

  final NativeStatus status;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final next = status.nextDigestAt;
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Icon(next == null ? Icons.warning_amber : Icons.schedule, color: next == null ? scheme.error : null),
        title: Text(next == null ? 'No digests scheduled' : 'Next digest ${formatDayTime(next)}'),
        subtitle: Text(next == null
            ? 'Held notifications wait until you open the digest yourself.'
            : status.exactAlarms
                ? 'Tap to edit the schedule'
                : 'May arrive up to 10 min late (exact alarms not allowed). Tap to edit.'),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}

class _HeldCard extends StatelessWidget {
  const _HeldCard({required this.status, required this.onOpen, required this.onDeliverNow});

  final NativeStatus status;
  final VoidCallback onOpen;
  final VoidCallback onDeliverNow;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${status.heldCount}', style: theme.textTheme.displaySmall),
            Text(status.heldCount == 1 ? 'notification held' : 'notifications held'),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              children: [
                FilledButton.tonal(onPressed: onOpen, child: const Text('Open digest')),
                OutlinedButton(onPressed: status.heldCount == 0 ? null : onDeliverNow, child: const Text('Deliver now')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _TodayCard extends StatelessWidget {
  const _TodayCard({required this.status});

  final NativeStatus status;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget stat(int value, String label) => Expanded(
          child: Column(
            children: [
              Text('$value', style: theme.textTheme.headlineSmall),
              Text(label, style: theme.textTheme.bodySmall),
            ],
          ),
        );
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Column(
          children: [
            Text('Today', style: theme.textTheme.titleSmall),
            const SizedBox(height: 8),
            Row(children: [stat(status.todayPassed, 'passed through'), stat(status.todayHeld, 'held')]),
          ],
        ),
      ),
    );
  }
}

class _WeekCard extends StatelessWidget {
  const _WeekCard({required this.stats});

  final Stats stats;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final s = stats;
    final heldShare = s.total == 0 ? 0 : (s.held * 100 / s.total).round();
    String count(String key) => '${s.bySource[key] ?? 0}';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Last ${s.days} days', style: theme.textTheme.titleSmall),
            const SizedBox(height: 8),
            Text('${s.total} notifications · $heldShare % held', style: theme.textTheme.bodyLarge),
            const SizedBox(height: 4),
            Text('Decided by rules ${count('RULE')} · AI ${count('AI')} · fallback ${count('FALLBACK')}', style: muted),
            if (s.shouldHaveBeenUrgent + s.shouldNotHaveBeenUrgent > 0)
              Text(
                'Corrections: ${s.shouldHaveBeenUrgent} should have come through, '
                '${s.shouldNotHaveBeenUrgent} should have been held',
                style: muted,
              ),
            if (s.topHeld.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text('Held most', style: theme.textTheme.labelLarge),
              for (final a in s.topHeld) _AppRow(label: a.appLabel, count: a.count, max: s.topHeld.first.count),
            ],
            if (s.topPassed.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text('Came through most', style: theme.textTheme.labelLarge),
              for (final a in s.topPassed) _AppRow(label: a.appLabel, count: a.count, max: s.topPassed.first.count),
            ],
          ],
        ),
      ),
    );
  }
}

/// One row of a tiny horizontal bar chart.
class _AppRow extends StatelessWidget {
  const _AppRow({required this.label, required this.count, required this.max});

  final String label;
  final int count;
  final int max;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          SizedBox(width: 96, child: Text(label, overflow: TextOverflow.ellipsis)),
          const SizedBox(width: 8),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: max == 0 ? 0 : count / max,
                minHeight: 8,
                backgroundColor: scheme.surfaceContainerHighest,
              ),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(width: 32, child: Text('$count', textAlign: TextAlign.end)),
        ],
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner({required this.icon, required this.text, required this.onTap, this.error = false});

  final IconData icon;
  final String text;
  final VoidCallback onTap;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final fg = error ? scheme.onErrorContainer : scheme.onSecondaryContainer;
    return Card(
      color: error ? scheme.errorContainer : scheme.secondaryContainer,
      child: ListTile(
        leading: Icon(icon, color: fg),
        title: Text(text, style: TextStyle(color: fg)),
        trailing: Icon(Icons.chevron_right, color: fg),
        onTap: onTap,
      ),
    );
  }
}

/// Onboarding steps reopened from the home screen, refreshing on return from settings.
class _SetupPage extends StatefulWidget {
  const _SetupPage({required this.onDone});

  final VoidCallback onDone;

  @override
  State<_SetupPage> createState() => _SetupPageState();
}

class _SetupPageState extends State<_SetupPage> with AutoRefresh {
  NativeStatus? _status;

  @override
  Future<void> reload() async {
    final s = await NativeBridge.instance.getStatus();
    if (mounted) setState(() => _status = s);
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    if (status == null) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    return OnboardingScreen(status: status, onRefresh: reload, onDone: widget.onDone);
  }
}
