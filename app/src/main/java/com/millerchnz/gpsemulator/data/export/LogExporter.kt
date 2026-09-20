package com.millerchnz.gpsemulator.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.millerchnz.gpsemulator.data.SampleField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes the rows currently matched by a viewer's filter to a shareable file.
 *
 * Both viewers describe a row as a list of [SampleField], so one exporter covers location
 * logs and geofence events, and a newly added field appears in exports automatically.
 */
object LogExporter {
    enum class Format(val label: String, val extension: String, val mimeType: String) {
        CSV("CSV", "csv", "text/csv"),
        JSON("JSON", "json", "application/json"),
    }

    private const val AUTHORITY_SUFFIX = ".exports"
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    suspend fun export(
        context: Context,
        baseName: String,
        rows: List<List<SampleField>>,
        format: Format,
    ): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val stamp = fileStamp.format(Instant.now().atZone(ZoneId.systemDefault()))
        val file = File(directory, "$baseName-$stamp.${format.extension}")
        file.writeText(
            when (format) {
                Format.CSV -> toCsv(rows)
                Format.JSON -> toJson(rows)
            },
        )
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    fun shareIntent(uri: Uri, format: Format): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${format.label}")
    }

    private fun toCsv(rows: List<List<SampleField>>): String {
        val keys = rows.firstOrNull()?.map { it.key } ?: return ""
        return buildString {
            append(keys.joinToString(",") { escapeCsv(it) })
            append('\n')
            rows.forEach { row ->
                append(row.joinToString(",") { escapeCsv(it.raw.orEmpty()) })
                append('\n')
            }
        }
    }

    private fun toJson(rows: List<List<SampleField>>): String {
        val array = JSONArray()
        rows.forEach { row ->
            val json = JSONObject()
            row.forEach { field -> json.put(field.key, field.raw ?: JSONObject.NULL) }
            array.put(json)
        }
        return array.toString(2)
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
