import 'package:flutter/material.dart';
import 'package:look_away/core/constants/app_colors.dart';
import 'package:look_away/core/constants/constants.dart';

class ThemeUtils {
  ThemeUtils._();

  /// Always dark, like the break overlay: this is an app you glance at, not one you read.
  static ThemeData get theme {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: AppColors.primaryColor,
      brightness: Brightness.dark,
    ).copyWith(primary: AppColors.primaryColor, surface: AppColors.backgroundColor);

    return ThemeData(
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.backgroundColor,
      cardTheme: CardThemeData(
        color: AppColors.cardColor,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(BorderRadiusValues.radius),
          side: const BorderSide(color: AppColors.borderColor),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(52),
          foregroundColor: Colors.white,
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(52),
          side: const BorderSide(color: AppColors.borderColor),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),
      chipTheme: const ChipThemeData(showCheckmark: false, side: BorderSide(color: AppColors.borderColor)),
    );
  }
}
