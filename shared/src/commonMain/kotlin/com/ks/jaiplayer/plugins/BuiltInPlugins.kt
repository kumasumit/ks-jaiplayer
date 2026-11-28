package com.ks.jaiplayer.plugins

import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugins.audio.AudioEffectsPlugin
import com.ks.jaiplayer.plugins.recent.RecentFilesPlugin
import com.ks.jaiplayer.plugins.resume.ResumeEntry
import com.ks.jaiplayer.plugins.resume.ResumePlugin
import com.ks.jaiplayer.plugins.tracks.TracksPlugin

/**
 * The plugins every build of KS JaiPlayer ships with. An entry point may pass fewer.
 * [onResumeChanged] receives the unfinished files (the Google TV app's Continue watching row).
 */
fun builtInPlugins(onResumeChanged: (List<ResumeEntry>) -> Unit = {}): List<JaiPlugin> =
    listOf(RecentFilesPlugin(), ResumePlugin(onChanged = onResumeChanged), TracksPlugin(), AudioEffectsPlugin())
