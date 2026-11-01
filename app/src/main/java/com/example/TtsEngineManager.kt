package com.example

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Manages the sherpa-onnx 2.0.1 OfflineTts engine for the single-speaker Bengali VITS model.
 * - Sample rate: 22050 Hz, mono 16-bit WAV output.
 * - Default scales: [noiseScale = 0.667f, lengthScale = 1.0f, noiseScaleW = 1.0f]
 * - Default numThreads = 4, debug = false
 * - Loads strictly from file paths (assetManager = null -> newFromFile).
 */
class TtsEngineManager {

    data class EngineParams(
        val noiseScale: Float = 0.667f,
        val lengthScale: Float = 1.0f,
        val noiseScaleW: Float = 1.0f,
        val numThreads: Int = 4,
        val debug: Boolean = false,
        val interSentencePauseMs: Int = 180
    )

    data class SynthesisProgress(
        val currentPieceIndex: Int,
        val totalPieces: Int,
        val percent: Int,
        val currentPiecePreview: String
    )

    data class SynthesisOutput(
        val wavFile: File,
        val sampleRate: Int,
        val totalSamples: Int,
        val durationSeconds: Float,
        val pieceCount: Int
    )

    private val engineMutex = Mutex()
    private var offlineTts: OfflineTts? = null
    private var loadedModelPath: String? = null
    private var loadedTokensPath: String? = null
    private var loadedParams: EngineParams? = null

