package com.ks.jaiplayer.desktop

import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.naturalNameOrder
import com.ks.jaiplayer.settings.SettingsStore
import java.io.File
import java.net.URI
import java.util.prefs.Preferences

/** The file types offered in the Open dialog (others can still be dropped or opened with the app). */
val mediaExtensions = setOf(
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "wmv", "flv", "mpg", "mpeg", "ts", "m2ts", "mts", "3gp", "ogv",
    "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "alac", "aiff",
)

val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "smi")

fun isMediaFile(file: File) = file.isFile && file.extension.lowercase() in mediaExtensions && !file.name.startsWith(".")

/**
 * The video and audio files in [paths]: files as they are, folders searched with their
 * subfolders (a series with season folders). Folders come out in natural name order
 * (Season 2 before Season 10, Episode 9 before Episode 10); hidden files are skipped.
 */
fun collectMedia(paths: List<File>, maxDepth: Int = 4): List<File> = paths.flatMap { path ->
    if (path.isDirectory) {
        path.walkTopDown().maxDepth(maxDepth)
            .onEnter { !it.name.startsWith(".") || it == path }
            .filter(::isMediaFile)
            .sortedWith(compareBy(naturalNameOrder) { it.relativeTo(path).invariantSeparatorsPath })
            .toList()
    } else {
        listOf(path).filter { it.isFile }
    }
}

/** A `file:///` URI, the form libVLC and the recent list use. */
fun fileUri(file: File): String = file.absoluteFile.toPath().toUri().toString()

fun mediaRefOf(file: File) = MediaRef(fileUri(file), file.name)

fun fileOf(media: MediaRef): File? = runCatching { File(URI(media.uri)) }.getOrNull()

/** The folder of [file], with the home folder shortened to `~`. */
fun shortLocation(file: File): String {
    val parent = file.absoluteFile.parent ?: return ""
    val home = System.getProperty("user.home")
    return if (parent.startsWith(home)) "~" + parent.removePrefix(home) else parent
}

/** Settings in `java.util.prefs`: a plist in ~/Library/Preferences on macOS, the registry on Windows. */
class PrefsSettingsStore(private val prefs: Preferences = Preferences.userRoot().node(DesktopVariant.prefsNode)) : SettingsStore {
    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value.take(Preferences.MAX_VALUE_LENGTH))
        runCatching { prefs.flush() }
    }
}
