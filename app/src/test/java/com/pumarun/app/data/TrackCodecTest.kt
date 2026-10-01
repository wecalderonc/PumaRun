package com.pumarun.app.data

import com.pumarun.app.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackCodecTest {
    @Test
    fun roundTripKeepsSegmentsAndSigns() {
        val track = listOf(
            listOf(LatLon(19.432608, -99.133209), LatLon(19.44, -99.14)),
            listOf(LatLon(19.45, -99.15)),
        )
        val decoded = TrackCodec.decode(TrackCodec.encode(track))
        assertEquals(2, decoded.size)
        assertEquals(2, decoded[0].size)
        assertEquals(19.432608, decoded[0][0].latitude, 1e-6)
        assertEquals(-99.133209, decoded[0][0].longitude, 1e-6)
        assertEquals(1, decoded[1].size)
    }

    @Test
    fun blankDecodesToEmpty() {
        assertTrue(TrackCodec.decode("").isEmpty())
        assertTrue(TrackCodec.decode("   ").isEmpty())
    }
}