import 'package:flutter/material.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/core/constants/constants.dart';
import 'package:look_away/models/break_state.dart';
import 'package:look_away/services/break_service.dart';
import 'package:look_away/views/widgets/option_chips.dart';
import 'package:look_away/views/widgets/section_card.dart';

class ScheduleSection extends StatelessWidget {
  final BreakState state;

  const ScheduleSection({super.key, required this.state});

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: 'Schedule',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Break every'),
          const SizedBox(height: 8),
          OptionChips<int>(
            options: BreakOptions.workMinutes,
            selected: state.workMinutes,
            label: (minutes) => '$minutes min',
            onSelected: (minutes) => _save(workMinutes: minutes),
          ),
          const SizedBox(height: PaddingValues.paddingMedium),
          const Text('Break length'),
          const SizedBox(height: 8),
          OptionChips<int>(
            options: BreakOptions.breakSeconds,
            selected: state.breakSeconds,
            label: (seconds) => '$seconds s',
            onSelected: (seconds) => _save(breakSeconds: seconds),
          ),
          const SizedBox(height: PaddingValues.paddingMedium),
          const Text('When the screen turns back on'),
          const SizedBox(height: 8),
          OptionChips<bool>(
            options: const [false, true],
            selected: state.resetAfterScreenOff,
            label: (reset) => reset ? 'Restart timer' : 'Continue timer',
            onSelected: (reset) => _save(resetAfterScreenOff: reset),
          ),
          const SizedBox(height: 6),
          Text(
            state.resetAfterScreenOff
                ? 'Starts a fresh ${state.workMinutes} min if the screen was off for at least ${state.breakSeconds} s. '
                      'That already counts as a break.'
                : 'Picks up exactly where the countdown stopped.',
            style: const TextStyle(color: AppColors.secondaryTextColor, fontSize: 13),
          ),
        ],
      ),
    );
  }

  Future<void> _save({int? workMinutes, int? breakSeconds, bool? resetAfterScreenOff}) => BreakService.setConfig(
    workMinutes: workMinutes ?? state.workMinutes,
    breakSeconds: breakSeconds ?? state.breakSeconds,
    resetAfterScreenOff: resetAfterScreenOff ?? state.resetAfterScreenOff,
  );
}
