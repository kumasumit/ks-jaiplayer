package com.ks.jaiplayer.plugins.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ks.jaiplayer.audio.AudioPreset
import com.ks.jaiplayer.audio.AudioSettings
import com.ks.jaiplayer.audio.AudioSupport
import com.ks.jaiplayer.audio.BAND_HZ
import com.ks.jaiplayer.audio.BAND_MAX_DB
import com.ks.jaiplayer.audio.CONTROL_MAX
import com.ks.jaiplayer.audio.MediaKind
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.plugin.ControlSlot
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.PluginContext
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.audio_bass
import com.ks.jaiplayer.resources.audio_db
import com.ks.jaiplayer.resources.audio_desktop_note
import com.ks.jaiplayer.resources.audio_dialogue
import com.ks.jaiplayer.resources.audio_effects
import com.ks.jaiplayer.resources.audio_equalizer
import com.ks.jaiplayer.resources.audio_hz
import com.ks.jaiplayer.resources.audio_khz
import com.ks.jaiplayer.resources.audio_on
import com.ks.jaiplayer.resources.audio_passthrough
import com.ks.jaiplayer.resources.audio_preset_bass
import com.ks.jaiplayer.resources.audio_preset_cinema
import com.ks.jaiplayer.resources.audio_preset_custom
import com.ks.jaiplayer.resources.audio_preset_dialogue
import com.ks.jaiplayer.resources.audio_preset_loud
import com.ks.jaiplayer.resources.audio_preset_music
import com.ks.jaiplayer.resources.audio_preset_night
import com.ks.jaiplayer.resources.audio_preset_original
import com.ks.jaiplayer.resources.audio_preset_vocal
import com.ks.jaiplayer.resources.audio_for_music
import com.ks.jaiplayer.resources.audio_for_videos
import com.ks.jaiplayer.resources.audio_reset
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.ui.IconControlMenu
import com.ks.jaiplayer.ui.JaiIcons
import com.ks.jaiplayer.ui.MenuChoice
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Built-in plugin: audio effects (specs/audio-effects.md). A panel in the player bar with the
 * presets (one for videos, one for music), bass and dialogue levels, a custom equalizer and a
 * reset. Changes apply at once and are saved; Original leaves the audio untouched.
 */
class AudioEffectsPlugin : JaiPlugin {
    override val id = "audio-effects"
    override val name = "Audio effects"

    override fun install(context: PluginContext) {
        val settings = mutableStateOf(load(context.settings))
        val engine = context.engine
        fun update(next: AudioSettings) {
            settings.value = next
            save(context.settings, next)
            apply(engine, next)
        }
        apply(engine, settings.value)
        if (engine.audioSupport == AudioSupport.None) return
        context.addControl(ControlSlot.End) { state ->
            val current by settings
            EffectsMenu(state, current, engine.audioSupport, ::update)
        }
    }

    companion object {
        fun apply(engine: PlayerEngine, s: AudioSettings) = engine.setAudioEffects(s.chain(MediaKind.Video), s.chain(MediaKind.Music))

        fun load(store: SettingsStore): AudioSettings {
            val custom = store.getString("custom")?.split(',')?.mapNotNull { it.toFloatOrNull() }
            return AudioSettings(
                enabled = store.getString("on") != "false",
                videoPreset = AudioPreset.of(store.getString("video")),
                musicPreset = AudioPreset.of(store.getString("music")),
                bass = store.getString("bass")?.toIntOrNull()?.coerceIn(0, CONTROL_MAX) ?: 0,
                dialogue = store.getString("dialogue")?.toIntOrNull()?.coerceIn(0, CONTROL_MAX) ?: 0,
                customDb = custom?.takeIf { it.size == BAND_HZ.size }?.map { it.coerceIn(-BAND_MAX_DB, BAND_MAX_DB) }
                    ?: List(BAND_HZ.size) { 0f },
            )
        }

        fun save(store: SettingsStore, s: AudioSettings) {
            store.putString("on", s.enabled.toString())
            store.putString("video", s.videoPreset.id)
            store.putString("music", s.musicPreset.id)
            store.putString("bass", s.bass.toString())
            store.putString("dialogue", s.dialogue.toString())
            store.putString("custom", s.customDb.joinToString(",") { it.roundToInt().toString() })
        }

        private val LABELS: Map<AudioPreset, StringResource> = mapOf(
            AudioPreset.Original to Res.string.audio_preset_original,
            AudioPreset.BassBoost to Res.string.audio_preset_bass,
            AudioPreset.Music to Res.string.audio_preset_music,
            AudioPreset.Cinema to Res.string.audio_preset_cinema,
            AudioPreset.Dialogue to Res.string.audio_preset_dialogue,
            AudioPreset.LoudClear to Res.string.audio_preset_loud,
            AudioPreset.Night to Res.string.audio_preset_night,
            AudioPreset.Vocal to Res.string.audio_preset_vocal,
            AudioPreset.Custom to Res.string.audio_preset_custom,
        )
    }

