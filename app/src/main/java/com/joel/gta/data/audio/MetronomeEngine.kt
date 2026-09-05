package com.joel.gta.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.*

enum class MetronomeSoundProfile(val label: String) {
    WOODBLOCK("Woodblock"),
    DIGITAL_BEEP("Digital Beep"),
    RIMSHOT("Rimshot")
}

data class MetronomeState(
    val isRunning: Boolean = false,
    val bpm: Int = 120,
    val currentBeat: Int = 1,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val isMuted: Boolean = false,
    val soundProfile: MetronomeSoundProfile = MetronomeSoundProfile.WOODBLOCK
)

object MetronomeEngine {

    const val MIN_BPM = 30
    const val MAX_BPM = 300
    const val MIN_BEATS_PER_BAR = 1
    const val MAX_BEATS_PER_BAR = 16

    private val _state = MutableStateFlow(MetronomeState())
    val state: StateFlow<MetronomeState> = _state.asStateFlow()

    // Autonomous scope that persists across dialog openings/dismissals
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var metronomeJob: Job? = null

    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SAMPLES = 1102 // ~25 ms at 44.1 kHz

    // Static AudioTracks preloaded with sound buffers
    private var woodblockAccentTrack: AudioTrack? = null
    private var woodblockRegularTrack: AudioTrack? = null

    private var digitalAccentTrack: AudioTrack? = null
    private var digitalRegularTrack: AudioTrack? = null

    private var rimshotAccentTrack: AudioTrack? = null
    private var rimshotRegularTrack: AudioTrack? = null

    private val tapTimestamps = mutableListOf<Long>()

    init {
        initAudioTracks()
    }

    private fun initAudioTracks() {
        try {
            woodblockAccentTrack = createTrack(synthesizeWoodblock(freq1 = 1500.0, freq2 = 2500.0, decayRate = 0.005))
            woodblockRegularTrack = createTrack(synthesizeWoodblock(freq1 = 950.0, freq2 = 1600.0, decayRate = 0.006))

            digitalAccentTrack = createTrack(synthesizeDigital(freq = 1250.0, decayRate = 0.007))
            digitalRegularTrack = createTrack(synthesizeDigital(freq = 820.0, decayRate = 0.007))

            rimshotAccentTrack = createTrack(synthesizeRimshot(freq = 1800.0, noiseFactor = 0.45, decayRate = 0.004))
            rimshotRegularTrack = createTrack(synthesizeRimshot(freq = 1100.0, noiseFactor = 0.35, decayRate = 0.005))
        } catch (_: Exception) {}
    }

    private fun createTrack(pcmData: ShortArray): AudioTrack {
        val byteBuffer = ByteArray(pcmData.size * 2)
        for (i in pcmData.indices) {
            val sample = pcmData[i].toInt()
            byteBuffer[i * 2] = (sample and 0xFF).toByte()
            byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            byteBuffer.size,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(byteBuffer, 0, byteBuffer.size)
        return track
    }

    private fun synthesizeWoodblock(freq1: Double, freq2: Double, decayRate: Double): ShortArray {
        val buffer = ShortArray(BUFFER_SAMPLES)
        for (i in 0 until BUFFER_SAMPLES) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t / decayRate)
            val wave = 0.7 * sin(2.0 * PI * freq1 * t) + 0.3 * sin(2.0 * PI * freq2 * t)
            val sample = (wave * envelope * 28000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
            buffer[i] = sample
        }
        return buffer
    }

