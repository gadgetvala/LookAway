import 'package:flutter/material.dart';
import 'package:look_away/views/screens/home/home.dart';
import 'package:look_away/views/screens/permissions/permissions.dart';

class Routes {
  Routes._();

  static const String homeScreen = HomeScreen.route;
  static const String permissionsScreen = PermissionsScreen.route;
}

class RouteGenerator {
  RouteGenerator._();

  static Route<dynamic>? generateRoute(RouteSettings settings) {
    return switch (settings.name) {
      Routes.homeScreen => MaterialPageRoute(settings: settings, builder: (_) => const HomeScreen()),
      Routes.permissionsScreen => MaterialPageRoute(settings: settings, builder: (_) => const PermissionsScreen()),
      _ => null,
    };
  }
}
