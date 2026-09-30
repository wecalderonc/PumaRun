package com.pumaconcolor.run.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pumaconcolor.run.domain.SessionStatus
import com.pumaconcolor.run.service.SessionManager
import com.pumaconcolor.run.ui.active.ActiveRunScreen
import com.pumaconcolor.run.ui.setup.SetupScreen
import com.pumaconcolor.run.ui.summary.SummaryScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

object Routes {
    const val SETUP = "setup"
    const val ACTIVE = "active"
    const val SUMMARY = "summary"
}

@HiltViewModel
class NavViewModel @Inject constructor(sessionManager: SessionManager) : ViewModel() {
    val status = sessionManager.state
        .map { it.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.state.value.status)
}

/** Screens follow the session status, so reopening the app mid-run lands on the active screen. */
@Composable
fun PumaNavHost(navViewModel: NavViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val status by navViewModel.status.collectAsStateWithLifecycle()

    val route = when (status) {
        SessionStatus.Idle -> Routes.SETUP
        SessionStatus.Countdown, SessionStatus.Running, SessionStatus.Paused -> Routes.ACTIVE
        SessionStatus.Finished -> Routes.SUMMARY
    }

    NavHost(navController = navController, startDestination = route) {
        composable(Routes.SETUP) { SetupScreen() }
        composable(Routes.ACTIVE) { ActiveRunScreen() }
        composable(Routes.SUMMARY) { SummaryScreen() }
    }

    LaunchedEffect(route) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
