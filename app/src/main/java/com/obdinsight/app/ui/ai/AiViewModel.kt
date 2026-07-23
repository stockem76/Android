package com.obdinsight.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdinsight.app.ai.ClaudeAnalyzer
import com.obdinsight.app.ai.SecurePrefs
import com.obdinsight.app.data.AiAnalysis
import com.obdinsight.app.data.PidExchange
import com.obdinsight.app.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

class AiViewModel(
    private val repository: Repository,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    val unknownExchanges: StateFlow<List<PidExchange>> = repository.observeUnknownExchanges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analyses: StateFlow<List<AiAnalysis>> = repository.observeAiAnalyses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _analyzingIds = MutableStateFlow<Set<Long>>(emptySet())
    val analyzingIds: StateFlow<Set<Long>> = _analyzingIds.asStateFlow()

    fun analyze(exchange: PidExchange) {
        val apiKey = securePrefs.apiKey
        viewModelScope.launch {
            _analyzingIds.value = _analyzingIds.value + exchange.id
            try {
                val hypothesis = ClaudeAnalyzer(apiKey ?: "").identify(
                    vehicleContext = securePrefs.vehicleContext,
                    mode = exchange.mode,
                    pid = exchange.pid,
                    requestHex = exchange.requestHex,
                    responseRaw = exchange.responseRaw,
                )
                val sourcesJson = JSONArray().apply {
                    hypothesis.sources.forEach { (title, url) ->
                        put(org.json.JSONObject().put("title", title).put("url", url))
                    }
                }
                repository.saveAiAnalysis(
                    AiAnalysis(
                        pidExchangeId = exchange.id,
                        createdAtMillis = System.currentTimeMillis(),
                        vehicleContext = securePrefs.vehicleContext,
                        requestHex = exchange.requestHex,
                        responseRaw = exchange.responseRaw,
                        hypothesisName = hypothesis.name,
                        hypothesisUnit = hypothesis.unit,
                        hypothesisFormula = hypothesis.formula,
                        confidence = hypothesis.confidence,
                        reasoning = hypothesis.reasoning,
                        sourcesJson = sourcesJson.toString(),
                        error = hypothesis.error,
                    ),
                )
            } finally {
                _analyzingIds.value = _analyzingIds.value - exchange.id
            }
        }
    }

    fun hasApiKey(): Boolean = !securePrefs.apiKey.isNullOrBlank()
}
