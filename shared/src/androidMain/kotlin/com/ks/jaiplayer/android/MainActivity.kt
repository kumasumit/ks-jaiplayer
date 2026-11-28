package com.ks.jaiplayer.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.plugins.builtInPlugins
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.ui.DeviceSetting
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.settings_match_frame_rate
import com.ks.jaiplayer.resources.settings_match_frame_rate_hint
import com.ks.jaiplayer.resources.settings_background_audio
import com.ks.jaiplayer.resources.settings_background_audio_hint
import com.ks.jaiplayer.ui.BrightnessControl
import com.ks.jaiplayer.resources.settings_system_colours
import com.ks.jaiplayer.resources.settings_system_colours_hint
import com.ks.jaiplayer.resources.settings_tunneling
import com.ks.jaiplayer.resources.settings_tunneling_hint
import com.ks.jaiplayer.ui.FormFactor
import com.ks.jaiplayer.ui.JaiPlayerApp
import com.ks.jaiplayer.ui.JaiPlayerState
import com.ks.jaiplayer.ui.MediaLibrary
import com.ks.jaiplayer.ui.PlatformHost
import com.ks.jaiplayer.ui.Screen
import com.ks.jaiplayer.update.GitHubUpdater

/**
 * The one activity of both Android apps, declared in this module's manifest. The phone app
 * (mobile/android) and the Google TV app (tv/google-tv) only add their launcher entry; which one
 * this is comes from the manifest ([formFactorOf]), never from the device.
 */
class MainActivity : ComponentActivity() {
    private lateinit var app: JaiPlayerState
    private lateinit var engine: ExoPlayerEngine
    private lateinit var session: PlayerSession
    private lateinit var pipActions: PipActions
    /** Whether picture-in-picture may start by itself now (Android 12+); kept for refreshes. */
    private var pipAutoEnter = false
    /** Picture-in-picture now; Compose state, so the player's controls follow it. */
    private val inPip = androidx.compose.runtime.mutableStateOf(false)

