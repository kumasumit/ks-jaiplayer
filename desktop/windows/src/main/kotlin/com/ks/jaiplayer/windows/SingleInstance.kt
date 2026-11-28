package com.ks.jaiplayer.windows

import com.ks.jaiplayer.desktop.ExternalOpen
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock

/**
 * One KS JaiPlayer at a time on Windows: a second launch (double click in Explorer, "Open with")
 * hands its files to the running app over a socket on this computer only, and exits; the running
 * window opens them and comes to the front. The first instance holds a lock file in the user's
 * app-data folder and writes the socket's port next to it.
 */
object SingleInstance {
    private val dir = com.ks.jaiplayer.desktop.DesktopVariant.cacheDir().apply { mkdirs() }
    private var lock: FileLock? = null

    /** True when this process should run the app; false after handing [files] to the running one. */
    fun claim(files: List<File>): Boolean {
        val channel = runCatching { RandomAccessFile(File(dir, "instance.lock"), "rw").channel }.getOrNull() ?: return true
        lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock != null) {
            serve()
            return true
        }
        // Another instance runs: send it the files (nothing to send still brings it forward).
        val sent = runCatching {
            val port = File(dir, "instance.port").readText().trim().toInt()
            Socket(InetAddress.getLoopbackAddress(), port).use { s ->
                s.soTimeout = 3_000
                s.getOutputStream().bufferedWriter().use { w ->
                    w.write(MAGIC); w.newLine()
                    files.forEach { w.write(it.absolutePath); w.newLine() }
                }
            }
        }.isSuccess
        return !sent // If the running instance cannot be reached, run normally.
    }

    private fun serve() {
        val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { File(dir, "instance.port").writeText(server.localPort.toString()) }
        Thread({
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use { s ->
                        s.soTimeout = 3_000
                        val lines = s.getInputStream().bufferedReader().readLines()
                        if (lines.firstOrNull() == MAGIC) {
                            // Only existing files: the socket accepts nothing else.
                            ExternalOpen.open(lines.drop(1).take(MAX_FILES).map(::File).filter { it.exists() })
                        }
                    }
                }
            }
        }, "single-instance").apply { isDaemon = true }.start()
    }

    private const val MAGIC = "ks-jaiplayer-open-1"
    private const val MAX_FILES = 500
}
