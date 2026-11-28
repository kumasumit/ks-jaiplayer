package com.ks.jaiplayer.ui

import com.ks.jaiplayer.vlc.VideoFrames
import com.ks.jaiplayer.vlc.VideoFrames.Chroma
import com.sun.jna.Memory
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Both frame layouts asked of libVLC (I420 and P010) turn into the same colours on screen: limited-range white, black and BT.601 red, drawn through the real shader
 * onto a CPU surface.
 */
class VideoColourTest {
    private data class Yuv(val y: Int, val u: Int, val v: Int)

    // 8-bit limited-range values; P010 uses them times four.
    private val white = Yuv(235, 128, 128) to intArrayOf(255, 255, 255)
    private val black = Yuv(16, 128, 128) to intArrayOf(0, 0, 0)
    private val red = Yuv(81, 90, 240) to intArrayOf(255, 0, 0)

    @Test
    fun everyLayoutGivesTheSameColours() {
        for (chroma in Chroma.entries) {
            for ((yuv, rgb) in listOf(white, black, red)) {
                val got = render(chroma, yuv)
                for (i in 0..2) {
                    assertTrue(abs(got[i] - rgb[i]) <= 3, "$chroma $yuv: got ${got.toList()}, want ${rgb.toList()}")
                }
            }
        }
    }

    @Test
    fun p010FiltersLikeI420() {
        // A ramp in luma and both chroma planes, drawn scaled down and up: the P010 shader joins
        // bytes and filters itself, and must land where the GPU-filtered 8-bit path does.
        for (scale in listOf(0.5f, 1f, 1.75f)) {
            val reference = renderRamp(Chroma.I420, scale)
            val got = renderRamp(Chroma.P010, scale)
            val worst = reference.indices.maxOf { abs(reference[it] - got[it]) }
            assertTrue(worst <= 4, "P010 at ×$scale differs from I420 by up to $worst")
        }
    }

    @Test
    fun theLayoutFollowsTheDecoder() {
        assertEquals(Chroma.I420, Chroma.forProposed("CVPN"))
        assertEquals(Chroma.P010, Chroma.forProposed("CVPP"))
        assertEquals(Chroma.I420, Chroma.forProposed("I420"))
        assertEquals(Chroma.I420, Chroma.forProposed("I0AL"))
        assertEquals(Chroma.I420, Chroma.forProposed("RV32"))
    }

    /** Fills a 16×16 frame with one colour in [chroma]'s layout, draws it, returns its RGB. */
    private fun render(chroma: Chroma, c: Yuv): IntArray {
        val px = draw(chroma, 16, 1f) { _, _, _ -> intArrayOf(c.y, c.u, c.v) }
        val i = (8 * 16 + 8) * 3
        return intArrayOf(px[i], px[i + 1], px[i + 2])
    }

    /** A 32×32 ramp (luma left to right, Cb top to bottom, Cr diagonal), every pixel's RGB. */
    private fun renderRamp(chroma: Chroma, scale: Float): IntArray =
        draw(chroma, 32, scale) { x, y, plane ->
            intArrayOf(16 + x * 219 / 31, 16 + y * 224 / 15, 16 + (x + y) * 224 / 46).also { if (plane == 0) it[1] = 0 }
        }

    /**
     * Fills a [size]×[size] frame in [chroma]'s layout ([yuv] gives 8-bit Y, Cb, Cr for a luma or
     * chroma position; plane 0 is luma), draws it scaled by [scale], returns RGB for every pixel.
     */
    private fun draw(chroma: Chroma, size: Int, scale: Float, yuv: (Int, Int, Int) -> IntArray): IntArray {
        val l = VideoFrames.Layout(size, size, chroma)
        val mem = Memory(l.bytes)
        val wide = chroma == Chroma.P010
        fun sample(offset: Long, value8: Int) {
            if (wide) mem.setShort(offset, ((value8 * 4) shl 6).toShort()) else mem.setByte(offset, value8.toByte())
        }
        val bps = chroma.bytesPerSample
        for (row in 0 until l.lumaLines) for (x in 0 until size) {
            sample(row.toLong() * l.lumaPitch + x * bps, yuv(x, minOf(row, size - 1), 0)[0])
        }
        for (row in 0 until l.chromaLines) for (x in 0 until l.chromaWidth) {
            val c = yuv(x, minOf(row, l.chromaHeight - 1), 1)
            if (wide) {
                val at = l.uOffset + row.toLong() * l.chromaPitch + x * 2 * bps
                sample(at, c[1])
                sample(at + bps, c[2])
            } else {
                sample(l.uOffset + row.toLong() * l.chromaPitch + x, c[1])
                sample(l.vOffset + row.toLong() * l.chromaPitch + x, c[2])
            }
        }
        val out = (size * scale).toInt()
        val surface = Surface.makeRasterN32Premul(out, out)
        drawVideoFrame(surface.canvas, VideoFrames.Frame(mem, l), 0f, 0f, scale, Paint())
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(out, out)
        surface.readPixels(bitmap, 0, 0)
        return IntArray(out * out * 3) { i ->
            val p = bitmap.getColor((i / 3) % out, (i / 3) / out)
            when (i % 3) { 0 -> Color.getR(p); 1 -> Color.getG(p); else -> Color.getB(p) }
        }
    }
}
