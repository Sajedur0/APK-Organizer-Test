import 'package:package_info_plus/package_info_plus.dart';

class VersionUtil {
  static String _cachedVersion = '1.0.0';

  static Future<String> getAppVersion() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      _cachedVersion = packageInfo.version;
      return _cachedVersion;
    } catch (e) {
      return _cachedVersion;
    }
  }

  static String get cachedVersion => _cachedVersion;
}