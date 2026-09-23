class ApkFile {
  static final RegExp _unsafeFileChars = RegExp(r'[<>:"/\\|?*\x00-\x1F]');
  static final RegExp _whitespace = RegExp(r'\s+');

  final String fileName;
  final String path;
  final int size;
  final String appName;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String? iconPath;
  final int lastModified;

  const ApkFile({
    required this.fileName,
    required this.path,
    required this.size,
    required this.appName,
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    this.iconPath,
    this.lastModified = 0,
  });

  ApkFile copyWith({
    String? fileName,
    String? path,
    int? size,
    String? appName,
    String? packageName,
    String? versionName,
    int? versionCode,
    String? Function()? iconPath,
    int? lastModified,
  }) {
    return ApkFile(
      fileName: fileName ?? this.fileName,
      path: path ?? this.path,
      size: size ?? this.size,
      appName: appName ?? this.appName,
      packageName: packageName ?? this.packageName,
      versionName: versionName ?? this.versionName,
      versionCode: versionCode ?? this.versionCode,
      iconPath: iconPath != null ? iconPath() : this.iconPath,
      lastModified: lastModified ?? this.lastModified,
    );
  }

  factory ApkFile.fromMap(Map<String, dynamic> map) {
    return ApkFile(
      fileName: map['fileName'] as String? ?? '',
      path: map['path'] as String? ?? '',
      size: (map['size'] as num?)?.toInt() ?? 0,
      appName: map['appName'] as String? ?? '',
      packageName: map['packageName'] as String? ?? 'unknown',
      versionName: map['versionName'] as String? ?? 'Unknown',
      versionCode: (map['versionCode'] as num?)?.toInt() ?? 0,
      iconPath: map['iconPath'] as String?,
      lastModified: (map['lastModified'] as num?)?.toInt() ?? 0,
    );
  }

  String get displayName {
    final trimmedAppName = appName.trim();
    if (trimmedAppName.isNotEmpty) return trimmedAppName;
    final trimmedFileName = fileName.trim();
    if (trimmedFileName.isNotEmpty) return trimmedFileName;
    return packageName == 'unknown' ? 'Unknown APK' : packageName;
  }

  String get searchableText => [
    displayName,
    fileName,
    packageName,
    versionName,
    versionCode.toString(),
    path,
  ].join(' ').toLowerCase();

  String get duplicateIdentity {
    final normalizedPackage = packageName.trim().toLowerCase();
    if (normalizedPackage.isNotEmpty && normalizedPackage != 'unknown') {
      final versionPart = versionCode > 0
          ? versionCode.toString()
          : versionName.trim().toLowerCase();
      return 'pkg:$normalizedPackage|version:$versionPart';
    }

    return 'fallback:${displayName.toLowerCase()}|${versionName.trim().toLowerCase()}';
  }

  static int compareByDisplayName(ApkFile a, ApkFile b) {
    final nameCompare = a.displayName.toLowerCase().compareTo(
      b.displayName.toLowerCase(),
    );
    if (nameCompare != 0) return nameCompare;
    final versionCompare = b.versionCode.compareTo(a.versionCode);
    if (versionCompare != 0) return versionCompare;
    return a.fileName.toLowerCase().compareTo(b.fileName.toLowerCase());
  }

  String get formattedSize {
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get suggestedRename {
    final safeAppName = _sanitizeFilePart(displayName);
    final versionSource =
        versionName.trim().isNotEmpty && versionName != 'Unknown'
        ? versionName
        : versionCode > 0
        ? versionCode.toString()
        : 'unknown';
    final safeVersion = _sanitizeFilePart(versionSource);
    return '${safeAppName}_$safeVersion.apk';
  }

  static String _sanitizeFilePart(String value) {
    final sanitized = value
        .trim()
        .replaceAll(_unsafeFileChars, '_')
        .replaceAll(_whitespace, '_')
        .replaceAll(RegExp(r'_+'), '_')
        .replaceAll(RegExp(r'^[._\s]+|[._\s]+$'), '');
    if (sanitized.isEmpty) return 'unknown';
    return sanitized.length > 80 ? sanitized.substring(0, 80) : sanitized;
  }

  @override
  String toString() =>
      'ApkFile(name: $appName, path: $path, size: $formattedSize)';
}
