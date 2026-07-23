package com.obdinsight.app.ui.connect

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obdinsight.app.bluetooth.BluetoothDeviceScanner
import com.obdinsight.app.bluetooth.DiscoveredDevice
import com.obdinsight.app.core.ConnectionState
import com.obdinsight.app.core.DiagnosticsController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectViewModel(
    private val controller: DiagnosticsController,
    private val adapter: BluetoothAdapter?,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = controller.connectionState

    private val _pairedDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val pairedDevices: StateFlow<List<DiscoveredDevice>> = _pairedDevices.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val bleDevices: StateFlow<List<DiscoveredDevice>> = _bleDevices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: kotlinx.coroutines.Job? = null

    fun refreshPaired() {
        val a = adapter ?: return
        _pairedDevices.value = BluetoothDeviceScanner(a).pairedClassicDevices()
    }

    fun startBleScan() {
        val a = adapter ?: return
        if (_scanning.value) return
        _bleDevices.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            val seen = LinkedHashMap<String, DiscoveredDevice>()
            try {
                BluetoothDeviceScanner(a).scanBleDevices().collect { device ->
                    seen[device.address] = device
                    _bleDevices.value = seen.values.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
                }
            } finally {
                _scanning.value = false
            }
        }
        // Stop scanning automatically after a reasonable window to save battery.
        viewModelScope.launch {
            kotlinx.coroutines.delay(15_000)
            stopBleScan()
        }
    }

    fun stopBleScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun connect(device: DiscoveredDevice) {
        stopBleScan()
        viewModelScope.launch { controller.connect(device) }
    }

    fun disconnect() {
        viewModelScope.launch { controller.disconnect() }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
