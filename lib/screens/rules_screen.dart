import 'package:flutter/material.dart';

import '../bridge/native_bridge.dart';
import '../models/rule.dart';
import '../widgets/auto_refresh.dart';

/// VIPs, apps to always hold, muted groups and custom rules.
class RulesScreen extends StatefulWidget {
  const RulesScreen({super.key});

  @override
  State<RulesScreen> createState() => _RulesScreenState();
}

class _RulesScreenState extends State<RulesScreen> with AutoRefresh {
  final _bridge = NativeBridge.instance;
  List<RuleItem>? _rules;
  List<AppSeen> _apps = const [];

  @override
  Future<void> reload() async {
    final (rules, apps) = await (_bridge.getRules(), _bridge.getAppsSeen()).wait;
    if (mounted) {
      setState(() {
        _rules = rules;
        _apps = apps;
      });
    }
  }

  String _appLabel(String? packageName) =>
      _apps.where((a) => a.packageName == packageName).firstOrNull?.appLabel ?? packageName ?? 'Any app';

  Future<void> _add({required String kind, required String matchType, required String pattern, String? packageName, String action = 'DIGEST'}) async {
    await _bridge.addRule(kind: kind, matchType: matchType, pattern: pattern, packageName: packageName, action: action);
    await reload();
  }

  Future<void> _delete(RuleItem rule) async {
    await _bridge.deleteRule(rule.id);
    await reload();
  }

  Future<void> _addVip() async {
    final name = await showDialog<String>(
      context: context,
      builder: (_) => const _TextDialog(
        title: 'Add VIP',
        label: 'Name as shown in notifications',
        hint: 'e.g. Anna Schmidt',
      ),
    );
    if (name != null) await _add(kind: 'VIP', matchType: 'SENDER', pattern: name, action: 'URGENT');
  }

  Future<void> _addDigestApp() async {
    final existing = _rules!.where((r) => r.kind == 'DIGEST_APP').map((r) => r.pattern).toSet();
    final app = await _pickApp(_apps.where((a) => !existing.contains(a.packageName)).toList());
    if (app != null) await _add(kind: 'DIGEST_APP', matchType: 'APP', pattern: app.packageName);
  }

  Future<void> _addMutedGroup() async {
    final result = await showDialog<(String, String?)>(
      context: context,
      builder: (_) => _GroupDialog(apps: _apps),
    );
    if (result != null) await _add(kind: 'MUTED_GROUP', matchType: 'CONVERSATION', pattern: result.$1, packageName: result.$2);
  }

  Future<void> _addCustom() async {
    final result = await showDialog<_CustomRule>(context: context, builder: (_) => _CustomRuleDialog(apps: _apps));
    if (result != null) {
      await _add(
        kind: 'CUSTOM',
        matchType: result.matchType,
        pattern: result.pattern,
        packageName: result.packageName,
        action: result.action,
      );
    }
  }

  Future<AppSeen?> _pickApp(List<AppSeen> apps) => showDialog<AppSeen>(
        context: context,
        builder: (context) => SimpleDialog(
          title: const Text('Always hold notifications from'),
          children: apps.isEmpty
              ? [const Padding(padding: EdgeInsets.all(24), child: Text('Apps appear here after Calm has seen a notification from them.'))]
              : [
                  for (final a in apps)
                    SimpleDialogOption(
                      onPressed: () => Navigator.pop(context, a),
                      child: Text('${a.appLabel}  ·  ${a.count}'),
                    ),
                ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final rules = _rules;
    if (rules == null) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    List<RuleItem> of(String kind) => rules.where((r) => r.kind == kind).toList();

    return Scaffold(
      appBar: AppBar(title: const Text('Rules')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          _Section(
            title: 'VIPs',
            subtitle: 'Always let these people through, in any app.',
            onAdd: _addVip,
            children: [for (final r in of('VIP')) _RuleTile(title: r.pattern, icon: Icons.star_outline, onDelete: () => _delete(r))],
          ),
          _Section(
            title: 'Always hold',
            subtitle: 'Apps that always go to the digest. Calls, alarms, codes and VIPs still come through.',
            onAdd: _addDigestApp,
            children: [
              for (final r in of('DIGEST_APP')) _RuleTile(title: _appLabel(r.pattern), icon: Icons.apps, onDelete: () => _delete(r)),
            ],
          ),
          _Section(
            title: 'Muted groups',
            subtitle: 'Group chats that always go to the digest.',
            onAdd: _addMutedGroup,
            children: [
              for (final r in of('MUTED_GROUP'))
                _RuleTile(title: r.pattern, subtitle: _appLabel(r.packageName), icon: Icons.group_off_outlined, onDelete: () => _delete(r)),
            ],
          ),
          _Section(
            title: 'Custom rules',
            subtitle: 'Per app, conversation, sender or keyword. If rules conflict, urgent wins.',
            onAdd: _addCustom,
            children: [
              for (final r in of('CUSTOM'))
                _RuleTile(
                  title: r.description,
                  subtitle: r.packageName == null ? null : _appLabel(r.packageName),
                  icon: r.action == 'URGENT' ? Icons.notifications_active_outlined : Icons.inbox_outlined,
                  onDelete: () => _delete(r),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.subtitle, required this.onAdd, required this.children});

  final String title;
  final String subtitle;
  final VoidCallback onAdd;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ListTile(
              title: Text(title, style: theme.textTheme.titleMedium),
              subtitle: Text(subtitle),
              trailing: IconButton.filledTonal(onPressed: onAdd, icon: const Icon(Icons.add), tooltip: 'Add'),
            ),
            ...children,
          ],
        ),
      ),
    );
  }
}

class _RuleTile extends StatelessWidget {
  const _RuleTile({required this.title, this.subtitle, required this.icon, required this.onDelete});

