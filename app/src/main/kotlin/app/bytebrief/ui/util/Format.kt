package app.bytebrief.ui.util

import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {
    fun relativeTime(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val d = (nowMillis - thenMillis).coerceAtLeast(0)
        return when {
            d < TimeUnit.MINUTES.toMillis(1) -> "now"
            d < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(d)}m"
            d < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(d)}h"
            d < TimeUnit.DAYS.toMillis(30) -> "${TimeUnit.MILLISECONDS.toDays(d)}d"
            d < TimeUnit.DAYS.toMillis(365) -> "${TimeUnit.MILLISECONDS.toDays(d) / 30}mo"
            else -> "${TimeUnit.MILLISECONDS.toDays(d) / 365}y"
        }
    }

    /** "just now" or "5m ago"; [relativeTime] alone reads "now", which breaks a trailing "ago". */
    fun ago(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String =
        relativeTime(thenMillis, nowMillis).let { if (it == "now") "just now" else "$it ago" }

    fun count(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 10_000 -> String.format(Locale.US, "%.0fk", n / 1000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1000.0)
        else -> NumberFormat.getIntegerInstance(Locale.US).format(n)
    }

    /** Decimal units (1 GB = 10^9 bytes), matching what model publishers and storage settings show. */
    fun bytes(b: Long): String = when {
        b >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", b / 1e9)
        b >= 1_000_000L -> String.format(Locale.US, "%.0f MB", b / 1e6)
        else -> "$b B"
    }
}
