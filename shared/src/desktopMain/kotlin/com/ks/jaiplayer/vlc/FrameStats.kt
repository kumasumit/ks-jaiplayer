package com.ks.jaiplayer.vlc

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * For measuring smoothness: with JAIPLAYER_PERF=1 the app prints, once a second, how many frames
 * libVLC delivered, how many the window drew, and the slowest draw. Off (and free) otherwise.
 */
internal object FrameStats {
    val enabled = System.getenv("JAIPLAYER_PERF") == "1"
    private val decoded = AtomicInteger()
    private val drawn = AtomicInteger()
    private val slowestDrawNs = AtomicLong()
    @Volatile private var windowStart = System.nanoTime()

    fun frameDecoded() {
        if (enabled) decoded.incrementAndGet()
    }

    @Volatile private var firstFrameLogged = false

    /** With JAIPLAYER_PERF=1: prints a one-off fact, such as the frame layout libVLC delivers. */
    fun note(what: String) {
        if (enabled) System.err.println("jaiplayer-video $what")
    }

    /** With JAIPLAYER_PERF=1: prints how long after the process started [what] happened. */
    fun mark(what: String) {
        if (!enabled) return
        val started = ProcessHandle.current().info().startInstant().orElse(null) ?: return
        System.err.println("jaiplayer-startup $what=${java.time.Duration.between(started, java.time.Instant.now()).toMillis()}")
    }

    fun frameDrawn(drawNs: Long) {
        if (!enabled) return
        if (!firstFrameLogged) {
            firstFrameLogged = true
            // From the process starting to the first video frame on screen (cold start).
            val started = ProcessHandle.current().info().startInstant().orElse(null)
            if (started != null) System.err.println("jaiplayer-startup firstFrameMs=${java.time.Duration.between(started, java.time.Instant.now()).toMillis()}")
        }
        drawn.incrementAndGet()
        slowestDrawNs.accumulateAndGet(drawNs, ::maxOf)
        val now = System.nanoTime()
        if (now - windowStart >= 1_000_000_000L) {
            windowStart = now
            System.err.println(
                "jaiplayer-perf decoded=${decoded.getAndSet(0)} drawn=${drawn.getAndSet(0)} " +
                    "slowestDrawMs=${"%.2f".format(slowestDrawNs.getAndSet(0) / 1e6)}",
            )
        }
    }
}