    @Composable
    private fun EffectsMenu(state: PlaybackState, s: AudioSettings, support: AudioSupport, update: (AudioSettings) -> Unit) {
        val kind = if (!state.hasVideo && state.positionMs >= 1000) MediaKind.Music else MediaKind.Video
        val preset = s.preset(kind)
        IconControlMenu(JaiIcons.Effects, stringResource(Res.string.audio_effects)) { _ ->
            Column(Modifier.width(320.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(s.enabled, role = Role.Switch) { update(s.copy(enabled = it)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.audio_on), Modifier.weight(1f))
                    Switch(checked = s.enabled, onCheckedChange = null)
                }
                if (state.audioPassthrough) Note(stringResource(Res.string.audio_passthrough))
                if (s.enabled) {
                    Heading(stringResource(if (kind == MediaKind.Video) Res.string.audio_for_videos else Res.string.audio_for_music))
                    AudioPreset.entries.forEach { p ->
                        // The menu stays open, so each choice can be heard at once.
                        MenuChoice(stringResource(LABELS.getValue(p)), p == preset) { update(s.withPreset(kind, p)) }
                    }
                    if (preset != AudioPreset.Original) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Level(stringResource(Res.string.audio_bass), s.bass) { update(s.copy(bass = it)) }
                        Level(stringResource(Res.string.audio_dialogue), s.dialogue) { update(s.copy(dialogue = it)) }
                    }
                    if (preset == AudioPreset.Custom) {
                        Heading(stringResource(Res.string.audio_equalizer))
                        BAND_HZ.forEachIndexed { i, hz ->
                            Band(hz, s.customDb[i]) { db ->
                                update(s.copy(customDb = s.customDb.toMutableList().also { it[i] = db }))
                            }
                        }
                    }
                    if (support == AudioSupport.Equalizer) Note(stringResource(Res.string.audio_desktop_note))
                    TextButton(
                        onClick = { update(AudioSettings(enabled = s.enabled, customDb = s.customDb)) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text(stringResource(Res.string.audio_reset)) }
                }
            }
        }
    }

    @Composable
    private fun Heading(text: String) = Text(
        text, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    @Composable
    private fun Note(text: String) = Text(
        text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    /** A 0 to 10 level; the arrow keys (and the remote) move it one step. */
    @Composable
    private fun Level(label: String, value: Int, onChange: (Int) -> Unit) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value.toFloat(),
                onValueChange = { v -> v.roundToInt().let { if (it != value) onChange(it) } },
                valueRange = 0f..CONTROL_MAX.toFloat(),
                steps = CONTROL_MAX - 1,
            )
        }
    }

    @Composable
    private fun Band(hz: Int, db: Float, onChange: (Float) -> Unit) {
        val freq = if (hz >= 1000) stringResource(Res.string.audio_khz, (hz / 1000).toString())
        else stringResource(Res.string.audio_hz, hz.toString())
        val gain = stringResource(Res.string.audio_db, (if (db > 0) "+" else "") + db.roundToInt())
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("$freq  $gain", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = db,
                onValueChange = { v -> v.roundToInt().toFloat().let { if (it != db) onChange(it) } },
                valueRange = -BAND_MAX_DB..BAND_MAX_DB,
                steps = (BAND_MAX_DB * 2).toInt() - 1,
            )
        }
    }
}
