package com.ks.jaiplayer.player

enum class PlaybackStatus { Idle, Opening, Playing, Paused, Ended, Error }

/** Why playback failed; the interface turns each into a sentence in the user's language. */
enum class PlayerError {
    /** The native engine (libVLC on desktop) could not be found or loaded. */
    EngineMissing,

    /** The file could not be opened or decoded. */
    CannotOpen,

    /** Playback stopped part way through. */
    Playback,
}

/** An audio or subtitle track. [name] and [language] are as the file states them, if at all. */
data class Track(val id: Int, val name: String? = null, val language: String? = null)

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val media: MediaRef? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 100,
    val muted: Boolean = false,
    val speed: Float = 1f,
    val hasVideo: Boolean = false,
    val audioTracks: List<Track> = emptyList(),
    val subtitleTracks: List<Track> = emptyList(),
    val audioTrackId: Int? = null,
    /** The subtitle track shown, or null for none. */
    val subtitleTrackId: Int? = null,
    val error: PlayerError? = null,
    /**
     * The audio goes out still encoded (Dolby or DTS passthrough to a TV or receiver), so audio
     * effects cannot apply to it.
     */
    val audioPassthrough: Boolean = false,
    /** Set when audio could not be played and the engine carried on without it, or with another track. */
    val audioNotice: AudioNotice? = null,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.Playing
    val canSeek: Boolean get() = durationMs > 0
}
