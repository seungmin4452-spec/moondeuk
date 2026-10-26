package com.example.moondeuk.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * AudioRecord로 16비트 모노 PCM을 받아 WAV 파일에 바로 쓴다.
 * 메모리에는 100ms 청크 하나만 들고 있고, 최대 길이를 넘기면 스스로 멈춘다.
 * 콜백은 녹음 스레드에서 호출된다.
 */
class PcmRecorder(val sampleRate: Int = 16_000) {

    enum class Failure { INIT_FAILED, START_FAILED, READ_ERROR, IO_ERROR, NO_PERMISSION }

    sealed interface Result {
        data class Saved(
            val file: File,
            val durationMs: Long,
            val stats: PcmStats,
            /** 녹음 중 시스템이 이 앱의 입력을 무음 처리한 적이 있는가 (다른 앱이 마이크를 가져간 경우 등). */
            val silencedBySystem: Boolean,
            val reachedLimit: Boolean,
        ) : Result

        data class Failed(val reason: Failure, val detail: String?) : Result
    }

    interface Listener {
        fun onLevel(chunkPeak: Int, elapsedMs: Long)
        fun onFinished(result: Result)
    }

    @Volatile private var stopRequested = false
    @Volatile private var thread: Thread? = null

    val isRunning: Boolean get() = thread?.isAlive == true

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(output: File, maxDurationMs: Long, listener: Listener) {
        check(!isRunning) { "이미 녹음 중" }
        require(maxDurationMs > 0)
        stopRequested = false
        thread = Thread({ record(output, maxDurationMs, listener) }, "PcmRecorder").also { it.start() }
    }

    fun stop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission") // start()가 권한을 요구한다
    private fun record(output: File, maxDurationMs: Long, listener: Listener) {
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val chunkSamples = sampleRate / 10 // 100ms
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuffer <= 0) {
            listener.onFinished(Result.Failed(Failure.INIT_FAILED, "getMinBufferSize=$minBuffer"))
            return
        }

        val audioRecord = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelMask, encoding, max(minBuffer, chunkSamples * 2 * 4))
        } catch (e: SecurityException) {
            listener.onFinished(Result.Failed(Failure.NO_PERMISSION, e.javaClass.simpleName))
            return
        } catch (e: IllegalArgumentException) {
            listener.onFinished(Result.Failed(Failure.INIT_FAILED, e.javaClass.simpleName))
            return
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            listener.onFinished(Result.Failed(Failure.INIT_FAILED, "state=${audioRecord.state}"))
            return
        }

        val silenced = AtomicBoolean(false)
        val directExecutor = Executor { it.run() }
        val recordingCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                if (configs.any { it.isClientSilenced }) silenced.set(true)
            }
        }
        audioRecord.registerAudioRecordingCallback(directExecutor, recordingCallback)

        val tempFile = File(output.parentFile, output.name + ".part")
        val stats = PcmStats()
        var totalSamples = 0L
        var failure: Result.Failed? = null
        val maxSamples = sampleRate.toLong() * maxDurationMs / 1000

        try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                failure = Result.Failed(Failure.START_FAILED, "recordingState=${audioRecord.recordingState}")
            } else {
                if (audioRecord.activeRecordingConfiguration?.isClientSilenced == true) silenced.set(true)
                RandomAccessFile(tempFile, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(WavFormat.header(sampleRate, 1, 16, 0))
                    val shorts = ShortArray(chunkSamples)
                    val bytes = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
                    while (!stopRequested && totalSamples < maxSamples) {
                        val want = min(chunkSamples.toLong(), maxSamples - totalSamples).toInt()
                        val read = audioRecord.read(shorts, 0, want)
                        if (read < 0) {
                            failure = Result.Failed(Failure.READ_ERROR, "read=$read")
                            break
                        }
                        if (read == 0) continue
                        bytes.clear()
                        for (i in 0 until read) bytes.putShort(shorts[i])
                        raf.write(bytes.array(), 0, read * 2)
                        val chunkPeak = stats.add(shorts, read)
                        totalSamples += read
                        listener.onLevel(chunkPeak, totalSamples * 1000 / sampleRate)
                    }
                    raf.seek(0)
                    raf.write(WavFormat.header(sampleRate, 1, 16, totalSamples * 2))
                }
            }
        } catch (e: IOException) {
            failure = Result.Failed(Failure.IO_ERROR, e.javaClass.simpleName)
        } catch (e: IllegalStateException) {
            failure = Result.Failed(Failure.START_FAILED, e.javaClass.simpleName)
        } finally {
            audioRecord.unregisterAudioRecordingCallback(recordingCallback)
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
            audioRecord.release()
        }

        val result = failure ?: run {
            if (!tempFile.renameTo(output)) {
                // 같은 디렉터리 rename이 실패하면 기존 파일 때문일 수 있어 지우고 한 번 더 시도
                output.delete()
                if (!tempFile.renameTo(output)) return@run Result.Failed(Failure.IO_ERROR, "rename")
            }
            Result.Saved(output, totalSamples * 1000 / sampleRate, stats, silenced.get(), totalSamples >= maxSamples)
        }
        if (result is Result.Failed) tempFile.delete()
        thread = null
        listener.onFinished(result)
    }
}
