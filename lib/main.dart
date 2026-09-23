import 'package:flutter/material.dart';

import 'services/preferences_service.dart';
import 'src/entry_point.dart';

export 'src/entry_point.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await PreferencesService.instance.init();
  runApp(const ApkManagerApp());
}
