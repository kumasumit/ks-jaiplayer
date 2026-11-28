package com.ks.jaiplayer.vlc

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Pointer

/**
 * The part of the libVLC 3.x C API that KS JaiPlayer uses, called through JNA. libVLC is
 * LGPL-2.1+ and is loaded at run time from the copy bundled in the app; nothing is linked statically.
 *
 * Names and arguments follow `vlc/libvlc*.h` of VLC 3.0. Pointers to libVLC objects are opaque.
 */
@Suppress("FunctionName", "ktlint:standard:function-naming")
internal interface LibVlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>?): Pointer?
    fun libvlc_release(instance: Pointer)
    fun libvlc_get_version(): String
    fun libvlc_errmsg(): String?

    fun libvlc_media_new_path(instance: Pointer, path: String): Pointer?
    fun libvlc_media_release(media: Pointer)
    /** Fills a libvlc_media_stats_t (15 four-byte fields); returns 1 when the stats are valid. */
    fun libvlc_media_get_stats(media: Pointer, stats: Pointer): Int
    fun libvlc_media_player_get_media(player: Pointer): Pointer?
    fun libvlc_media_add_option(media: Pointer, option: String)
    /** Synchronous parse (local files only here): fills the media's track list. */
    fun libvlc_media_parse(media: Pointer)
    fun libvlc_media_tracks_get(media: Pointer, tracks: com.sun.jna.ptr.PointerByReference): Int
    fun libvlc_media_tracks_release(tracks: Pointer, count: Int)

    fun libvlc_media_player_new(instance: Pointer): Pointer?
    fun libvlc_media_player_release(player: Pointer)
    fun libvlc_media_player_set_media(player: Pointer, media: Pointer?)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_set_pause(player: Pointer, doPause: Int)
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_get_length(player: Pointer): Long
    fun libvlc_media_player_get_time(player: Pointer): Long
    fun libvlc_media_player_set_time(player: Pointer, time: Long)
    fun libvlc_media_player_get_state(player: Pointer): Int

    fun libvlc_media_player_set_rate(player: Pointer, rate: Float): Int

    fun libvlc_audio_get_volume(player: Pointer): Int
    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int
    fun libvlc_audio_set_mute(player: Pointer, mute: Int)

    // Equalizer (libVLC 3.0): ten bands at 60 Hz … 16 kHz, -20 to 20 dB. The player copies the
    // values, so the equalizer may be freed right after libvlc_media_player_set_equalizer.
    fun libvlc_audio_equalizer_new(): Pointer?
    fun libvlc_audio_equalizer_release(equalizer: Pointer)
    fun libvlc_audio_equalizer_set_preamp(equalizer: Pointer, preamp: Float): Int
    fun libvlc_audio_equalizer_set_amp_at_index(equalizer: Pointer, amp: Float, band: Int): Int
    /** [equalizer] null turns the equalizer off (the audio passes unchanged). */
    fun libvlc_media_player_set_equalizer(player: Pointer, equalizer: Pointer?): Int

    fun libvlc_audio_get_track_description(player: Pointer): Pointer?
    fun libvlc_audio_get_track(player: Pointer): Int
    fun libvlc_audio_set_track(player: Pointer, id: Int): Int
    fun libvlc_video_get_spu_description(player: Pointer): Pointer?
    fun libvlc_video_get_spu(player: Pointer): Int
    fun libvlc_video_set_spu(player: Pointer, id: Int): Int
    fun libvlc_track_description_list_release(list: Pointer)

    /** [type] 0 is a subtitle; [uri] is a `file:///` URI; [select] is a C bool (0 or 1). */
    fun libvlc_media_player_add_slave(player: Pointer, type: Int, uri: String, select: Int): Int

    fun libvlc_video_set_callbacks(player: Pointer, lock: LockCallback, unlock: UnlockCallback?, display: DisplayCallback?, opaque: Pointer?)
    fun libvlc_video_set_format_callbacks(player: Pointer, setup: FormatCallback, cleanup: CleanupCallback?)

    /** `void *(*lock)(void *opaque, void **planes)`: point `planes[0]` at the frame buffer. */
    fun interface LockCallback : Callback { fun invoke(opaque: Pointer?, planes: Pointer): Pointer? }

    /** `void (*unlock)(void *opaque, void *picture, void *const *planes)`. */
    fun interface UnlockCallback : Callback { fun invoke(opaque: Pointer?, picture: Pointer?, planes: Pointer?) }

    /** `void (*display)(void *opaque, void *picture)`: a decoded frame is ready to show. */
    fun interface DisplayCallback : Callback { fun invoke(opaque: Pointer?, picture: Pointer?) }

    /**
     * `unsigned (*setup)(void **opaque, char *chroma, unsigned *width, unsigned *height,
     * unsigned *pitches, unsigned *lines)`: choose the pixel format; returns the buffer count.
     */
    fun interface FormatCallback : Callback {
        fun invoke(opaque: Pointer?, chroma: Pointer, width: Pointer, height: Pointer, pitches: Pointer, lines: Pointer): Int
    }

    /** `void (*cleanup)(void *opaque)`. */
    fun interface CleanupCallback : Callback { fun invoke(opaque: Pointer?) }

    companion object {
        // libvlc_state_t
        const val STATE_NOTHING = 0
        const val STATE_OPENING = 1
        const val STATE_BUFFERING = 2
        const val STATE_PLAYING = 3
        const val STATE_PAUSED = 4
        const val STATE_STOPPED = 5
        const val STATE_ENDED = 6
        const val STATE_ERROR = 7

        const val SLAVE_SUBTITLE = 0
    }
}
