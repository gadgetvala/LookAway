class AppPermissions {
  /// "Display over other apps": required for the break overlay.
  final bool overlay;

  /// Required on Android 13+ to see the countdown and the pre-break warning.
  final bool notifications;

  /// Exempt from battery optimization, so Android is far less likely to kill the timer.
  final bool battery;

  const AppPermissions({required this.overlay, required this.notifications, required this.battery});

  factory AppPermissions.fromMap(Map<Object?, Object?> map) => AppPermissions(
    overlay: map['overlay'] as bool? ?? false,
    notifications: map['notifications'] as bool? ?? false,
    battery: map['battery'] as bool? ?? false,
  );

  bool get allGranted => overlay && notifications && battery;
}
