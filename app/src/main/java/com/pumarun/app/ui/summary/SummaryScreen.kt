package com.pumarun.app.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pumarun.app.R
import com.pumarun.app.domain.SessionState
import com.pumarun.app.domain.Units
import com.pumarun.app.service.SessionManager
import com.pumarun.app.ui.map.MapCamera
import com.pumarun.app.ui.map.RunMap
import com.pumarun.app.ui.theme.OffPaceRed
import com.pumarun.app.ui.theme.OnPaceGreen
import androidx.compose.ui.text.style.TextAlign
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {
    val state: StateFlow<SessionState> = sessionManager.state
    fun newRun() = sessionManager.reset()
}

@Composable
fun SummaryScreen(viewModel: SummaryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.position != null || state.track.any { it.isNotEmpty() }) {
            RunMap(
                position = state.position,
                track = state.track,
                camera = MapCamera.FitRoute,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
            )
        }
        Icon(
            if (state.goalReached) Icons.Filled.EmojiEvents else Icons.Filled.Flag,
            contentDescription = null,
            tint = if (state.goalReached) OnPaceGreen else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        )
        Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(if (state.goalReached) R.string.goal_reached else R.string.goal_not_reached),
            color = if (state.goalReached) OnPaceGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryRow(stringResource(R.string.total_distance), "${Units.formatKm(state.distanceMeters)} km")
                HorizontalDivider()
                SummaryRow(stringResource(R.string.total_time), Units.formatDuration(state.elapsedMs))
                HorizontalDivider()
                SummaryRow(
                    stringResource(R.string.average_pace),
                    "${Units.formatPace(state.avgPaceSecPerKm)} ${stringResource(R.string.per_km)}",
                )
                val targetTime = state.config?.targetTimeSec
                if (state.config?.targetPaceSecPerKm != null && targetTime != null) {
                    HorizontalDivider()
                    SummaryRow(
                        stringResource(R.string.target),
                        "${Units.formatPace(state.config?.targetPaceSecPerKm)} ${stringResource(R.string.per_km)}",
                    )
                    HorizontalDivider()
                    SummaryRow(stringResource(R.string.target_time_label), Units.formatDuration((targetTime * 1000).toLong()))
                    state.goalTimeMs?.let { goalMs ->
                        HorizontalDivider()
                        SummaryRow(stringResource(R.string.goal_time_label), Units.formatDuration(goalMs))
                        val delta = goalMs / 1000.0 - targetTime
                        val magnitude = Units.formatDelta(delta).drop(1)
                        Text(
                            stringResource(if (delta <= 0) R.string.beat_target else R.string.missed_target, magnitude),
                            color = if (delta <= 0) OnPaceGreen else OffPaceRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    }
                    HorizontalDivider()
                    SummaryRow(stringResource(R.string.pace_alerts_count), state.paceAlertCount.toString())
                }
            }
        }

        Button(onClick = viewModel::newRun, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.new_run), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}
