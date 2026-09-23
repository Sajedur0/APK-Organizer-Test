import 'dart:async';

/// Hands out work item indexes to a bounded pool of concurrent workers.
///
/// Compared to `queue.removeAt(0)` (which shifts the whole list every time and
/// makes batch operations O(n²)), this is a single integer cursor — the same
/// pattern the services use for rename / move / delete batches.
class ParallelWorkQueue {
  final int total;
  int _cursor = 0;

  ParallelWorkQueue(this.total);

  int get completed => _cursor;

  /// Next index, or `null` once every item has been handed out.
  int? next() {
    if (_cursor >= total) return null;
    final value = _cursor;
    _cursor++;
    return value;
  }

  bool get isDone => _cursor >= total;
}

/// Runs [task] for indexes `0 .. total - 1` with at most [concurrency] tasks in
/// flight, stopping early when [isCancelled] returns true.
///
/// Dart executes synchronous code atomically between awaits, so the shared
/// cursor needs no locking. In-flight tasks are always awaited before this
/// future completes, which keeps callers from observing half-finished writes.
Future<void> runParallel({
  required int total,
  required int concurrency,
  required Future<void> Function(int index) task,
  bool Function()? isCancelled,
}) async {
  if (total <= 0) return;

  final queue = ParallelWorkQueue(total);
  final workers = concurrency < 1
      ? 1
      : (concurrency > total ? total : concurrency);

  Future<void> worker() async {
    while (true) {
      if (isCancelled?.call() ?? false) return;
      final index = queue.next();
      if (index == null) return;
      await task(index);
    }
  }

  await Future.wait(List<Future<void>>.generate(workers, (_) => worker()));
}
