package com.apkorganizer.utils

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * A simple cooperative cancellation flag for long-running organize tasks.
 * (Direct port of the Dart `CancellationToken`.)
 */
class CancellationToken {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    val isCancelled: Boolean
        get() = cancelled
}

/**
 * Runs [task] for indexes `0 .. total - 1` with at most [concurrency] tasks in
 * flight, stopping early when [isCancelled] returns true.
 *
 * Port of the Dart `runParallel`: workers pull indexes from a shared cursor
 * (dynamic load balancing) so one slow item can't block otherwise-idle
 * workers. Callers are expected to invoke this from a single-threaded context
 * (e.g. the Main dispatcher) so shared state inside [task] needs no locking.
 */
suspend fun runParallel(
    total: Int,
    concurrency: Int,
    isCancelled: (() -> Boolean)? = null,
    task: suspend (index: Int) -> Unit,
) = coroutineScope {
    if (total <= 0) return@coroutineScope
    val cursor = AtomicInteger(0)
    val workers = concurrency.coerceIn(1, total)
    repeat(workers) {
        launch {
            while (true) {
                if (isCancelled?.invoke() == true) return@launch
                val index = cursor.getAndIncrement()
                if (index >= total) return@launch
                task(index)
            }
        }
    }
}
