---
type: Plan
title: Android playback CPU
description: How JaiPlayer's CPU use during Android playback is measured (whole device first), how it compares with other players, and what is ruled in or out.
tags: [android, performance, battery, media3]
---

# Android playback CPU

Golden rule (the owner's brief "Playback Optimization and StreamFlix Readiness", 2026-11-28, which
replaces the first CPU brief): optimize what the whole device spends, not which process the CPU is
counted in.

## Rule: compare players on the whole device

Never compare players with different architectures on app-process CPU alone. Media3 runs its whole
pipeline inside the app; an app that plays through Android's MediaPlayer (MX Player) runs it in
`mediaserver`, so its app process looks cheap while the device works as hard. Compare whole-device
CPU, or the app plus the system media processes (`mediaserver`, the codec HAL `c2@…-service`,
`media.swcodec`, surfaceflinger), always with the same file, device and method.

The first comparison broke this rule: "MX Player is about 4× more CPU-efficient" counted the app
process only and was wrong. No app-only CPU targets apply.

## Comparison (Pixel 11, Android 17, 1080p60 H.264, 1×)

| | Whole device | Player pipeline |
|---|---|---|
| JaiPlayer DEV 1.12.0-dev+fe14942 | 139 %, 151 % | about 52 %, all in the app (ExoPlayer runs in-process) |
| MX Player | 157 %, 177 % (an ad's WebView about 12 % of it) | app about 11 % + `mediaserver` 34 % |

The two are broadly level. Both spend the same outside the player: codec HAL 16 %, surfaceflinger
15 %, `media.swcodec` (AAC) 6 %. The phone at rest: 29–31 %. VLC was not measured this way (its
speed is set to 2× for all videos on that phone).

## Method

- **Whole device** (the comparison figure): busy time of all CPUs from the first line of `/proc/stat`
  (total minus idle and iowait) over 20 s, divided by CLK_TCK (100): **percent of one core**, out of
  700 % on the Pixel 11. The busiest processes in the same window from `top -b -n 2 -d 20 -s 9`.
- **App and threads** (where JaiPlayer's own time goes): `utime + stime` from `/proc/<pid>/stat` and
  `/proc/<pid>/task/*/stat` over 20 s, percent of one core.
- Memory: TOTAL PSS from `dumpsys meminfo`.
- Files opened with `ACTION_VIEW`, an explicit package and `--grant-read-uri-permission` on a
  MediaStore URI, from the start, controls hidden, 2 rounds, sampled after 8 s; phone charging,
  other players stopped.
- Builds: release-equivalent only (R8, not debuggable), and compiled to the same state as the
  players compared with (`cmd package compile -m speed-profile -f <package>`); a fresh adb install
  runs uncompiled. The DEV 1.12.0 APK is release-equivalent (one minified `classes.dex`, baseline
  profile). A debug build costs much more (57–70 % app on the same file) and is never compared with
  a release build.

## Decoders: hardware, confirmed by the codec's flags

`dumpsys media.player` lists each codec's attributes, the ones behind
`MediaCodecInfo.isHardwareAccelerated()` and `isSoftwareOnly()`:

| Decoder | Used for | hw-accelerated | software-only | vendor |
|---|---|---|---|---|
| `c2.google.avc.decoder` | H.264 | 1 | 0 | 1 |
| `c2.google.hevc.decoder` | HEVC, incl. 4K 10-bit | 1 | 0 | 1 |
| `c2.android.hevc.decoder` (not used) | — | 0 | 1 | 0 |

## JaiPlayer's own time (app process, % of one core)

Not a comparison with other players; it shows where JaiPlayer spends its share.

- 1080p60 H.264 at 1×: 49–52 %; 4K30 HEVC 10-bit: 39 %; PSS 145 MB.
- Threads at 1080p60: ExoPlayer:Playback 10–15, MediaCodec_loop 8–11 + 4–5, CodecLooper 3–9,
  binder threads about 2 each, main thread 3, RenderThread about 0: the Media3 and MediaCodec pipeline.
- It follows the frames handled: at 0.5×, 1×, 2× and 4× (speed accurate to 0.5 %) the app uses
  29, 46, 61 and 100 %. Whether that is worse than MX at 4× needs the whole-device method (Job 3).

## Ruled out, with evidence

- Software decoding: both decoders are hardware (table above).
- Indirect rendering: video goes to a SurfaceView (`PlayerSurface`, `SURFACE_TYPE_SURFACE_VIEW`).
- UI work per frame: gfxinfo shows 0 frames in 10 s with controls hidden; main thread 3 %,
  RenderThread about 0 %.
- Position polling: one 250 ms refresh on the main thread, inside that 3 %.
- Media3 dynamic scheduling alone (`experimentalSetDynamicSchedulingEnabled`): no change, 57–65 %
  with and without it in alternating debug builds.

## Decisions

- Media3 ExoPlayer stays the Android and Google TV engine; Android's MediaPlayer only moves CPU into
  a system process and gives up control over streaming, tracks and speed.
- One playback engine per platform.
- The automatic landscape orientation stays as it is (a separate decision).

## Phase 2 of the first brief: results (finished before the new brief, at the owner's request)

Release builds signed with a test key, installed next to DEV as `com.ks.jaiplayer`, 3-minute
1080p60 H.264 file, app and whole device at 1× (20 s) and 4× (10 s), 2 rounds, builds alternated.

| Build | App 1× | App 4× | Whole device 1× | Whole device 4× |
|---|---|---|---|---|
| Baseline | 53.0, 53.6 % | 124, 128 % | 154, 158 % | 322, 305 % |
| 1: dynamic scheduling + renderer duration-to-progress | 49.9, 52.7 % | 124, 129 % | 152, 145 % | 329, 299 % |
| 1 + 2: speed change in AudioTrack | 46.4, 51.5 % | 126, 126 % | 222 (outlier), 146 % | 309, 295 % |
| 1 + 2 + 4: drop late frames before decoding | 52.0, 51.9 % | 124, 130 % | 172, 152 % | 296, 309 % |

1. **Not kept**: within the noise between rounds.
2. **Not kept**: within the noise.
3. **Not done**: Media3 reads an audio processor's active state only when the stream restarts
   (seek, new file), so turning the effects processor off with no effect chosen would leave a chosen
   effect silent until the next seek, or need a restart with a gap in the sound. The copy it would
   save (192 KB/s) is below what can be measured.
4. **Not kept**: within the noise.
5. **Kept** (47b0979): release builds are profileable from adb (`<profileable android:shell="true"/>`).
   A simpleperf trace of the baseline at 1080p60 1×: MediaCodec_loop 31 %, ExoPlayer:Playback 26 %,
   CodecLooper 8 %, binder threads 20 %; 41 % of samples in `libc` (system calls, the codec's per-buffer
   `mmap`/`munmap`), 22 % in ART. The playback thread ran in ART's interpreter: a fresh adb install is
   not compiled ahead of time (`dumpsys package dexopt`: test build `run-from-apk`, DEV `verify`,
   MX `speed-profile`). Compiled (`cmd package compile -m speed-profile`, which Android's idle
   maintenance does for every app), the app takes 46–49 % at 1×, the same as full `speed`
   compilation (46–47 %) and as MX's pipeline (45 %). 4× stays about 125 %: the per-frame cost is in
   Android's codec libraries.
6. **Not applicable**: no playback change was kept, so there is no before and after.

Method rule from this: compile every player to the same state before comparing
(`adb shell cmd package compile -m speed-profile -f <package>`; check with `dumpsys package dexopt`).

## Against VLC and MX Player (Pixel 11, DEV `63e650c`)

JaiPlayer DEV 1.12.0-dev+63e650c (CI build), VLC 3.7.2 and MX Player 3.5.3, each with its own
defaults except VLC's saved 2× speed, set to 1×. Files: 75-second cuts, test pattern, all with AAC
audio: 1080p60 H.264 10 Mb/s; Blu-ray style 1080p23.976 H.264 36 Mb/s; 4K23.976 HEVC 10-bit 61 Mb/s.
Method as above (20-second window after 10 s, whole device from `/proc/stat`, memory `TOTAL PSS`,
frames from SurfaceFlinger timestats), two rounds each, rotating which player goes first. At rest
the phone used 30–54 %.

| File | Player | Whole device | Player pipeline | Memory | Dropped frames (20 s) |
|---|---|---|---|---|---|
| 1080p60 | JaiPlayer | 134–145 % | 47 % in the app | **148–149 MB** | **0–1** of 1204–1231 |
| | VLC | **106–112 %** | 39 % in the app | 186–187 MB | 31–39 of 1197–1202 |
| | MX Player | 155–165 % | 12–16 % app + 35 % `mediaserver` | 292–388 MB | 2–8 |
| Blu-ray style | JaiPlayer | 128–180 % | 32–34 % | **148–149 MB** | **0** |
| | VLC | **69–72 %** | 26–27 % | 194–195 MB | 0–24 |
| | MX Player | 116–159 % | 8 % app + 23–24 % `mediaserver` | 278–289 MB | 0 |
| 4K HEVC 10-bit | JaiPlayer | 141–162 % | 34–35 % | **148–149 MB** | **0** |
| | VLC | **98–99 %** | 29–30 % | 197–204 MB | 0–1 |
| | MX Player | 155–156 % | 14–15 % app + 24 % `mediaserver` | 320–321 MB | 1–9 |

- **Start:** from the `VIEW` intent to the first audio playback event, three rounds: JaiPlayer
  0.33–0.43 s, VLC 0.39–0.48 s, MX Player 0.56–0.72 s.
- **Size installed:** JaiPlayer 2.0 MB, VLC 101 MB, MX Player 119 MB.
- **Stability:** no crash, no ANR and no process restart in any run, including a stress run on
  the 4K file per player: ten rounds of seek forward, seek back and pause/resume by media keys,
  then five times Home and back through the launcher. With equal seeks (net backwards) JaiPlayer
  came back playing. VLC and MX Player reopened on their own library or home screens, so playback
  after the return is not comparable, and the run does not show that either honoured every key.
- **Reading it:** JaiPlayer uses the least memory, drops the fewest frames and starts fastest; it is
  level with MX Player on the whole device. VLC costs 30–60 points less on the whole device at 1×,
  and drops frames at 1080p60. In JaiPlayer's runs `system_server` used 14–20 % against VLC's 8–10 %;
  where the rest of the gap goes is not yet profiled.
- **5.1 AC-3 and E-AC-3** stopped playback in this run; see below.

## Dolby audio failure (Pixel 11)

- **Existing limitation, not a regression:** production 1.11.3 (release APK) and DEV `63e650c` fail
  the same way on AC-3 and E-AC-3 files: error state at position 0.
- **Cause:** the phone's Dolby decoder (`c2.dolby.eac3.decoder`, build CD1A.261005.003) is created,
  then its service cannot register the app's first input buffer (binder "Operation not permitted",
  `registerSender` error 4), so `queueInputBuffer` throws. The failure is below the app; why other
  players have sound was not traced (VLC has its own decoders).
- **Why the video stopped:** ExoPlayer treats any renderer error as fatal for the whole player.
- **Fix (`ae9c4a1`):** on an audio renderer error the Android engine prepares again at the same
  position with another supported audio track, or with audio off, and says so on screen; video stays
  on its decoder (`AudioFallback`). The same message shows when the device has no decoder for any
  audio track.
- **Checked:** `AudioFallbackTest` (6 tests; 62 desktop tests, none failing). Pixel 11, 90-second cuts
  of Sintel: AC-3 and E-AC-3 play the picture with the message; an AC-3 + AAC file switches to AAC;
  one recovered error each, no crash. Phone and Google TV emulators (API 36, no Dolby decoder):
  the whole Sintel files, AC-3 and E-AC-3, play with the message (watched for about 30 seconds each).
  The E-AC-3 file is a conversion of the AC-3 one, not an original. No real Google TV tested.
- **Still open:** these files have no sound on the Pixel 11 (a decoder choice, P1-A).

## Next (Job 3 of the brief)

Measure JaiPlayer and MX Player at 4× on the Pixel 11 with the whole-device method (device, app,
`mediaserver`), both compiled, report, and continue to profiling and fixes only if JaiPlayer is
meaningfully worse or a profile shows clear waste.
