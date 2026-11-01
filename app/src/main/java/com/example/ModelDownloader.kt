package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Handles downloading, HTTP Range resuming, and exact byte-size verification of model.onnx.
 */
object ModelDownloader {

    const val MODEL_URL =
        "https://huggingface.co/demoqqq/banglatts-eleven-new/resolve/main/model.onnx"
    const val MODEL_FILENAME = "model.onnx"
    const val EXPECTED_MODEL_SIZE_BYTES = 114160150L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getModelFile(filesDir: File): File = File(filesDir, MODEL_FILENAME)

    /**
     * Checks if filesDir/model.onnx exists AND its exact size is 114,160,150 bytes.
     */
    fun isDefaultModelReady(filesDir: File): Boolean {
        val file = getModelFile(filesDir)
        return file.exists() && file.isFile && file.length() == EXPECTED_MODEL_SIZE_BYTES
    }

    /**
     * Returns any partial bytes already downloaded on disk so UI can display resumable progress.
     */
    fun getPartialDownloadedBytes(filesDir: File): Long {
        val file = getModelFile(filesDir)
        if (!file.exists()) return 0L
        val len = file.length()
        return if (len in 1 until EXPECTED_MODEL_SIZE_BYTES) len else 0L
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    data class DownloadProgress(
        val downloadedBytes: Long,
        val totalBytes: Long = EXPECTED_MODEL_SIZE_BYTES,
        val percent: Int,
        val resumedFromBytes: Long = 0L
    )

    sealed class DownloadOutcome {
        data class Success(val modelFile: File) : DownloadOutcome()
        data class Error(
            val messageBn: String,
            val canResume: Boolean,
            val currentBytes: Long
        ) : DownloadOutcome()
    }

    /**
     * Downloads model.onnx to filesDir/model.onnx with HTTP Range request support for resuming
     * interrupted downloads, and strictly verifies the final size == 114,160,150 bytes.
     * If final size does not match after completion, deletes the file and returns Error.
     */
    suspend fun downloadAndVerifyModel(
        context: Context,
        filesDir: File,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val targetFile = getModelFile(filesDir)

        // If already verified, return immediately
        if (targetFile.exists() && targetFile.length() == EXPECTED_MODEL_SIZE_BYTES) {
            onProgress(
                DownloadProgress(
                    downloadedBytes = EXPECTED_MODEL_SIZE_BYTES,
                    totalBytes = EXPECTED_MODEL_SIZE_BYTES,
                    percent = 100
                )
            )
            return@withContext DownloadOutcome.Success(targetFile)
        }

        // If oversized/corrupted, remove before starting
        if (targetFile.exists() && targetFile.length() > EXPECTED_MODEL_SIZE_BYTES) {
            targetFile.delete()
        }

        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

        if (!isNetworkAvailable(context)) {
            return@withContext DownloadOutcome.Error(
                messageBn = "ইন্টারনেট সংযোগ নেই। প্রথমবার ভয়েস মডেল ডাউনলোডের জন্য একবার ইন্টারনেট সংযোগ প্রয়োজন (১১৪ মেগাবাইট)। ডাউনলোড সম্পন্ন হলে অ্যাপটি আজীবন অফলাইনে চলবে।",
                canResume = existingBytes > 0L,
                currentBytes = existingBytes
            )
        }

        val requestBuilder = Request.Builder()
            .url(MODEL_URL)
            .header("User-Agent", "BanglaTTS-Android/1.0")

        if (existingBytes in 1 until EXPECTED_MODEL_SIZE_BYTES) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code

                // HTTP 416 means range not satisfiable -> reset file and ask user to retry
                if (code == 416) {
                    targetFile.delete()
                    return@withContext DownloadOutcome.Error(
                        messageBn = "আগের আংশিক ডাউনলোড রিসেট করা হয়েছে। অনুগ্রহ করে 'পুনরায় চেষ্টা করুন' বাটনে চাপ দিন।",
                        canResume = false,
                        currentBytes = 0L
                    )
                }

                if (!response.isSuccessful && code != 206) {
                    return@withContext DownloadOutcome.Error(
                        messageBn = "সার্ভার থেকে মডেল ডাউনলোড করা যায়নি (HTTP $code)। অনুগ্রহ করে পুনরায় চেষ্টা করুন।",
                        canResume = existingBytes > 0L,
                        currentBytes = existingBytes
                    )
                }

                val isResuming = (code == 206 && existingBytes > 0L)
                val startBytes = if (isResuming) existingBytes else 0L

                val body = response.body ?: return@withContext DownloadOutcome.Error(
                    messageBn = "সার্ভার থেকে খালি রেসপন্স এসেছে। পুনরায় চেষ্টা করুন।",
                    canResume = startBytes > 0L,
                    currentBytes = startBytes
                )

                var totalRead = startBytes
                val initialPercent = ((totalRead * 100L) / EXPECTED_MODEL_SIZE_BYTES).toInt().coerceIn(0, 100)
                onProgress(
                    DownloadProgress(
                        downloadedBytes = totalRead,
                        totalBytes = EXPECTED_MODEL_SIZE_BYTES,
                        percent = initialPercent,
                        resumedFromBytes = startBytes
                    )
                )

                body.byteStream().use { input ->
                    FileOutputStream(targetFile, isResuming).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var lastReportedPercent = initialPercent
                        var lastReportTimeMs = System.currentTimeMillis()

                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read

                            val percent = ((totalRead * 100L) / EXPECTED_MODEL_SIZE_BYTES)
                                .toInt()
                                .coerceIn(0, 100)
                            val now = System.currentTimeMillis()
                            if (percent != lastReportedPercent || (now - lastReportTimeMs) >= 200L) {
                                lastReportedPercent = percent
                                lastReportTimeMs = now
                                onProgress(
                                    DownloadProgress(
                                        downloadedBytes = totalRead,
                                        totalBytes = EXPECTED_MODEL_SIZE_BYTES,
                                        percent = percent,
                                        resumedFromBytes = startBytes
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Strict size verification after download completes
            val finalSize = if (targetFile.exists()) targetFile.length() else -1L
            if (finalSize != EXPECTED_MODEL_SIZE_BYTES) {
                targetFile.delete()
                return@withContext DownloadOutcome.Error(
                    messageBn = "মডেল ফাইল যাচাই ব্যর্থ হয়েছে! প্রত্যাশিত সাইজ ১১৪১৬০১৫০ বাইট, কিন্তু পাওয়া গেছে $finalSize বাইট। ক্ষতিগ্রস্ত ফাইলটি মুছে ফেলা হয়েছে। অনুগ্রহ করে পুনরায় চেষ্টা করুন।",
                    canResume = false,
                    currentBytes = 0L
                )
            }

            onProgress(
                DownloadProgress(
                    downloadedBytes = EXPECTED_MODEL_SIZE_BYTES,
                    totalBytes = EXPECTED_MODEL_SIZE_BYTES,
                    percent = 100
                )
            )
            DownloadOutcome.Success(targetFile)
        } catch (e: IOException) {
            val savedSoFar = if (targetFile.exists()) targetFile.length() else 0L
            DownloadOutcome.Error(
                messageBn = "ডাউনলোড চলাকালীন ইন্টারনেট সংযোগ বিচ্ছিন্ন হয়েছে (${formatMb(savedSoFar)} / ১০৮.৮৭ MB সংরক্ষিত আছে)। 'পুনরায় চেষ্টা করুন' চাপলে এখান থেকেই ডাউনলোড শুরু হবে।",
                canResume = savedSoFar in 1 until EXPECTED_MODEL_SIZE_BYTES,
                currentBytes = savedSoFar
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val savedSoFar = if (targetFile.exists()) targetFile.length() else 0L
            DownloadOutcome.Error(
                messageBn = "ডাউনলোড ত্রুটি: ${e.localizedMessage ?: "অজানা সমস্যা"}। অনুগ্রহ করে পুনরায় চেষ্টা করুন।",
                canResume = savedSoFar in 1 until EXPECTED_MODEL_SIZE_BYTES,
                currentBytes = savedSoFar
            )
        }
    }

    fun formatMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.2f MB", mb)
    }
}
