package com.ks.jaiplayer.mac

import com.ks.jaiplayer.desktop.DesktopConfig
import com.ks.jaiplayer.desktop.ExternalOpen
import com.ks.jaiplayer.desktop.runDesktopApp
import com.ks.jaiplayer.vlc.BundledVlc
import com.ks.jaiplayer.vlc.VlcLocation
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/** macOS entry point: the system menu bar and Finder "Open With". Plays through the libVLC bundled in the app. */
fun main(args: Array<String>) {
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", com.ks.jaiplayer.desktop.DesktopVariant.appName)

    // Finder sends files as Apple events, before or after the window exists. Set this first.
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { event -> ExternalOpen.open(event.files) }
        }
    }

    runDesktopApp(
        DesktopConfig(
            vlcLocations = vlcLocations(),
            vlcArgs = MAC_VLC_ARGS,
            hardwareDecoding = MAC_HARDWARE_DECODING,
            systemLanguages = preferredLanguages(),
            initialFiles = args.map(::File).filter { it.exists() },
            systemAppMenu = true,
            useMetaShortcuts = true,
            nativeFolderDialog = true,
            integration = MacIntegration(),
            transparentTitleBar = true,
        ),
    )
}

/**
 * Software decoding (libavcodec) is the default, because libVLC cannot draw subtitles onto
 * VideoToolbox frames delivered through callbacks ("blending YUVA to CVPN failed").
 * `JAIPLAYER_VLC_ARGS` replaces these options, for diagnosis only.
 */
val MAC_VLC_ARGS: List<String> = System.getenv("JAIPLAYER_VLC_ARGS")?.split(' ')?.filter { it.isNotBlank() }
    ?: listOf("--codec=avcodec,all", "--avcodec-hw=none")

/**
 * Files without subtitles decode with VideoToolbox, at about half the CPU for 4K HEVC; when a
 * subtitle comes into play the engine goes on in software at the same place.
 */
val MAC_HARDWARE_DECODING: String? = ":codec=videotoolbox,avcodec,all".takeUnless { System.getenv("JAIPLAYER_NO_HW") == "1" }

/**
 * The libVLC bundled in the app. JAIPLAYER_VLC_PATH (a VLC.app, for diagnosis only) replaces it;
 * an installed VLC is never looked for.
 */
private fun vlcLocations(): List<VlcLocation> {
    val override = System.getenv("JAIPLAYER_VLC_PATH")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: return listOfNotNull(BundledVlc.location())
    return listOf(
        VlcLocation(File(override, "Contents/MacOS/lib"), File(override, "Contents/MacOS/plugins")),
        VlcLocation(File(override, "lib"), File(override, "plugins")),
    )
}

/** The user's preferred languages from System Settings (AppleLanguages), most preferred first. */
private fun preferredLanguages(): List<String> {
    val fromSystem = runCatching {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleLanguages").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.SECONDS)
        Regex("\"?([A-Za-z]{2,3}(?:-[A-Za-z0-9]+)*)\"?").findAll(text.substringAfter('(').substringBefore(')'))
            .map { it.groupValues[1] }.toList()
    }.getOrDefault(emptyList())
    return fromSystem.ifEmpty { listOf(java.util.Locale.getDefault().toLanguageTag()) }
}
