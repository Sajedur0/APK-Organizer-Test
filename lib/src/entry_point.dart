import 'package:flutter/material.dart';

import '../app_theme.dart';
import '../screens/home_page.dart';
import '../services/app_update_service.dart';
import '../services/preferences_service.dart';
import '../utils/version_util.dart';

class ApkManagerApp extends StatefulWidget {
  const ApkManagerApp({super.key});

  @override
  State<ApkManagerApp> createState() => _ApkManagerAppState();
}

class _ApkManagerAppState extends State<ApkManagerApp> {
  String _appVersion = '1.0.0';

  @override
  void initState() {
    super.initState();
    // Register the in-app update observer (handles resume, flexible listener).
    AppUpdateService.instance.init();
    VersionUtil.getAppVersion().then((version) {
      if (mounted) {
        setState(() => _appVersion = version);
      }
    });
    // Note: The primary update check is triggered from HomePage.initState via
    // AppUpdateService.checkAndPromptUpdate() after the first frame (spec
    // integration point). This avoids blocking splash/startup and ensures
    // Scaffold/Navigator context is ready. No duplicate check here.
  }

  @override
  void dispose() {
    // Note: AppUpdateService is a singleton; removing the observer here is
    // only relevant for hot-restart / tests. The install-state subscription
    // is kept alive for the app lifetime.
    // AppUpdateService.instance.dispose(); // intentionally not called to keep flexible listener alive
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: PreferencesService.instance.themeMode,
      builder: (context, mode, _) {
        return MaterialApp(
          title: 'APK Organizer',
          debugShowCheckedModeBanner: false,
          navigatorKey: AppUpdateService.instance.navigatorKey,
          theme: AppTheme.light,
          darkTheme: AppTheme.dark,
          themeMode: mode,
          home: HomePage(
            appVersion: _appVersion,
          ),
        );
      },
    );
  }
}
