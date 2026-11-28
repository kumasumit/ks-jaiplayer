package com.ks.jaiplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.jaiplayer.player.AudioNotice
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.player.PlayerController
import com.ks.jaiplayer.player.PlayerError
import com.ks.jaiplayer.player.formatPlaybackTime
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.common_pause
import com.ks.jaiplayer.resources.player_time_of
import com.ks.jaiplayer.resources.player_fit_screen
import com.ks.jaiplayer.resources.player_fill_screen
import com.ks.jaiplayer.resources.player_brightness
import com.ks.jaiplayer.resources.audio_notice_no_audio
import com.ks.jaiplayer.resources.audio_notice_other_track
import com.ks.jaiplayer.resources.error_cannot_open
import com.ks.jaiplayer.resources.error_playback
import com.ks.jaiplayer.resources.error_vlc_missing_title
import com.ks.jaiplayer.resources.player_close
import com.ks.jaiplayer.resources.player_exit_full_screen
import com.ks.jaiplayer.resources.player_full_screen
import com.ks.jaiplayer.resources.player_mute
import com.ks.jaiplayer.resources.player_play
import com.ks.jaiplayer.resources.player_seek_back
import com.ks.jaiplayer.resources.player_seek_forward
import com.ks.jaiplayer.resources.player_unmute
import com.ks.jaiplayer.resources.player_volume
import kotlinx.coroutines.delay
import androidx.compose.runtime.CompositionLocalProvider
import com.ks.jaiplayer.player.Playlist
import com.ks.jaiplayer.player.formatSpeed
import com.ks.jaiplayer.player.playbackSpeeds
import com.ks.jaiplayer.player.stepSpeed
import com.ks.jaiplayer.player.MIN_SPEED
import com.ks.jaiplayer.player.MAX_SPEED
import com.ks.jaiplayer.plugin.ControlSlot
import com.ks.jaiplayer.resources.player_episode_of
import com.ks.jaiplayer.resources.player_episodes
import com.ks.jaiplayer.resources.player_next
import com.ks.jaiplayer.resources.player_previous
import com.ks.jaiplayer.resources.player_speed
import com.ks.jaiplayer.resources.player_speed_normal
import com.ks.jaiplayer.resources.player_speed_slower
import com.ks.jaiplayer.resources.player_speed_faster
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val HIDE_AFTER_MS = 3_000L
private const val SCROLL_HINT_MS = 700L

/**
 * Turns desktop scrolling into seeking and volume. Sideways movement (a trackpad swipe) adds up
 * and seeks 2 seconds per step, vertical movement changes the volume 5 per step; the stronger
 * direction of each event wins, so a slightly diagonal swipe does one thing.
 */
private class DesktopScroll {
    private var sideways = 0f
    private var vertical = 0f

    fun handle(dx: Float, dy: Float, state: PlaybackState, controller: PlayerController): GestureHint? {
        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (!state.canSeek) return null
            sideways += dx
            val steps = sideways.toInt()
            if (steps == 0) return null
            sideways -= steps
            val target = (state.positionMs + steps * SEEK_PER_STEP_MS).coerceIn(0, state.durationMs)
            controller.seekTo(target)
            return GestureHint.Seek(target, target - state.positionMs)
        }
        vertical += dy
        val steps = vertical.toInt()
        if (steps == 0) return null
        vertical -= steps
        val level = (state.volume - steps * VOLUME_PER_STEP).coerceIn(0, PlayerController.MAX_VOLUME)
        controller.setVolume(level)
        return GestureHint.Volume(level)
    }

    private companion object {
        const val SEEK_PER_STEP_MS = 2_000L
        const val VOLUME_PER_STEP = 5
    }
}
private val SEEK_ACTIONS = setOf(PlayerAction.SeekBack, PlayerAction.SeekForward, PlayerAction.SeekBackLong, PlayerAction.SeekForwardLong)

