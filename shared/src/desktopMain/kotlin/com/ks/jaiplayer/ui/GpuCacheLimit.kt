package com.ks.jaiplayer.ui

import org.jetbrains.skia.DirectContext
import java.awt.Component
import java.awt.Container
import java.awt.Window

/**
 * Lowers Skia's GPU resource cache for the window that shows video. Each frame's three planes are
 * uploaded as new textures, and Skia keeps old ones until its cache budget (256 MB by default) is
 * full: at 4K that is about 360 MB of graphics memory holding frames that will never be drawn
 * again. A small budget keeps only what is in use.
 *
 * Skiko does not expose the window's context, so it is reached by reflection. If anything along
 * the way is missing (another Skiko version), nothing changes and playback is exactly as before.
 */
internal object GpuCacheLimit {
    /** Room for the frame on screen and the next one at 4K (two × 12.4 MB), plus the interface. */
    const val BYTES = 64L * 1024 * 1024

    private var applied: DirectContext? = null
    private var gaveUp = false
    private var frames = 0
    private var misses = 0
    private const val MAX_MISSES = 120

    /**
     * Called while a frame is drawn, so the window and its context exist. Once applied it only
     * checks again every 300 frames (in case Skiko made a new context, after a display change).
     */
    fun apply() {
        if (gaveUp) return
        if (applied != null && frames++ % 300 != 0) return
        val window = Window.getWindows().firstOrNull { it.isShowing && it.isFocusableWindow } ?: return
        val context = runCatching { contextOf(window) }.getOrNull()
        if (context == null) {
            // Not there yet (the first frames), or not reachable in this Skiko: stop trying after a while.
            if (applied == null && ++misses > MAX_MISSES) gaveUp = true
            return
        }
        if (context === applied) return
        runCatching { context.resourceCacheLimit = BYTES }
            .onSuccess { applied = context }
            .onFailure { gaveUp = true }
    }

    private fun contextOf(window: Window): DirectContext? {
        val layer = find(window) { it is org.jetbrains.skiko.SkiaLayer } ?: return null
        val redrawer = org.jetbrains.skiko.SkiaLayer::class.java.getMethod("getRedrawer\$skiko").invoke(layer) ?: return null
        val handler = fieldValue(redrawer, "contextHandler") ?: return null
        var type: Class<*>? = handler.javaClass
        while (type != null) {
            val getter = type.declaredMethods.firstOrNull { it.name == "getContext" && it.parameterCount == 0 }
            if (getter != null) {
                getter.isAccessible = true
                return getter.invoke(handler) as? DirectContext
            }
            type = type.superclass
        }
        return null
    }

    private fun fieldValue(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            type.declaredFields.firstOrNull { it.name == name }?.let { it.isAccessible = true; return it.get(owner) }
            type = type.superclass
        }
        return null
    }

    private fun find(root: Component, match: (Component) -> Boolean): Component? {
        if (match(root)) return root
        if (root is Container) for (child in root.components) find(child, match)?.let { return it }
        return null
    }
}
