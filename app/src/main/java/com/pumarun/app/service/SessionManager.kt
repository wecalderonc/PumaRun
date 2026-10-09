package com.pumarun.app.service

import android.os.SystemClock
import com.pumarun.app.data.SessionRepository
import com.pumarun.app.domain.Announcement
import com.pumarun.app.domain.AnnouncementScheduler
import com.pumarun.app.domain.SessionConfig
import com.pumarun.app.domain.SessionEngine
import com.pumarun.app.domain.SessionState
import com.pumarun.app.domain.SessionStatus
import com.pumarun.app.location.LocationSource
import com.pumarun.app.voice.VoiceCoach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single active run. Shared by [RunTrackingService] (which keeps the process alive)
 * and the UI (which only observes [state] and forwards user actions).
 */
@Singleton
class SessionManager @Inject constructor(
    private val locationSource: LocationSource,
    private val voice: VoiceCoach,
    private val repository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _openRunId = MutableStateFlow<Long?>(null)
    val openRunId: StateFlow<Long?> = _openRunId.asStateFlow()

    private var engine: SessionEngine? = null
    private var scheduler: AnnouncementScheduler? = null
    private var countdownJob: Job? = null
    private var locationJob: Job? = null
    private var tickerJob: Job? = null
    private var warmupHalfwaySpoken = false

    fun consumeOpenRun() {
        _openRunId.value = null
    }

    fun start(config: SessionConfig) {
        if (_state.value.isActive) return
        voice.ensureInitialized()
        warmupHalfwaySpoken = false
        engine = SessionEngine(config, clock = SystemClock::elapsedRealtime)
        scheduler = AnnouncementScheduler(config)
        _state.value = SessionState(status = SessionStatus.Countdown, config = config, countdownSeconds = COUNTDOWN_SECONDS)

        // Start GPS during the countdown so the first fix is ready when the run begins.
        locationJob = scope.launch {
            locationSource.locations()
                .catch { /* Location stream failed; the UI shows GPS as searching. */ }
                .collect { point ->
                    engine?.onLocation(point)
                    publish()
                }
        }

        countdownJob = scope.launch {
            for (s in COUNTDOWN_SECONDS downTo 1) {
                _state.value = _state.value.copy(countdownSeconds = s)
                voice.announce(Announcement.Countdown(s))
                delay(1_000)
            }
            beginRunning()
        }
    }

    private fun beginRunning() {
        val e = engine ?: return
        val warmupMeters = _state.value.config?.warmupMeters ?: 0.0
        e.start()
        val warming = warmupMeters > 0
        _state.value = _state.value.copy(
            status = if (warming) SessionStatus.Warmup else SessionStatus.Running,
            countdownSeconds = 0,
            warmingUp = warming,
        )
        if (warming) voice.announce(Announcement.WarmupStarted(warmupMeters))
        else voice.announce(Announcement.Started)
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                e.tick()
                publish()
            }
        }
    }

    fun pause() {
        val status = _state.value.status
        if (status != SessionStatus.Running && status != SessionStatus.Warmup) return
        engine?.pause()
        _state.value = _state.value.copy(status = SessionStatus.Paused)
        publish()
        voice.announce(Announcement.Paused)
    }

    fun resume() {
        if (_state.value.status != SessionStatus.Paused) return
        engine?.resume()
        val status = if (engine?.isWarmingUp == true) SessionStatus.Warmup else SessionStatus.Running
        _state.value = _state.value.copy(status = status, warmingUp = status == SessionStatus.Warmup)
        publish()
        voice.announce(Announcement.Resumed)
    }

    fun stop() {
        val current = _state.value
        if (!current.isActive) return
        countdownJob?.cancel()
        tickerJob?.cancel()
        locationJob?.cancel()

        if (current.status == SessionStatus.Countdown || engine?.isWarmingUp == true) {
            voice.stop()
            clear()
            _state.value = SessionState()
            return
        }

        val e = engine
        e?.pause()
        val final = (e?.snapshot(current) ?: current).copy(
            status = SessionStatus.Finished,
            warmingUp = false,
            paceAlertCount = scheduler?.paceAlertCount ?: current.paceAlertCount,
        )
        _state.value = final
        voice.announce(Announcement.Finished(final.distanceMeters, final.elapsedMs))
        clear()
        scope.launch {
            val id = repository.save(final)
            if (id != null) _openRunId.value = id
            _state.value = SessionState()
        }
    }

    /** Returns to Idle after the summary has been shown. */
    fun reset() {
        if (_state.value.isActive) return
        _state.value = SessionState()
    }

    private fun clear() {
        engine = null
        scheduler = null
        countdownJob = null
        tickerJob = null
        locationJob = null
    }

    private fun publish() {
        val e = engine ?: return
        val base = _state.value
        var next = e.snapshot(base)
        if (base.status == SessionStatus.Warmup && !next.warmingUp) {
            next = next.copy(status = SessionStatus.Running)
            voice.announce(Announcement.Started)
        }
        val config = next.config
        if (next.status == SessionStatus.Warmup && config != null && !warmupHalfwaySpoken &&
            config.warmupMeters >= 200 && next.distanceMeters >= config.warmupMeters / 2.0
        ) {
            warmupHalfwaySpoken = true
            voice.announce(Announcement.WarmupHalfway)
        }
        if (next.status == SessionStatus.Running) {
            scheduler?.let { s ->
                val announcements = s.update(next)
                next = next.copy(paceAlertCount = s.paceAlertCount)
                announcements.forEach(voice::announce)
            }
        }
        _state.value = next

        if (next.status == SessionStatus.Running && next.goalReached && next.config?.autoStopAtGoal == true) {
            stop()
        }
    }

    companion object {
        const val COUNTDOWN_SECONDS = 3
        private const val TICK_MS = 1_000L
    }
}
