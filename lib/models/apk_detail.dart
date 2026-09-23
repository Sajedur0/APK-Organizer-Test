import '../utils/format_util.dart';

/// A permission requested by an APK, as declared in its manifest.
class ApkPermission {
  /// Full permission string, e.g. `android.permission.INTERNET`.
  final String name;

  /// Whether the flag was set in the archive manifest. Note that for an APK
  /// file (not yet installed) this only reflects the manifest flags, not a
  /// runtime grant.
  final bool granted;

  const ApkPermission({required this.name, required this.granted});

  factory ApkPermission.fromMap(Map<String, dynamic> map) {
    return ApkPermission(
      name: map['name'] as String? ?? '',
      granted: map['granted'] as bool? ?? false,
    );
  }

  static const String androidPrefix = 'android.permission.';

  /// Short, readable name: `INTERNET` instead of `android.permission.INTERNET`.
  late final String shortName = () {
    if (name.startsWith(androidPrefix)) {
      return name.substring(androidPrefix.length);
    }
    if (name.startsWith('com.') || name.startsWith('android.')) {
      final parts = name.split('.');
      return parts.isEmpty ? name : parts.last;
    }
    return name;
  }();

  /// Best-effort grouping used for the "risky" hint in the detail page.
  late final bool isSensitive = name.startsWith(androidPrefix) &&
      const <String>{
        'SEND_SMS',
        'RECEIVE_SMS',
        'READ_SMS',
        'CALL_PHONE',
        'READ_CONTACTS',
        'WRITE_CONTACTS',
        'RECORD_AUDIO',
        'CAMERA',
        'ACCESS_FINE_LOCATION',
        'ACCESS_BACKGROUND_LOCATION',
        'READ_CALL_LOG',
        'WRITE_CALL_LOG',
        'READ_PHONE_STATE',
        'SYSTEM_ALERT_WINDOW',
        'REQUEST_INSTALL_PACKAGES',
      }.contains(shortName);

  @override
  String toString() => name;
}

/// Full details of a single APK file (parsed natively).
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

  ApkDetailInfo({
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
    final rawPermissions = map['permissions'];
    final List<ApkPermission> permissions = <ApkPermission>[];
    if (rawPermissions is List) {
      for (final entry in rawPermissions) {
        if (entry is Map) {
          permissions.add(
            ApkPermission.fromMap(Map<String, dynamic>.from(entry)),
          );
        }
      }
    }

    final rawAbis = map['supportedAbis'];
    final List<String> abis = <String>[];
    if (rawAbis is List) {
      for (final entry in rawAbis) {
        final value = entry?.toString() ?? '';
        if (value.isNotEmpty) abis.add(value);
      }
    }

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
      permissions: List<ApkPermission>.unmodifiable(permissions),
    );
  }

  late final String formattedSize = FormatUtil.formatBytes(fileSize);

  late final String signatureDisplay = () {
    final hash = signatureHash;
    if (hash == null || hash.isEmpty) return 'Unknown';
    if (hash.length <= 16) return hash;
    return '${hash.substring(0, 8)}...${hash.substring(hash.length - 8)}';
  }();

  String get sdkDisplay => 'API $minSdkVersion → API $targetSdkVersion';

  String get abisDisplay =>
      supportedAbis.isEmpty ? 'Unknown' : supportedAbis.join(', ');

  /// Permissions that are usually worth a second look, shown first.
  late final List<ApkPermission> sensitivePermissions =
      permissions.where((permission) => permission.isSensitive).toList();

  late final int sensitivePermissionCount = sensitivePermissions.length;
}
