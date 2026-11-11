package com.hometalk.app.presentation.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hometalk.app.presentation.components.DeviceRow
import com.hometalk.app.presentation.components.QrCodeImage
import com.hometalk.app.presentation.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onRefresh: () -> Unit,
    onStartHostingPairing: () -> Unit,
    onStopHostingPairing: () -> Unit,
    onOpenPairDialog: (com.hometalk.app.domain.model.Device) -> Unit,
    onDismissPairDialog: () -> Unit,
    onPairPinChanged: (String) -> Unit,
    onConfirmPair: () -> Unit,
    onOpenManualPair: () -> Unit,
    onDismissManualPair: () -> Unit,
    onManualHostChanged: (String) -> Unit,
    onManualPinChanged: (String) -> Unit,
    onConfirmManualPair: () -> Unit,
    onUnpair: (com.hometalk.app.domain.model.Device) -> Unit,
    onActionMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onActionMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family members") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh discovery")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Discovery") {
                    Text(
                        text = state.discoveryStatus.ifBlank { "Looking for phones on this Wi-Fi…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.pairingSession == null) {
                            OutlinedButton(onClick = onStartHostingPairing, enabled = !state.isBusy) {
                                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text("Show pairing PIN")
                            }
                        } else {
                            OutlinedButton(onClick = onStopHostingPairing) {
                                Text("Stop pairing")
                            }
                        }
                        OutlinedButton(onClick = onOpenManualPair) {
                            Text("Pair by IP")
                        }
                    }
                }
            }

            state.pairingSession?.let { session ->
                item {
                    SectionCard(title = "Pairing PIN") {
                        Text(
                            text = session.pin,
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ask the other phone to tap Pair and enter this PIN. Expires in a few minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.pairingQrPayload?.let { payload ->
                            Spacer(modifier = Modifier.height(16.dp))
                            QrCodeImage(
                                payload = payload,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = payload,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.devices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GroupOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No phones found yet",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Keep HomeTalk open on both phones on the same Wi-Fi. Then show a pairing PIN on one phone and pair from the other.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(state.devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        onPair = onOpenPairDialog,
                        onUnpair = onUnpair,
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.showPairDialogFor?.let { device ->
        AlertDialog(
            onDismissRequest = onDismissPairDialog,
            title = { Text("Pair with ${device.name}") },
            text = {
                Column {
                    Text("Enter the PIN shown on the other phone.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.pairPinInput,
                        onValueChange = onPairPinChanged,
                        label = { Text("PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmPair,
                    enabled = state.pairPinInput.length == 6 && !state.isBusy,
                ) {
                    Text("Pair")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPairDialog) { Text("Cancel") }
            },
        )
    }

    if (state.showManualPairDialog) {
        AlertDialog(
            onDismissRequest = onDismissManualPair,
            title = { Text("Pair by IP") },
            text = {
                Column {
                    Text("Use this when phones are on separate router subnets. The other phone must be showing a pairing PIN.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.manualHostInput,
                        onValueChange = onManualHostChanged,
                        label = { Text("IP address") },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.manualPinInput,
                        onValueChange = onManualPinChanged,
                        label = { Text("PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmManualPair,
                    enabled = state.manualHostInput.isNotBlank() &&
                        state.manualPinInput.length == 6 &&
                        !state.isBusy,
                ) {
                    Text("Pair")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissManualPair) { Text("Cancel") }
            },
        )
    }
}