@Composable
fun PlayerScreen(app: JaiPlayerState) {
    val state by app.engine.state.collectAsState()
    val form = LocalFormFactor.current
    val controller = app.controller

    // Every interaction bumps the counter; the controls hide 3 s after the last one while playing.
    var interaction by remember { mutableIntStateOf(0) }
    var controlsShown by remember { mutableStateOf(true) }
    fun poke() { controlsShown = true; interaction++ }
    var menuOpen by remember { mutableStateOf(false) }
    // Phone: pinch switches between fitting and filling the screen; gestures show a hint.
    var fill by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<GestureHint?>(null) }
    var fastSpeedBefore by remember { mutableStateOf<Float?>(null) }
    // Desktop: a hint from the trackpad or wheel fades a moment after the last scroll.
    var scrollHintAt by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrollHintAt) {
        if (scrollHintAt > 0) { delay(SCROLL_HINT_MS); if (fastSpeedBefore == null) hint = null }
    }
    val scroll = remember { DesktopScroll() }
    LaunchedEffect(interaction, state.isPlaying, menuOpen) {
        if (state.isPlaying && !menuOpen) {
            delay(HIDE_AFTER_MS)
            controlsShown = false
        } else {
            controlsShown = true
        }
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    // Desktop: Tab moves through the buttons; Enter presses the focused one, Space stays play/pause.
    var controlFocused by remember { mutableStateOf(false) }
    // TV: controls brought up by a seek key open on the seek bar, so holding the key keeps seeking.
    var focusSeekBar by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(controlsShown) {
        // Desktop: when the controls hide, a button that had focus goes with them; the player takes it back.
        if (form == FormFactor.Desktop && !controlsShown) runCatching { rootFocus.requestFocus() }
        if (form == FormFactor.Tv) runCatching {
            when {
                !controlsShown -> rootFocus.requestFocus()
                focusSeekBar -> seekFocus.requestFocus()
                else -> playFocus.requestFocus()
            }
            focusSeekBar = false
        }
    }

    fun handle(event: KeyEvent, tvControlsShown: Boolean): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val action = actionForKey(event.key, event.isShiftPressed, tvControlsShown)
        if (action == null) {
            val pluginAction = if (tvControlsShown) null else app.plugins.keyAction(event.key)
            pluginAction?.invoke() ?: return false
            poke()
            return true
        }
        if (form == FormFactor.Tv && !tvControlsShown && !controlsShown && action in SEEK_ACTIONS) focusSeekBar = true
        poke()
        // TV: up and down bring up the controls; the remote has its own volume keys for the TV.
        if (form == FormFactor.Tv && action in setOf(PlayerAction.VolumeUp, PlayerAction.VolumeDown)) return true
        when (action) {
            PlayerAction.NextItem -> app.next()
            PlayerAction.PreviousItem -> app.previous()
            PlayerAction.ToggleFullScreen -> app.fullScreen = !app.fullScreen
            PlayerAction.Back -> when {
                app.fullScreen && form == FormFactor.Desktop -> app.fullScreen = false
                form == FormFactor.Tv && controlsShown && state.isPlaying -> controlsShown = false
                else -> app.closePlayer()
            }
            else -> controller.perform(action)
        }
        return true
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            // Desktop: keys always drive the player, whatever has focus. TV: while the controls
            // are shown, the remote moves focus between them first.
            .onPreviewKeyEvent {
                val enterOnButton = form == FormFactor.Desktop && controlFocused && it.key in setOf(Key.Enter, Key.NumPadEnter)
                if (enterOnButton) false else if (form != FormFactor.Tv || !controlsShown) handle(it, false) else false
            }
            .onKeyEvent { if (form == FormFactor.Tv) handle(it, true) else false }
            .focusRequester(rootFocus)
            .focusable()
            .mediaDropTarget { files -> app.openAll(files) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Move || e.type == PointerEventType.Press) poke()
                        // Desktop: a two-finger sideways swipe seeks; the wheel or a vertical swipe changes the volume.
                        if (form == FormFactor.Desktop && e.type == PointerEventType.Scroll) {
                            val delta = e.changes.first().scrollDelta
                            scroll.handle(delta.x, delta.y, app.engine.state.value, controller)?.let { hint = it; scrollHintAt++ }
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .then(
                if (form == FormFactor.Phone) Modifier.phoneGestures(
                    state = { app.engine.state.value }, controller = controller, brightness = app.host.brightness,
                    fill = { fill }, onFill = { fill = it }, onHint = { hint = it },
                ) else Modifier,
            )
            .pointerInput(form) {
                detectTapGestures(
                    // Phone: a long press plays at 2× until the finger lifts.
                    onPress = {
                        tryAwaitRelease()
                        fastSpeedBefore?.let { app.engine.setSpeed(it); fastSpeedBefore = null; hint = null }
                    },
                    onLongPress = {
                        if (form == FormFactor.Phone && app.engine.state.value.isPlaying && fastSpeedBefore == null) {
                            fastSpeedBefore = app.engine.state.value.speed
                            app.engine.setSpeed(2f)
                            hint = GestureHint.Fast
                        }
                    },
                    onDoubleTap = { offset ->
                        when (form) {
                            FormFactor.Desktop -> app.fullScreen = !app.fullScreen
                            else -> controller.seekBy(
                                if (offset.x < size.width / 2) -PlayerController.SEEK_STEP_MS else PlayerController.SEEK_STEP_MS,
                            )
                        }
                    },
                )
            },
    ) {
        VideoSurface(app.engine, Modifier.fillMaxSize(), fill = fill)
        if (!state.hasVideo && state.status != PlaybackStatus.Opening) AudioOnly(state)
        state.error?.let { ErrorBanner(it, state, Modifier.align(Alignment.Center)) }
        // Above the controls, and shown even when they are hidden.
        AudioNoticeBanner(state, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 72.dp))
        hint?.let { GestureHintBadge(it, Modifier.align(Alignment.Center)) }

        // Reduce Motion (a system setting): the controls appear and go without fading.
        val reduce = app.host.reduceMotion
        AnimatedVisibility(
            // In a picture-in-picture window the system draws the buttons; the app's would be tiny.
            controlsShown && !app.host.inPictureInPicture,
            enter = if (reduce) EnterTransition.None else fadeIn(),
            exit = if (reduce) ExitTransition.None else fadeOut(),
            modifier = Modifier.fillMaxSize().onFocusChanged { controlFocused = it.hasFocus },
        ) {
            CompositionLocalProvider(LocalMenuOpen provides { open: Boolean -> menuOpen = open; poke() }) {
                Controls(app, state, playFocus, seekFocus)
            }
        }
    }
}