    private fun synthesizeDigital(freq: Double, decayRate: Double): ShortArray {
        val buffer = ShortArray(BUFFER_SAMPLES)
        for (i in 0 until BUFFER_SAMPLES) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t / decayRate)
            val wave = sin(2.0 * PI * freq * t)
            val sample = (wave * envelope * 26000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
            buffer[i] = sample
        }
        return buffer
    }

    private fun synthesizeRimshot(freq: Double, noiseFactor: Double, decayRate: Double): ShortArray {
        val buffer = ShortArray(BUFFER_SAMPLES)
        val random = Random(42)
        for (i in 0 until BUFFER_SAMPLES) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t / decayRate)
            val tone = sin(2.0 * PI * freq * t)
            val noise = (random.nextDouble() * 2.0 - 1.0)
            val mix = (1.0 - noiseFactor) * tone + noiseFactor * noise
            val sample = (mix * envelope * 29000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
            buffer[i] = sample
        }
        return buffer
    }

    private fun playClick(isAccent: Boolean) {
        if (_state.value.isMuted) return

        try {
            val track = when (_state.value.soundProfile) {
                MetronomeSoundProfile.WOODBLOCK -> if (isAccent) woodblockAccentTrack else woodblockRegularTrack
                MetronomeSoundProfile.DIGITAL_BEEP -> if (isAccent) digitalAccentTrack else digitalRegularTrack
                MetronomeSoundProfile.RIMSHOT -> if (isAccent) rimshotAccentTrack else rimshotRegularTrack
            }

            track?.let {
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        } catch (_: Exception) {}
    }

    fun toggle(scope: CoroutineScope? = null) {
        if (_state.value.isRunning) {
            stop()
        } else {
            start()
        }
    }

    fun start(externalScope: CoroutineScope? = null) {
        if (metronomeJob?.isActive == true) return

        _state.value = _state.value.copy(isRunning = true, currentBeat = 1)

        // Launch in autonomous engineScope so it persists when dialogs close
        metronomeJob = engineScope.launch {
            while (isActive) {
                val currentBpm = _state.value.bpm.coerceIn(MIN_BPM, MAX_BPM)
                val beatsPerBar = _state.value.beatsPerBar
                val beatIntervalMs = (60_000L / currentBpm)

                val beat = _state.value.currentBeat
                val isAccent = (beat == 1)

                playClick(isAccent)

                delay(beatIntervalMs)

                val nextBeat = if (beat >= beatsPerBar) 1 else beat + 1
                _state.value = _state.value.copy(currentBeat = nextBeat)
            }
        }
    }

    fun stop() {
        metronomeJob?.cancel()
        metronomeJob = null
        _state.value = _state.value.copy(isRunning = false, currentBeat = 1)
    }

    fun setBpm(newBpm: Int) {
        _state.value = _state.value.copy(bpm = newBpm.coerceIn(MIN_BPM, MAX_BPM))
    }

    fun adjustBpm(delta: Int) {
        val newBpm = (_state.value.bpm + delta).coerceIn(MIN_BPM, MAX_BPM)
        _state.value = _state.value.copy(bpm = newBpm)
    }

    fun setTimeSignature(numerator: Int, denominator: Int = 4) {
        val validDenominator = if (denominator in listOf(2, 4, 8, 16)) denominator else 4
        _state.value = _state.value.copy(
            beatsPerBar = numerator.coerceIn(MIN_BEATS_PER_BAR, MAX_BEATS_PER_BAR),
            beatUnit = validDenominator,
            currentBeat = 1
        )
    }

    fun setBeatsPerBar(beats: Int) {
        setTimeSignature(beats, _state.value.beatUnit)
    }

    fun setBeatUnit(unit: Int) {
        setTimeSignature(_state.value.beatsPerBar, unit)
    }

    fun setSoundProfile(profile: MetronomeSoundProfile) {
        _state.value = _state.value.copy(soundProfile = profile)
    }

    fun toggleMute() {
        _state.value = _state.value.copy(isMuted = !_state.value.isMuted)
    }

    /**
     * Tap Tempo feature: Call each time user taps the button.
     * Computes moving average interval and sets target BPM.
     */
    fun recordTap(): Int {
        val now = SystemClock.elapsedRealtime()

        if (tapTimestamps.isNotEmpty() && (now - tapTimestamps.last()) > 2500) {
            tapTimestamps.clear()
        }

        tapTimestamps.add(now)
        if (tapTimestamps.size > 5) {
            tapTimestamps.removeAt(0)
        }

        if (tapTimestamps.size >= 2) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until tapTimestamps.size) {
                intervals.add(tapTimestamps[i] - tapTimestamps[i - 1])
            }
            val avgIntervalMs = intervals.average()
            if (avgIntervalMs > 0) {
                val calculatedBpm = (60_000.0 / avgIntervalMs).toInt().coerceIn(MIN_BPM, MAX_BPM)
                setBpm(calculatedBpm)
                return calculatedBpm
            }
        }

        return _state.value.bpm
    }

    fun release() {
        stop()
        listOf(
            woodblockAccentTrack, woodblockRegularTrack,
            digitalAccentTrack, digitalRegularTrack,
            rimshotAccentTrack, rimshotRegularTrack
        ).forEach {
            try {
                it?.release()
            } catch (_: Exception) {}
        }
    }
}
