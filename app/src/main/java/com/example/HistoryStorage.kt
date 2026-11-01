package com.example

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GenerationHistoryItem(
    val id: String,
    val text: String,
    val speed: Float,
    val durationSeconds: Float,
    val createdAtEpochMs: Long,
    val wavFilePath: String
) {
    val formattedTimestamp: String
        get() {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            return sdf.format(Date(createdAtEpochMs))
        }

    val wavFile: File
        get() = File(wavFilePath)
}

/**
 * Local-only persistence for the last 8 generated WAV audio items inside filesDir/history/.
 */
object HistoryStorage {

    const val MAX_HISTORY_ITEMS = 8
    private const val HISTORY_JSON_NAME = "history_last_8.json"
    private const val HISTORY_DIR_NAME = "history_wavs"

    fun getHistoryDir(filesDir: File): File {
        val dir = File(filesDir, HISTORY_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun loadHistory(filesDir: File): List<GenerationHistoryItem> {
        val jsonFile = File(filesDir, HISTORY_JSON_NAME)
        if (!jsonFile.exists()) return emptyList()
        return try {
            val raw = jsonFile.readText(Charsets.UTF_8)
            val arr = JSONArray(raw)
            val list = mutableListOf<GenerationHistoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val wavPath = obj.optString("wavFilePath", "")
                if (wavPath.isNotEmpty() && File(wavPath).exists()) {
                    list.add(
                        GenerationHistoryItem(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            text = obj.optString("text", ""),
                            speed = obj.optDouble("speed", 1.0).toFloat(),
                            durationSeconds = obj.optDouble("durationSeconds", 0.0).toFloat(),
                            createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis()),
                            wavFilePath = wavPath
                        )
                    )
                }
            }
            list.take(MAX_HISTORY_ITEMS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addGeneration(
        filesDir: File,
        text: String,
        speed: Float,
        durationSeconds: Float,
        wavFile: File
    ): List<GenerationHistoryItem> {
        val current = loadHistory(filesDir).toMutableList()
        val newItem = GenerationHistoryItem(
            id = "gen_${System.currentTimeMillis()}",
            text = text.trim(),
            speed = speed,
            durationSeconds = durationSeconds,
            createdAtEpochMs = System.currentTimeMillis(),
            wavFilePath = wavFile.absolutePath
        )

        current.add(0, newItem)

        // Keep strictly the last 8 items; delete older WAV files
        val kept = current.take(MAX_HISTORY_ITEMS)
        val removed = current.drop(MAX_HISTORY_ITEMS)
        for (old in removed) {
            try {
                val f = File(old.wavFilePath)
                if (f.exists() && kept.none { it.wavFilePath == old.wavFilePath }) {
                    f.delete()
                }
            } catch (_: Exception) {
            }
        }

        saveHistory(filesDir, kept)
        return kept
    }

    fun deleteHistoryItem(filesDir: File, itemId: String): List<GenerationHistoryItem> {
        val current = loadHistory(filesDir)
        val toRemove = current.filter { it.id == itemId }
        val remaining = current.filterNot { it.id == itemId }
        for (item in toRemove) {
            try {
                File(item.wavFilePath).delete()
            } catch (_: Exception) {
            }
        }
        saveHistory(filesDir, remaining)
        return remaining
    }

    fun clearAllHistory(filesDir: File): List<GenerationHistoryItem> {
        val current = loadHistory(filesDir)
        for (item in current) {
            try {
                File(item.wavFilePath).delete()
            } catch (_: Exception) {
            }
        }
        saveHistory(filesDir, emptyList())
        return emptyList()
    }

    private fun saveHistory(filesDir: File, items: List<GenerationHistoryItem>) {
        try {
            val arr = JSONArray()
            for (item in items.take(MAX_HISTORY_ITEMS)) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("speed", item.speed.toDouble())
                    put("durationSeconds", item.durationSeconds.toDouble())
                    put("createdAtEpochMs", item.createdAtEpochMs)
                    put("wavFilePath", item.wavFilePath)
                }
                arr.put(obj)
            }
            File(filesDir, HISTORY_JSON_NAME).writeText(arr.toString(2), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
