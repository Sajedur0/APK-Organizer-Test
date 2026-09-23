import 'dart:collection';

enum LogLevel { info, warning, error }

class LogEntry {
  final DateTime timestamp;
  final LogLevel level;
  final String operation;
  final String message;
  final String? filePath;

  const LogEntry({
    required this.timestamp,
    required this.level,
    required this.operation,
    required this.message,
    this.filePath,
  });

  String get formattedTime {
    final h = timestamp.hour.toString().padLeft(2, '0');
    final m = timestamp.minute.toString().padLeft(2, '0');
    final s = timestamp.second.toString().padLeft(2, '0');
    return '$h:$m:$s';
  }

  String get levelLabel {
    switch (level) {
      case LogLevel.info:
        return 'INFO';
      case LogLevel.warning:
        return 'WARN';
      case LogLevel.error:
        return 'ERROR';
    }
  }

  @override
  String toString() => '[$formattedTime] [$levelLabel] [$operation] $message'
      '${filePath != null ? " ($filePath)" : ""}';
}

class LoggerService {
  static final LoggerService instance = LoggerService._();
  LoggerService._();

  final Queue<LogEntry> _entries = Queue();
  final List<void Function(LogEntry)> _listeners = [];
  static const int _maxEntries = 500;

  List<LogEntry> get entries => List.unmodifiable(_entries);

  void addListener(void Function(LogEntry) listener) {
    _listeners.add(listener);
  }

  void removeListener(void Function(LogEntry) listener) {
    _listeners.remove(listener);
  }

  void info(String operation, String message, {String? filePath}) {
    _add(LogLevel.info, operation, message, filePath: filePath);
  }

  void warning(String operation, String message, {String? filePath}) {
    _add(LogLevel.warning, operation, message, filePath: filePath);
  }

  void error(String operation, String message, {String? filePath}) {
    _add(LogLevel.error, operation, message, filePath: filePath);
  }

  void _add(LogLevel level, String operation, String message,
      {String? filePath}) {
    final entry = LogEntry(
      timestamp: DateTime.now(),
      level: level,
      operation: operation,
      message: message,
      filePath: filePath,
    );
    _entries.addLast(entry);
    while (_entries.length > _maxEntries) {
      _entries.removeFirst();
    }
    for (final listener in _listeners) {
      listener(entry);
    }
  }

  void clear() {
    _entries.clear();
  }
}
