import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import 'circular_reveal_clipper.dart';

/// Wrap your [MaterialApp] (or any root widget) with this controller to enable
/// a Telegram-style circular reveal theme switch.
///
/// Call [revealFrom] from a toggle button's [GestureDetector.onTapDown], passing
/// the button's global tap position (from `details.globalPosition`) and a callback
/// that flips your theme state. The old theme is snapshotted and held static while
/// the new theme grows from the tap point inside an expanding circular clip.
class ThemeRevealController extends StatefulWidget {
  final Widget Function(BuildContext context) builder;
  final Duration duration;

  const ThemeRevealController({
    super.key,
    required this.builder,
    this.duration = const Duration(milliseconds: 620),
  });

  @override
  State<ThemeRevealController> createState() => ThemeRevealControllerState();

  static ThemeRevealControllerState of(BuildContext context) {
    final state = context.findAncestorStateOfType<ThemeRevealControllerState>();
    assert(state != null, 'ThemeRevealController not found in widget tree');
    return state!;
  }
}

class ThemeRevealControllerState extends State<ThemeRevealController>
    with SingleTickerProviderStateMixin {
  final GlobalKey _repaintKey = GlobalKey();

  late final AnimationController _controller;
  late Animation<double> _radiusAnim;

  Offset _center = Offset.zero;
  double _maxRadius = 0;
  ui.Image? _oldSnapshot;
  bool _isRevealing = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration);
  }

  Future<ui.Image?> _captureScreen() async {
    try {
      final boundary =
          _repaintKey.currentContext?.findRenderObject()
              as RenderRepaintBoundary?;
      if (boundary == null) return null;
      final pixelRatio = MediaQuery.of(context).devicePixelRatio;
      return await boundary.toImage(pixelRatio: pixelRatio);
    } catch (_) {
      return null;
    }
  }

  /// Trigger the circular reveal transition.
  ///
  /// [tapPosition] is the global [Offset] of the tap (the circle's center).
  /// [switchThemeState] flips the actual theme state (e.g.
  /// `ref.read(themeProvider.notifier).toggle()` or a `setState` callback).
  Future<void> revealFrom(
    Offset tapPosition,
    VoidCallback switchThemeState,
  ) async {
    if (_isRevealing) return;
    final size = MediaQuery.of(context).size;

    // Farthest corner distance from tap point -> full coverage radius.
    final corners = [
      Offset.zero,
      Offset(size.width, 0),
      Offset(0, size.height),
      Offset(size.width, size.height),
    ];
    final maxDist = corners
        .map((c) => (c - tapPosition).distance)
        .reduce(math.max);

    // Snapshot the current (old) theme BEFORE switching.
    final snapshot = await _captureScreen();
    if (snapshot == null) {
      // Fallback: no animation if we can't capture.
      switchThemeState();
      return;
    }

    setState(() {
      _center = tapPosition;
      _maxRadius = maxDist;
      _oldSnapshot = snapshot;
      _isRevealing = true;
    });

    // Flip the actual theme state now (new theme paints underneath the clip).
    switchThemeState();

    _radiusAnim = Tween<double>(begin: 0, end: _maxRadius).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutExpo),
    );

    _controller.reset();
    await _controller.forward();

    if (mounted) {
      setState(() {
        _isRevealing = false;
        _oldSnapshot = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final newThemeLayer = RepaintBoundary(
      key: _repaintKey,
      child: widget.builder(context),
    );

    if (!_isRevealing || _oldSnapshot == null) return newThemeLayer;

    return Stack(
      fit: StackFit.expand,
      children: [
        // Static old-theme snapshot underneath.
        SizedBox.expand(
          child: RawImage(
            image: _oldSnapshot,
            fit: BoxFit.fill,
            filterQuality: FilterQuality.medium,
          ),
        ),
        // New theme, clipped to a growing circle.
        AnimatedBuilder(
          animation: _radiusAnim,
          builder: (context, child) {
            return ClipPath(
              clipper: CircularRevealClipper(
                center: _center,
                radius: _radiusAnim.value,
              ),
              child: child,
            );
          },
          child: newThemeLayer,
        ),
      ],
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _oldSnapshot?.dispose();
    super.dispose();
  }
}
