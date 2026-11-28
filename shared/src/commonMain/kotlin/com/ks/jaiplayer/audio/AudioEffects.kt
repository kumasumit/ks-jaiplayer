package com.ks.jaiplayer.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Audio effects (specs/audio-effects.md): presets, the bass and dialogue controls and a custom
 * equalizer, resolved into an [AudioChain] that each engine applies in its own way. Original (or
 * effects off) resolves to null: the engine leaves the audio untouched.
 */
enum class AudioPreset(val id: String) {
    Original("original"),
    BassBoost("bass"),
    Music("music"),
    Cinema("cinema"),
    Dialogue("dialogue"),
    LoudClear("loud"),
    Night("night"),
    Vocal("vocal"),
    Custom("custom");

    companion object {
        fun of(id: String?): AudioPreset = entries.firstOrNull { it.id == id } ?: Original
    }
}

/** Dynamic range control. Not available on desktop (libVLC 3 cannot add its compressor at run time). */
enum class Dynamics(
    val thresholdDb: Float,
    val ratio: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val makeupDb: Float,
) {
    None(0f, 1f, 1f, 1f, 0f),

    /** Evens out dialogue against music and effects a little. */
    Light(-22f, 2f, 10f, 200f, 3f),

    /** Louder overall, peaks held back by the limiter. */
    Loud(-20f, 3f, 5f, 150f, 7f),

    /** Quiet speech up, explosions down: for low volume at night. */
    Night(-32f, 4f, 5f, 300f, 12f),
}

/** What the engine applies. Gains are in dB; [bandsDb] follows [BAND_HZ]. */
data class AudioChain(
    val bandsDb: List<Float>,
    val dynamics: Dynamics = Dynamics.None,
    /** Extra gain on the centre (dialogue) channel of 5.1 and 7.1 audio. */
    val centerDb: Float = 0f,
    /** Stereo width: 1 unchanged, above 1 wider. */
    val width: Float = 1f,
) {
    /** The largest boost of the equalizer's whole curve: the headroom it needs to avoid clipping. */
    val peakBoostDb: Float get() = equalizerPeakDb(bandsDb)
}

/**
 * The ten equalizer bands: libVLC's (octaves from 31 Hz to 16 kHz; checked by
 * VlcEqualizerIntegrationTest), used on Android too, so a preset sounds alike everywhere.
 */
val BAND_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/** The levels of the bass and dialogue controls, 0 (none) to 10. */
const val CONTROL_MAX = 10

/** Custom equalizer range, in dB. */
const val BAND_MAX_DB = 12f

/** Which kind of file plays: each kind keeps its own preset. */
enum class MediaKind { Video, Music }

