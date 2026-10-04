package digital.vmstudio.code.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import digital.vmstudio.code.navigation.VmDestination
import digital.vmstudio.code.navigation.VmNavHost

/**
 * Root layout.
 *
 * The navigation surface adapts to width: a bottom bar on a phone, a rail from
 * medium width up so a tablet or unfolded device does not waste vertical space and
 * can eventually show a multi-pane workspace beside it.
 */
@Composable
fun VmApp(
    widthSizeClass: WindowWidthSizeClass,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    // Secondary screens and the full-screen AI Cockpit own the full viewport;
    // hiding the bulky 5-tab bar in the AI chat gives an immersive, clutter-free
    // canvas like Kimi and Claude Code mobile.
    val isOnAgent = currentDestination.isOn(VmDestination.Agent) ||
        currentDestination?.route?.startsWith("agent") == true ||
        currentDestination?.route?.contains("/agent") == true
    val showPrimaryNavigation = !isOnAgent && VmDestination.primaryDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    fun navigateToPrimary(destination: VmDestination.Primary) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && showPrimaryNavigation) {
                NavigationRail {
                    VmDestination.primaryDestinations.forEach { destination ->
                        val selected = currentDestination.isOn(destination)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToPrimary(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!useRail && showPrimaryNavigation) {
                        NavigationBar {
                            VmDestination.primaryDestinations.forEach { destination ->
                                val selected = currentDestination.isOn(destination)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToPrimary(destination) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    VmNavHost(
                        navController = navController,
                        isOnline = isOnline,
                    )

                    // Above the graph so an approval raised by background work
                    // reaches the user wherever they currently are.
                    CommandApprovalHost()
                    FileEditApprovalHost()
                }
            }
        }
    }
}

private fun androidx.navigation.NavDestination?.isOn(destination: VmDestination.Primary?): Boolean =
    destination?.let { dest -> this?.hierarchy?.any { it.route == dest.route } } == true
