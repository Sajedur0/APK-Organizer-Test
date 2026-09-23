/// Shared formatting helpers.
///
/// Keeping this logic in one place means the APK list, the detail pages and
/// the summary dialogs can never disagree about how a size is displayed.
class FormatUtil {
  const FormatUtil._();

  static const List<String> _units = <String>['B', 'KB', 'MB', 'GB', 'TB'];

  /// Human readable byte size, e.g. `4.2 MB`.
  ///
  /// Uses binary steps (1024) with one decimal for KB/MB and two for GB+ —
  /// the exact format the app has always shown.
  static String formatBytes(int bytes) {
    if (bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  /// Short relative description of a timestamp (`Today`, `3d ago`, or a date).
  static String formatAge(int millisSinceEpoch, {DateTime? now}) {
    if (millisSinceEpoch <= 0) return 'Unknown';
    final DateTime date = DateTime.fromMillisecondsSinceEpoch(millisSinceEpoch);
    final DateTime reference = now ?? DateTime.now();
    final int days = reference.difference(date).inDays;
    if (days <= 0) return 'Today';
    if (days == 1) return 'Yesterday';
    if (days < 30) return '${days}d ago';
    final months = <String>[
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ];
    final label = '${months[date.month - 1]} ${date.day}';
    if (date.year == reference.year) return label;
    return '$label, ${date.year}';
  }

  /// Directory part of a path (without trailing separator).
  static String parentPath(String path) {
    final int index = path.lastIndexOf('/');
    if (index < 0) return path;
    if (index == 0) return '/';
    return path.substring(0, index);
  }

  /// File name part of a path.
  static String fileName(String path) {
    final int index = path.lastIndexOf('/');
    return index >= 0 ? path.substring(index + 1) : path;
  }

  /// Units list is exposed for callers that build custom labels.
  static List<String> get units => List<String>.unmodifiable(_units);
}
