import 'package:flutter/material.dart';
import '../models/assistant_ui_models.dart';
import '../services/assistant_brain_service.dart';

class AssistantHomeScreen extends StatefulWidget {
  const AssistantHomeScreen({super.key});

  @override
  State<AssistantHomeScreen> createState() => _AssistantHomeScreenState();
}

class _AssistantHomeScreenState extends State<AssistantHomeScreen> {
  late AssistantBrainService _brainService;
  AssistantUiState _state = AssistantUiState(
    personality: AssistantPersonality.luna,
    status: AssistantUiStatus.idle,
  );
  List<AssistantUiResult> _history = [];
  final TextEditingController _commandController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _brainService = AssistantBrainService(onStateChanged: (newState) {
      if (mounted) {
        setState(() {
          _state = newState;
        });
        _refreshHistory();
      }
    });
    _loadInitialState();
  }

  Future<void> _loadInitialState() async {
    final state = await _brainService.getAssistantState();
    final history = await _brainService.getCommandHistory();
    if (!mounted) return;
    setState(() {
      _state = state;
      _history = history;
    });
  }

  Future<void> _refreshHistory() async {
    final history = await _brainService.getCommandHistory();
    if (!mounted) return;
    setState(() {
      _history = history;
    });
  }

  Future<void> _sendCommand() async {
    final command = _commandController.text.trim();
    if (command.isEmpty) return;

    _commandController.clear();
    await _brainService.submitTextCommand(command, _state.personality);
  }

  Future<void> _toggleVoice() async {
    if (_state.status == AssistantUiStatus.listening) {
      await _brainService.stopVoiceListening();
    } else {
      await _brainService.startVoiceListening(_state.personality);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isLuna = _state.personality == AssistantPersonality.luna;
    final accentColor = isLuna ? const Color(0xFFE85D75) : const Color(0xFF2F7CF6);
    final assistantName = isLuna ? "Luna" : "Nova";

    return Scaffold(
      backgroundColor: theme.colorScheme.surface,
      appBar: AppBar(
        title: Text(assistantName, style: const TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.analytics_outlined),
            onPressed: () async {
              final diag = await _brainService.getPhase26Diagnostics();
              if (!context.mounted) return;
              showDialog(
                context: context,
                builder: (ctx) => AlertDialog(
                  title: const Text("Phase 27 Diagnostics"),
                  content: SingleChildScrollView(
                    child: Text(diag.entries.map((e) => "${e.key}: ${e.value}").join("\n")),
                  ),
                  actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text("OK"))],
                ),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // Header / Status Area
          _buildStatusHeader(accentColor, theme),

          // History Area
          Expanded(
            child: _history.isEmpty
              ? Center(child: Text("No commands yet", style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)))
              : ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.all(16),
                  itemCount: _history.length,
                  itemBuilder: (context, index) => _buildHistoryItem(_history[index], theme),
                ),
          ),

          // Result / Progress Card (Overlay-like)
          if (_state.status != AssistantUiStatus.idle)
            _buildActiveTaskCard(accentColor, theme),

          // Input Area
          _buildInputArea(accentColor, theme),
        ],
      ),
    );
  }

  Widget _buildStatusHeader(Color accentColor, ThemeData theme) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: accentColor.withValues(alpha: 0.1),
      child: Row(
        children: [
          CircleAvatar(
            backgroundColor: accentColor,
            radius: 12,
            child: Text(_state.personality.name[0].toUpperCase(), style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 12),
          Text(
            _getStatusLabel(),
            style: theme.textTheme.labelLarge?.copyWith(color: accentColor, fontWeight: FontWeight.bold),
          ),
          const Spacer(),
          // Personality Switch
          SegmentedButton<AssistantPersonality>(
            segments: const [
              ButtonSegment(value: AssistantPersonality.luna, label: Text("Luna"), icon: Icon(Icons.face_retouching_natural, size: 16)),
              ButtonSegment(value: AssistantPersonality.nova, label: Text("Nova"), icon: Icon(Icons.face, size: 16)),
            ],
            selected: {_state.personality},
            onSelectionChanged: (val) {
              _brainService.setPersonality(val.first);
            },
            showSelectedIcon: false,
            style: const ButtonStyle(visualDensity: VisualDensity.compact),
          ),
        ],
      ),
    );
  }

  String _getStatusLabel() {
    return switch (_state.status) {
      AssistantUiStatus.idle => "Ready",
      AssistantUiStatus.listening => "Listening...",
      AssistantUiStatus.processingVoice => "Processing...",
      AssistantUiStatus.speaking => "Speaking...",
      AssistantUiStatus.running => "Thinking...",
      AssistantUiStatus.permissionRequired => "Mic Permission Required",
      _ => "Busy",
    };
  }

  Widget _buildActiveTaskCard(Color accentColor, ThemeData theme) {
    if (_state.voiceError != null) {
      return Container(
        margin: const EdgeInsets.all(16),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: theme.colorScheme.errorContainer,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          children: [
            Icon(Icons.error_outline, color: theme.colorScheme.error),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                _state.voiceError!,
                style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.error),
              ),
            ),
            IconButton(
              icon: Icon(Icons.close, color: theme.colorScheme.error),
              onPressed: () => setState(() => _state = AssistantUiState(personality: _state.personality, status: AssistantUiStatus.idle)),
            ),
          ],
        ),
      );
    }
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: accentColor.withValues(alpha: 0.3)),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.1), blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (_state.status == AssistantUiStatus.running || _state.status == AssistantUiStatus.processingVoice)
                const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
              if (_state.status != AssistantUiStatus.running && _state.status != AssistantUiStatus.processingVoice)
                Icon(_getStatusIcon(_state.status), color: _getStatusColor(_state.status, accentColor), size: 20),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  _state.partialTranscript ?? _state.progressMessage ?? _state.status.name.toUpperCase(),
                  style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close, size: 18),
                onPressed: () => setState(() => _state = AssistantUiState(personality: _state.personality, status: AssistantUiStatus.idle)),
              ),
            ],
          ),
          if (_state.lastResult != null && _state.status != AssistantUiStatus.running && _state.status != AssistantUiStatus.listening) ...[
            const Divider(),
            Text(_state.lastResult!.resultTitle ?? "Result", style: theme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.bold, color: accentColor)),
            const SizedBox(height: 4),
            Text(_state.lastResult!.resultMessage ?? "", style: theme.textTheme.bodyMedium),
          ],
        ],
      ),
    );
  }

  Widget _buildInputArea(Color accentColor, ThemeData theme) {
    final isListening = _state.status == AssistantUiStatus.listening;
    
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        border: Border(top: BorderSide(color: theme.colorScheme.outlineVariant)),
      ),
      child: Row(
        children: [
          // Voice Button
          IconButton.filledTonal(
            icon: Icon(isListening ? Icons.stop : Icons.mic),
            onPressed: _toggleVoice,
            style: IconButton.styleFrom(
              backgroundColor: isListening ? Colors.red.withValues(alpha: 0.1) : null,
              foregroundColor: isListening ? Colors.red : null,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: _commandController,
              decoration: InputDecoration(
                hintText: "Ask ${_state.personality == AssistantPersonality.luna ? "Luna" : "Nova"}...",
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(24), borderSide: BorderSide.none),
                filled: true,
                contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
              ),
              onSubmitted: (_) => _sendCommand(),
            ),
          ),
          const SizedBox(width: 12),
          IconButton.filled(
            icon: const Icon(Icons.send),
            onPressed: _sendCommand,
            style: IconButton.styleFrom(backgroundColor: accentColor),
          ),
        ],
      ),
    );
  }

  Widget _buildHistoryItem(AssistantUiResult item, ThemeData theme) {
    final isLuna = item.personality == AssistantPersonality.luna;
    final accent = isLuna ? const Color(0xFFE85D75) : const Color(0xFF2F7CF6);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(item.commandText, style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold))),
                const SizedBox(width: 8),
                Text(_formatTimestamp(item.timestampMs), style: theme.textTheme.bodySmall),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(color: accent.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(4)),
                  child: Text(item.personality.name.toUpperCase(), style: theme.textTheme.labelSmall?.copyWith(color: accent, fontWeight: FontWeight.bold)),
                ),
                const SizedBox(width: 8),
                Icon(_getStatusIcon(item.status), size: 14, color: _getStatusColor(item.status, accent)),
                const SizedBox(width: 4),
                Text(item.status.name.toUpperCase(), style: theme.textTheme.labelSmall?.copyWith(color: _getStatusColor(item.status, accent))),
              ],
            ),
            if (item.resultMessage != null) ...[
              const SizedBox(height: 8),
              Text(item.resultMessage!, style: theme.textTheme.bodySmall, maxLines: 2, overflow: TextOverflow.ellipsis),
            ],
          ],
        ),
      ),
    );
  }

  IconData _getStatusIcon(AssistantUiStatus status) {
    return switch (status) {
      AssistantUiStatus.idle => Icons.hourglass_empty,
      AssistantUiStatus.running => Icons.sync,
      AssistantUiStatus.completed => Icons.check_circle_outline,
      AssistantUiStatus.blocked => Icons.block,
      AssistantUiStatus.needsConfirmation => Icons.help_outline,
      AssistantUiStatus.failed => Icons.error_outline,
      AssistantUiStatus.listening => Icons.mic,
      AssistantUiStatus.processingVoice => Icons.settings_voice,
      AssistantUiStatus.speaking => Icons.volume_up,
      AssistantUiStatus.permissionRequired => Icons.mic_off,
    };
  }

  Color _getStatusColor(AssistantUiStatus status, Color accent) {
    return switch (status) {
      AssistantUiStatus.completed => Colors.green,
      AssistantUiStatus.blocked => Colors.orange,
      AssistantUiStatus.needsConfirmation => Colors.blue,
      AssistantUiStatus.failed => Colors.red,
      AssistantUiStatus.listening => Colors.red,
      AssistantUiStatus.speaking => Colors.green,
      AssistantUiStatus.permissionRequired => Colors.orange,
      _ => accent,
    };
  }

  String _formatTimestamp(int ms) {
    final date = DateTime.fromMillisecondsSinceEpoch(ms);
    return "${date.hour}:${date.minute.toString().padLeft(2, '0')}";
  }
}
