package com.obdinsight.app.obd

/**
 * A single Mode 01 ("show current data") PID as defined by SAE J1979 - these formulas are
 * universal across every OBD-II compliant vehicle (the Audi A1 8X CAYC is EU5, OBD-II compliant
 * since 2010), unlike manufacturer-specific PIDs/DIDs which vary by ECU and are exactly what
 * this app hands off to the AI + web-search analyzer instead of guessing at.
 */
data class StandardPid(
    val pid: Int,
    val name: String,
    val minBytes: Int,
    val unit: String,
    val decode: (List<Int>) -> Double,
) {
    val pidHex: String get() = "%02X".format(pid)
}

object StandardPids {
    const val MODE_CURRENT_DATA = 0x01
    const val MODE_FREEZE_FRAME = 0x02
    const val MODE_STORED_DTC = 0x03
    const val MODE_CLEAR_DTC = 0x04
    const val MODE_PENDING_DTC = 0x07
    const val MODE_VEHICLE_INFO = 0x09

    private fun ab(data: List<Int>) = data[0] * 256 + data[1]

    /** Ordered by PID; formulas per SAE J1979 Mode 01. */
    val ALL: List<StandardPid> = listOf(
        StandardPid(0x04, "Calculated engine load", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x05, "Engine coolant temperature", 1, "°C") { d -> (d[0] - 40).toDouble() },
        StandardPid(0x06, "Short term fuel trim - Bank 1", 1, "%") { d -> (d[0] - 128) * 100.0 / 128.0 },
        StandardPid(0x07, "Long term fuel trim - Bank 1", 1, "%") { d -> (d[0] - 128) * 100.0 / 128.0 },
        StandardPid(0x08, "Short term fuel trim - Bank 2", 1, "%") { d -> (d[0] - 128) * 100.0 / 128.0 },
        StandardPid(0x09, "Long term fuel trim - Bank 2", 1, "%") { d -> (d[0] - 128) * 100.0 / 128.0 },
        StandardPid(0x0A, "Fuel pressure", 1, "kPa") { d -> d[0] * 3.0 },
        StandardPid(0x0B, "Intake manifold absolute pressure", 1, "kPa") { d -> d[0].toDouble() },
        StandardPid(0x0C, "Engine RPM", 2, "rpm") { d -> ab(d) / 4.0 },
        StandardPid(0x0D, "Vehicle speed", 1, "km/h") { d -> d[0].toDouble() },
        StandardPid(0x0E, "Timing advance", 1, "° before TDC") { d -> d[0] / 2.0 - 64.0 },
        StandardPid(0x0F, "Intake air temperature", 1, "°C") { d -> (d[0] - 40).toDouble() },
        StandardPid(0x10, "MAF air flow rate", 2, "g/s") { d -> ab(d) / 100.0 },
        StandardPid(0x11, "Throttle position", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x1F, "Run time since engine start", 2, "s") { d -> ab(d).toDouble() },
        StandardPid(0x21, "Distance traveled with MIL on", 2, "km") { d -> ab(d).toDouble() },
        StandardPid(0x22, "Fuel rail pressure (vac)", 2, "kPa") { d -> ab(d) * 0.079 },
        StandardPid(0x23, "Fuel rail gauge pressure", 2, "kPa") { d -> ab(d) * 10.0 },
        StandardPid(0x2C, "Commanded EGR", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x2F, "Fuel level input", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x33, "Absolute barometric pressure", 1, "kPa") { d -> d[0].toDouble() },
        StandardPid(0x42, "Control module voltage", 2, "V") { d -> ab(d) / 1000.0 },
        StandardPid(0x43, "Absolute load value", 2, "%") { d -> ab(d) / 2.55 },
        StandardPid(0x44, "Commanded equivalence ratio (lambda)", 2, "λ") { d -> ab(d) / 32768.0 },
        StandardPid(0x45, "Relative throttle position", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x46, "Ambient air temperature", 1, "°C") { d -> (d[0] - 40).toDouble() },
        StandardPid(0x47, "Relative accelerator pedal position A", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x49, "Accelerator pedal position D", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x4C, "Commanded throttle actuator", 1, "%") { d -> d[0] / 2.55 },
        StandardPid(0x5C, "Engine oil temperature", 1, "°C") { d -> (d[0] - 40).toDouble() },
        StandardPid(0x5E, "Engine fuel rate", 2, "L/h") { d -> ab(d) / 20.0 },
    )

    val BY_PID: Map<Int, StandardPid> = ALL.associateBy { it.pid }

    /** The four Mode 01 "supported PIDs" bitmask requests that cover PIDs 01-80. */
    val SUPPORT_BITMASK_PIDS = listOf(0x00, 0x20, 0x40, 0x60)
}
