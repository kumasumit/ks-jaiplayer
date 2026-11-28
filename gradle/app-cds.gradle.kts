// The other chip's Skia library out of the macOS app, then AppCDS for it (desktop/AppCds.kt): after the app image is made and libVLC's plugin
// cache is in it, the app is started once in training mode and an archive of its classes is added,
// so it starts about a second sooner. The dmg is built from that app image. If anything fails,
// the app ships without the archive.
if (System.getProperty("os.name").startsWith("Mac")) {
    for (type in listOf("", "Release")) {
        val appDir = layout.buildDirectory.dir(if (type == "") "compose/binaries/main/app" else "compose/binaries/main-release/app")
        // Skiko's runtime jar holds the Skia library for both Mac chips. Compose unpacks this
        // chip's copy into the app (skiko.library.path) and the other one stays in the jar, 22 MB
        // that can never load. Removed before the plugin cache re-seals the app and before the
        // class archive records the jars.
        val strip = tasks.register("stripOtherChipSkia$type") {
            description = "Removes the other Mac chip's Skia library from the ${type.ifEmpty { "debug" }} app."
            dependsOn("create${type}Distributable")
            doLast {
                val contents = appDir.get().asFile.listFiles { f -> f.name.endsWith(".app") }?.singleOrNull()
                    ?.let { File(it, "Contents/app") } ?: return@doLast
                val unpacked = contents.list()?.filter { it.startsWith("libskiko-macos-") && it.endsWith(".dylib") }.orEmpty()
                if (unpacked.isEmpty()) return@doLast // nothing unpacked: leave the jar as Skiko's only source
                contents.listFiles { f -> f.name.startsWith("skiko-awt-runtime-macos-") && f.extension == "jar" }.orEmpty().forEach { jar ->
                    val unused = { name: String -> name.startsWith("libskiko-macos-") && unpacked.none { name.startsWith(it) } }
                    val names = java.util.zip.ZipFile(jar).use { zip -> zip.entries().toList().map { it.name } }
                    if (names.none(unused)) return@forEach
                    val stripped = File(jar.path + ".tmp")
                    java.util.zip.ZipFile(jar).use { zip ->
                        java.util.zip.ZipOutputStream(stripped.outputStream()).use { out ->
                            zip.entries().asSequence().filterNot { unused(it.name) }.forEach { entry ->
                                out.putNextEntry(java.util.zip.ZipEntry(entry.name).apply { time = entry.time })
                                zip.getInputStream(entry).use { it.copyTo(out) }
                                out.closeEntry()
                            }
                        }
                    }
                    val saved = jar.length() - stripped.length()
                    check(stripped.renameTo(jar) || run { jar.delete(); stripped.renameTo(jar) }) { "could not replace $jar" }
                    println("Skia: removed the other chip's library from ${jar.name} (${saved / 1_048_576} MB)")
                }
            }
        }
        tasks.matching { it.name == "vlcPluginCache$type" }.configureEach { dependsOn(strip) }
        val cds = tasks.register<JavaExec>("appCds$type") {
            description = "Adds an AppCDS archive to the ${type.ifEmpty { "debug" }} app, so it starts faster."
            classpath = project.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
            mainClass.set("com.ks.jaiplayer.desktop.AppCdsKt")
            args(appDir.get().asFile.path)
            dependsOn("create${type}Distributable", strip)
        }
        // After the plugin cache (it re-seals the app too), before any installer.
        tasks.matching { it.name == "vlcPluginCache$type" }.configureEach { cds.get().dependsOn(this) }
        tasks.matching { it.name.startsWith("package$type") && it.name != "package${type}UberJarForCurrentOS" && (type != "" || !it.name.startsWith("packageRelease")) }
            .configureEach { dependsOn(cds) }
    }
}