  final String title;
  final String? subtitle;
  final IconData icon;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) => ListTile(
        dense: true,
        leading: Icon(icon),
        title: Text(title),
        subtitle: subtitle == null ? null : Text(subtitle!),
        trailing: IconButton(onPressed: onDelete, icon: const Icon(Icons.close), tooltip: 'Remove'),
      );
}

class _TextDialog extends StatefulWidget {
  const _TextDialog({required this.title, required this.label, this.hint});

  final String title;
  final String label;
  final String? hint;

  @override
  State<_TextDialog> createState() => _TextDialogState();
}

class _TextDialogState extends State<_TextDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final text = _controller.text.trim();
    if (text.isNotEmpty) Navigator.pop(context, text);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(widget.title),
        content: TextField(
          controller: _controller,
          autofocus: true,
          decoration: InputDecoration(labelText: widget.label, hintText: widget.hint),
          onSubmitted: (_) => _submit(),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(onPressed: _submit, child: const Text('Add')),
        ],
      );
}

DropdownButtonFormField<String?> _appDropdown(List<AppSeen> apps, String? value, ValueChanged<String?> onChanged, {String anyLabel = 'Any app'}) =>
    DropdownButtonFormField<String?>(
      initialValue: value,
      isExpanded: true,
      decoration: const InputDecoration(labelText: 'App'),
      items: [
        DropdownMenuItem(value: null, child: Text(anyLabel)),
        for (final a in apps) DropdownMenuItem(value: a.packageName, child: Text(a.appLabel)),
      ],
      onChanged: onChanged,
    );

class _GroupDialog extends StatefulWidget {
  const _GroupDialog({required this.apps});

  final List<AppSeen> apps;

  @override
  State<_GroupDialog> createState() => _GroupDialogState();
}

class _GroupDialogState extends State<_GroupDialog> {
  final _name = TextEditingController();
  String? _package;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: const Text('Mute a group'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: _name, autofocus: true, decoration: const InputDecoration(labelText: 'Group name')),
            const SizedBox(height: 8),
            _appDropdown(widget.apps, _package, (v) => setState(() => _package = v)),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(
            onPressed: () {
              final name = _name.text.trim();
              if (name.isNotEmpty) Navigator.pop(context, (name, _package));
            },
            child: const Text('Mute'),
          ),
        ],
      );
}

class _CustomRule {
  const _CustomRule(this.matchType, this.pattern, this.packageName, this.action);

  final String matchType;
  final String pattern;
  final String? packageName;
  final String action;
}

class _CustomRuleDialog extends StatefulWidget {
  const _CustomRuleDialog({required this.apps});

  final List<AppSeen> apps;

  @override
  State<_CustomRuleDialog> createState() => _CustomRuleDialogState();
}

class _CustomRuleDialogState extends State<_CustomRuleDialog> {
  static const _types = {'SENDER': 'Sender', 'CONVERSATION': 'Conversation', 'KEYWORD': 'Keyword', 'APP': 'Whole app'};

  String _type = 'SENDER';
  String _action = 'URGENT';
  String? _package;
  final _pattern = TextEditingController();

  @override
  void dispose() {
    _pattern.dispose();
    super.dispose();
  }

  void _submit() {
    if (_type == 'APP') {
      if (_package != null) Navigator.pop(context, _CustomRule('APP', _package!, null, _action));
      return;
    }
    final pattern = _pattern.text.trim();
    if (pattern.isNotEmpty) Navigator.pop(context, _CustomRule(_type, pattern, _package, _action));
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: const Text('New rule'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              DropdownButtonFormField<String>(
                initialValue: _type,
                decoration: const InputDecoration(labelText: 'Match'),
                items: [for (final e in _types.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
                onChanged: (v) => setState(() => _type = v!),
              ),
              if (_type != 'APP')
                TextField(
                  controller: _pattern,
                  decoration: InputDecoration(
                    labelText: _type == 'KEYWORD' ? 'Word or phrase' : 'Name as shown in notifications',
                  ),
                ),
              const SizedBox(height: 8),
              _appDropdown(
                widget.apps,
                _package,
                (v) => setState(() => _package = v),
                anyLabel: _type == 'APP' ? 'Choose an app' : 'Any app',
              ),
              const SizedBox(height: 16),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'URGENT', label: Text('Let through')),
                  ButtonSegment(value: 'DIGEST', label: Text('Hold')),
                ],
                selected: {_action},
                onSelectionChanged: (s) => setState(() => _action = s.single),
                showSelectedIcon: false,
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(onPressed: _submit, child: const Text('Add')),
        ],
      );
}
