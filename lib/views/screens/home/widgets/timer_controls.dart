import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:look_away/core/constants/constants.dart';
import 'package:look_away/core/extensions/duration_ex.dart';
import 'package:look_away/core/utils/enums.dart';
import 'package:look_away/models/break_state.dart';
import 'package:look_away/providers/permissions_provider.dart';
import 'package:look_away/routes/routes.dart';
import 'package:look_away/services/break_service.dart';
import 'package:look_away/views/widgets/option_chips.dart';
import 'package:look_away/views/widgets/section_card.dart';

/// Equivalent of the macOS menu: Reset, Skip For, Quit, plus Start and Break now.
class TimerControls extends ConsumerWidget {
  final BreakState state;

  const TimerControls({super.key, required this.state});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (!state.isRunning) {
      return FilledButton.icon(
        icon: const Icon(Icons.play_arrow_rounded),
        label: const Text('Start'),
        onPressed: () async {
          final permissions = await ref.read(permissionsProvider.future);
          if (!context.mounted) return;
          // Without the overlay permission a break can't be shown, so set that up first.
          if (!permissions.overlay) {
            Navigator.of(context).pushNamed(Routes.permissionsScreen);
            return;
          }
          await BreakService.start();
        },
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: PaddingValues.paddingMedium,
      children: [
        Row(
          spacing: PaddingValues.paddingSmall,
          children: [
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.visibility_off_outlined),
                label: const Text('Break now'),
                onPressed: state.phase == BreakPhase.onBreak ? null : BreakService.breakNow,
              ),
            ),
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.restart_alt_rounded),
                label: const Text('Reset'),
                onPressed: BreakService.reset,
              ),
            ),
          ],
        ),
        SectionCard(
          title: 'Pause for',
          child: state.phase == BreakPhase.paused
              ? FilledButton.tonal(onPressed: BreakService.resume, child: const Text('Resume now'))
              : OptionChips<int>(
                  options: BreakOptions.pauseMinutes,
                  label: (minutes) => Duration(minutes: minutes).label,
                  onSelected: BreakService.pause,
                ),
        ),
        TextButton.icon(
          icon: const Icon(Icons.stop_rounded),
          label: const Text('Stop LookAway'),
          style: TextButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
          onPressed: BreakService.stop,
        ),
      ],
    );
  }
}
