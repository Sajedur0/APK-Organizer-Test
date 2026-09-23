import 'dart:io';

import 'package:flutter/material.dart';

import '../app_theme.dart';
import '../models/apk_file.dart';

/// Icon edge in physical pixels used when decoding the cached APK icon.
///
/// The tile draws a 52dp box; decoding the PNG at ~3x that size (instead of its
/// full resolution) cuts decode time and memory substantially while staying
/// crisp on high density screens.
const int _kIconDecodeSize = 160;

class ApkListTile extends StatelessWidget {
  final ApkFile apk;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onInstall;
  final VoidCallback onDelete;
  final VoidCallback onAutoRename;
  final VoidCallback onManualRename;
  final VoidCallback onMove;
  final VoidCallback? onDetails;
  final VoidCallback? onShare;

  const ApkListTile({
    super.key,
    required this.apk,
    required this.isSelected,
    required this.onTap,
    required this.onLongPress,
    required this.onInstall,
    required this.onDelete,
    required this.onAutoRename,
    required this.onManualRename,
    required this.onMove,
    this.onDetails,
    this.onShare,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final textTheme = theme.textTheme;
    final iconPath = apk.iconPath;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      color: isSelected ? colorScheme.primaryContainer.withAlpha(100) : null,
      shape: RoundedRectangleBorder(
        borderRadius: AppRadius.cardBorder,
        side: BorderSide(
          color: isSelected
              ? colorScheme.primary.withAlpha(140)
              : colorScheme.outlineVariant.withAlpha(120),
        ),
      ),
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        borderRadius: AppRadius.cardBorder,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(AppRadius.card),
                    ),
                    child: iconPath != null && iconPath.isNotEmpty
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(AppRadius.card),
                            child: Image.file(
                              File(iconPath),
                              width: 52,
                              height: 52,
                              fit: BoxFit.cover,
                              cacheWidth: _kIconDecodeSize,
                              cacheHeight: _kIconDecodeSize,
                              filterQuality: FilterQuality.medium,
                              gaplessPlayback: true,
                              errorBuilder: (context, error, stackTrace) =>
                                  Icon(
                                    Icons.android,
                                    color: colorScheme.primary,
                                    size: 28,
                                  ),
                            ),
                          )
                        : Icon(
                            Icons.android,
                            color: colorScheme.primary,
                            size: 28,
                          ),
                  ),
                  if (isSelected)
                    Positioned(
                      right: -4,
                      top: -4,
                      child: Container(
                        width: 22,
                        height: 22,
                        decoration: BoxDecoration(
                          color: colorScheme.primary,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: colorScheme.surface,
                            width: 2,
                          ),
                        ),
                        child: const Icon(
                          Icons.check,
                          size: 12,
                          color: Colors.white,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      apk.displayName,
                      style: textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: colorScheme.primaryContainer.withAlpha(150),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            'v${apk.versionName}',
                            style: textTheme.labelSmall?.copyWith(
                              color: colorScheme.onPrimaryContainer,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            apk.formattedSize,
                            style: textTheme.bodySmall?.copyWith(
                              color: colorScheme.onSurfaceVariant,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      apk.fileName,
                      style: textTheme.bodySmall?.copyWith(
                        color: colorScheme.outline,
                        fontSize: 11,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              PopupMenuButton<String>(
                tooltip: 'More actions',
                onSelected: (value) {
                  switch (value) {
                    case 'details':
                      onDetails?.call();
                    case 'install':
                      onInstall();
                    case 'auto_rename':
                      onAutoRename();
                    case 'manual_rename':
                      onManualRename();
                    case 'move':
                      onMove();
                    case 'delete':
                      onDelete();
                    case 'share':
                      onShare?.call();
                  }
                },
                itemBuilder: (context) => [
                  if (onDetails != null)
                    const PopupMenuItem(
                      value: 'details',
                      child: ListTile(
                        leading: Icon(Icons.info_outline),
                        title: Text('Details'),
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                      ),
                    ),
                  const PopupMenuItem(
                    value: 'install',
                    child: ListTile(
                      leading: Icon(Icons.install_mobile),
                      title: Text('Install'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'auto_rename',
                    child: ListTile(
                      leading: Icon(Icons.auto_fix_high),
                      title: Text('Auto Rename'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'manual_rename',
                    child: ListTile(
                      leading: Icon(Icons.drive_file_rename_outline),
                      title: Text('Rename'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'move',
                    child: ListTile(
                      leading: Icon(Icons.drive_file_move),
                      title: Text('Move'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  if (onShare != null)
                    const PopupMenuItem(
                      value: 'share',
                      child: ListTile(
                        leading: Icon(Icons.share),
                        title: Text('Share'),
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                      ),
                    ),
                  PopupMenuItem(
                    value: 'delete',
                    child: ListTile(
                      leading: Icon(
                        Icons.delete_outline,
                        color: colorScheme.error,
                      ),
                      title: Text(
                        'Delete',
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
