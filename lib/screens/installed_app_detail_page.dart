import 'dart:io';

import 'package:flutter/material.dart';

import '../models/installed_app.dart';
import '../widgets/compact_action_chip.dart';

class InstalledAppDetailPage extends StatefulWidget {
  final InstalledApp app;
  final VoidCallback onBackup;
  final VoidCallback onUninstall;

  const InstalledAppDetailPage({
    super.key,
    required this.app,
    required this.onBackup,
    required this.onUninstall,
  });

  @override
  State<InstalledAppDetailPage> createState() => _InstalledAppDetailPageState();
}

class _InstalledAppDetailPageState extends State<InstalledAppDetailPage> {
  late final String _installedDate;

  @override
  void initState() {
    super.initState();
    _installedDate = _resolveInstalledDate();
  }

  String _resolveInstalledDate() {
    try {
      final file = File(widget.app.sourceDir);
      if (file.existsSync()) {
        final date = file.lastModifiedSync();
        final months = [
          'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
          'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
        ];
        return '${months[date.month - 1]} ${date.day}, ${date.year}';
      }
    } catch (_) {}
    return 'Unknown';
  }

  @override
  Widget build(BuildContext context) {
    final app = widget.app;
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: Text(app.displayName)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildHeader(colorScheme, textTheme),
          const SizedBox(height: 20),
          _buildSectionTitle('App Info', colorScheme, textTheme),
          const SizedBox(height: 8),
          _buildInfoCard(context, [
            _infoRow(context, Icons.info_outline, 'Version', app.versionName),
            _infoRow(context, Icons.numbers_outlined, 'Version Code',
                app.versionCode.toString()),
            _infoRow(
              context,
              Icons.category_outlined,
              'Type',
              app.isSystemApp ? 'System App' : 'User App',
            ),
            _infoRow(context, Icons.folder_outlined, 'Package', app.packageName),
            _infoRow(context, Icons.storage_outlined, 'Size', app.formattedSize),
          ], colorScheme),
          const SizedBox(height: 20),
          _buildSectionTitle('Installation', colorScheme, textTheme),
          const SizedBox(height: 8),
          _buildInfoCard(context, [
            _infoRow(context, Icons.source_outlined, 'Source', app.sourceDir),
            _infoRow(context, Icons.event_available_outlined, 'Updated', _installedDate),
          ], colorScheme),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: CompactActionChip(
                  icon: Icons.archive_outlined,
                  label: 'Backup APK',
                  color: colorScheme.primary,
                  onTap: () {
                    Navigator.pop(context);
                    widget.onBackup();
                  },
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: CompactActionChip(
                  icon: Icons.delete_outline,
                  label: app.isSystemApp ? 'Uninstall Updates' : 'Uninstall',
                  color: colorScheme.error,
                  onTap: () {
                    Navigator.pop(context);
                    widget.onUninstall();
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildHeader(ColorScheme colorScheme, TextTheme textTheme) {
    final app = widget.app;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(18),
              ),
              child: app.iconPath != null && app.iconPath!.isNotEmpty
                  ? ClipRRect(
                      borderRadius: BorderRadius.circular(18),
                      child: Image.file(
                        File(app.iconPath!),
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => Icon(
                          Icons.android,
                          color: colorScheme.primary,
                          size: 36,
                        ),
                      ),
                    )
                  : Icon(
                      Icons.android,
                      color: colorScheme.primary,
                      size: 36,
                    ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    app.displayName,
                    style: textTheme.titleLarge
                        ?.copyWith(fontWeight: FontWeight.bold),
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
                      app.packageName,
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
    );
  }

  Widget _buildSectionTitle(
    String title,
    ColorScheme colorScheme,
    TextTheme textTheme,
  ) {
    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: Text(
        title,
        style: textTheme.titleSmall?.copyWith(
          fontWeight: FontWeight.w600,
          color: colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }

  Widget _buildInfoCard(BuildContext context, List<Widget> rows, ColorScheme colorScheme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(children: rows),
      ),
    );
  }

  Widget _infoRow(BuildContext context, IconData icon, String label, String value) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: colorScheme.onSurfaceVariant),
          const SizedBox(width: 10),
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
              textAlign: TextAlign.end,
            ),
          ),
        ],
      ),
    );
  }
}
