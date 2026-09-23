import 'package:flutter/material.dart';

import 'theme_reveal_overlay.dart';

/// A reusable sun/moon toggle button that triggers a circular reveal theme
/// switch from its own tap position.
class ThemeSwitchButton extends StatelessWidget {
  final bool isDarkMode;
  final VoidCallback onThemeChanged;
  final double iconSize;

  const ThemeSwitchButton({
    super.key,
    required this.isDarkMode,
    required this.onThemeChanged,
    this.iconSize = 24,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (details) {
        ThemeRevealController.of(context).revealFrom(
          details.globalPosition,
          onThemeChanged,
        );
      },
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Icon(
          isDarkMode ? Icons.dark_mode : Icons.light_mode,
          size: iconSize,
        ),
      ),
    );
  }
}
