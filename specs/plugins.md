---
type: Specification
title: Plugins
description: The minimal core of KS JaiPlayer and the plugin API that adds every other feature, with the built-in plugins.
tags: [plugins, architecture, core]
---

# Plugins

KS JaiPlayer keeps its core as small as a player can be, and adds every other feature as a plugin (owner's decision, 2026-11-23).

## The core
Open a file or a series folder, play and pause, seek, volume and mute, full screen, play a series episode after episode (previous, next, the episode list), playback speed, the language setting and app updates. These are in `shared/src/commonMain` and are never plugins: speed and series playback are in the core by the owner's request, language and updates by suite rules.

## The API
`com.ks.jaiplayer.plugin` in `commonMain`:

- `JaiPlugin` has an `id` (stable, unique) and `install(context)`, called once at start.
- `PluginContext` gives the plugin the `engine`, the `controller`, the platform `host`, its own `settings` (keys stored as `plugin.<id>.<key>`) and `open(media)`, and lets it add:
  - `addControl(slot, content)`: a control in the player bar, at `Start` (after volume) or `End` (before full screen), drawn with the playback state. Plugins use the shared `ControlButton`, `IconControlMenu`, `TextControlMenu` and `MenuChoice`, so they look and behave like the core (focusable on TV, never focused on desktop);
  - `addHomeSection(content)`: a section of the home screen, under the Open buttons;
  - `addMenuEntry(group, entry)`: an entry in the desktop File or Playback menu (submenus allowed); labels are composable, so they follow the language;
  - `addKeyBinding(key, action)`: a key in the player. The core keys (Space, arrows, F, M, N, P, [, ], Esc) cannot be taken, so the keyboard and the TV remote always mean the same;
  - `onMediaOpened(listener)`.
- The core never knows which plugins exist: each entry point passes the list (`builtInPlugins()`). Two plugins with one id are refused.

## Built-in plugins
| Plugin | id | Adds |
|---|---|---|
| Recent files | `recent-files` | The last 20 files on the home screen (missing ones greyed, remove one or all) and File › Open recent |
| Resume | `resume` | Opens a file where it was left (files of 3 minutes or more, started, not in the last 5 %); keeps the last 50 positions; feeds Google TV's Continue watching row |
| Tracks | `tracks` | Subtitle menu (off, embedded tracks, add a subtitle file), audio track menu, keys S and A, menu entries |
| Audio effects | `audio-effects` | Presets for videos and for music, bass and dialogue levels, a 10-band equalizer ([Audio effects](audio-effects.md)) |

## Third-party plugins (desktop)
The macOS and Windows apps also load plugins from jars the user adds, off until turned on in Settings › Plugins, with a versioned API (`PLUGIN_API_VERSION`). Android and Google TV keep the built-in plugins only, because Google Play does not allow loading code from files. How to write one: [Writing a desktop plugin](writing-plugins.md).
