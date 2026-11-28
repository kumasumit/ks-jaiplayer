---
type: Plan
title: Audit against the suite standards
description: KS JaiPlayer checked against KS-Suite's architecture, security and storage standards, with each item classified and the plan for the next release.
tags: [plan, audit, architecture, storage, security, performance, dev]
---

# Audit against the suite standards

Audited 2026-11-27 against KS-Suite's [architecture standards](https://github.com/kumasumit/ks-suite/blob/main/specs/architecture-standards.md) and [release cycle](https://github.com/kumasumit/ks-suite/blob/main/specs/release-cycle.md), on production v1.11.3 (`windows-v1.8.0` for Windows). Progress: [Progress](progress.md).

## Class: C, confirmed

JaiPlayer keeps only settings, recent files and resume positions (a path or URI and a time), through `SettingsStore`: `SharedPreferences` on Android and Google TV, `java.util.prefs` on desktop. Nothing secret, nothing costly to lose, no structured data. So: no SQLite, encryption, app lock or backups.

## Items

| Item | Finding | Class |
|---|---|---|
| Storage | native preferences per platform, per OS user, as class C asks | Not applicable (already right) |
| Settings export/import | a handful of settings; resume positions are tied to one device's paths | Optional, not planned |
| SQLite, encryption, app lock, `.ksbackup` | no data that needs them | Not applicable |
| Architecture | minimal core plus compiled-in plugins ([Plugins](plugins.md)); `PlayerEngine` per platform | Not applicable (already right) |
| Dependencies | Compose Multiplatform, coroutines, JNA (Apache-2.0), Media3 and tvprovider (Apache-2.0), ks-utility, bundled LGPL libVLC; each is in use ([Licences](licences.md), [Dependencies](dependencies.md)) | Not applicable (nothing to remove) |
| Dependency vulnerability alerts | Dependabot alerts are off on the repository (free) | Recommended: the owner turns them on in the repository's security settings (owner step) |
| Permissions | internet, media read, foreground playback service, `REQUEST_INSTALL_PACKAGES` for the updater (not in the Play build); cleartext http for home servers ([Constraints](constraints.md)) | Not applicable (each is needed) |
| Privacy | no telemetry or ads (`ks-suite-check`); [`PRIVACY.md`](../PRIVACY.md) | Not applicable (already right) |
| Android backup | the system backs up preferences per application ID, so DEV and PROD never restore into each other | Not applicable |
| DEV/PROD side by side | done: [DEV builds side by side](dev-variant.md); checked on the emulators and this Mac | Required, done; Windows and real devices pending the owner |
| ks-utility v0.19.0 (DEV mode, `SHA256SUMS`, `environment: production`) | the bump workflow moves the pin; `release.yml` uses `secrets: inherit` | Required |
| DEV Android signing | the four `DEV_*` secrets are not set yet | Required, owner step (blocks signed DEV APKs) |
| Size and startup baseline | sizes below; cold start and memory not yet measured | Required |

## Baseline (v1.11.3 release files)

| Platform | File | Size |
|---|---|---|
| Android phone | APK / AAB | 1.98 MB / 5.19 MB |
| Google TV | APK / AAB | 1.99 MB / 5.20 MB |
| macOS | dmg arm64 / x64 | 103.3 MB / 113.1 MB |
| Windows | msi x64 (`windows-v1.8.0` line) | 96.9 MB |

Cold start and memory: measured on the emulators and this Mac with the DEV build next to production before the release pull request; Windows and real devices are reported as pending the owner's check.

## Plan

1. Commit the DEV variant on `dev` (`feat:`, so the next release is 1.12.0).
2. Take ks-utility v0.19.0 (bump workflow).
3. Run `:shared:desktopTest :shared:check`; measure sizes, cold start and memory against v1.11.3.
4. DEV builds for all four platforms with the release workflow's rehearsal, once the owner has added the DEV signing secrets.
5. Release pull request `dev` → `main` with the DEV build report, left open for the owner's test and approval.
