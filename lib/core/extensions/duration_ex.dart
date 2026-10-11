extension DurationEx on Duration {
  /// 19:59, or 1:59:59 when an hour or longer.
  String get clock {
    final seconds = inSeconds.remainder(60).toString().padLeft(2, '0');
    if (inHours == 0) return '$inMinutes:$seconds';
    final minutes = inMinutes.remainder(60).toString().padLeft(2, '0');
    return '$inHours:$minutes:$seconds';
  }

  /// 10 min, 1 hr, 2 hr.
  String get label => inMinutes >= 60 ? '$inHours hr' : '$inMinutes min';
}
