import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/schedule.dart';
import '../util/format.dart';
import '../widgets/auto_refresh.dart';

/// The dynamic list of digest times. Every edit reschedules the alarm natively right away.
class ScheduleScreen extends StatefulWidget {
  const ScheduleScreen({super.key});

  @override
  State<ScheduleScreen> createState() => _ScheduleScreenState();
}

class _ScheduleScreenState extends State<ScheduleScreen> with AutoRefresh {
  final _bridge = NativeBridge.instance;
  Schedule? _schedule;
  String? _group;

  @override
  Future<void> reload() async {
    final s = await _bridge.getSchedule();
    if (!mounted) return;
    setState(() {
      _schedule = s;
      if (!s.groups.contains(_group)) _group = s.groups.first;
    });
  }

  Future<TimeOfDay?> _pickTime(int initialMinute, {String? help}) => showTimePicker(
        context: context,
        helpText: help,
        initialTime: TimeOfDay(hour: initialMinute ~/ 60, minute: initialMinute % 60),
      );

  Future<void> _add() async {
    final t = await _pickTime(12 * 60, help: 'New digest time');
    if (t == null) return;
    await _bridge.addDigestTime(_group!, t.hour * 60 + t.minute);
    await reload();
  }

  Future<void> _edit(DigestTimeItem item) async {
    final t = await _pickTime(item.minute, help: 'Change digest time');
    if (t == null) return;
    await _bridge.updateDigestTime(item.id, minute: t.hour * 60 + t.minute, enabled: item.enabled);
    await reload();
  }

  Future<void> _toggle(DigestTimeItem item, bool enabled) async {
    await _bridge.updateDigestTime(item.id, minute: item.minute, enabled: enabled);
    await reload();
  }

  Future<void> _delete(DigestTimeItem item) async {
    await _bridge.deleteDigestTime(item.id);
    await reload();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text('Removed ${formatMinute(item.minute)}'),
      action: SnackBarAction(
        label: 'Undo',
        onPressed: () async {
          await _bridge.addDigestTime(item.group, item.minute);
          await reload();
        },
      ),
    ));
  }

  Future<void> _generate() async {
    final result = await showDialog<_Generated>(context: context, builder: (_) => const _GeneratorDialog());
    if (result == null) return;
    await _bridge.generateDigestTimes(_group!, start: result.start, end: result.end, interval: result.interval);
    await reload();
  }

  Future<void> _setMode(ScheduleMode mode) async {
    await _bridge.setScheduleMode(mode);
    await reload();
  }

  @override
  Widget build(BuildContext context) {
    final s = _schedule;
    final group = _group;
    return Scaffold(
      appBar: AppBar(title: const Text('Digest schedule')),
      floatingActionButton: s == null
          ? null
          : FloatingActionButton(onPressed: _add, tooltip: 'Add time', child: const Icon(Icons.add)),
      body: s == null || group == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
              children: [
                SegmentedButton<ScheduleMode>(
                  segments: [for (final m in ScheduleMode.values) ButtonSegment(value: m, label: Text(m.label))],
                  selected: {s.mode},
                  onSelectionChanged: (sel) => _setMode(sel.single),
                  showSelectedIcon: false,
                ),
                const SizedBox(height: 16),
                _Preview(schedule: s),
                if (s.groups.length > 1) ...[
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: [
                      for (final g in s.groups)
                        ChoiceChip(
                          label: Text('${groupLabels[g]} (${s.timesIn(g).where((t) => t.enabled).length})'),
                          selected: g == group,
                          onSelected: (_) => setState(() => _group = g),
                        ),
                    ],
                  ),
                ],
                for (final (a, b) in s.warnings[group] ?? const <(int, int)>[])
                  _Hint(
                    icon: Icons.info_outline,
                    text: '${formatMinute(a)} and ${formatMinute(b)} are less than 15 minutes apart.',
                  ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text(s.groups.length > 1 ? 'Times for ${groupLabels[group]}' : 'Times',
                        style: Theme.of(context).textTheme.titleSmall),
                    const Spacer(),
                    TextButton.icon(onPressed: _generate, icon: const Icon(Icons.auto_awesome_motion), label: const Text('Generate')),
                  ],
                ),
                if (s.timesIn(group).isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 24),
                    child: Text('No times yet. Add one with + or generate a series.', textAlign: TextAlign.center),
                  ),
                for (final t in s.timesIn(group))
                  Card(
                    child: ListTile(
                      onTap: () => _edit(t),
                      title: Text(
                        formatMinute(t.minute),
                        style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                              color: t.enabled ? null : Theme.of(context).colorScheme.outline,
                            ),
                      ),
                      subtitle: t.enabled ? null : const Text('Paused'),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Switch(value: t.enabled, onChanged: (v) => _toggle(t, v)),
                          IconButton(onPressed: () => _delete(t), icon: const Icon(Icons.delete_outline), tooltip: 'Delete'),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _Preview extends StatelessWidget {
  const _Preview({required this.schedule});

  final Schedule schedule;

  @override
  Widget build(BuildContext context) {
    if (schedule.upcoming.isEmpty) {
      return const _Hint(
        icon: Icons.warning_amber,
        error: true,
        text: 'No digests scheduled. Held notifications wait until you open the digest screen yourself.',
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Next ${schedule.upcoming.length == 1 ? 'digest' : '${schedule.upcoming.length} digests'}: '
          '${schedule.upcoming.map(formatDayTime).join(', ')}',
          style: Theme.of(context).textTheme.bodyLarge,
        ),
        if (!schedule.exactAlarms)
          const _Hint(
            icon: Icons.timer_off_outlined,
            text: 'Exact alarms are not allowed, so digests may arrive up to 10 minutes late.',
          ),
      ],
    );
  }
}

