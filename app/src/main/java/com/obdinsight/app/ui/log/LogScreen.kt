package com.obdinsight.app.ui.log

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obdinsight.app.core.ConnectionState
import com.obdinsight.app.core.DiagnosticsController
import com.obdinsight.app.data.RawFrame
import com.obdinsight.app.data.Repository
import com.obdinsight.app.export.CsvExporter
import com.obdinsight.app.ui.nav.SimpleViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun LogScreen(controller: DiagnosticsController, repository: Repository) {
    val viewModel: LogViewModel = viewModel(
        factory = SimpleViewModelFactory { LogViewModel(controller, repository) },
    )
    val connectionState by viewModel.connectionState.collectAsState()
    val frames by viewModel.rawFrames.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Raw log") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (connectionState !is ConnectionState.Connected) {
                Text("Connect to your dongle first (see the Connect tab).")
                return@Scaffold
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${frames.size} raw TX/RX frames (most recent first)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = {
                    val sessionId = viewModel.currentSessionId() ?: return@Button
                    scope.launch {
                        val (allFrames, allExchanges) = viewModel.exportData(sessionId)
                        val rawFile = CsvExporter.writeRawFrames(context, sessionId, allFrames)
                        val pidFile = CsvExporter.writeExchanges(context, sessionId, allExchanges)
                        context.startActivity(CsvExporter.shareIntent(context, listOf(rawFile, pidFile)))
                    }
                }) { Text("Export CSV") }
            }
            FrameList(frames)
        }
    }
}

@Composable
private fun FrameList(frames: List<RawFrame>) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(frames, key = { it.id }) { f ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "${f.direction}  ${f.hex}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (f.ascii.isNotBlank()) {
                        Text(f.ascii, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
