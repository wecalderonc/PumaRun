package com.pumarun.app.ui.active

import androidx.lifecycle.ViewModel
import com.pumarun.app.domain.SessionState
import com.pumarun.app.service.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ActiveRunViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {
    val state: StateFlow<SessionState> = sessionManager.state

    fun pause() = sessionManager.pause()
    fun resume() = sessionManager.resume()
    fun stop() = sessionManager.stop()
}
