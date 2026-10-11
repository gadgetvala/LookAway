import 'package:flutter_test/flutter_test.dart';
import 'package:look_away/core/extensions/duration_ex.dart';
import 'package:look_away/core/utils/enums.dart';
import 'package:look_away/models/break_state.dart';

void main() {
  final t0 = DateTime(2026);
  BreakState state(String phase, {bool screenOn = true}) => BreakState.fromMap({
    'phase': phase,
    'timeLeftMs': 60000,
    'screenOn': screenOn,
    'workMin': 20,
    'breakSec': 20,
  }, now: t0);

  test('parses native phases and settings', () {
    expect(state('working').resetAfterScreenOff, isFalse);
    expect(state('break').phase, BreakPhase.onBreak);
    expect(state('working').phase, BreakPhase.working);
    expect(state('garbage').phase, BreakPhase.stopped);
  });

  test('extrapolates the countdown locally and never goes negative', () {
    expect(state('working').remainingAt(t0.add(const Duration(seconds: 15))), const Duration(seconds: 45));
    expect(state('working').remainingAt(t0.add(const Duration(minutes: 5))), Duration.zero);
  });

  test('frozen while the screen is off', () {
    expect(
      state('working', screenOn: false).remainingAt(t0.add(const Duration(minutes: 5))),
      const Duration(minutes: 1),
    );
  });

  test('formats clocks', () {
    expect(const Duration(minutes: 19, seconds: 5).clock, '19:05');
    expect(const Duration(hours: 1, minutes: 2, seconds: 3).clock, '1:02:03');
    expect(const Duration(minutes: 120).label, '2 hr');
  });
}
