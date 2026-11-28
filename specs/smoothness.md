---
type: Specification
title: Smooth playback
description: How desktop video reaches the screen without stalls, how seeking and playback speed behave, and how smoothness is measured.
tags: [performance, desktop, libvlc, playback, speed]
---

# Smooth playback

Goal: playback as smooth and responsive as the best desktop players (IINA is the feature reference, never a code source). Android and Google TV already play through Media3 on a hardware `SurfaceView`; this page is mostly about the desktop path, where frames come from libVLC through video callbacks.

## Desktop frame path
- **YUV straight to the GPU.** libVLC delivers YUV 4:2:0 frames, not RGB (`VideoFrames.Chroma`): `P010` for VideoToolbox's 10-bit pictures (a plain copy, no conversion to 8-bit on the CPU) and 8-bit `I420` for everything else. 10-bit software decoding (files with subtitles) is converted to `I420` on purpose: handing the 16-bit planes to the GPU was measured and cost more ([macOS playback](plan-macos-playback.md)). The surface wraps the planes as Skia images over the native memory (no copy) and a runtime shader converts them to colour while drawing: limited range; BT.2020 weights for P010 of 3840 or 2160 and larger, BT.709 for 720 lines or larger, BT.601 below (libVLC 3 does not report the colour space through the callbacks). There is no HDR tone mapping. This Skia build cannot wrap 16-bit planes, so the P010 shader reads them as bytes and filters bilinearly itself. Subtitles are blended into the planes by libVLC, so they show as before. `VideoColourTest` draws both layouts through the real shader.
- **Three rotating buffers, no shared waiting.** libVLC decodes into one buffer, one holds the newest finished frame, one is on screen. A buffer on screen is not given back to libVLC until the surface has drawn a newer one, because the window may render a recorded frame after the draw call returns. A lock guards only the three indices, so the decoder and the window never wait for each other (`VideoFrames.kt`).
- **Redraw only the video.** A new frame invalidates the video canvas's drawing, not the composition.

- **Small GPU texture cache.** Each frame's planes are new Skia images, uploaded as new textures; Skia keeps old ones until its cache budget (256 MB) is full, about 360 MB of graphics memory holding frames that are never drawn again. `GpuCacheLimit` sets the window's budget to 64 MB (reached by reflection, since Skiko does not expose it; if that fails nothing changes, and `JAIPLAYER_GPU_CACHE=default` turns it off for diagnosis). Measured on an Apple chip: 187 to 237 MB less memory while playing, the same CPU, 30 of 30 frames drawn at 4K.

## Seeking
- Seeks are coalesced in the libVLC engine: while one runs, newer requests replace each other, and only the latest is sent. Dragging the bar or holding an arrow key never builds a queue.
- The seek bar scrubs live: the picture follows the thumb while it is dragged, and letting go seeks to the exact spot.
- Just after a seek, the bar keeps showing the target until libVLC reports a time near it (at most 1.5 s), so it never jumps back and forth.

## Playback speed
- Range **0.25× to 4×**, the same on both engines (`MIN_SPEED`, `MAX_SPEED`).
- The speed menu has a fine control at the top (− and + move by 0.05× and keep the menu open, so any speed such as 1.35× can be set) and the usual speeds below: 0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4. The keys `[` and `]` (and the remote) step through the usual speeds.
- Sound keeps its pitch (libVLC's time stretching, Media3's Sonic). The speed carries over from one episode to the next.

## Measuring
- `JAIPLAYER_PERF=1` makes the desktop app print, once a second, how many frames libVLC delivered, how many the window drew and the slowest draw (`FrameStats.kt`). It also turns on libVLC's statistics. Off and free otherwise.
- `VlcSpeedIntegrationTest` plays `JAIPLAYER_TEST_MEDIA` (40 s or longer) at 1×, 2×, 2.5×, 3× and 4× and fails if time does not move at that speed, if any audio buffer is lost, or if fewer than 20 pictures a second are shown.
- macOS slows drawing for a window that another window covers; measure with the player window uncovered.

Measured on an Apple-silicon Mac (2026-11-25), test clips 1080p60 H.264 and 2160p30 HEVC, software decoding, window uncovered:

| | Before | After |
|---|---|---|
| UI-thread draw time, 4K frame | up to 2 ms | under 0.1 ms |
| CPU, 4K HEVC at 1× | 125% | about 65% |
| CPU, 1080p60 at 1× | 67% | about 45% |
| 4K HEVC at 2× / 4×: pictures shown per second (dropped in 3 s) | 32 (77) / 26 (313) | 60 (0) / 115 (0) |
| 1080p60 at 4×: pictures shown per second (dropped) | 88 (454) | 225 (0) |
| Audio buffers lost at any speed up to 4× | 0 | 0 |

## Not done yet
- Hardware decoding on macOS (VideoToolbox) stays off: subtitles cannot be blended onto VideoToolbox frames that arrive through callbacks ([Constraints](constraints.md)). It would cut CPU use further for 4K and HDR.
- Hand checks on real Windows machines and on Android and Google TV devices at 2×–4× (they never block a release).
