import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/ai_settings.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _bridge = NativeBridge.instance;
  AiSettings? _ai;

  @override
  void initState() {
    super.initState();
    _bridge.getAiSettings().then(_set);
  }

  void _set(AiSettings ai) {
    if (mounted) setState(() => _ai = ai);
  }

  Future<void> _editKey() async {
    final key = await showDialog<String>(context: context, builder: (_) => const _ApiKeyDialog());
    if (key != null) _set(await _bridge.setApiKey(key));
  }

  Future<void> _removeKey() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove API key?'),
        content: const Text('Calm will run on rules only. Anything no rule covers is let through.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok == true) _set(await _bridge.setApiKey(null));
  }

  Future<void> _editModel() async {
    final ai = _ai!;
    final controller = TextEditingController(text: ai.model);
    final model = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Model'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(labelText: 'Model name', helperText: 'Default: ${ai.defaultModel}'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, ''), child: const Text('Reset')),
          FilledButton(onPressed: () => Navigator.pop(context, controller.text), child: const Text('Save')),
        ],
      ),
    );
    controller.dispose();
    if (model != null) _set(await _bridge.setAiModel(model));
  }

  @override
  Widget build(BuildContext context) {
    final ai = _ai;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ai == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [
                Card(
                  child: Column(
                    children: [
                      SwitchListTile(
                        title: const Text('AI classification'),
                        subtitle: Text(ai.active
                            ? 'Messages no rule covers are judged by ${ai.model}.'
                            : ai.hasKey
                                ? 'Off. Anything no rule covers is let through.'
                                : 'Add an API key to use it. Until then Calm runs on rules only.'),
                        value: ai.enabled,
                        onChanged: (v) async => _set(await _bridge.setAiEnabled(v)),
                      ),
                      const Divider(height: 1),
                      ListTile(
                        leading: const Icon(Icons.key_outlined),
                        title: const Text('Anthropic API key'),
                        subtitle: Text(ai.hasKey ? 'Stored encrypted on this phone · …${ai.keyHint}' : 'Not set'),
                        trailing: ai.hasKey
                            ? IconButton(onPressed: _removeKey, icon: const Icon(Icons.delete_outline), tooltip: 'Remove')
                            : null,
                        onTap: _editKey,
                      ),
                      ListTile(
                        leading: const Icon(Icons.memory_outlined),
                        title: const Text('Model'),
                        subtitle: Text(ai.model),
                        onTap: _editModel,
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(4, 4, 4, 16),
                  child: Text(
                    'Only the app name, conversation name, sender name, whether it is a group, the message text '
                    'and the current time are sent. Phone numbers and email addresses are removed first. If the '
                    'AI is slow (over 5 s), fails or is unsure, the message is let through.',
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ),
                const _TestBox(),
              ],
            ),
    );
  }
}

class _ApiKeyDialog extends StatefulWidget {
  const _ApiKeyDialog();

  @override
  State<_ApiKeyDialog> createState() => _ApiKeyDialogState();
}

class _ApiKeyDialogState extends State<_ApiKeyDialog> {
  final _controller = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final key = _controller.text.trim();
    return AlertDialog(
      title: const Text('Anthropic API key'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: _controller,
            autofocus: true,
            obscureText: _obscure,
            autocorrect: false,
            enableSuggestions: false,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              labelText: 'sk-ant-…',
              suffixIcon: IconButton(
                onPressed: () => setState(() => _obscure = !_obscure),
                icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              ),
            ),
          ),
          const SizedBox(height: 8),
          const Text('Create one at console.anthropic.com → API Keys. It is stored encrypted and only sent to Anthropic.'),
          if (key.isNotEmpty && !key.startsWith('sk-ant-'))
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                'This doesn\'t look like an Anthropic key.',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(onPressed: key.isEmpty ? null : () => Navigator.pop(context, key), child: const Text('Save')),
      ],
    );
  }
}

/// Type a fake message and see what Calm would decide, through the real rules and AI.
class _TestBox extends StatefulWidget {
  const _TestBox();

  @override
  State<_TestBox> createState() => _TestBoxState();
}

class _TestBoxState extends State<_TestBox> {
  final _app = TextEditingController(text: 'WhatsApp');
  final _sender = TextEditingController();
  final _conversation = TextEditingController();
  final _text = TextEditingController();
  bool _group = false;
  bool _running = false;
  TestResult? _result;

  static const _sourceLabels = {'RULE': 'Rule', 'AI': 'AI', 'FALLBACK': 'Fallback'};

  @override
  void dispose() {
    for (final c in [_app, _sender, _conversation, _text]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _run() async {
    if (_text.text.trim().isEmpty) return;
    setState(() {
      _running = true;
      _result = null;
    });
    final result = await NativeBridge.instance.testClassification(
      text: _text.text.trim(),
      appLabel: _app.text.trim(),
      sender: _sender.text.trim(),
      conversation: _conversation.text.trim(),
      isGroup: _group,
    );
    if (mounted) {
      setState(() {
        _result = result;
        _running = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final r = _result;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Test classification', style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            const Text('Nothing is stored. This runs the same rules and AI as real notifications, without the 3 s wait.'),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: TextField(controller: _app, decoration: const InputDecoration(labelText: 'App'))),
                const SizedBox(width: 12),
                Expanded(child: TextField(controller: _sender, decoration: const InputDecoration(labelText: 'Sender'))),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: TextField(controller: _conversation, decoration: const InputDecoration(labelText: 'Conversation (optional)')),
                ),
                const SizedBox(width: 12),
                const Text('Group'),
                Switch(value: _group, onChanged: (v) => setState(() => _group = v)),
              ],
            ),
            TextField(
              controller: _text,
              minLines: 2,
              maxLines: 5,
              decoration: const InputDecoration(labelText: 'Message', hintText: 'Bin in 10 Minuten unten, kommst du runter?'),
            ),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: _running ? null : _run,
                icon: _running
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.play_arrow),
                label: const Text('Classify'),
              ),
            ),
            if (r != null) ...[
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: r.urgent ? scheme.tertiaryContainer : scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      r.urgent ? 'Urgent: comes through now' : 'Held for the digest',
                      style: theme.textTheme.titleSmall?.copyWith(
                        color: r.urgent ? scheme.onTertiaryContainer : scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(r.reason),
                    const SizedBox(height: 4),
                    Text(
                      '${_sourceLabels[r.decisionSource] ?? r.decisionSource} · ${r.millis} ms',
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
