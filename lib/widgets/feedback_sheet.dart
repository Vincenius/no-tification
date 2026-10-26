import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/entry.dart';
import '../models/feedback.dart';

/// "Should (not) have been urgent": marks the entry and offers a rule so it doesn't happen again.
Future<void> showFeedbackSheet(BuildContext context, Entry entry) async {
  final bridge = NativeBridge.instance;
  final options = await bridge.getFeedback(entry);
  if (options == null || !context.mounted) return;

  // A suggestion id, [_justNote], or null when dismissed.
  final choice = await showModalBottomSheet<String>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _FeedbackSheet(entry: entry, options: options),
  );
  if (choice == null || !context.mounted) return;

  final applied = await bridge.applyFeedback(entry, suggestion: choice == _justNote ? null : choice);
  if (!context.mounted) return;
  ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(applied == null ? 'Noted. Thanks!' : 'Done: $applied')));
}

const _justNote = '_note';

class _FeedbackSheet extends StatelessWidget {
  const _FeedbackSheet({required this.entry, required this.options});

  final Entry entry;
  final FeedbackOptions options;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final shouldBeUrgent = options.feedback == 'SHOULD_BE_URGENT';
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              shouldBeUrgent ? 'Should have been urgent' : 'Should not have been urgent',
              style: theme.textTheme.titleLarge,
            ),
            const SizedBox(height: 4),
            Text(
              '${entry.appLabel} · ${entry.conversation}\nDecided by ${entry.decisionSource.toLowerCase()}: ${entry.reason}',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 12),
            if (options.note != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(options.note!)),
            if (options.suggestions.isNotEmpty) ...[
              Text('Make it a rule', style: theme.textTheme.titleSmall),
              for (final s in options.suggestions)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    s.id.startsWith('remove') || s.id == 'unmute-group'
                        ? Icons.remove_circle_outline
                        : shouldBeUrgent
                        ? Icons.notifications_active_outlined
                        : Icons.inbox_outlined,
                  ),
                  title: Text(s.label),
                  subtitle: Text(s.detail),
                  onTap: () => Navigator.pop(context, s.id),
                ),
              const SizedBox(height: 8),
            ],
            Row(
              children: [
                TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
                const Spacer(),
                OutlinedButton(onPressed: () => Navigator.pop(context, _justNote), child: const Text('Just note it')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
