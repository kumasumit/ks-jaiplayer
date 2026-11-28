package com.ks.jaiplayer

import com.ks.jaiplayer.player.AudioFallback
import com.ks.jaiplayer.player.AudioNotice
import com.ks.jaiplayer.ui.message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A failing audio decoder (a phone's Dolby decoder, for example) must never stop a video. */
class AudioFallbackTest {
    private val fallback = AudioFallback<String>()

    @Test
    fun anotherPlayableTrackTakesOver() {
        assertEquals(AudioFallback.Next.Track("aac"), fallback.after("ac3", listOf("ac3", "aac"), hasVideo = true))
    }

    @Test
    fun theOnlyTrackFailingLeavesThePicturePlaying() {
        assertEquals(AudioFallback.Next.PictureOnly, fallback.after("eac3", listOf("eac3"), hasVideo = true))
    }

    @Test
    fun failedTracksAreNeverTriedAgain() {
        val tracks = listOf("ac3", "eac3")
        assertEquals(AudioFallback.Next.Track("eac3"), fallback.after("ac3", tracks, hasVideo = true))
        assertEquals(AudioFallback.Next.PictureOnly, fallback.after("eac3", tracks, hasVideo = true))
        // Chosen again by the viewer: still no way back to a broken track.
        assertEquals(AudioFallback.Next.PictureOnly, fallback.after("ac3", tracks, hasVideo = true))
    }

    @Test
    fun soundOnlyMediaWithNothingLeftStops() {
        assertEquals(AudioFallback.Next.Stop, fallback.after("ac3", listOf("ac3"), hasVideo = false))
    }

    @Test
    fun theNextFileStartsAfresh() {
        fallback.after("ac3", listOf("ac3", "aac"), hasVideo = true)
        fallback.reset()
        assertEquals(AudioFallback.Next.Track("ac3"), fallback.after("aac", listOf("ac3", "aac"), hasVideo = true))
    }

    @Test
    fun eachNoticeHasItsOwnMessage() {
        assertNotEquals(AudioNotice.OtherTrack.message, AudioNotice.NoAudio.message)
    }
}
