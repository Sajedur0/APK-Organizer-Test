import '../utils/format_util.dart';

/// One installed application on the device.
///
/// Derived strings (display name, search text, size label) are cached on the
/// instance so list builds and searches never re-compute them.
class InstalledApp {
  final String appName;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String? iconPath;
  final String sourceDir;
  final int size;
  final bool isSystemApp;

  InstalledApp({
    required this.appName,
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    required this.iconPath,
    required this.sourceDir,
    required this.size,
    required this.isSystemApp,
  });

  factory InstalledApp.fromMap(Map<String, dynamic> map) {
    return InstalledApp(
      appName: map['appName'] as String? ?? '',
      packageName: map['packageName'] as String? ?? '',
      versionName: map['versionName'] as String? ?? 'Unknown',
      versionCode: (map['versionCode'] as num?)?.toInt() ?? 0,
      iconPath: map['iconPath'] as String?,
      sourceDir: map['sourceDir'] as String? ?? '',
      size: (map['size'] as num?)?.toInt() ?? 0,
      isSystemApp: map['isSystemApp'] as bool? ?? false,
    );
  }

  late final String displayName =
      appName.trim().isEmpty ? packageName : appName;

  /// Lower-cased [displayName] — primary sort key.
  late final String sortName = displayName.toLowerCase();

  /// Lower-cased haystack used by the search box.
  late final String searchLower = [
    displayName,
    packageName,
    versionName,
    versionCode.toString(),
  ].join(' ').toLowerCase();

  /// Backwards compatible alias of [searchLower].
  String get searchableText => searchLower;

  late final String formattedSize =
      size <= 0 ? 'Unknown size' : FormatUtil.formatBytes(size);

  String get versionLabel => 'v$versionName ($versionCode)';

  static int compareByName(InstalledApp a, InstalledApp b) {
    final name = a.sortName.compareTo(b.sortName);
    if (name != 0) return name;
    return a.packageName.compareTo(b.packageName);
  }

  static int compareBySize(InstalledApp a, InstalledApp b) {
    final sizeCompare = a.size.compareTo(b.size);
    if (sizeCompare != 0) return sizeCompare;
    return compareByName(a, b);
  }
}
