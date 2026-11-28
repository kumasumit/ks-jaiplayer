package com.ks.jaiplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerAction
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.app_name
import com.ks.jaiplayer.resources.common_pause
import com.ks.jaiplayer.resources.common_settings
import com.ks.jaiplayer.resources.home_open_file
import com.ks.jaiplayer.resources.menu_file
import com.ks.jaiplayer.resources.menu_playback
import com.ks.jaiplayer.resources.menu_quit
import com.ks.jaiplayer.resources.menu_view
import com.ks.jaiplayer.resources.menu_help_site
import com.ks.jaiplayer.resources.menu_help
import com.ks.jaiplayer.resources.menu_zoom
import com.ks.jaiplayer.resources.menu_minimize
import com.ks.jaiplayer.resources.menu_window
import com.ks.jaiplayer.resources.player_close
import com.ks.jaiplayer.resources.player_full_screen
import com.ks.jaiplayer.resources.player_mute
import com.ks.jaiplayer.resources.player_play
import com.ks.jaiplayer.resources.player_seek_back
import com.ks.jaiplayer.resources.player_seek_forward
import com.ks.jaiplayer.ui.FormFactor
import com.ks.jaiplayer.ui.JaiPlayerApp
import com.ks.jaiplayer.ui.JaiPlayerState
import com.ks.jaiplayer.ui.PlatformHost
import com.ks.jaiplayer.ui.PluginManager
import com.ks.jaiplayer.ui.ProvideLanguage
import com.ks.jaiplayer.ui.Screen
import com.ks.jaiplayer.vlc.VlcLocation
import com.ks.jaiplayer.vlc.VlcPlayerEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.MenuScope
import com.ks.jaiplayer.player.formatSpeed
import com.ks.jaiplayer.player.playbackSpeeds
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.MenuEntry
import com.ks.jaiplayer.plugin.MenuGroup
import com.ks.jaiplayer.plugin.PluginScreenState
import com.ks.jaiplayer.plugins.builtInPlugins
import com.ks.jaiplayer.resources.home_open_series
import com.ks.jaiplayer.resources.player_next
import com.ks.jaiplayer.resources.player_previous
import com.ks.jaiplayer.resources.player_speed
import javax.swing.JFileChooser
import kotlin.math.abs

/** What differs between the macOS and Windows entry points. */
class DesktopConfig(
    /** Where libVLC is, in order: the copy bundled in the app (or a diagnosis override). */
    val vlcLocations: List<VlcLocation>,
    /** libVLC options this platform needs. */
    val vlcArgs: List<String> = emptyList(),
    /** A per-file libVLC option that decodes in hardware, used for files without subtitles; null for none. */
    val hardwareDecoding: String? = null,
    /** The system's preferred languages, most preferred first. */
    val systemLanguages: List<String>,
    /** Files to open at start, e.g. from the command line. */
    val initialFiles: List<File> = emptyList(),
    /** macOS: the system menu holds Quit and Settings, so the File menu leaves them out. */
    val systemAppMenu: Boolean = false,
    /** Command (macOS) or Ctrl (Windows) shortcuts. */
    val useMetaShortcuts: Boolean = false,
    /** macOS: folders are chosen in the native file dialog; Windows uses a folder chooser. */
    val nativeFolderDialog: Boolean = false,
    /** The features added to the minimal core; see specs/plugins.md. */
    val plugins: List<JaiPlugin> = builtInPlugins(),
    /**
     * Windows: the system's folder picker, run off the window's thread: it gets the owner window,
     * a fallback chooser for when it is unavailable, and where to send the folder (null: cancelled).
     */
    val folderPicker: ((Frame?, () -> File?, (File?) -> Unit) -> Unit)? = null,
    /** macOS: the content runs under a transparent title bar, as in QuickTime. */
    val transparentTitleBar: Boolean = false,
    /** This OS's Now Playing, media keys and stay-awake; null for none. */
    val integration: DesktopIntegration? = null,
)

/**
 * Files opened from outside while the app runs (Finder "Open With", a second launch). The entry
 * point sends them here; the window opens the latest.
 */
object ExternalOpen {
    internal val files = Channel<List<File>>(Channel.UNLIMITED)
    fun open(files: List<File>) { if (files.isNotEmpty()) this.files.trySend(files) }
}

