import 'package:flutter/services.dart';
import 'package:look_away/models/app_permissions.dart';
import 'package:look_away/models/break_state.dart';

/// Thin wrapper over the platform channels. All timing lives natively in BreakService.kt;
/// Flutter only sends commands and renders the state stream.
class BreakService {
  BreakService._();

  static const _control = MethodChannel('com.gadgetvala.lookaway/control');
  static const _state = EventChannel('com.gadgetvala.lookaway/state');

  static final Stream<BreakState> states = _state.receiveBroadcastStream().map(
    (event) => BreakState.fromMap(event as Map<Object?, Object?>),
  );

  static Future<void> start() => _control.invokeMethod('start');

  static Future<void> stop() => _control.invokeMethod('stop');

  static Future<void> reset() => _control.invokeMethod('reset');

  static Future<void> breakNow() => _control.invokeMethod('breakNow');

  static Future<void> skip() => _control.invokeMethod('skip');

  static Future<void> resume() => _control.invokeMethod('resume');

  static Future<void> pause(int minutes) => _control.invokeMethod('pause', {'minutes': minutes});

  static Future<void> setConfig({
    required int workMinutes,
    required int breakSeconds,
    required bool resetAfterScreenOff,
  }) => _control.invokeMethod('setConfig', {
    'workMin': workMinutes,
    'breakSec': breakSeconds,
    'resetAfterScreenOff': resetAfterScreenOff,
  });

  static Future<AppPermissions> permissions() async =>
      AppPermissions.fromMap(await _control.invokeMethod<Map<Object?, Object?>>('getPermissions') ?? const {});

  static Future<void> requestOverlay() => _control.invokeMethod('requestOverlay');

  static Future<void> requestNotifications() => _control.invokeMethod('requestNotifications');

  /// App info page: battery "Unrestricted" on Pixel/Samsung, plus "Autostart" on Xiaomi/Oppo/Vivo.
  static Future<void> openAppSettings() => _control.invokeMethod('openAppSettings');
}
