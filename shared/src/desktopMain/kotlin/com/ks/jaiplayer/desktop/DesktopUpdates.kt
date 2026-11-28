package com.ks.jaiplayer.desktop

import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.update.GitHubUpdater
import com.ks.jaiplayer.update.OpenedOn
import com.ks.jaiplayer.update.UpdateInstaller
import com.ks.utility.appupdate.UpdatePlatform
import java.io.File

/** The running version: the packaged app and `run` pass it as -Djaiplayer.version (from the git tag). */
fun desktopAppVersion(): String = System.getProperty("jaiplayer.version")?.takeIf { it.isNotBlank() } ?: "0.0.0"

/**
 * Where updates are downloaded, and ks-utility's desktop installer (`com.ks.utility.appupdate.
 * DesktopInstaller`): Windows starts the installer and quits; macOS opens the dmg, shows that the
 * app now closes, and quits 6 s later so Finder can replace it. Nothing installs silently.
 */
class DesktopInstaller(beforeQuit: () -> Unit) : UpdateInstaller {
    private val installer = com.ks.utility.appupdate.DesktopInstaller(DesktopVariant.appName, beforeQuit)

    override fun downloadDir(): File = File(DesktopVariant.cacheDir(), "update")

    override fun open(file: File): OpenedOn = when (installer.open(file)) {
        com.ks.utility.appupdate.DesktopInstaller.Opened.MAC -> OpenedOn.Mac
        com.ks.utility.appupdate.DesktopInstaller.Opened.WINDOWS -> OpenedOn.Windows
    }
}

fun desktopUpdater(settings: SettingsStore, beforeQuit: () -> Unit): GitHubUpdater? {
    // A DEV build never offers production files (KS-Suite "DEV and PROD side by side").
    if (DesktopVariant.isDev) return null
    val platform = UpdatePlatform.current() ?: return null
    return GitHubUpdater(GitHubUpdater.REPO, desktopAppVersion(), platform, settings, DesktopInstaller(beforeQuit)).also { it.start() }
}
