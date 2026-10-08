package com.apkorganizer.utils

import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Shared formatting helpers.
 *
 * Keeping this logic in one place means the APK list, the detail pages and
 * the summary dialogs can never disagree about how a size is displayed.
 */
object FormatUtil {

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    /**
     * Human readable byte size, e.g. `4.2 MB`.
     *
     * Uses binary steps (1024) with one decimal for KB/MB and two for GB+ —
     * the exact format the app has always shown.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        if (bytes < 1024L * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    /** Short relative description of a timestamp (`Today`, `3d ago`, or a date). */
    fun formatAge(millisSinceEpoch: Long, now: Long = System.currentTimeMillis()): String {
        if (millisSinceEpoch <= 0L) return "Unknown"
        val date = Date(millisSinceEpoch)
        val reference = Date(now)
        val days = (now - millisSinceEpoch) / (24L * 60 * 60 * 1000)
        if (days <= 0L) return "Today"
        if (days == 1L) return "Yesterday"
        if (days < 30L) return "${days}d ago"
        val monthLabel = MONTHS[date.month.coerceIn(0, 11)]
        val label = "$monthLabel ${date.date}"
        return if (date.year == reference.year) label else "$label, ${date.year + 1900}"
    }

    /** Directory part of a path (without trailing separator). */
    fun parentPath(path: String): String {
        val index = path.lastIndexOf('/')
        if (index < 0) return path
        if (index == 0) return "/"
        return path.substring(0, index)
    }

    /** File name part of a path. */
    fun fileName(path: String): String {
        val index = path.lastIndexOf('/')
        return if (index >= 0) path.substring(index + 1) else path
    }

    /** Formats a [Date] as `Jan 5, 2026` (same manual layout as the app). */
    fun formatDate(date: Date): String {
        val month = MONTHS[date.month.coerceIn(0, 11)]
        return "$month ${date.date}, ${date.year + 1900}"
    }

    /** `MMM d, yyyy` helper backed by [SimpleDateFormat] for other callers. */
    fun formatShortDate(millis: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
}
