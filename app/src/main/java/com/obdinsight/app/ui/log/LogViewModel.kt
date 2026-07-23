package com.obdinsight.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdinsight.app.core.ConnectionState
import com.obdinsight.app.core.DiagnosticsController
import com.obdinsight.app.data.RawFrame
import com.obdinsight.app.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class LogViewModel(
    private val controller: DiagnosticsController,
    private val repository: Repository,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = controller.connectionState

    val rawFrames: StateFlow<List<RawFrame>> = controller.connectionState
        .flatMapLatest { state ->
            if (state is ConnectionState.Connected) repository.observeRawFrames(state.sessionId) else emptyFlow()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun exportData(sessionId: Long) =
        repository.getAllRawFrames(sessionId) to repository.getAllExchanges(sessionId)

    fun currentSessionId(): Long? = (connectionState.value as? ConnectionState.Connected)?.sessionId
}
