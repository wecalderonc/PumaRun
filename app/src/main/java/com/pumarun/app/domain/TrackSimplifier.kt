package com.pumarun.app.domain

/** Keeps a route drawable. A long run can store a point every few meters. */
object TrackSimplifier {
    const val MAX_POINTS = 600

    fun simplify(track: List<List<LatLon>>, maxPoints: Int = MAX_POINTS): List<List<LatLon>> {
        val total = track.sumOf { it.size }
        if (total <= maxPoints) return track
        val stride = ((total + maxPoints - 1) / maxPoints).coerceAtLeast(2)
        return track.map { segment ->
            if (segment.size <= 2) segment
            else buildList {
                segment.forEachIndexed { index, point ->
                    if (index == 0 || index == segment.lastIndex || index % stride == 0) add(point)
                }
            }
        }
    }
}
