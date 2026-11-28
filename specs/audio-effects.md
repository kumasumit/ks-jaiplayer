---
type: Specification
title: Audio effects
description: The audio presets, bass and dialogue controls and custom equalizer of KS JaiPlayer, how each platform applies them, their limits, and how they are tested.
tags: [audio, equalizer, dialogue, plugin, android, google-tv, macos, windows]
---

# Audio effects

A built-in plugin (`AudioEffectsPlugin`, [Plugins](plugins.md)) with a button in the player bar. It can be switched off in Settings › Plugins, and then the audio is never touched.

## What the panel offers

- **Audio effects on** (on by default; with the default preset Original nothing changes).
- **Presets**, one remembered for videos and one for music; the panel shows the one for the file playing (music: no picture after a second of playback).
- **Bass** and **Dialogue**, 0 to 10, on top of any preset except Original.
- **Equalizer**, ten bands of ±12 dB, with the Custom preset.
- **Reset to original**: both presets back to Original, the levels to 0 (the custom curve is kept).
- Changes apply at once, without restarting playback, and are saved (`plugin.audio-effects.*` in the app's settings). On Google TV the panel is the side panel the remote moves through; the arrow keys move a level one step.

| Preset | Tone (equalizer) | Android and Google TV also |
|---|---|---|
| Original | none: the audio is not processed | |
| Bass boost | +4 to +6 dB below 125 Hz | |
| Music | warm bass, clear highs | slightly wider stereo |
| Cinema | bass and the 2 kHz presence band | light compression, centre +3 dB, wider stereo |
| Dialogue boost | bass down, 1 to 4 kHz up | light compression, centre +6 dB |
| Loud and clear | gentle lift | compression with make-up gain, limiter |
| Night mode | deep bass down, speech up | strong compression (quiet speech up, loud effects down), centre +4 dB |
| Vocal boost | speech and singing range up | centre +3 dB |
| Custom | the user's ten bands | |

The Dialogue level adds speech-band gain, centre-channel gain (5.1 and 7.1) and, from 5, light compression. Every boost is paid for with headroom (the curve's highest boost is taken off before the equalizer), so dialogue enhancement does not just turn the volume up.

## Per platform

**Android and Google TV** (`EffectsAudioProcessor` running `AudioDsp`, in Media3's audio sink before its speed change): the equalizer (peaking filters on libVLC's bands), centre-channel gain, stereo width, a compressor and a peak limiter at -1 dBFS with instant attack and no look-ahead, so it adds no latency and never clips. 16-bit and float PCM; other formats pass unchanged. Original or effects off copies the samples bit for bit.

**Dolby and DTS passthrough** (a TV or receiver that decodes the sound itself): encoded audio never reaches the processor, so effects cannot apply. The panel says so and how to turn passthrough off (the device's sound settings). JaiPlayer never turns passthrough off by itself.

**macOS and Windows** (`VlcPlayerEngine.applyEffects`): libVLC 3's own equalizer, already in the bundled LGPL plugins, changed live with `libvlc_media_player_set_equalizer`. Its preamp is the headroom: 12 dB means unchanged level (0 dB would lower everything by 12 dB, measured). Original turns the equalizer off, so the audio passes unchanged. Limits, which the panel states: no compressor, limiter, centre-channel gain or stereo width. libVLC 3 replaces its list of audio filters with the equalizer alone and offers no public way to add its `compressor` filter at run time, so Night mode and Loud and clear change the tone only on desktop.

No preset claims Dolby Atmos, DTS:X or any other licensed technology.

## Dependencies, cost and performance

No new library, service or permission on any platform: Media3's audio processor interface and libVLC's equalizer were already there; the DSP is about 150 lines of Kotlin. Cost per sample on Android: up to ten second-order filters per channel plus one envelope follower; Original skips all of it. Release sizes and start-up are compared with v1.11.3 in the DEV build report ([Progress](progress.md)).

## Tests

Automated (`./gradlew :shared:desktopTest`):
- `AudioEffectsTest`: presets resolve; Original and off give no processing; settings round trip; the DSP is bit-exact with no chain; a full-scale 60 Hz tone with maximum bass stays at or below -1 dBFS; Night mode narrows a 30× gap between quiet and loud by more than four times; centre gain touches only the centre channel.
- `VlcEqualizerIntegrationTest` (bundled libVLC, sound captured with libVLC's memory output, nothing heard): libVLC's bands are `BAND_HZ`; a flat equalizer keeps the level; a +9 dB band with its headroom keeps the level; a change applies while playing; Original gives the original level back.

By hand (the owner, per preset, on each platform): music with strong bass, a dialogue-heavy film, an action film, a quiet recording, stereo and 5.1 files; laptop or TV speakers, headphones, Bluetooth and HDMI. Listen for clipping, crackle and pumping; check that seeking, playback speed and lip sync stay right; on Google TV with a receiver, check the passthrough note.
