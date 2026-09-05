package com.joel.gta

import com.joel.gta.data.audio.MetronomeEngine
import com.joel.gta.data.audio.PitchDetector
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class StageToolsTest {

    @Test
    fun testFrequencyToStandardGuitarNotes() {
        // Standard guitar strings: E2 (82.4Hz), A2 (110Hz), D3 (146.8Hz), G3 (196Hz), B3 (246.9Hz), E4 (329.6Hz)
        val (e2Note, e2Oct, e2Cents) = PitchDetector.frequencyToNote(82.41f)
        assertEquals("E", e2Note)
        assertEquals(2, e2Oct)
        assertTrue(abs(e2Cents) < 1.0f)

        val (a2Note, a2Oct, a2Cents) = PitchDetector.frequencyToNote(110.00f)
        assertEquals("A", a2Note)
        assertEquals(2, a2Oct)
        assertTrue(abs(a2Cents) < 1.0f)

        val (d3Note, d3Oct, d3Cents) = PitchDetector.frequencyToNote(146.83f)
        assertEquals("D", d3Note)
        assertEquals(3, d3Oct)
        assertTrue(abs(d3Cents) < 1.0f)

        val (g3Note, g3Oct, g3Cents) = PitchDetector.frequencyToNote(196.00f)
        assertEquals("G", g3Note)
        assertEquals(3, g3Oct)
        assertTrue(abs(g3Cents) < 1.0f)

        val (b3Note, b3Oct, b3Cents) = PitchDetector.frequencyToNote(246.94f)
        assertEquals("B", b3Note)
        assertEquals(3, b3Oct)
        assertTrue(abs(b3Cents) < 1.0f)

        val (e4Note, e4Oct, e4Cents) = PitchDetector.frequencyToNote(329.63f)
        assertEquals("E", e4Note)
        assertEquals(4, e4Oct)
        assertTrue(abs(e4Cents) < 1.0f)

        val (a4Note, a4Oct, a4Cents) = PitchDetector.frequencyToNote(440.00f)
        assertEquals("A", a4Note)
        assertEquals(4, a4Oct)
        assertTrue(abs(a4Cents) < 1.0f)
    }

    @Test
    fun testNearestGuitarStringMapping() {
        assertEquals("E2", PitchDetector.findNearestGuitarString(82.0f))
        assertEquals("A2", PitchDetector.findNearestGuitarString(112.5f))
        assertEquals("D3", PitchDetector.findNearestGuitarString(148.0f))
        assertEquals("G3", PitchDetector.findNearestGuitarString(194.0f))
        assertEquals("B3", PitchDetector.findNearestGuitarString(245.0f))
        assertEquals("E4", PitchDetector.findNearestGuitarString(332.0f))
    }

    @Test
    fun testNoteToFrequency() {
        val freqA4 = PitchDetector.noteToFrequency("A", 4)
        assertEquals(440f, freqA4, 0.5f)

        val freqE2 = PitchDetector.noteToFrequency("E", 2)
        assertEquals(82.41f, freqE2, 0.5f)
    }

    @Test
    fun testSyntheticSineWavePitchDetection() {
        val sampleRate = 44100
        val targetFreq = 440.0f
        val bufferSize = 4096
        val buffer = ShortArray(bufferSize)

        for (i in 0 until bufferSize) {
            val angle = 2.0 * PI * targetFreq * i / sampleRate
            buffer[i] = (sin(angle) * 20000).toInt().toShort()
        }

        val detected = PitchDetector.detectPitchAutocorrelation(buffer, bufferSize, sampleRate)
        assertTrue("Detected pitch should be close to 440Hz, got: $detected", abs(detected - targetFreq) < 2.0f)
    }

    @Test
    fun testMetronomeEngineSettings() {
        MetronomeEngine.setBpm(120)
        assertEquals(120, MetronomeEngine.state.value.bpm)

        MetronomeEngine.adjustBpm(5)
        assertEquals(125, MetronomeEngine.state.value.bpm)

        MetronomeEngine.adjustBpm(-10)
        assertEquals(115, MetronomeEngine.state.value.bpm)

        // Expanded Clamping tests: 30 - 300 BPM
        MetronomeEngine.setBpm(350)
        assertEquals(300, MetronomeEngine.state.value.bpm)

        MetronomeEngine.setBpm(10)
        assertEquals(30, MetronomeEngine.state.value.bpm)

        // Time signatures (odd meters, prog rock, clamping 1-16)
        MetronomeEngine.setTimeSignature(7, 8)
        assertEquals(7, MetronomeEngine.state.value.beatsPerBar)
        assertEquals(8, MetronomeEngine.state.value.beatUnit)

        MetronomeEngine.setTimeSignature(5, 4)
        assertEquals(5, MetronomeEngine.state.value.beatsPerBar)
        assertEquals(4, MetronomeEngine.state.value.beatUnit)

        MetronomeEngine.setTimeSignature(16, 16)
        assertEquals(16, MetronomeEngine.state.value.beatsPerBar)
        assertEquals(16, MetronomeEngine.state.value.beatUnit)

        // Clamping numerator
        MetronomeEngine.setTimeSignature(25, 8)
        assertEquals(16, MetronomeEngine.state.value.beatsPerBar)

        MetronomeEngine.setTimeSignature(0, 8)
        assertEquals(1, MetronomeEngine.state.value.beatsPerBar)

        // Invalid denominator fallback to 4
        MetronomeEngine.setTimeSignature(7, 3)
        assertEquals(4, MetronomeEngine.state.value.beatUnit)

        // Sound profiles
        MetronomeEngine.setSoundProfile(com.joel.gta.data.audio.MetronomeSoundProfile.WOODBLOCK)
        assertEquals(com.joel.gta.data.audio.MetronomeSoundProfile.WOODBLOCK, MetronomeEngine.state.value.soundProfile)

        MetronomeEngine.setSoundProfile(com.joel.gta.data.audio.MetronomeSoundProfile.DIGITAL_BEEP)
        assertEquals(com.joel.gta.data.audio.MetronomeSoundProfile.DIGITAL_BEEP, MetronomeEngine.state.value.soundProfile)

        MetronomeEngine.setSoundProfile(com.joel.gta.data.audio.MetronomeSoundProfile.RIMSHOT)
        assertEquals(com.joel.gta.data.audio.MetronomeSoundProfile.RIMSHOT, MetronomeEngine.state.value.soundProfile)
    }
}
