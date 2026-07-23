package com.obdinsight.app.ui.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obdinsight.app.bluetooth.DiscoveredDevice
import com.obdinsight.app.bluetooth.LinkKind
import com.obdinsight.app.core.ConnectionState
import com.obdinsight.app.core.DiagnosticsController
import com.obdinsight.app.ui.nav.SimpleViewModelFactory

private fun requiredBtPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasBtPermissions(context: Context): Boolean =
    requiredBtPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(controller: DiagnosticsController) {
    val context = LocalContext.current
    val adapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    val viewModel: ConnectViewModel = viewModel(
        factory = SimpleViewModelFactory { ConnectViewModel(controller, adapter) },
    )

    var permissionsGranted by remember { mutableStateOf(hasBtPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) viewModel.refreshPaired()
    }

    LaunchedEffect(Unit) {
        if (permissionsGranted) viewModel.refreshPaired()
    }

    val connectionState by viewModel.connectionState.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState()
    val ble by viewModel.bleDevices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Connect") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            ConnectionBanner(connectionState, onDisconnect = { viewModel.disconnect() })

            if (adapter == null) {
                Text("This device has no Bluetooth adapter.")
                return@Scaffold
            }
            if (!adapter.isEnabled) {
                Text("Bluetooth is turned off. Enable it in system settings and come back.")
                return@Scaffold
            }
            if (!permissionsGranted) {
                Text("Bluetooth permission is required to scan for and connect to your dongle.")
                Button(onClick = { permissionLauncher.launch(requiredBtPermissions()) }) {
                    Text("Grant permission")
                }
                return@Scaffold
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Paired (classic) devices", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.refreshPaired() }) { Text("Refresh") }
            }
            if (paired.isEmpty()) {
                Text("No paired classic devices. Pair your dongle in Android Bluetooth settings first if it uses classic Bluetooth.")
            }
            DeviceList(paired) { viewModel.connect(it) }

            Divider(Modifier.padding(vertical = 12.dp))

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Nearby BLE devices", style = MaterialTheme.typography.titleMedium)
                if (scanning) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                } else {
                    Button(onClick = { viewModel.startBleScan() }) { Text("Scan") }
                }
            }
            Text(
                "Carista dongles connect over BLE and don't need to be paired first - just scan and tap.",
                style = MaterialTheme.typography.bodySmall,
            )
            DeviceList(ble) { viewModel.connect(it) }
        }
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, onDisconnect: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            when (state) {
                is ConnectionState.Disconnected -> Text("Not connected")
                is ConnectionState.Connecting -> Row {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Connecting...")
                }
                is ConnectionState.Connected -> {
                    Text("Connected to ${state.deviceName}", style = MaterialTheme.typography.titleMedium)
                    state.profileLabel?.let { Text("BLE profile: $it", style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Disconnect")
                    }
                }
                is ConnectionState.Error -> Text("Connection failed: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DeviceList(devices: List<DiscoveredDevice>, onConnect: (DiscoveredDevice) -> Unit) {
    LazyColumn {
        items(devices, key = { it.address + it.kind.name }) { d ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(d.name)
                        Text(
                            d.address + (d.rssi?.let { "  ${it}dBm" } ?: "") +
                                if (d.kind == LinkKind.BLE) "  (BLE)" else "  (classic)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { onConnect(d) }) { Text("Connect") }
                }
            }
        }
    }
}
