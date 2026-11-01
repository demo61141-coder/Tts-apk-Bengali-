package com.example

import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val ttsEngineManager = TtsEngineManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                BanglaTtsApp(ttsEngineManager = ttsEngineManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsEngineManager.release()
    }
}

private enum class PlaybackState {
    IDLE, PLAYING, PAUSED
}

private data class SamplePrompt(
    val label: String,
    val text: String
)

private val SAMPLE_BENGALI_PROMPTS = listOf(
    SamplePrompt(
        label = "সংবাদ পাঠ",
        text = "আজকের প্রধান সংবাদে আপনাদের স্বাগতম। প্রযুক্তির নতুন দিগন্তে বাংলা কৃত্রিম বুদ্ধিমত্তা ও কণ্ঠস্বর সংশ্লেষণ এক নতুন মাইলফলক স্পর্শ করেছে। এই অ্যাপটি সম্পূর্ণ অফলাইনে কাজ করে।"
    ),
    SamplePrompt(
        label = "স্বাগত বার্তা",
        text = "নমস্কার ও আসসালামু আলাইকুম। বাংলা টেক্সট টু স্পিচ অডিও স্টুডিওতে আপনাকে স্বাগতম। আপনার পছন্দের যেকোনো বাংলা লেখা এখানে লিখুন এবং স্পষ্ট কণ্ঠে শুনুন।"
    ),
    SamplePrompt(
        label = "সাহিত্য ও কবিতা",
        text = "বাংলার মুখ আমি দেখিয়াছি, তাই আমি পৃথিবীর রূপ খুঁজিতে যাই না আর। অন্ধকারে জেগে উঠে ডুমুরের গাছে চেয়ে দেখি ছাতার মতন বড়ো পাতাটির নিচে বসে আছে ভোরের দোয়েল পাখি।"
    ),
    SamplePrompt(
        label = "প্রযুক্তি গাইড",
        text = "প্রথমবার মডেল ডাউনলোড সম্পন্ন হলে কোনো ইন্টারনেট ছাড়াই আপনি অসংখ্যবার বাংলা অডিও তৈরি, সংরক্ষণ এবং সোশ্যাল মিডিয়ায় শেয়ার করতে পারবেন।"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanglaTtsApp(ttsEngineManager: TtsEngineManager) {
    val context = LocalContext.current
    val filesDir = remember { context.filesDir }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 1. Token table state (84 lines verified on startup)
    var tokenValidation by remember {
        mutableStateOf<TokenTable.ValidationResult?>(null)
    }

    // 2. Model availability & download state
    var isModelReady by remember {
        mutableStateOf(ModelDownloader.isDefaultModelReady(filesDir))
    }
    var activeModelFile by remember {
        mutableStateOf(ModelDownloader.getModelFile(filesDir))
    }
    var activeModelLabel by rememberSaveable {
        mutableStateOf("banglatts-eleven-new (Default VITS)")
    }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableIntStateOf(0) }
    var downloadedBytes by remember {
        mutableLongStateOf(ModelDownloader.getPartialDownloadedBytes(filesDir))
    }
    var downloadErrorBn by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    // 3. Engine status & initialization state
    var isEngineInitialized by remember { mutableStateOf(false) }
    var engineStatusMessageBn by remember {
        mutableStateOf("মডেল পরীক্ষা করা হচ্ছে...")
    }

    // 4. Text input & speed state
    var inputText by rememberSaveable {
        mutableStateOf(
            "বাংলা টেক্সট টু স্পিচ স্টুডিওতে আপনাকে স্বাগতম। এখানে যেকোনো বাংলা বাক্য লিখুন এবং অডিও তৈরি করুন। প্রথমবার ডাউনলোডের পর এটি সম্পূর্ণ অফলাইনে কাজ করে।"
        )
    }
    val speedOptions = remember { listOf(0.75f, 1.0f, 1.25f, 1.5f) }
    var selectedSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    // 5. Custom VITS & Engine parameters
    var showCustomSettings by rememberSaveable { mutableStateOf(false) }
    var showModelAndApiPanel by rememberSaveable { mutableStateOf(false) }
    var noiseScale by rememberSaveable { mutableFloatStateOf(0.667f) }
    var lengthScale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var noiseScaleW by rememberSaveable { mutableFloatStateOf(1.0f) }
    var numThreads by rememberSaveable { mutableIntStateOf(4) }
    var interSentencePauseMs by rememberSaveable { mutableIntStateOf(180) }

    // Optional API / Cloud Webhook URL for easy external integration
    var apiEndpointUrl by rememberSaveable { mutableStateOf("") }
    var isSyncingApi by remember { mutableStateOf(false) }

    // 6. Real-time generation progress state
    var isGenerating by remember { mutableStateOf(false) }
    var generationPercent by remember { mutableIntStateOf(0) }
    var currentPieceIdx by remember { mutableIntStateOf(0) }
    var totalPieceCount by remember { mutableIntStateOf(0) }
    var currentPiecePreview by remember { mutableStateOf("") }
    var generationErrorBn by remember { mutableStateOf<String?>(null) }

    // 7. Generated WAV output & MediaPlayer state
    var currentWavFile by remember { mutableStateOf<File?>(null) }
    var currentWavDurationSec by remember { mutableFloatStateOf(0f) }
    var currentWavText by rememberSaveable { mutableStateOf("") }
    var playbackState by remember { mutableStateOf(PlaybackState.IDLE) }
    var playbackPositionMs by remember { mutableIntStateOf(0) }
    var playbackDurationMs by remember { mutableIntStateOf(0) }
    val mediaPlayerHolder = remember { mutableStateOf<MediaPlayer?>(null) }

    // 8. Local History of last 8 generations
    var historyList by remember {
        mutableStateOf(HistoryStorage.loadHistory(filesDir))
    }

    fun stopAndReleasePlayer() {
        try {
            mediaPlayerHolder.value?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayerHolder.value?.release()
        } catch (_: Exception) {
        }
        mediaPlayerHolder.value = null
        playbackState = PlaybackState.IDLE
        playbackPositionMs = 0
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndReleasePlayer()
        }
    }

    // Poll MediaPlayer position while playing
    LaunchedEffect(playbackState) {
        while (isActive && playbackState == PlaybackState.PLAYING) {
            val mp = mediaPlayerHolder.value
            if (mp != null) {
                try {
                    if (mp.isPlaying) {
                        playbackPositionMs = mp.currentPosition
                        playbackDurationMs = mp.duration.coerceAtLeast(1)
                    }
                } catch (_: Exception) {
                }
            }
            delay(150L)
        }
    }

    fun startDownload() {
        if (isDownloading) return
        downloadErrorBn = null
        isDownloading = true
        downloadJob = coroutineScope.launch {
            val outcome = ModelDownloader.downloadAndVerifyModel(
                context = context,
                filesDir = filesDir,
                onProgress = { progress ->
                    downloadedBytes = progress.downloadedBytes
                    downloadPercent = progress.percent
                }
            )
            isDownloading = false
            when (outcome) {
                is ModelDownloader.DownloadOutcome.Success -> {
                    activeModelFile = outcome.modelFile
                    activeModelLabel = "banglatts-eleven-new (১১৪ MB যাচাইকৃত)"
                    isModelReady = true
                    downloadErrorBn = null
                    engineStatusMessageBn = "মডেল ডাউনলোড ও যাচাই সম্পন্ন! ভয়েস ইঞ্জিন লোড হচ্ছে..."
                    val tokenFile = File(filesDir, "tokens.txt")
                    val initRes = ttsEngineManager.ensureInitialized(
                        modelFile = outcome.modelFile,
                        tokensFile = tokenFile,
                        params = TtsEngineManager.EngineParams(
                            noiseScale = noiseScale,
                            lengthScale = lengthScale,
                            noiseScaleW = noiseScaleW,
                            numThreads = numThreads,
                            interSentencePauseMs = interSentencePauseMs
                        )
                    )
                    isEngineInitialized = initRes.isSuccess
                    engineStatusMessageBn = if (initRes.isSuccess) {
                        "মডেল প্রস্তুত • অফলাইন সক্রিয় (${initRes.getOrDefault(22050)} Hz)"
                    } else {
                        initRes.exceptionOrNull()?.localizedMessage ?: "ইঞ্জিন লোড ত্রুটি"
                    }
                }

                is ModelDownloader.DownloadOutcome.Error -> {
                    isModelReady = false
                    downloadedBytes = outcome.currentBytes
                    downloadPercent =
                        ((outcome.currentBytes * 100L) / ModelDownloader.EXPECTED_MODEL_SIZE_BYTES)
                            .toInt()
                            .coerceIn(0, 99)
                    downloadErrorBn = outcome.messageBn
                    engineStatusMessageBn = "মডেল অনুপস্থিত • ডাউনলোড প্রয়োজন"
                }
            }
        }
    }

    // Startup verification of tokens.txt (84 lines) and model.onnx (114,160,150 bytes)
    LaunchedEffect(Unit) {
        val validation = withContext(Dispatchers.IO) {
            TokenTable.writeAndVerifyTokensFile(filesDir)
        }
        tokenValidation = validation
        if (!validation.isValid) {
            engineStatusMessageBn = validation.errorMessageBn ?: "টোকেন টেবিল ত্রুটি!"
            return@LaunchedEffect
        }

        val ready = withContext(Dispatchers.IO) {
            ModelDownloader.isDefaultModelReady(filesDir)
        }
        isModelReady = ready
        if (ready) {
            downloadedBytes = ModelDownloader.EXPECTED_MODEL_SIZE_BYTES
            downloadPercent = 100
            engineStatusMessageBn = "মডেল যাচাইকৃত (১১৪,১৬০,১৫০ বাইট)। ইঞ্জিন প্রস্তুত হচ্ছে..."
            val initRes = ttsEngineManager.ensureInitialized(
                modelFile = activeModelFile,
                tokensFile = File(filesDir, "tokens.txt"),
                params = TtsEngineManager.EngineParams(
                    noiseScale = noiseScale,
                    lengthScale = lengthScale,
                    noiseScaleW = noiseScaleW,
                    numThreads = numThreads,
                    interSentencePauseMs = interSentencePauseMs
                )
            )
            isEngineInitialized = initRes.isSuccess
            engineStatusMessageBn = if (initRes.isSuccess) {
                "মডেল প্রস্তুত • অফলাইন সক্রিয় (${initRes.getOrDefault(22050)} Hz)"
            } else {
                initRes.exceptionOrNull()?.localizedMessage ?: "ইঞ্জিন লোড ব্যর্থ"
            }

            // Load most recent history item into player preview if available
            val existingHistory = HistoryStorage.loadHistory(filesDir)
            historyList = existingHistory
            if (existingHistory.isNotEmpty() && currentWavFile == null) {
                val latest = existingHistory.first()
                if (latest.wavFile.exists()) {
                    currentWavFile = latest.wavFile
                    currentWavDurationSec = latest.durationSeconds
                    currentWavText = latest.text
                }
            }
        } else {
            val partial = ModelDownloader.getPartialDownloadedBytes(filesDir)
            downloadedBytes = partial
            downloadPercent =
                ((partial * 100L) / ModelDownloader.EXPECTED_MODEL_SIZE_BYTES).toInt().coerceIn(0, 99)
            engineStatusMessageBn = "মডেল অনুপস্থিত • প্রথমবার ব্যবহারের জন্য ডাউনলোড প্রয়োজন"
        }
    }

    // SAF launcher for "txt ফাইল থেকে নিন" (zero storage permission)
    val txtFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val loadedText = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (!loadedText.isNullOrBlank()) {
                    inputText = loadedText.trim()
                    snackbarHostState.showSnackbar("txt ফাইল থেকে বাংলা টেক্সট লোড হয়েছে (${loadedText.length} অক্ষর)")
                } else {
                    snackbarHostState.showSnackbar("txt ফাইলটি পড়া যায়নি অথবা ফাইলটি খালি।")
                }
            }
        }
    }

    // SAF launcher for exporting WAV to any user-chosen folder or Cloud Drive
    val exportWavSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        val wavFile = currentWavFile
        if (uri != null && wavFile != null) {
            coroutineScope.launch {
                val res = withContext(Dispatchers.IO) {
                    WavWriter.exportWavToUri(context, wavFile, uri)
                }
                snackbarHostState.showSnackbar(
                    res.getOrElse { "এক্সপোর্ট ব্যর্থ: ${it.localizedMessage}" }
                )
            }
        }
    }

    // SAF launcher for loading a custom/local .onnx model file
    val customModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                engineStatusMessageBn = "নির্বাচিত মডেল ফাইল কপি ও যাচাই করা হচ্ছে..."
                val copyResult = withContext(Dispatchers.IO) {
                    try {
                        val destFile = File(filesDir, "custom_loaded_model.onnx")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output, 64 * 1024)
                            }
                        }
                        if (destFile.exists() && destFile.length() > 1024 * 100) {
                            // If it's the exact 114,160,150-byte default model, also copy to model.onnx
                            if (destFile.length() == ModelDownloader.EXPECTED_MODEL_SIZE_BYTES) {
                                val primaryModel = ModelDownloader.getModelFile(filesDir)
                                destFile.copyTo(primaryModel, overwrite = true)
                                destFile.delete()
                                Result.success(primaryModel to "banglatts-eleven-new (১১৪ MB যাচাইকৃত)")
                            } else {
                                Result.success(destFile to "কাস্টম মডেল (${ModelDownloader.formatMb(destFile.length())})")
                            }
                        } else {
                            destFile.delete()
                            Result.failure(IllegalArgumentException("নির্বাচিত ফাইলটি বৈধ ONNX মডেল নয়।"))
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                copyResult.onSuccess { (file, label) ->
                    activeModelFile = file
                    activeModelLabel = label
                    isModelReady = true
                    val initRes = ttsEngineManager.ensureInitialized(
                        modelFile = file,
                        tokensFile = File(filesDir, "tokens.txt"),
                        params = TtsEngineManager.EngineParams(
                            noiseScale = noiseScale,
                            lengthScale = lengthScale,
                            noiseScaleW = noiseScaleW,
                            numThreads = numThreads,
                            interSentencePauseMs = interSentencePauseMs
                        )
                    )
                    isEngineInitialized = initRes.isSuccess
                    engineStatusMessageBn = if (initRes.isSuccess) {
                        "মডেল প্রস্তুত ($label • ${initRes.getOrDefault(22050)} Hz)"
                    } else {
                        "কাস্টম মডেল লোড ব্যর্থ: ${initRes.exceptionOrNull()?.localizedMessage}"
                    }
                    snackbarHostState.showSnackbar("মডেল সফলভাবে লোড হয়েছে: $label")
                }.onFailure { err ->
                    snackbarHostState.showSnackbar("মডেল লোড ব্যর্থ: ${err.localizedMessage}")
                }
            }
        }
    }

    fun playWavFile(wavFile: File, labelText: String, durationSec: Float) {
        if (!wavFile.exists()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("অডিও ফাইলটি পাওয়া যায়নি।")
            }
            return
        }
        stopAndReleasePlayer()
        currentWavFile = wavFile
        currentWavText = labelText
        currentWavDurationSec = durationSec
        try {
            val mp = MediaPlayer().apply {
                setDataSource(wavFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    playbackState = PlaybackState.IDLE
                    playbackPositionMs = playbackDurationMs
                }
            }
            playbackDurationMs = mp.duration.coerceAtLeast(1)
            playbackPositionMs = 0
            mp.start()
            mediaPlayerHolder.value = mp
            playbackState = PlaybackState.PLAYING
        } catch (e: Exception) {
            playbackState = PlaybackState.IDLE
            coroutineScope.launch {
                snackbarHostState.showSnackbar("অডিও চালাতে সমস্যা হয়েছে: ${e.localizedMessage}")
            }
        }
    }

    fun pausePlayback() {
        val mp = mediaPlayerHolder.value ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                playbackPositionMs = mp.currentPosition
                playbackState = PlaybackState.PAUSED
            }
        } catch (_: Exception) {
        }
    }

    fun resumePlayback() {
        val mp = mediaPlayerHolder.value
        if (mp != null && playbackState == PlaybackState.PAUSED) {
            try {
                mp.start()
                playbackState = PlaybackState.PLAYING
            } catch (_: Exception) {
            }
        } else {
            val file = currentWavFile
            if (file != null) {
                playWavFile(file, currentWavText, currentWavDurationSec)
            }
        }
    }

    fun triggerGenerateAudio() {
        val validation = tokenValidation
        if (validation == null || !validation.isValid) {
            generationErrorBn = validation?.errorMessageBn
                ?: "টোকেন ফাইল (৮৪ লাইন) যাচাই ব্যর্থ হয়েছে। অডিও তৈরি করা যাবে না।"
            return
        }
        val cleanText = inputText.trim()
        if (cleanText.isEmpty()) {
            generationErrorBn = "অনুগ্রহ করে প্রথমে বাংলা টেক্সট লিখুন বা পেস্ট করুন।"
            return
        }
        if (isGenerating) return

        generationErrorBn = null
        stopAndReleasePlayer()
        isGenerating = true
        generationPercent = 0
        currentPieceIdx = 0
        val piecesPreview = ttsEngineManager.splitIntoSentencePieces(cleanText)
        totalPieceCount = piecesPreview.size.coerceAtLeast(1)
        engineStatusMessageBn = "অডিও প্রসেসিং চলছে (০%)..."

        coroutineScope.launch {
            val historyDir = HistoryStorage.getHistoryDir(filesDir)
            val timestamp = System.currentTimeMillis()
            val outFile = File(historyDir, "banglatts_$timestamp.wav")
            val tokenFile = File(filesDir, "tokens.txt")

            val params = TtsEngineManager.EngineParams(
                noiseScale = noiseScale,
                lengthScale = lengthScale,
                noiseScaleW = noiseScaleW,
                numThreads = numThreads,
                debug = false,
                interSentencePauseMs = interSentencePauseMs
            )

            val result = ttsEngineManager.synthesizeToWav(
                text = cleanText,
                speed = selectedSpeed,
                modelFile = activeModelFile,
                tokensFile = tokenFile,
                outputWavFile = outFile,
                params = params,
                onProgress = { progress ->
                    currentPieceIdx = progress.currentPieceIndex
                    totalPieceCount = progress.totalPieces
                    generationPercent = progress.percent
                    currentPiecePreview = progress.currentPiecePreview
                    engineStatusMessageBn =
                        "প্রসেসিং চলছে: বাক্য ${progress.currentPieceIndex}/${progress.totalPieces} (${progress.percent}%)"
                }
            )

            isGenerating = false
            result.onSuccess { output ->
                generationPercent = 100
                currentWavFile = output.wavFile
                currentWavDurationSec = output.durationSeconds
                currentWavText = cleanText
                engineStatusMessageBn =
                    "মডেল প্রস্তুত • সর্বশেষ অডিও: ${String.format(Locale.US, "%.1f", output.durationSeconds)} সেকেন্ড (${output.sampleRate} Hz)"

                historyList = withContext(Dispatchers.IO) {
                    HistoryStorage.addGeneration(
                        filesDir = filesDir,
                        text = cleanText,
                        speed = selectedSpeed,
                        durationSeconds = output.durationSeconds,
                        wavFile = output.wavFile
                    )
                }

                // Automatically play the newly synthesized Bengali audio
                playWavFile(output.wavFile, cleanText, output.durationSeconds)
            }.onFailure { err ->
                generationErrorBn = err.localizedMessage ?: "অডিও জেনারেশন ব্যর্থ হয়েছে।"
                engineStatusMessageBn = "অডিও জেনারেশন ত্রুটি • পুনরায় চেষ্টা করুন"
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header & Real-Time Model Status Bar (Always visible)
                HeaderAndStatusBanner(
                    isModelReady = isModelReady,
                    tokenValidation = tokenValidation,
                    statusLineBn = engineStatusMessageBn,
                    activeModelLabel = activeModelLabel,
                    historyCount = historyList.size
                )

                // Token validation error banner if tokens.txt != 84 lines
                if (tokenValidation != null && tokenValidation?.isValid == false) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("token_error_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "টোকেন ত্রুটি",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = tokenValidation?.errorMessageBn
                                    ?: "টোকেন টেবিল যাচাই ব্যর্থ হয়েছে (৮৪ লাইন আবশ্যক)।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Conditional Screen: First-Run Download Screen vs Main Studio Screen
                if (!isModelReady) {
                    FirstRunDownloadCard(
                        isDownloading = isDownloading,
                        downloadPercent = downloadPercent,
                        downloadedBytes = downloadedBytes,
                        totalBytes = ModelDownloader.EXPECTED_MODEL_SIZE_BYTES,
                        errorMessageBn = downloadErrorBn,
                        tokenLineCount = tokenValidation?.lineCount ?: 0,
                        onStartDownload = { startDownload() },
                        onRetryDownload = { startDownload() },
                        onPickLocalModelFile = {
                            customModelPickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                } else {
                    // MAIN STUDIO INTERFACE (100% Offline Ready)
                    val sentencePieces = remember(inputText) {
                        ttsEngineManager.splitIntoSentencePieces(inputText)
                    }

                    // 1. Text Input + Paste + "txt ফাইল থেকে নিন"
                    TextInputSectionCard(
                        inputText = inputText,
                        onInputTextChange = { inputText = it },
                        sentencePieceCount = sentencePieces.size,
                        onPasteClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText =
                                clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!clipText.isNullOrBlank()) {
                                inputText = clipText.trim()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("ক্লিপবোর্ড থেকে টেক্সট পেস্ট করা হয়েছে")
                                }
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("ক্লিপবোর্ডে কোনো টেক্সট পাওয়া যায়নি")
                                }
                            }
                        },
                        onPickTxtFileClick = {
                            txtFilePickerLauncher.launch(arrayOf("text/plain", "text/*"))
                        },
                        onClearClick = { inputText = "" },
                        onSamplePromptClick = { sample -> inputText = sample }
                    )

                    // 2. Speed Selector (0.75x / 1x / 1.25x / 1.5x) & Generate Button + Real Progress
                    SpeedAndGenerateCard(
                        speedOptions = speedOptions,
                        selectedSpeed = selectedSpeed,
                        onSpeedSelected = { selectedSpeed = it },
                        isGenerating = isGenerating,
                        generationPercent = generationPercent,
                        currentPieceIdx = currentPieceIdx,
                        totalPieceCount = totalPieceCount,
                        currentPiecePreview = currentPiecePreview,
                        generationErrorBn = generationErrorBn,
                        onGenerateClick = { triggerGenerateAudio() }
                    )

                    // 3. Play / Pause / Resume + Save (MediaStore / SAF) + Share Card
                    AudioPlayerAndExportCard(
                        wavFile = currentWavFile,
                        wavDurationSec = currentWavDurationSec,
                        wavTextSnippet = currentWavText,
                        playbackState = playbackState,
                        playbackPositionMs = playbackPositionMs,
                        playbackDurationMs = playbackDurationMs,
                        onPlayClick = {
                            val file = currentWavFile
                            if (file != null) {
                                playWavFile(file, currentWavText, currentWavDurationSec)
                            }
                        },
                        onPauseClick = { pausePlayback() },
                        onResumeClick = { resumePlayback() },
                        onStopClick = { stopAndReleasePlayer() },
                        onSaveMediaStoreClick = {
                            val file = currentWavFile
                            if (file != null) {
                                coroutineScope.launch {
                                    val displayName =
                                        "BanglaTTS_${System.currentTimeMillis() / 1000}.wav"
                                    val saveRes = withContext(Dispatchers.IO) {
                                        WavWriter.saveWavToMediaStore(context, file, displayName)
                                    }
                                    snackbarHostState.showSnackbar(
                                        saveRes.getOrElse {
                                            "সেভ করতে ত্রুটি: ${it.localizedMessage}"
                                        }
                                    )
                                }
                            }
                        },
                        onExportSafClick = {
                            val defaultName =
                                "BanglaTTS_${System.currentTimeMillis() / 1000}.wav"
                            exportWavSafLauncher.launch(defaultName)
                        },
                        onShareClick = {
                            val file = currentWavFile
                            if (file != null) {
                                val snippet = currentWavText.take(40).ifBlank { "বাংলা অডিও" }
                                val res = WavWriter.shareWavFile(context, file, snippet)
                                if (res.isFailure) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "শেয়ার ত্রুটি: ${res.exceptionOrNull()?.localizedMessage}"
                                        )
                                    }
                                }
                            }
                        }
                    )

                    // 4. Custom Audio Settings & Model/API Manager Panels
                    CustomAudioSettingsCard(
                        expanded = showCustomSettings,
                        onToggleExpand = { showCustomSettings = !showCustomSettings },
                        noiseScale = noiseScale,
                        onNoiseScaleChange = { noiseScale = it },
                        lengthScale = lengthScale,
                        onLengthScaleChange = { lengthScale = it },
                        noiseScaleW = noiseScaleW,
                        onNoiseScaleWChange = { noiseScaleW = it },
                        numThreads = numThreads,
                        onNumThreadsChange = { numThreads = it },
                        interSentencePauseMs = interSentencePauseMs,
                        onInterSentencePauseChange = { interSentencePauseMs = it },
                        onResetDefaults = {
                            noiseScale = 0.667f
                            lengthScale = 1.0f
                            noiseScaleW = 1.0f
                            numThreads = 4
                            interSentencePauseMs = 180
                        }
                    )

                    ModelAndApiIntegrationCard(
                        expanded = showModelAndApiPanel,
                        onToggleExpand = { showModelAndApiPanel = !showModelAndApiPanel },
                        activeModelLabel = activeModelLabel,
                        activeModelPath = activeModelFile.absolutePath,
                        onLoadCustomModelClick = {
                            customModelPickerLauncher.launch(arrayOf("*/*"))
                        },
                        onRestoreDefaultModelClick = {
                            val defaultFile = ModelDownloader.getModelFile(filesDir)
                            if (ModelDownloader.isDefaultModelReady(filesDir)) {
                                activeModelFile = defaultFile
                                activeModelLabel = "banglatts-eleven-new (১১৪ MB যাচাইকৃত)"
                                coroutineScope.launch {
                                    ttsEngineManager.ensureInitialized(
                                        modelFile = defaultFile,
                                        tokensFile = File(filesDir, "tokens.txt")
                                    )
                                    snackbarHostState.showSnackbar("ডিফল্ট ভয়েস মডেলে ফিরে যাওয়া হয়েছে")
                                }
                            } else {
                                isModelReady = false
                            }
                        },
                        apiEndpointUrl = apiEndpointUrl,
                        onApiEndpointChange = { apiEndpointUrl = it },
                        isSyncingApi = isSyncingApi,
                        hasGeneratedWav = currentWavFile?.exists() == true,
                        onSyncToApiClick = {
                            val wav = currentWavFile
                            val url = apiEndpointUrl.trim()
                            if (wav == null || !wav.exists()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("সিঙ্ক করার জন্য প্রথমে অডিও তৈরি করুন।")
                                }
                            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("বৈধ এপিআই URL দিন (https://...)")
                                }
                            } else {
                                isSyncingApi = true
                                coroutineScope.launch {
                                    val syncRes = withContext(Dispatchers.IO) {
                                        uploadWavToCustomApi(url, wav, currentWavText, selectedSpeed)
                                    }
                                    isSyncingApi = false
                                    snackbarHostState.showSnackbar(syncRes)
                                }
                            }
                        }
                    )

                    // 5. History of Last 8 Generations (Local Only)
                    HistorySectionCard(
                        historyItems = historyList,
                        currentlyPlayingFile = currentWavFile,
                        playbackState = playbackState,
                        onPlayHistoryItem = { item ->
                            playWavFile(item.wavFile, item.text, item.durationSeconds)
                        },
                        onUseTextFromHistory = { item ->
                            inputText = item.text
                            selectedSpeed = item.speed
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("হিস্ট্রি থেকে টেক্সট ইনপুট বক্সে নেওয়া হয়েছে")
                            }
                        },
                        onSaveHistoryItem = { item ->
                            coroutineScope.launch {
                                val name = "BanglaTTS_${item.id}.wav"
                                val res = withContext(Dispatchers.IO) {
                                    WavWriter.saveWavToMediaStore(context, item.wavFile, name)
                                }
                                snackbarHostState.showSnackbar(
                                    res.getOrElse { "সেভ ত্রুটি: ${it.localizedMessage}" }
                                )
                            }
                        },
                        onShareHistoryItem = { item ->
                            WavWriter.shareWavFile(context, item.wavFile, item.text.take(36))
                        },
                        onDeleteHistoryItem = { item ->
                            if (currentWavFile?.absolutePath == item.wavFilePath) {
                                stopAndReleasePlayer()
                                currentWavFile = null
                            }
                            historyList = HistoryStorage.deleteHistoryItem(filesDir, item.id)
                        },
                        onClearAllHistory = {
                            stopAndReleasePlayer()
                            currentWavFile = null
                            historyList = HistoryStorage.clearAllHistory(filesDir)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HeaderAndStatusBanner(
    isModelReady: Boolean,
    tokenValidation: TokenTable.ValidationResult?,
    statusLineBn: String,
    activeModelLabel: String,
    historyCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_status_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "বাংলা টিটিএস আইকন",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "বাংলা কণ্ঠস্বর স্টুডিও (BanglaTTS)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "sherpa-onnx 2.0.1 • একক কণ্ঠস্বর বাংলা VITS • 22050 Hz Mono WAV",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Indicator Line (Required #6: small status line showing whether model is ready or still missing)
            Surface(
                color = if (isModelReady) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_status_line")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isModelReady) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = if (isModelReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = statusLineBn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Quick Dashboard Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardStatBadge(
                    label = "টোকেন টেবিল",
                    value = if (tokenValidation?.isValid == true) "৮৪ লাইন যাচাইকৃত" else "ত্রুটি",
                    modifier = Modifier.weight(1f)
                )
                DashboardStatBadge(
                    label = "সক্রিয় মডেল",
                    value = if (isModelReady) "প্রস্তুত (অফলাইন)" else "অনুপস্থিত",
                    modifier = Modifier.weight(1f)
                )
                DashboardStatBadge(
                    label = "হিস্ট্রি",
                    value = "$historyCount / ৮টি",
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun DashboardStatBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FirstRunDownloadCard(
    isDownloading: Boolean,
    downloadPercent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    errorMessageBn: String?,
    tokenLineCount: Int,
    onStartDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onPickLocalModelFile: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("first_run_download_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "প্রথমবার ভয়েস মডেল ডাউনলোড",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "অ্যাপটির সাইজ ছোট রাখার জন্য ১১৪ মেগাবাইটের বাংলা VITS ভয়েস মডেলটি (model.onnx) শুধুমাত্র প্রথমবার ইন্টারনেট থেকে ডাউনলোড হবে। ডাউনলোড অসমাপ্ত থাকলে পুনরায় চেষ্টা করলে ঠিক যেখানে থেমেছিল সেখান থেকেই (HTTP Range Resume) শুরু হবে। একবার ডাউনলোড হলে অ্যাপটি আজীবন সম্পূর্ণ অফলাইনে কাজ করবে।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "• মডেল ফাইল: filesDir/model.onnx (১১৪,১৬০,১৫০ বাইট)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• টোকেন ফাইল: filesDir/tokens.txt ($tokenLineCount লাইন প্রস্তুত)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• উৎস: huggingface.co/demoqqq/banglatts-eleven-new",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Plain ProgressBar + Plain Percent Text
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDownloading) {
                            "মডেল ডাউনলোড হচ্ছে..."
                        } else if (downloadedBytes > 0L) {
                            "আংশিক ডাউনলোড সংরক্ষিত আছে (রিজিউম প্রস্তুত)"
                        } else {
                            "ডাউনলোডের জন্য প্রস্তুত"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$downloadPercent%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("download_percent_text")
                    )
                }

                LinearProgressIndicator(
                    progress = { (downloadPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .testTag("download_progress_bar"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "${ModelDownloader.formatMb(downloadedBytes)} / ${ModelDownloader.formatMb(totalBytes)} ($downloadedBytes / $totalBytes bytes)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!errorMessageBn.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "ডাউনলোড ত্রুটি",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = errorMessageBn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Download and Retry buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartDownload,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("download_model_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (downloadedBytes > 0L) "ডাউনলোড চালিয়ে যান" else "মডেল ডাউনলোড করুন",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onRetryDownload,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("retry_download_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "পুনরায় চেষ্টা করুন",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "পুনরায় চেষ্টা করুন",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            OutlinedButton(
                onClick = onPickLocalModelFile,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pick_local_model_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ডিভাইসে থাকা model.onnx ফাইল থেকে সরাসরি লোড করুন",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextInputSectionCard(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    sentencePieceCount: Int,
    onPasteClick: () -> Unit,
    onPickTxtFileClick: () -> Unit,
    onClearClick: () -> Unit,
    onSamplePromptClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "বাংলা টেক্সট ইনপুট",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "বাক্য খণ্ড: ${sentencePieceCount}টি • অক্ষর: ${inputText.length}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                placeholder = {
                    Text("এখানে বাংলা টেক্সট লিখুন বা পেস্ট করুন...")
                },
                minLines = 5,
                maxLines = 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bengali_text_input")
            )

            // Paste + "txt ফাইল থেকে নিন" + Clear buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPasteClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("paste_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "পেস্ট করুন",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onPickTxtFileClick,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp)
                        .testTag("pick_txt_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "txt ফাইল থেকে নিন",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("clear_text_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "টেক্সট মুছুন",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Sample Bengali Texts
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "দ্রুত পরীক্ষার নমুনা বাক্য:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SAMPLE_BENGALI_PROMPTS.forEach { sample ->
                        FilterChip(
                            selected = inputText == sample.text,
                            onClick = { onSamplePromptClick(sample.text) },
                            label = {
                                Text(
                                    text = sample.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedAndGenerateCard(
    speedOptions: List<Float>,
    selectedSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    isGenerating: Boolean,
    generationPercent: Int,
    currentPieceIdx: Int,
    totalPieceCount: Int,
    currentPiecePreview: String,
    generationErrorBn: String?,
    onGenerateClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "কণ্ঠের গতি (Speed Selector)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speedOptions.forEach { speed ->
                    val label = if (speed == 1.0f) "1x" else "${speed}x"
                    val tagSuffix = speed.toString().replace('.', '_')
                    val isSelected = selectedSpeed == speed
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSpeedSelected(speed) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("speed_$tagSuffix")
                    )
                }
            }

            // Generate Button
            Button(
                onClick = onGenerateClick,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("generate_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isGenerating) {
                        "অডিও তৈরি হচ্ছে ($generationPercent%)..."
                    } else {
                        "অডিও তৈরি করুন (Generate WAV)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Real-time sentence-piece progress bar + plain percent text
            if (isGenerating || generationPercent > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isGenerating) {
                                "বাক্য খণ্ড প্রসেসিং: $currentPieceIdx / $totalPieceCount"
                            } else {
                                "সর্বশেষ জেনারেশন সম্পন্ন ($totalPieceCount বাক্য খণ্ড)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$generationPercent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("generate_percent_text")
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (generationPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .testTag("generate_progress_bar"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    if (isGenerating && currentPiecePreview.isNotBlank()) {
                        Text(
                            text = "চলমান বাক্য: \"$currentPiecePreview...\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!generationErrorBn.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = generationErrorBn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerAndExportCard(
    wavFile: File?,
    wavDurationSec: Float,
    wavTextSnippet: String,
    playbackState: PlaybackState,
    playbackPositionMs: Int,
    playbackDurationMs: Int,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onSaveMediaStoreClick: () -> Unit,
    onExportSafClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val hasWav = wavFile != null && wavFile.exists()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_player_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "অডিও প্লেয়ার ও এক্সপোর্ট (22050 Hz WAV)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (hasWav) {
                    Text(
                        text = String.format(Locale.US, "%.1f সেকেন্ড", wavDurationSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!hasWav) {
                Text(
                    text = "এখনো কোনো অডিও তৈরি হয়নি। উপরের 'অডিও তৈরি করুন' বাটনে চাপ দিলে এখানে Play / Pause / Resume, Save এবং Share অপশন সক্রিয় হবে।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (wavTextSnippet.isNotBlank()) {
                    Text(
                        text = "\"$wavTextSnippet\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Playback progress bar
                val playProgress = if (playbackDurationMs > 0) {
                    (playbackPositionMs.toFloat() / playbackDurationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { playProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Play / Pause / Resume / Stop Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("play_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "চালান",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "চালান", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onPauseClick,
                        enabled = playbackState == PlaybackState.PLAYING,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "বিরতি",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "বিরতি", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onResumeClick,
                        enabled = playbackState == PlaybackState.PAUSED,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(48.dp)
                            .testTag("resume_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "পুনরায় চালান",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "পুনরায় চালান", style = MaterialTheme.typography.labelLarge)
                    }

                    IconButton(
                        onClick = onStopClick,
                        enabled = playbackState != PlaybackState.IDLE,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "থামান"
                        )
                    }
                }

                // Save (MediaStore) + Export (SAF/Cloud) + Share (System Share Sheet)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveMediaStoreClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_wav_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "সেভ (WAV)", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onExportSafClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_saf_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "এক্সপোর্ট", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = onShareClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_wav_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "শেয়ার করুন", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomAudioSettingsCard(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    noiseScale: Float,
    onNoiseScaleChange: (Float) -> Unit,
    lengthScale: Float,
    onLengthScaleChange: (Float) -> Unit,
    noiseScaleW: Float,
    onNoiseScaleWChange: (Float) -> Unit,
    numThreads: Int,
    onNumThreadsChange: (Int) -> Unit,
    interSentencePauseMs: Int,
    onInterSentencePauseChange: (Int) -> Unit,
    onResetDefaults: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .testTag("toggle_custom_settings_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            text = "কাস্টম অডিও ও VITS ইঞ্জিন সেটিংস",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Scales: [${String.format(Locale.US, "%.3f", noiseScale)}, ${String.format(Locale.US, "%.2f", lengthScale)}, ${String.format(Locale.US, "%.2f", noiseScaleW)}] • Threads: $numThreads",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "বিস্তারিত দেখুন"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Noise Scale (কণ্ঠের প্রাকৃতিক বৈচিত্র্য): ${String.format(Locale.US, "%.3f", noiseScale)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = noiseScale,
                    onValueChange = onNoiseScaleChange,
                    valueRange = 0.3f..1.2f
                )

                Text(
                    text = "Length Scale (বেস উচ্চারণ দৈর্ঘ্য): ${String.format(Locale.US, "%.2f", lengthScale)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = lengthScale,
                    onValueChange = onLengthScaleChange,
                    valueRange = 0.7f..1.4f
                )

                Text(
                    text = "Noise Scale W (ছন্দ ও ডিউরেশন বৈচিত্র্য): ${String.format(Locale.US, "%.2f", noiseScaleW)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = noiseScaleW,
                    onValueChange = onNoiseScaleWChange,
                    valueRange = 0.4f..1.4f
                )

                Text(
                    text = "বাক্যের মাঝখানে বিরতি (Inter-sentence Pause): $interSentencePauseMs ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = interSentencePauseMs.toFloat(),
                    onValueChange = { onInterSentencePauseChange(it.toInt()) },
                    valueRange = 0f..600f
                )

                Text(
                    text = "CPU Threads (শেরপা-ওএনএনএক্স থ্রেড সংখ্যা): $numThreads",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = numThreads.toFloat(),
                    onValueChange = { onNumThreadsChange(it.toInt().coerceIn(1, 8)) },
                    steps = 6,
                    valueRange = 1f..8f
                )

                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("প্রস্তাবিত ডিফল্টে রিসেট করুন ([0.667, 1.0, 1.0], 4 Threads)")
                }
            }
        }
    }
}

@Composable
private fun ModelAndApiIntegrationCard(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    activeModelLabel: String,
    activeModelPath: String,
    onLoadCustomModelClick: () -> Unit,
    onRestoreDefaultModelClick: () -> Unit,
    apiEndpointUrl: String,
    onApiEndpointChange: (String) -> Unit,
    isSyncingApi: Boolean,
    hasGeneratedWav: Boolean,
    onSyncToApiClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .testTag("toggle_model_api_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column {
                        Text(
                            text = "নতুন মডেল লোড ও এপিআই/ক্লাউড সিঙ্ক",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সক্রিয়: $activeModelLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "বিস্তারিত"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "মডেল পাথ: $activeModelPath",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onLoadCustomModelClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("load_custom_onnx_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নতুন .onnx মডেল লোড")
                    }

                    OutlinedButton(
                        onClick = onRestoreDefaultModelClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ডিফল্ট মডেল")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ওয়েবহুক / কাস্টম এপিআই ইন্টিগ্রেশন (ঐচ্ছিক ক্লাউড সিঙ্ক):",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiEndpointUrl,
                    onValueChange = onApiEndpointChange,
                    placeholder = { Text("https://your-api.example.com/upload-audio") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_endpoint_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onSyncToApiClick,
                    enabled = !isSyncingApi && hasGeneratedWav,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_api_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSyncingApi) "এপিআই সার্ভারে পাঠানো হচ্ছে..." else "সর্বশেষ WAV অডিও এপিআই-তে পাঠান"
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySectionCard(
    historyItems: List<GenerationHistoryItem>,
    currentlyPlayingFile: File?,
    playbackState: PlaybackState,
    onPlayHistoryItem: (GenerationHistoryItem) -> Unit,
    onUseTextFromHistory: (GenerationHistoryItem) -> Unit,
    onSaveHistoryItem: (GenerationHistoryItem) -> Unit,
    onShareHistoryItem: (GenerationHistoryItem) -> Unit,
    onDeleteHistoryItem: (GenerationHistoryItem) -> Unit,
    onClearAllHistory: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_section_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "সাম্প্রতিক ৮টি জেনারেশন (${historyItems.size}/8)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (historyItems.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAllHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Text("সব মুছুন", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (historyItems.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "এখনো কোনো অডিও জেনারেশন হিস্ট্রি নেই। অডিও তৈরি করলে সর্বশেষ ৮টি অডিও এখানে সংরক্ষিত থাকবে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                historyItems.forEachIndexed { index, item ->
                    val isCurrent = currentlyPlayingFile?.absolutePath == item.wavFilePath &&
                        playbackState == PlaybackState.PLAYING

                    Surface(
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_item_$index")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1} • ${item.speed}x • ${String.format(Locale.US, "%.1f", item.durationSeconds)}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.formattedTimestamp,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onUseTextFromHistory(item) }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onPlayHistoryItem(item) },
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "চালান",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("চালান", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = { onUseTextFromHistory(item) },
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text("টেক্সট নিন", style = MaterialTheme.typography.labelMedium)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(
                                    onClick = { onSaveHistoryItem(item) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SaveAlt,
                                        contentDescription = "সেভ করুন",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onShareHistoryItem(item) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "শেয়ার করুন",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteHistoryItem(item) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "মুছুন",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun uploadWavToCustomApi(
    endpointUrl: String,
    wavFile: File,
    text: String,
    speed: Float
): String {
    return try {
        val client = OkHttpClient()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", text)
            .addFormDataPart("speed", speed.toString())
            .addFormDataPart(
                "audio",
                wavFile.name,
                wavFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .build()
        val request = Request.Builder()
            .url(endpointUrl)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                "এপিআই সিঙ্ক সফল হয়েছে (HTTP ${response.code})!"
            } else {
                "এপিআই সার্ভার রেসপন্স: HTTP ${response.code}"
            }
        }
    } catch (e: Exception) {
        "এপিআই সংযোগ ত্রুটি: ${e.localizedMessage ?: "অজানা সমস্যা"}"
    }
}