/** The saved settings of the audio effects. */
data class AudioSettings(
    val enabled: Boolean = true,
    val videoPreset: AudioPreset = AudioPreset.Original,
    val musicPreset: AudioPreset = AudioPreset.Original,
    val bass: Int = 0,
    val dialogue: Int = 0,
    val customDb: List<Float> = List(BAND_HZ.size) { 0f },
) {
    fun preset(kind: MediaKind) = if (kind == MediaKind.Video) videoPreset else musicPreset

    fun withPreset(kind: MediaKind, preset: AudioPreset) =
        if (kind == MediaKind.Video) copy(videoPreset = preset) else copy(musicPreset = preset)

    /** The chain for [kind], or null when the audio is left untouched. */
    fun chain(kind: MediaKind): AudioChain? {
        val preset = preset(kind)
        if (!enabled || preset == AudioPreset.Original) return null
        val base = if (preset == AudioPreset.Custom) customDb else PRESET_DB.getValue(preset)
        val b = bass.coerceIn(0, CONTROL_MAX) / CONTROL_MAX.toFloat()
        val d = dialogue.coerceIn(0, CONTROL_MAX) / CONTROL_MAX.toFloat()
        val bands = base.indices.map { i ->
            (base[i] + b * BASS_SHAPE[i] + d * DIALOGUE_SHAPE[i]).coerceIn(-BAND_MAX_DB, BAND_MAX_DB)
        }
        val (dynamics, center, width) = PRESET_SHAPE[preset] ?: Triple(Dynamics.None, 0f, 1f)
        return AudioChain(
            bandsDb = bands,
            dynamics = if (dynamics == Dynamics.None && d >= 0.5f) Dynamics.Light else dynamics,
            centerDb = center + d * 6f,
            width = width,
        )
    }

    companion object {
        // Gains per band (31 Hz … 16 kHz). Boosts stay moderate: the headroom ([AudioChain.peakBoostDb])
        // and, on Android, the limiter keep them from clipping. Speech sits around 1 to 4 kHz.
        private val PRESET_DB = mapOf(
            AudioPreset.BassBoost to listOf(5f, 6f, 4f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f),
            AudioPreset.Music to listOf(3f, 4f, 2.5f, 0.5f, 0f, 0f, 1f, 2f, 2.5f, 2f),
            AudioPreset.Cinema to listOf(3f, 4f, 2f, 0f, 0f, 1.5f, 3f, 2f, 1f, 0f),
            AudioPreset.Dialogue to listOf(-5f, -4f, -2.5f, -1f, 0.5f, 2.5f, 4.5f, 3f, 0.5f, 0f),
            AudioPreset.LoudClear to listOf(1f, 2f, 1f, 0f, 0f, 1f, 2f, 1.5f, 1f, 0f),
            AudioPreset.Night to listOf(-6f, -5f, -3f, -1f, 0f, 1.5f, 3f, 1.5f, 0f, 0f),
            AudioPreset.Vocal to listOf(-3f, -2f, -1f, 0f, 1f, 3f, 4f, 2.5f, 1f, 0f),
        )

        // Dynamics, centre channel gain and stereo width per preset (Android and Google TV).
        private val PRESET_SHAPE = mapOf(
            AudioPreset.Cinema to Triple(Dynamics.Light, 3f, 1.25f),
            AudioPreset.Dialogue to Triple(Dynamics.Light, 6f, 1f),
            AudioPreset.LoudClear to Triple(Dynamics.Loud, 2f, 1f),
            AudioPreset.Night to Triple(Dynamics.Night, 4f, 1f),
            AudioPreset.Vocal to Triple(Dynamics.None, 3f, 1f),
            AudioPreset.Music to Triple(Dynamics.None, 0f, 1.1f),
        )

        private val BASS_SHAPE = listOf(5f, 7f, 4.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)
        private val DIALOGUE_SHAPE = listOf(-3f, -3f, -2f, -0.5f, 1f, 3f, 4f, 2f, 0f, 0f)
    }
}

/** Peaking-filter quality of each band: wide enough that neighbouring bands blend smoothly. */
internal const val BAND_Q = 1.1

/**
 * RBJ peaking filter for [hz] with [gainDb] at [sampleRate]: (b0, b1, b2, a1, a2), normalised.
 * Bands at or above 45 % of the sample rate are left flat.
 */
internal fun peakingCoefficients(hz: Int, gainDb: Float, sampleRate: Int): DoubleArray {
    if (abs(gainDb) < 0.05f || hz >= sampleRate * 0.45) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
    val a = 10.0.pow(gainDb / 40.0)
    val w = 2 * PI * hz / sampleRate
    val alpha = sin(w) / (2 * BAND_Q)
    val a0 = 1 + alpha / a
    return doubleArrayOf(
        (1 + alpha * a) / a0, (-2 * cos(w)) / a0, (1 - alpha * a) / a0,
        (-2 * cos(w)) / a0, (1 - alpha / a) / a0,
    )
}

/** The highest gain, in dB (0 at least), of the whole 10-band curve, sampled from 20 Hz to 20 kHz. */
internal fun equalizerPeakDb(bandsDb: List<Float>, sampleRate: Int = 48000): Float {
    val filters = BAND_HZ.indices.map { peakingCoefficients(BAND_HZ[it], bandsDb[it], sampleRate) }
    var peak = 0.0
    for (step in 0..120) {
        val hz = 20.0 * 1000.0.pow(step / 120.0)
        val w = 2 * PI * hz / sampleRate
        var db = 0.0
        for (c in filters) db += 20 * log10(magnitude(c, w))
        if (db > peak) peak = db
    }
    return peak.toFloat()
}

private fun magnitude(c: DoubleArray, w: Double): Double {
    val (b0, b1, b2, a1, a2) = c.toList()
    val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
    val nr = b0 + b1 * cw + b2 * c2; val ni = -(b1 * sw + b2 * s2)
    val dr = 1 + a1 * cw + a2 * c2; val di = -(a1 * sw + a2 * s2)
    return sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
}

private operator fun List<Double>.component5() = this[4]

/** How much of the audio effects a platform can apply. */
enum class AudioSupport {
    /** None (an engine without effects). */
    None,

    /** The equalizer only (desktop: libVLC's equalizer). */
    Equalizer,

    /** Equalizer, dynamics, limiter, centre channel and stereo width (Android and Google TV). */
    Full,
}
