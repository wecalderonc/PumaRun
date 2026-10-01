package com.pumarun.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pumarun.app.domain.CoachStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class DistanceUnit { Kilometers, Meters }
enum class TargetMode { None, Pace, Speed, Time }
enum class AnnounceOption(val meters: Int?) { None(null), M500(500), K1(1000), K2(2000), Custom(null) }

/** What the user last entered on the setup screen, kept as raw text so it restores verbatim. */
data class SetupPreferences(
    val goalText: String = "5",
    val goalUnit: DistanceUnit = DistanceUnit.Kilometers,
    val targetMode: TargetMode = TargetMode.None,
    val targetPaceText: String = "",
    val targetSpeedText: String = "",
    val targetTimeText: String = "",
    val announceOption: AnnounceOption = AnnounceOption.K1,
    val customIntervalText: String = "",
    val paceAlertsEnabled: Boolean = true,
    val backOnPaceEnabled: Boolean = true,
    val autoStopAtGoal: Boolean = false,
    val coachStyle: CoachStyle = CoachStyle.Medium,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val GOAL_TEXT = stringPreferencesKey("goal_text")
        val GOAL_UNIT = stringPreferencesKey("goal_unit")
        val TARGET_MODE = stringPreferencesKey("target_mode")
        val TARGET_PACE = stringPreferencesKey("target_pace")
        val TARGET_SPEED = stringPreferencesKey("target_speed")
        val TARGET_TIME = stringPreferencesKey("target_time")
        val COACH = stringPreferencesKey("coach")
        val ANNOUNCE = stringPreferencesKey("announce")
        val CUSTOM_INTERVAL = stringPreferencesKey("custom_interval")
        val PACE_ALERTS = booleanPreferencesKey("pace_alerts")
        val BACK_ON_PACE = booleanPreferencesKey("back_on_pace")
        val AUTO_STOP = booleanPreferencesKey("auto_stop")
        val VERSION = intPreferencesKey("version")
    }

    val preferences: Flow<SetupPreferences> = context.dataStore.data.map { p ->
        val d = SetupPreferences()
        SetupPreferences(
            goalText = p[Keys.GOAL_TEXT] ?: d.goalText,
            goalUnit = p[Keys.GOAL_UNIT].toEnum(d.goalUnit),
            targetMode = p[Keys.TARGET_MODE].toEnum(d.targetMode),
            targetPaceText = p[Keys.TARGET_PACE] ?: d.targetPaceText,
            targetSpeedText = p[Keys.TARGET_SPEED] ?: d.targetSpeedText,
            targetTimeText = p[Keys.TARGET_TIME] ?: d.targetTimeText,
            coachStyle = p[Keys.COACH].toEnum(d.coachStyle),
            announceOption = p[Keys.ANNOUNCE].toEnum(d.announceOption),
            customIntervalText = p[Keys.CUSTOM_INTERVAL] ?: d.customIntervalText,
            paceAlertsEnabled = p[Keys.PACE_ALERTS] ?: d.paceAlertsEnabled,
            backOnPaceEnabled = p[Keys.BACK_ON_PACE] ?: d.backOnPaceEnabled,
            autoStopAtGoal = p[Keys.AUTO_STOP] ?: d.autoStopAtGoal,
        )
    }

    suspend fun load(): SetupPreferences = preferences.first()

    suspend fun save(prefs: SetupPreferences) {
        context.dataStore.edit { p ->
            p[Keys.VERSION] = 1
            p[Keys.GOAL_TEXT] = prefs.goalText
            p[Keys.GOAL_UNIT] = prefs.goalUnit.name
            p[Keys.TARGET_MODE] = prefs.targetMode.name
            p[Keys.TARGET_PACE] = prefs.targetPaceText
            p[Keys.TARGET_SPEED] = prefs.targetSpeedText
            p[Keys.TARGET_TIME] = prefs.targetTimeText
            p[Keys.COACH] = prefs.coachStyle.name
            p[Keys.ANNOUNCE] = prefs.announceOption.name
            p[Keys.CUSTOM_INTERVAL] = prefs.customIntervalText
            p[Keys.PACE_ALERTS] = prefs.paceAlertsEnabled
            p[Keys.BACK_ON_PACE] = prefs.backOnPaceEnabled
            p[Keys.AUTO_STOP] = prefs.autoStopAtGoal
        }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
