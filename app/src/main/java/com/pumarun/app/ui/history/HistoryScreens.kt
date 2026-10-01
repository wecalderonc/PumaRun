package com.pumarun.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pumarun.app.R
import com.pumarun.app.data.SavedRun
import com.pumarun.app.data.SessionRepository
import com.pumarun.app.domain.Units
import com.pumarun.app.ui.map.MapCamera
import com.pumarun.app.ui.map.RunMap
import com.pumarun.app.ui.theme.OnPaceGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(repository: SessionRepository) : ViewModel() {
    val runs = repository.observeRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tracks_title), fontWeight = FontWeight.Bold) }) }) { padding ->
        if (runs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.tracks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(runs, key = { it.id }) { run ->
                    RunCard(run, onClick = { onOpen(run.id) })
                }
            }
        }
    }
}

@Composable
private fun RunCard(run: SavedRun, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatWhen(run.finishedAtEpochMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${Units.formatKm(run.distanceMeters)} km", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "${Units.formatDuration(run.elapsedMs)} · ${Units.formatPace(run.avgPaceSecPerKm)} ${stringResource(R.string.per_km)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            run.goalMeters?.let {
                Text(
                    stringResource(if (run.goalReached) R.string.goal_reached else R.string.goal_not_reached) +
                        " · ${Units.formatKm(it)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (run.goalReached) OnPaceGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val repository: SessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")

    private val _run = MutableStateFlow<SavedRun?>(null)
    val run = _run.asStateFlow()

    init {
        viewModelScope.launch { id?.let { _run.value = repository.get(it) } }
    }

    fun delete(onDone: () -> Unit) {
        val runId = id ?: return
        viewModelScope.launch {
            repository.delete(runId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(onBack: () -> Unit, viewModel: TrackDetailViewModel = hiltViewModel()) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val saved = run

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(saved?.let { formatWhen(it.finishedAtEpochMs) } ?: stringResource(R.string.tracks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (saved != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tracks_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (saved == null) {
            Text(
                stringResource(R.string.tracks_missing),
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val end = saved.track.lastOrNull()?.lastOrNull()
                if (end != null) {
                    RunMap(
                        position = end,
                        track = saved.track,
                        camera = MapCamera.FitRoute,
                        modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)),
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryLine(stringResource(R.string.total_distance), "${Units.formatKm(saved.distanceMeters)} km")
                        HorizontalDivider()
                        SummaryLine(stringResource(R.string.total_time), Units.formatDuration(saved.elapsedMs))
                        HorizontalDivider()
                        SummaryLine(
                            stringResource(R.string.average_pace),
                            "${Units.formatPace(saved.avgPaceSecPerKm)} ${stringResource(R.string.per_km)}",
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tracks_delete)) },
            text = { Text(stringResource(R.string.tracks_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) { Text(stringResource(R.string.tracks_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun formatWhen(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
