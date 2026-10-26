package com.example.moondeuk.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAndStatsTest {

    @Test
    fun header_hasPcmFieldsForMono16k() {
        val h = ByteBuffer.wrap(WavFormat.header(16_000, 1, 16, 32_000)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(WavFormat.HEADER_SIZE, h.capacity())
        assertArrayEquals("RIFF".toByteArray(), h.array().copyOfRange(0, 4))
        assertEquals(36 + 32_000, h.getInt(4))
        assertArrayEquals("WAVE".toByteArray(), h.array().copyOfRange(8, 12))
        assertEquals(16, h.getInt(16))
        assertEquals(1, h.getShort(20).toInt()) // PCM
        assertEquals(1, h.getShort(22).toInt()) // mono
        assertEquals(16_000, h.getInt(24))
        assertEquals(32_000, h.getInt(28)) // byte rate
        assertEquals(2, h.getShort(32).toInt()) // block align
        assertEquals(16, h.getShort(34).toInt())
        assertArrayEquals("data".toByteArray(), h.array().copyOfRange(36, 40))
        assertEquals(32_000, h.getInt(40))
    }

    @Test(expected = IllegalArgumentException::class)
    fun header_rejectsNegativeSize() {
        WavFormat.header(16_000, 1, 16, -1)
    }

    @Test
    fun stats_allZeroIsSilent() {
        val s = PcmStats()
        s.add(ShortArray(1600), 1600)
        assertTrue(s.looksSilent)
        assertEquals(0.0, s.nonZeroRatio, 0.0)
        assertTrue(s.peakDbfs.isInfinite())
    }

    @Test
    fun stats_emptyIsSilent() {
        assertTrue(PcmStats().looksSilent)
    }

    @Test
    fun stats_toneIsNotSilent_andPeakTracksAbsMax() {
        val s = PcmStats()
        val buf = ShortArray(1600) { i -> if (i % 2 == 0) 8000 else -16384 }
        val chunkPeak = s.add(buf, buf.size)
        assertEquals(16384, chunkPeak)
        assertEquals(16384, s.peak)
        assertEquals(-6.02, s.peakDbfs, 0.01)
        assertFalse(s.looksSilent)
    }

    @Test
    fun stats_handlesShortMinValue() {
        val s = PcmStats()
        s.add(shortArrayOf(Short.MIN_VALUE), 1)
        assertEquals(32768, s.peak)
        assertEquals(0.0, s.peakDbfs, 1e-9)
    }

    @Test
    fun stats_countRespectsPartialBuffer() {
        val s = PcmStats()
        val buf = ShortArray(10) { 1000 }
        s.add(buf, 4)
        assertEquals(4L, s.samples)
    }
}
