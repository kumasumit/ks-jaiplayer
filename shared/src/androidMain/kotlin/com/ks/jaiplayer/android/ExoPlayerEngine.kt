package com.ks.jaiplayer.android

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.ks.jaiplayer.audio.AudioChain
import com.ks.jaiplayer.audio.AudioSupport
import com.ks.jaiplayer.player.AudioFallback
import com.ks.jaiplayer.player.AudioNotice
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerController.Companion.SEEK_STEP_MS
import com.ks.jaiplayer.player.clampSpeed
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.player.PlayerError
import com.ks.jaiplayer.player.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Android and Google TV engine: Media3 ExoPlayer with the device's hardware decoders. When a
 * decoder fails, the next one for the format is tried; audio that still cannot be decoded gives way to
 * another audio track, or to the picture alone ([AudioFallback]). It takes audio focus (pauses for a call or
 * another player, lowers its volume for a notification) and pauses when headphones are unplugged.
 * Every call must come from the main thread, as ExoPlayer requires; the interface does that.
 *
 * Track ids are positions in ExoPlayer's track-group list of that type, so they stay valid
 * until the media changes.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerEngine(context: Context) : PlayerEngine {
    /** The audio effects (specs/audio-effects.md), on decoded audio before Media3's speed change. */
    private val effects = EffectsAudioProcessor()

    internal val player: ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        object : DefaultRenderersFactory(context.applicationContext) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(effects))
                    .build()
        }.setEnableDecoderFallback(true),
    )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        // The media session's and the remote's skip buttons move as far as the player's arrows.
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()

    /**
     * Google TV: switch the display to the video's frame rate when that is seamless (Media3's
     * default and Google TV's recommendation); off keeps the TV's own rate.
     */
    fun setFrameRateMatching(on: Boolean) {
        player.videoChangeFrameRateStrategy =
            if (on) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    }

    /**
     * Google TV: tunnelled playback hands decoding and timing to the TV's video pipeline, which
     * helps 4K on some low-end TVs and hurts on others, so it is the viewer's switch.
     */
    fun setTunneling(on: Boolean) {
        (player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector)?.let { selector ->
            selector.parameters = selector.buildUponParameters().setTunnelingEnabled(on).build()
        }
    }

    /** True once the file's tracks are known and none is video (music, podcasts, audiobooks). */
    fun isAudioOnly(): Boolean = !player.currentTracks.isEmpty && !player.currentTracks.containsType(C.TRACK_TYPE_VIDEO)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var volume = 100
    private var muted = false
    private var subtitles = emptyList<MediaItem.SubtitleConfiguration>()
    private var everPlayed = false
    private var videoEffects: AudioChain? = null
    private var musicEffects: AudioChain? = null
    private var passthrough = false
    private val audioFallback = AudioFallback<TrackGroup>()
    private var audioNotice: AudioNotice? = null

    override val audioSupport = AudioSupport.Full

    override fun setAudioEffects(video: AudioChain?, music: AudioChain?) {
        videoEffects = video
        musicEffects = music
        applyEffects()
    }

    /** Music settings once the file is known to have no video; video settings otherwise. */
    private fun applyEffects() = effects.setChain(if (isAudioOnly()) musicEffects else videoEffects)

    init {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
                passthrough = !Util.isEncodingLinearPcm(audioTrackConfig.encoding)
                refresh()
            }
        })
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TRACKS_CHANGED)) applyEffects()
                refresh()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (recoverFromAudioError(error)) return
                _state.update {
                    it.copy(status = PlaybackStatus.Error, error = if (everPlayed) PlayerError.Playback else PlayerError.CannotOpen)
                }
            }
        })
        scope.launch {
            while (isActive) {
                if (player.isPlaying) refresh()
                delay(250)
            }
        }
    }

    /**
     * An audio decoder or audio output failure stops ExoPlayer altogether. Playback is prepared
     * again, from the same position, with another audio track or with audio switched off; video
     * stays on its hardware decoder. False when the error is not about audio.
     */
    private fun recoverFromAudioError(error: PlaybackException): Boolean {
        val failure = error as? ExoPlaybackException ?: return false
        if (failure.type != ExoPlaybackException.TYPE_RENDERER) return false
        if (!MimeTypes.isAudio(failure.rendererFormat?.sampleMimeType)) return false
        val tracks = player.currentTracks
        val broken = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return false
        val playable = groupsOf(tracks, C.TRACK_TYPE_AUDIO).map { it.mediaTrackGroup }
        val next = audioFallback.after(broken.mediaTrackGroup, playable, tracks.containsType(C.TRACK_TYPE_VIDEO))
        val parameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        when (next) {
            is AudioFallback.Next.Track -> {
                parameters.setOverrideForType(TrackSelectionOverride(next.track, 0))
                audioNotice = AudioNotice.OtherTrack
            }
            AudioFallback.Next.PictureOnly -> {
                parameters.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                audioNotice = AudioNotice.NoAudio
            }
            AudioFallback.Next.Stop -> return false
        }
        player.trackSelectionParameters = parameters.build()
        player.prepare()
        return true
    }

    /** The file has audio, but the device has no decoder for any of it, so ExoPlayer plays the picture alone. */
    private fun noSupportedAudio(tracks: Tracks): Boolean {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        return audio.isNotEmpty() && audio.none { it.isSupported || it.isSelected } && tracks.containsType(C.TRACK_TYPE_VIDEO)
    }

    private fun refresh() {
        val tracks = player.currentTracks
        val status = when {
            _state.value.status == PlaybackStatus.Error && player.playerError != null -> PlaybackStatus.Error
            player.currentMediaItem == null -> PlaybackStatus.Idle
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
            player.playbackState == Player.STATE_BUFFERING && !everPlayed -> PlaybackStatus.Opening
            player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) -> PlaybackStatus.Playing
            else -> PlaybackStatus.Paused
        }
        if (player.isPlaying) everPlayed = true
        _state.update {
            it.copy(
                status = status,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                speed = player.playbackParameters.speed,
                hasVideo = player.videoSize.width > 0,
                audioTracks = tracksOf(tracks, C.TRACK_TYPE_AUDIO),
                subtitleTracks = tracksOf(tracks, C.TRACK_TYPE_TEXT),
                audioTrackId = selectedOf(tracks, C.TRACK_TYPE_AUDIO),
                subtitleTrackId = selectedOf(tracks, C.TRACK_TYPE_TEXT),
                error = if (status == PlaybackStatus.Error) it.error else null,
                audioPassthrough = passthrough,
                audioNotice = if (noSupportedAudio(tracks)) AudioNotice.NoAudio else audioNotice,
            )
        }
    }

    private fun groupsOf(tracks: Tracks, type: Int) = tracks.groups.filter { it.type == type && it.isSupported }

    private fun tracksOf(tracks: Tracks, type: Int) = groupsOf(tracks, type).mapIndexed { i, g ->
        val format = g.getTrackFormat(0)
        Track(i, format.label, format.language?.takeUnless { it == C.LANGUAGE_UNDETERMINED })
    }

    private fun selectedOf(tracks: Tracks, type: Int): Int? =
        groupsOf(tracks, type).indexOfFirst { it.isSelected }.takeIf { it >= 0 }

    /** The video's size in pixels, (0, 0) before it is known; for picture-in-picture. */
    fun videoSize(): Pair<Int, Int> = player.videoSize.let { it.width to it.height }

    override fun open(media: MediaRef) {
        everPlayed = false
        subtitles = emptyList()
        // The last file's failed audio must not silence this one.
        audioFallback.reset()
        audioNotice = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build()
        _state.update {
            PlaybackState(status = PlaybackStatus.Opening, media = media, volume = it.volume, muted = it.muted, speed = it.speed)
        }
        // The title shows in the media session: picture-in-picture, Bluetooth, the TV's now-playing card.
        val metadata = MediaMetadata.Builder().setTitle(media.title).setDisplayTitle(media.title).build()
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(media.uri)).setMediaMetadata(metadata).build())
        player.prepare()
        player.play()
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun setVolume(volume: Int) {
        this.volume = volume.coerceIn(0, 100)
        applyVolume()
        _state.update { it.copy(volume = this.volume) }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        applyVolume()
        _state.update { it.copy(muted = muted) }
    }

    private fun applyVolume() {
        player.volume = if (muted) 0f else volume / 100f
    }

    override fun setSpeed(speed: Float) {
        val clamped = clampSpeed(speed)
        player.setPlaybackSpeed(clamped)
        _state.update { it.copy(speed = clamped) }
    }

    override fun selectAudioTrack(id: Int) {
        audioNotice = null
        select(C.TRACK_TYPE_AUDIO, id)
    }

    override fun selectSubtitleTrack(id: Int?) {
        if (id == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            select(C.TRACK_TYPE_TEXT, id)
        }
    }

    private fun select(type: Int, id: Int) {
        val group = groupsOf(player.currentTracks, type).getOrNull(id) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** Media3 takes side-loaded subtitles with the media item, so the item is set again in place. */
    override fun addSubtitle(uri: String) {
        val item = player.currentMediaItem ?: return
        val mime = when (uri.substringAfterLast('.').lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        subtitles = subtitles + MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val position = player.currentPosition
        val playing = player.playWhenReady
        player.setMediaItem(item.buildUpon().setSubtitleConfigurations(subtitles).build(), position)
        player.prepare()
        player.playWhenReady = playing
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        _state.update { PlaybackState(volume = it.volume, muted = it.muted, speed = it.speed) }
    }

    override fun release() {
        scope.cancel()
        player.release()
    }
}
