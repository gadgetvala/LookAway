import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:look_away/core/constants/app_assets.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/core/constants/constants.dart';
import 'package:look_away/providers/break_provider.dart';
import 'package:look_away/providers/permissions_provider.dart';
import 'package:look_away/routes/routes.dart';
import 'package:look_away/views/screens/home/widgets/countdown_ring.dart';
import 'package:look_away/views/screens/home/widgets/schedule_section.dart';
import 'package:look_away/views/screens/home/widgets/setup_banner.dart';
import 'package:look_away/views/screens/home/widgets/timer_controls.dart';

class HomeScreen extends ConsumerWidget {
  static const route = '/home';

  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final breakState = ref.watch(breakStateProvider);
    final permissions = ref.watch(permissionsProvider).value;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        titleSpacing: PaddingValues.padding,
        title: Row(
          spacing: 12,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.asset(AppAssets.logo, width: 32, height: 32),
            ),
            const Text(kAppName, style: TextStyle(fontWeight: FontWeight.w700)),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Permissions & reliability',
            icon: const Icon(Icons.tune_rounded),
            onPressed: () => Navigator.of(context).pushNamed(Routes.permissionsScreen),
          ),
        ],
      ),
      body: SafeArea(
        child: switch (breakState) {
          AsyncData(:final value) => ListView(
            padding: const EdgeInsets.all(PaddingValues.padding),
            children: [
              if (permissions != null && !permissions.allGranted) ...[
                SetupBanner(permissions: permissions),
                const SizedBox(height: PaddingValues.padding),
              ],
              Center(child: CountdownRing(state: value)),
              const SizedBox(height: PaddingValues.padding * 1.5),
              TimerControls(state: value),
              const SizedBox(height: PaddingValues.paddingMedium),
              ScheduleSection(state: value),
              const SizedBox(height: PaddingValues.padding),
              Text(
                'Every ${value.workMinutes} minutes, look at something 20 feet away for '
                '${value.breakSeconds} seconds. The timer only runs while your screen is on and unlocked.',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.secondaryTextColor, fontSize: 13, height: 1.4),
              ),
            ],
          ),
          AsyncError(:final error) => Center(child: Text('Couldn\'t reach the timer service.\n$error')),
          _ => const Center(child: CircularProgressIndicator()),
        },
      ),
    );
  }
}
