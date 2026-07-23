package com.obdinsight.app.bluetooth

import java.util.UUID

/**
 * The Carista dongle communicates over Bluetooth Low Energy using a vendor GATT profile that
 * is not publicly documented (Carista's app is closed-source, and no public teardown lists its
 * exact service/characteristic UUIDs at the time this was written). In practice nearly every
 * consumer ELM327-compatible BLE dongle - including, as far as can be observed externally,
 * Carista's - wraps the same ELM327 AT-command byte stream inside one of a small number of
 * well-known "BLE serial bridge" GATT profiles. [BleTransport] tries each of these in turn and
 * falls back to generic characteristic discovery if none match, so a dongle using an
 * undocumented-but-similar profile still has a good chance of working.
 */
object BleUuids {

    data class Candidate(
        val label: String,
        val service: UUID,
        val writeCharacteristic: UUID,
        val notifyCharacteristic: UUID,
    )

    val CLIENT_CHARACTERISTIC_CONFIG: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Tried in order; the first one whose service+characteristics are all present wins. */
    val KNOWN_PROFILES: List<Candidate> = listOf(
        // Very common Chinese ELM327-BLE clone profile (Vgate iCar Pro BLE and many others).
        Candidate(
            label = "FFF0 serial bridge",
            service = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB"),
            writeCharacteristic = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB"),
            notifyCharacteristic = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB"),
        ),
        // HM-10 style single-characteristic serial bridge.
        Candidate(
            label = "FFE0/FFE1 serial bridge",
            service = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"),
            writeCharacteristic = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
            notifyCharacteristic = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"),
        ),
        // Nordic UART Service - common when a vendor wraps ELM327 output over a Nordic BLE chip.
        Candidate(
            label = "Nordic UART Service",
            service = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"),
            writeCharacteristic = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"),
            notifyCharacteristic = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"),
        ),
    )
}
