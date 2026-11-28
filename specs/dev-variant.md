---
type: Specification
title: DEV builds side by side with production
description: How a DEV build of KS JaiPlayer (-PksVariant=dev) installs next to the production app on every platform without touching it.
tags: [release, dev, android, google-tv, macos, windows]
---

# DEV builds side by side with production

KS-Suite's release cycle ("DEV, test, approval, production" and "DEV and PROD side by side" in [release-cycle.md](https://github.com/kumasumit/ks-suite/blob/main/specs/release-cycle.md)) has the owner test DEV builds by hand before any production release. A DEV build installs next to the production app and never changes it. `-PksVariant=dev` selects it; ks-utility's DEV mode passes it in the release workflow's rehearsal.

| | Production | DEV |
|---|---|---|
| Android phone and Google TV ID | `com.ks.jaiplayer` | `com.ks.jaiplayer.dev` (its own data, automatically) |
| Name | KS JaiPlayer | KS JaiPlayer DEV (launcher, window, Dock/taskbar, home screen title) |
| Icon and TV banner | blue tile | orange tile marked DEV (`shared/android-dev/res`, `tv_banner_dev`, `packaging/icon-dev.icns`, `icon-dev.ico`) |
| macOS bundle ID | `com.ks.jaiplayer` | `com.ks.jaiplayer.dev` |
| macOS folders | `~/Library/Application Support/KS JaiPlayer`, `~/Library/Caches/com.ks.jaiplayer` | `… /KS JaiPlayer DEV`, `… /com.ks.jaiplayer.dev` |
| Windows MSI upgrade code | `5d0f2a4e-8a3b-4f7e-9c51-2b6f1e0c7a93` | `b8e3c1d7-4f26-4a9e-8d05-7c2f9a61e4b0` (so its own product code) |
| Windows install folder, Start menu, shortcut | KS JaiPlayer | KS JaiPlayer DEV |
| Windows folders | `%APPDATA%\KS JaiPlayer`, `%LOCALAPPDATA%\KS JaiPlayer` | `… \KS JaiPlayer DEV` |
| Desktop settings (`java.util.prefs`) | node `com/ks/jaiplayer` | node `com/ks/jaiplayer-dev` |
| Single instance (Windows lock and port file) | in `%LOCALAPPDATA%\KS JaiPlayer` | in `%LOCALAPPDATA%\KS JaiPlayer DEV` |
| Video and audio file types | claimed (Open With, double click); on macOS at rank Alternate | macOS: claimed at rank Alternate like production, so Open With and `open -a` work but DEV never becomes the default app; Windows: not claimed, so DEV never becomes their default app |
| In-app updater | on (GitHub releases) | off: a DEV build never offers production files |
| Version | from the tag | `-PksDevVersion=X.Y.Z-dev+sha7` (Android version name; the desktop installers keep X.Y.Z, which jpackage needs), files named `ks-jaiplayer-dev-<version>-<platform>.<ext>` |
| Android signing | suite key (`ks-release`) | DEV keystore secrets `DEV_KEYSTORE_BASE64`, `DEV_KEYSTORE_PASSWORD`, `DEV_KEY_ALIAS`, `DEV_KEY_PASSWORD` (the owner creates them; `release.yml` passes all secrets) |

Production's IDs, bundle ID, upgrade code, names and signing do not change.

No DEV data ever reaches production (KS-Suite d7743ce): no `sharedUserId`, no shared files, folders, databases or keychain items, no import or migration from DEV, and Android's backup is kept per application ID, so a backup of one never restores into the other. All data is per OS user (the Android user profile, macOS `~/Library`, Windows `%APPDATA%` and `%LOCALAPPDATA%`), never `ProgramData` or the install folder.

The switch: `DesktopVariant` (`shared/src/desktopMain`, reads `-Djaiplayer.variant=dev`, which the desktop builds pass when `ksVariant=dev`); on Android the `.dev` application ID (`PlatformHost.isDev`, `MainActivity`). The root `build.gradle.kts` adds the ID suffix, the `appLabel` and `tvBanner` manifest placeholders and the DEV icon resources.

Checked on 2026-11-27: production v1.11.3 and DEV installed and ran side by side on the phone and Google TV emulators, and on this Mac. Still to check by hand: a real phone and TV, and Windows (MSI install, upgrade and uninstall of each without touching the other).
