---
type: Plan
title: macOS playback
description: How JaiPlayer's playback cost on macOS is measured, and the results of Job 2 of the playback optimization brief (10-bit frame path).
tags: [macos, performance, playback, libvlc]
---

# macOS playback

Job 2 of the brief "Playback Optimization and StreamFlix Readiness". The [Android rule](plan-android-cpu.md) applies: compare on the whole device, with the same file, machine and method.

## Method
- MacBook, Apple M5 (10 cores), macOS 27.2.1, on battery, sound muted, nothing else building or playing.
- Release app images (`createReleaseDistributable`, DEV variant): **before** is `5cdace2`, **after** is the change, built the same way. Run from the executable with the file as argument and `JAIPLAYER_PERF=1`.
- The window is set to 1280×748; each run warms up for about 14 s and is then measured for 30 s. Runs alternate before and after, 2 rounds. A run with the window covered, or with another busy process, is thrown away.
- **Whole device**: busy time of all CPUs (`host_statistics`), percent of one core (1000 % in all). **App**, `VTDecoderXPCService`, `WindowServer`, `coreaudiod`: CPU time from `ps`. **GPU**: the mean of `ioreg`'s "Device Utilization %". **Memory**: `footprint`.
- Clips (made with ffmpeg, test pattern): 4K30 HEVC 10-bit 20 Mb/s without subtitles (VideoToolbox), the same with a subtitle track shown (software decoding), 1080p60 H.264 10 Mb/s (VideoToolbox, the control).
- Whole-system power (`AccumulatedSystemLoad`) was too noisy over 30 s to show a difference (5.7 to 17.8 W) and is not used.

## Change
libVLC 3 proposes a chroma to the video callbacks; the app always asked for 8-bit `I420`. For VideoToolbox's 10-bit frames (`CVPP`) it now asks for `P010`, a plain copy, and the GPU shader reads the 16-bit planes ([Smooth playback](smoothness.md)).

## Results
Two rounds each; all runs showed every frame, with no crash.

| Case | | App CPU | Whole device | GPU | Memory |
|---|---|---|---|---|---|
| 4K 10-bit, VideoToolbox | before | 50.7–51.3 % | 123–134 % | 20–42 % | 490–530 MB |
| | after (P010) | **33.2–34.6 %** | **116–120 %** | 35–37 % | 567–600 MB |
| 4K 10-bit + subtitles, software | before | 71.7–72.6 % | 136–141 % | 19–23 % | 1.57–1.62 GB |
| | tried: 16-bit planar to the GPU (`I0AL`) | 81.7–81.8 % | 156–165 % | 37–47 % | 1.69 GB |
| | after (converted to `I420` as before) | 70.9 % (1 clean round) | 141 % | 19 % | 1.56 GB |
| 1080p60 8-bit, VideoToolbox (path unchanged) | before | 32.9–33.5 % | 133–140 % | 30 % | 470 MB |
| | with the change | 31.1–31.7 % | 126–132 % | 29–30 % | 405 MB |

## Findings and decisions
- **Kept: P010.** App CPU for 4K 10-bit falls by about a third (17 points), the whole device by about 10 points. The cost: about 70 MB more memory (frames twice the size) and, in most runs, a busier GPU. Battery effect not proven either way.
- **Not kept: 16-bit planar from software decoding.** libVLC's conversion to 8-bit costs less than uploading frames twice the size: +10 CPU points and +15–25 for the whole device when it was tried.
- 8-bit video is unchanged.
- Colours: P010 of 3840 or 2160 and larger now uses BT.2020 weights (it used BT.709 before). There is still no HDR tone mapping; a 10-bit UHD video in BT.709 would be slightly off, since libVLC 3 does not report the colour space.
- The test pattern is one kind of content; real films, Windows and Intel Macs are not measured.

## Against VLC and IINA

JaiPlayer DEV `63e650c` (CI build), VLC 3.0.24 and IINA 1.5.1 with their default settings, method as above (VLC and IINA have their own window chrome; video about 1280×720 in each). Files: 1080p60 H.264 10 Mb/s with AAC; Blu-ray style 1080p23.976 H.264 36 Mb/s with AC-3 5.1; 4K23.976 HEVC 10-bit HDR10-tagged 61 Mb/s with E-AC-3 5.1 (all test pattern). Clean runs only, 2 or 3 each; VLC on 4K has one.

| File | Player | App CPU | Whole device | GPU | Memory | Sound starts after |
|---|---|---|---|---|---|---|
| 1080p60 | JaiPlayer | 30–32 % | 126–138 % | 28–31 % | 406–424 MB | 0.85–1.3 s |
| | VLC | **12–13 %** | **98–121 %** | **5 %** | **174–188 MB** | 1.1–1.2 s |
| | IINA | 27 % | 138–151 % | 60–62 % | 531–555 MB | **0.7–1.0 s** |
| Blu-ray style | JaiPlayer | 18 % | 86–88 % | 13–15 % | 413–458 MB | 1.2–1.3 s |
| | VLC | **9 %** | **76–82 %** | **1–3 %** | **175 MB** | 1.1 s |
| | IINA | 13–14 % | 95–96 % | 28 % | 372–380 MB | **0.7 s** |
| 4K HDR | JaiPlayer | 34–36 % | 118–126 % | 35–37 % | 592–667 MB | 1.2–1.6 s |
| | VLC | **12 %** | **88 %** | **8 %** | **176–184 MB** | 1.1 s |
| | IINA | 15–17 % | 98–119 % | 37–62 % | 574–695 MB | **0.7–0.9 s** |

- **Size** (installed): JaiPlayer DEV 195 MB (dmg 83 MB), VLC 148 MB, IINA 197 MB; production 1.11.3 is 230 MB.
- **Stability**: no crash and no exit in any run; JaiPlayer showed every frame in uncovered runs. Runs are about a minute each; there is no long soak or seek stress on the Mac.
- **Picture**: on the HDR-tagged clip JaiPlayer's colours are paler than VLC's and IINA's: it has no HDR tone mapping. The clip is a synthetic pattern, so a real HDR film is still to be compared.
- **Reading it**: VLC costs the least because it draws decoder frames itself, while JaiPlayer copies every frame into memory and uploads it to the GPU. JaiPlayer is level with IINA at 1080p60 and behind it on 4K and start time.
- "Sound starts after" is the time from launch to the player's first power assertion for audio output, one method for all three. Power draw was too noisy to compare.

## Left for later
- 4K 10-bit with subtitles needs about 1.6 GB of memory and twice the CPU of hardware decoding, because subtitles force software decoding. Drawing subtitles in the app over hardware-decoded frames would remove that, and is a larger change.
