import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart' hide AboutDialog;
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../app_theme.dart';
import '../models/apk_file.dart';
import '../services/apk_manager_service.dart';
import '../services/duplicate_handler.dart';
import '../services/file_operations.dart';
import '../services/app_update_service.dart';
import '../services/preferences_service.dart';
import '../services/renamer_service.dart';
import '../services/scanner_service.dart';
import '../utils/format_util.dart';
import '../utils/parallel_work_queue.dart';
import '../utils/permission_utils.dart';
import '../widgets/hexagon_dots_loading.dart';
import '../widgets/apk_list_tile.dart';
import '../widgets/bottom_sheet_action.dart';
import '../widgets/detail_row.dart';
import '../widgets/directory_browser_sheet.dart';
import '../widgets/permission_denied_view.dart';
import '../widgets/selection_bottom_bar.dart';

import '../dialogs/about_dialog.dart';
import '../dialogs/privacy_policy_dialog.dart';
import '../dialogs/summary_dialog.dart';
import 'apk_detail_page.dart';
import 'installed_apps_page.dart';

class HomePage extends StatefulWidget {
  final String appVersion;

  const HomePage({
    super.key,
    required this.appVersion,
  });

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  /// Master list in scan order (never re-sorted in place) …
  final List<ApkFile> _allApkFiles = [];

  /// … plus O(1) lookup/update maps so selection, rename, move and delete never
  /// scan the whole list. `_positionByPath` lets an entry be replaced in place
  /// without rebuilding or re-sorting the master list.
  final Map<String, ApkFile> _apkIndex = {};
  final Map<String, int> _positionByPath = {};

  /// Sorted + filtered view that the ListView renders.
  List<ApkFile> _filteredApkFiles = [];

  final Set<String> _selectedPaths = {};
  bool _isLoading = false;
  bool _hasPermission = false;
  String? _errorMessage;
  String _searchQuery = '';
  bool _isSearching = false;
  late TextEditingController _searchController;
  late FocusNode _searchFocusNode;
  Timer? _searchDebounce;

  ApkSortMode _sortMode = ApkSortMode.name;
  bool _sortAscending = true;
  String? _filterDirectory;

  final _scannerService = ScannerService();
  final _renamerService = RenamerService();
  final _duplicateHandler = DuplicateHandler();
  final _fileOperations = FileOperations();

  // --- Live scan state -----------------------------------------------------
  // Progress batches are buffered and applied at most every
  // [_scanFlushInterval], so a scan of thousands of files triggers a handful of
  // list rebuilds per second instead of one per file.
  static const Duration _scanFlushInterval = Duration(milliseconds: 130);
  static const Duration _minResumeRescanGap = Duration(seconds: 20);

