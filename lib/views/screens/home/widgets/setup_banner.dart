import 'package:flutter/material.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/models/app_permissions.dart';
import 'package:look_away/routes/routes.dart';

/// Nudges toward the permissions screen until everything that keeps breaks reliable is granted.
class SetupBanner extends StatelessWidget {
  final AppPermissions permissions;

  const SetupBanner({super.key, required this.permissions});

  @override
  Widget build(BuildContext context) {
    final critical = !permissions.overlay;
    return Card(
      color: (critical ? Theme.of(context).colorScheme.error : AppColors.warning).withValues(alpha: 0.12),
      child: ListTile(
        leading: Icon(
          critical ? Icons.error_outline_rounded : Icons.shield_outlined,
          color: critical ? Theme.of(context).colorScheme.error : AppColors.warning,
        ),
        title: Text(critical ? 'Overlay permission needed' : 'Finish setup'),
        subtitle: Text(
          critical
              ? 'LookAway can\'t show breaks over other apps yet.'
              : 'Allow notifications and background use so Android doesn\'t stop your breaks.',
        ),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: () => Navigator.of(context).pushNamed(Routes.permissionsScreen),
      ),
    );
  }
}
