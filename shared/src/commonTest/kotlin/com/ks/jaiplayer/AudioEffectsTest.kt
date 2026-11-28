package com.ks.jaiplayer

import com.ks.jaiplayer.audio.AudioChain
import com.ks.jaiplayer.audio.AudioDsp
import com.ks.jaiplayer.audio.AudioPreset
import com.ks.jaiplayer.audio.AudioSettings
import com.ks.jaiplayer.audio.BAND_HZ
import com.ks.jaiplayer.audio.Dynamics
import com.ks.jaiplayer.audio.MediaKind
import com.ks.jaiplayer.plugins.audio.AudioEffectsPlugin
import com.ks.jaiplayer.settings.MemorySettingsStore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioEffectsTest {
    private val rate = 48000

    @Test
    fun originalAndOffLeaveTheAudioUntouched() {
        assertNull(AudioSettings().chain(MediaKind.Video))
        assertNull(AudioSettings(videoPreset = AudioPreset.Cinema, enabled = false).chain(MediaKind.Video))
        // A preset for videos does not touch music.
        assertNull(AudioSettings(videoPreset = AudioPreset.Cinema).chain(MediaKind.Music))
        assertNotNull(AudioSettings(videoPreset = AudioPreset.Cinema).chain(MediaKind.Video))
    }

    @Test
    fun presetsAndControlsResolve() {
        val night = AudioSettings(videoPreset = AudioPreset.Night).chain(MediaKind.Video)!!
        assertEquals(Dynamics.Night, night.dynamics)
        assertTrue(night.bandsDb[0] < 0, "night mode cuts deep bass")
        val bass = AudioSettings(musicPreset = AudioPreset.BassBoost, bass = 10).chain(MediaKind.Music)!!
        assertTrue(bass.bandsDb[0] > AudioSettings(musicPreset = AudioPreset.BassBoost).chain(MediaKind.Music)!!.bandsDb[0])
        assertTrue(bass.bandsDb.all { it <= 12f }, "bands stay within ±12 dB")
        val dialogue = AudioSettings(videoPreset = AudioPreset.Music, dialogue = 10).chain(MediaKind.Video)!!
        assertEquals(Dynamics.Light, dialogue.dynamics)
        assertTrue(dialogue.centerDb > 0)
        val custom = AudioSettings(videoPreset = AudioPreset.Custom, customDb = List(10) { if (it == 5) 6f else 0f }).chain(MediaKind.Video)!!
        assertEquals(6f, custom.bandsDb[5])
        assertTrue(custom.peakBoostDb in 5.5f..6.5f, "headroom follows the curve: ${custom.peakBoostDb}")
    }

    @Test
    fun settingsAreSavedAndLoaded() {
        val store = MemorySettingsStore()
        val s = AudioSettings(
            enabled = false, videoPreset = AudioPreset.Night, musicPreset = AudioPreset.Music,
            bass = 4, dialogue = 7, customDb = List(10) { it - 5f },
        )
        AudioEffectsPlugin.save(store, s)
        assertEquals(s, AudioEffectsPlugin.load(store))
        assertEquals(AudioSettings(), AudioEffectsPlugin.load(MemorySettingsStore()))
    }

    @Test
    fun noChainIsBitExact() {
        val dsp = AudioDsp(rate, 2).apply { set(null) }
        val samples = sine(1000.0, 0.5f, 4800, 2)
        val copy = samples.copyOf()
        dsp.process(samples, 4800)
        assertContentEquals(copy, samples)
    }

    @Test
    fun boostsNeverClip() {
        val dsp = AudioDsp(rate, 2).apply {
            set(AudioSettings(musicPreset = AudioPreset.BassBoost, bass = 10).chain(MediaKind.Music))
        }
        val samples = sine(60.0, 1f, rate, 2) // full-scale bass
        dsp.process(samples, rate)
        val peak = samples.maxOf { abs(it) }
        assertTrue(peak <= 0.8915f, "peak $peak stays at or below -1 dBFS")
        assertTrue(peak > 0.5f, "still loud: $peak")
    }

    @Test
    fun nightModeNarrowsTheGapBetweenQuietAndLoud() {
        val chain = AudioChain(bandsDb = List(BAND_HZ.size) { 0f }, dynamics = Dynamics.Night)
        val quiet = rms(processed(chain, sine(1000.0, 0.03f, rate, 2)))
        val loud = rms(processed(chain, sine(1000.0, 0.9f, rate, 2)))
        val before = 0.9f / 0.03f // 30 times, about 30 dB
        assertTrue(loud / quiet < before / 4, "loud/quiet ${loud / quiet} vs $before")
        assertTrue(quiet > 0.03f * 0.7071f * 2, "quiet speech comes up: $quiet")
    }

    @Test
    fun centreBoostTouchesOnlyTheCentreChannel() {
        val chain = AudioChain(bandsDb = List(BAND_HZ.size) { 0f }, centerDb = 6f)
        val frames = 4800
        val samples = FloatArray(frames * 6) { 0.1f }
        AudioDsp(rate, 6).apply { set(chain) }.process(samples, frames)
        val last = (frames - 1) * 6
        assertTrue(abs(samples[last + 2] - 0.1995f) < 0.002f, "centre +6 dB: ${samples[last + 2]}")
        assertTrue(abs(samples[last] - 0.1f) < 0.001f, "front left unchanged: ${samples[last]}")
    }

    private fun processed(chain: AudioChain, samples: FloatArray): FloatArray {
        AudioDsp(rate, 2).apply { set(chain) }.process(samples, samples.size / 2)
        // Skip the first half second, while the detector settles.
        return samples.copyOfRange(samples.size / 2, samples.size)
    }

    private fun sine(hz: Double, amplitude: Float, frames: Int, channels: Int) = FloatArray(frames * channels) { i ->
        (amplitude * sin(2 * PI * hz * (i / channels) / rate)).toFloat()
    }

    private fun rms(s: FloatArray) = sqrt(s.sumOf { (it * it).toDouble() } / s.size).toFloat()
}
