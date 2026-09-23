import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart' hide AboutDialog;
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/apk_file.dart';
import '../services/apk_manager_service.dart';
import '../services/duplicate_handler.dart';
import '../services/file_operations.dart';
import '../services/app_update_service.dart';
import '../services/preferences_service.dart';
import '../services/renamer_service.dart';
import '../services/scanner_service.dart';
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
  List<ApkFile> _allApkFiles = [];
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
  StreamSubscription<Map<String, dynamic>>? _progressSubscription;

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
    WidgetsBinding.instance.removeObserver(this);
    _progressSubscription?.cancel();
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
      if (hasPerm && !_hasPermission) {
        setState(() => _hasPermission = true);
        await _scanApkFiles();
      } else if (hasPerm && _allApkFiles.isEmpty && !_isLoading) {
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
      await Future.delayed(const Duration(milliseconds: 500));
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
    final hasPerm = await PermissionUtils.hasStoragePermission();
    if (!hasPerm) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = 'Storage permission is required to scan APK files.';
      });
      return;
    }
    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _selectedPaths.clear();
      _allApkFiles = [];
      _filteredApkFiles = [];
    });
    _progressSubscription?.cancel();
    _progressSubscription = ApkManagerService.scanProgressStream.listen((
      event,
    ) {
      if (!mounted) return;
      if (event['type'] == 'progress') {
        setState(() {
          final apkMap = event['apk'];
          if (apkMap is Map) {
            _upsertScannedApk(
              ApkFile.fromMap(Map<String, dynamic>.from(apkMap)),
            );
          }
        });
      }
    });
    try {
      final result = await _scannerService.scanAllStorage();
      result.allFiles.sort(ApkFile.compareByDisplayName);
      if (!mounted) return;
      setState(() {
        _allApkFiles = result.allFiles;
        _applyFilter();
      });
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() => _errorMessage = e.message);
      _showSnackBar(e.message, isError: true);
    } finally {
      _progressSubscription?.cancel();
      _progressSubscription = null;
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _applyFilter() {
    final valid = _allApkFiles.map((a) => a.path).toSet();
    _selectedPaths.removeWhere((p) => !valid.contains(p));
    List<ApkFile> filtered;
    if (_searchQuery.trim().isEmpty) {
      filtered = List.from(_allApkFiles);
    } else {
      final q = _searchQuery.trim().toLowerCase();
      filtered = _allApkFiles.where((a) => a.searchableText.contains(q)).toList();
    }
    if (_filterDirectory != null) {
      filtered = filtered.where((a) => a.path.startsWith(_filterDirectory!)).toList();
    }
    _sortFiles(filtered);
    _filteredApkFiles = filtered;
  }

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(const Duration(milliseconds: 300), () {
      if (mounted) {
        setState(() {
          _searchQuery = value;
          _applyFilter();
        });
      }
    });
  }

  void _sortFiles(List<ApkFile> files) {
    files.sort((a, b) {
      int cmp;
      switch (_sortMode) {
        case ApkSortMode.name:
          cmp = ApkFile.compareByDisplayName(a, b);
        case ApkSortMode.size:
          cmp = a.size.compareTo(b.size);
        case ApkSortMode.date:
          cmp = a.lastModified.compareTo(b.lastModified);
        case ApkSortMode.version:
          cmp = a.versionCode.compareTo(b.versionCode);
      }
      return _sortAscending ? cmp : -cmp;
    });
  }

  void _upsertScannedApk(ApkFile apk) {
    if (apk.path.isEmpty) return;
    final index = _allApkFiles.indexWhere((item) => item.path == apk.path);
    if (index >= 0) {
      _allApkFiles[index] = apk;
    } else {
      _allApkFiles.add(apk);
    }
    _allApkFiles.sort(ApkFile.compareByDisplayName);
    _applyFilter();
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
        _showSnackBar('Installation started for ${apk.appName}');
      }
    } on ApkManagerException catch (e) {
      if (!silent) _showSnackBar(e.message, isError: true);
      rethrow;
    }
  }

  Future<void> _installSelectedApks() async {
    if (_selectedPaths.isEmpty) return;
    int started = 0;
    int failed = 0;
    final failedNames = <String>[];
    for (final path in List<String>.from(_selectedPaths)) {
      final apk = _apkByPath(path);
      if (apk == null) continue;
      try {
        await _installApk(apk, silent: true);
        started++;
      } catch (_) {
        failed++;
        failedNames.add(apk.appName);
      }
    }
    _showSnackBar(
      '$started installation(s) started'
      '${failed > 0 ? ', $failed failed: ${failedNames.join(", ")}' : ''}',
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
      'Are you sure you want to delete "${apk.appName}"?',
    );
    if (!confirmed) return;
    try {
      await ApkManagerService.deleteApk(apk.path);
      setState(() {
        _allApkFiles.removeWhere((a) => a.path == apk.path);
        _selectedPaths.remove(apk.path);
        _applyFilter();
      });
      _showSnackBar('${apk.appName} deleted');
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
    final failed = <String>[];
    for (final path in List<String>.from(_selectedPaths)) {
      try {
        await ApkManagerService.deleteApk(path);
      } on ApkManagerException {
        failed.add(path);
      }
    }
    setState(() {
      for (final path in List<String>.from(_selectedPaths)) {
        if (!failed.contains(path)) {
          _allApkFiles.removeWhere((a) => a.path == path);
        }
      }
      _selectedPaths.clear();
      _applyFilter();
    });
    if (failed.isEmpty) {
      _showSnackBar('All selected APKs deleted');
    } else {
      _showSnackBar('${failed.length} file(s) failed to delete', isError: true);
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
    final newName = apk.suggestedRename;
    try {
      final result = await ApkManagerService.renameApk(apk.path, newName);
      final oldName = apk.fileName;
      final newPath = result['newPath'] as String?;
      final newNameResult = result['newName'] as String?;
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
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Rename APK'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(
            labelText: 'New file name',
            hintText: 'e.g., MyApp_1.0.apk',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
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
    try {
      final result = await ApkManagerService.renameApk(apk.path, newName.trim());
      _applyRenameResult(apk.path, result);
      _showSnackBar('File renamed successfully');
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  /// Applies a native rename result to the in-memory list without re-scanning.
  void _applyRenameResult(String oldPath, Map<String, dynamic> result) {
    final newPath = result['newPath'] as String?;
    final newName = result['newName'] as String?;
    if (newPath == null || newName == null) return;
    if (!mounted) return;
    setState(() {
      final index = _allApkFiles.indexWhere((a) => a.path == oldPath);
      if (index >= 0) {
        _allApkFiles[index] =
            _allApkFiles[index].copyWith(path: newPath, fileName: newName);
        _allApkFiles.sort(ApkFile.compareByDisplayName);
      }
      if (_selectedPaths.remove(oldPath)) _selectedPaths.add(newPath);
      _applyFilter();
    });
  }

  /// Applies a reverse rename (newPath -> originalPath/originalName) to the
  /// in-memory list, used by the rename "Undo" action.
  void _applyRenameResultFrom(
    String newPath,
    String originalPath,
    String originalName,
  ) {
    if (!mounted) return;
    setState(() {
      final index = _allApkFiles.indexWhere((a) => a.path == newPath);
      if (index >= 0) {
        _allApkFiles[index] = _allApkFiles[index].copyWith(
          path: originalPath,
          fileName: originalName,
        );
        _allApkFiles.sort(ApkFile.compareByDisplayName);
      }
      if (_selectedPaths.remove(newPath)) _selectedPaths.add(originalPath);
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
      'Rename ${_selectedPaths.length} APK file(s) using AppName_Version format?',
    );
    if (!confirmed) return;
    final selected = _selectedPaths
        .map(_apkByPath)
        .whereType<ApkFile>()
        .toList();
    final summary = await _renamerService.autoRenameAll(selected);
    // Update the in-memory list from the returned results instead of a full
    // storage re-scan.
    final renameByOldPath = {
      for (final r in summary.results)
        if (r.success && r.newPath != null && r.newName != null)
          r.originalPath: r,
    };
    if (mounted) {
      setState(() {
        _allApkFiles = _allApkFiles.map((apk) {
          final r = renameByOldPath[apk.path];
          if (r == null) return apk;
          return apk.copyWith(path: r.newPath, fileName: r.newName);
        }).toList();
        _allApkFiles.sort(ApkFile.compareByDisplayName);
        _selectedPaths.clear();
        _applyFilter();
      });
    }
    _showSnackBar(
      '${summary.succeeded} file(s) renamed'
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

  String _originalFileName(String path) {
    final idx = path.lastIndexOf('/');
    return idx >= 0 ? path.substring(idx + 1) : path;
  }

  Future<void> _undoRename(
    List<({String newPath, String originalPath, String originalName})> items,
  ) async {
    int failed = 0;
    for (final item in items) {
      try {
        await ApkManagerService.renameApk(item.newPath, item.originalName);
        _applyRenameResultFrom(
          item.newPath,
          item.originalPath,
          item.originalName,
        );
      } on ApkManagerException {
        failed++;
      }
    }
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
          appName: apk.appName,
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
      final selectedDir = await showModalBottomSheet<String>(
        context: context,
        isScrollControlled: true,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
        builder: (ctx) => DirectoryBrowserSheet(initialDirectories: roots),
      );
      if (selectedDir == null) return;
      final summary = await _fileOperations.batchMove(paths, selectedDir);
      // Update the in-memory list from the returned results instead of a full
      // storage re-scan.
      final moveByOldPath = {
        for (final r in summary.results)
          if (r.success && r.destPath != null) r.sourcePath: r.destPath!,
      };
      if (mounted) {
        setState(() {
          _allApkFiles = _allApkFiles.map((apk) {
            final newPath = moveByOldPath[apk.path];
            if (newPath == null) return apk;
            final idx = newPath.lastIndexOf('/');
            final newFileName =
                idx >= 0 ? newPath.substring(idx + 1) : apk.fileName;
            return apk.copyWith(path: newPath, fileName: newFileName);
          }).toList();
          _allApkFiles.sort(ApkFile.compareByDisplayName);
          _selectedPaths.clear();
          _applyFilter();
        });
      }
      final msg =
          '${summary.succeeded} file(s) moved${summary.failed > 0 ? ', ${summary.failed} failed' : ''}${summary.conflictsResolved > 0 ? ', ${summary.conflictsResolved} conflict(s) resolved' : ''}';
      final undoMap = <String, String>{};
      for (final r in summary.results) {
        if (r.success && r.destPath != null) {
          undoMap[r.destPath!] = r.sourcePath;
        }
      }
      _showSnackBar(
        msg,
        actionLabel: undoMap.isNotEmpty ? 'Undo' : null,
        onAction: undoMap.isNotEmpty ? () => _undoMove(undoMap) : null,
        duration: const Duration(seconds: 6),
      );
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  Future<void> _undoMove(Map<String, String> undoMap) async {
    final failed = <String>[];
    for (final entry in undoMap.entries) {
      try {
        await ApkManagerService.moveApk(entry.key, entry.value);
      } on ApkManagerException {
        failed.add(entry.key);
      }
    }
    if (mounted) {
      setState(() {
        for (final entry in undoMap.entries) {
          final idx = entry.value.lastIndexOf('/');
          final newFileName =
              idx >= 0 ? entry.value.substring(idx + 1) : '';
          final index = _allApkFiles.indexWhere((a) => a.path == entry.key);
          if (index >= 0) {
            _allApkFiles[index] = _allApkFiles[index].copyWith(
              path: entry.value,
              fileName: newFileName.isNotEmpty
                  ? newFileName
                  : _allApkFiles[index].fileName,
            );
          }
        }
        _allApkFiles.sort(ApkFile.compareByDisplayName);
        _applyFilter();
      });
    }
    _showSnackBar(
      failed.isEmpty
          ? 'Move undone'
          : '${failed.length} file(s) failed to restore',
      isError: failed.isNotEmpty,
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
      if (_selectedPaths.length == _filteredApkFiles.length) {
        _selectedPaths.clear();
      } else {
        _selectedPaths.addAll(_filteredApkFiles.map((a) => a.path));
      }
    });
  }

  void _clearSelection() => setState(() => _selectedPaths.clear());

  ApkFile? _apkByPath(String path) {
    for (final apk in _allApkFiles) {
      if (apk.path == path) return apk;
    }
    return null;
  }

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
      final renameByOldPath = {
        for (final r in renameSummary.results)
          if (r.success) r.originalPath: r,
      };
      if (mounted) {
        setState(() {
          _allApkFiles = _allApkFiles.map((apk) {
            final r = renameByOldPath[apk.path];
            if (r == null) return apk;
            return apk.copyWith(path: r.newPath, fileName: r.newName);
          }).toList();
          _selectedPaths.clear();
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

      final deletedPaths = dupSummary.deletedPaths.toSet();
      if (mounted) {
        setState(() {
          _allApkFiles.removeWhere((apk) => deletedPaths.contains(apk.path));
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
          if (dupSummary.filesDeleted > 0)
            'Removed ${dupSummary.filesDeleted} duplicate(s)',
        ],
        errors: [
          ...renameSummary.results
              .where((r) => !r.success)
              .map((r) => 'Rename: ${r.error}'),
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
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => ValueListenableBuilder<(int, int, String)>(
        valueListenable: progress,
        builder: (_, value, _) {
          final done = value.$1;
          final total = value.$2;
          final ratio = total == 0 ? null : done / total;
          return AlertDialog(
            title: Text('Auto Organize — ${value.$3}'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                LinearProgressIndicator(value: ratio),
                const SizedBox(height: 12),
                Text(
                  total == 0 ? 'Working…' : '$done / $total',
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
  }

  void _closeOrganizeProgressDialog() {
    if (!mounted) return;
    final navigator = Navigator.of(context, rootNavigator: true);
    if (navigator.canPop()) navigator.pop();
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
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
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
    final dirs = _allApkFiles
        .map((a) {
          final idx = a.path.lastIndexOf('/');
          return idx > 0 ? a.path.substring(0, idx) : '/';
        })
        .toSet()
        .toList()
      ..sort();
    if (!mounted) return;
    final selected = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (ctx) => _DirectoryFilterSheet(
        directories: dirs,
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
        return const Center(child: HexagonDotsLoading(minRadius: 10));
      }

      return Center(
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
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            if (_searchQuery.isEmpty) ...[
              const SizedBox(height: 8),
              Text(
                'Tap the scan button to search for APK files',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 80),
      itemCount: _filteredApkFiles.length,
      itemBuilder: (context, index) {
        final apk = _filteredApkFiles[index];
        return ApkListTile(
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
    );
  }

  void _onDrawerItemSelected(String route) {
    Navigator.pop(context);
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
              ? 'Sharing ${apks.first.appName}'
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
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
    final lastSlash = apk.path.lastIndexOf('/');
    final dirPath = lastSlash >= 0 ? apk.path.substring(0, lastSlash) : '/';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
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
                            apk.appName.isNotEmpty ? apk.appName : apk.fileName,
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
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
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
                      borderRadius: BorderRadius.circular(24),
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
      floatingActionButton:
          (_hasPermission &&
              _selectedPaths.isEmpty &&
              !_isLoading &&
              !_isSearching)
          ? FloatingActionButton.extended(
              onPressed: _scanApkFiles,
              icon: const Icon(Icons.refresh),
              label: const Text('Scan Now'),
            )
          : null,
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
                  borderRadius: BorderRadius.circular(24),
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
