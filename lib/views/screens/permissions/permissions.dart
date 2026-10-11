import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/core/constants/constants.dart';
import 'package:look_away/providers/permissions_provider.dart';
import 'package:look_away/routes/routes.dart';
import 'package:look_away/services/break_service.dart';
import 'package:look_away/views/screens/permissions/widgets/permission_tile.dart';

/// First-run setup, and the place to come back to when a phone keeps killing the timer.
class PermissionsScreen extends ConsumerWidget {
  static const route = '/permissions';

  const PermissionsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final permissions = ref.watch(permissionsProvider).value;
    final canPop = Navigator.of(context).canPop();

    return Scaffold(
      appBar: AppBar(backgroundColor: Colors.transparent, title: const Text('Set up $kAppName')),
      body: SafeArea(
        child: permissions == null
            ? const Center(child: CircularProgressIndicator())
            : ListView(
                padding: const EdgeInsets.all(PaddingValues.padding),
                children: [
                  const Text(
                    'LookAway runs quietly in the background and covers the screen when it\'s time for a break. '
                    'Android needs your OK for that.',
                    style: TextStyle(color: AppColors.secondaryTextColor, height: 1.4),
                  ),
                  const SizedBox(height: PaddingValues.padding),
                  PermissionTile(
                    icon: Icons.layers_outlined,
                    title: 'Display over other apps',
                    description: 'Shows the full-screen break reminder on top of whatever you\'re using.',
                    granted: permissions.overlay,
                    required: true,
                    onPressed: BreakService.requestOverlay,
                  ),
                  const SizedBox(height: PaddingValues.paddingSmall),
                  PermissionTile(
                    icon: Icons.notifications_outlined,
                    title: 'Notifications',
                    description: 'Shows the countdown, a 20 second warning, and a sound when the break ends.',
                    granted: permissions.notifications,
                    onPressed: BreakService.requestNotifications,
                  ),
                  const SizedBox(height: PaddingValues.paddingSmall),
                  PermissionTile(
                    icon: Icons.battery_charging_full_rounded,
                    title: 'Run in background',
                    description:
                        'In App info, open Battery and choose "Unrestricted". On Xiaomi, Oppo, Vivo and Realme '
                        'also turn on "Autostart". Without this, some phones stop the timer.',
                    granted: permissions.battery,
                    actionLabel: 'Open',
                    onPressed: BreakService.openAppSettings,
                  ),
                  const SizedBox(height: PaddingValues.padding * 1.5),
                  FilledButton(
                    onPressed: permissions.overlay
                        ? () => canPop
                              ? Navigator.of(context).pop()
                              : Navigator.of(context).pushReplacementNamed(Routes.homeScreen)
                        : null,
                    child: Text(canPop ? 'Done' : 'Continue'),
                  ),
                ],
              ),
      ),
    );
  }
}
