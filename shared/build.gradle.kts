import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code used by two or more platforms: the player core and the whole interface (commonMain),
// the libVLC engine shared by macOS and Windows (desktopMain), and the Media3 engine shared by
// Android phones and Google TV (androidMain).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "com.ks.jaiplayer.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // JVM code shared by desktop and Android (ks-utility is JVM-only): app updates.
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.ks.utility) }
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.ks.utility)
                implementation(libs.jna)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                // Skia's native library for this machine, so tests can draw video frames (VideoColourTest).
                implementation(compose.desktop.currentOs)
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        androidMain.dependencies {
            implementation(libs.ks.utility)
            implementation(libs.androidx.activity.compose)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui.compose)
            implementation(libs.media3.session)
            implementation(libs.androidx.tvprovider)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

// ---- Interface text: ks-utility language files → Compose resources ------------------------------
// The app's en.json / hi.json and ks-utility's shared catalogue are converted to Android-style
// strings.xml, which Compose Multiplatform resources read on every platform. Needs Node 23.6+.
val langTool = rootProject.file("ks-utility/typescript/tools/lang.mjs")
val langDir = layout.projectDirectory.dir("src/commonMain/lang")
val sharedLangDir = rootProject.layout.projectDirectory.dir("ks-utility/lang/common")
val langResources = layout.buildDirectory.dir("generated/lang/composeResources")

val languageResources by tasks.registering(Exec::class) {
    description = "Converts the language files into Compose string resources."
    inputs.dir(langDir)
    inputs.dir(sharedLangDir)
    outputs.dir(langResources)
    val out = langResources.get().asFile
    // compose, not android: Compose Multiplatform shows Android escapes (You\'re) as they are (ks-utility v0.5.0).
    commandLine("node", langTool.path, "compose", langDir.asFile.path, "--out", out.path)
}

val checkLanguageFiles by tasks.registering(Exec::class) {
    description = "Checks the language files with the KS-Suite language-file check."
    inputs.dir(langDir)
    inputs.dir(sharedLangDir)
    commandLine("node", langTool.path, "check", langDir.asFile.path)
}

tasks.named("check") { dependsOn(checkLanguageFiles) }

compose.resources {
    publicResClass = true
    packageOfResClass = "com.ks.jaiplayer.resources"
    generateResClass = always
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = languageResources.map { langResources.get() },
    )
}

// The libVLC integration test plays the file named by JAIPLAYER_TEST_MEDIA, when set.
tasks.withType<Test>().configureEach {
    listOf("JAIPLAYER_TEST_MEDIA", "JAIPLAYER_VLC_LOG").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
}
