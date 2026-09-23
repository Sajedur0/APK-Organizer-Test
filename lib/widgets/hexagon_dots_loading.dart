import 'dart:math' as math;

import 'package:flutter/material.dart';

class HexagonDotsLoading extends StatefulWidget {
  final Color? color;
  final Duration wavePeriod;
  final double minRadius;

  const HexagonDotsLoading({
    super.key,
    this.color,
    this.wavePeriod = const Duration(milliseconds: 1200),
    this.minRadius = 12.0,
  }) : assert(minRadius > 0, 'minRadius must be > 0');

  @override
  State<HexagonDotsLoading> createState() => _HexagonDotsLoadingState();
}

class _HexagonDotsLoadingState extends State<HexagonDotsLoading>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: widget.wavePeriod)
      ..repeat();
  }

  @override
  void didUpdateWidget(covariant HexagonDotsLoading oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.wavePeriod != widget.wavePeriod) {
      _ctrl.duration = widget.wavePeriod;
      if (!_ctrl.isAnimating) _ctrl.repeat();
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.color ?? Theme.of(context).colorScheme.primary;
    final dimension = widget.minRadius * 5;

    return Center(
      child: SizedBox.square(
        dimension: dimension,
        child: AnimatedBuilder(
          animation: _ctrl,
          builder: (context, _) {
            return CustomPaint(
              painter: _HexagonDotsPainter(color: color, progress: _ctrl.value),
            );
          },
        ),
      ),
    );
  }
}

class _HexagonDotsPainter extends CustomPainter {
  static const int _outerDotCount = 6;
  static const double _twoPi = math.pi * 2;

  final Color color;
  final double progress;

  const _HexagonDotsPainter({required this.color, required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final orbitRadius = size.shortestSide * 0.32;
    final minDotRadius = size.shortestSide * 0.075;
    final maxDotRadius = size.shortestSide * 0.13;
    final paint = Paint()..style = PaintingStyle.fill;

    for (int i = 0; i < _outerDotCount; i++) {
      final angle = -math.pi / 2 + i * _twoPi / _outerDotCount;
      final offset = Offset(
        math.cos(angle) * orbitRadius,
        math.sin(angle) * orbitRadius,
      );
      final pulse = _pulseFor(i, _outerDotCount);
      final radius = minDotRadius + (maxDotRadius - minDotRadius) * pulse;
      final alpha = (100 + 155 * pulse).round().clamp(0, 255);

      paint.color = color.withAlpha(alpha);
      canvas.drawCircle(center + offset, radius, paint);
    }

    final centerPulse = _pulseFor(_outerDotCount, _outerDotCount + 1);
    paint.color = color.withAlpha((120 + 135 * centerPulse).round());
    canvas.drawCircle(
      center,
      minDotRadius + (maxDotRadius - minDotRadius) * centerPulse,
      paint,
    );
  }

  double _pulseFor(int index, int total) {
    final shifted = (progress - index / total) % 1.0;
    return (math.cos((shifted * _twoPi) - math.pi) + 1) / 2;
  }

  @override
  bool shouldRepaint(covariant _HexagonDotsPainter oldDelegate) {
    return oldDelegate.color != color || oldDelegate.progress != progress;
  }
}
