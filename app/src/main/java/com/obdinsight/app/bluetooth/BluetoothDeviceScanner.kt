package com.obdinsight.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class LinkKind { CLASSIC_PAIRED, BLE }

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val kind: LinkKind,
    val rssi: Int? = null,
)

/**
 * Lists already-paired classic Bluetooth devices (fast, no scan needed - the user pairs the
 * dongle once in Android's Bluetooth settings) and actively scans for nearby BLE devices
 * (Carista dongles, and most other BLE OBD2 clones, don't need to be "paired" first - the app
 * just connects directly over GATT).
 */
class BluetoothDeviceScanner(private val adapter: BluetoothAdapter) {

    @SuppressLint("MissingPermission")
    fun pairedClassicDevices(): List<DiscoveredDevice> =
        adapter.bondedDevices
            .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
            .map { DiscoveredDevice(it, it.name ?: it.address, it.address, LinkKind.CLASSIC_PAIRED) }

    @SuppressLint("MissingPermission")
    fun scanBleDevices(): Flow<DiscoveredDevice> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner
            ?: run { close(); return@callbackFlow }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: return
                trySend(
                    DiscoveredDevice(
                        device = device,
                        name = name,
                        address = device.address,
                        kind = LinkKind.BLE,
                        rssi = result.rssi,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(ObdTransportException("BLE scan failed (errorCode=$errorCode)"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)

        awaitClose { scanner.stopScan(callback) }
    }
}
