class InstalledApp {
  final String appName;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String? iconPath;
  final String sourceDir;
  final int size;
  final bool isSystemApp;

  const InstalledApp({
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

  String get displayName => appName.trim().isEmpty ? packageName : appName;

  String get searchableText => [
    displayName,
    packageName,
    versionName,
    versionCode.toString(),
  ].join(' ').toLowerCase();

  String get formattedSize {
    if (size <= 0) return 'Unknown size';
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  static int compareByName(InstalledApp a, InstalledApp b) {
    final name = a.displayName.toLowerCase().compareTo(
      b.displayName.toLowerCase(),
    );
    if (name != 0) return name;
    return a.packageName.compareTo(b.packageName);
  }
}
