import 'package:flutter/foundation.dart';

const String kAppName = 'LookAway';

class BreakOptions {
  BreakOptions._();

  /// "Break every" choices in minutes. The 1 minute option only exists in debug builds, for testing.
  static const List<int> workMinutes = [if (kDebugMode) 1, 10, 20, 30, 45, 60];

  /// "Break length" choices in seconds.
  static const List<int> breakSeconds = [20, 30, 60];

  /// "Pause for" choices in minutes (same as the macOS "Skip For" menu).
  static const List<int> pauseMinutes = [10, 30, 60, 120];
}

class PaddingValues {
  PaddingValues._();

  static const double padding = 20;
  static const double paddingMedium = 15;
  static const double paddingSmall = 10;
}

class BorderRadiusValues {
  BorderRadiusValues._();

  static const double radius = 20;
  static const double radiusSmall = 12;
}
