package com.obdinsight.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obdinsight.app.core.ConnectionState
import com.obdinsight.app.core.DiagnosticsController
import com.obdinsight.app.data.PidExchange
import com.obdinsight.app.data.Repository
import com.obdinsight.app.ui.nav.SimpleViewModelFactory

@Composable
fun ScanScreen(controller: DiagnosticsController, repository: Repository) {
    val viewModel: ScanViewModel = viewModel(
        factory = SimpleViewModelFactory { ScanViewModel(controller, repository) },
    )
    val connectionState by viewModel.connectionState.collectAsState()
    val exchanges by viewModel.exchanges.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val customResult by viewModel.customResult.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Scan") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (connectionState !is ConnectionState.Connected) {
                Text("Connect to your dongle first (see the Connect tab).")
                return@Scaffold
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { viewModel.runQuickScan() }, enabled = !scanning) {
                    Text(if (scanning) "Scanning..." else "Quick scan (supported PIDs)")
                }
                OutlinedButton(onClick = { viewModel.refreshLive() }, enabled = !scanning) {
                    Text("Refresh")
                }
            }
            if (scanning && progress.second > 0) {
                LinearProgressIndicator(
                    progress = { progress.first.toFloat() / progress.second },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            lastError?.let { Text("Scan error: $it", color = MaterialTheme.colorScheme.error) }

            CustomProbeCard(
                onProbe = { mode, pid -> viewModel.sendCustom(mode, pid) },
                onRawAt = { viewModel.sendRawAt(it) },
                result = customResult,
            )

            Text(
                "Live exchanges (most recent first)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            ExchangeList(exchanges)
        }
    }
}

@Composable
private fun CustomProbeCard(
    onProbe: (mode: String, pidOrDid: String) -> Unit,
    onRawAt: (String) -> Unit,
    result: String?,
) {
    var mode by remember { mutableStateOf("22") }
    var pid by remember { mutableStateOf("") }
    var atCommand by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Send a custom mode + PID/DID", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = mode, onValueChange = { mode = it },
                    label = { Text("Mode (hex)") }, modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = pid, onValueChange = { pid = it },
                    label = { Text("PID/DID (hex)") },
                    modifier = Modifier.padding(start = 8.dp).fillMaxWidth(),
                )
            }
            Button(
                onClick = { onProbe(mode, pid) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Probe") }

            Text(
                "Or send a raw AT/OBD command directly",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = atCommand, onValueChange = { atCommand = it },
                    label = { Text("e.g. ATRV, 010C, 22F40C") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = { onRawAt(atCommand) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Send")
            }
            result?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExchangeList(exchanges: List<PidExchange>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(exchanges, key = { it.id }) { ex ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.padding(10.dp)) {
                    if (ex.decodedName != null) {
                        Text(
                            "${ex.decodedName}: ${ex.decodedValue?.let { "%.2f".format(it) }} ${ex.decodedUnit ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    } else {
                        Text("Unrecognized PID/DID", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "req ${ex.requestHex}  ->  ${ex.responseRaw.replace("\n", " | ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
