package com.ks.jaiplayer.player

/** What the player does about audio it cannot play; the picture always keeps going. */
enum class AudioNotice {
    /** One audio track failed; another one is playing. */
    OtherTrack,

    /** No audio track can be played; the picture plays alone. */
    NoAudio,
}

/**
 * Decides what to play after an audio decoder fails, so that a broken decoder (some phones' Dolby
 * decoder, for example) never stops a video. [T] is the engine's own identity of an audio track.
 * A track that failed is not chosen again for the same file; call [reset] when a file is opened.
 */
class AudioFallback<T> {
    sealed interface Next<out T> {
        data class Track<T>(val track: T) : Next<T>
        data object PictureOnly : Next<Nothing>

        /** Nothing is left to play: the engine reports an error. */
        data object Stop : Next<Nothing>
    }

    private val failed = mutableSetOf<T>()

    fun reset() = failed.clear()

    /** [broken] failed to play; [playable] are the file's audio tracks the device says it supports. */
    fun after(broken: T, playable: List<T>, hasVideo: Boolean): Next<T> {
        failed += broken
        val other = playable.firstOrNull { it !in failed }
        return when {
            other != null -> Next.Track(other)
            hasVideo -> Next.PictureOnly
            else -> Next.Stop
        }
    }
}
