package com.pumarun.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class UnitsTest {

    @Test
    fun speedPaceConversionsRoundTrip() {
        assertEquals(360.0, Units.speedKmhToPaceSecPerKm(10.0)!!, 1e-9)
        assertEquals(12.0, Units.paceSecPerKmToSpeedKmh(300.0)!!, 1e-9)
        assertNull(Units.speedKmhToPaceSecPerKm(0.0))
    }

    @Test
    fun parsePace() {
        assertEquals(345.0, Units.parsePace("5:45")!!, 0.0)
        assertEquals(345.0, Units.parsePace("5.45")!!, 0.0)
        assertEquals(300.0, Units.parsePace("5")!!, 0.0)
        assertNull(Units.parsePace("5:75"))
        assertNull(Units.parsePace("abc"))
        assertNull(Units.parsePace(""))
        assertNull(Units.parsePace("0:00"))
    }

    @Test
    fun parseDuration() {
        assertEquals(1_800.0, Units.parseDuration("30")!!, 0.0)
        assertEquals(1_770.0, Units.parseDuration("29:30")!!, 0.0)
        assertEquals(6_300.0, Units.parseDuration("1:45:00")!!, 0.0)
        assertNull(Units.parseDuration("29:75"))
        assertNull(Units.parseDuration("abc"))
        assertNull(Units.parseDuration("0:00"))
        assertNull(Units.parseDuration("30:"))
    }

    @Test
    fun formatClock() {
        assertEquals("6:30", Units.formatClock(390.0))
        assertEquals("29:00", Units.formatClock(1_740.0))
        assertEquals("1:45:00", Units.formatClock(6_300.0))
    }

    @Test
    fun formatDelta() {
        assertEquals("+0:12", Units.formatDelta(12.2))
        assertEquals("-1:05", Units.formatDelta(-65.0))
        assertEquals("+1:00:00", Units.formatDelta(3_600.0))
    }

    @Test
    fun parseDecimalAcceptsComma() {
        assertEquals(10.5, Units.parseDecimal("10,5")!!, 0.0)
        assertEquals(21.1, Units.parseDecimal(" 21.1 ")!!, 0.0)
    }

    @Test
    fun formatDuration() {
        assertEquals("00:00", Units.formatDuration(0))
        assertEquals("05:07", Units.formatDuration(307_400))
        assertEquals("1:02:03", Units.formatDuration(3_723_000))
    }

    @Test
    fun formatPace() {
        assertEquals("5:45", Units.formatPace(345.0))
        assertEquals("6:00", Units.formatPace(359.6))
        assertEquals("--:--", Units.formatPace(null))
        assertEquals("--:--", Units.formatPace(Double.POSITIVE_INFINITY))
    }

    @Test
    fun formatKmUsesLocale() {
        assertEquals("2.50", Units.formatKm(2_500.0, Locale.US))
        assertEquals("2,50", Units.formatKm(2_500.0, Locale("es", "ES")))
    }
}
