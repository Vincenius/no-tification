import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/captured_notification.dart';
import '../models/native_status.dart';

/// Live list of every intercepted notification.
class LogScreen extends StatefulWidget {
  const LogScreen({super.key});

  @override
  State<LogScreen> createState() => _LogScreenState();
}

class _LogScreenState extends State<LogScreen> {
  final _bridge = NativeBridge.instance;
  final List<CapturedNotification> _items = [];
  NativeStatus _status = NativeStatus.unknown;
  final List<StreamSubscription<Object?>> _subs = [];

  @override
  void initState() {
    super.initState();
    _subs.add(_bridge.notifications.listen((n) => setState(() => _items.insert(0, n))));
    _subs.add(_bridge.statusChanges.listen((s) => setState(() => _status = s)));
    _load();
  }

  @override
  void dispose() {
    for (final s in _subs) {
      s.cancel();
    }
    super.dispose();
  }

  Future<void> _load() async {
    final (log, status) = await (_bridge.getLog(), _bridge.getStatus()).wait;
    if (!mounted) return;
    setState(() {
      _items
        ..clear()
        ..addAll(log);
      _status = status;
    });
  }

  Future<void> _clear() async {
    await _bridge.clearLog();
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Live log'),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh), tooltip: 'Reload'),
          IconButton(
            onPressed: _items.isEmpty ? null : _clear,
            icon: const Icon(Icons.delete_sweep_outlined),
            tooltip: 'Clear',
          ),
        ],
      ),
      body: Column(
        children: [
          _StatusBanner(status: _status, count: _items.length),
          Expanded(
            child: _items.isEmpty
                ? const _EmptyState()
                : RefreshIndicator(
                    onRefresh: _load,
                    child: ListView.builder(
                      padding: const EdgeInsets.only(bottom: 24),
                      itemCount: _items.length,
                      itemBuilder: (context, i) => _LogTile(
                        item: _items[i],
                        onTap: () => _bridge.openNotification(_items[i]),
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.status, required this.count});

  final NativeStatus status;
  final int count;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final ok = status.listenerConnected;
    return Container(
      width: double.infinity,
      color: ok ? scheme.secondaryContainer : scheme.errorContainer,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Text(
        ok
            ? 'Listener connected · $count captured · ${status.ignoredCount} ignored'
            : 'Listener not connected. Toggle notification access off and on if this persists.',
        style: TextStyle(color: ok ? scheme.onSecondaryContainer : scheme.onErrorContainer),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) => const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Text(
            'Nothing captured yet.\nSend yourself a message to see it appear here.',
            textAlign: TextAlign.center,
          ),
        ),
      );
}

class _LogTile extends StatelessWidget {
  const _LogTile({required this.item, required this.onTap});

  final CapturedNotification item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final tags = [
      if (item.category != null) item.category!,
      if (item.isGroupConversation) 'group',
      if (item.messages.isNotEmpty) '${item.messages.length} msg',
    ];

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(item.appLabel, style: theme.textTheme.labelLarge)),
                Text(_time(item.postTime), style: muted),
              ],
            ),
            if (item.conversationTitle != null)
              Text(item.conversationTitle!, style: theme.textTheme.titleSmall),
            if (item.title != null && item.title != item.conversationTitle)
              Text(item.title!, style: theme.textTheme.titleSmall),
            if (item.messages.isNotEmpty)
              for (final m in item.messages.reversed.take(3).toList().reversed)
                Text(
                  m.sender == null ? m.text : '${m.sender}: ${m.text}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                )
            else if (item.text != null)
              Text(item.text!, maxLines: 3, overflow: TextOverflow.ellipsis),
            if (tags.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(tags.join(' · '), style: muted),
            ],
          ],
        ),
      ),
    );
  }

  static String _time(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:'
      '${t.second.toString().padLeft(2, '0')}';
}
