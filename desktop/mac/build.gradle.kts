import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// macOS entry point and packaging (.dmg). Builds a dmg only on a Mac.
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
// The class archive that makes the app start faster (after the plugin cache).
apply(from = rootProject.file("gradle/app-cds.gradle.kts"))

// jpackage needs MAJOR >= 1; untagged development builds package as 1.0.0.
val versionName = (rootProject.extra["appVersionName.mac"] as String).substringBefore('-')
val installerVersion = versionName.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) >= 1 } ?: "1.0.0"

// DEV builds (-PksVariant=dev, KS-Suite "DEV and PROD side by side") install next to the
// production app: their own bundle ID, name, icon and settings, no file types claimed, no updater.
val dev = providers.gradleProperty("ksVariant").orNull == "dev"
val appName = if (dev) "KS JaiPlayer DEV" else "KS JaiPlayer"

compose.desktop {
    application {
        jvmArgs += listOf("-Djaiplayer.version=${rootProject.extra["appVersionName.mac"]}")
        mainClass = "com.ks.jaiplayer.mac.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua")
        // Trackpad pinch (MacIntegration.pinch) uses the JDK's macOS gesture classes.
        jvmArgs += listOf("--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED")
        if (dev) jvmArgs += listOf("-Djaiplayer.variant=dev")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = appName
            packageVersion = installerVersion
            description = "KS JaiPlayer, a media player"
            vendor = "KS-Suite"
            appResourcesRootDir.set(layout.buildDirectory.dir("vlc-resources"))
            modules("java.prefs", "java.desktop", "jdk.unsupported")
            macOS {
                bundleID = if (dev) "com.ks.jaiplayer.dev" else "com.ks.jaiplayer"
                // KS JaiPlayer's icon (a blue tile with a ring and a play mark) on the macOS icon grid.
                iconFile.set(project.file(if (dev) "packaging/icon-dev.icns" else "packaging/icon.icns"))
                appCategory = "public.app-category.video"
                infoPlist {
                    // Video and audio files, at rank Alternate: listed in Open With, never the default
                    // app unless the user picks it. DEV needs them too, or macOS hands it no files.
                    extraKeysRawXml = file("packaging/document-types.plist").readText()
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
