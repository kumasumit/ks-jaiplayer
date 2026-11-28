import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Windows entry point and packaging (.msi). Builds an msi only on Windows (CI).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.ks.utility)
    implementation(libs.jna)
}

// libVLC and its LGPL plugins go into the installer (build/vlc-resources).
apply(from = rootProject.file("gradle/bundled-vlc.gradle.kts"))

// The MSI version needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName.windows"] as String).substringBefore('-')
val installerVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

val VIDEO_TYPES = mapOf(
    "mp4" to "video/mp4", "m4v" to "video/x-m4v", "mkv" to "video/x-matroska", "webm" to "video/webm",
    "avi" to "video/x-msvideo", "mov" to "video/quicktime", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
    "ts" to "video/mp2t", "m2ts" to "video/mp2t", "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "3gp" to "video/3gpp",
)
val AUDIO_TYPES = mapOf(
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac", "wav" to "audio/wav",
    "ogg" to "audio/ogg", "opus" to "audio/opus", "wma" to "audio/x-ms-wma",
)

// DEV builds (-PksVariant=dev, KS-Suite "DEV and PROD side by side") install next to the
// production app: their own upgrade code (so their own product), name, install folder, shortcuts,
// icon and app-data folder; no file types claimed; no updater.
val dev = providers.gradleProperty("ksVariant").orNull == "dev"

compose.desktop {
    application {
        jvmArgs += listOf("-Djaiplayer.version=${rootProject.extra["appVersionName.windows"]}")
        if (dev) jvmArgs += listOf("-Djaiplayer.variant=dev")
        mainClass = "com.ks.jaiplayer.windows.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = if (dev) "KS JaiPlayer DEV" else "KS JaiPlayer"
            packageVersion = installerVersion
            description = "KS JaiPlayer, a media player"
            vendor = "KS-Suite"
            appResourcesRootDir.set(layout.buildDirectory.dir("vlc-resources"))
            // jdk.accessibility lets Narrator and NVDA read the app (Java Access Bridge).
            modules("java.prefs", "java.desktop", "jdk.unsupported", "jdk.accessibility")
            windows {
                menu = true
                menuGroup = "KS-Suite"
                // KS JaiPlayer's icon, 16 to 256 px.
                iconFile.set(project.file(if (dev) "packaging/icon-dev.ico" else "packaging/icon.ico"))
                shortcut = true
                dirChooser = true
                // Fixed for the life of the app, so new versions upgrade the installed one.
                // DEV has its own, fixed too, so it never upgrades or removes production.
                upgradeUuid = if (dev) "b8e3c1d7-4f26-4a9e-8d05-7c2f9a61e4b0" else "5d0f2a4e-8a3b-4f7e-9c51-2b6f1e0c7a93"
                // "Open with" and double click in Explorer, for the same kinds of file as on macOS.
                // Only production claims them, so DEV never becomes their default app.
                if (!dev) {
                    for ((ext, mime) in VIDEO_TYPES) fileAssociation(mime, ext, "Video")
                    for ((ext, mime) in AUDIO_TYPES) fileAssociation(mime, ext, "Audio")
                }
            }
        }
        // Release builds are shrunk by ProGuard (unused library code removed; names kept).
        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false)
            configurationFiles.from(rootProject.file("shared/proguard/desktop.pro"))
        }
    }
}