@Composable
private fun GestureHintBadge(hint: GestureHint, modifier: Modifier) {
    val text = when (hint) {
        is GestureHint.Seek -> {
            val sign = if (hint.deltaMs < 0) "−" else "+"
            "${formatPlaybackTime(hint.targetMs)}   ($sign${formatPlaybackTime(kotlin.math.abs(hint.deltaMs))})"
        }
        is GestureHint.Volume -> "${stringResource(Res.string.player_volume)}  ${hint.level}"
        is GestureHint.Brightness -> "${stringResource(Res.string.player_brightness)}  ${(hint.level * 100).toInt()}"
        is GestureHint.Fill -> stringResource(if (hint.fill) Res.string.player_fill_screen else Res.string.player_fit_screen)
        GestureHint.Fast -> "2×  ▶▶"
    }
    Surface(modifier, color = Color(0xCC1D2025), shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AudioOnly(state: PlaybackState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(JaiIcons.Audio, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text(state.media?.title.orEmpty(), fontSize = 22.sp, color = Color.White)
        }
    }
}

@Composable
private fun ErrorBanner(error: PlayerError, state: PlaybackState, modifier: Modifier) {
    val text = when (error) {
        PlayerError.EngineMissing -> stringResource(Res.string.error_vlc_missing_title)
        PlayerError.CannotOpen -> stringResource(Res.string.error_cannot_open, state.media?.title.orEmpty())
        PlayerError.Playback -> stringResource(Res.string.error_playback)
    }
    Surface(modifier, color = Color(0xCC1D2025), shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(horizontal = 20.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.error)
    }
}

/** How long the message about unplayable audio stays on screen. */
private const val AUDIO_NOTICE_MS = 8_000L

@Composable
private fun AudioNoticeBanner(state: PlaybackState, modifier: Modifier) {
    val notice = state.audioNotice ?: return
    var shown by remember(state.media, notice) { mutableStateOf(true) }
    LaunchedEffect(state.media, notice) { delay(AUDIO_NOTICE_MS); shown = false }
    if (!shown) return
    Surface(modifier.padding(horizontal = 16.dp), color = Color(0xCC1D2025), shape = RoundedCornerShape(12.dp)) {
        Text(stringResource(notice.message), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White)
    }
}

/** The sentence for each notice. */
internal val AudioNotice.message: StringResource
    get() = when (this) {
        AudioNotice.OtherTrack -> Res.string.audio_notice_other_track
        AudioNotice.NoAudio -> Res.string.audio_notice_no_audio
    }

@Composable
private fun Controls(app: JaiPlayerState, state: PlaybackState, playFocus: FocusRequester, seekFocus: FocusRequester) {
    val m = LocalMetrics.current
    val form = LocalFormFactor.current
    val controller = app.controller
    val scrim = Color.Black.copy(alpha = 0.65f)

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Top: back and the title.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(scrim, Color.Transparent)))
                // macOS: the traffic lights sit in a transparent title bar above this row.
                .padding(top = app.host.titleBarInsetDp.dp)
                .padding(m.safe),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (form != FormFactor.Tv) {
                ControlButton(JaiIcons.ArrowBack, stringResource(Res.string.player_close), app::closePlayer)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                state.media?.title.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val list = app.playlist
            if (list != null && list.size > 1) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(Res.string.player_episode_of, (list.index + 1).toString(), list.size.toString()),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Bottom: seek bar and buttons.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                .padding(m.safe),
        ) {
            SeekBar(state, controller, seekFocus)
            val list = app.playlist
            val series = list != null && list.size > 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (series) {
                    ControlButton(JaiIcons.Previous, stringResource(Res.string.player_previous), app::previous, enabled = list.hasPrevious)
                }
                ControlButton(
                    if (state.isPlaying) JaiIcons.Pause else JaiIcons.Play,
                    stringResource(if (state.isPlaying) Res.string.common_pause else Res.string.player_play),
                    controller::togglePlay,
                    Modifier.focusRequester(playFocus),
                )
                if (series) {
                    ControlButton(JaiIcons.Next, stringResource(Res.string.player_next), app::next, enabled = list.hasNext)
                }
                ControlButton(
                    JaiIcons.Back10,
                    pluralStringResource(Res.plurals.player_seek_back, 10, "10"),
                    { controller.seekBy(-PlayerController.SEEK_STEP_MS) },
                )
                ControlButton(
                    JaiIcons.Forward10,
                    pluralStringResource(Res.plurals.player_seek_forward, 10, "10"),
                    { controller.seekBy(PlayerController.SEEK_STEP_MS) },
                )
                Spacer(Modifier.width(m.gap))
                ControlButton(
                    if (state.muted || state.volume == 0) JaiIcons.Muted else JaiIcons.Volume,
                    stringResource(if (state.muted) Res.string.player_unmute else Res.string.player_mute),
                    { app.engine.setMuted(!state.muted) },
                )
                if (form == FormFactor.Desktop) VolumeSlider(state, controller)
                PluginControls(app, ControlSlot.Start, state)
                Spacer(Modifier.weight(1f))
                // A phone has no room for everything in one row: the rest goes in a row of its own.
                if (form != FormFactor.Phone) {
                    SpeedMenu(app, state)
                    if (series) EpisodeMenu(app, list)
                    PluginControls(app, ControlSlot.End, state)
                }
                if (form == FormFactor.Desktop) {
                    ControlButton(
                        if (app.fullScreen) JaiIcons.ExitFullScreen else JaiIcons.FullScreen,
                        stringResource(if (app.fullScreen) Res.string.player_exit_full_screen else Res.string.player_full_screen),
                        { app.fullScreen = !app.fullScreen },
                    )
                }
            }
            if (form == FormFactor.Phone) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    SpeedMenu(app, state)
                    if (series) EpisodeMenu(app, list)
                    PluginControls(app, ControlSlot.End, state)
                }
            }
        }
    }
}

