import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/entry.dart';
import '../util/format.dart';
import '../widgets/auto_refresh.dart';
import '../widgets/feedback_sheet.dart';

/// Every processed notification with its decision and reason, live.
class LogScreen extends StatefulWidget {
  const LogScreen({super.key});

  @override
  State<LogScreen> createState() => _LogScreenState();
}

class _LogScreenState extends State<LogScreen> with AutoRefresh {
  final _bridge = NativeBridge.instance;
  List<Entry>? _entries;

  @override
  Future<void> reload() async {
    final entries = await _bridge.getHistory();
    if (mounted) setState(() => _entries = entries);
  }

  @override
  Widget build(BuildContext context) {
    final entries = _entries;
    return Scaffold(
      appBar: AppBar(title: const Text('Log')),
      body: switch (entries) {
        null => const Center(child: CircularProgressIndicator()),
        [] => const Center(
            child: Padding(
              padding: EdgeInsets.all(32),
              child: Text(
                'Nothing processed yet.\nTurn Calm on and notifications will appear here with their decision.',
                textAlign: TextAlign.center,
              ),
            ),
          ),
        _ => RefreshIndicator(
            onRefresh: reload,
            child: ListView.separated(
              padding: const EdgeInsets.only(bottom: 24),
              itemCount: entries.length,
              separatorBuilder: (_, _) => const Divider(height: 1),
              itemBuilder: (context, i) => _LogTile(entry: entries[i], onTap: () => _bridge.openEntry(entries[i])),
            ),
          ),
      },
    );
  }
}

class _LogTile extends StatelessWidget {
  const _LogTile({required this.entry, required this.onTap});

  final Entry entry;
  final VoidCallback onTap;

  static const _sourceLabels = {'RULE': 'Rule', 'AI': 'AI', 'FALLBACK': 'Fallback'};

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final muted = theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant);
    final e = entry;

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _Badge(
                  label: e.urgent ? 'Urgent' : 'Held',
                  background: e.urgent ? scheme.tertiaryContainer : scheme.surfaceContainerHighest,
                  foreground: e.urgent ? scheme.onTertiaryContainer : scheme.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Expanded(child: Text('${e.appLabel} · ${e.conversation}', style: theme.textTheme.labelLarge, overflow: TextOverflow.ellipsis)),
                Text(formatDayTime(e.receivedAt), style: muted),
              ],
            ),
            const SizedBox(height: 4),
            Text(e.displayText, maxLines: 2, overflow: TextOverflow.ellipsis),
            const SizedBox(height: 4),
            Text('${_sourceLabels[e.decisionSource] ?? e.decisionSource}: ${e.reason}', style: muted),
            Align(
              alignment: Alignment.centerRight,
              child: e.feedback != null
                  ? Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        e.feedback == 'SHOULD_BE_URGENT' ? 'Marked: should have been urgent' : 'Marked: should not have been urgent',
                        style: muted?.copyWith(fontStyle: FontStyle.italic),
                      ),
                    )
                  : TextButton.icon(
                      style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
                      onPressed: () => showFeedbackSheet(context, e),
                      icon: Icon(e.urgent ? Icons.thumb_down_outlined : Icons.priority_high, size: 16),
                      label: Text(e.urgent ? 'Should not have been urgent' : 'Should have been urgent'),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.label, required this.background, required this.foreground});

  final String label;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(8)),
        child: Text(label, style: Theme.of(context).textTheme.labelSmall?.copyWith(color: foreground)),
      );
}
