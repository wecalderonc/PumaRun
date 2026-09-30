package com.pumaconcolor.run.domain

/**
 * Trainer personality. Besides the tone of the phrases (see Phrases), each style sets how quickly
 * and how often the coach reacts: harder styles push sooner and repeat more.
 */
enum class CoachStyle(
    /** How long the runner must stay below target before the first push. */
    val paceSustainMs: Long,
    /** Minimum time between pushes while still below target. */
    val paceRepeatMs: Long,
    /** Interval for praise/encouragement while on pace; null disables it. */
    val encourageEveryMs: Long?,
    /** Whether to cheer near the finish ("almost there", last 200 m). */
    val cheersFinish: Boolean,
) {
    Off(paceSustainMs = 15_000, paceRepeatMs = 60_000, encourageEveryMs = null, cheersFinish = false),
    Soft(paceSustainMs = 15_000, paceRepeatMs = 90_000, encourageEveryMs = 240_000, cheersFinish = true),
    Medium(paceSustainMs = 15_000, paceRepeatMs = 45_000, encourageEveryMs = 150_000, cheersFinish = true),
    Hard(paceSustainMs = 10_000, paceRepeatMs = 25_000, encourageEveryMs = 180_000, cheersFinish = true),
    Annoying(paceSustainMs = 8_000, paceRepeatMs = 12_000, encourageEveryMs = 60_000, cheersFinish = true),
}

enum class CoachCue { Push, Encourage, BackOnPace, AlmostThere, FinalMeters }