/** Runs the desktop app (macOS or Windows): one window, the shared interface and a menu bar. */
fun runDesktopApp(config: DesktopConfig) {
    com.ks.jaiplayer.vlc.FrameStats.mark("main")
    ExternalOpen.open(config.initialFiles)
    application {
        val engine = remember { VlcPlayerEngine.create(config.vlcLocations, config.vlcArgs, config.hardwareDecoding) }
        val windowState = rememberWindowState(size = DpSize(1120.dp, 720.dp))
        val windowRef = remember { arrayOfNulls<Frame>(1) }
        val settings = remember { PrefsSettingsStore() }
        // Built-in plugins plus the jars added to the plugins folder (only those switched on load).
        val pluginManager = remember { DesktopPlugins(settings, config.plugins) }
        val host = remember { DesktopHost(config.nativeFolderDialog, config.folderPicker, pluginManager, if (config.transparentTitleBar) MAC_TITLE_BAR_DP else 0) { windowRef[0] } }
        val updater = remember { desktopUpdater(settings) { engine.release() } }
        val app = remember { JaiPlayerState(engine, host, settings, config.systemLanguages, pluginManager.active(), updater) }

        val quit = { config.integration?.stop(); engine.release(); exitApplication() }
        Window(
            onCloseRequest = quit,
            state = windowState,
            title = DesktopVariant.appName,
            // The window's own icon (Windows title bar and taskbar, the Dock when run from Gradle).
            icon = windowIcon,
        ) {
            windowRef[0] = window
            LaunchedEffect(Unit) { com.ks.jaiplayer.vlc.FrameStats.mark("windowComposed") }
            // Packaging only (desktop/AppCds.kt): show the home screen, then the player, then quit,
            // so the classes a normal start uses are recorded for the AppCDS archive.
            if (System.getenv("JAIPLAYER_CDS_TRAIN") == "1") LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2_500)
                app.open(MediaRef(File(System.getProperty("java.io.tmpdir"), "jaiplayer-cds-training.mp4").toURI().toString(), "training"))
                kotlinx.coroutines.delay(2_500)
                app.showSettings = true
                kotlinx.coroutines.delay(1_500)
                quit()
            }
            if (config.transparentTitleBar) LaunchedEffect(Unit) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                // The app shows the file's name itself, so the title bar shows only the traffic lights.
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
            config.integration?.let { integration ->
                LaunchedEffect(Unit) {
                    val labels = IntegrationLabels(
                        play = org.jetbrains.compose.resources.getString(Res.string.player_play),
                        pause = org.jetbrains.compose.resources.getString(Res.string.common_pause),
                        next = org.jetbrains.compose.resources.getString(Res.string.player_next),
                        previous = org.jetbrains.compose.resources.getString(Res.string.player_previous),
                    )
                    // Pinch out on the trackpad goes full screen while a video plays; pinch in leaves it.
                    integration.pinch(window) { zoomIn ->
                        if (app.screen == Screen.Player && zoomIn != app.fullScreen) app.fullScreen = zoomIn
                    }
                    integration.start(window, labels) { action ->
                        when (action) {
                            PlayerAction.NextItem -> app.next()
                            PlayerAction.PreviousItem -> app.previous()
                            else -> app.controller.perform(action)
                        }
                    }
                    kotlinx.coroutines.flow.combine(engine.state, snapshotFlow { app.playlist }) { s, list ->
                        val now = s.media?.let { m ->
                            NowPlaying(m.uri, m.title, s.durationMs, s.positionMs, s.isPlaying, s.speed, list?.hasNext == true, list?.hasPrevious == true)
                        }
                        now to s.hasVideo
                    }.collect { (now, video) -> integration.update(now, video) }
                }
            }
            LaunchedEffect(Unit) {
                ExternalOpen.files.receiveAsFlow().collect {
                    app.openAll(collectMedia(it).map(::mediaRefOf))
                    // Opened from outside (Finder, Explorer, a second launch): bring the window forward.
                    if (window.state == Frame.ICONIFIED) window.state = Frame.NORMAL
                    window.toFront()
                }
            }
            LaunchedEffect(app.fullScreen) {
                windowState.placement = if (app.fullScreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
            LaunchedEffect(Unit) {
                snapshotFlow { windowState.placement }.collect { app.fullScreen = it == WindowPlacement.Fullscreen }
            }
            ProvideLanguage(app.language) {
                val appName = stringResource(Res.string.app_name) + if (DesktopVariant.isDev) " DEV" else ""
                window.title = app.playlist?.current?.title?.takeIf { app.screen == Screen.Player } ?: appName
                AppMenuBar(app, config, quit)
                androidx.compose.foundation.layout.Box {
                    JaiPlayerApp(app)
                    // The transparent title bar's strip still moves the window (the traffic lights
                    // are drawn by macOS above it).
                    if (config.transparentTitleBar) {
                        WindowDraggableArea(
                            androidx.compose.ui.Modifier.fillMaxWidth().height(MAC_TITLE_BAR_DP.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameWindowScope.AppMenuBar(app: JaiPlayerState, config: DesktopConfig, quit: () -> Unit) {
    fun shortcut(key: Key, shift: Boolean = false) =
        if (config.useMetaShortcuts) KeyShortcut(key, meta = true, shift = shift) else KeyShortcut(key, ctrl = true, shift = shift)
    val inPlayer = app.screen == Screen.Player
    val playback by app.engine.state.collectAsState()
    val screenState = PluginScreenState(inPlayer, playback)
    val list = app.playlist

    @Composable
    fun MenuScope.pluginEntries(group: MenuGroup) {
        app.plugins.menuEntries.filter { it.first == group }.forEach { (_, entry) -> PluginEntry(entry, screenState) }
    }

    MenuBar {
        Menu(stringResource(Res.string.menu_file)) {
            Item(stringResource(Res.string.home_open_file), shortcut = shortcut(Key.O), onClick = app::pickAndOpen)
            Item(stringResource(Res.string.home_open_series), shortcut = shortcut(Key.O, shift = true), onClick = app::pickFolderAndOpen)
            pluginEntries(MenuGroup.File)
            Separator()
            Item(stringResource(Res.string.player_close), enabled = inPlayer, shortcut = shortcut(Key.W), onClick = app::closePlayer)
            if (!config.systemAppMenu) {
                Item(stringResource(Res.string.common_settings), onClick = { app.showSettings = true })
                Separator()
                Item(stringResource(Res.string.menu_quit), shortcut = shortcut(Key.Q), onClick = quit)
            }
        }
        // Menu items carry no single-key shortcuts: the player window handles those keys itself.
        Menu(stringResource(Res.string.menu_playback), enabled = inPlayer) {
            Item(stringResource(if (playback.isPlaying) Res.string.common_pause else Res.string.player_play), onClick = app.controller::togglePlay)
            Item(pluralStringResource(Res.plurals.player_seek_back, 10, "10"), onClick = { app.controller.perform(PlayerAction.SeekBack) })
            Item(pluralStringResource(Res.plurals.player_seek_forward, 10, "10"), onClick = { app.controller.perform(PlayerAction.SeekForward) })
            Separator()
            Item(stringResource(Res.string.player_previous), enabled = list?.hasPrevious == true, onClick = app::previous)
            Item(stringResource(Res.string.player_next), enabled = list?.hasNext == true, onClick = app::next)
            Menu(stringResource(Res.string.player_speed)) {
                playbackSpeeds.forEach { speed ->
                    CheckboxItem(formatSpeed(speed), checked = abs(playback.speed - speed) < 0.001f, onCheckedChange = { app.engine.setSpeed(speed) })
                }
            }
            Separator()
            Item(stringResource(Res.string.player_mute), onClick = { app.controller.perform(PlayerAction.ToggleMute) })
            pluginEntries(MenuGroup.Playback)
        }
        Menu(stringResource(Res.string.menu_view)) {
            Item(stringResource(Res.string.player_full_screen), enabled = inPlayer,
                shortcut = if (config.useMetaShortcuts) KeyShortcut(Key.F, meta = true, ctrl = true) else KeyShortcut(Key.F11),
                onClick = { app.fullScreen = !app.fullScreen })
        }
        // macOS convention: a Window menu (minimize, zoom) and a Help menu.
        if (config.systemAppMenu) {
            Menu(stringResource(Res.string.menu_window)) {
                Item(stringResource(Res.string.menu_minimize), shortcut = shortcut(Key.M), onClick = { window.state = Frame.ICONIFIED })
                Item(stringResource(Res.string.menu_zoom), onClick = {
                    window.extendedState = if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                })
            }
        }
        Menu(stringResource(Res.string.menu_help)) {
            Item(stringResource(Res.string.menu_help_site), onClick = {
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(HELP_URL)) }
            })
        }
    }
}

/**
 * macOS: Accessibility › Display › Reduce motion. Windows: "Animation effects" off
 * (SPI_GETCLIENTAREAANIMATION). Read once at start.
 */
private fun systemReducesMotion(): Boolean = runCatching {
    val os = System.getProperty("os.name")
    when {
        os.startsWith("Mac") -> {
            val p = ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            out == "1"
        }
        os.startsWith("Windows") -> {
            val value = com.sun.jna.Memory(4).apply { setInt(0, 1) }
            val ok = com.sun.jna.NativeLibrary.getInstance("user32").getFunction("SystemParametersInfoW")
                .invokeInt(arrayOf(0x1042, 0, value, 0)) != 0
            ok && value.getInt(0) == 0
        }
        else -> false
    }
}.getOrDefault(false)

/** The height of the macOS title bar, kept free at the top when the content runs under it. */
private const val MAC_TITLE_BAR_DP = 28

/** KS JaiPlayer's icon from the app's resources, or null to keep the system's default. */
private val windowIcon: androidx.compose.ui.graphics.painter.Painter? by lazy {
    runCatching {
        val stream = DesktopConfig::class.java.getResourceAsStream("/jaiplayer-icon.png") ?: return@runCatching null
        val image = stream.use { javax.imageio.ImageIO.read(it) }
        androidx.compose.ui.graphics.painter.BitmapPainter(image.toComposeImageBitmap())
    }.getOrNull()
}

private const val HELP_URL = "https://github.com/kumasumit/ks-jaiplayer"

@Composable
private fun MenuScope.PluginEntry(entry: MenuEntry, state: PluginScreenState) {
    val children = entry.children
    if (children == null) {
        Item(entry.label(), enabled = entry.enabled(state), onClick = entry.onClick)
    } else {
        Menu(entry.label(), enabled = entry.enabled(state)) {
            children().forEach { PluginEntry(it, state) }
        }
    }
}

/** File dialogs and file checks for macOS and Windows. */
private class DesktopHost(
    private val nativeFolderDialog: Boolean,
    private val folderPicker: ((Frame?, () -> File?, (File?) -> Unit) -> Unit)?,
    override val plugins: PluginManager,
    override val titleBarInsetDp: Int,
    private val window: () -> Frame?,
) : PlatformHost {
    override val formFactor = FormFactor.Desktop

    override val isDev: Boolean = DesktopVariant.isDev

    override val reduceMotion: Boolean by lazy { systemReducesMotion() }

    override fun pickMedia(onPicked: (MediaRef) -> Unit) {
        choose(mediaExtensions)?.let { onPicked(mediaRefOf(it)) }
    }

    override fun pickSubtitle(onPicked: (String) -> Unit) {
        choose(subtitleExtensions)?.let { onPicked(fileUri(it)) }
    }

    override fun pickFolder(onPicked: (List<MediaRef>) -> Unit) {
        fun open(folder: File?) {
            val files = collectMedia(listOfNotNull(folder))
            if (files.isNotEmpty()) onPicked(files.map(::mediaRefOf))
        }
        val picker = folderPicker
        if (picker != null) picker(window(), ::chooseFolder, ::open) else open(chooseFolder())
    }

    private fun chooseFolder(): File? {
        if (nativeFolderDialog) {
            // macOS: the native dialog chooses folders while this property is set.
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(window(), null as String?, FileDialog.LOAD)
                dialog.isVisible = true
                val name = dialog.file ?: return null
                return File(dialog.directory, name)
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(window()) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun exists(media: MediaRef) = fileOf(media)?.isFile == true

    override fun location(media: MediaRef) = fileOf(media)?.let(::shortLocation).orEmpty()

    private fun choose(extensions: Set<String>): File? {
        val dialog = FileDialog(window(), null as String?, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}
