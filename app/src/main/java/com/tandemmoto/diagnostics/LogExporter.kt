package com.tandemmoto.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tandemmoto.R
import java.io.File
import java.io.IOException

/**
 * Shares the logs through the system share sheet, only when the user asks. The raw log files are
 * never exposed: a single export file is built in cache/exports/, the only path the FileProvider
 * serves (res/xml/log_export_paths.xml).
 */
object LogExporter {
    private const val TAG = "LogExporter"

    /** Writes [header], then each of [logs] (oldest first), into [out]. */
    fun buildExport(header: String, logs: List<File>, out: File): File {
        out.parentFile?.mkdirs()
        out.outputStream().buffered().use { stream ->
            stream.write("$header\n\n".toByteArray(Charsets.UTF_8))
            logs.forEach { log -> log.inputStream().use { it.copyTo(stream) } }
        }
        return out
    }

    fun share(context: Context) {
        val export = try {
            buildExport(
                header = AppLog.environmentSummary(),
                logs = AppLog.fileLog?.files().orEmpty(),
                out = File(context.cacheDir, "exports/tandemmoto-logs.txt")
            )
        } catch (e: IOException) {
            AppLog.w(TAG, "Couldn't build the log export", e)
            Toast.makeText(context, R.string.export_logs_failed, Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", export)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_logs_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.export_logs_chooser))
        )
        // Android doesn't report whether the user actually picked a target.
        AppLog.i(TAG, "Log export opened")
    }
}
