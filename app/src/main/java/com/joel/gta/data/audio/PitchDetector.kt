package com.joel.gta.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

data class TunerState(
    val isListening: Boolean = false,
    val noteName: String = "--",
    val octave: Int = 0,
    val frequencyHz: Float = 0f,
    val targetFrequencyHz: Float = 0f,
    val centsOffset: Float = 0f, // -50f .. +50f
    val isInTune: Boolean = false, // within ±3 cents
    val nearestGuitarString: String? = null,
    val signalStrengthRms: Float = 0f,
    val hasAudioPermission: Boolean = true,
    val errorMessage: String? = null
)

object PitchDetector {

    val NOTES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Standard 6-string guitar frequencies
    val GUITAR_STRINGS = listOf(
        Pair("E2", 82.41f),
        Pair("A2", 110.00f),
        Pair("D3", 146.83f),
        Pair("G3", 196.00f),
        Pair("B3", 246.94f),
        Pair("E4", 329.63f)
    )

    private val _tunerState = MutableStateFlow(TunerState())
    val tunerState: StateFlow<TunerState> = _tunerState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = 4096
    private const val MIN_FREQ = 60.0f  // Low E is ~82Hz, support down to 60Hz
    private const val MAX_FREQ = 800.0f // High E is ~330Hz, support harmonics up to 800Hz

    fun frequencyToNote(freq: Float): Triple<String, Int, Float> {
        if (freq <= 20f || freq > 5000f) return Triple("--", 0, 0f)
        val semitonesFromA4 = 12.0 * ln(freq.toDouble() / 440.0) / ln(2.0)
        val roundedSemitones = semitonesFromA4.roundToInt()
        val cents = ((semitonesFromA4 - roundedSemitones) * 100.0).toFloat().coerceIn(-50f, 50f)

        val midiNote = roundedSemitones + 69 // A4 is MIDI 69
        val noteIndex = Math.floorMod(midiNote, 12)
        val octave = (midiNote / 12) - 1

        return Triple(NOTES[noteIndex], octave, cents)
    }

    fun noteToFrequency(noteName: String, octave: Int): Float {
        val index = NOTES.indexOf(noteName)
        if (index == -1) return 0f
        val midiNote = (octave + 1) * 12 + index
        return (440.0 * 2.0.pow((midiNote - 69) / 12.0)).toFloat()
    }

    fun findNearestGuitarString(freq: Float): String? {
        if (freq <= 30f) return null
        return GUITAR_STRINGS.minByOrNull { abs(it.second - freq) }?.first
    }

    @SuppressLint("MissingPermission")
    fun startListening(scope: CoroutineScope) {
        if (recordingJob?.isActive == true) return

        recordingJob = scope.launch(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val actualBufferSize = max(minBufferSize, BUFFER_SIZE)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    actualBufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    _tunerState.value = _tunerState.value.copy(
                        isListening = false,
                        errorMessage = "Microphone could not be initialized."
                    )
                    return@launch
                }

                audioRecord?.startRecording()
                _tunerState.value = _tunerState.value.copy(
                    isListening = true,
                    errorMessage = null
                )

                val audioBuffer = ShortArray(BUFFER_SIZE)

                while (isActive) {
                    val readCount = audioRecord?.read(audioBuffer, 0, BUFFER_SIZE) ?: 0
                    if (readCount <= 0) continue

                    // Calculate RMS to determine signal amplitude
                    var sumSquares = 0.0
                    for (i in 0 until readCount) {
                        val sample = audioBuffer[i].toDouble()
                        sumSquares += sample * sample
                    }
                    val rms = sqrt(sumSquares / readCount).toFloat()

                    // If noise floor is too quiet, clear active note display
                    if (rms < 350f) {
                        _tunerState.value = _tunerState.value.copy(
                            signalStrengthRms = rms,
                            noteName = "--",
                            frequencyHz = 0f,
                            centsOffset = 0f,
                            isInTune = false
                        )
                        delay(50)
                        continue
                    }

                    // Autocorrelation pitch detection
                    val detectedPitch = detectPitchAutocorrelation(audioBuffer, readCount, SAMPLE_RATE)
                    if (detectedPitch in MIN_FREQ..MAX_FREQ) {
                        val (noteName, octave, cents) = frequencyToNote(detectedPitch)
                        val targetFreq = noteToFrequency(noteName, octave)
                        val inTune = abs(cents) <= 3.5f
                        val nearestString = findNearestGuitarString(detectedPitch)

                        _tunerState.value = _tunerState.value.copy(
                            noteName = noteName,
                            octave = octave,
                            frequencyHz = detectedPitch,
                            targetFrequencyHz = targetFreq,
                            centsOffset = cents,
                            isInTune = inTune,
                            nearestGuitarString = nearestString,
                            signalStrengthRms = rms
                        )
                    }

                    delay(30) // ~30 fps tuner refresh rate
                }
            } catch (e: SecurityException) {
                _tunerState.value = _tunerState.value.copy(
                    isListening = false,
                    hasAudioPermission = false,
                    errorMessage = "Microphone permission required."
                )
            } catch (e: Exception) {
                _tunerState.value = _tunerState.value.copy(
                    isListening = false,
                    errorMessage = e.message ?: "Audio input error"
                )
            } finally {
                stopRecordingInternal()
            }
        }
    }

    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        stopRecordingInternal()
        _tunerState.value = _tunerState.value.copy(isListening = false)
    }

    private fun stopRecordingInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    /**
     * Normalized autocorrelation with parabolic interpolation for fractional sample accuracy.
     */
    fun detectPitchAutocorrelation(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minLag = (sampleRate / MAX_FREQ).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / MIN_FREQ).toInt().coerceAtMost(size / 2)

        var bestLag = -1
        var bestCorrelation = 0.0

        // Zero-lag energy
        var energy0 = 0.0
        for (i in 0 until (size - maxLag)) {
            val sample = buffer[i].toDouble()
            energy0 += sample * sample
        }
        if (energy0 <= 0.0) return -1f

        val correlation = DoubleArray(maxLag + 2)

        for (lag in minLag..maxLag) {
            var sum = 0.0
            var energyLag = 0.0
            val limit = size - lag

            for (i in 0 until limit) {
                val s1 = buffer[i].toDouble()
                val s2 = buffer[i + lag].toDouble()
                sum += s1 * s2
                energyLag += s2 * s2
            }

            val denom = sqrt(energy0 * energyLag)
            val normalized = if (denom > 0.0) sum / denom else 0.0
            correlation[lag] = normalized

            if (normalized > bestCorrelation && normalized > 0.55) {
                bestCorrelation = normalized
                bestLag = lag
            }
        }

        if (bestLag < minLag || bestLag > maxLag) return -1f

        // Parabolic interpolation for sub-sample peak localization
        val prev = correlation[bestLag - 1]
        val curr = correlation[bestLag]
        val next = correlation[bestLag + 1]

        val delta = if ((prev - 2 * curr + next) != 0.0) {
            (prev - next) / (2.0 * (prev - 2 * curr + next))
        } else {
            0.0
        }

        val fineLag = bestLag + delta
        return if (fineLag > 0.0) (sampleRate / fineLag).toFloat() else -1f
    }
}
