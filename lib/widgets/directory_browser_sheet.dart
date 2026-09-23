import 'package:flutter/material.dart';

import '../models/directory_entry.dart';
import '../services/apk_manager_service.dart';
import '../widgets/hexagon_dots_loading.dart';

class DirectoryBrowserSheet extends StatefulWidget {
  final List<DirectoryEntry> initialDirectories;

  const DirectoryBrowserSheet({super.key, required this.initialDirectories});

  @override
  State<DirectoryBrowserSheet> createState() => _DirectoryBrowserSheetState();
}

class _DirectoryBrowserSheetState extends State<DirectoryBrowserSheet> {
  late List<DirectoryEntry> _subdirectories;
  String? _currentDirectoryPath;
  String _currentDirectoryName = 'Internal Storage';
  bool _isLoading = false;
  final List<_DirectoryHistoryEntry> _navigationHistory = [];

  @override
  void initState() {
    super.initState();
    _subdirectories = widget.initialDirectories;
  }

  Future<void> _navigateInto(DirectoryEntry directory) async {
    if (!mounted) return;

    setState(() => _isLoading = true);
    try {
      _navigationHistory.add(
        _DirectoryHistoryEntry(
          directories: List.from(_subdirectories),
          path: _currentDirectoryPath,
          name: _currentDirectoryName,
        ),
      );

      final subdirectories = await ApkManagerService.getSubdirectories(
        directory.path,
      );
      if (!mounted) return;

      setState(() {
        _subdirectories = subdirectories;
        _currentDirectoryPath = directory.path;
        _currentDirectoryName = directory.name;
        _isLoading = false;
      });
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to open directory: ${e.message}'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  void _navigateBack() {
    if (_navigationHistory.isEmpty) return;

    setState(() {
      final previousState = _navigationHistory.removeLast();
      _subdirectories = previousState.directories;
      _currentDirectoryPath = previousState.path;
      _currentDirectoryName = previousState.name;
    });
  }

  Future<void> _createFolder() async {
    if (_currentDirectoryPath == null || _currentDirectoryPath!.isEmpty) return;

    final TextEditingController controller = TextEditingController();
    final String? folderName = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create New Folder'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Folder name',
            hintText: 'Enter folder name',
            prefixIcon: Icon(Icons.create_new_folder_outlined),
          ),
          textInputAction: TextInputAction.done,
          onSubmitted: (value) => Navigator.pop(context, value),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text),
            child: const Text('Create'),
          ),
        ],
      ),
    );
    controller.dispose();

    if (!mounted) return;
    if (folderName == null || folderName.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Folder name cannot be empty')),
      );
      return;
    }

    final String trimmedFolderName = folderName.trim();

    setState(() => _isLoading = true);
    try {
      final DirectoryEntry createdDirectory =
          await ApkManagerService.createDirectory(
            _currentDirectoryPath!,
            trimmedFolderName,
          );

      final List<DirectoryEntry> updatedSubdirectories =
          await ApkManagerService.getSubdirectories(_currentDirectoryPath!);
      if (!mounted) return;

      setState(() {
        _subdirectories = updatedSubdirectories;
        _isLoading = false;
      });

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Folder created: ${createdDirectory.name}')),
      );
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to create folder: ${e.message}'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool isRoot = _navigationHistory.isEmpty;
    return DraggableScrollableSheet(
      initialChildSize: 0.5,
      maxChildSize: 0.8,
      minChildSize: 0.3,
      expand: false,
      builder: (context, scrollController) => Column(
        children: [
          Container(
            margin: const EdgeInsets.only(top: 12),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Theme.of(
                context,
              ).colorScheme.onSurfaceVariant.withAlpha(77),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
            child: Row(
              children: [
                if (!isRoot)
                  IconButton(
                    icon: const Icon(Icons.arrow_back),
                    onPressed: _navigateBack,
                    tooltip: 'Go back',
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                  ),
                if (!isRoot) const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    isRoot
                        ? 'Select destination folder'
                        : _currentDirectoryName,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (!isRoot)
                  IconButton(
                    icon: const Icon(Icons.create_new_folder_outlined),
                    tooltip: 'Create new folder',
                    onPressed: _isLoading ? null : _createFolder,
                  ),
              ],
            ),
          ),
          if (!isRoot)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(
                _currentDirectoryPath ?? '',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          const Divider(height: 1),
          Expanded(
            child: _isLoading
                ? const Center(child: HexagonDotsLoading())
                : ListView.builder(
                    controller: scrollController,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    itemCount: _subdirectories.length + (isRoot ? 0 : 1),
                    itemBuilder: (context, index) {
                      if (!isRoot && index == 0) {
                        return ListTile(
                          leading: Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: Theme.of(
                                context,
                              ).colorScheme.primaryContainer,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Icon(
                              Icons.check_circle_outline,
                              color: Theme.of(context).colorScheme.primary,
                              size: 22,
                            ),
                          ),
                          title: Text(
                            'Use this folder',
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              color: Theme.of(context).colorScheme.primary,
                            ),
                          ),
                          subtitle: Text(
                            _currentDirectoryPath ?? '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          onTap: () =>
                              Navigator.pop(context, _currentDirectoryPath),
                        );
                      }

                      final dirIndex = isRoot ? index : index - 1;
                      final dir = _subdirectories[dirIndex];
                      return ListTile(
                        leading: Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: Theme.of(
                              context,
                            ).colorScheme.primaryContainer,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Icon(
                            Icons.folder_rounded,
                            color: Theme.of(context).colorScheme.primary,
                            size: 22,
                          ),
                        ),
                        title: Text(
                          dir.name,
                          style: const TextStyle(fontWeight: FontWeight.w500),
                        ),
                        subtitle: Text(
                          dir.path,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => _navigateInto(dir),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _DirectoryHistoryEntry {
  final List<DirectoryEntry> directories;
  final String? path;
  final String name;

  _DirectoryHistoryEntry({
    required this.directories,
    required this.path,
    required this.name,
  });
}
