package com.pumarun.app.ui.setup

import com.pumarun.app.data.DistanceUnit
import com.pumarun.app.data.SetupPreferences
import com.pumarun.app.data.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupParsingTest {

    @Test
    fun goalInKmAndMeters() {
        assertEquals(21_100.0, SetupViewModel.parseGoalMeters(SetupPreferences(goalText = "21.1"))!!, 1e-6)
        assertEquals(
            800.0,
            SetupViewModel.parseGoalMeters(SetupPreferences(goalText = "800", goalUnit = DistanceUnit.Meters))!!,
            0.0,
        )
        assertNull(SetupViewModel.parseGoalMeters(SetupPreferences(goalText = "0")))
        assertNull(SetupViewModel.parseGoalMeters(SetupPreferences(goalText = "x")))
    }

    @Test
    fun targetFromPaceOrSpeed() {
        assertNull(SetupViewModel.parseTarget(SetupPreferences(targetMode = TargetMode.None, targetPaceText = "5:00")))
        assertEquals(
            330.0,
            SetupViewModel.parseTarget(SetupPreferences(targetMode = TargetMode.Pace, targetPaceText = "5:30"))!!,
            0.0,
        )
        assertEquals(
            360.0,
            SetupViewModel.parseTarget(SetupPreferences(targetMode = TargetMode.Speed, targetSpeedText = "10"))!!,
            1e-9,
        )
        assertNull(SetupViewModel.parseTarget(SetupPreferences(targetMode = TargetMode.Speed, targetSpeedText = "90")))
    }

    @Test
    fun targetTimeIsTurnedIntoPace() {
        val today = SetupPreferences(goalText = "5", targetMode = TargetMode.Time, targetTimeText = "30:00")
        assertEquals(360.0, SetupViewModel.parseTarget(today)!!, 1e-9)
        val tomorrow = today.copy(targetTimeText = "29:00")
        assertEquals(348.0, SetupViewModel.parseTarget(tomorrow)!!, 1e-9)
        val half = SetupPreferences(goalText = "21.1", targetMode = TargetMode.Time, targetTimeText = "1:45:00")
        assertEquals(298.6, SetupViewModel.parseTarget(half)!!, 0.1)
        assertNull(SetupViewModel.parseTarget(today.copy(targetTimeText = "2:00")))
    }
}
