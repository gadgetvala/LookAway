import 'dart:async';

import 'package:flutter/material.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/core/extensions/duration_ex.dart';
import 'package:look_away/core/utils/enums.dart';
import 'package:look_away/models/break_state.dart';

class CountdownRing extends StatefulWidget {
  final BreakState state;

  const CountdownRing({super.key, required this.state});

  @override
  State<CountdownRing> createState() => _CountdownRingState();
}

class _CountdownRingState extends State<CountdownRing> {
  late final Timer _ticker;

  @override
  void initState() {
    super.initState();
    // Native pushes only state changes; the per-second countdown is extrapolated locally.
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => setState(() {}));
  }

  @override
  void dispose() {
    _ticker.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final left = state.remainingAt(DateTime.now());
    final length = state.phaseLength;

    final (color, caption) = switch (state.phase) {
      BreakPhase.stopped => (AppColors.borderColor, 'Not running'),
      BreakPhase.working => (AppColors.primaryColor, 'until your next break'),
      BreakPhase.onBreak => (AppColors.success, 'Look away now'),
      BreakPhase.paused => (AppColors.warning, 'paused, resumes in'),
    };
    final shown = state.isRunning ? left : Duration(minutes: state.workMinutes);

    return Semantics(
      label: state.isRunning ? '${shown.clock} $caption' : caption,
      excludeSemantics: true,
      child: SizedBox.square(
        dimension: 260,
        child: Stack(
          fit: StackFit.expand,
          children: [
            CircularProgressIndicator(
              value: length == null ? 1 : (left.inMilliseconds / length.inMilliseconds).clamp(0.0, 1.0),
              strokeWidth: 10,
              strokeCap: StrokeCap.round,
              color: color,
              backgroundColor: AppColors.borderColor,
            ),
            Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  shown.clock,
                  style: TextStyle(
                    fontSize: 60,
                    fontWeight: FontWeight.w700,
                    color: state.isRunning ? AppColors.primaryTextColor : AppColors.secondaryTextColor,
                    fontFeatures: const [FontFeature.tabularFigures()],
                  ),
                ),
                Text(caption, style: const TextStyle(color: AppColors.secondaryTextColor, fontSize: 15)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
