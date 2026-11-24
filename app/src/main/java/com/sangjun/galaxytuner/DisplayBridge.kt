package com.sangjun.galaxytuner

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Process
import android.util.DisplayMetrics
import android.view.Display

/**
 * The hidden `IWindowManager` forced-size / forced-density calls, reached without a shell.
 *
 * `setForcedDisplaySize`, `setForcedDisplayDensityForUser` and their clear counterparts are
 * protected by `WRITE_SECURE_SETTINGS`, which the app already holds after the one-time Shizuku
 * setup, so resolution and smallest width work while Shizuku is closed. Each call returns `null`
 * when it went through and a short reason when the firmware refused it (SecurityException, a
 * missing method, a hidden-API block); the caller then runs the same change through `wm`.
 */
object DisplayBridge {

    private const val DISPLAY_ID = 0

    private fun userId(): Int = Process.myUid() / 100_000

    /**
     * The window manager service. `WindowManagerGlobal.getWindowManagerService()` is the handle
     * Settings itself uses; ServiceManager is the second route for builds that hide it.
     */
    private fun service(): Any? {
        try {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val method = global.getDeclaredMethod("getWindowManagerService")
            method.isAccessible = true
            method.invoke(null)?.let { return it }
        } catch (t: Throwable) {
            // Not reachable on this build; try the raw binder below.
        }
        return try {
            val manager = Class.forName("android.os.ServiceManager")
            val binder = manager.getMethod("getService", String::class.java)
                .invoke(null, "window") as? android.os.IBinder
                ?: return null
            Class.forName("android.view.IWindowManager\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, binder)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Calls one overload whose parameters are all ints.
     * Returns null on success, otherwise a short reason.
     */
    private fun call(name: String, vararg args: Int): String? {
        val target = service() ?: return "윈도우 서비스를 열지 못했어요."
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.all { it == Int::class.javaPrimitiveType }
        } ?: return "$name 메서드를 찾지 못했어요."
        return try {
            method.invoke(target, *args.toTypedArray())
            null
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException ?: e
            "${cause.javaClass.simpleName}${cause.message?.let { ": $it" } ?: ""}"
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}${t.message?.let { ": $it" } ?: ""}"
        }
    }

    /** Forces the logical size (the panel itself stays untouched). */
    fun setSize(width: Int, height: Int): String? {
        val withDisplay = call("setForcedDisplaySize", DISPLAY_ID, width, height)
        if (withDisplay == null) return null
        // Older builds only carry the two-argument form.
        return call("setForcedDisplaySize", width, height)
    }

    fun clearSize(): String? {
        val withDisplay = call("clearForcedDisplaySize", DISPLAY_ID)
        if (withDisplay == null) return null
        return call("clearForcedDisplaySize")
    }

    fun setDensity(dpi: Int): String? {
        val forUser = call("setForcedDisplayDensityForUser", DISPLAY_ID, dpi, userId())
        if (forUser == null) return null
        val withDisplay = call("setForcedDisplayDensity", DISPLAY_ID, dpi)
        if (withDisplay == null) return null
        return call("setForcedDisplayDensity", dpi)
    }

    fun clearDensity(): String? {
        val forUser = call("clearForcedDisplayDensityForUser", DISPLAY_ID, userId())
        if (forUser == null) return null
        val withDisplay = call("clearForcedDisplayDensity", DISPLAY_ID)
        if (withDisplay == null) return null
        return call("clearForcedDisplayDensity")
    }

    /**
     * The display as DisplayManager reports it: the panel's own mode gives the physical size,
     * the real metrics give what is in use now (an override included), and Resources gives the
     * density the UI currently lays out with.
     *
     * `nativeDensity` is the panel density remembered from a shell read; without it the density
     * override cannot be told apart from the default, so it is left unset.
     */
    fun read(context: Context?, nativeDensity: Int?): DisplayInfo? {
        val ctx = context ?: return null
        return try {
            val manager = ctx.getSystemService(DisplayManager::class.java) ?: return null
            val display = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
            val mode = display.mode
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)

            val physicalWidth = mode?.physicalWidth ?: 0
            val physicalHeight = mode?.physicalHeight ?: 0
            if (physicalWidth <= 0 || physicalHeight <= 0) return null

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            if (width <= 0 || height <= 0 || density <= 0) return null

            // A rotated screen swaps width and height; the panel is still the same size.
            val matchesPanel = (width == physicalWidth && height == physicalHeight) ||
                (width == physicalHeight && height == physicalWidth)

            DisplayInfo(
                widthPx = width,
                heightPx = height,
                densityDpi = density,
                physicalWidthPx = physicalWidth,
                physicalHeightPx = physicalHeight,
                physicalDensityDpi = nativeDensity,
                overrideWidthPx = if (matchesPanel) null else width,
                overrideHeightPx = if (matchesPanel) null else height,
                overrideDensityDpi = if (nativeDensity != null && density != nativeDensity) density else null
            )
        } catch (t: Throwable) {
            null
        }
    }
}
