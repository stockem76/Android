package com.obdinsight.app.core

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.obdinsight.app.ai.SecurePrefs
import com.obdinsight.app.bluetooth.BleTransport
import com.obdinsight.app.bluetooth.ClassicBluetoothTransport
import com.obdinsight.app.bluetooth.DiscoveredDevice
import com.obdinsight.app.bluetooth.LinkKind
import com.obdinsight.app.bluetooth.ObdTransport
import com.obdinsight.app.data.Repository
import com.obdinsight.app.obd.Elm327Session
import com.obdinsight.app.obd.ScanEngine
import com.obdinsight.app.obd.StandardPids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String, val profileLabel: String?, val sessionId: Long) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Owns the single active connection to the OBD dongle and everything downstream of it: the
 * ELM327 session, the scan engine, and the raw-frame logging pipeline. Screens observe
 * [connectionState] and call through here rather than touching the transport/session directly.
 */
class DiagnosticsController(
    private val context: Context,
    private val repository: Repository,
    private val securePrefs: SecurePrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rawFrameJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var transport: ObdTransport? = null
        private set
    var session: Elm327Session? = null
        private set
    var scanEngine: ScanEngine? = null
        private set
    var currentSessionId: Long? = null
        private set

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    }

    suspend fun connect(discovered: DiscoveredDevice) {
        _connectionState.value = ConnectionState.Connecting
        try {
            val newTransport: ObdTransport = when (discovered.kind) {
                LinkKind.CLASSIC_PAIRED -> {
                    val adapter = bluetoothAdapter ?: throw IllegalStateException("Bluetooth not available")
                    ClassicBluetoothTransport(adapter, discovered.device)
                }
                LinkKind.BLE -> BleTransport(context, discovered.device)
            }
            newTransport.connect()

            val newSession = Elm327Session(newTransport)
            newSession.initialize()

            val sessionId = repository.startSession(
                deviceName = discovered.name,
                deviceAddress = discovered.address,
                transportKind = discovered.kind.name,
                vehicleLabel = securePrefs.vehicleContext,
            )

            transport = newTransport
            session = newSession
            scanEngine = ScanEngine(newSession, repository.scanSinkFor(sessionId))
            currentSessionId = sessionId

            rawFrameJob?.cancel()
            rawFrameJob = scope.launch {
                newTransport.rawEvents.collect { event ->
                    repository.recordRawFrame(sessionId, event)
                }
            }

            val profileLabel = (newTransport as? BleTransport)?.matchedProfileLabel
            _connectionState.value = ConnectionState.Connected(discovered.name, profileLabel, sessionId)
        } catch (e: Exception) {
            runCatching { transport?.disconnect() }
            transport = null
            session = null
            scanEngine = null
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun disconnect() {
        rawFrameJob?.cancel()
        rawFrameJob = null
        currentSessionId?.let { repository.endSession(it) }
        runCatching { transport?.disconnect() }
        transport = null
        session = null
        scanEngine = null
        currentSessionId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun runQuickScan(onProgress: suspend (Int, Int) -> Unit = { _, _ -> }) {
        scanEngine?.quickScan(onProgress)
    }

    suspend fun pollLive() {
        val supported = session?.discoverSupportedPids() ?: return
        val known = supported.filter { StandardPids.BY_PID.containsKey(it) }.sorted()
        scanEngine?.pollOnce(known)
    }

    suspend fun probeCustom(mode: Int, pidOrDidHex: String) {
        scanEngine?.probeCustom(mode, pidOrDidHex)
    }

    suspend fun sendRawCommand(command: String): String =
        session?.sendRaw(command) ?: throw IllegalStateException("Not connected")
}
