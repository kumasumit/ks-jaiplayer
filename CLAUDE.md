# ks-jaiplayer

Multi-platform media player for Android, Google TV, Windows, and macOS.

## Specs
- All specs for this project live in `specs/` in the repository root, as an Open Knowledge Format (OKF) v0.2 bundle. Never create spec files anywhere else. Start at `specs/index.md`.
- One topic per file. Every file except `index.md` and `log.md` starts with YAML frontmatter: `type` (required: `Specification`, `Policy`, `Plan`, `Playbook` or `Reference`), `title`, `description` (one sentence), `tags`.
- When a spec is added: list it in `specs/index.md` with its description. When a spec is added or changed: add a line under today's date (`# YYYY-MM-DD`, newest first) in `specs/log.md`.
- Link between specs with relative markdown links (`[Dependencies](dependencies.md)`), so they also work on GitHub.

## Stack
- Kotlin Multiplatform + Compose Multiplatform
- Playback: Media3/ExoPlayer on Android and Google TV; libVLC 3.x on macOS and Windows through our own JNA binding (not vlcj, which is GPL-3.0). See `specs/licences.md`.
- A minimal core plus plugins: features beyond open/play/seek/volume/full screen/series/speed are `JaiPlugin`s (`specs/plugins.md`).
- Package: `com.ks.jaiplayer`

## Module layout
| Gradle project | Folder | Purpose |
|---|---|---|
| `:shared` | `shared/src/commonMain` | Player core, plugin API, built-in plugins, the whole UI. **No platform imports.** |
| | `shared/src/jvmSharedMain` | JVM code for desktop and Android: app updates (ks-utility) |
| | `shared/src/desktopMain` | libVLC engine (JNA), video surface, desktop app runner |
| | `shared/src/androidMain` | Media3 ExoPlayer engine, video surface, and everything both Android apps run: the activity, manifest, pickers, picture-in-picture, updates |
| `:desktop:mac` | `desktop/mac/` | macOS entry point (menu bar, Finder Open With), dmg with bundled libVLC |
| `:desktop:windows` | `desktop/windows/` | Windows entry point, msi with bundled libVLC |
| `:mobile:android` | `mobile/android/` | The phone app (APK + AAB): phone launcher entry |
| `:tv:google-tv` | `tv/google-tv/` | The Google TV app (`-google-tv` APK + AAB): Leanback entry, banner, TV layout |

## Platform constraints
- Platform code goes behind the `PlayerEngine` interface (`commonMain`) with `expect`/`actual` `VideoSurface`. Never call ExoPlayer or libVLC from `commonMain`.
- **Separate phone and Google TV apps.** `mobile/android` and `tv/google-tv` are two application modules with the same package name (`com.ks.jaiplayer`), version and keystore; the TV app's versionCode adds 1,000,000,000 (Google Play needs a different code per bundle). Both only add a launcher entry to what `shared/src/androidMain` declares. The TV app declares `android.software.leanback` required and `android.hardware.touchscreen` not required, and marks itself with the `com.ks.jaiplayer.FORM_FACTOR` meta-data (`formFactorOf`). TV screens must be D-pad navigable. Each app updates to its own build (`UpdatePlatform.GOOGLE_TV` / `ANDROID`).
- **Desktop installers carry libVLC 3.x** with only its LGPL plugins (`gradle/bundled-vlc.gradle.kts`, `vlc/VlcBundle.kt`; see [Bundled libVLC](specs/plan-bundled-vlc-and-plugins.md)). The app plays only through that copy and never looks for an installed VLC; `JAIPLAYER_VLC_PATH` replaces it for diagnosis only. If it fails to load, show a clear message (reinstall the app). Don't crash. On macOS files without subtitles decode with VideoToolbox (`MAC_HARDWARE_DECODING`); a file with subtitles, or one where a subtitle is turned on, decodes in software (`MAC_VLC_ARGS`), because subtitles cannot be blended onto VideoToolbox frames delivered through callbacks.
- **Android playback behaves like a system media player** (both apps): audio focus, pause on headphones unplugged, decoder fallback, a Media3 media session (`PlayerSession`) with the series' next/previous, picture-in-picture auto-enter on the phone, R8 on release and Play builds. See `specs/constraints.md`.
- Keyboard shortcuts on desktop (space, arrows, F for fullscreen) must match the remote's D-pad actions on TV.
- Implement the `ACTION_VIEW` intent for `video/*` and `audio/*` so other KS apps (`ks-streamflix`, `ks-tv-launcher`) can hand off playback. It accepts `content`, `file`, `http` and `https` URIs; an http(s) link must play without extra headers (ks-streamflix sends signed links), plain http is allowed for home servers, and `EXTRA_TITLE` names it.

