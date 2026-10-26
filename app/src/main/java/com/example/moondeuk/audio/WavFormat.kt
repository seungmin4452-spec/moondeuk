package com.example.moondeuk.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM WAV(RIFF) 44바이트 헤더. */
object WavFormat {
    const val HEADER_SIZE = 44

    fun header(sampleRate: Int, channels: Int, bitsPerSample: Int, dataSize: Long): ByteArray {
        require(sampleRate > 0 && channels > 0 && bitsPerSample % 8 == 0) { "잘못된 오디오 형식" }
        require(dataSize in 0..(0xFFFFFFFFL - 36)) { "WAV 데이터 크기 초과: $dataSize" }
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(ascii("RIFF"))
            putInt((36 + dataSize).toInt()) // 부호 없는 32비트 값으로 기록됨
            put(ascii("WAVE"))
            put(ascii("fmt "))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put(ascii("data"))
            putInt(dataSize.toInt())
        }.array()
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)
}
