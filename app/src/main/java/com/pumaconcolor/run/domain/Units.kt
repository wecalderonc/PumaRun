package com.pumaconcolor.run.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Units {

    fun speedKmhToPaceSecPerKm(kmh: Double): Double? =
        if (kmh > 0.0) 3600.0 / kmh else null

    fun paceSecPerKmToSpeedKmh(secPerKm: Double): Double? =
        if (secPerKm > 0.0) 3600.0 / secPerKm else null

    /** Accepts "5:45", "5.45" or "5" (minutes) and returns seconds per km. */
    fun parsePace(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(':', '.', ',')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.takeIf { it > 0 }?.let { it * 60.0 }
            2 -> {
                val min = parts[0].toIntOrNull() ?: return null
                val sec = parts[1].toIntOrNull() ?: return null
                if (min < 0 || sec !in 0..59) return null
                val total = min * 60 + sec
                if (total > 0) total.toDouble() else null
            }
            else -> null
        }
    }

    /** Accepts "30" (minutes), "29:30" (min:sec) or "1:45:00" (h:min:sec); returns seconds. */
    fun parseDuration(text: String): Double? {
        val parts = text.trim().split(':')
        if (parts.any { it.isEmpty() }) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it < 0 }) return null
        val total = when (nums.size) {
            1 -> nums[0] * 60
            2 -> if (nums[1] < 60) nums[0] * 60 + nums[1] else return null
            3 -> if (nums[1] < 60 && nums[2] < 60) nums[0] * 3600 + nums[1] * 60 + nums[2] else return null
            else -> return null
        }
        return total.toDouble().takeIf { it > 0 }
    }

    /** Editable time text without a leading zero: "6:30", "29:00", "1:45:00". */
    fun formatClock(totalSeconds: Double): String {
        val total = totalSeconds.roundToLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** Signed schedule difference, e.g. "+0:12" (behind) or "-1:05" (ahead). */
    fun formatDelta(deltaSec: Double): String {
        val total = abs(deltaSec).roundToLong()
        val sign = if (deltaSec < 0) "-" else "+"
        return if (total >= 3600) String.format(Locale.US, "%s%d:%02d:%02d", sign, total / 3600, (total % 3600) / 60, total % 60)
        else String.format(Locale.US, "%s%d:%02d", sign, total / 60, total % 60)
    }

    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun formatPace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite() || secPerKm <= 0.0 ||
            secPerKm >= MAX_DISPLAY_PACE_SEC
        ) return "--:--"
        val total = secPerKm.roundToLong()
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    fun formatKm(meters: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.2f", meters / 1000.0)

    fun formatSpeedKmh(kmh: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", kmh)

    /** Splits seconds into (minutes, seconds) after rounding to the nearest second. */
    fun minutesSeconds(totalSeconds: Double): Pair<Int, Int> {
        val rounded = totalSeconds.roundToInt()
        return rounded / 60 to rounded % 60
    }

    private const val MAX_DISPLAY_PACE_SEC = 60 * 60.0
}
