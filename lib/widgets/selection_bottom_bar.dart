import 'package:flutter/material.dart';

import 'compact_action_chip.dart';

class SelectionBottomBar extends StatelessWidget {
  final int selectedCount;
  final int totalCount;
  final VoidCallback onSelectAll;
  final VoidCallback onClearSelection;
  final VoidCallback onInstall;
  final VoidCallback onDelete;
  final VoidCallback onAutoRename;
  final VoidCallback onMove;
  final VoidCallback onShare;

  const SelectionBottomBar({
    super.key,
    required this.selectedCount,
    required this.totalCount,
    required this.onSelectAll,
    required this.onClearSelection,
    required this.onInstall,
    required this.onDelete,
    required this.onAutoRename,
    required this.onMove,
    required this.onShare,
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
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      '$selectedCount selected',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        fontWeight: FontWeight.w600,
                        color: colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
                  const SizedBox(width: 4),
                  IconButton(
                    onPressed: onClearSelection,
                    icon: const Icon(Icons.close),
                    tooltip: 'Clear selection',
                    style: IconButton.styleFrom(
                      backgroundColor: colorScheme.surfaceContainerHighest,
                    ),
                  ),
                ],
              ),

              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  CompactActionChip(
                    icon: Icons.install_mobile,
                    label: 'Install',
                    color: colorScheme.primary,
                    onTap: onInstall,
                  ),
                  CompactActionChip(
                    icon: Icons.auto_fix_high,
                    label: 'Auto Rename',
                    color: colorScheme.tertiary,
                    onTap: onAutoRename,
                  ),
                  CompactActionChip(
                    icon: Icons.drive_file_move,
                    label: 'Move',
                    color: Colors.deepOrange,
                    onTap: onMove,
                  ),
                  CompactActionChip(
                    icon: Icons.share,
                    label: 'Share',
                    color: colorScheme.secondary,
                    onTap: onShare,
                  ),
                  CompactActionChip(
                    icon: Icons.delete_outline,
                    label: 'Delete',
                    color: colorScheme.error,
                    onTap: onDelete,
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
