import 'dart:io';

import 'package:flutter/material.dart';

import '../app_theme.dart';
import '../models/apk_detail.dart';
import '../services/apk_manager_service.dart';
import '../widgets/hexagon_dots_loading.dart';

class ApkDetailPage extends StatefulWidget {
  final String filePath;
  final String appName;

  const ApkDetailPage({
    super.key,
    required this.filePath,
    required this.appName,
  });

  @override
  State<ApkDetailPage> createState() => _ApkDetailPageState();
}

class _ApkDetailPageState extends State<ApkDetailPage> {
  ApkDetailInfo? _detail;
  bool _isLoading = true;
  String? _error;
  bool _showAllPermissions = false;

  @override
  void initState() {
    super.initState();
    _loadDetail();
  }

  Future<void> _loadDetail() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final detail = await ApkManagerService.getApkDetail(widget.filePath);
      if (!mounted) return;
      setState(() {
        _detail = detail;
        _isLoading = false;
      });
    } on ApkManagerException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: Text(_detail?.appName.isNotEmpty == true ? _detail!.appName : widget.appName)),
      body: _isLoading
          ? const Center(child: HexagonDotsLoading(minRadius: 10))
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.error_outline, size: 48, color: colorScheme.error),
                        const SizedBox(height: 16),
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        FilledButton.icon(
                          onPressed: _loadDetail,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Retry'),
                        ),
                      ],
                    ),
                  ),
                )
              : _buildContent(colorScheme, textTheme),
    );
  }

  Widget _buildContent(ColorScheme colorScheme, TextTheme textTheme) {
    final detail = _detail!;
    final displayPermissions = _showAllPermissions
        ? detail.permissions
        : detail.permissions.take(10).toList();
    final hasMore = detail.permissions.length > 10;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _buildHeader(detail, colorScheme, textTheme),
        const SizedBox(height: 20),
        _buildSectionTitle('App Info', colorScheme, textTheme),
        const SizedBox(height: 8),
        _buildInfoCard([
          _infoRow(Icons.info_outline, 'Version', '${detail.versionName} (${detail.versionCode})'),
          _infoRow(Icons.sd_card_outlined, 'SDK', detail.sdkDisplay),
          _infoRow(Icons.folder_outlined, 'Package', detail.packageName),
          _infoRow(Icons.storage_outlined, 'Size', detail.formattedSize),
        ], colorScheme),
        const SizedBox(height: 20),
        _buildSectionTitle('ABI Support', colorScheme, textTheme),
        const SizedBox(height: 8),
        _buildInfoCard([
          _infoRow(Icons.memory_outlined, 'Supported ABIs', detail.abisDisplay),
        ], colorScheme),
        if (detail.signatureHash != null && detail.signatureHash!.isNotEmpty) ...[
          const SizedBox(height: 20),
          _buildSectionTitle('Signature', colorScheme, textTheme),
          const SizedBox(height: 8),
          _buildInfoCard([
            _infoRow(Icons.shield_outlined, 'SHA-256', detail.signatureDisplay),
          ], colorScheme),
        ],
        if (detail.permissions.isNotEmpty) ...[
          const SizedBox(height: 20),
          _buildSectionTitle('Permissions (${detail.permissions.length})', colorScheme, textTheme),
          const SizedBox(height: 8),
          _buildPermissionsList(displayPermissions, colorScheme, textTheme),
          if (hasMore)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: TextButton.icon(
                onPressed: () => setState(() => _showAllPermissions = !_showAllPermissions),
                icon: Icon(_showAllPermissions ? Icons.expand_less : Icons.expand_more),
                label: Text(_showAllPermissions ? 'Show less' : 'Show all ${detail.permissions.length}'),
              ),
            ),
        ],
        const SizedBox(height: 80),
      ],
    );
  }

  Widget _buildHeader(ApkDetailInfo detail, ColorScheme colorScheme, TextTheme textTheme) {
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
              child: detail.iconPath != null && detail.iconPath!.isNotEmpty
                  ? ClipRRect(
                      borderRadius: BorderRadius.circular(18),
                      child: Image.file(
                        File(detail.iconPath!),
                        width: 64,
                        height: 64,
                        fit: BoxFit.cover,
                        cacheWidth: 192,
                        cacheHeight: 192,
                        filterQuality: FilterQuality.medium,
                        gaplessPlayback: true,
                        errorBuilder: (_, _, _) => Icon(Icons.android,
                            color: colorScheme.primary, size: 36),
                      ),
                    )
                  : Icon(Icons.android, color: colorScheme.primary, size: 36),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(detail.appName.isNotEmpty ? detail.appName : detail.fileName,
                      style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                      maxLines: 2, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(detail.packageName,
                        style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurfaceVariant),
                        maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title, ColorScheme colorScheme, TextTheme textTheme) {
    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: Text(title,
          style: textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.w600, color: colorScheme.onSurfaceVariant)),
    );
  }

  Widget _buildInfoCard(List<Widget> rows, ColorScheme colorScheme) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(children: rows),
      ),
    );
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Theme.of(context).colorScheme.onSurfaceVariant),
          const SizedBox(width: 10),
          SizedBox(
            width: 100,
            child: Text(label,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ),
          Expanded(
            child: Text(value,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
                textAlign: TextAlign.end),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionsList(List<ApkPermission> perms, ColorScheme colorScheme, TextTheme textTheme) {
    return Card(
      child: Column(
        children: perms.map((perm) => ListTile(
          visualDensity: VisualDensity.compact,
          dense: true,
          leading: Icon(
            perm.granted ? Icons.check_circle : Icons.remove_circle_outline,
            size: 20,
            color: perm.granted ? colorScheme.primary : colorScheme.outline,
          ),
          title: Text(perm.shortName, style: textTheme.bodySmall),
          subtitle: Text(perm.name, style: textTheme.labelSmall?.copyWith(color: colorScheme.outline)),
        )).toList(),
      ),
    );
  }
}
