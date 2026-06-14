package com.nkhearn25.toiltracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nkhearn25.toiltracker.ToilTrackerViewModel
import com.nkhearn25.toiltracker.ui.screens.*
import java.time.LocalDate

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Calendar : Screen("calendar", "Calendar", Icons.Default.DateRange)
    object LogHours : Screen("log_hours", "Log", Icons.Default.Add)
    object History : Screen("history", "History", Icons.AutoMirrored.Filled.List)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun ToilTrackerApp(viewModel: ToilTrackerViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val items = listOf(
        Screen.Dashboard,
        Screen.Calendar,
        Screen.LogHours,
        Screen.History
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                uiState.metrics?.let { metrics ->
                    DashboardScreen(
                        metrics = metrics,
                        onSetupClick = { navController.navigate(Screen.Settings.route) }
                    )
                }
            }
            composable(Screen.Calendar.route) {
                if (uiState.config != null && uiState.metrics != null) {
                    CalendarScreen(
                        config = uiState.config!!,
                        metrics = uiState.metrics!!,
                        onDateSelected = { date ->
                            selectedDate = date
                            navController.navigate(Screen.LogHours.route)
                        }
                    )
                }
            }
            composable(Screen.LogHours.route) {
                uiState.config?.let { config ->
                    LogHoursScreen(
                        initialDate = selectedDate,
                        config = config,
                        onSave = { date, offset, note ->
                            viewModel.saveAdjustment(date, offset, note)
                            scope.launch {
                                snackbarHostState.showSnackbar("Entry saved for $date")
                            }
                            navController.popBackStack(Screen.Calendar.route, inclusive = false)
                        }
                    )
                }
            }
            composable(Screen.History.route) {
                uiState.metrics?.let { metrics ->
                    HistoryScreen(
                        metrics = metrics,
                        onDelete = { date -> viewModel.deleteAdjustment(date) }
                    )
                }
            }
            composable(Screen.Settings.route) {
                uiState.config?.let { config ->
                    SettingsScreen(
                        config = config,
                        onSave = { hours, start, month, day, week ->
                            viewModel.updateConfig(hours, start, month, day, week)
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
