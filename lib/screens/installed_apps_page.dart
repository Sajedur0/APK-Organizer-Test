import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../models/installed_app.dart';
import '../screens/installed_app_detail_page.dart';
import '../services/apk_manager_service.dart';
import '../widgets/compact_action_chip.dart';
import '../widgets/directory_browser_sheet.dart';
import '../widgets/hexagon_dots_loading.dart';

class InstalledAppsPage extends StatefulWidget {
  final bool includeSystem;
  final String title;

  const InstalledAppsPage({
    super.key,
    required this.includeSystem,
    required this.title,
  });

  @override
  State<InstalledAppsPage> createState() => _InstalledAppsPageState();
}

class _InstalledAppsPageState extends State<InstalledAppsPage>
    with WidgetsBindingObserver {
  List<InstalledApp> _apps = [];
  List<InstalledApp> _filteredApps = [];
  final Set<String> _selectedPackages = {};
  final Set<String> _pendingUninstallPackages = {};
  bool _isLoading = true;
  String? _errorMessage;
  String _searchQuery = '';
  bool _isSearching = false;
  late TextEditingController _searchController;
  late FocusNode _searchFocusNode;
  Timer? _searchDebounce;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _searchFocusNode = FocusNode();
    WidgetsBinding.instance.addObserver(this);
    _loadApps();
    ApkManagerService.addOnPackageRemovedListener(_onPackageRemoved);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocusNode.dispose();
    _searchDebounce?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    ApkManagerService.removeOnPackageRemovedListener(_onPackageRemoved);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _onAppResumed();
  }

  Future<void> _onAppResumed() async {
    if (_pendingUninstallPackages.isNotEmpty) {
      _pendingUninstallPackages.clear();
      await _loadApps();
      return;
    }

    if (_selectedPackages.isNotEmpty) {
      bool hasChanges = false;
      final Set<String> stillSelected = {};
      for (final pkg in _selectedPackages) {
        final exists = _apps.any((app) => app.packageName == pkg);
        if (!exists) {
          hasChanges = true;
        } else {
          stillSelected.add(pkg);
        }
      }
      if (hasChanges && mounted) {
        setState(() {
          _selectedPackages.clear();
          _selectedPackages.addAll(stillSelected);
        });
        _showSnackBar('Some apps were uninstalled');
        await _loadApps();
      }
    }
  }

  Future<void> _loadApps() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _selectedPackages.clear();
    });
    try {
      final apps = await ApkManagerService.getInstalledApps(
        includeSystem: widget.includeSystem,
      );
      if (!mounted) return;
      setState(() {
        _apps = apps;
        _applyFilter();
        _isLoading = false;
      });
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.message;
        _isLoading = false;
      });
    }
  }

  void _applyFilter() {
    final validPkgs = _apps.map((a) => a.packageName).toSet();
    _selectedPackages.removeWhere((p) => !validPkgs.contains(p));
    if (_searchQuery.trim().isEmpty) {
      _filteredApps = List.from(_apps);
    } else {
      final q = _searchQuery.trim().toLowerCase();
      _filteredApps = _apps.where((a) => a.searchableText.contains(q)).toList();
    }
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

  void _openAppDetails(InstalledApp app) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (ctx) => InstalledAppDetailPage(
          app: app,
          onBackup: () => _backupApp(app),
          onUninstall: () => _uninstallApp(app),
        ),
      ),
    );
  }

  void _toggleSelection(InstalledApp app) {
    setState(() {
      if (_selectedPackages.contains(app.packageName)) {
        _selectedPackages.remove(app.packageName);
      } else {
        _selectedPackages.add(app.packageName);
      }
    });
  }

  void _selectAll() {
    setState(() {
      if (_selectedPackages.length == _filteredApps.length) {
        _selectedPackages.clear();
      } else {
        _selectedPackages
          ..clear()
          ..addAll(_filteredApps.map((a) => a.packageName));
      }
    });
  }

  void _clearSelection() => setState(() { _selectedPackages.clear(); });

  InstalledApp? _appByPackage(String pkg) {
    for (final app in _apps) {
      if (app.packageName == pkg) return app;
    }
    return null;
  }

  Future<String?> _pickBackupDirectory() async {
    final roots = await ApkManagerService.getDirectories();
    if (!mounted) return null;
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(8)),
      ),
      builder: (ctx) => DirectoryBrowserSheet(initialDirectories: roots),
    );
  }

  Future<void> _backupApp(InstalledApp app) async {
    try {
      final dir = await _pickBackupDirectory();
      if (dir == null) return;
      final result = await ApkManagerService.backupInstalledApp(
        app.packageName,
        dir,
      );
      if (!mounted) return;
      _showSnackBar('Backup saved: ${result['fileName'] ?? app.displayName}');
    } on ApkManagerException catch (e) {
      _showSnackBar(e.message, isError: true);
    }
  }

  Future<void> _backupSelectedApps() async {
    if (_selectedPackages.isEmpty) return;
    try {
      final dir = await _pickBackupDirectory();
      if (dir == null) return;
      int ok = 0, fail = 0;
      setState(() => _isLoading = true);
      for (final pkg in List<String>.from(_selectedPackages)) {
        final app = _appByPackage(pkg);
        if (app == null) continue;
        try {
          await ApkManagerService.backupInstalledApp(app.packageName, dir);
          ok++;
        } on ApkManagerException catch (_) {
          fail++;
        }
      }
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _selectedPackages.clear();
      });
      _showSnackBar(
        '$ok app(s) backed up${fail > 0 ? ', $fail failed' : ''}',
        isError: fail > 0 && ok == 0,
      );
    } on ApkManagerException catch (e) {
      if (mounted) setState(() => _isLoading = false);
      _showSnackBar(e.message, isError: true);
    }
  }

  void _onPackageRemoved(String packageName) {
    if (!mounted) return;
    setState(() {
      _apps.removeWhere((app) => app.packageName == packageName);
      _selectedPackages.remove(packageName);
      _pendingUninstallPackages.remove(packageName);
      _applyFilter();
    });
  }

  Future<void> _uninstallApp(InstalledApp app) async {
    try {
      _pendingUninstallPackages.add(app.packageName);
      await ApkManagerService.uninstallPackage(app.packageName);
      _showSnackBar(
        'Uninstall requested for ${app.displayName}. Please confirm in system dialog.',
      );
    } on ApkManagerException catch (e) {
      _pendingUninstallPackages.remove(app.packageName);
      _showSnackBar(e.message, isError: true);
    }
  }

  Future<void> _uninstallSelectedApps() async {
    if (_selectedPackages.isEmpty) return;
    final confirmed = await _confirm(
      'Uninstall Selected',
      'Open uninstall confirmation for ${_selectedPackages.length} selected app(s)?',
    );
    if (!confirmed) return;

    final int count = _selectedPackages.length;
    for (final pkg in List<String>.from(_selectedPackages)) {
      final app = _appByPackage(pkg);
      if (app == null) continue;
      try {
        _pendingUninstallPackages.add(app.packageName);
        await ApkManagerService.uninstallPackage(app.packageName);
      } catch (_) {
        _pendingUninstallPackages.remove(app.packageName);
        // Ignore errors for batch
      }
    }
    setState(() => _selectedPackages.clear());
    _showSnackBar(
      'Uninstall requests sent for $count app(s). Please confirm in system dialogs.',
    );
  }

  Future<bool> _confirm(String title, String message) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  void _showSnackBar(String message, {bool isError = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).clearSnackBars();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? Theme.of(context).colorScheme.error : null,
      ),
    );
  }

  Widget _buildTitle() {
    return Row(
      children: [
        Expanded(
          child: Text(
            widget.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (_isLoading) ...[
          const SizedBox(width: 10),
          const SizedBox(
            width: 20,
            height: 20,
            child: HexagonDotsLoading(minRadius: 4),
          ),
        ],
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final allSelected =
        _selectedPackages.isNotEmpty &&
        _selectedPackages.length == _filteredApps.length;

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
                    hintText: 'Search apps...',
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
                              color: colorScheme.onSurfaceVariant.withAlpha(
                                150,
                              ),
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
                    fillColor: colorScheme.surfaceContainerHighest
                        .withAlpha(120),
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
            : _buildTitle(),
        actions: [
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
          else ...[
            IconButton(
              icon: Icon(
                allSelected ? Icons.check_box : Icons.check_box_outline_blank,
                color: allSelected ? colorScheme.primary : null,
              ),
              tooltip: 'Select all',
              onPressed: _filteredApps.isEmpty ? null : _selectAll,
            ),
            IconButton(
              icon: const Icon(Icons.search),
              tooltip: 'Search',
              onPressed: () => setState(() => _isSearching = true),
            ),
          ],
          if (!_isSearching)
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Refresh',
              onPressed: _isLoading ? null : _loadApps,
            ),
        ],
      ),
      bottomNavigationBar: _selectedPackages.isEmpty
          ? null
          : _InstalledAppsBottomBar(
              selectedCount: _selectedPackages.length,
              totalCount: _filteredApps.length,
              onSelectAll: _selectAll,
              onClearSelection: _clearSelection,
              onBackup: _backupSelectedApps,
              onUninstall: _uninstallSelectedApps,
            ),
      body: _buildBody(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    if (_isLoading && _apps.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const HexagonDotsLoading(minRadius: 10),
            const SizedBox(height: 16),
            Text(
              widget.includeSystem
                  ? 'Loading system apps...'
                  : 'Loading installed apps...',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      );
    }
    if (_errorMessage != null && _apps.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.error_outline, size: 48, color: colorScheme.error),
              const SizedBox(height: 16),
              Text(_errorMessage!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _loadApps,
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }
    if (_filteredApps.isEmpty) {
      return Center(
        child: Text(
          _searchQuery.isEmpty ? 'No apps found' : 'No apps match your search',
          style: Theme.of(context).textTheme.titleMedium,
        ),
      );
    }

    return Column(
      children: [
        Container(
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainerHighest.withAlpha(128),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              Icon(
                widget.includeSystem
                    ? Icons.settings_applications_rounded
                    : Icons.apps_rounded,
                size: 18,
                color: colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 8),
              Text('${_filteredApps.length} app(s)'),
              const Spacer(),
              if (_isLoading)
                const SizedBox(
                  width: 16,
                  height: 16,
                  child: HexagonDotsLoading(),
                ),
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: _filteredApps.length,
            itemBuilder: (context, index) {
              final app = _filteredApps[index];
              final selected = _selectedPackages.contains(app.packageName);
              return _InstalledAppTile(
                key: ValueKey(app.packageName),
                app: app,
                isSelected: selected,
                onTap: () {
                  if (_selectedPackages.isNotEmpty) {
                    _toggleSelection(app);
                  } else {
                    _openAppDetails(app);
                  }
                },
                onLongPress: () => _toggleSelection(app),
                onBackup: () => _backupApp(app),
                onUninstall: () => _uninstallApp(app),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _InstalledAppTile extends StatelessWidget {
  final InstalledApp app;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onBackup;
  final VoidCallback onUninstall;

  const _InstalledAppTile({
    super.key,
    required this.app,
    required this.isSelected,
    required this.onTap,
    required this.onLongPress,
    required this.onBackup,
    required this.onUninstall,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      color: isSelected ? colorScheme.primaryContainer.withAlpha(110) : null,
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        onLongPress: onLongPress,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  Container(
                    width: 50,
                    height: 50,
                    decoration: BoxDecoration(
                      color: colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: app.iconPath != null && app.iconPath!.isNotEmpty
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.file(
                              File(app.iconPath!),
                              fit: BoxFit.cover,
                              errorBuilder: (_, error, stackTrace) =>
                                  Icon(Icons.apps, color: colorScheme.primary),
                            ),
                          )
                        : Icon(Icons.apps, color: colorScheme.primary),
                  ),
                  if (isSelected)
                    Positioned(
                      right: -5,
                      top: -5,
                      child: CircleAvatar(
                        radius: 11,
                        backgroundColor: colorScheme.primary,
                        child: const Icon(
                          Icons.check,
                          size: 13,
                          color: Colors.white,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      app.displayName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      app.packageName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'v${app.versionName} - ${app.formattedSize}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: colorScheme.outline,
                      ),
                    ),
                  ],
                ),
              ),
              PopupMenuButton<String>(
                onSelected: (v) {
                  if (v == 'backup') onBackup();
                  if (v == 'uninstall') onUninstall();
                },
                itemBuilder: (ctx) => [
                  const PopupMenuItem(
                    value: 'backup',
                    child: ListTile(
                      leading: Icon(Icons.archive_outlined),
                      title: Text('Backup APK'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  PopupMenuItem(
                    value: 'uninstall',
                    child: ListTile(
                      leading: Icon(
                        Icons.delete_outline,
                        color: colorScheme.error,
                      ),
                      title: Text(
                        app.isSystemApp ? 'Uninstall Updates' : 'Uninstall',
                        style: TextStyle(color: colorScheme.error),
                      ),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _InstalledAppsBottomBar extends StatelessWidget {
  final int selectedCount;
  final int totalCount;
  final VoidCallback onSelectAll;
  final VoidCallback onClearSelection;
  final VoidCallback onBackup;
  final VoidCallback onUninstall;

  const _InstalledAppsBottomBar({
    required this.selectedCount,
    required this.totalCount,
    required this.onSelectAll,
    required this.onClearSelection,
    required this.onBackup,
    required this.onUninstall,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(26),
            blurRadius: 12,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  TextButton.icon(
                    onPressed: onSelectAll,
                    icon: Icon(
                      selectedCount == totalCount
                          ? Icons.check_box
                          : Icons.check_box_outline_blank,
                    ),
                    label: const Text('Select All'),
                  ),
                  const Spacer(),
                  Text('$selectedCount selected'),
                  IconButton(
                    onPressed: onClearSelection,
                    icon: const Icon(Icons.close),
                    tooltip: 'Clear selection',
                  ),
                ],
              ),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  CompactActionChip(
                    icon: Icons.archive_outlined,
                    label: 'Backup',
                    color: colorScheme.primary,
                    onTap: onBackup,
                  ),
                  CompactActionChip(
                    icon: Icons.delete_outline,
                    label: 'Uninstall',
                    color: colorScheme.error,
                    onTap: onUninstall,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
