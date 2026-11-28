package com.ks.jaiplayer.audio

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The audio effects for decoded PCM (Android and Google TV; libVLC has its own equalizer):
 * the 10-band equalizer, centre-channel gain, stereo width, a compressor and a peak limiter, in
 * that order, on interleaved float samples (-1 to 1). No look-ahead, so it adds no latency.
 *
 * [set] may be called from any thread; the audio thread picks up the new settings at the next
 * buffer. With null the samples pass through unchanged.
 */
class AudioDsp(private val sampleRate: Int, private val channels: Int) {
    private class Plan(
        val coefficients: Array<DoubleArray>,
        val active: IntArray,
        val preamp: Float,
        val center: Float,
        val width: Float,
        val dynamics: Dynamics,
        val attack: Float,
        val release: Float,
        val makeup: Float,
    )

    @Volatile private var plan: Plan? = null

    // Filter state per band and channel (transposed direct form II).
    private val z1 = Array(BAND_HZ.size) { DoubleArray(channels) }
    private val z2 = Array(BAND_HZ.size) { DoubleArray(channels) }
    private var envelope = 0f
    private var limiterGain = 1f
    private val limiterRelease = coefficient(LIMITER_RELEASE_MS)

    /** The centre channel in Android's order for 5.1 and 7.1 (FL, FR, FC, LFE, …); -1 otherwise. */
    private val centerIndex = if (channels >= 6) 2 else -1

    fun set(chain: AudioChain?) {
        plan = chain?.let { c ->
            val coefficients = Array(BAND_HZ.size) { peakingCoefficients(BAND_HZ[it], c.bandsDb[it], sampleRate) }
            // Headroom for the boosts: the limiter catches what is left, so half of the peak is enough.
            Plan(
                coefficients = coefficients,
                active = coefficients.indices.filter { coefficients[it][1] != 0.0 || coefficients[it][0] != 1.0 }.toIntArray(),
                preamp = dbToGain(-c.peakBoostDb * 0.5f),
                center = if (centerIndex >= 0) dbToGain(c.centerDb) else 1f,
                width = if (channels == 2) c.width else 1f,
                dynamics = c.dynamics,
                attack = coefficient(c.dynamics.attackMs),
                release = coefficient(c.dynamics.releaseMs),
                makeup = dbToGain(c.dynamics.makeupDb),
            )
        }
    }

    /** Forgets the filters' and the detectors' state, after a seek. */
    fun reset() {
        z1.forEach { it.fill(0.0) }
        z2.forEach { it.fill(0.0) }
        envelope = 0f
        limiterGain = 1f
    }

    /** Processes [frames] frames of interleaved [samples] in place. */
    fun process(samples: FloatArray, frames: Int) {
        val p = plan ?: return
        val ch = channels
        val active = p.active
        for (f in 0 until frames) {
            val base = f * ch
            var peak = 0f
            for (c in 0 until ch) {
                var x = samples[base + c].toDouble()
                for (k in active) {
                    val co = p.coefficients[k]
                    val y = co[0] * x + z1[k][c]
                    z1[k][c] = co[1] * x - co[3] * y + z2[k][c]
                    z2[k][c] = co[2] * x - co[4] * y
                    x = y
                }
                var v = x.toFloat() * p.preamp
                if (c == centerIndex) v *= p.center
                samples[base + c] = v
            }
            if (p.width != 1f) {
                val l = samples[base]; val r = samples[base + 1]
                val mid = (l + r) * 0.5f; val side = (l - r) * 0.5f * p.width
                samples[base] = mid + side; samples[base + 1] = mid - side
            }
            for (c in 0 until ch) peak = max(peak, abs(samples[base + c]))

            var gain = 1f
            if (p.dynamics != Dynamics.None) {
                envelope = if (peak > envelope) p.attack * envelope + (1 - p.attack) * peak
                else p.release * envelope + (1 - p.release) * peak
                val levelDb = 20f * log10(envelope + 1e-9f)
                val over = levelDb - p.dynamics.thresholdDb
                val reductionDb = if (over > 0) over * (1f - 1f / p.dynamics.ratio) else 0f
                gain = dbToGain(-reductionDb) * p.makeup
            }
            // Peak limiter at -1 dBFS: instant attack, so nothing ever clips; smooth release.
            val out = peak * gain
            val target = if (out > CEILING) CEILING / out else 1f
            limiterGain = if (target < limiterGain) target else target + (limiterGain - target) * limiterRelease
            val total = gain * limiterGain
            if (total != 1f) for (c in 0 until ch) samples[base + c] *= total
        }
    }

    private fun coefficient(ms: Float): Float = exp(-1f / (ms * 0.001f * sampleRate))

    private companion object {
        const val CEILING = 0.891f // -1 dBFS
        const val LIMITER_RELEASE_MS = 80f
        fun dbToGain(db: Float): Float = 10f.pow(db / 20f)
    }
}

/** Clamps for writing back to 16-bit PCM (the limiter already keeps samples within -1 dBFS). */
internal fun Float.toPcm16(): Short = (min(1f, max(-1f, this)) * 32767f).toInt().toShort()
