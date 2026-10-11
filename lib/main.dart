import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:look_away/core/constants/constants.dart';
import 'package:look_away/core/utils/theme_utils.dart';
import 'package:look_away/routes/routes.dart';
import 'package:look_away/services/break_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // First run (or overlay permission revoked) goes straight to setup.
  final permissions = await BreakService.permissions();

  runApp(
    ProviderScope(child: LookAway(initialRoute: permissions.overlay ? Routes.homeScreen : Routes.permissionsScreen)),
  );
}

class LookAway extends StatelessWidget {
  final String initialRoute;

  const LookAway({super.key, required this.initialRoute});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: kAppName,
      theme: ThemeUtils.theme,
      initialRoute: initialRoute,
      onGenerateRoute: RouteGenerator.generateRoute,
    );
  }
}
