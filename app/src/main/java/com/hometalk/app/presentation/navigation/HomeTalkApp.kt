package com.hometalk.app.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hometalk.app.presentation.devices.DevicesScreen
import com.hometalk.app.presentation.devices.DevicesViewModel
import com.hometalk.app.presentation.home.HomeScreen
import com.hometalk.app.presentation.home.HomeViewModel
import com.hometalk.app.presentation.settings.SettingsScreen
import com.hometalk.app.presentation.settings.SettingsViewModel

private enum class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("home", "Talk", Icons.Filled.Home, Icons.Outlined.Home),
    Devices("devices", "Family", Icons.Filled.People, Icons.Outlined.People),
    Settings("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun HomeTalkApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onTalkPressed = viewModel::onTalkPressed,
                    onTalkReleased = viewModel::onTalkReleased,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onPermissionsResult = viewModel::updatePermissions,
                )
            }
            composable(Destination.Devices.route) {
                val viewModel: DevicesViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DevicesScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onStartHostingPairing = viewModel::startHostingPairing,
                    onStopHostingPairing = viewModel::stopHostingPairing,
                    onOpenPairDialog = viewModel::openPairDialog,
                    onDismissPairDialog = viewModel::dismissPairDialog,
                    onPairPinChanged = viewModel::onPairPinChanged,
                    onConfirmPair = viewModel::confirmPairWithDevice,
                    onOpenManualPair = viewModel::openManualPairDialog,
                    onDismissManualPair = viewModel::dismissManualPairDialog,
                    onManualHostChanged = viewModel::onManualHostChanged,
                    onManualPinChanged = viewModel::onManualPinChanged,
                    onConfirmManualPair = viewModel::confirmManualPair,
                    onUnpair = viewModel::unpair,
                    onDirectTalkPress = viewModel::onDirectTalkPressed,
                    onDirectTalkRelease = viewModel::onDirectTalkReleased,
                    onActionMessageShown = viewModel::consumeActionMessage,
                )
            }
            composable(Destination.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    pairedDevices = pairedDevices,
                    onDeviceNameChange = viewModel::updateDeviceName,
                    onRoomNameChange = viewModel::updateRoomName,
                    onSpeakerVolumeChange = viewModel::updateSpeakerVolume,
                    onNotificationsChange = viewModel::updateNotificationsEnabled,
                    onBitrateChange = viewModel::updateAudioBitrate,
                    onAddPeer = viewModel::addManualPeer,
                    onRemovePeer = viewModel::removeManualPeer,
                    onUnpair = viewModel::unpair,
                    onBackgroundServiceChange = viewModel::updateKeepServiceInBackground,
                )
            }
        }
    }
}