@Composable
private fun PluginControls(app: JaiPlayerState, slot: ControlSlot, state: PlaybackState) {
    app.plugins.controls.filter { it.first == slot }.forEach { (_, content) -> content(state) }
}

@Composable
private fun SpeedMenu(app: JaiPlayerState, state: PlaybackState) {
    TextControlMenu(formatSpeed(state.speed), stringResource(Res.string.player_speed)) { close ->
        // Fine control first: − and + move by 0.05× and keep the menu open, so any speed from
        // 0.25× to 4× can be set; the usual speeds follow as one-tap choices.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            SpeedStepButton("−", stringResource(Res.string.player_speed_slower), state.speed > MIN_SPEED) {
                app.engine.setSpeed(stepSpeed(app.engine.state.value.speed, faster = false))
            }
            Text(
                formatSpeed(state.speed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            SpeedStepButton("+", stringResource(Res.string.player_speed_faster), state.speed < MAX_SPEED) {
                app.engine.setSpeed(stepSpeed(app.engine.state.value.speed, faster = true))
            }
        }
        HorizontalDivider()
        playbackSpeeds.forEach { speed ->
            val text = if (speed == 1f) "${formatSpeed(speed)}  ·  ${stringResource(Res.string.player_speed_normal)}" else formatSpeed(speed)
            MenuChoice(text, kotlin.math.abs(state.speed - speed) < 0.001f) { app.engine.setSpeed(speed); close() }
        }
    }
}

