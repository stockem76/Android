package com.obdinsight.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdinsight.app.core.ConnectionState
import com.obdinsight.app.core.DiagnosticsController
import com.obdinsight.app.data.PidExchange
import com.obdinsight.app.data.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModel(
    private val controller: DiagnosticsController,
    repository: Repository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = controller.connectionState

    val exchanges: StateFlow<List<PidExchange>> = controller.connectionState
        .flatMapLatest { state ->
            if (state is ConnectionState.Connected) repository.observeExchanges(state.sessionId) else emptyFlow()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _customResult = MutableStateFlow<String?>(null)
    val customResult: StateFlow<String?> = _customResult.asStateFlow()

    fun runQuickScan() {
        if (_scanning.value) return
        _scanning.value = true
        _lastError.value = null
        viewModelScope.launch {
            try {
                controller.runQuickScan { done, total -> _progress.value = done to total }
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _scanning.value = false
            }
        }
    }

    fun refreshLive() {
        if (_scanning.value) return
        viewModelScope.launch {
            runCatching { controller.pollLive() }
        }
    }

    fun sendCustom(modeHex: String, pidOrDidHex: String) {
        viewModelScope.launch {
            val mode = modeHex.trim().toIntOrNull(16)
            if (mode == null) {
                _customResult.value = "Mode must be a hex number, e.g. 01 or 22"
                return@launch
            }
            try {
                controller.probeCustom(mode, pidOrDidHex.trim())
                _customResult.value = "Sent - see it in the exchange list below."
            } catch (e: Exception) {
                _customResult.value = "Failed: ${e.message}"
            }
        }
    }

    fun sendRawAt(command: String) {
        viewModelScope.launch {
            _customResult.value = try {
                controller.sendRawCommand(command)
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
        }
    }
}
