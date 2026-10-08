package com.apkorganizer.services

import java.util.ArrayDeque

enum class LogLevel { INFO, WARNING, ERROR }

class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val operation: String,
    val message: String,
    val filePath: String? = null,
) {
    val formattedTime: String
        get() {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            return "%02d:%02d:%02d".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND),
            )
        }

    val levelLabel: String
        get() = when (level) {
            LogLevel.INFO -> "INFO"
            LogLevel.WARNING -> "WARN"
            LogLevel.ERROR -> "ERROR"
        }

    override fun toString(): String =
        "[$formattedTime] [$levelLabel] [$operation] $message" +
            (filePath?.let { " ($it)" } ?: "")
}

/**
 * In-memory ring-buffer logger (max 500 entries) with listener support.
 * Direct port of the Dart `LoggerService` singleton.
 */
object LoggerService {

    private const val MAX_ENTRIES = 500

    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    val allEntries: List<LogEntry>
        get() = synchronized(lock) { entries.toList() }

    fun addListener(listener: (LogEntry) -> Unit) {
        synchronized(lock) { listeners.add(listener) }
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        synchronized(lock) { listeners.remove(listener) }
    }

    fun info(operation: String, message: String, filePath: String? = null) {
        add(LogLevel.INFO, operation, message, filePath)
    }

    fun warning(operation: String, message: String, filePath: String? = null) {
        add(LogLevel.WARNING, operation, message, filePath)
    }

    fun error(operation: String, message: String, filePath: String? = null) {
        add(LogLevel.ERROR, operation, message, filePath)
    }

    private fun add(level: LogLevel, operation: String, message: String, filePath: String?) {
        val entry = LogEntry(
            level = level,
            operation = operation,
            message = message,
            filePath = filePath,
        )
        val toNotify: List<(LogEntry) -> Unit>
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            toNotify = listeners.toList()
        }
        for (listener in toNotify) listener(entry)
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
