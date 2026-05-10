package com.aggregatorx.shielded.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.aggregatorx.shielded.ui.screens.ProvidersScreen
import com.aggregatorx.shielded.ui.screens.SearchScreen
import com.aggregatorx.shielded.ui.screens.SettingsScreen
import com.aggregatorx.shielded.ui.theme.*
import com.aggregatorx.shielded.ui.viewmodel.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavTab("search",    "SEARCH",    Icons.Default.Search),
    NavTab("providers", "PROVIDERS", Icons.Default.Storage),
    NavTab("settings",  "SETTINGS",  Icons.Default.Settings)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShieldTheme { ShieldApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShieldApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Hoist SearchViewModel so TopAppBar controls can reach it
    val searchVm: SearchViewModel = hiltViewModel()
    val searchState by searchVm.state.collectAsState()

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "[ SHIELD ]",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonGreen
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureBlack,
                    titleContentColor = NeonGreen
                ),
                actions = {
                    // ── PAUSE / PLAY ──────────────────────────────────────────
                    IconButton(onClick = { searchVm.togglePause() }) {
                        Icon(
                            imageVector = if (searchState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (searchState.isPaused) "Resume" else "Pause",
                            tint = if (searchState.isPaused) AccentAmber else NeonGreen
                        )
                    }
                    // ── PANIC REFRESH ─────────────────────────────────────────
                    IconButton(onClick = { searchVm.panicRefresh() }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Panic Refresh",
                            tint = AccentRed
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceBlack,
                tonalElevation = 0.dp
            ) {
                TABS.forEach { tab ->
                    val selected = currentRoute == tab.route ||
                        backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != tab.route) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) NeonGreen else TextDim
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) NeonGreen else TextDim
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NeonGreenFaint,
                            selectedIconColor = NeonGreen,
                            unselectedIconColor = TextDim
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "search",
            modifier = Modifier.padding(padding)
        ) {
            composable("search")    { SearchScreen(vm = searchVm) }
            composable("providers") { ProvidersScreen() }
            composable("settings")  { SettingsScreen() }
        }
    }
}
