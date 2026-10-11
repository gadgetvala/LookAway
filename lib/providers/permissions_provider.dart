import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:look_away/models/app_permissions.dart';
import 'package:look_away/services/break_service.dart';

final permissionsProvider = AsyncNotifierProvider<PermissionsNotifier, AppPermissions>(PermissionsNotifier.new);

/// Permissions are granted in system Settings, outside the app, so re-check whenever we come back.
class PermissionsNotifier extends AsyncNotifier<AppPermissions> {
  @override
  Future<AppPermissions> build() {
    final listener = AppLifecycleListener(onResume: refresh);
    ref.onDispose(listener.dispose);
    return BreakService.permissions();
  }

  Future<void> refresh() async => state = AsyncData(await BreakService.permissions());
}
