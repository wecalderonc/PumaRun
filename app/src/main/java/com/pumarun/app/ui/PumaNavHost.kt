package com.pumarun.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pumarun.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pumarun.app.domain.SessionStatus
import com.pumarun.app.service.SessionManager
import com.pumarun.app.ui.active.ActiveRunScreen
import com.pumarun.app.ui.history.HistoryScreen
import com.pumarun.app.ui.history.TrackDetailScreen
import com.pumarun.app.ui.permissions.PermissionGate
import com.pumarun.app.ui.setup.SetupScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val SETUP = "setup"
    const val ACTIVE = "active"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"

    fun historyDetail(id: Long) = "history/$id"
}

private val idleRoutes = setOf(Routes.SETUP, Routes.HISTORY, Routes.HISTORY_DETAIL)

@HiltViewModel
class NavViewModel @Inject constructor(private val sessionManager: SessionManager) : ViewModel() {
    val status = sessionManager.state
        .map { it.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionManager.state.value.status)
    val openRunId = sessionManager.openRunId
    fun consumeOpenRun() = sessionManager.consumeOpenRun()
}

/** Screens follow the session status, so reopening the app mid-run lands on the active screen. */
@Composable
fun PumaNavHost(navViewModel: NavViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val status by navViewModel.status.collectAsStateWithLifecycle()
    val openRunId by navViewModel.openRunId.collectAsStateWithLifecycle()
    val current by navController.currentBackStackEntryAsState()
    val route = current?.destination?.route

    LaunchedEffect(status, openRunId) {
        val currentRoute = navController.currentDestination?.route
        val savedId = openRunId
        if (savedId != null) {
            navController.navigate(Routes.historyDetail(savedId)) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
            navViewModel.consumeOpenRun()
            return@LaunchedEffect
        }
        val target = when (status) {
            SessionStatus.Idle -> if (currentRoute in idleRoutes) null else Routes.SETUP
            SessionStatus.Countdown, SessionStatus.Warmup, SessionStatus.Running, SessionStatus.Paused ->
                Routes.ACTIVE
            SessionStatus.Finished -> null
        }
        if (target != null && currentRoute != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (status == SessionStatus.Idle && route in idleRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.SETUP,
                        onClick = {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.DirectionsRun, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_new_run)) },
                    )
                    NavigationBarItem(
                        selected = route == Routes.HISTORY || route == Routes.HISTORY_DETAIL,
                        onClick = {
                            navController.navigate(Routes.HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Route, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_tracks)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SETUP,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.SETUP) {
                PermissionGate { SetupScreen() }
            }
            composable(Routes.ACTIVE) { ActiveRunScreen() }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpen = { id -> navController.navigate(Routes.historyDetail(id)) })
            }
            composable(
                Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                TrackDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNewRun = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