  final List<ApkFile> _scanBuffer = [];
  Timer? _scanFlushTimer;
  List<String>? _cachedDirectoryList;
  int _cachedDirectoryListCount = -1;
  int _scanFoundCount = 0;
  String _scanDirectory = '';
  bool _isDiscovering = true;
  bool _stopRequested = false;
  DateTime? _lastScanFinishedAt;
  DialogRoute<void>? _organizeProgressRoute;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _searchFocusNode = FocusNode();
    _loadSortPreferences();
    WidgetsBinding.instance.addObserver(this);
    _checkPermissionAndScan();
    // Google Play In-App Update — check on every app start after first frame
    // so it doesn't block startup or run before Navigator/Scaffold is ready.
    // The service itself (AppUpdateService) uses InAppUpdate.isAndroid as a
    // guard and handles its own installStateListener subscription.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      AppUpdateService.checkAndPromptUpdate();
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocusNode.dispose();
    _searchDebounce?.cancel();
    _scanFlushTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _onAppResumed();
  }

  void _loadSortPreferences() {
    final prefs = PreferencesService.instance;
    final savedIndex = prefs.sortModeIndex;
    if (savedIndex != null &&
        savedIndex >= 0 &&
        savedIndex < ApkSortMode.values.length) {
      _sortMode = ApkSortMode.values[savedIndex];
    }
    _sortAscending = prefs.sortAscending;
  }

  Future<void> _onAppResumed() async {
    try {
      final hasPerm = await PermissionUtils.hasStoragePermission();
      if (!mounted) return;

      if (!hasPerm) {
        if (_hasPermission) setState(() => _hasPermission = false);
        return;
      }

      if (!_hasPermission) {
        setState(() => _hasPermission = true);
        await _scanApkFiles();
        return;
      }

      // Rescanning the whole storage on every resume is wasteful (and jarring).
      // Only refresh when the list is empty and nothing ran recently.
      final lastScan = _lastScanFinishedAt;
      final recentlyScanned =
          lastScan != null && DateTime.now().difference(lastScan) < _minResumeRescanGap;
      if (_allApkFiles.isEmpty && !_isLoading && !recentlyScanned) {
        await _scanApkFiles();
      }
    } catch (e) {
      debugPrint('_onAppResumed error: $e');
    }
  }

  Future<void> _requestPermission() async {
    setState(() => _isLoading = true);
    try {
      await ApkManagerService.requestStoragePermission();
      // The native side resolves the request when the settings screen returns,
      // so re-check straight away instead of waiting on a fixed timer.
      await _checkPermissionAndScan();
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _checkPermissionAndScan() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final hasPerm = await PermissionUtils.hasStoragePermission();
      if (!mounted) return;
      setState(() => _hasPermission = hasPerm);
      if (hasPerm) await _scanApkFiles();
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() => _errorMessage = e.message);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _scanApkFiles() async {
    // Never start a second scan on top of a running one.
    if (_scannerService.isScanning) return;

    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = 'Storage permission is required to scan APK files.';
      });
      return;
    }

    _scanFlushTimer?.cancel();
    _scanBuffer.clear();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _stopRequested = false;
      _scanFoundCount = 0;
      _scanDirectory = '';
      _isDiscovering = true;
      _selectedPaths.clear();
      _allApkFiles.clear();
      _apkIndex.clear();
      _positionByPath.clear();
      _filteredApkFiles = [];
    });

    try {
      final result = await _scannerService.scanAllStorage(
        isCancelled: () => _stopRequested,
        onProgress: _onScanProgress,
      );

      // Apply whatever the final batch left in the buffer before finishing.
      _flushScanBuffer();
      if (!mounted) return;

      setState(() {
        _apkIndex.clear();
        _positionByPath.clear();
        _allApkFiles
          ..clear()
          ..addAll(result.allFiles);
        for (var i = 0; i < _allApkFiles.length; i++) {
          final apk = _allApkFiles[i];
          _apkIndex[apk.path] = apk;
          _positionByPath[apk.path] = i;
        }
        _scanFoundCount = result.allFiles.length;
        _applyFilter();
      });

      if (result.cancelled) {
        _showSnackBar(
          'Scan stopped — showing ${result.allFiles.length} file(s) found so far',
        );
      }
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() => _errorMessage = e.message);
      _showSnackBar(e.message, isError: true);
    } finally {
      _scanFlushTimer?.cancel();
      _scanFlushTimer = null;
      _lastScanFinishedAt = DateTime.now();
      if (mounted) {
        setState(() {
          _isLoading = false;
          _scanDirectory = '';
        });
      }
    }
  }

  /// Receives throttled batches from [ScannerService] and buffers them until
  /// the next UI flush (never per file, which used to re-sort the whole list on
  /// every event).
  void _onScanProgress(ScanProgress progress) {
    if (!mounted) return;
    _scanFoundCount = progress.filesFound;
    if (progress.currentDirectory.isNotEmpty) {
      _scanDirectory = progress.currentDirectory;
    }
    _isDiscovering = progress.isDiscovering;
    if (progress.apks.isNotEmpty) _scanBuffer.addAll(progress.apks);

    if (_scanFlushTimer?.isActive ?? false) return;
    _scanFlushTimer = Timer(_scanFlushInterval, () {
      _scanFlushTimer = null;
      if (mounted) _flushScanBuffer();
    });
  }

  /// Merges buffered scan results into the master list and rebuilds the
  /// visible (sorted/filtered) slice once.
  void _flushScanBuffer() {
    if (_scanBuffer.isEmpty) {
      if (mounted && _isLoading) {
        setState(() {}); // refresh the found-count / directory label
      }
      return;
    }
    for (final apk in _scanBuffer) {
      _upsert(apk);
    }
    _scanBuffer.clear();
    if (!mounted) return;
    setState(_applyFilter);
  }

  /// Adds [apk] to the master list, or replaces the existing entry for the same
  /// path in place.
  void _upsert(ApkFile apk) {
    if (apk.path.isEmpty) return;
    final position = _positionByPath[apk.path];
    if (position == null) {
      _positionByPath[apk.path] = _allApkFiles.length;
      _allApkFiles.add(apk);
    } else {
      _allApkFiles[position] = apk;
    }
    _apkIndex[apk.path] = apk;
  }

  /// Replaces the entry at [oldPath] with [apk] (used after rename/move).
  void _replaceEntry(String oldPath, ApkFile apk) {
    final position = _positionByPath.remove(oldPath);
    _apkIndex.remove(oldPath);
    if (position != null) {
      _allApkFiles[position] = apk;
      _positionByPath[apk.path] = position;
    } else {
      _positionByPath[apk.path] = _allApkFiles.length;
      _allApkFiles.add(apk);
    }
    _apkIndex[apk.path] = apk;
    if (_selectedPaths.remove(oldPath)) _selectedPaths.add(apk.path);
  }

  /// Removes [path] from every in-memory structure.
  void _removeEntry(String path) {
    final position = _positionByPath.remove(path);
    _apkIndex.remove(path);
    if (position != null && position < _allApkFiles.length) {
      if (position == _allApkFiles.length - 1) {
        _allApkFiles.removeLast();
      } else {
        _allApkFiles.removeAt(position);
        // Re-index the tail that shifted down.
        for (var i = position; i < _allApkFiles.length; i++) {
          _positionByPath[_allApkFiles[i].path] = i;
        }
      }
    }
    _selectedPaths.remove(path);
  }

  /// Rebuilds [_filteredApkFiles] from the master list.
  ///
  /// Runs on every search keystroke (debounced), sort change and scan flush, so
  /// it avoids per-call allocations: cached search strings, no throwaway path
  /// sets and a single comparator selected up front.
  void _applyFilter() {
    if (_selectedPaths.isNotEmpty) {
      _selectedPaths.removeWhere((path) => !_apkIndex.containsKey(path));
    }

    final query = _searchQuery.trim().toLowerCase();
    final directory = _filterDirectory;
    final hasQuery = query.isNotEmpty;
    final hasDirectory = directory != null && directory.isNotEmpty;

    List<ApkFile> result;
    if (!hasQuery && !hasDirectory) {
      result = List<ApkFile>.of(_allApkFiles);
    } else {
      result = <ApkFile>[];
      for (final apk in _allApkFiles) {
        if (hasQuery && !apk.searchLower.contains(query)) continue;
        if (hasDirectory && !apk.path.startsWith(directory)) continue;
        result.add(apk);
      }
    }

    _sortFiles(result);
    _filteredApkFiles = result;
  }

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(const Duration(milliseconds: 220), () {
      if (!mounted) return;
      setState(() {
        _searchQuery = value;
        _applyFilter();
      });
    });
  }

  /// Sorts [files] using the current mode/direction.
  static int Function(ApkFile, ApkFile) _comparatorFor(ApkSortMode mode) {
    switch (mode) {
      case ApkSortMode.name:
        return ApkFile.compareByDisplayName;
      case ApkSortMode.size:
        return ApkFile.compareBySize;
      case ApkSortMode.date:
        return ApkFile.compareByDate;
      case ApkSortMode.version:
        return ApkFile.compareByVersion;
    }
  }

  void _sortFiles(List<ApkFile> files) {
    final comparator = _comparatorFor(_sortMode);
    if (_sortAscending) {
      files.sort(comparator);
    } else {
      files.sort((a, b) => comparator(b, a));
    }
  }



  Future<void> _installApk(ApkFile apk, {bool silent = false}) async {
    final canInstall = await PermissionUtils.canInstallPackages();
    if (!canInstall) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Install Permission Required',
        message:
            'APK Organizer needs permission to install APK files on your device.',
        requestPermission: PermissionUtils.requestInstallPermission,
      );
      if (!granted) {
        _showSnackBar('Install permission denied.', isError: true);
        return;
      }
    }
    try {
      final result = await ApkManagerService.installApk(apk.path);
      final status = result['status'] as String? ?? '';
      if (status == 'redirected_to_settings') {
        _showSnackBar('Please enable "Install unknown apps" and try again.');
      } else if (!silent) {
        _showSnackBar('Installation started for ${apk.displayName}');
      }
    } on ApkManagerException catch (e) {
      if (!silent) _showSnackBar(e.message, isError: true);
      rethrow;
    }
  }

  Future<void> _installSelectedApks() async {
    if (_selectedPaths.isEmpty) return;
    final apps = _selectedPaths
        .map(_apkByPath)
        .whereType<ApkFile>()
        .toList(growable: false);
    if (apps.isEmpty) return;

    int started = 0;
    int failed = 0;
    final failedNames = <String>[];
    for (var i = 0; i < apps.length; i++) {
      if (!mounted) return;
      // Android shows one installer at a time, so the intents are spaced out
      // instead of being fired back to back (where all but the last are lost).
      if (i > 0) {
        await Future<void>.delayed(const Duration(milliseconds: 900));
        if (!mounted) return;
      }
      try {
        await _installApk(apps[i], silent: true);
        started++;
      } catch (_) {
        failed++;
        if (failedNames.length < 3) failedNames.add(apps[i].displayName);
      }
    }
    if (!mounted) return;
    final failedLabel = failedNames.isEmpty
        ? ''
        : ', $failed failed (${failedNames.join(', ')}'
            '${failed > failedNames.length ? '…' : ''})';
    _showSnackBar(
      '$started installation(s) started$failedLabel',
      isError: failed > 0 && started == 0,
    );
  }

  Future<void> _deleteApk(ApkFile apk) async {
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Storage Permission Required',
        message:
            'APK Organizer needs access to your device\'s storage to delete APK files.',
        requestPermission:
            PermissionUtils.requestStoragePermissionWithRationale,
      );
      if (!granted) {
        _showSnackBar('Storage permission denied.', isError: true);
        return;
      }
    }
    final confirmed = await _showConfirmDialog(
      'Delete APK',
      'Are you sure you want to delete "${apk.displayName}"?',
    );
    if (!confirmed) return;
    try {
      await ApkManagerService.deleteApk(apk.path);
      if (!mounted) return;
      setState(() {
        _removeEntry(apk.path);
        _applyFilter();
      });
      _showSnackBar('${apk.displayName} deleted');
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  Future<void> _deleteSelectedApks() async {
    if (_selectedPaths.isEmpty) return;
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Storage Permission Required',
        message:
            'APK Organizer needs access to your device\'s storage to delete APK files.',
        requestPermission:
            PermissionUtils.requestStoragePermissionWithRationale,
      );
      if (!granted) {
        _showSnackBar('Storage permission denied.', isError: true);
        return;
      }
    }
    final confirmed = await _showConfirmDialog(
      'Delete Selected',
      'Are you sure you want to delete ${_selectedPaths.length} APK file(s)?',
    );
    if (!confirmed) return;
    final paths = List<String>.from(_selectedPaths);
    final failed = <String>[];
    for (final path in paths) {
      try {
        await ApkManagerService.deleteApk(path);
      } on ApkManagerException {
        failed.add(path);
      }
    }
    if (!mounted) return;
    setState(() {
      for (final path in paths) {
        if (failed.contains(path)) continue;
        _removeEntry(path);
      }
      _selectedPaths.clear();
      _applyFilter();
    });
    if (failed.isEmpty) {
      _showSnackBar('${paths.length} APK file(s) deleted');
    } else if (failed.length == paths.length) {
      _showSnackBar('Could not delete the selected file(s)', isError: true);
    } else {
      _showSnackBar(
        '${paths.length - failed.length} deleted, ${failed.length} failed to delete',
        isError: true,
      );
    }
  }

  Future<void> _autoRenameApk(ApkFile apk) async {
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Storage Permission Required',
        message:
            'APK Organizer needs access to your device\'s storage to rename APK files.',
        requestPermission:
            PermissionUtils.requestStoragePermissionWithRationale,
      );
      if (!granted) {
        _showSnackBar('Storage permission denied.', isError: true);
        return;
      }
    }
    if (!apk.needsRename) {
      _showSnackBar('${apk.fileName} already follows AppName_Version.apk');
      return;
    }
    final newName = apk.suggestedRename;
    try {
      final result = await ApkManagerService.renameApk(apk.path, newName);
      final oldName = apk.fileName;
      final newPath = result['newPath'] as String?;
      final newNameResult = result['newName'] as String?;
      if (!mounted) return;
      _applyRenameResult(apk.path, result);
      _showSnackBar(
        'Renamed to ${newNameResult ?? newName}',
        actionLabel: 'Undo',
        onAction: () async {
          if (newPath != null) {
            try {
              await ApkManagerService.renameApk(newPath, oldName);
              _applyRenameResultFrom(newPath, apk.path, oldName);
              _showSnackBar('Renamed back');
            } on ApkManagerException catch (e) {
              _showSnackBar(e.message, isError: true);
            }
          }
        },
        duration: const Duration(seconds: 6),
      );
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  Future<void> _manualRenameApk(ApkFile apk) async {
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Storage Permission Required',
        message:
            'APK Organizer needs access to your device\'s storage to rename APK files.',
        requestPermission:
            PermissionUtils.requestStoragePermissionWithRationale,
      );
      if (!granted) {
        _showSnackBar('Storage permission denied.', isError: true);
        return;
      }
    }
    final controller = TextEditingController(text: apk.suggestedRename);
    if (!mounted) {
      controller.dispose();
      return;
    }
    final newName = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: AppRadius.dialogBorder),
        title: const Text('Rename APK'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(
            labelText: 'New file name',
            hintText: 'e.g., MyApp_1.0.apk',
            border: OutlineInputBorder(
              borderRadius: AppRadius.controlBorder,
            ),
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, controller.text),
            child: const Text('Rename'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (newName == null || newName.trim().isEmpty) return;
    if (newName.trim().toLowerCase() == apk.fileName.toLowerCase()) {
      _showSnackBar('File name is unchanged');
      return;
    }
    try {
      final result = await ApkManagerService.renameApk(apk.path, newName.trim());
      if (!mounted) return;
      _applyRenameResult(apk.path, result);
      final applied = result['newName'] as String? ?? newName.trim();
      _showSnackBar('Renamed to $applied');
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  /// Applies a native rename result to the in-memory list without re-scanning.
  ///
  /// Returns the new path so callers can build an accurate "Undo" action even
  /// when the native layer had to add a conflict suffix.
  String? _applyRenameResult(String oldPath, Map<String, dynamic> result) {
    final newPath = result['newPath'] as String?;
    final newName = result['newName'] as String?;
    if (newPath == null || newName == null || !mounted) return null;
    final existing = _apkIndex[oldPath];
    if (existing == null) return newPath;
    setState(() {
      _replaceEntry(
        oldPath,
        existing.copyWith(path: newPath, fileName: newName),
      );
      _applyFilter();
    });
    return newPath;
  }

  /// Applies a reverse rename (newPath -> originalPath/originalName) to the
  /// in-memory list, used by the rename "Undo" action.
  void _applyRenameResultFrom(
    String newPath,
    String originalPath,
    String originalName,
  ) {
    if (!mounted) return;
    final existing = _apkIndex[newPath];
    if (existing == null) return;
    setState(() {
      _replaceEntry(
        newPath,
        existing.copyWith(path: originalPath, fileName: originalName),
      );
      _applyFilter();
    });
  }

  Future<void> _renameSelectedApksAuto() async {
    if (_selectedPaths.isEmpty) return;
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Storage Permission Required',
        message:
            'APK Organizer needs access to your device\'s storage to rename APK files.',
        requestPermission:
            PermissionUtils.requestStoragePermissionWithRationale,
      );
      if (!granted) {
        _showSnackBar('Storage permission denied.', isError: true);
        return;
      }
    }
    final confirmed = await _showConfirmDialog(
      'Auto Rename',
      'Rename ${_selectedPaths.length} APK file(s) to AppName_Version.apk?\n\n'
          'Files that already follow this format are skipped.',
    );
    if (!confirmed) return;
    final selected = _selectedPaths
        .map(_apkByPath)
        .whereType<ApkFile>()
        .toList();
    final summary = await _renamerService.autoRenameAll(selected);
    if (!mounted) return;
    // Update the in-memory list from the returned results instead of a full
    // storage re-scan.
    setState(() {
      for (final r in summary.results) {
        if (!r.success || r.newPath == null || r.newName == null) continue;
        final existing = _apkIndex[r.originalPath];
        if (existing == null) continue;
        _replaceEntry(
          r.originalPath,
          existing.copyWith(path: r.newPath, fileName: r.newName),
        );
      }
      _selectedPaths.clear();
      _applyFilter();
    });
    final unchanged = summary.total - summary.succeeded - summary.failed;
    _showSnackBar(
      '${summary.succeeded} file(s) renamed'
      '${unchanged > 0 ? ', $unchanged already named' : ''}'
      '${summary.failed > 0 ? ', ${summary.failed} failed' : ''}',
      actionLabel: summary.results.any((r) => r.success && r.newPath != null)
          ? 'Undo'
          : null,
      onAction: summary.results.any((r) => r.success && r.newPath != null)
          ? () => _undoRename(summary.results
              .where((r) => r.success && r.newPath != null && r.originalPath.isNotEmpty)
              .map((r) => (newPath: r.newPath!, originalPath: r.originalPath, originalName: _originalFileName(r.originalPath)))
              .toList())
          : null,
      duration: const Duration(seconds: 6),
    );
  }

  String _originalFileName(String path) => FormatUtil.fileName(path);

  Future<void> _undoRename(
    List<({String newPath, String originalPath, String originalName})> items,
  ) async {
    if (items.isEmpty) return;
    final results = List<String?>.filled(items.length, null);
    await runParallel(
      total: items.length,
      concurrency: 3,
      task: (index) async {
        final item = items[index];
        try {
          final result = await ApkManagerService.renameApk(
            item.newPath,
            item.originalName,
          );
          results[index] = result['newPath'] as String? ?? item.originalPath;
        } on ApkManagerException {
          results[index] = null;
        }
      },
    );

    if (!mounted) return;
    int failed = 0;
    setState(() {
      for (var i = 0; i < items.length; i++) {
        final restoredPath = results[i];
        if (restoredPath == null) {
          failed++;
          continue;
        }
        final existing = _apkIndex[items[i].newPath];
        if (existing == null) continue;
        _replaceEntry(
          items[i].newPath,
          existing.copyWith(
            path: restoredPath,
            fileName: _originalFileName(restoredPath),
          ),
        );
      }
      _applyFilter();
    });
    _showSnackBar(
      failed == 0
          ? 'Rename undone'
          : '$failed file(s) failed to restore',
      isError: failed > 0,
    );
  }

  Future<void> _moveApk(ApkFile apk) async => _showMoveDialog([apk.path]);

  void _showApkDetail(ApkFile apk) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ApkDetailPage(
          filePath: apk.path,
          appName: apk.displayName,
        ),
      ),
    );
  }

  Future<void> _moveSelectedApks() async {
    if (_selectedPaths.isEmpty) return;
    await _showMoveDialog(_selectedPaths.toList());
  }

  Future<void> _showMoveDialog(List<String> paths) async {
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      final granted = await PermissionUtils.showPermissionRationaleAndRequest(
        context,
        title: 'Storage Permission Required',
        message:
            'APK Organizer needs access to your device\'s storage to move APK files.',
        requestPermission:
            PermissionUtils.requestStoragePermissionWithRationale,
      );
      if (!granted) {
        _showSnackBar('Storage permission denied.', isError: true);
        return;
      }
    }
    try {
      final roots = await ApkManagerService.getDirectories();
      if (!mounted) return;
      if (!mounted) return;
      final selectedDir = await showModalBottomSheet<String>(
        context: context,
        isScrollControlled: true,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(AppRadius.sheet),
          ),
        ),
        builder: (ctx) => DirectoryBrowserSheet(initialDirectories: roots),
      );
      if (selectedDir == null) return;
      final summary = await _fileOperations.batchMove(paths, selectedDir);
      if (!mounted) return;
      // Update the in-memory list from the returned results instead of a full
      // storage re-scan.
      setState(() {
        for (final r in summary.results) {
          final newPath = r.destPath;
          if (!r.success || newPath == null || r.skipped) continue;
          final existing = _apkIndex[r.sourcePath];
          if (existing == null) continue;
          _replaceEntry(
            r.sourcePath,
            existing.copyWith(
              path: newPath,
              fileName: _originalFileName(newPath),
            ),
          );
        }
        _selectedPaths.clear();
        _applyFilter();
      });

      final undoMap = <String, String>{};
      for (final r in summary.results) {
        if (r.success && !r.skipped && r.destPath != null) {
          undoMap[r.destPath!] = r.sourcePath;
        }
      }
      _showSnackBar(
        summary.describe(),
        actionLabel: undoMap.isNotEmpty ? 'Undo' : null,
        onAction: undoMap.isNotEmpty ? () => _undoMove(undoMap) : null,
        duration: const Duration(seconds: 6),
      );
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  Future<void> _undoMove(Map<String, String> undoMap) async {
    if (undoMap.isEmpty) return;
    final entries = undoMap.entries.toList();
    final restoredPaths = List<String?>.filled(entries.length, null);

    await runParallel(
      total: entries.length,
      concurrency: 3,
      task: (index) async {
        final entry = entries[index];
        try {
          final result = await ApkManagerService.moveApk(
            entry.key,
            FormatUtil.parentPath(entry.value),
          );
          restoredPaths[index] = result['destPath'] as String?;
        } on ApkManagerException {
          restoredPaths[index] = null;
        }
      },
    );

    if (!mounted) return;
    var failed = 0;
    setState(() {
      for (var i = 0; i < entries.length; i++) {
        final restoredPath = restoredPaths[i];
        if (restoredPath == null) {
          failed++;
          continue;
        }
        final existing = _apkIndex[entries[i].key];
        if (existing == null) continue;
        _replaceEntry(
          entries[i].key,
          existing.copyWith(
            path: restoredPath,
            fileName: _originalFileName(restoredPath),
          ),
        );
      }
      _applyFilter();
    });
    _showSnackBar(
      failed == 0
          ? 'Move undone'
          : '$failed file(s) failed to restore',
      isError: failed > 0,
    );
  }

  void _toggleSelection(ApkFile apk) {
    setState(() {
      if (_selectedPaths.contains(apk.path)) {
        _selectedPaths.remove(apk.path);
      } else {
        _selectedPaths.add(apk.path);
      }
    });
  }

  void _selectAll() {
    setState(() {
      final allSelected = _filteredApkFiles.isNotEmpty &&
          _selectedPaths.length == _filteredApkFiles.length;
      if (allSelected) {
        _selectedPaths.clear();
      } else {
        _selectedPaths
          ..clear()
          ..addAll(_filteredApkFiles.map((a) => a.path));
      }
    });
  }

  void _clearSelection() => setState(() => _selectedPaths.clear());

  ApkFile? _apkByPath(String path) => _apkIndex[path];

  Future<void> _smartOrganize() async {
    if (!_hasPermission) {
      _showSnackBar(
        'Storage permission required to organize APK files.',
        isError: true,
      );
      return;
    }
    if (_allApkFiles.isEmpty) {
      _showSnackBar('No APK files found to organize.');
      return;
    }
    final confirmed = await _showConfirmDialog(
      'Auto Organize',
      'This will:\n1. Auto-rename all APK files to AppName_VersionName.apk\n2. Remove duplicate files (keeping the newest)\n\nContinue with ${_allApkFiles.length} file(s)?',
    );
    if (!confirmed) return;

    final token = CancellationToken();
    // (done, total, phaseLabel) — phaseLabel is "Renaming" or "Removing duplicates".
    final progress = ValueNotifier<(int, int, String)>((0, 0, 'Renaming'));
    final initialCount = _allApkFiles.length;

    unawaited(_showOrganizeProgressDialog(token, progress));

    try {
      // 1. Parallel rename (bounded worker pool, cancellable, with progress).
      //    Skips files already correctly named, so no native call is wasted.
      final renameSummary = await _renamerService.autoRenameAll(
        _allApkFiles,
        onProgress: (done, total) => progress.value = (done, total, 'Renaming'),
        isCancelled: () => token.isCancelled,
      );

      // Update the in-memory list instead of re-scanning all storage.
      if (mounted) {
        setState(() {
          for (final r in renameSummary.results) {
            if (!r.success) continue;
            final newPath = r.newPath;
            final newName = r.newName;
            if (newPath == null || newName == null) continue;
            final existing = _apkIndex[r.originalPath];
            if (existing == null) continue;
            _replaceEntry(
              r.originalPath,
              existing.copyWith(path: newPath, fileName: newName),
            );
          }
          _applyFilter();
        });
      }

      // 2. Remove duplicates (now concurrent + cancellable, with progress),
      //    then update the in-memory list from the returned deleted paths —
      //    no full re-scan. Deletion is intentionally a separate pass *after*
      //    renaming so the duplicate "keep" decision and the on-disk files can
      //    never race (a renamed file and its duplicate share the same package
      //    identity, so deleting concurrently could remove the wrong copy).
      progress.value = (0, 0, 'Removing duplicates');
      final dupSummary = await _duplicateHandler.removeDuplicates(
        _allApkFiles,
        onProgress: (done, total) =>
            progress.value = (done, total, 'Removing duplicates'),
        isCancelled: () => token.isCancelled,
      );

      final deletedPaths = dupSummary.deletedPaths;
      if (mounted) {
        setState(() {
          for (final path in deletedPaths) {
            _removeEntry(path);
          }
          _selectedPaths.clear();
          _applyFilter();
        });
      }

      if (mounted) _closeOrganizeProgressDialog();
      _showSummaryDialog(
        title: 'Auto Organize Complete',
        stats: {
          'Total Files Scanned': initialCount,
          'Files Renamed': renameSummary.succeeded,
          'Rename Failures': renameSummary.failed,
          'Duplicate Groups': dupSummary.duplicateGroups,
          'Duplicates Removed': dupSummary.filesDeleted,
        },
        details: [
          if (renameSummary.succeeded > 0)
            'Renamed ${renameSummary.succeeded} file(s)',
          if (renameSummary.skipped > 0)
            '${renameSummary.skipped} file(s) already had the correct name',
          if (dupSummary.filesDeleted > 0)
            'Removed ${dupSummary.filesDeleted} duplicate(s)',
          if (dupSummary.errors.isNotEmpty)
            '${dupSummary.errors.length} duplicate(s) could not be removed',
        ],
        errors: [
          ...renameSummary.results
              .where((r) => !r.success)
              .map((r) => 'Rename ${r.originalPath.split('/').last}: ${r.error}'),
          ...dupSummary.errors,
        ],
      );
    } catch (e) {
      if (mounted) _closeOrganizeProgressDialog();
      _showSnackBar('Smart organize failed: $e', isError: true);
    }
  }

  Future<void> _showOrganizeProgressDialog(
    CancellationToken token,
    ValueNotifier<(int, int, String)> progress,
  ) async {
    if (!mounted) return;
    // The dialog is pushed manually so it can be closed by identity later:
    // popping "whatever is on top" could dismiss an unrelated route.
    final route = DialogRoute<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => ValueListenableBuilder<(int, int, String)>(
        valueListenable: progress,
        builder: (_, value, _) {
          final done = value.$1;
          final total = value.$2;
          final ratio = total == 0 ? null : done / total;
          return AlertDialog(
            shape: RoundedRectangleBorder(
              borderRadius: AppRadius.dialogBorder,
            ),
            title: Text('Auto Organize — ${value.$3}'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(value: ratio),
                ),
                const SizedBox(height: 12),
                Text(
                  total == 0
                      ? 'Working…'
                      : '$done / $total file(s)',
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => token.cancel(),
                child: const Text('Cancel'),
              ),
            ],
          );
        },
      ),
    );
    _organizeProgressRoute = route;
    try {
      await Navigator.of(context).push(route);
    } finally {
      if (identical(_organizeProgressRoute, route)) {
        _organizeProgressRoute = null;
      }
    }
  }

  /// Closes exactly the organize-progress dialog (and nothing else).
  void _closeOrganizeProgressDialog() {
    final route = _organizeProgressRoute;
    _organizeProgressRoute = null;
    if (route == null || !mounted || !route.isActive) return;
    Navigator.of(context).removeRoute(route);
  }


  void _showSummaryDialog({
    required String title,
    required Map<String, int> stats,
    List<String> details = const [],
    List<String> errors = const [],
  }) {
    if (!mounted) return;
    showDialog(
      context: context,
      builder: (ctx) => SummaryDialog(
        title: title,
        stats: stats,
        details: details,
        errors: errors,
      ),
    );
  }

  void _showSnackBar(
    String message, {
    bool isError = false,
    String? actionLabel,
    VoidCallback? onAction,
    Duration? duration,
  }) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).clearSnackBars();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? Theme.of(context).colorScheme.error : null,
        behavior: SnackBarBehavior.floating,
        duration: duration ?? const Duration(seconds: 4),
        action: actionLabel != null && onAction != null
            ? SnackBarAction(label: actionLabel, onPressed: onAction)
            : null,
      ),
    );
  }

  Future<bool> _showConfirmDialog(String title, String message) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: AppRadius.dialogBorder),
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Widget _buildSortButton() {
    final colorScheme = Theme.of(context).colorScheme;
    final sortLabels = {
      ApkSortMode.name: 'Name',
      ApkSortMode.size: 'Size',
      ApkSortMode.date: 'Date',
      ApkSortMode.version: 'Version',
    };
    final sortIcons = {
      ApkSortMode.name: Icons.sort_by_alpha,
      ApkSortMode.size: Icons.storage_outlined,
      ApkSortMode.date: Icons.schedule,
      ApkSortMode.version: Icons.tag,
    };
    return PopupMenuButton<ApkSortMode>(
      icon: Icon(sortIcons[_sortMode], size: 20),
      tooltip: 'Sort: ${sortLabels[_sortMode]}',
      shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
      onSelected: (mode) {
        setState(() {
          if (_sortMode == mode) {
            _sortAscending = !_sortAscending;
          } else {
            _sortMode = mode;
            _sortAscending = true;
          }
          _applyFilter();
        });
        PreferencesService.instance.setSort(_sortMode.index, _sortAscending);
      },
      itemBuilder: (ctx) => ApkSortMode.values.map((mode) {
        final selected = _sortMode == mode;
        return PopupMenuItem(
          value: mode,
          child: Row(
            children: [
              Icon(
                sortIcons[mode],
                size: 20,
                color: selected ? colorScheme.primary : null,
              ),
              const SizedBox(width: 12),
              Text(sortLabels[mode]!,
                  style: selected ? TextStyle(color: colorScheme.primary, fontWeight: FontWeight.w600) : null),
              const Spacer(),
              if (selected)
                Icon(
                  _sortAscending ? Icons.arrow_upward : Icons.arrow_downward,
                  size: 16,
                  color: colorScheme.primary,
                ),
            ],
          ),
        );
      }).toList(),
    );
  }

  Widget _buildFilterButton() {
    final colorScheme = Theme.of(context).colorScheme;
    final hasFilter = _filterDirectory != null;
    return IconButton(
      icon: Icon(
        Icons.filter_list,
        size: 20,
        color: hasFilter ? colorScheme.primary : null,
      ),
      tooltip: hasFilter ? 'Filter active' : 'Filter by directory',
      onPressed: _showFilterSheet,
    );
  }

  /// Opens a searchable bottom sheet to filter APK files by their directory.
  Future<void> _showFilterSheet() async {
    if (_allApkFiles.isEmpty) return;
    // Reuse the cached list while the scan set is unchanged (the sheet is
    // rebuilt from scratch every time it opens).
    if (_cachedDirectoryList == null ||
        _cachedDirectoryListCount != _allApkFiles.length) {
      final dirs = <String>{
        for (final apk in _allApkFiles) apk.directory,
      }.toList()
        ..sort();
      _cachedDirectoryList = dirs;
      _cachedDirectoryListCount = _allApkFiles.length;
    }
    if (!mounted) return;
    final selected = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(AppRadius.sheet),
        ),
      ),
      builder: (ctx) => _DirectoryFilterSheet(
        directories: _cachedDirectoryList!,
        currentFilter: _filterDirectory,
      ),
    );
    if (selected == null) return;
    setState(() {
      _filterDirectory = selected == 'all' ? null : selected;
      _applyFilter();
    });
  }



  Widget _buildBody() {
    if (!_hasPermission && !_isLoading) {
      return PermissionDeniedView(onRequestPermission: _requestPermission);
    }

    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.error_outline,
                size: 64,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: 16),
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _checkPermissionAndScan,
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_filteredApkFiles.isEmpty) {
      if (_isLoading) {
        return Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const HexagonDotsLoading(minRadius: 10),
              const SizedBox(height: 20),
              Text(
                _isDiscovering
                    ? 'Searching storage…'
                    : 'Reading APK files…',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
              if (_scanFoundCount > 0) ...[
                const SizedBox(height: 6),
                Text(
                  '$_scanFoundCount found so far',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.outline,
                  ),
                ),
              ],
              if (_scanDirectory.isNotEmpty) ...[
                const SizedBox(height: 10),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Text(
                    _scanDirectory,
                    textAlign: TextAlign.center,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.outline,
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 20),
              TextButton.icon(
                onPressed: _stopScan,
                icon: const Icon(Icons.stop_circle_outlined),
                label: const Text('Stop'),
              ),
            ],
          ),
        );
      }

      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.folder_open_outlined,
                size: 80,
                color: Theme.of(context).colorScheme.outline,
              ),
              const SizedBox(height: 16),
              Text(
                _searchQuery.isNotEmpty
                    ? 'No APK files match your search'
                    : 'No APK files found',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
              if (_searchQuery.isEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'Tap Scan Now to look for APK files on this device',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 20),
                FilledButton.icon(
                  onPressed: _scanApkFiles,
                  icon: const Icon(Icons.search),
                  label: const Text('Scan Now'),
                ),
              ],
            ],
          ),
        ),
      );
    }

    return Column(
      children: [
        if (_isLoading) _buildScanProgressBar(),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.only(top: 4, bottom: 88),
            // Every tile has an identical intrinsic height, so the list can be
            // laid out from a single prototype: no per-item measurement, exact
            // scrollbar and instant jump-to-top behaviour on long lists.
            prototypeItem: _buildPrototypeTile(),
            addAutomaticKeepAlives: false,
            itemCount: _filteredApkFiles.length,
            cacheExtent: 600,
            itemBuilder: (context, index) {
              final apk = _filteredApkFiles[index];
              return ApkListTile(
                key: ValueKey<String>(apk.path),
                apk: apk,
                isSelected: _selectedPaths.contains(apk.path),
                onTap: () {
                  if (_selectedPaths.isNotEmpty) {
                    _toggleSelection(apk);
                  } else {
                    _showApkDetailsBottomSheet(apk);
                  }
                },
                onLongPress: () => _toggleSelection(apk),
                onInstall: () => _installApk(apk),
                onDelete: () => _deleteApk(apk),
                onAutoRename: () => _autoRenameApk(apk),
                onManualRename: () => _manualRenameApk(apk),
                onMove: () => _moveApk(apk),
                onDetails: () => _showApkDetail(apk),
                onShare: () => _shareApkFiles([apk]),
              );
            },
          ),
        ),
      ],
    );
  }

  /// A single, never-shown tile used by the list to measure item height once.
  Widget _buildPrototypeTile() {
    final sample = _filteredApkFiles.isNotEmpty
        ? _filteredApkFiles.first
        : ApkFile(
          fileName: 'example_1.0.apk',
          path: '/example_1.0.apk',
          size: 1024,
          appName: 'Example',
          packageName: 'com.example',
          versionName: '1.0',
          versionCode: 1,
        );
    return ApkListTile(
      apk: sample,
      isSelected: false,
      onTap: () {},
      onLongPress: () {},
      onInstall: () {},
      onDelete: () {},
      onAutoRename: () {},
      onManualRename: () {},
      onMove: () {},
    );
  }

  /// Thin progress strip shown above the list while a scan is running.
  Widget _buildScanProgressBar() {
    final colorScheme = Theme.of(context).colorScheme;
    final directory = _scanDirectory;
    final label = _isDiscovering
        ? 'Searching storage…'
        : 'Reading APK files…';
    return Material(
      color: colorScheme.surfaceContainerLow,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
        child: Row(
          children: [
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: colorScheme.primary,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '$_scanFoundCount found · $label',
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (directory.isNotEmpty)
                    Text(
                      directory,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: colorScheme.outline,
                      ),
                    ),
                ],
              ),
            ),
            TextButton(
              onPressed: _stopScan,
              child: const Text('Stop'),
            ),
          ],
        ),
      ),
    );
  }

  /// Requests the running scan to stop; partial results are kept.
  void _stopScan() {
    if (!_scannerService.isScanning) return;
    setState(() => _stopRequested = true);
    _showSnackBar('Stopping scan…');
  }

  void _onDrawerItemSelected(String route) {
    Navigator.of(context).pop();
    switch (route) {
      case 'apk_manager':
        break;
      case 'smart_organize':
        _smartOrganize();
        break;
      case 'installed':
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => const InstalledAppsPage(
              includeSystem: false,
              title: 'Installed Apps',
            ),
          ),
        );
        break;
      case 'app_system':
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => const InstalledAppsPage(
              includeSystem: true,
              title: 'System Apps',
            ),
          ),
        );
        break;
      case 'theme':
        _toggleTheme();
        break;
      case 'rate_us':
        _rateUs();
        break;
      case 'privacy_policy':
        _showPrivacyPolicy();
        break;
      case 'about':
        _showAbout();
        break;
      case 'share':
        _shareApp();
        break;
      case 'exit':
        _exitApp();
        break;
    }
  }

  Future<void> _shareApp() async {
    final playStoreUrl =
        'https://play.google.com/store/apps/details?id=com.apkorganizer';
    final text = 'Check out APK Organizer - Manage and organize your APK files!\n\n$playStoreUrl';
    await SharePlus.instance.share(ShareParams(text: text));
  }

  /// Shares one or more APK files via the system share sheet.
  Future<void> _shareApkFiles(List<ApkFile> apks) async {
    final files = <XFile>[];
    for (final apk in apks) {
      if (await File(apk.path).exists()) {
        files.add(
          XFile(
            apk.path,
            mimeType: 'application/vnd.android.package-archive',
          ),
        );
      }
    }
    if (files.isEmpty) {
      _showSnackBar('No valid APK file(s) to share.', isError: true);
      return;
    }
    try {
      await SharePlus.instance.share(
        ShareParams(
          files: files,
          text: files.length == 1
              ? 'Sharing ${apks.first.displayName}'
              : 'Sharing ${files.length} APK files',
        ),
      );
    } catch (e) {
      _showSnackBar('Failed to share: $e', isError: true);
    }
  }

  Future<void> _shareSelectedApks() async {
    if (_selectedPaths.isEmpty) return;
    final apks =
        _selectedPaths.map(_apkByPath).whereType<ApkFile>().toList();
    await _shareApkFiles(apks);
  }

  Future<void> _rateUs() async {
    const url =
        'https://play.google.com/store/apps/details?id=com.apkorganizer';
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else {
      _showSnackBar('Could not open Play Store', isError: true);
    }
  }

  void _showPrivacyPolicy() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => PrivacyPolicyPage(appVersion: widget.appVersion),
      ),
    );
  }

  void _showAbout() {
    showDialog(
      context: context,
      builder: (_) => AboutDialog(appVersion: widget.appVersion),
    );
  }

  IconData _themeToggleIcon() {
    final mode = PreferencesService.instance.themeMode.value;
    return mode == ThemeMode.dark
        ? Icons.light_mode_outlined
        : Icons.dark_mode_outlined;
  }

  String _themeToggleLabel() {
    final mode = PreferencesService.instance.themeMode.value;
    return mode == ThemeMode.dark ? 'Light Mode' : 'Dark Mode';
  }

  /// Toggles between dark and light theme. When the current theme is light the
  /// menu shows "Dark Mode" (tapping switches to dark); once dark, it shows
  /// "Light Mode" (tapping switches back to light). The app bar icon reflects
  /// the action too.
  Future<void> _toggleTheme() async {
    final prefs = PreferencesService.instance;
    final next =
        prefs.themeMode.value == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    await prefs.setThemeMode(next);
    if (mounted) setState(() {});
  }

  void _exitApp() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: AppRadius.dialogBorder),
        title: const Text('Exit'),
        content: const Text('Are you sure you want to exit?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              SystemNavigator.pop();
            },
            child: const Text('Exit'),
          ),
        ],
      ),
    );
  }

  void _showApkDetailsBottomSheet(ApkFile apk) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final dirPath = apk.directory;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(AppRadius.sheet),
        ),
      ),
      builder: (ctx) => DraggableScrollableSheet(
        initialChildSize: 0.55,
        maxChildSize: 0.85,
        minChildSize: 0.4,
        expand: false,
        builder: (ctx, scrollController) => SingleChildScrollView(
          controller: scrollController,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                margin: const EdgeInsets.only(top: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: colorScheme.onSurfaceVariant.withAlpha(77),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 16),
                child: Row(
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        color: colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(18),
                        boxShadow: [
                          BoxShadow(
                            color: colorScheme.primary.withAlpha(30),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: apk.iconPath != null && apk.iconPath!.isNotEmpty
                          ? ClipRRect(
                              borderRadius: BorderRadius.circular(18),
                              child: Image.file(
                                File(apk.iconPath!),
                                width: 72,
                                height: 72,
                                fit: BoxFit.cover,
                                cacheWidth: 216,
                                cacheHeight: 216,
                                filterQuality: FilterQuality.medium,
                                errorBuilder: (_, _, _) => Icon(
                                  Icons.android,
                                  color: colorScheme.primary,
                                  size: 40,
                                ),
                              ),
                            )
                          : Icon(
                              Icons.android,
                              color: colorScheme.primary,
                              size: 40,
                            ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            apk.displayName,
                            style: textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 4),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: colorScheme.surfaceContainerHighest,
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              apk.packageName,
                              style: textTheme.bodySmall?.copyWith(
                                color: colorScheme.onSurfaceVariant,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(left: 8, bottom: 14),
                      child: Text(
                        'Actions',
                        style: textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w600,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        BottomSheetAction(
                          icon: Icons.info_outline,
                          label: 'Details',
                          color: colorScheme.primary,
                          onTap: () {
                            Navigator.pop(ctx);
                            _showApkDetail(apk);
                          },
                        ),
                        BottomSheetAction(
                          icon: Icons.install_mobile,
                          label: 'Install',
                          color: colorScheme.primary,
                          onTap: () {
                            Navigator.pop(ctx);
                            _installApk(apk);
                          },
                        ),
                        BottomSheetAction(
                          icon: Icons.auto_fix_high,
                          label: 'Auto Rename',
                          color: colorScheme.tertiary,
                          onTap: () {
                            Navigator.pop(ctx);
                            _autoRenameApk(apk);
                          },
                        ),
                        BottomSheetAction(
                          icon: Icons.drive_file_rename_outline,
                          label: 'Rename',
                          color: colorScheme.secondary,
                          onTap: () {
                            Navigator.pop(ctx);
                            _manualRenameApk(apk);
                          },
                        ),
                        BottomSheetAction(
                          icon: Icons.drive_file_move_outlined,
                          label: 'Move',
                          color: Colors.deepOrange,
                          onTap: () {
                            Navigator.pop(ctx);
                            _moveApk(apk);
                          },
                        ),
                        BottomSheetAction(
                          icon: Icons.share_outlined,
                          label: 'Share',
                          color: colorScheme.secondary,
                          onTap: () {
                            Navigator.pop(ctx);
                            _shareApkFiles([apk]);
                          },
                        ),
                        BottomSheetAction(
                          icon: Icons.delete_outline,
                          label: 'Delete',
                          color: colorScheme.error,
                          onTap: () {
                            Navigator.pop(ctx);
                            _deleteApk(apk);
                          },
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 16,
                ),
                child: Column(
                  children: [
                    DetailRow(
                      icon: Icons.info_outline,
                      label: 'Version',
                      value: '${apk.versionName} (${apk.versionCode})',
                    ),
                    const SizedBox(height: 12),
                    DetailRow(
                      icon: Icons.folder_outlined,
                      label: 'Directory',
                      value: dirPath,
                    ),
                    const SizedBox(height: 12),
                    DetailRow(
                      icon: Icons.storage_outlined,
                      label: 'File Size',
                      value: apk.formattedSize,
                    ),
                    const SizedBox(height: 12),
                    DetailRow(
                      icon: Icons.description_outlined,
                      label: 'File Name',
                      value: apk.fileName,
                    ),
                    if (apk.lastModified > 0) ...[
                      const SizedBox(height: 12),
                      DetailRow(
                        icon: Icons.schedule_outlined,
                        label: 'Modified',
                        value: FormatUtil.formatAge(apk.lastModified),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget? _buildScanFab() {
    if (!_hasPermission || _selectedPaths.isNotEmpty || _isSearching) {
      return null;
    }
    if (_isLoading) {
      return FloatingActionButton.extended(
        heroTag: 'scan_fab',
        onPressed: _stopScan,
        backgroundColor: Theme.of(context).colorScheme.errorContainer,
        foregroundColor: Theme.of(context).colorScheme.onErrorContainer,
        icon: const Icon(Icons.stop_rounded),
        label: const Text('Stop'),
      );
    }
    return FloatingActionButton.extended(
      heroTag: 'scan_fab',
      onPressed: _scanApkFiles,
      icon: const Icon(Icons.refresh),
      label: const Text('Scan Now'),
    );
  }

  Widget _buildAppTitle() {
    if (!_isLoading) return const SizedBox.shrink();
    return const SizedBox(
      width: 20,
      height: 20,
      child: HexagonDotsLoading(minRadius: 4),
    );
  }

  Widget _buildApkCountBadge() {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: Container(
        height: 32,
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          color: colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.folder_zip_outlined,
              size: 16,
              color: colorScheme.onPrimaryContainer,
            ),
            const SizedBox(width: 6),
            Text(
              '${_allApkFiles.length}',
              style: textTheme.labelLarge?.copyWith(
                color: colorScheme.onPrimaryContainer,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(
        title: _isSearching
            ? SizedBox(
                height: 44,
                child: TextField(
                  autofocus: true,
                  focusNode: _searchFocusNode,
                  controller: _searchController,
                  decoration: InputDecoration(
                    hintText: 'Search APKs...',
                    hintStyle: TextStyle(
                      color: colorScheme.onSurfaceVariant.withAlpha(150),
                      fontSize: 14,
                    ),
                    prefixIcon: Icon(
                      Icons.search_rounded,
                      color: colorScheme.onSurfaceVariant.withAlpha(150),
                      size: 20,
                    ),
                    suffixIcon: _searchQuery.isNotEmpty
                        ? IconButton(
                            icon: Icon(
                              Icons.close_rounded,
                              size: 18,
                              color: colorScheme.onSurfaceVariant.withAlpha(150),
                            ),
                            onPressed: () {
                              _searchController.clear();
                              _searchDebounce?.cancel();
                              setState(() {
                                _searchQuery = '';
                                _applyFilter();
                              });
                              _searchFocusNode.requestFocus();
                            },
                          )
                        : null,
                    filled: true,
                    fillColor: colorScheme.surfaceContainerHighest.withAlpha(120),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(999),
                      borderSide: BorderSide.none,
                    ),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 10,
                    ),
                    isDense: true,
                  ),
                  style: textTheme.bodyLarge?.copyWith(fontSize: 14),
                  onChanged: _onSearchChanged,
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => _searchFocusNode.unfocus(),
                ),
              )
            : _buildAppTitle(),
        actions: [
          _buildApkCountBadge(),
          if (_isSearching)
            IconButton(
              icon: const Icon(Icons.close),
              onPressed: () {
                _searchDebounce?.cancel();
                _searchFocusNode.unfocus();
                setState(() {
                  _isSearching = false;
                  _searchQuery = '';
                  _searchController.clear();
                  _applyFilter();
                });
              },
            )
          else if (_allApkFiles.isNotEmpty) ...[
            IconButton(
              icon: Icon(_themeToggleIcon()),
              tooltip: _themeToggleLabel(),
              onPressed: _toggleTheme,
            ),
            _buildSortButton(),
            _buildFilterButton(),
            IconButton(
              icon: const Icon(Icons.search),
              tooltip: 'Search',
              onPressed: () => setState(() => _isSearching = true),
            ),
          ],
        ],
      ),
      drawer: NavigationDrawer(
        selectedIndex: 0,
        onDestinationSelected: (idx) {
          final routes = [
            'apk_manager',
            'smart_organize',
            'installed',
            'app_system',
            'theme',
            'rate_us',
            'privacy_policy',
            'about',
            'share',
            'exit',
          ];
          if (idx >= 0 && idx < routes.length) {
            _onDrawerItemSelected(routes[idx]);
          }
        },
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 24, 28, 16),
            child: Row(
              children: [
                Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withAlpha(60),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                        spreadRadius: 1,
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(14),
                    child: Image.asset(
                      'assets/icon/app_icon.png',
                      width: 48,
                      height: 48,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'APK Organizer',
                      style: textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      'v${widget.appVersion}',
                      style: textTheme.bodySmall?.copyWith(
                        color: colorScheme.outline,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const Divider(indent: 28, endIndent: 28),
          NavigationDrawerDestination(
            icon: Icon(Icons.folder_outlined),
            selectedIcon: Icon(Icons.folder),
            label: Text('APK Manager (${_allApkFiles.length})'),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.auto_awesome_outlined),
            label: Text('Smart Organize'),
          ),
          const Divider(indent: 28, endIndent: 28),
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 16, 28, 8),
            child: Text(
              'Device Apps',
              style: textTheme.labelMedium?.copyWith(
                color: colorScheme.outline,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.apps_rounded),
            label: Text('Installed Apps'),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.settings_applications_rounded),
            label: Text('System Apps'),
          ),
          const Divider(indent: 28, endIndent: 28),
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 16, 28, 8),
            child: Text(
              'App Settings',
              style: textTheme.labelMedium?.copyWith(
                color: colorScheme.outline,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          NavigationDrawerDestination(
            icon: Icon(_themeToggleIcon()),
            selectedIcon: Icon(_themeToggleIcon()),
            label: Text(_themeToggleLabel()),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.star_outline_rounded),
            label: Text('Rate Us'),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.privacy_tip_outlined),
            label: Text('Privacy Policy'),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.info_outline_rounded),
            label: Text('About'),
          ),
          const NavigationDrawerDestination(
            icon: Icon(Icons.share_outlined),
            label: Text('Share'),
          ),
          const Divider(indent: 28, endIndent: 28),
          const NavigationDrawerDestination(
            icon: Icon(Icons.exit_to_app_rounded),
            label: Text('Exit'),
          ),
        ],
      ),
      body: _buildBody(),
      floatingActionButton: _buildScanFab(),
      bottomNavigationBar: _selectedPaths.isEmpty
          ? null
          : SelectionBottomBar(
                selectedCount: _selectedPaths.length,
                totalCount: _filteredApkFiles.length,
                onSelectAll: _selectAll,
                onClearSelection: _clearSelection,
                onInstall: _installSelectedApks,
                onDelete: _deleteSelectedApks,
                onAutoRename: _renameSelectedApksAuto,
                onMove: _moveSelectedApks,
                onShare: _shareSelectedApks,
              ),
    );
  }
}

/// A searchable bottom sheet for choosing a directory to filter APK files by.
class _DirectoryFilterSheet extends StatefulWidget {
  final List<String> directories;
  final String? currentFilter;

  const _DirectoryFilterSheet({
    required this.directories,
    required this.currentFilter,
  });

  @override
  State<_DirectoryFilterSheet> createState() => _DirectoryFilterSheetState();
}

class _DirectoryFilterSheetState extends State<_DirectoryFilterSheet> {
  late TextEditingController _controller;
  late List<String> _filtered;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _filtered = widget.directories;
    _controller.addListener(_onSearch);
  }

  void _onSearch() {
    final q = _controller.text.trim().toLowerCase();
    setState(() {
      _filtered = q.isEmpty
          ? widget.directories
          : widget.directories
              .where((d) => d.toLowerCase().contains(q))
              .toList();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final selected = widget.currentFilter;
    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      maxChildSize: 0.9,
      minChildSize: 0.4,
      expand: false,
      builder: (ctx, scrollController) => Column(
        children: [
          Container(
            margin: const EdgeInsets.only(top: 12),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: colorScheme.onSurfaceVariant.withAlpha(77),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
            child: Text(
              'Filter by directory',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: TextField(
              controller: _controller,
              decoration: InputDecoration(
                hintText: 'Search directories...',
                prefixIcon: const Icon(Icons.search, size: 20),
                suffixIcon: _controller.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.close, size: 18),
                        onPressed: () => _controller.clear(),
                      )
                    : null,
                filled: true,
                fillColor: colorScheme.surfaceContainerHighest.withAlpha(120),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(999),
                  borderSide: BorderSide.none,
                ),
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(horizontal: 12),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: ListView.builder(
              controller: scrollController,
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: _filtered.length + 1,
              itemBuilder: (context, index) {
                if (index == 0) {
                  final isSel = selected == null;
                  return ListTile(
                    leading: Icon(Icons.all_inclusive,
                        color: isSel ? colorScheme.primary : null),
                    title: Text('All Directories',
                        style: isSel
                            ? TextStyle(
                                color: colorScheme.primary,
                                fontWeight: FontWeight.w600)
                            : null),
                    selected: isSel,
                    onTap: () => Navigator.pop(ctx, 'all'),
                  );
                }
                final dir = _filtered[index - 1];
                final isSel = selected == dir;
                return ListTile(
                  leading: Icon(Icons.folder_outlined,
                      color: isSel ? colorScheme.primary : null),
                  title: Text(dir.split('/').last,
                      overflow: TextOverflow.ellipsis),
                  subtitle: Text(dir,
                      maxLines: 1, overflow: TextOverflow.ellipsis),
                  selected: isSel,
                  onTap: () => Navigator.pop(ctx, dir),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

enum ApkSortMode { name, size, date, version }

/// A simple cooperative cancellation flag for long-running organize tasks.
class CancellationToken {
  bool _cancelled = false;

  void cancel() => _cancelled = true;

  bool get isCancelled => _cancelled;
}
