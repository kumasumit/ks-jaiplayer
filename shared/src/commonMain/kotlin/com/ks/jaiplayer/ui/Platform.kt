package com.ks.jaiplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.PlayerEngine
import org.jetbrains.compose.resources.StringResource

/** Which kind of device the interface is laid out for. */
enum class FormFactor { Desktop, Phone, Tv }

/** What the shared interface needs from the platform it runs on. */
interface PlatformHost {
    val formFactor: FormFactor

    /** A DEV build, installed next to production (KS-Suite "DEV and PROD side by side"): its title says DEV. */
    val isDev: Boolean get() = false

    /** Shows the platform's file picker for video and audio; calls [onPicked] if a file is chosen. */
    fun pickMedia(onPicked: (MediaRef) -> Unit)

    /**
     * Lets the user choose a folder (a series, a season) and returns its video and audio files,
     * subfolders included, in natural name order ([com.ks.jaiplayer.player.naturalNameOrder]).
     */
    fun pickFolder(onPicked: (List<MediaRef>) -> Unit)

    /** Shows the file picker for subtitle files; calls [onPicked] with the file's URI. */
    fun pickSubtitle(onPicked: (String) -> Unit)

    /** Whether the file is still there (recent files that were moved or deleted are greyed out). */
    fun exists(media: MediaRef): Boolean

    /** A short form of where the file is, shown under its name in the recent list. */
    fun location(media: MediaRef): String

    /** The device's media library, browsed in the app instead of a picker (the Google TV app); null elsewhere. */
    val library: MediaLibrary? get() = null

    /** True while the video plays in a picture-in-picture window (phone): the app's own controls hide. */
    val inPictureInPicture: Boolean get() = false

    /** The system's Reduce Motion (or animations off) setting: the interface then avoids fades. */
    val reduceMotion: Boolean get() = false

    /** Height of a title bar drawn over the content (macOS transparent title bar), kept free at the top; 0 elsewhere. */
    val titleBarInsetDp: Int get() = 0

    /** The screen's brightness for the player (the phone's left-side swipe); null where it cannot be set. */
    val brightness: BrightnessControl? get() = null

    /** The plugins and their switches (desktop, where plugins can be added as jars); null elsewhere. */
    val plugins: PluginManager? get() = null

    /**
     * The system's colour scheme for the screens outside the player (Material You on the phone,
     * Android 12+, while its switch is on); null keeps the app's own colours. Read during
     * composition, so a snapshot state behind it re-themes the app when it changes.
     */
    val systemColors: androidx.compose.material3.ColorScheme? get() = null

    /** Switches that only make sense on this platform, shown under "This device" in Settings. */
    val deviceSettings: List<DeviceSetting> get() = emptyList()
}

/** Sets the brightness of the app's window only, 0 to 1; [reset] gives it back to the system setting. */
interface BrightnessControl {
    fun current(): Float
    fun set(level: Float)
    fun reset()
}

/** One plugin as Settings shows it. [file] is the jar of an added plugin; [problem] why it cannot load. */
class PluginInfo(val id: String, val name: String, val file: String?, val problem: PluginProblem?)

enum class PluginProblem { WrongVersion, Unreadable }

/** Lists the plugins and turns them on or off; changes apply the next time the app starts. */
interface PluginManager {
    val plugins: List<PluginInfo>
    fun isOn(id: String): Boolean
    fun set(id: String, on: Boolean)
    /** Opens the folder added plugins go in. */
    fun openFolder()
}

/** An on/off setting of one platform (frame-rate matching on TV, background audio on the phone). */
class DeviceSetting(
    val label: StringResource,
    val description: StringResource,
    val isOn: () -> Boolean,
    val set: (Boolean) -> Unit,
)

/**
 * Draws the engine's picture. Each platform draws its own engine's frames. With [fill] the
 * picture covers the whole area and its edges are cut off; otherwise it fits inside.
 */
@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier = Modifier, fill: Boolean = false)

/** Lets files be dropped on the element (desktop); no effect where there is no drag and drop. */
expect fun Modifier.mediaDropTarget(onDrop: (List<MediaRef>) -> Unit): Modifier

/** Shows [content] with interface text in [language] (`en` or `hi`). */
@Composable
expect fun ProvideLanguage(language: String, content: @Composable () -> Unit)
