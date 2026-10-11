enum BreakPhase {
  stopped,
  working,
  onBreak,
  paused;

  static BreakPhase fromName(String name) => switch (name) {
    'working' => working,
    'break' => onBreak,
    'paused' => paused,
    _ => stopped,
  };
}
