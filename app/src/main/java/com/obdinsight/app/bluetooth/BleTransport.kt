@file:Suppress("DEPRECATION")

package com.obdinsight.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * BLE transport for ELM327-compatible dongles, including Carista's own hardware (Carista is
 * confirmed to speak Bluetooth Low Energy; see [BleUuids] for the caveat on its exact GATT
 * profile not being publicly documented). Auto-detects the write/notify characteristic pair
 * from a list of known "BLE serial bridge" profiles used by most consumer OBD2 BLE dongles,
 * and falls back to generic discovery (first writable + first notifiable characteristic found
 * anywhere on the device) if none of the known profiles match.
 */
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : ObdTransport {

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = 23

    private val commandMutex = Mutex()
    private var pendingResponse: CompletableDeferred<Unit>? = null
    private val responseBuffer = StringBuilder()

    private var connectDeferred: CompletableDeferred<Unit>? = null
    private var servicesDeferred: CompletableDeferred<Unit>? = null
    private var mtuDeferred: CompletableDeferred<Unit>? = null
    private var descriptorDeferred: CompletableDeferred<Unit>? = null
    private var writeDeferred: CompletableDeferred<Unit>? = null

    private val _rawEvents = MutableSharedFlow<RawIoEvent>(extraBufferCapacity = 256)
    override val rawEvents = _rawEvents.asSharedFlow()

    override val isConnected: Boolean
        get() = writeChar != null && notifyChar != null

    var matchedProfileLabel: String? = null
        private set

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectDeferred?.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                writeChar = null
                notifyChar = null
                connectDeferred?.completeExceptionally(ObdTransportException("BLE disconnected (status=$status)"))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDeferred?.complete(Unit)
            } else {
                servicesDeferred?.completeExceptionally(ObdTransportException("Service discovery failed (status=$status)"))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
            // Not fatal if this fails - we just fall back to 20-byte writes.
            mtuDeferred?.complete(Unit)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: android.bluetooth.BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                descriptorDeferred?.complete(Unit)
            } else {
                descriptorDeferred?.completeExceptionally(ObdTransportException("Enable notifications failed (status=$status)"))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeDeferred?.complete(Unit)
            } else {
                writeDeferred?.completeExceptionally(ObdTransportException("BLE write failed (status=$status)"))
            }
        }

        // API 32 and below only call the deprecated 2-arg overload. API 33+ calls the 3-arg
        // overload below instead of this one - override both so notifications are handled on
        // every supported API level (minSdk 26 through compileSdk 34).
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { handleIncoming(it) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(value)
        }
    }

    private fun handleIncoming(bytes: ByteArray) {
        emitRaw(RawIoEvent.Direction.RX, bytes)
        responseBuffer.append(String(bytes, Charsets.US_ASCII))
        if (responseBuffer.contains(ByteUtils.PROMPT_CHAR)) {
            pendingResponse?.complete(Unit)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        connectDeferred = CompletableDeferred()
        gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        withTimeout(15_000) { connectDeferred!!.await() }

        servicesDeferred = CompletableDeferred()
        gatt!!.discoverServices()
        withTimeout(15_000) { servicesDeferred!!.await() }

        mtuDeferred = CompletableDeferred()
        gatt!!.requestMtu(247)
        runCatching { withTimeout(3_000) { mtuDeferred!!.await() } }

        val (_, wChar, nChar, label) = findCharacteristics(gatt!!.services)
            ?: throw ObdTransportException(
                "No writable+notifiable GATT characteristic pair found on this device. " +
                    "It may not be an ELM327-compatible dongle, or it uses a GATT profile " +
                    "this app doesn't recognize yet."
            )
        matchedProfileLabel = label
        writeChar = wChar
        notifyChar = nChar

        gatt!!.setCharacteristicNotification(nChar, true)
        val cccd = nChar.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        if (cccd != null) {
            descriptorDeferred = CompletableDeferred()
            cccd.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt!!.writeDescriptor(cccd)
            withTimeout(5_000) { descriptorDeferred!!.await() }
        }
    }

    override suspend fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeChar = null
        notifyChar = null
    }

    override suspend fun sendCommand(command: String, timeoutMillis: Long): String =
        commandMutex.withLock {
            withTimeout(timeoutMillis) {
                responseBuffer.clear()
                pendingResponse = CompletableDeferred()
                writeLine(command)
                pendingResponse!!.await()
                ByteUtils.stripPromptAndWhitespace(responseBuffer.toString())
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun writeLine(command: String) {
        val g = gatt ?: throw ObdTransportException("Not connected")
        val wChar = writeChar ?: throw ObdTransportException("Not connected")
        val bytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
        emitRaw(RawIoEvent.Direction.TX, bytes)

        val chunkSize = (negotiatedMtu - 3).coerceAtLeast(20)
        var offset = 0
        while (offset < bytes.size) {
            val end = (offset + chunkSize).coerceAtMost(bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            writeDeferred = CompletableDeferred()
            wChar.value = chunk
            wChar.writeType = if (wChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            g.writeCharacteristic(wChar)
            if (wChar.writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                withTimeout(5_000) { writeDeferred!!.await() }
            }
            offset = end
        }
    }

    private fun emitRaw(direction: RawIoEvent.Direction, bytes: ByteArray) {
        _rawEvents.tryEmit(
            RawIoEvent(
                timestampMillis = System.currentTimeMillis(),
                direction = direction,
                hex = ByteUtils.toHex(bytes),
                ascii = ByteUtils.toPrintableAscii(bytes),
            )
        )
    }

    private data class Found(
        val service: BluetoothGattService,
        val write: BluetoothGattCharacteristic,
        val notify: BluetoothGattCharacteristic,
        val label: String,
    )

    private fun findCharacteristics(services: List<BluetoothGattService>): Found? {
        for (candidate in BleUuids.KNOWN_PROFILES) {
            val service = services.firstOrNull { it.uuid == candidate.service } ?: continue
            val write = service.getCharacteristic(candidate.writeCharacteristic) ?: continue
            val notify = service.getCharacteristic(candidate.notifyCharacteristic) ?: continue
            return Found(service, write, notify, candidate.label)
        }

        // Generic fallback: first writable characteristic + first notifiable characteristic
        // anywhere on the device (may be the same characteristic, as with HM-10 style modules).
        var write: BluetoothGattCharacteristic? = null
        var notify: BluetoothGattCharacteristic? = null
        var owningService: BluetoothGattService? = null
        for (service in services) {
            for (c in service.characteristics) {
                val writable = c.properties and (
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    ) != 0
                val notifiable = c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                if (writable && write == null) {
                    write = c
                    owningService = service
                }
                if (notifiable && notify == null) notify = c
            }
        }
        return if (write != null && notify != null) {
            Found(owningService!!, write, notify, "generic fallback")
        } else {
            null
        }
    }
}
