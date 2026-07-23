package com.obdinsight.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.obdinsight.app.data.PidExchange
import com.obdinsight.app.data.RawFrame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exports a session's raw frames and decoded/unknown PID exchanges as CSV, shareable via any app. */
object CsvExporter {

    private fun escape(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }

    fun writeRawFrames(context: Context, sessionId: Long, frames: List<RawFrame>): File {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "session_${sessionId}_raw_$stamp.csv")
        file.bufferedWriter().use { w ->
            w.appendLine("timestamp_millis,direction,hex,ascii")
            for (f in frames) {
                w.appendLine(
                    listOf(f.timestampMillis.toString(), f.direction, escape(f.hex), escape(f.ascii))
                        .joinToString(","),
                )
            }
        }
        return file
    }

    fun writeExchanges(context: Context, sessionId: Long, exchanges: List<PidExchange>): File {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "session_${sessionId}_pids_$stamp.csv")
        file.bufferedWriter().use { w ->
            w.appendLine("timestamp_millis,mode,pid,request_hex,response_raw,decoded_name,decoded_value,decoded_unit,is_known_standard_pid")
            for (e in exchanges) {
                w.appendLine(
                    listOf(
                        e.timestampMillis.toString(),
                        e.mode?.toString() ?: "",
                        e.pid?.let { "%02X".format(it) } ?: "",
                        escape(e.requestHex),
                        escape(e.responseRaw),
                        escape(e.decodedName ?: ""),
                        e.decodedValue?.toString() ?: "",
                        escape(e.decodedUnit ?: ""),
                        e.isKnownStandardPid.toString(),
                    ).joinToString(","),
                )
            }
        }
        return file
    }

    fun shareIntent(context: Context, files: List<File>): Intent {
        val uris = files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Export diagnostic log")
    }
}