@Composable
private fun SpeedStepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label }.tvFocusRing(RoundedCornerShape(20.dp)),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun EpisodeMenu(app: JaiPlayerState, list: Playlist) {
    IconControlMenu(JaiIcons.Episodes, stringResource(Res.string.player_episodes)) { close ->
        list.items.forEachIndexed { i, media ->
            MenuChoice("${i + 1}.  ${media.title}", i == list.index) { app.jumpTo(i); close() }
        }
    }
}

@Composable
private fun SeekBar(state: PlaybackState, controller: PlayerController, focus: FocusRequester) {
    val form = LocalFormFactor.current
    val focusManager = LocalFocusManager.current
    // While the thumb is dragged the bar shows the drag position and the picture follows it
    // (engines coalesce the seeks); letting go seeks to the exact spot.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = dragging?.let { (it * duration).toLong() } ?: state.positionMs
    val timeOf = stringResource(Res.string.player_time_of, formatPlaybackTime(shownMs), formatPlaybackTime(state.durationMs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(formatPlaybackTime(shownMs))
        Slider(
            value = fraction,
            onValueChange = {
                dragging = it
                controller.seekTo((it * duration).toLong())
            },
            onValueChangeFinished = {
                dragging?.let { controller.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.canSeek,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(focus).desktopNoFocus().tvFocusRing(RoundedCornerShape(12.dp))
                // Screen readers say the time ("1:23 of 40:00"), not a percentage.
                .semantics { stateDescription = timeOf }
                .onPreviewKeyEvent { e -> if (form == FormFactor.Tv) tvSeekBarKey(e, controller, focusManager) else false },
        )
        TimeText(formatPlaybackTime(state.durationMs))
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
}

@Composable
private fun VolumeSlider(state: PlaybackState, controller: PlayerController) {
    val volumeLabel = stringResource(Res.string.player_volume)
    var value by remember { mutableFloatStateOf(state.volume.toFloat()) }
    LaunchedEffect(state.volume) { value = state.volume.toFloat() }
    Slider(
        value = if (state.muted) 0f else value,
        onValueChange = { value = it; controller.setVolume(it.toInt()) },
        valueRange = 0f..PlayerController.MAX_VOLUME.toFloat(),
        modifier = Modifier.width(110.dp).desktopNoFocus().semantics { contentDescription = volumeLabel },
        colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
    )
    Spacer(Modifier.width(4.dp))
    Text("${if (state.muted) 0 else state.volume}", color = Color.White, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(28.dp))
}

/**
 * TV remote on the seek bar: left/right jump 10 s (the slider's own step would be a tiny fraction
 * of a long film), up/down leave the bar, so focus is never trapped in it.
 */
private fun tvSeekBarKey(e: KeyEvent, controller: PlayerController, focusManager: FocusManager): Boolean {
    if (e.type != KeyEventType.KeyDown) return e.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
    when (e.key) {
        // Holding left or right seeks further the longer it is held, as the keyboard's arrows do.
        Key.DirectionLeft -> controller.perform(PlayerAction.SeekBack)
        Key.DirectionRight -> controller.perform(PlayerAction.SeekForward)
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        else -> return false
    }
    return true
}
