import 'package:flutter/material.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/core/constants/constants.dart';

class PermissionTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final bool granted;
  final bool required;
  final String actionLabel;
  final VoidCallback onPressed;

  const PermissionTile({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    required this.granted,
    required this.onPressed,
    this.required = false,
    this.actionLabel = 'Allow',
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(PaddingValues.paddingMedium),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: PaddingValues.paddingMedium,
          children: [
            Icon(icon, color: AppColors.primaryColor),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: 4,
                children: [
                  Text.rich(
                    TextSpan(
                      text: title,
                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
                      children: [
                        TextSpan(
                          text: required ? '  Required' : '  Recommended',
                          style: const TextStyle(
                            color: AppColors.secondaryTextColor,
                            fontWeight: FontWeight.w400,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Text(description, style: const TextStyle(color: AppColors.secondaryTextColor, height: 1.35)),
                ],
              ),
            ),
            granted
                ? const Padding(
                    padding: EdgeInsets.all(8),
                    child: Icon(Icons.check_circle_rounded, color: AppColors.success, semanticLabel: 'Granted'),
                  )
                : FilledButton.tonal(
                    style: FilledButton.styleFrom(minimumSize: const Size(0, 40)),
                    onPressed: onPressed,
                    child: Text(actionLabel),
                  ),
          ],
        ),
      ),
    );
  }
}