    /**
     * Initializes or re-initializes sherpa-onnx OfflineTts from file paths in filesDir.
     */
    suspend fun ensureInitialized(
        modelFile: File,
        tokensFile: File,
        params: EngineParams = EngineParams()
    ): Result<Int> = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            try {
                if (!modelFile.exists() || modelFile.length() <= 0L) {
                    return@withLock Result.failure(
                        IllegalStateException("মডেল ফাইল পাওয়া যায়নি (${modelFile.name})।")
                    )
                }
                if (!tokensFile.exists() || tokensFile.length() <= 0L) {
                    return@withLock Result.failure(
                        IllegalStateException("টোকেন ফাইল পাওয়া যায়নি (${tokensFile.name})।")
                    )
                }

                val currentTts = offlineTts
                if (currentTts != null &&
                    loadedModelPath == modelFile.absolutePath &&
                    loadedTokensPath == tokensFile.absolutePath &&
                    loadedParams == params
                ) {
                    return@withLock Result.success(currentTts.sampleRate())
                }

                // Release previous native instance if config changed
                try {
                    currentTts?.release()
                } catch (_: Throwable) {
                }
                offlineTts = null

                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    lexicon = "",
                    tokens = tokensFile.absolutePath,
                    dataDir = "",
                    dictDir = "",
                    noiseScale = params.noiseScale,
                    noiseScaleW = params.noiseScaleW,
                    lengthScale = params.lengthScale
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = params.numThreads.coerceIn(1, 8),
                    debug = params.debug,
                    provider = "cpu"
                )

                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = "",
                    ruleFars = "",
                    maxNumSentences = 1,
                    silenceScale = 1.0f
                )

                // Passing assetManager = null tells sherpa-onnx 2.0.1 to load from file paths via newFromFile(config)
                val newTts = OfflineTts(assetManager = null, config = ttsConfig)
                offlineTts = newTts
                loadedModelPath = modelFile.absolutePath
                loadedTokensPath = tokensFile.absolutePath
                loadedParams = params

                Result.success(newTts.sampleRate())
            } catch (t: Throwable) {
                Result.failure(
                    IllegalStateException(
                        "শেরপা-ওএনএনএক্স ইঞ্জিন লোড করতে ব্যর্থ: ${t.localizedMessage ?: t.javaClass.simpleName}",
                        t
                    )
                )
            }
        }
    }

    /**
     * Splits Bengali text into clean sentence pieces for piece-by-piece synthesis and real percent progress.
     */
    fun splitIntoSentencePieces(rawText: String): List<String> {
        val normalized = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val pieces = mutableListOf<String>()
        val current = StringBuilder()

        for (ch in normalized) {
            current.append(ch)
            if (ch == '।' || ch == '॥' || ch == '.' || ch == '?' || ch == '!' || ch == '\n') {
                val piece = current.toString().trim()
                if (piece.isNotEmpty() && piece.any { !it.isWhitespace() }) {
                    pieces.add(piece)
                }
                current.clear()
            } else if (current.length >= 160 && (ch == ',' || ch == '—' || ch == ';')) {
                val piece = current.toString().trim()
                if (piece.isNotEmpty()) {
                    pieces.add(piece)
                }
                current.clear()
            }
        }

        val tail = current.toString().trim()
        if (tail.isNotEmpty()) {
            pieces.add(tail)
        }

        return pieces.filter { piece ->
            piece.any { !it.isWhitespace() }
        }
    }

    /**
     * Synthesizes the input Bengali text piece-by-piece, reporting real percent progress after each piece,
     * and writes the joined 22050 Hz 16-bit mono WAV file.
     */
    suspend fun synthesizeToWav(
        text: String,
        speed: Float,
        modelFile: File,
        tokensFile: File,
        outputWavFile: File,
        params: EngineParams = EngineParams(),
        onProgress: (SynthesisProgress) -> Unit
    ): Result<SynthesisOutput> = withContext(Dispatchers.Default) {
        val pieces = splitIntoSentencePieces(text)
        if (pieces.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("অনুগ্রহ করে বাংলা টেক্সট লিখুন।")
            )
        }

        val initResult = ensureInitialized(modelFile, tokensFile, params)
        if (initResult.isFailure) {
            return@withContext Result.failure(
                initResult.exceptionOrNull()
                    ?: IllegalStateException("ভয়েস ইঞ্জিন প্রস্তুত নয়।")
            )
        }

        engineMutex.withLock {
            val tts = offlineTts ?: return@withLock Result.failure(
                IllegalStateException("ভয়েস ইঞ্জিন লোড হয়নি।")
            )

            try {
                val sampleRate = tts.sampleRate().takeIf { it > 0 } ?: WavWriter.DEFAULT_SAMPLE_RATE
                val pauseSamplesCount = ((sampleRate * params.interSentencePauseMs) / 1000).coerceAtLeast(0)
                val chunks = ArrayList<FloatArray>(pieces.size)
                var totalSampleCount = 0

                onProgress(
                    SynthesisProgress(
                        currentPieceIndex = 0,
                        totalPieces = pieces.size,
                        percent = 0,
                        currentPiecePreview = pieces.first().take(48)
                    )
                )

                for ((index, piece) in pieces.withIndex()) {
                    coroutineContext.ensureActive()

                    // Single-speaker VITS model: sid = 0, speed = 0.75f / 1.0f / 1.25f / 1.5f
                    val generated = tts.generate(
                        text = piece,
                        sid = 0,
                        speed = speed.coerceIn(0.5f, 2.0f)
                    )

                    val pieceSamples = generated.samples
                    if (pieceSamples.isNotEmpty()) {
                        chunks.add(pieceSamples)
                        totalSampleCount += pieceSamples.size
                        if (index < pieces.lastIndex && pauseSamplesCount > 0) {
                            totalSampleCount += pauseSamplesCount
                        }
                    }

                    val completedCount = index + 1
                    val realPercent = ((completedCount * 100) / pieces.size).coerceIn(1, 100)
                    onProgress(
                        SynthesisProgress(
                            currentPieceIndex = completedCount,
                            totalPieces = pieces.size,
                            percent = realPercent,
                            currentPiecePreview = piece.take(48)
                        )
                    )
                }

                if (totalSampleCount == 0 || chunks.isEmpty()) {
                    return@withLock Result.failure(
                        IllegalStateException("এই টেক্সট থেকে কোনো অডিও স্যাম্পল তৈরি হয়নি। বৈধ বাংলা অক্ষর ব্যবহার করুন।")
                    )
                }

                // Join all synthesized sentence pieces into one contiguous FloatArray
                val combined = FloatArray(totalSampleCount)
                var offset = 0
                for ((i, chunk) in chunks.withIndex()) {
                    System.arraycopy(chunk, 0, combined, offset, chunk.size)
                    offset += chunk.size
                    if (i < chunks.lastIndex && pauseSamplesCount > 0 && offset + pauseSamplesCount <= combined.size) {
                        offset += pauseSamplesCount // zero-initialized silence pause
                    }
                }

                val savedOk = WavWriter.writeWavFile(
                    outputFile = outputWavFile,
                    samples = combined,
                    sampleRate = sampleRate
                )
                if (!savedOk) {
                    return@withLock Result.failure(
                        IllegalStateException("WAV অডিও ফাইল সংরক্ষণ করতে ব্যর্থ হয়েছে।")
                    )
                }

                val durationSec = combined.size.toFloat() / sampleRate.toFloat()
                Result.success(
                    SynthesisOutput(
                        wavFile = outputWavFile,
                        sampleRate = sampleRate,
                        totalSamples = combined.size,
                        durationSeconds = durationSec,
                        pieceCount = pieces.size
                    )
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Result.failure(
                    IllegalStateException(
                        "অডিও জেনারেশন ত্রুটি: ${t.localizedMessage ?: t.javaClass.simpleName}",
                        t
                    )
                )
            }
        }
    }

    fun release() {
        try {
            offlineTts?.release()
        } catch (_: Throwable) {
        }
        offlineTts = null
    }
}
