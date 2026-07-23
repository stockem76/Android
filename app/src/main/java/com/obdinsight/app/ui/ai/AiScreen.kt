package com.obdinsight.app.ui.ai

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obdinsight.app.ai.SecurePrefs
import com.obdinsight.app.data.AiAnalysis
import com.obdinsight.app.data.PidExchange
import com.obdinsight.app.data.Repository
import com.obdinsight.app.ui.nav.SimpleViewModelFactory
import org.json.JSONArray

@Composable
fun AiScreen(repository: Repository, securePrefs: SecurePrefs) {
    val viewModel: AiViewModel = viewModel(
        factory = SimpleViewModelFactory { AiViewModel(repository, securePrefs) },
    )
    val unknown by viewModel.unknownExchanges.collectAsState()
    val analyses by viewModel.analyses.collectAsState()
    val analyzing by viewModel.analyzingIds.collectAsState()
    val analysesByExchange = remember(analyses) { analyses.groupBy { it.pidExchangeId } }

    Scaffold(topBar = { TopAppBar(title = { Text("AI analysis") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!viewModel.hasApiKey()) {
                Text(
                    "Add your own Anthropic API key in Settings to enable AI identification " +
                        "of unrecognized PIDs/DIDs (it stays on this device and is used only " +
                        "for direct calls to api.anthropic.com).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Scaffold
            }
            Text(
                "${unknown.size} unrecognized PID/DID exchanges captured. Tap one to ask Claude " +
                    "(with web search) what it's likely measuring on this vehicle.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Divider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(unknown, key = { it.id }) { ex ->
                    UnknownExchangeCard(
                        exchange = ex,
                        analyses = analysesByExchange[ex.id].orEmpty(),
                        isAnalyzing = ex.id in analyzing,
                        onAnalyze = { viewModel.analyze(ex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnknownExchangeCard(
    exchange: PidExchange,
    analyses: List<AiAnalysis>,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("req ${exchange.requestHex}  ->  ${exchange.responseRaw.replace("\n", " | ")}")
            Row(Modifier.padding(top = 6.dp)) {
                if (isAnalyzing) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Asking Claude...")
                } else {
                    Button(onClick = onAnalyze) {
                        Text(if (analyses.isEmpty()) "Ask AI" else "Ask again")
                    }
                }
            }
            analyses.firstOrNull()?.let { a -> AnalysisResult(a) }
        }
    }
}

@Composable
private fun AnalysisResult(a: AiAnalysis) {
    Column(Modifier.padding(top = 8.dp)) {
        if (a.error != null) {
            Text(a.error, color = MaterialTheme.colorScheme.error)
            return
        }
        Text(
            (a.hypothesisName ?: "Unknown") +
                (a.hypothesisUnit?.let { "  ($it)" } ?: "") +
                (a.confidence?.let { "  - confidence: $it" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
        )
        a.hypothesisFormula?.let { Text("Formula: $it", style = MaterialTheme.typography.bodySmall) }
        Text(a.reasoning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        val sources = runCatching { JSONArray(a.sourcesJson) }.getOrNull()
        if (sources != null && sources.length() > 0) {
            Text("Sources:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            for (i in 0 until sources.length()) {
                val obj = sources.getJSONObject(i)
                Text("- ${obj.optString("title")}: ${obj.optString("url")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
