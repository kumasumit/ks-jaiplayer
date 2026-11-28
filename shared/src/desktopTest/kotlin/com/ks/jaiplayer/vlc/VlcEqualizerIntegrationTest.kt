package com.ks.jaiplayer.vlc

import com.ks.jaiplayer.audio.AudioPreset
import com.ks.jaiplayer.audio.AudioSettings
import com.ks.jaiplayer.audio.BAND_HZ
import com.ks.jaiplayer.audio.MediaKind
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop audio effects with the bundled libVLC: the equalizer changes the sound while it
 * plays (no restart), and turning it off gives the original level back. The sound is captured
 * with libVLC's memory output (amem), so nothing is heard. Runs only when the bundled copy has
 * been prepared (`./gradlew :desktop:mac:bundleVlc`) or JAIPLAYER_TEST_VLC names one.
 */
class VlcEqualizerIntegrationTest {
    private val vlcDir = System.getenv("JAIPLAYER_TEST_VLC")?.let(::File)
        ?: File("../desktop/mac/build/vlc-resources/common/vlc").absoluteFile

    @Suppress("FunctionName")
    private interface Capture : Library {
        fun libvlc_audio_set_callbacks(mp: Pointer, play: Play, pause: Pointer?, resume: Pointer?, flush: Pointer?, drain: Pointer?, opaque: Pointer?)
        fun libvlc_audio_set_format(mp: Pointer, format: String, rate: Int, channels: Int)
        fun libvlc_audio_equalizer_get_band_count(): Int
        fun libvlc_audio_equalizer_get_band_frequency(index: Int): Float
    }

    /** `void (*play)(void *data, const void *samples, unsigned count, int64_t pts)`. */
    private fun interface Play : Callback { fun invoke(data: Pointer?, samples: Pointer, count: Int, pts: Long) }

    @Test fun equalizerChangesTheSoundWhilePlaying() {
        val lib = File(vlcDir, "lib")
        if (!File(lib, "libvlc.dylib").isFile && !File(lib, "libvlc.dll").isFile) return println("Skipped: no bundled libVLC at $vlcDir")
        val vlc = VlcLoader.load(listOf(VlcLocation(lib, File(vlcDir, "plugins"))))!!.lib
        val capture = Native.load(File(lib, if (File(lib, "libvlc.dll").isFile) "libvlc.dll" else "libvlc.dylib").path, Capture::class.java)
        val wav = toneFile(seconds = 12)
        val inst = vlc.libvlc_new(2, arrayOf("--quiet", "--no-video"))!!
        val mp = vlc.libvlc_media_player_new(inst)!!
        val window = Window()
        val play = Play { _, samples, count, _ -> window.add(samples.getByteArray(0, count * 4)) }
        try {
            capture.libvlc_audio_set_callbacks(mp, play, null, null, null, null, null)
            capture.libvlc_audio_set_format(mp, "S16N", 48000, 2)
            val media = vlc.libvlc_media_new_path(inst, wav.path)!!
            vlc.libvlc_media_player_set_media(mp, media)
            vlc.libvlc_media_release(media)
            assertEquals(0, vlc.libvlc_media_player_play(mp))

            // libVLC's bands are the ones the presets are made for.
            val bands = (0 until capture.libvlc_audio_equalizer_get_band_count()).map { capture.libvlc_audio_equalizer_get_band_frequency(it).toInt() }
            assertEquals(BAND_HZ, bands)

            val original = window.levelAfter(2_500)

            // A flat equalizer at the unity preamp leaves the level as it was.
            setEqualizer(vlc, mp, AudioSettings(videoPreset = AudioPreset.Custom))
            val flat = window.levelAfter(2_000)

            // +9 dB on the tone's band, with the headroom preamp: about the original level.
            setEqualizer(vlc, mp, AudioSettings(videoPreset = AudioPreset.Custom, customDb = List(10) { if (it == 5) 9f else 0f }))
            val boosted = window.levelAfter(2_000)

            // +9 dB on a band far from the tone, same headroom: the tone is about 9 dB quieter.
            setEqualizer(vlc, mp, AudioSettings(videoPreset = AudioPreset.Custom, customDb = List(10) { if (it == 0) 9f else 0f }))
            val elsewhere = window.levelAfter(2_000)

            setEqualizer(vlc, mp, AudioSettings())
            val back = window.levelAfter(2_000)

            println(
                "1 kHz tone: original %.1f, flat %.1f, +9 dB at 1 kHz %.1f, +9 dB at 31 Hz %.1f, off %.1f dB"
                    .format(original, flat, boosted, elsewhere, back),
            )
            assertTrue(original > -40, "sound arrives: $original dB")
            assertTrue(kotlin.math.abs(flat - original) < 0.5, "flat keeps the level: ${flat - original} dB")
            assertTrue(kotlin.math.abs(boosted - original) < 2, "boost with headroom keeps the level: ${boosted - original} dB")
            assertTrue(original - elsewhere in 7.0..11.0, "the equalizer acts live: ${original - elsewhere} dB")
            assertTrue(kotlin.math.abs(back - original) < 0.5, "Original gives the same level back: ${back - original} dB")
        } finally {
            vlc.libvlc_media_player_stop(mp)
            vlc.libvlc_media_player_release(mp)
            vlc.libvlc_release(inst)
            wav.delete()
        }
    }

    private fun setEqualizer(vlc: LibVlc, mp: Pointer, s: AudioSettings) {
        val chain = s.chain(MediaKind.Video)
        if (chain == null) {
            vlc.libvlc_media_player_set_equalizer(mp, null)
            return
        }
        val eq = vlc.libvlc_audio_equalizer_new()!!
        // As VlcPlayerEngine.applyEffects does.
        vlc.libvlc_audio_equalizer_set_preamp(eq, (VlcPlayerEngine.VLC_UNITY_PREAMP_DB - chain.peakBoostDb).coerceIn(-20f, VlcPlayerEngine.VLC_UNITY_PREAMP_DB))
        chain.bandsDb.forEachIndexed { i, db -> vlc.libvlc_audio_equalizer_set_amp_at_index(eq, db, i) }
        assertEquals(0, vlc.libvlc_media_player_set_equalizer(mp, eq))
        vlc.libvlc_audio_equalizer_release(eq)
    }

    /** The RMS level, in dBFS, of the last half second captured. */
    private class Window {
        private val samples = ArrayDeque<Short>()
        @Synchronized fun add(bytes: ByteArray) {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            while (b.remaining() >= 2) samples.addLast(b.short)
            while (samples.size > 48000) samples.removeFirst()
        }
        fun levelAfter(ms: Long): Double {
            Thread.sleep(ms)
            synchronized(this) {
                val sum = samples.sumOf { (it / 32768.0) * (it / 32768.0) }
                return 20 * log10(sqrt(sum / samples.size.coerceAtLeast(1)) + 1e-9)
            }
        }
    }

    /** A 1 kHz tone at -20 dBFS, 48 kHz stereo 16-bit. */
    private fun toneFile(seconds: Int): File {
        val frames = 48000 * seconds
        val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val v = (0.1 * 32767 * sin(2 * PI * 1000 * i / 48000)).toInt().toShort()
            data.putShort(v); data.putShort(v)
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + frames * 4); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(2); putInt(48000); putInt(48000 * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(frames * 4)
        }
        return File.createTempFile("jaiplayer-tone", ".wav").apply { writeBytes(header.array() + data.array()) }
    }
}
