import 'package:look_away/core/utils/enums.dart';

/// Snapshot of the native timer, pushed by the service on every change.
class BreakState {
  final BreakPhase phase;

  /// Time left in the current phase when this snapshot was taken.
  final Duration timeLeft;

  /// Work time only counts while the screen is on; false means the countdown is frozen.
  final bool screenOn;
  final int workMinutes;
  final int breakSeconds;

  /// When the screen comes back on: false continues the countdown, true restarts it after a full break.
  final bool resetAfterScreenOff;
  final DateTime receivedAt;

  const BreakState({
    required this.phase,
    required this.timeLeft,
    required this.screenOn,
    required this.workMinutes,
    required this.breakSeconds,
    required this.resetAfterScreenOff,
    required this.receivedAt,
  });

  factory BreakState.fromMap(Map<Object?, Object?> map, {DateTime? now}) => BreakState(
    phase: BreakPhase.fromName(map['phase'] as String? ?? ''),
    timeLeft: Duration(milliseconds: (map['timeLeftMs'] as num? ?? 0).toInt()),
    screenOn: map['screenOn'] as bool? ?? true,
    workMinutes: (map['workMin'] as num? ?? 20).toInt(),
    breakSeconds: (map['breakSec'] as num? ?? 20).toInt(),
    resetAfterScreenOff: map['resetAfterScreenOff'] as bool? ?? false,
    receivedAt: now ?? DateTime.now(),
  );

  bool get isRunning => phase != BreakPhase.stopped;

  /// Time left right now, extrapolated locally so the UI can tick without asking native every second.
  Duration remainingAt(DateTime now) {
    if (phase == BreakPhase.stopped) return Duration.zero;
    if (phase == BreakPhase.working && !screenOn) return timeLeft;
    final left = timeLeft - now.difference(receivedAt);
    return left.isNegative ? Duration.zero : left;
  }

  /// Full length of the current phase, for the progress ring. Null when it has no fixed length.
  Duration? get phaseLength => switch (phase) {
    BreakPhase.working => Duration(minutes: workMinutes),
    BreakPhase.onBreak => Duration(seconds: breakSeconds),
    _ => null,
  };
}