## Commands
```bash
./gradlew :shared:desktopTest :shared:check                  # tests and the language-file check
JAIPLAYER_TEST_MEDIA=<video with 2 audio tracks> ./gradlew :shared:desktopTest   # + real libVLC test
./gradlew :mobile:android:installDebug                       # Android phone
./gradlew :tv:google-tv:installDebug                         # Google TV
./gradlew :desktop:mac:run --args="<file or folder>"         # run on macOS
./gradlew :desktop:mac:packageReleaseDmg                     # .dmg (on a Mac)
./gradlew :desktop:windows:packageReleaseMsi                 # .msi (on Windows / CI)
./gradlew -PksVariant=dev <any of the above>                 # DEV build, installs next to production (specs/dev-variant.md)
```
Native installers only build on their own OS. Releases use ks-utility's shared release workflow (`.github/workflows/release.yml` calls it). The build needs Node 23.6+ for the language files. `JAIPLAYER_VLC_LOG=<file>` writes libVLC's log for diagnosis only together with `JAIPLAYER_VLC_PATH=<a full VLC>`, since the logging plugin is GPL and not bundled. The real libVLC test uses the bundled copy in `desktop/mac/build/vlc-resources/common/vlc` (`./gradlew :desktop:mac:bundleVlc`); `JAIPLAYER_TEST_VLC=<folder with lib/ and plugins/>` picks another. `JAIPLAYER_PERF=1` prints frame rates once a second (see `specs/smoothness.md`). `JAIPLAYER_NO_SMTC=1` and `JAIPLAYER_NO_THUMBBAR=1` turn off the Windows media overlay and taskbar buttons, and `JAIPLAYER_NO_HW=1` VideoToolbox on macOS, for diagnosis. The macOS app image gets an AppCDS archive during packaging (`gradle/app-cds.gradle.kts`).

## Platform folders
- Every KS-Suite app repo has the same top-level platform folders: `tv/google-tv/`, `desktop/mac/`, `desktop/windows/`, `mobile/android/` and `mobile/ios/`. All five exist in every app repo, whether or not the app supports that platform yet.
- This app targets `mobile/android`, `tv/google-tv` (a separate TV app), `desktop/windows` and `desktop/mac`. The other folders are empty on purpose (a `.gitkeep` only): they mark where that platform's code would go, not missing work. Do not add code for a platform that is not a target without the owner asking.
- Three layers, narrowest first: a platform folder holds code for one platform of this app; `shared/` in the repository root holds code used by two or more platforms of this app, written once and never copied between platform folders; `ks-utility` holds generic code used by several apps. Here `shared/` is the Kotlin Multiplatform module (UI, state, playlist and library logic).
- The Gradle build follows this layout (see Module layout): platform entry points and packaging in the platform folders, code used by more than one platform in `shared/`.
- Never rename, merge or remove these folders, and never add another top-level platform folder; the layout is a suite-wide rule.

## Shared code (`ks-utility`)
- Generic utility code shared across the KS-Suite apps lives in the `ks-utility` repository. It is a dependency of this app: a git submodule at `ks-utility/`, pinned to a commit. After cloning, run `git submodule update --init`.
- Before writing a generic helper (one that knows nothing about this app), look in `ks-utility/kotlin` and use it from there. If it is missing and another app would need it too, add it in the `ks-utility` repository, not here. Application-specific logic stays in this repo.
- Never edit files under `ks-utility/` from this repo. Change them in the `ks-utility` repository, then move the pin: `git -C ks-utility fetch && git -C ks-utility checkout <commit>`, and commit the changed `ks-utility` entry.
- New ks-utility tags arrive by themselves: `.github/workflows/ks-utility-bump.yml` (ks-utility's shared bump workflow) moves the pin on `dev` the day ks-utility releases, after the tests pass. The shared workflows in `.github/workflows/` are called at `@main`, which is always ks-utility's newest release (its `main` moves only with a tag), so they need no bump and no workflow token; ks-suite-check requires it.
- Gradle wiring: `includeBuild("ks-utility/kotlin")` in `settings.gradle.kts`, `ks-utility = { module = "com.ks:utility" }` in `gradle/libs.versions.toml`, `implementation(libs.ks.utility)` where used. It is a plain Kotlin/JVM library with no Android SDK.
- `ks-utility` is JVM-only: use it from `jvmSharedMain`, `androidMain`, `desktopMain` and the app modules, never from `commonMain`. Its language files (`lang/common`) and `typescript/tools/lang.mjs` produce the Compose string resources.
- CI checks out with `submodules: true` (`.github/workflows/release.yml`).

## Working efficiently (keep token use low)
- Use the knowledge graph first: follow the rules in the `graphify` section at the end of this file. [graphify](https://github.com/Graphify-Labs/graphify) is a development tool only; it is never a dependency of the code and is never bundled or shipped.
- One-time setup on a new machine (owner): `uv tool install graphifyy` (the package name has two y's), then `/graphify .` in Claude Code. The setup in `.claude/` is committed; `graphify-out/` is generated and git-ignored.
- Read only the specs the task needs, and do not re-read a file that has not changed in this session.
- Never read generated files: `build/`, `.gradle/`, `graphify-out/graph.json`.
- Keep command output short (show only failures), and keep summaries brief.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- `graphify-out/` is generated and git-ignored: never commit it.