class _Hint extends StatelessWidget {
  const _Hint({required this.icon, required this.text, this.error = false});

  final IconData icon;
  final String text;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = error ? scheme.error : scheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: TextStyle(color: color))),
        ],
      ),
    );
  }
}

class _Generated {
  const _Generated(this.start, this.end, this.interval);

  final int start;
  final int end;
  final int interval;
}

/// "Every N hours/minutes between START and END". Replaces the times of the selected group.
class _GeneratorDialog extends StatefulWidget {
  const _GeneratorDialog();

  @override
  State<_GeneratorDialog> createState() => _GeneratorDialogState();
}

class _GeneratorDialogState extends State<_GeneratorDialog> {
  int _start = 8 * 60;
  int _end = 20 * 60;
  final _every = TextEditingController(text: '2');
  bool _hours = true;

  int? get _interval {
    final n = int.tryParse(_every.text.trim());
    if (n == null || n <= 0) return null;
    return _hours ? n * 60 : n;
  }

  int get _count {
    final i = _interval;
    if (i == null || i < 15) return 0;
    final span = _end >= _start ? _end - _start : _end + 1440 - _start;
    return span ~/ i + 1;
  }

  Future<void> _pick(bool start) async {
    final initial = start ? _start : _end;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay(hour: initial ~/ 60, minute: initial % 60));
    if (t == null) return;
    setState(() => start ? _start = t.hour * 60 + t.minute : _end = t.hour * 60 + t.minute);
  }

  @override
  void dispose() {
    _every.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final interval = _interval;
    final tooShort = interval != null && interval < 15;
    return AlertDialog(
      title: const Text('Generate times'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text('Every'),
              const SizedBox(width: 12),
              SizedBox(
                width: 56,
                child: TextField(
                  controller: _every,
                  keyboardType: TextInputType.number,
                  textAlign: TextAlign.center,
                  onChanged: (_) => setState(() {}),
                ),
              ),
              const SizedBox(width: 12),
              DropdownButton<bool>(
                value: _hours,
                items: const [
                  DropdownMenuItem(value: true, child: Text('hours')),
                  DropdownMenuItem(value: false, child: Text('minutes')),
                ],
                onChanged: (v) => setState(() => _hours = v!),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              const Text('from'),
              TextButton(onPressed: () => _pick(true), child: Text(formatMinute(_start))),
              const Text('to'),
              TextButton(onPressed: () => _pick(false), child: Text(formatMinute(_end))),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            tooShort
                ? 'The interval must be at least 15 minutes.'
                : 'Creates $_count times and replaces the current list. You can edit them afterwards.',
            style: TextStyle(color: tooShort ? Theme.of(context).colorScheme.error : null),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          onPressed: interval == null || tooShort ? null : () => Navigator.pop(context, _Generated(_start, _end, interval)),
          child: const Text('Generate'),
        ),
      ],
    );
  }
}
