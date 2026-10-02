package com.pumarun.app.ui.setup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pumarun.app.R
import com.pumarun.app.data.AnnounceOption
import com.pumarun.app.data.DistanceUnit
import com.pumarun.app.data.TargetMode
import com.pumarun.app.domain.CoachStyle
import com.pumarun.app.domain.SessionConfig
import com.pumarun.app.domain.Units
import com.pumarun.app.location.LocationSettingsChecker
import com.pumarun.app.location.LocationSettingsResult
import com.pumarun.app.service.RunTrackingService
import com.pumarun.app.ui.map.RunMap
import com.pumarun.app.ui.privacy.openPrivacyPolicy
import com.pumarun.app.voice.VoiceCoach
import kotlinx.coroutines.launch

private val quickGoals = listOf("1", "3", "5", "10", "21.1")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locationOffMessage = stringResource(R.string.location_off)
    var pendingConfig by remember { mutableStateOf<SessionConfig?>(null) }

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val config = pendingConfig
        pendingConfig = null
        if (result.resultCode == Activity.RESULT_OK && config != null) {
            RunTrackingService.start(context, config)
        } else {
            scope.launch { snackbar.showSnackbar(locationOffMessage) }
        }
    }

    fun onStart() {
        val config = viewModel.prepareStart() ?: return
        scope.launch {
            when (val check = LocationSettingsChecker.check(context)) {
                LocationSettingsResult.Satisfied,
                LocationSettingsResult.Unavailable -> RunTrackingService.start(context, config)
                is LocationSettingsResult.Resolvable -> {
                    pendingConfig = config
                    resolutionLauncher.launch(IntentSenderRequest.Builder(check.resolution).build())
                }
            }
        }
    }

    val p = ui.prefs

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title), fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (voiceStatus == VoiceCoach.Status.MissingData || voiceStatus == VoiceCoach.Status.Unavailable) {
                VoiceBanner(missingData = voiceStatus == VoiceCoach.Status.MissingData) {
                    try {
                        context.startActivity(
                            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }

            Section(stringResource(R.string.map_where)) {
                RunMap(
                    position = position,
                    track = emptyList(),
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                )
            }

            Section(stringResource(R.string.goal_label)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = p.goalText,
                        onValueChange = { v -> viewModel.edit { copy(goalText = v) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = ui.goalError,
                        supportingText = if (ui.goalError) ({ Text(stringResource(R.string.error_goal)) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.width(140.dp)) {
                        DistanceUnit.entries.forEachIndexed { i, unit ->
                            SegmentedButton(
                                selected = p.goalUnit == unit,
                                onClick = { viewModel.edit { copy(goalUnit = unit) } },
                                shape = SegmentedButtonDefaults.itemShape(i, DistanceUnit.entries.size),
                            ) {
                                Text(stringResource(if (unit == DistanceUnit.Kilometers) R.string.unit_km else R.string.unit_m))
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickGoals.forEach { km ->
                        FilterChip(
                            selected = p.goalUnit == DistanceUnit.Kilometers && p.goalText == km,
                            onClick = { viewModel.edit { copy(goalText = km, goalUnit = DistanceUnit.Kilometers) } },
                            label = { Text("${km}K") },
                        )
                    }
                }
            }

            Section(stringResource(R.string.target_label)) {
                val modes = TargetMode.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = p.targetMode == mode,
                            onClick = { viewModel.edit { copy(targetMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) {
                            Text(
                                stringResource(
                                    when (mode) {
                                        TargetMode.None -> R.string.target_none
                                        TargetMode.Pace -> R.string.target_pace
                                        TargetMode.Speed -> R.string.target_speed
                                        TargetMode.Time -> R.string.target_time
                                    }
                                ),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                when (p.targetMode) {
                    TargetMode.None -> Unit
                    TargetMode.Pace -> {
                        val equivalent = SetupViewModel.parseTarget(p)
                            ?.let(Units::paceSecPerKmToSpeedKmh)
                            ?.let { stringResource(R.string.target_equivalent_speed, Units.formatSpeedKmh(it)) }
                        TargetField(
                            value = p.targetPaceText,
                            label = stringResource(R.string.target_pace_hint),
                            keyboardType = KeyboardType.Text,
                            isError = ui.targetError,
                            helper = equivalent,
                            onChange = { v -> viewModel.edit { copy(targetPaceText = v) } },
                        )
                    }
                    TargetMode.Speed -> {
                        val equivalent = SetupViewModel.parseTarget(p)
                            ?.let { stringResource(R.string.target_equivalent_pace, Units.formatPace(it)) }
                        TargetField(
                            value = p.targetSpeedText,
                            label = stringResource(R.string.target_speed_hint),
                            keyboardType = KeyboardType.Decimal,
                            isError = ui.targetError,
                            helper = equivalent,
                            onChange = { v -> viewModel.edit { copy(targetSpeedText = v) } },
                        )
                    }
                    TargetMode.Time -> {
                        val derived = SetupViewModel.parseTarget(p)?.let { pace ->
                            stringResource(
                                R.string.target_time_derived,
                                Units.formatPace(pace),
                                Units.formatSpeedKmh(Units.paceSecPerKmToSpeedKmh(pace) ?: 0.0),
                            )
                        }
                        TargetField(
                            value = p.targetTimeText,
                            label = stringResource(R.string.target_time_hint),
                            keyboardType = KeyboardType.Text,
                            isError = ui.targetError,
                            helper = derived,
                            onChange = { v -> viewModel.edit { copy(targetTimeText = v) } },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(-30, -15, 15, 30).forEach { delta ->
                                OutlinedButton(
                                    onClick = { viewModel.adjustTargetTime(delta) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    Text(if (delta < 0) "−${-delta}s" else "+${delta}s")
                                }
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.announce_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnnounceOption.entries.forEach { option ->
                        FilterChip(
                            selected = p.announceOption == option,
                            onClick = { viewModel.edit { copy(announceOption = option) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            AnnounceOption.None -> R.string.announce_none
                                            AnnounceOption.M500 -> R.string.announce_500
                                            AnnounceOption.K1 -> R.string.announce_1k
                                            AnnounceOption.K2 -> R.string.announce_2k
                                            AnnounceOption.Custom -> R.string.announce_custom
                                        }
                                    )
                                )
                            },
                        )
                    }
                }
                if (p.announceOption == AnnounceOption.Custom) {
                    OutlinedTextField(
                        value = p.customIntervalText,
                        onValueChange = { v -> viewModel.edit { copy(customIntervalText = v) } },
                        label = { Text(stringResource(R.string.custom_interval_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = ui.intervalError,
                        supportingText = if (ui.intervalError) ({ Text(stringResource(R.string.error_interval)) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Section(stringResource(R.string.alerts_label)) {
                val hasTarget = p.targetMode != TargetMode.None
                SwitchRow(
                    text = stringResource(R.string.pace_alerts),
                    checked = p.paceAlertsEnabled,
                    enabled = hasTarget,
                    onChange = { v -> viewModel.edit { copy(paceAlertsEnabled = v) } },
                )
                SwitchRow(
                    text = stringResource(R.string.back_on_pace),
                    checked = p.backOnPaceEnabled,
                    enabled = hasTarget && p.paceAlertsEnabled,
                    onChange = { v -> viewModel.edit { copy(backOnPaceEnabled = v) } },
                )
                SwitchRow(
                    text = stringResource(R.string.auto_stop),
                    checked = p.autoStopAtGoal,
                    enabled = true,
                    onChange = { v -> viewModel.edit { copy(autoStopAtGoal = v) } },
                )
            }

            Section(stringResource(R.string.coach_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoachStyle.entries.forEach { style ->
                        FilterChip(
                            selected = p.coachStyle == style,
                            onClick = { viewModel.edit { copy(coachStyle = style) } },
                            label = { Text(stringResource(style.labelRes())) },
                        )
                    }
                }
                Text(
                    stringResource(p.coachStyle.descriptionRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = viewModel::testVoice, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.test_voice))
            }

            Button(
                onClick = ::onStart,
                enabled = ui.loaded,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(stringResource(R.string.start), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { context.openPrivacyPolicy() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_policy_button))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun CoachStyle.labelRes() = when (this) {
    CoachStyle.Off -> R.string.coach_off
    CoachStyle.Soft -> R.string.coach_soft
    CoachStyle.Medium -> R.string.coach_medium
    CoachStyle.Hard -> R.string.coach_hard
    CoachStyle.Annoying -> R.string.coach_annoying
}

private fun CoachStyle.descriptionRes() = when (this) {
    CoachStyle.Off -> R.string.coach_off_desc
    CoachStyle.Soft -> R.string.coach_soft_desc
    CoachStyle.Medium -> R.string.coach_medium_desc
    CoachStyle.Hard -> R.string.coach_hard_desc
    CoachStyle.Annoying -> R.string.coach_annoying_desc
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun TargetField(
    value: String,
    label: String,
    keyboardType: KeyboardType,
    isError: Boolean,
    helper: String?,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        supportingText = when {
            isError -> ({ Text(stringResource(R.string.error_target)) })
            helper != null -> ({ Text(helper) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun VoiceBanner(missingData: Boolean, onInstall: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (missingData) R.string.voice_missing else R.string.voice_unavailable),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (missingData) {
                TextButton(onClick = onInstall) { Text(stringResource(R.string.install_voice)) }
            }
        }
    }
}
