package com.pumarun.app.data

import com.pumarun.app.domain.LatLon
import java.util.Locale

/** Compact text form of a route so Room can store it in one column. */
object TrackCodec {
    fun encode(track: List<List<LatLon>>): String =
        track.joinToString(SEGMENT) { segment ->
            segment.joinToString(POINT) { p ->
                String.format(Locale.US, "%.6f%s%.6f", p.latitude, PAIR, p.longitude)
            }
        }

    fun decode(raw: String): List<List<LatLon>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SEGMENT).map { segment ->
            segment.split(POINT).mapNotNull { pair ->
                val bits = pair.split(PAIR)
                if (bits.size != 2) return@mapNotNull null
                val lat = bits[0].toDoubleOrNull() ?: return@mapNotNull null
                val lon = bits[1].toDoubleOrNull() ?: return@mapNotNull null
                LatLon(lat, lon)
            }
        }.filter { it.isNotEmpty() }
    }

    private const val SEGMENT = "|"
    private const val POINT = ";"
    private const val PAIR = ","
}
