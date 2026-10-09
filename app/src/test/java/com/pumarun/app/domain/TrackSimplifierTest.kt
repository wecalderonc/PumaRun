package com.pumarun.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSimplifierTest {

    @Test
    fun keepsShortRoutesIntact() {
        val track = listOf(List(10) { LatLon(it.toDouble(), 0.0) })
        assertEquals(track, TrackSimplifier.simplify(track, maxPoints = 20))
    }

    @Test
    fun capsLongRoutesAndKeepsTheEnds() {
        val segment = List(1_000) { LatLon(it.toDouble(), 1.0) }
        val simplified = TrackSimplifier.simplify(listOf(segment), maxPoints = 100).single()
        assertTrue(simplified.size <= 101)
        assertEquals(segment.first(), simplified.first())
        assertEquals(segment.last(), simplified.last())
    }
}
