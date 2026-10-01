package com.pumarun.app.ui.active

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pumarun.app.R
import com.pumarun.app.domain.SessionState
import com.pumarun.app.domain.SessionStatus
import com.pumarun.app.domain.Units
import com.pumarun.app.ui.map.RunMap
import com.pumarun.app.ui.theme.OffPaceRed
import com.pumarun.app.ui.theme.OnPaceGreen
import kotlinx.coroutines.launch

@Composable
fun ActiveRunScreen(viewModel: ActiveRunViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        if (state.status == SessionStatus.Countdown) {
            RunMap(state.position, state.track, Modifier.fillMaxSize())
            Countdown(state.countdownSeconds, onCancel = viewModel::stop)
        } else {
            RunDashboard(
                state = state,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onStop = viewModel::stop,
            )
        }
    }
}

@Composable
private fun Countdown(seconds: Int, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.get_ready), style = MaterialTheme.typography.headlineMedium)
        Text(
            seconds.toString(),
            fontSize = 160.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))
        HoldToStopButton(onStop = onCancel)
    }
}

@Composable
private fun RunDashboard(state: SessionState, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    val config = state.config
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            RunMap(position = state.position, track = state.track, modifier = Modifier.fillMaxSize())
            GpsIndicator(state.gpsAccuracyMeters, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (state.status == SessionStatus.Paused) {
                        Text(stringResource(R.string.paused), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else if (state.goalReached) {
                        Text(stringResource(R.string.goal_reached_badge), color = OnPaceGreen, fontWeight = FontWeight.Bold)
                    }
                    Text(Units.formatKm(state.distanceMeters), fontSize = 40.sp, fontWeight = FontWeight.Black)
                    config?.let {
                        Text(
                            stringResource(R.string.of_goal, Units.formatKm(it.goalMeters)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (state.goalReached) OnPaceGreen else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Units.formatDuration(state.elapsedMs), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            state.scheduleDeltaSec?.takeIf { !state.goalReached }?.let { delta ->
                val behind = delta > 0
                Text(
                    stringResource(
                        if (behind) R.string.schedule_behind else R.string.schedule_ahead,
                        Units.formatDelta(delta).drop(1),
                    ),
                    color = if (behind) OffPaceRed else OnPaceGreen,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val target = config?.targetPaceSecPerKm
            val paceColor = when {
                target == null || state.currentPaceSecPerKm == null -> MaterialTheme.colorScheme.onSurface
                state.isBelowTarget -> OffPaceRed
                else -> OnPaceGreen
            }
            Stat(stringResource(R.string.current_pace), Units.formatPace(state.currentPaceSecPerKm), paceColor)
            Stat(stringResource(R.string.avg_pace), Units.formatPace(state.avgPaceSecPerKm))
            if (target != null) Stat(stringResource(R.string.target), Units.formatPace(target))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val paused = state.status == SessionStatus.Paused
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = if (paused) onResume else onPause,
                    modifier = Modifier.size(88.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(if (paused) R.string.resume else R.string.pause), style = MaterialTheme.typography.labelMedium)
            }
            HoldToStopButton(onStop = onStop)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.per_km), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GpsIndicator(accuracy: Float?, modifier: Modifier = Modifier) {
    val (label, color) = when {
        accuracy == null -> stringResource(R.string.gps_searching) to MaterialTheme.colorScheme.onSurfaceVariant
        accuracy <= 10f -> stringResource(R.string.gps_good) to OnPaceGreen
        accuracy <= 20f -> stringResource(R.string.gps_fair) to Color(0xFFFFC107)
        else -> stringResource(R.string.gps_poor) to OffPaceRed
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
    }
}

@Composable
private fun HoldToStopButton(onStop: () -> Unit) {
    val currentOnStop by rememberUpdatedState(onStop)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            currentOnStop()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(200)) }
                        }
                    })
                },
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(88.dp),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.error,
                trackColor = Color.Transparent,
            )
            Icon(
                Icons.Filled.Stop,
                contentDescription = stringResource(R.string.hold_to_stop),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.hold_to_stop), style = MaterialTheme.typography.labelMedium)
    }
}

private const val HOLD_MS = 1_500
