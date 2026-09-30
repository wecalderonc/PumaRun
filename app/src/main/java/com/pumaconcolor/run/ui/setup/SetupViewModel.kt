package com.pumaconcolor.run.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaconcolor.run.data.AnnounceOption
import com.pumaconcolor.run.data.DistanceUnit
import com.pumaconcolor.run.data.SettingsRepository
import com.pumaconcolor.run.data.SetupPreferences
import com.pumaconcolor.run.data.TargetMode
import com.pumaconcolor.run.domain.SessionConfig
import com.pumaconcolor.run.domain.Units
import com.pumaconcolor.run.voice.VoiceCoach
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val prefs: SetupPreferences = SetupPreferences(),
    val loaded: Boolean = false,
    val goalError: Boolean = false,
    val targetError: Boolean = false,
    val intervalError: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val voiceCoach: VoiceCoach,
) : ViewModel() {

    private val _ui = MutableStateFlow(SetupUiState())
    val ui: StateFlow<SetupUiState> = _ui.asStateFlow()

    val voiceStatus: StateFlow<VoiceCoach.Status> = voiceCoach.status

    init {
        voiceCoach.ensureInitialized()
        viewModelScope.launch {
            val saved = settings.load()
            _ui.update { it.copy(prefs = saved, loaded = true) }
        }
    }

    fun edit(transform: SetupPreferences.() -> SetupPreferences) {
        _ui.update { it.copy(prefs = it.prefs.transform(), goalError = false, targetError = false, intervalError = false) }
    }

    fun testVoice() = voiceCoach.speakTest(_ui.value.prefs.coachStyle)

    /** Nudges the target finish time, e.g. -30 s to aim a little faster than last time. */
    fun adjustTargetTime(deltaSec: Int) = edit {
        val current = Units.parseDuration(targetTimeText) ?: return@edit this
        val next = (current + deltaSec).coerceAtLeast(60.0)
        copy(targetTimeText = Units.formatClock(next))
    }

    /** Validates input, persists it and returns the config to run, or null if invalid. */
    fun prepareStart(): SessionConfig? {
        val p = _ui.value.prefs
        val goalMeters = parseGoalMeters(p)
        val target = parseTarget(p)
        val interval = parseInterval(p)

        val targetInvalid = p.targetMode != TargetMode.None && target == null
        val intervalInvalid = p.announceOption == AnnounceOption.Custom && interval == null
        _ui.update {
            it.copy(goalError = goalMeters == null, targetError = targetInvalid, intervalError = intervalInvalid)
        }
        if (goalMeters == null || targetInvalid || intervalInvalid) return null

        viewModelScope.launch { settings.save(p) }
        return SessionConfig(
            goalMeters = goalMeters,
            targetPaceSecPerKm = target,
            announceIntervalMeters = if (p.announceOption == AnnounceOption.Custom) interval else p.announceOption.meters,
            paceAlertsEnabled = p.paceAlertsEnabled,
            backOnPaceEnabled = p.backOnPaceEnabled,
            autoStopAtGoal = p.autoStopAtGoal,
            coachStyle = p.coachStyle,
        )
    }

    companion object {
        private const val MAX_GOAL_METERS = 1_000_000.0
        private const val MIN_GOAL_METERS = 50.0

        fun parseGoalMeters(p: SetupPreferences): Double? {
            val value = Units.parseDecimal(p.goalText) ?: return null
            val meters = if (p.goalUnit == DistanceUnit.Kilometers) value * 1000 else value
            return meters.takeIf { it in MIN_GOAL_METERS..MAX_GOAL_METERS }
        }

        fun parseTarget(p: SetupPreferences): Double? = when (p.targetMode) {
            TargetMode.None -> null
            TargetMode.Pace -> Units.parsePace(p.targetPaceText)?.takeIf { it in 120.0..1800.0 }
            TargetMode.Speed -> Units.parseDecimal(p.targetSpeedText)
                ?.takeIf { it in 2.0..40.0 }
                ?.let(Units::speedKmhToPaceSecPerKm)
            TargetMode.Time -> {
                val seconds = Units.parseDuration(p.targetTimeText)
                val meters = parseGoalMeters(p)
                if (seconds == null || meters == null) null
                else (seconds / (meters / 1000.0)).takeIf { it in 120.0..1800.0 }
            }
        }

        fun parseInterval(p: SetupPreferences): Int? =
            p.customIntervalText.trim().toIntOrNull()?.takeIf { it in 50..100_000 }
    }
}
