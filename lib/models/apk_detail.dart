class ApkPermission {
  final String name;
  final bool granted;

  const ApkPermission({required this.name, required this.granted});

  factory ApkPermission.fromMap(Map<String, dynamic> map) {
    return ApkPermission(
      name: map['name'] as String? ?? '',
      granted: map['granted'] as bool? ?? false,
    );
  }

  String get shortName => name.startsWith('android.permission.')
      ? name.substring(20)
      : name.startsWith('com.')
          ? name.split('.').last
          : name;
}

class ApkDetailInfo {
  final String packageName;
  final String versionName;
  final int versionCode;
  final String appName;
  final int minSdkVersion;
  final int targetSdkVersion;
  final List<String> supportedAbis;
  final String? signatureHash;
  final String? iconPath;
  final int fileSize;
  final String fileName;
  final String filePath;
  final List<ApkPermission> permissions;

  const ApkDetailInfo({
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    required this.appName,
    required this.minSdkVersion,
    required this.targetSdkVersion,
    required this.supportedAbis,
    this.signatureHash,
    this.iconPath,
    required this.fileSize,
    required this.fileName,
    required this.filePath,
    required this.permissions,
  });

  factory ApkDetailInfo.fromMap(Map<String, dynamic> map) {
    final perms = (map['permissions'] as List<dynamic>?)
            ?.map((e) => ApkPermission.fromMap(Map<String, dynamic>.from(e)))
            .toList() ??
        [];
    final abis = (map['supportedAbis'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toList() ??
        [];
    return ApkDetailInfo(
      packageName: map['packageName'] as String? ?? '',
      versionName: map['versionName'] as String? ?? 'Unknown',
      versionCode: (map['versionCode'] as num?)?.toInt() ?? 0,
      appName: map['appName'] as String? ?? '',
      minSdkVersion: (map['minSdkVersion'] as num?)?.toInt() ?? 1,
      targetSdkVersion: (map['targetSdkVersion'] as num?)?.toInt() ?? 1,
      supportedAbis: abis,
      signatureHash: map['signatureHash'] as String?,
      iconPath: map['iconPath'] as String?,
      fileSize: (map['fileSize'] as num?)?.toInt() ?? 0,
      fileName: map['fileName'] as String? ?? '',
      filePath: map['filePath'] as String? ?? '',
      permissions: perms,
    );
  }

  String get formattedSize {
    final size = fileSize;
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get signatureDisplay {
    if (signatureHash == null) return 'Unknown';
    final s = signatureHash!;
    if (s.length <= 16) return s;
    return '${s.substring(0, 8)}...${s.substring(s.length - 8)}';
  }

  String get sdkDisplay =>
      'API $minSdkVersion → API $targetSdkVersion';

  String get abisDisplay =>
      supportedAbis.isEmpty ? 'Unknown' : supportedAbis.join(', ');
}
