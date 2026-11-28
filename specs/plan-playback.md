---
type: Plan
title: Playback plan
description: The consolidated, owner-approved playback plan (2026-11-28) in phases with gates; it replaces the "Playback Optimization and StreamFlix Readiness" brief.
tags: [plan, playback, performance, audio, hdr]
---

# Playback plan

Approved by the owner on 2026-11-28. Measurements already taken are in [Android playback
CPU](plan-android-cpu.md) and [macOS playback](plan-macos-playback.md).

## Rules for every phase

- Work only in ks-jaiplayer on `dev`; commit and push after every finished step; stop at the end of
  each phase with a report of under 10 lines and wait for the owner.
- Correctness and format support come before CPU gains.
- Keep a change only if before-and-after measurements (same file, device, method, 2+ rounds, real
  media where available) show a gain beyond noise and playback quality is unchanged.
- Media3 stays on Android and Google TV, libVLC on desktop; Windows unchanged until benchmarked.
- Protect the 2 MB Android size, low memory, fast start and smooth playback.
- Playback stays behind the engine interface, free of app UI, so Streamflix clients can reuse it.
- Needs in ks-utility or ks-streamflix are reported as handoffs, never changed here.
- Real films: 1080p, 4K HDR, and AC-3 / E-AC-3 audio if available, 20 minutes or longer each. The
  folder is still to be named by the owner. Until then, free test files in `~/Downloads/ks-test-films`
  (outside the repository): Blender's Sintel 1080p with AC-3 5.1 (15 minutes, MD5 checked), an E-AC-3
  copy made from it, and Jellyfin's 30-second 1080p and 4K HEVC HDR10 clips.

## Player principles

The owner's, for KS JaiPlayer only (2026-11-28; not suite rules):

- Prefer each platform's own hardware-accelerated playback path.
- Avoid copying or converting video frames between CPU and GPU.
- A custom path needs a measured reason.

## Completion criteria

Owner's update, 2026-11-28: JaiPlayer is completed and released before any Streamflix work.
Releases may happen at gates along the way with the owner's approval; the plan is complete only when
every criterion is done. Status and evidence are tracked in [Progress](progress.md).

1. HDR colour correct.
2. AC-3 / E-AC-3 resolved and the audio codec matrix validated.
3. Native video view trial done and the approved solution built.
4. CPU, GPU, memory and battery optimized from measurements.
5. Android 4× investigation done.
6. Audio enhancement presets validated.
7. Hardware-accelerated playback validated.
8. Subtitles, audio tracks, seeking, playback speed and background playback validated.
9. Local files, HTTP and HLS validated.
10. Real films tested.
11. Size, start-up and smoothness preserved.
12. Android, Google TV, macOS and Windows all validated.
13. A stable, versioned, reusable playback interface for Streamflix (phase P2-M).
14. DEV builds for all platforms.
15. The owner's manual test and pull request approval.
16. Production release published and verified.

## P0 (current)

### P0-A Android Dolby audio

1. Reproduce the AC-3 and E-AC-3 failure with real files on DEV and on production 1.11.3. Report
   whether it is a regression or an existing limitation, and why the audio failure stops the video.
2. An audio decoder failure never stops or crashes playback: select another playable audio track;
   if none, continue video only with a clear message. Hardware video decoding stays on.

Accept when both real files keep playing video on the phone and Google TV emulators, the fallback
and the message are covered by tests, and the regression question is answered with evidence.

### P0-B macOS HDR colour

1. State whether the pale HDR picture is new or also in 1.11.3 (screenshots of the JaiPlayer window
   only).
2. HDR-to-SDR tone mapping (PQ, HLG, BT.2020) in the existing frame shader. Report what true HDR
   output on an HDR display needs; do not build it.

Accept when a colour test matches a reference within a stated tolerance, screenshots are close to
VLC's, and CPU and GPU are not worse beyond noise.

### P0-A result (2026-11-28)

Done, see [Android playback CPU](plan-android-cpu.md), "Dolby audio failure": an existing
limitation, not a regression; the video now keeps playing. Sound for AC-3 / E-AC-3 on such a device
still needs a decision (P1-A).

**Gate 1:** stop after P0; the owner decides the 1.12.0 release scope. No DEV build, tag or release
before that.

## Later phases (each only on the owner's word)

- **P1-A Audio compatibility** (report first, no code): AC-3, E-AC-3, DTS, TrueHD, AAC, FLAC, MP3
  and Opus on Android, Google TV, macOS and Windows. Options: native decoding, passthrough
  (validated on Google TV), a software decoder limited to the missing codecs, an optional
  downloadable component; for each, licence and distribution terms, installed size, CPU, memory,
  battery, and how far it reduces Streamflix server-side audio conversion. No complete multimedia
  engine. Recommend one. **Gate 2.**
- **P1-B macOS baseline:** 20 minutes of each real film in JaiPlayer DEV, VLC and IINA under equal
  conditions: app CPU, whole-Mac CPU, GPU, memory at start and end, battery drain, time to first
  picture, dropped frames, stability. Verify VideoToolbox decoding independently of drawing.
- **P1-C Native video view trial.** Stop before it and ask the owner to switch to Opus High; its
  prompt comes separately. **Gate 3:** go or no-go.
- **P1-D Android CPU:** compare with VLC at equal frame delivery and decoder configuration, with a
  per-process breakdown (app, mediaserver, codec service, surfaceflinger) and the display refresh
  rate during playback; then 4× against VLC and MX Player without losing speed accuracy or frames.
  Change code only for a clear measured cause.
- **P2:** skip drawing when the window is hidden or minimized (audio and picture-in-picture
  unaffected); JVM and native memory tuning from measurements only, with no limits that cause
  instability or heavy garbage collection; Streamflix compatibility (http and HLS links, start
  position, progress back); final real-film benchmarks; then the DEV build for the owner's test.
  **Gate 4.**
- **P2-M Playback interface for Streamflix:** a stable, versioned, reusable playback interface.
  Starts only when Claude Chat's specification arrives; no design work before that.
- **P3:** further size reduction, only if asked.
