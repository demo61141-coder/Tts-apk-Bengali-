package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Utility for writing 16-bit mono PCM WAV files (22050 Hz) and exporting/sharing via MediaStore, SAF, and FileProvider.
 */
object WavWriter {

    const val DEFAULT_SAMPLE_RATE = 22050
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    /**
     * Writes float audio samples in [-1.0f, 1.0f] to a standard 44-byte header 16-bit mono PCM WAV file.
     */
    fun writeWavFile(
        outputFile: File,
        samples: FloatArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    writeWavStream(bos, samples, sampleRate)
                }
            }
            outputFile.exists() && outputFile.length() > 44L
        } catch (e: Exception) {
            false
        }
    }

    private fun writeWavStream(
        out: OutputStream,
        samples: FloatArray,
        sampleRate: Int
    ) {
        val dataSize = samples.size * 2 // 16-bit = 2 bytes per sample
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)
        val totalFileSizeMinus8 = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk descriptor
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalFileSizeMinus8)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        // fmt sub-chunk
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat 1 = PCM
        header.putShort(CHANNELS.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())
        // data sub-chunk
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())

        // Write 16-bit little-endian PCM samples in chunks
        val chunkSamples = 4096
        val byteChunk = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        var index = 0
        while (index < samples.size) {
            byteChunk.clear()
            val end = min(index + chunkSamples, samples.size)
            while (index < end) {
                val clamped = max(-1.0f, min(1.0f, samples[index]))
                val pcm16 = (clamped * 32767.0f).toInt().toShort()
                byteChunk.putShort(pcm16)
                index++
            }
            out.write(byteChunk.array(), 0, byteChunk.position())
        }
        out.flush()
    }

    /**
     * Saves an existing WAV file to public MediaStore (Music/BanglaTTS) without requiring storage permissions.
     */
    fun saveWavToMediaStore(
        context: Context,
        sourceWavFile: File,
        displayFileName: String
    ): Result<String> {
        if (!sourceWavFile.exists() || sourceWavFile.length() <= 44L) {
            return Result.failure(IllegalArgumentException("অডিও ফাইলটি পাওয়া যায়নি।"))
        }

        val cleanName = if (displayFileName.endsWith(".wav", ignoreCase = true)) {
            displayFileName
        } else {
            "$displayFileName.wav"
        }

        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.TITLE, cleanName.removeSuffix(".wav"))
                put(MediaStore.Audio.Media.ARTIST, "BanglaTTS Studio")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/BanglaTTS"
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, values)
                ?: return Result.failure(IllegalStateException("MediaStore এ অডিও এন্ট্রি তৈরি করা যায়নি।"))

            resolver.openOutputStream(itemUri)?.use { outStream ->
                FileInputStream(sourceWavFile).use { inStream ->
                    inStream.copyTo(outStream, 64 * 1024)
                }
            } ?: return Result.failure(IllegalStateException("আউটপুট স্ট্রিম খোলা যায়নি।"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, completeValues, null, null)
            }

            Result.success("Music/BanglaTTS/$cleanName এ সফলভাবে সেভ হয়েছে!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copies a WAV file to a user-chosen SAF document Uri (e.g., local folder or Google Drive).
     */
    fun exportWavToUri(context: Context, sourceWavFile: File, targetUri: Uri): Result<String> {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                FileInputStream(sourceWavFile).use { inStream ->
                    inStream.copyTo(outStream, 64 * 1024)
                }
            } ?: return Result.failure(IllegalStateException("নির্বাচিত ফাইলে লেখা সম্ভব হয়নি।"))
            Result.success("নির্বাচিত ফোল্ডার/ক্লাউডে WAV ফাইল এক্সপোর্ট সম্পন্ন হয়েছে!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Launches the Android system share sheet for sharing the WAV file to social media, messaging, or cloud apps.
     */
    fun shareWavFile(context: Context, wavFile: File, titleSnippet: String): Result<Unit> {
        return try {
            if (!wavFile.exists()) {
                return Result.failure(IllegalStateException("শেয়ার করার জন্য অডিও ফাইল পাওয়া যায়নি।"))
            }
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, wavFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "BanglaTTS অডিও: $titleSnippet")
                putExtra(Intent.EXTRA_TEXT, "BanglaTTS Studio দিয়ে তৈরি বাংলা অডিও: \"$titleSnippet\"")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "বাংলা অডিও শেয়ার করুন").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
