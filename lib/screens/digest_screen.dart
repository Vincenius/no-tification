import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/entry.dart';
import '../util/format.dart';
import '../widgets/auto_refresh.dart';

/// Everything held and not yet seen, grouped by app → conversation, newest first.
class DigestScreen extends StatefulWidget {
  const DigestScreen({super.key});

  @override
  State<DigestScreen> createState() => _DigestScreenState();
}

class _DigestScreenState extends State<DigestScreen> with AutoRefresh {
  final _bridge = NativeBridge.instance;
  List<Entry>? _entries;

  @override
  Future<void> reload() async {
    final entries = await _bridge.getDigest();
    if (mounted) setState(() => _entries = entries);
  }

  Future<void> _markAllSeen() async {
    final ids = _entries?.map((e) => e.id).toList() ?? const [];
    if (ids.isEmpty) return;
    await _bridge.markSeen(ids);
    await reload();
  }

  @override
  Widget build(BuildContext context) {
    final entries = _entries;
    return Scaffold(
      appBar: AppBar(
        title: Text(entries == null || entries.isEmpty ? 'Digest' : 'Digest (${entries.length})'),
        actions: [
          TextButton.icon(
            onPressed: entries == null || entries.isEmpty ? null : _markAllSeen,
            icon: const Icon(Icons.done_all),
            label: const Text('Mark all as seen'),
          ),
        ],
      ),
      body: switch (entries) {
        null => const Center(child: CircularProgressIndicator()),
        [] => const _Empty(),
        _ => RefreshIndicator(
            onRefresh: reload,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [for (final group in DigestGroup.build(entries)) _AppCard(group: group, onTap: _bridge.openEntry)],
            ),
          ),
      },
    );
  }
}

class _AppCard extends StatelessWidget {
  const _AppCard({required this.group, required this.onTap});

  final DigestGroup group;
  final ValueChanged<Entry> onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Row(
              children: [
                Expanded(child: Text(group.appLabel, style: theme.textTheme.titleMedium)),
                Text('${group.count}', style: muted),
              ],
            ),
          ),
          for (final (conversation, entries) in group.conversations) ...[
            if (group.conversations.length > 1 || conversation != group.appLabel)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: Text(conversation, style: theme.textTheme.titleSmall),
              ),
            for (final e in entries)
              InkWell(
                onTap: () => onTap(e),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: Text(e.displayText, maxLines: 3, overflow: TextOverflow.ellipsis)),
                      const SizedBox(width: 8),
                      Text(formatDayTime(e.postTime), style: muted),
                    ],
                  ),
                ),
              ),
          ],
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty();

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.spa_outlined, size: 48, color: Theme.of(context).colorScheme.outline),
              const SizedBox(height: 12),
              const Text('Nothing held. All calm.', textAlign: TextAlign.center),
            ],
          ),
        ),
      );
}
