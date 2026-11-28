package com.ks.jaiplayer.desktop

import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.PLUGIN_API_VERSION
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.ui.PluginInfo
import com.ks.jaiplayer.ui.PluginManager
import com.ks.jaiplayer.ui.PluginProblem
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.jar.JarFile

/**
 * The desktop's plugins: the built-in ones and any `.jar` the user copied into [folder]
 * (specs/writing-plugins.md). Each switch is kept in the settings; built-in plugins start on,
 * added ones start off. A jar is identified from its manifest (`JaiPlayer-Plugin-Id`,
 * `-Name`, `-Api`) without running any of its code; only plugins that are on, built for this
 * [PLUGIN_API_VERSION], are loaded (through `ServiceLoader`, one class loader per jar).
 */
class DesktopPlugins(
    private val settings: SettingsStore,
    private val builtIn: List<JaiPlugin>,
    val folder: File = defaultFolder(),
) : PluginManager {

    private class Added(val file: File, val id: String?, val name: String?, val api: Int?)

    private val added: List<Added> = runCatching {
        folder.mkdirs()
        folder.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }.orEmpty().sortedBy { it.name }.map(::read)
    }.getOrDefault(emptyList())

    override val plugins: List<PluginInfo> by lazy {
        val builtInIds = builtIn.map { it.id }.toSet()
        builtIn.map { PluginInfo(it.id, it.name, null, null) } + added.map { a ->
            val problem = when {
                a.id == null || a.id in builtInIds -> PluginProblem.Unreadable
                a.api != PLUGIN_API_VERSION -> PluginProblem.WrongVersion
                else -> null
            }
            PluginInfo(a.id ?: a.file.name, a.name ?: a.file.nameWithoutExtension, a.file.name, problem)
        }
    }

    override fun isOn(id: String): Boolean {
        val default = builtIn.any { it.id == id }
        return settings.getString(KEY_PREFIX + id)?.toBooleanStrictOrNull() ?: default
    }

    override fun set(id: String, on: Boolean) = settings.putString(KEY_PREFIX + id, on.toString())

    override fun openFolder() {
        runCatching { folder.mkdirs(); java.awt.Desktop.getDesktop().open(folder) }
    }

    /** The plugins to install now: built-in ones that are on, then added ones that are on and loadable. */
    fun active(): List<JaiPlugin> {
        val loadable = plugins.filter { it.file != null && it.problem == null && isOn(it.id) }.map { it.id }.toSet()
        val loaded = added.filter { it.id in loadable }.mapNotNull { a ->
            runCatching {
                val loader = URLClassLoader(arrayOf(a.file.toURI().toURL()), JaiPlugin::class.java.classLoader)
                ServiceLoader.load(JaiPlugin::class.java, loader).firstOrNull { it.id == a.id && it.apiVersion == PLUGIN_API_VERSION }
            }.onFailure { System.err.println("KS JaiPlayer: plugin ${a.file.name} could not be loaded: $it") }.getOrNull()
        }
        return builtIn.filter { isOn(it.id) } + loaded
    }

    private fun read(file: File): Added = runCatching {
        JarFile(file).use { jar ->
            val attrs = jar.manifest?.mainAttributes
            Added(file, attrs?.getValue("JaiPlayer-Plugin-Id")?.trim()?.takeIf { ID.matches(it) },
                attrs?.getValue("JaiPlayer-Plugin-Name")?.trim()?.take(80), attrs?.getValue("JaiPlayer-Plugin-Api")?.trim()?.toIntOrNull())
        }
    }.getOrElse { Added(file, null, null, null) }

    companion object {
        private const val KEY_PREFIX = "plugins.on."
        private val ID = Regex("[a-z0-9][a-z0-9.-]{1,63}")

        /** macOS `~/Library/Application Support/KS JaiPlayer[ DEV]/plugins`, Windows `%APPDATA%\\KS JaiPlayer[ DEV]\\plugins`. */
        fun defaultFolder(): File {
            return File(DesktopVariant.supportDir(), "plugins")
        }
    }
}
