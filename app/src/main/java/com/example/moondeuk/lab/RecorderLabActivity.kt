package com.example.moondeuk.lab

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.moondeuk.R
import com.example.moondeuk.audio.PcmRecorder
import com.example.moondeuk.audio.PcmStats
import com.example.moondeuk.audio.WavFormat
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * 1단계 기술 검증용 화면: 마이크 PCM 녹음 → 앱 전용 저장소에 WAV 저장 → 재생 → 삭제.
 * 화면이 보일 때만 녹음한다(onStop에서 중지). 백그라운드 녹음은 별도 실험이다.
 * 로그에는 길이·크기 수치만 남기고 음성 내용은 남기지 않는다.
 */
class RecorderLabActivity : AppCompatActivity() {

    private enum class Mode { IDLE, RECORDING, PLAYING }

    private val recorder = PcmRecorder()
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var mode = Mode.IDLE

    private lateinit var statusText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var levelText: TextView
    private lateinit var resultText: TextView
    private lateinit var recordButton: Button
    private lateinit var stopButton: Button
    private lateinit var playButton: Button
    private lateinit var deleteButton: Button

    private val wavFile: File by lazy { File(File(filesDir, "lab").apply { mkdirs() }, "lab_recording.wav") }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording() else showStatus(getString(R.string.lab_permission_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorder_lab)
        statusText = findViewById(R.id.statusText)
        levelBar = findViewById(R.id.levelBar)
        levelText = findViewById(R.id.levelText)
        resultText = findViewById(R.id.resultText)
        recordButton = findViewById(R.id.recordButton)
        stopButton = findViewById(R.id.stopButton)
        playButton = findViewById(R.id.playButton)
        deleteButton = findViewById(R.id.deleteButton)

        recordButton.setOnClickListener { onRecordClicked() }
        stopButton.setOnClickListener { stopAll() }
        playButton.setOnClickListener { startPlayback() }
        deleteButton.setOnClickListener { deleteRecording() }

        showStatus(getString(R.string.lab_status_ready))
        showSavedFileInfo()
        render()
    }

    override fun onStop() {
        super.onStop()
        // 화면을 벗어나면 녹음·재생을 멈춘다. 숨겨진 녹음을 하지 않는다.
        stopAll()
    }

    private fun onRecordClicked() {
        if (mode != Mode.IDLE) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (mode != Mode.IDLE) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showStatus(getString(R.string.lab_permission_denied))
            return
        }
        mode = Mode.RECORDING
        levelBar.progress = 0
        resultText.text = ""
        showStatus(getString(R.string.lab_status_recording, 0f, MAX_DURATION_MS / 1000))
        render()
        recorder.start(wavFile, MAX_DURATION_MS, object : PcmRecorder.Listener {
            override fun onLevel(chunkPeak: Int, elapsedMs: Long) = runOnUiThread {
                if (mode != Mode.RECORDING) return@runOnUiThread
                val db = PcmStats.toDbfs(chunkPeak.toDouble())
                // -60 dBFS ~ 0 dBFS를 0~100으로 표시. 실제 입력 크기에 연결되어 있다.
                levelBar.progress = if (db.isInfinite()) 0 else ((db + 60) / 60 * 100).toInt().coerceIn(0, 100)
                levelText.text = formatDb(db)
                showStatus(getString(R.string.lab_status_recording, elapsedMs / 1000f, MAX_DURATION_MS / 1000))
            }

            override fun onFinished(result: PcmRecorder.Result) = runOnUiThread { onRecordingFinished(result) }
        })
    }

    private fun onRecordingFinished(result: PcmRecorder.Result) {
        mode = Mode.IDLE
        levelBar.progress = 0
        levelText.text = ""
        when (result) {
            is PcmRecorder.Result.Saved -> {
                val s = result.stats
                Log.i(TAG, "saved durationMs=${result.durationMs} bytes=${result.file.length()} " +
                    "peakDbfs=${fmt(s.peakDbfs)} rmsDbfs=${fmt(s.rmsDbfs)} nonZero=${fmt(s.nonZeroRatio * 100)}% " +
                    "silencedBySystem=${result.silencedBySystem} reachedLimit=${result.reachedLimit}")
                showStatus(getString(R.string.lab_status_saved))
                val lines = mutableListOf(
                    getString(R.string.lab_result_summary, result.durationMs / 1000f, result.file.length() / 1024),
                    getString(R.string.lab_result_levels, formatDb(s.peakDbfs), formatDb(s.rmsDbfs), s.nonZeroRatio * 100),
                )
                lines += getString(if (s.looksSilent) R.string.lab_verdict_silent else R.string.lab_verdict_ok)
                if (result.silencedBySystem) lines += getString(R.string.lab_verdict_silenced)
                resultText.text = lines.joinToString("\n")
            }
            is PcmRecorder.Result.Failed -> {
                Log.w(TAG, "record failed reason=${result.reason} detail=${result.detail}")
                showStatus(getString(R.string.lab_status_failed, result.reason.name, result.detail ?: "-"))
            }
        }
        render()
    }

    private fun startPlayback() {
        if (mode != Mode.IDLE || !wavFile.exists()) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change -> if (change < 0) stopPlayback() }
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            showStatus(getString(R.string.lab_status_focus_denied))
            return
        }
        focusRequest = request
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(wavFile.path)
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "playback error what=$what extra=$extra")
                    stopPlayback()
                    showStatus(getString(R.string.lab_status_play_failed))
                    true
                }
                prepare()
                start()
            }
            mode = Mode.PLAYING
            showStatus(getString(R.string.lab_status_playing))
        } catch (e: IOException) {
            Log.w(TAG, "playback prepare failed ${e.javaClass.simpleName}")
            stopPlayback()
            showStatus(getString(R.string.lab_status_play_failed))
        }
        render()
    }

    private fun stopPlayback() {
        player?.run {
            setOnCompletionListener(null)
            release()
        }
        player = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        if (mode == Mode.PLAYING) {
            mode = Mode.IDLE
            showStatus(getString(R.string.lab_status_ready))
        }
        render()
    }

    private fun stopAll() {
        if (mode == Mode.RECORDING) recorder.stop() // 완료는 onRecordingFinished에서 처리
        stopPlayback()
    }

    private fun deleteRecording() {
        if (mode != Mode.IDLE) return
        val deleted = !wavFile.exists() || wavFile.delete()
        resultText.text = ""
        showStatus(getString(if (deleted) R.string.lab_status_deleted else R.string.lab_status_delete_failed))
        render()
    }

    private fun showSavedFileInfo() {
        if (!wavFile.exists()) return
        val dataBytes = (wavFile.length() - WavFormat.HEADER_SIZE).coerceAtLeast(0)
        val seconds = dataBytes / (recorder.sampleRate * 2f)
        resultText.text = getString(R.string.lab_result_existing, seconds, wavFile.length() / 1024)
    }

    private fun render() {
        val hasFile = wavFile.exists()
        recordButton.isEnabled = mode == Mode.IDLE
        stopButton.isEnabled = mode != Mode.IDLE
        playButton.isEnabled = mode == Mode.IDLE && hasFile
        deleteButton.isEnabled = mode == Mode.IDLE && hasFile
    }

    private fun showStatus(text: String) {
        statusText.text = text
    }

    private fun formatDb(db: Double) = if (db.isInfinite()) "-∞ dBFS" else "${fmt(db)} dBFS"

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    companion object {
        private const val TAG = "RecorderLab"
        private const val MAX_DURATION_MS = 10_000L
    }
}
