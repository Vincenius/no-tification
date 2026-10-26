import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/ai_settings.dart';
import '../models/feedback.dart';
import '../util/format.dart';
import '../widgets/auto_refresh.dart';
import 'rules_screen.dart';

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
                const SizedBox(height: 12),
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.rule),
                    title: const Text('VIPs and rules'),
                    subtitle: const Text('VIP contacts, apps to always hold, muted groups, custom rules'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const RulesScreen())),
                  ),
                ),
                const SizedBox(height: 12),
                const _MaintenanceCard(),
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

/// History retention and the listener health check.
class _MaintenanceCard extends StatefulWidget {
  const _MaintenanceCard();

  @override
  State<_MaintenanceCard> createState() => _MaintenanceCardState();
}

class _MaintenanceCardState extends State<_MaintenanceCard> with AutoRefresh {
  final _bridge = NativeBridge.instance;
  Maintenance? _m;
  bool _reconnecting = false;

  static const _retentionChoices = [1, 3, 7, 14, 30, 90];

  @override
  Future<void> reload() => _load(_bridge.getMaintenance());

  Future<void> _load(Future<Maintenance> future) async {
    final m = await future;
    if (mounted) setState(() => _m = m);
  }

  Future<void> _reconnect() async {
    setState(() => _reconnecting = true);
    await _load(_bridge.reconnectListener());
    // Rebinding takes a moment; look again shortly.
    await Future<void>.delayed(const Duration(seconds: 2));
    await _load(_bridge.getMaintenance());
    if (mounted) setState(() => _reconnecting = false);
  }

  @override
  Widget build(BuildContext context) {
    final m = _m;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    if (m == null) return const SizedBox.shrink();

    final healthy = m.notificationAccess && m.listenerConnected;
    final healthText = !m.notificationAccess
        ? 'Notification access is off'
        : m.listenerConnected
            ? 'Listener connected'
            : 'Listener disconnected${m.listenerDisconnectedSince == null ? '' : ' since ${formatDayTime(m.listenerDisconnectedSince!)}'}';

    return Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListTile(
            leading: const Icon(Icons.history),
            title: const Text('Keep history for'),
            subtitle: Text(m.lastCleanupAt == null
                ? 'Held notifications you haven\'t seen are always kept.'
                : 'Last cleanup ${formatDayTime(m.lastCleanupAt!)}. Unseen held notifications are always kept.'),
            trailing: DropdownButton<int>(
              value: _retentionChoices.contains(m.retentionDays) ? m.retentionDays : null,
              hint: Text('${m.retentionDays} days'),
              items: [
                for (final d in _retentionChoices) DropdownMenuItem(value: d, child: Text(d == 1 ? '1 day' : '$d days')),
              ],
              onChanged: (d) {
                if (d != null) _load(_bridge.setRetentionDays(d));
              },
            ),
          ),
          const Divider(height: 1),
          ListTile(
            leading: Icon(healthy ? Icons.check_circle_outline : Icons.error_outline, color: healthy ? scheme.primary : scheme.error),
            title: Text(healthText),
            subtitle: Text([
              if (m.silencingSuspended) 'Silencing is paused until it reconnects.',
              if (m.lastHealthCheckAt != null) 'Checked ${formatDayTime(m.lastHealthCheckAt!)}. Checks run every 15 minutes while Calm is on.',
            ].join(' ')),
            trailing: TextButton(
              onPressed: _reconnecting ? null : _reconnect,
              child: Text(_reconnecting ? 'Reconnecting…' : 'Reconnect'),
            ),
          ),
          ListTile(
            leading: Icon(m.batteryOptimizationIgnored ? Icons.battery_charging_full : Icons.battery_alert_outlined),
            title: Text(m.batteryOptimizationIgnored ? 'Battery optimization off for Calm' : 'Battery optimization is on'),
            subtitle: m.batteryOptimizationIgnored ? null : const Text('Some phones then stop Calm in the background.'),
            trailing: m.batteryOptimizationIgnored
                ? null
                : TextButton(
                    onPressed: () async {
                      await _bridge.openBatteryOptimizationSettings();
                    },
                    child: const Text('Fix'),
                  ),
          ),
        ],
      ),
    );
  }
}
