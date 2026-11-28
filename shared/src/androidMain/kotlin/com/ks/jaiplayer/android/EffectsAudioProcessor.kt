package com.ks.jaiplayer.android

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ks.jaiplayer.audio.AudioChain
import com.ks.jaiplayer.audio.AudioDsp
import com.ks.jaiplayer.audio.toPcm16
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.Volatile

/**
 * Media3 audio processor for the audio effects: runs [AudioDsp] on decoded 16-bit or float PCM,
 * before Media3's speed change, so seeking and playback speed work as before. With no chain
 * (Original, or effects off) it copies the samples unchanged. Encoded audio (Dolby or DTS
 * passthrough) never reaches it.
 */
@UnstableApi
internal class EffectsAudioProcessor : BaseAudioProcessor() {
    @Volatile private var chain: AudioChain? = null
    @Volatile private var dsp: AudioDsp? = null
    private var isFloat = false
    private var work = FloatArray(0)

    fun setChain(value: AudioChain?) {
        chain = value
        dsp?.set(value)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        val format = inputAudioFormat
        isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val current = dsp
        if (current != null && sameFormat == format) {
            current.reset()
        } else {
            dsp = AudioDsp(format.sampleRate, format.channelCount).also { it.set(chain) }
            sameFormat = format
        }
    }

    private var sameFormat: AudioFormat? = null

    override fun onReset() {
        dsp = null
        sameFormat = null
        work = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val d = dsp
        if (d == null || chain == null) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        val samples = if (isFloat) size / 4 else size / 2
        if (work.size < samples) work = FloatArray(samples)
        if (isFloat) {
            for (i in 0 until samples) work[i] = input.getFloat()
        } else {
            for (i in 0 until samples) work[i] = input.getShort() / 32768f
        }
        d.process(work, samples / channels)
        if (isFloat) {
            for (i in 0 until samples) out.putFloat(work[i])
        } else {
            for (i in 0 until samples) out.putShort(work[i].toPcm16())
        }
        out.flip()
    }
}
