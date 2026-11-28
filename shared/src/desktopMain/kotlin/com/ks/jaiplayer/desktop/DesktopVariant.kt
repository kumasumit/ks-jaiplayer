package com.ks.jaiplayer.desktop

import java.io.File

/**
 * Production or DEV (KS-Suite release-cycle.md, "DEV and PROD side by side"). A DEV build
 * (`-PksVariant=dev`, which passes `-Djaiplayer.variant=dev`) installs next to the production app
 * and never touches it: its own name, settings, folders and single-instance lock, and no updater.
 */
object DesktopVariant {
    val isDev: Boolean = System.getProperty("jaiplayer.variant") == "dev"

    /** The name people see: the app, its folders, the window and the Dock or taskbar. */
    val appName: String = if (isDev) "KS JaiPlayer DEV" else "KS JaiPlayer"

    /** The `java.util.prefs` node for settings, recent files and resume positions. */
    val prefsNode: String = if (isDev) "com/ks/jaiplayer-dev" else "com/ks/jaiplayer"

    private val home: String get() = System.getProperty("user.home")
    private val windows: Boolean get() = System.getProperty("os.name").startsWith("Windows")

    /** macOS `~/Library/Application Support/<app>`, Windows `%APPDATA%\<app>`. */
    fun supportDir(): File =
        File(if (windows) System.getenv("APPDATA") ?: home else "$home/Library/Application Support", appName)

    /** macOS `~/Library/Caches/com.ks.jaiplayer[.dev]`, Windows `%LOCALAPPDATA%\<app>`. */
    fun cacheDir(): File =
        if (windows) File(System.getenv("LOCALAPPDATA") ?: home, appName)
        else File(home, "Library/Caches/com.ks.jaiplayer" + if (isDev) ".dev" else "")
}
