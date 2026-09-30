package com.pumaconcolor.run.voice

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.pumaconcolor.run.R
import com.pumaconcolor.run.domain.Announcement
import com.pumaconcolor.run.domain.CoachCue
import com.pumaconcolor.run.domain.CoachStyle
import com.pumaconcolor.run.domain.Units
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Turns [Announcement]s into spoken sentences in the TTS locale. */
@Singleton
class Phrases @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var cachedLocale: Locale? = null
    private var cachedResources: Resources? = null

    private fun res(locale: Locale): Resources {
        if (locale != cachedLocale || cachedResources == null) {
            val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
            cachedResources = context.createConfigurationContext(config).resources
            cachedLocale = locale
        }
        return cachedResources!!
    }

    fun text(announcement: Announcement, locale: Locale): String {
        val r = res(locale)
        return when (announcement) {
            is Announcement.Countdown -> announcement.seconds.toString()
            Announcement.Started -> r.getString(R.string.tts_go)
            Announcement.Paused -> r.getString(R.string.tts_paused)
            Announcement.Resumed -> r.getString(R.string.tts_resumed)
            is Announcement.Milestone -> {
                val dist = distance(r, locale, announcement.distanceMeters)
                val time = duration(r, announcement.elapsedMs)
                val base = announcement.avgPaceSecPerKm?.let {
                    r.getString(R.string.tts_milestone, dist, time, pace(r, it))
                } ?: r.getString(R.string.tts_milestone_no_pace, dist, time)
                announcement.scheduleDeltaSec?.let { "$base ${schedule(r, it)}" } ?: base
            }
            is Announcement.Halfway -> r.getString(
                R.string.tts_halfway,
                distance(r, locale, announcement.distanceMeters),
                duration(r, announcement.elapsedMs),
            )
            is Announcement.GoalReached -> {
                val dist = distance(r, locale, announcement.goalMeters)
                val time = duration(r, announcement.elapsedMs)
                val base = announcement.avgPaceSecPerKm?.let {
                    r.getString(R.string.tts_goal_pace, dist, time, pace(r, it))
                } ?: r.getString(R.string.tts_goal, dist, time)
                announcement.scheduleDeltaSec?.let { delta ->
                    val seconds = abs(delta).roundToInt()
                    val verdict = when {
                        seconds < ON_TARGET_SECONDS -> r.getString(R.string.tts_goal_exact)
                        delta < 0 -> r.getString(R.string.tts_goal_beat, duration(r, seconds * 1000L))
                        else -> r.getString(R.string.tts_goal_missed, duration(r, seconds * 1000L))
                    }
                    "$base $verdict"
                } ?: base
            }
            is Announcement.Coach -> coachLine(r, announcement.cue, announcement.style)
            is Announcement.BelowPace -> r.getString(
                R.string.tts_below_pace,
                pace(r, announcement.currentPaceSecPerKm),
                pace(r, announcement.targetPaceSecPerKm),
            )
            Announcement.BackOnPace -> r.getString(R.string.tts_back_on_pace)
            is Announcement.Finished -> r.getString(
                R.string.tts_finished,
                distance(r, locale, announcement.distanceMeters),
                duration(r, announcement.elapsedMs),
            )
        }
    }

    fun testPhrase(locale: Locale): String = res(locale).getString(R.string.tts_test)

    /** A sample push so the user can hear what the chosen trainer sounds like. */
    fun coachSample(style: CoachStyle, locale: Locale): String? =
        if (style == CoachStyle.Off) null else coachLine(res(locale), CoachCue.Push, style)

    private fun schedule(r: Resources, deltaSec: Double): String {
        val seconds = abs(deltaSec).roundToInt()
        return when {
            seconds < ON_TARGET_SECONDS -> r.getString(R.string.tts_on_target)
            deltaSec < 0 -> r.getString(R.string.tts_ahead, duration(r, seconds * 1000L))
            else -> r.getString(R.string.tts_behind, duration(r, seconds * 1000L))
        }
    }

    private val lastPick = mutableMapOf<Int, Int>()

    private fun coachLine(r: Resources, cue: CoachCue, style: CoachStyle): String {
        val arrayId = coachArray(cue, style) ?: return r.getString(R.string.tts_back_on_pace)
        val options = r.getStringArray(arrayId)
        // Never repeat the previous line from the same pool back to back.
        val previous = lastPick[arrayId]
        val candidates = options.indices.filter { it != previous || options.size == 1 }
        val index = candidates[Random.nextInt(candidates.size)]
        lastPick[arrayId] = index
        return options[index]
    }

    private fun coachArray(cue: CoachCue, style: CoachStyle): Int? = when (style) {
        CoachStyle.Off -> null
        CoachStyle.Soft -> when (cue) {
            CoachCue.Push -> R.array.coach_soft_push
            CoachCue.Encourage -> R.array.coach_soft_encourage
            CoachCue.BackOnPace -> R.array.coach_soft_back
            CoachCue.AlmostThere -> R.array.coach_soft_almost
            CoachCue.FinalMeters -> R.array.coach_soft_final
        }
        CoachStyle.Medium -> when (cue) {
            CoachCue.Push -> R.array.coach_medium_push
            CoachCue.Encourage -> R.array.coach_medium_encourage
            CoachCue.BackOnPace -> R.array.coach_medium_back
            CoachCue.AlmostThere -> R.array.coach_medium_almost
            CoachCue.FinalMeters -> R.array.coach_medium_final
        }
        CoachStyle.Hard -> when (cue) {
            CoachCue.Push -> R.array.coach_hard_push
            CoachCue.Encourage -> R.array.coach_hard_encourage
            CoachCue.BackOnPace -> R.array.coach_hard_back
            CoachCue.AlmostThere -> R.array.coach_hard_almost
            CoachCue.FinalMeters -> R.array.coach_hard_final
        }
        CoachStyle.Annoying -> when (cue) {
            CoachCue.Push -> R.array.coach_annoying_push
            CoachCue.Encourage -> R.array.coach_annoying_encourage
            CoachCue.BackOnPace -> R.array.coach_annoying_back
            CoachCue.AlmostThere -> R.array.coach_annoying_almost
            CoachCue.FinalMeters -> R.array.coach_annoying_final
        }
    }

    private fun distance(r: Resources, locale: Locale, meters: Double): String {
        return if (meters < 1000) {
            val m = meters.roundToInt()
            r.getQuantityString(R.plurals.tts_meters, m, m.toString())
        } else {
            val km = meters / 1000.0
            val format = NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = 2
                minimumFractionDigits = 0
            }
            val isOne = abs(km - 1.0) < 0.005
            r.getQuantityString(R.plurals.tts_kilometers, if (isOne) 1 else 2, format.format(km))
        }
    }

    private fun duration(r: Resources, ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildList {
            if (h > 0) add(r.getQuantityString(R.plurals.tts_hours, h, h))
            if (m > 0) add(r.getQuantityString(R.plurals.tts_minutes, m, m))
            if (s > 0 || (h == 0 && m == 0)) add(r.getQuantityString(R.plurals.tts_seconds, s, s))
        }.joinToString(" ")
    }

    private fun pace(r: Resources, secPerKm: Double): String {
        val (m, s) = Units.minutesSeconds(secPerKm)
        val parts = buildList {
            if (m > 0) add(r.getQuantityString(R.plurals.tts_minutes, m, m))
            if (s > 0 || m == 0) add(r.getQuantityString(R.plurals.tts_seconds, s, s))
        }.joinToString(" ")
        return r.getString(R.string.tts_pace, parts)
    }
}

private const val ON_TARGET_SECONDS = 3
