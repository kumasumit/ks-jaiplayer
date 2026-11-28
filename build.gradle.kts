// Plugins are declared once here and applied in the modules that use them.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// ---- Versioning (never hardcoded) ----------------------------------------------------------------
// Each platform has its own version (specs/plan-per-platform.md): a tag <platform>-vX.Y.Z releases
// that platform, a plain vX.Y.Z every platform. Priority: -PversionName / -PversionCode (the release
// workflow passes them from the tag), then the higher of the platform's latest own tag and the latest
// plain tag, then a development fallback. The modules read appVersionName.<platform> and
// appVersionCode.
fun git(vararg args: String): String? = runCatching {
    val result = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim().ifEmpty { null } else null
}.getOrNull()

fun versionParts(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
fun higherVersion(a: String, b: String): String {
    val x = versionParts(a)
    val y = versionParts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return if (d > 0) a else b
    }
    return a
}
val plainTagVersion: String? = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")?.removePrefix("v")

/** Platforms: android, google-tv, mac, windows (the tag prefixes). */
fun appVersionNameOf(platform: String): String =
    // A DEV build (ks-utility's DEV mode) passes its own version, X.Y.Z-dev+sha7.
    providers.gradleProperty("ksDevVersion").orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("versionName").orNull
        ?: listOfNotNull(
            plainTagVersion,
            git("describe", "--tags", "--abbrev=0", "--match", "$platform-v[0-9]*")?.removePrefix("$platform-v"),
        ).reduceOrNull(::higherVersion)
        ?: "0.0.0" // A build from source: no version, so it never checks for updates by itself.


val appVersionCode: Int =
    providers.gradleProperty("versionCode").orNull?.toIntOrNull()
        ?: git("rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: 1

for (platform in listOf("android", "google-tv", "mac", "windows")) extra["appVersionName.$platform"] = appVersionNameOf(platform)
extra["appVersionCode"] = appVersionCode

// ---- The two Android apps: phone (mobile/android) and Google TV (tv/google-tv) ---------------------
// Separate APKs and AABs with the same package name, version and keystore, so other KS apps hand
// playback to com.ks.jaiplayer whichever one is installed, and each updates over itself. Google
// Play needs a different versionCode for every bundle of one app: the TV app adds TV_CODE_OFFSET,
// which also stops the phone build from being installed over the TV build (a lower code).
val TV_CODE_OFFSET = 1_000_000_000
val keystoreProperties = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Blank counts as missing: GitHub Actions passes an unset secret as an empty string.
fun signingValue(env: String, key: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

// DEV builds (-PksVariant=dev, KS-Suite "DEV and PROD side by side") install next to production:
// application ID + ".dev" (so their own data), "KS JaiPlayer DEV", DEV-marked icon and banner, and
// no updater. Production's ID, name and signing are unchanged; DEV signs with the DEV keystore secrets.
val devVariant = providers.gradleProperty("ksVariant").orNull == "dev"

val androidCompileSdk = libs.versions.androidCompileSdk.get().toInt()
val androidMinSdk = libs.versions.androidMinSdk.get().toInt()
val androidTargetSdk = libs.versions.androidTargetSdk.get().toInt()

subprojects {
    plugins.withId("com.android.application") {
        val tv = path == ":tv:google-tv"
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileSdk = androidCompileSdk
            defaultConfig {
                applicationId = "com.ks.jaiplayer"
                minSdk = androidMinSdk
                targetSdk = androidTargetSdk
                versionCode = appVersionCode + if (tv) TV_CODE_OFFSET else 0
                versionName = appVersionNameOf(if (tv) "google-tv" else "android")
                manifestPlaceholders["appLabel"] = if (devVariant) "KS JaiPlayer DEV" else "KS JaiPlayer"
                if (devVariant) applicationIdSuffix = ".dev"
                if (tv) manifestPlaceholders["tvBanner"] = if (devVariant) "@drawable/tv_banner_dev" else "@drawable/tv_banner"
            }
            if (devVariant) {
                sourceSets.getByName("main").res.srcDir(rootProject.file("shared/android-dev/res"))
            }
            val storeFilePath = signingValue("SIGNING_STORE_FILE", "storeFile")
            if (storeFilePath != null) {
                signingConfigs.create("release") {
                    storeFile = file(storeFilePath)
                    storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                    keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                    keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
                }
            }
            // R8 removes unused code and resources: a smaller APK/AAB and a faster start (also for the
            // `play` build, which starts from this one). Libraries bring their own keep rules.
            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                signingConfig = signingConfigs.findByName("release")
            }
            // The Google Play build (the AABs, ks-utility release workflow `play-build-type`): the
            // release build without the in-app updater, which Play forbids.
            buildTypes.create("play") {
                initWith(buildTypes.getByName("release"))
                matchingFallbacks += "release"
            }
            sourceSets.getByName("play").manifest.srcFile(rootProject.file("shared/android-play/AndroidManifest.xml"))
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            androidResources { localeFilters += listOf("en", "hi") }
        }
        dependencies.add("implementation", project(":shared"))
    }
}
