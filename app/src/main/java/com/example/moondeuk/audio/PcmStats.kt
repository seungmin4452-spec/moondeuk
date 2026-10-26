package com.example.moondeuk.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 16비트 PCM 입력의 크기 통계. 실제 소리가 들어왔는지(무음/0만 들어왔는지) 판단하는 데 쓴다.
 * 음성 내용은 다루지 않는다.
 */
class PcmStats {
    var samples = 0L
        private set
    var nonZeroSamples = 0L
        private set
    var peak = 0
        private set
    private var sumSquares = 0.0

    /** 청크를 누적하고 그 청크의 최대 절댓값을 돌려준다. */
    fun add(buffer: ShortArray, count: Int): Int {
        require(count in 0..buffer.size)
        var chunkPeak = 0
        for (i in 0 until count) {
            val v = buffer[i].toInt()
            val a = abs(v)
            if (a > chunkPeak) chunkPeak = a
            if (v != 0) nonZeroSamples++
            sumSquares += v.toDouble() * v
        }
        samples += count
        if (chunkPeak > peak) peak = chunkPeak
        return chunkPeak
    }

    val nonZeroRatio: Double get() = if (samples == 0L) 0.0 else nonZeroSamples.toDouble() / samples

    val peakDbfs: Double get() = toDbfs(peak.toDouble())

    val rmsDbfs: Double get() = if (samples == 0L) Double.NEGATIVE_INFINITY else toDbfs(sqrt(sumSquares / samples))

    /** 실제 입력으로 보기 어려운 경우: 샘플이 없거나, 거의 0이거나, 매우 작음. */
    val looksSilent: Boolean get() = samples == 0L || nonZeroRatio < 0.01 || peakDbfs < SILENCE_PEAK_DBFS

    companion object {
        /** 실험값. 에뮬레이터 호스트 마이크 미연결 시 0 또는 극소 노이즈만 들어오는 것을 걸러낸다. */
        const val SILENCE_PEAK_DBFS = -60.0

        fun toDbfs(amplitude: Double): Double =
            if (amplitude <= 0.0) Double.NEGATIVE_INFINITY else 20 * log10(amplitude / 32768.0)
    }
}
