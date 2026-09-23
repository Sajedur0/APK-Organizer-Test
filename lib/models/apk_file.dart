import '../utils/format_util.dart';

/// Immutable description of one APK file on disk.
///
/// ## Performance note
/// Every derived value (`displayName`, search text, sort key, suggested file
/// name, formatted size) is computed **once** and cached on the instance.
/// The list, search and sort code paths used to rebuild these strings on every
/// comparison/keystroke, which dominated the scroll & search cost with a few
/// hundred APKs. `copyWith` returns a new instance, so the caches can never go
/// stale.
class ApkFile {
  static final RegExp _unsafeFileChars = RegExp(r'[<>:"/\\|?*\x00-\x1F]');
  static final RegExp _whitespace = RegExp(r'\s+');
  static final RegExp _underscores = RegExp(r'_+');
  static final RegExp _edgeSeparators = RegExp(r'^[._\s]+|[._\s]+$');

  /// Suffix the native layer appends when a name already exists (`_1`, `_2`).
  static final RegExp _conflictSuffix = RegExp(r'_\d+$');

  final String fileName;
  final String path;
  final int size;
  final String appName;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String? iconPath;
  final int lastModified;

  ApkFile({
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

  /// Name shown in the list: app label, else file name, else package name.
  late final String displayName = () {
    final trimmedAppName = appName.trim();
    if (trimmedAppName.isNotEmpty) return trimmedAppName;
    final trimmedFileName = fileName.trim();
    if (trimmedFileName.isNotEmpty) return trimmedFileName;
    return packageName == 'unknown' ? 'Unknown APK' : packageName;
  }();

  /// Lower-cased [displayName] — used as the primary sort key.
  late final String sortName = displayName.toLowerCase();

  /// Lower-cased [versionName] (used by version comparisons).
  late final String versionLower = versionName.toLowerCase();

  /// Everything the search box matches against, already lower-cased.
  late final String searchLower = [
    displayName,
    fileName,
    packageName,
    versionName,
    versionCode.toString(),
  ].join(' ').toLowerCase();

  /// Backwards compatible alias of [searchLower].
  String get searchableText => searchLower;

  /// Folder that contains this file.
  late final String directory = FormatUtil.parentPath(path);

  /// True when the APK could not be parsed at all.
  bool get isUnparsed => packageName.trim().isEmpty || packageName == 'unknown';

  /// Identity used for duplicate detection: same package **and** same version
  /// are duplicates; different versions of an app never are.
  ///
  /// When the package name is unknown we also require an identical file size,
  /// because two unrelated APKs with the same generic label and version
  /// (`"Unknown"`) must never be grouped — deleting the wrong file is far worse
  /// than missing a duplicate.
  late final String duplicateIdentity = () {
    final normalizedPackage = packageName.trim().toLowerCase();
    if (normalizedPackage.isNotEmpty && normalizedPackage != 'unknown') {
      final versionPart = versionCode > 0
          ? versionCode.toString()
          : versionName.trim().toLowerCase();
      return 'pkg:$normalizedPackage|version:$versionPart';
    }
    return 'fallback:${displayName.toLowerCase()}|'
        '${versionName.trim().toLowerCase()}|$size';
  }();

  static int compareByDisplayName(ApkFile a, ApkFile b) {
    final nameCompare = a.sortName.compareTo(b.sortName);
    if (nameCompare != 0) return nameCompare;
    final versionCompare = b.versionCode.compareTo(a.versionCode);
    if (versionCompare != 0) return versionCompare;
    final fileCompare = a.fileName.toLowerCase().compareTo(
      b.fileName.toLowerCase(),
    );
    if (fileCompare != 0) return fileCompare;
    return a.path.compareTo(b.path);
  }

  /// Sorts by file size (ties broken by name so the order is stable).
  static int compareBySize(ApkFile a, ApkFile b) {
    final sizeCompare = a.size.compareTo(b.size);
    if (sizeCompare != 0) return sizeCompare;
    return compareByDisplayName(a, b);
  }

  /// Sorts by modified date.
  static int compareByDate(ApkFile a, ApkFile b) {
    final dateCompare = a.lastModified.compareTo(b.lastModified);
    if (dateCompare != 0) return dateCompare;
    return compareByDisplayName(a, b);
  }

  /// Sorts by version code, then by version name, then by name.
  static int compareByVersion(ApkFile a, ApkFile b) {
    final codeCompare = a.versionCode.compareTo(b.versionCode);
    if (codeCompare != 0) return codeCompare;
    final versionCompare = a.versionLower.compareTo(b.versionLower);
    if (versionCompare != 0) return versionCompare;
    return compareByDisplayName(a, b);
  }

  late final String formattedSize = FormatUtil.formatBytes(size);

  /// Target name for "auto rename": `AppName_VersionName.apk`.
  late final String suggestedRename = () {
    final safeAppName = _sanitizeFilePart(displayName);
    final versionSource =
        versionName.trim().isNotEmpty && versionName != 'Unknown'
        ? versionName
        : versionCode > 0
        ? versionCode.toString()
        : 'unknown';
    final safeVersion = _sanitizeFilePart(versionSource);
    return '${safeAppName}_$safeVersion.apk';
  }();

  /// Stem (file name without extension) of [suggestedRename].
  late final String _suggestedStem = suggestedRename.substring(
    0,
    suggestedRename.length - '.apk'.length,
  );

  /// True when the file already matches the auto-rename target.
  ///
  /// A numeric conflict suffix added by the native layer (`Name_v1_1.apk`) also
  /// counts as "already named", which keeps "Smart Organize" idempotent: a
  /// second run does no work instead of renaming files over and over.
  late final bool needsRename = () {
    final currentStem = fileName.toLowerCase().endsWith('.apk')
        ? fileName.substring(0, fileName.length - 4)
        : fileName;
    final normalizedCurrent = _conflictSuffix.hasMatch(currentStem)
        ? currentStem.substring(0, currentStem.lastIndexOf('_'))
        : currentStem;
    return normalizedCurrent.toLowerCase() != _suggestedStem.toLowerCase();
  }();

  static String _sanitizeFilePart(String value) {
    final sanitized = value
        .trim()
        .replaceAll(_unsafeFileChars, '_')
        .replaceAll(_whitespace, '_')
        .replaceAll(_underscores, '_')
        .replaceAll(_edgeSeparators, '');
    if (sanitized.isEmpty) return 'unknown';
    return sanitized.length > 80 ? sanitized.substring(0, 80) : sanitized;
  }

  @override
  String toString() =>
      'ApkFile(name: $appName, path: $path, size: $formattedSize)';
}