    /** The orientation the player wants (its picture's); not applied while in picture-in-picture. */
    private var videoOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // A landscape lock kept in the small window would turn the whole home screen sideways.
        requestedOrientation = if (isInPictureInPictureMode) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else videoOrientation
    }
    /** Null in the Google Play build, which Play updates. */
    private var updater: GitHubUpdater? = null
    private var onMediaPicked: ((MediaRef) -> Unit)? = null
    private var onFolderPicked: ((List<MediaRef>) -> Unit)? = null
    private var onSubtitlePicked: ((String) -> Unit)? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        Documents.keepAccess(this, uri)
        onMediaPicked?.invoke(Documents.mediaRef(this, uri))
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@registerForActivityResult
        Documents.keepAccess(this, tree)
        val items = Documents.mediaInTree(this, tree)
        if (items.isNotEmpty()) onFolderPicked?.invoke(items)
    }
    private var onPermission: ((Boolean) -> Unit)? = null
    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onPermission?.invoke(result.values.any { it })
    }
    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        onSubtitlePicked?.invoke(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val form = formFactorOf(this)
        engine = ExoPlayerEngine(this)
        if (form == FormFactor.Tv) {
            engine.setFrameRateMatching(deviceFlag(MATCH_FRAME_RATE, default = true))
            engine.setTunneling(deviceFlag(TUNNELING, default = false))
        }
        val settings = PrefsStore(getSharedPreferences("settings", MODE_PRIVATE))
        // The Play build has no updater; a DEV build (ID ending .dev) never offers production files.
        updater = if (isPlayBuild(this) || packageName.endsWith(".dev")) null else androidUpdater(this, settings, updatePlatformOf(form))
        app = JaiPlayerState(
            engine = engine,
            host = host(form),
            settings = settings,
            systemLanguages = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it].toLanguageTag() } },
            // Google TV: unfinished files appear on the home screen's Continue watching row.
            plugins = builtInPlugins(onResumeChanged = if (form == FormFactor.Tv) WatchNext(this)::publish else { _ -> }),
            updates = updater,
        )
        session = PlayerSession(
            this, engine,
            hasNext = { app.playlist?.hasNext == true },
            hasPrevious = { app.playlist?.hasPrevious == true },
            onNext = app::next,
            onPrevious = app::previous,
        )
        pipActions = PipActions(this) { app.controller.perform(it) }.also { it.register() }
        // Back closes the browser or the video before it leaves the app. On TV the player's
        // controls take the remote's keys while they show, so Compose never sees Back then.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    app.browse != null -> app.browse = null
                    app.screen == Screen.Player -> app.closePlayer()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
        handleViewIntent(intent)
        setContent {
            // Immersive full screen and screen kept on while a video plays.
            LaunchedEffect(Unit) {
                snapshotFlow { app.screen == Screen.Player }.collect { inPlayer ->
                    applyImmersive(inPlayer)
                    if (!inPlayer) app.host.brightness?.reset()
                }
            }
            // Phone: a video plays in the orientation of its picture (either way round, by the
            // sensor); the rest of the app follows the phone.
            if (form == FormFactor.Phone) LaunchedEffect(Unit) {
                combine(snapshotFlow { app.screen == Screen.Player }, engine.state.map { engine.videoSize() }) { inPlayer, size ->
                    val (w, h) = size
                    when {
                        !inPlayer || w <= 0 || h <= 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        w >= h -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                }.distinctUntilChanged().collect {
                    videoOrientation = it
                    if (!isInPictureInPictureMode) requestedOrientation = it
                }
            }
            LaunchedEffect(Unit) { snapshotFlow { app.playlist }.collect { session.playlistChanged() } }
            // Phone, Android 12+: swiping home while a video plays moves it to picture-in-picture
            // smoothly (auto-enter); the params follow the video's shape.
            if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LaunchedEffect(Unit) {
                combine(snapshotFlow { app.screen == Screen.Player }, engine.state.map { it.isPlaying }) { inPlayer, playing -> inPlayer && playing }
                    .combine(engine.state.map { engine.videoSize() }) { auto, _ -> auto }
                    .distinctUntilChanged()
                    .collect { auto -> pipAutoEnter = auto; setPictureInPictureParams(pipParams(auto)) }
            }
            // Android 8 to 12: the play/pause button in the window follows the playback state.
            if (pipSupported && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) LaunchedEffect(Unit) {
                engine.state.map { it.isPlaying }.distinctUntilChanged()
                    .collect { runCatching { setPictureInPictureParams(pipParams(pipAutoEnter)) } }
            }
            // Phone: an audio file that plays is hosted by the playback service, so it goes on in
            // the background; anything else is not.
            if (form == FormFactor.Phone) LaunchedEffect(Unit) {
                engine.state.map { it.isPlaying && engine.isAudioOnly() }.distinctUntilChanged().collect { audio ->
                    if (audio && backgroundAudioOn()) PlaybackService.attach(this@MainActivity, session.mediaSession)
                }
            }
            JaiPlayerApp(app)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** ACTION_VIEW from another app (ks-streamflix, ks-tv-launcher, a file manager). */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        app.open(Documents.mediaRef(this, uri, intent.getStringExtra(Intent.EXTRA_TITLE)))
    }

    private fun applyImmersive(on: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, !on)
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Picture-in-picture is for the phone app only. */
    private val pipSupported: Boolean
        get() = app.host.formFactor == FormFactor.Phone && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val (w, h) = engine.videoSize()
        val ratio = if (w > 0 && h > 0) Rational(w, h) else Rational(16, 9)
        val builder = PictureInPictureParams.Builder().setAspectRatio(clamp(ratio))
            .setActions(pipActions.actions(engine.state.value.isPlaying))
        // The animation into the window starts from the picture itself, not the whole screen.
        com.ks.jaiplayer.ui.VideoBounds.rect?.let { builder.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Video is not resized seamlessly (that is for other content); the system cross-fades it.
            builder.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    /** Phone, Android 8–11: pressing Home while a video plays continues it in picture-in-picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return // auto-enter does it
        if (pipSupported && app.screen == Screen.Player && engine.state.value.isPlaying) {
            runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
        }
    }

    /** Android allows picture-in-picture ratios between 1:2.39 and 2.39:1. */
    private fun clamp(r: Rational): Rational {
        val v = r.toFloat()
        return when {
            v > 2.39f -> Rational(239, 100)
            v < 1 / 2.39f -> Rational(100, 239)
            else -> r
        }
    }

    override fun onResume() {
        super.onResume()
        updater?.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen pauses, except picture-in-picture and background audio on the phone.
        val background = app.host.formFactor == FormFactor.Phone && backgroundAudioOn() && engine.isAudioOnly()
        if (!isInPictureInPictureMode && !background) engine.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // A new activity makes its own session, and two sessions may not share an id.
        PlaybackService.detach()
        session.release()
        pipActions.unregister()
        if (isFinishing) engine.release()
    }

    private fun host(form: FormFactor) = object : PlatformHost {
        override val formFactor = form

        override val isDev: Boolean = packageName.endsWith(".dev")

        override val inPictureInPicture: Boolean get() = inPip.value

        // "Remove animations" in Android's accessibility settings sets the animator scale to 0.
        override val reduceMotion: Boolean
            get() = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

        override val brightness: BrightnessControl? = if (form != FormFactor.Phone) null else object : BrightnessControl {
            override fun current(): Float = window.attributes.screenBrightness.takeIf { it >= 0f }
                ?: (android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
            override fun set(level: Float) { window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(0.01f, 1f) } }
            override fun reset() { window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }

        override val systemColors: androidx.compose.material3.ColorScheme?
            get() = if (dynamicColorsSupported(form) && systemColorsOn.value) androidx.compose.material3.dynamicDarkColorScheme(this@MainActivity) else null

        override val deviceSettings: List<DeviceSetting> = buildList {
            if (dynamicColorsSupported(form)) add(
                DeviceSetting(
                    Res.string.settings_system_colours, Res.string.settings_system_colours_hint,
                    isOn = { systemColorsOn.value },
                    set = { setDeviceFlag(SYSTEM_COLOURS, it); systemColorsOn.value = it },
                ),
            )
            if (form == FormFactor.Phone) add(
                DeviceSetting(
                    Res.string.settings_background_audio, Res.string.settings_background_audio_hint,
                    isOn = { backgroundAudioOn() },
                    set = { setDeviceFlag(BACKGROUND_AUDIO, it); if (!it) PlaybackService.detach() },
                ),
            )
            if (form == FormFactor.Tv) add(
                DeviceSetting(
                    Res.string.settings_match_frame_rate, Res.string.settings_match_frame_rate_hint,
                    isOn = { deviceFlag(MATCH_FRAME_RATE, default = true) },
                    set = { setDeviceFlag(MATCH_FRAME_RATE, it); engine.setFrameRateMatching(it) },
                ),
            )
            if (form == FormFactor.Tv) add(
                DeviceSetting(
                    Res.string.settings_tunneling, Res.string.settings_tunneling_hint,
                    isOn = { deviceFlag(TUNNELING, default = false) },
                    set = { setDeviceFlag(TUNNELING, it); engine.setTunneling(it) },
                ),
            )
        }

        // Google TV often has no system file picker, so the TV app browses the media library.
        override val library: MediaLibrary? = if (form == FormFactor.Tv) {
            AndroidMediaLibrary(this@MainActivity) { permissions, onResult ->
                onPermission = onResult
                askPermission.launch(permissions)
            }
        } else null

        override fun pickMedia(onPicked: (MediaRef) -> Unit) {
            onMediaPicked = onPicked
            pickMedia.launch(arrayOf("video/*", "audio/*"))
        }

        override fun pickFolder(onPicked: (List<MediaRef>) -> Unit) {
            onFolderPicked = onPicked
            pickFolder.launch(null)
        }

        override fun pickSubtitle(onPicked: (String) -> Unit) {
            onSubtitlePicked = onPicked
            pickSubtitle.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "application/octet-stream", "*/*"))
        }

        override fun exists(media: MediaRef) = Documents.exists(this@MainActivity, media)
        override fun location(media: MediaRef) = Documents.location(this@MainActivity, media)
    }

    private fun deviceFlag(key: String, default: Boolean): Boolean =
        getSharedPreferences("settings", MODE_PRIVATE).getString("device.$key", null)?.toBooleanStrictOrNull() ?: default

    private fun setDeviceFlag(key: String, on: Boolean) =
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("device.$key", on.toString()).apply()

    private fun backgroundAudioOn() = deviceFlag(BACKGROUND_AUDIO, default = true)

    /** Material You: the phone app on Android 12+ (the TV keeps the app's own look). */
    private fun dynamicColorsSupported(form: FormFactor) = form == FormFactor.Phone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** The "Use your wallpaper's colours" switch, as state so turning it re-themes the app at once. */
    private val systemColorsOn by lazy { androidx.compose.runtime.mutableStateOf(deviceFlag(SYSTEM_COLOURS, default = true)) }

    private companion object {
        const val MATCH_FRAME_RATE = "matchFrameRate"
        const val BACKGROUND_AUDIO = "backgroundAudio"
        const val SYSTEM_COLOURS = "systemColours"
        const val TUNNELING = "tunneling"
    }
}

/** Settings in SharedPreferences. */
private class PrefsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
