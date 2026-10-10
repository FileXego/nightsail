import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Dart 侧引擎客户端：MethodChannel 命令 + EventChannel 状态流的唯一封装。
/// UI 层不直接接触平台通道。
class EngineClient {
  static const _method = MethodChannel('app.nightsail/engine');
  static const _events = EventChannel('app.nightsail/engine_events');

  Stream<EngineSnapshot> get snapshots =>
      _events.receiveBroadcastStream().map((e) => EngineSnapshot.fromMap(Map<String, dynamic>.from(e as Map)));

  Future<void> arm(String mode, int durationMinutes) =>
      _method.invokeMethod('arm', {'mode': mode, 'durationMinutes': durationMinutes});

  Future<void> disarm() => _method.invokeMethod('disarm');

  Future<void> requestEmergencyUnlock() => _method.invokeMethod('requestEmergencyUnlock');

  Future<void> setWhitelist(List<String> apps) =>
      _method.invokeMethod('setWhitelist', {'apps': apps});

  Future<EngineSnapshot> snapshot() async =>
      EngineSnapshot.fromMap(Map<String, dynamic>.from(await _method.invokeMethod('snapshot') as Map));
}

class EngineSnapshot {
  final String state;
  final String? mode;
  final int remainingSeconds;
  final bool accessibilityAlive;
  final bool emergencyRequested;

  const EngineSnapshot({
    required this.state,
    this.mode,
    required this.remainingSeconds,
    required this.accessibilityAlive,
    required this.emergencyRequested,
  });

  factory EngineSnapshot.fromMap(Map<String, dynamic> m) => EngineSnapshot(
        state: m['state'] as String? ?? 'IDLE',
        mode: m['mode'] as String?,
        remainingSeconds: (m['remainingSeconds'] as num?)?.toInt() ?? 0,
        accessibilityAlive: m['accessibilityAlive'] as bool? ?? false,
        emergencyRequested: m['emergencyRequested'] as bool? ?? false,
      );

  static const idle = EngineSnapshot(
    state: 'IDLE',
    remainingSeconds: 0,
    accessibilityAlive: false,
    emergencyRequested: false,
  );
}

final engineClientProvider = Provider<EngineClient>((ref) => EngineClient());

final engineSnapshotProvider = StreamProvider<EngineSnapshot>((ref) =>
    ref.watch(engineClientProvider).snapshots);
