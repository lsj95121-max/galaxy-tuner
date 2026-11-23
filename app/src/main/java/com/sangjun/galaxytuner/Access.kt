package com.sangjun.galaxytuner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the app is allowed to do right now.
 *
 * `WRITE_SECURE_SETTINGS` (granted once through Shizuku) is what lets the app change
 * Settings.Global/Secure/System by itself, with no Shizuku running. Shell-only features
 * (dumpsys, wm, sysfs) still need the Shizuku connection.
 */
object Access {

    private const val SECURE_WRITE = Manifest.permission.WRITE_SECURE_SETTINGS

    /** Observed by the UI. */
    var secureWrite by mutableStateOf(false)
        private set

    private var app: Context? = null

    fun attach(context: Context) {
        app = context.applicationContext
        refresh()
    }

    fun hasSecureWrite(): Boolean = try {
        app?.checkSelfPermission(SECURE_WRITE) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun refresh() {
        secureWrite = hasSecureWrite()
    }

    /**
     * Grants the settings permission from the Shizuku shell (uid 2000), the same way
     * `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` does.
     * The write-settings app-op is allowed as well so Settings.System writes are never
     * blocked on firmwares that ignore WRITE_SECURE_SETTINGS for the system table.
     */
    suspend fun grant(): String {
        val context = app ?: return "앱 정보를 읽지 못했어요."
        if (hasSecureWrite()) {
            refresh()
            return "이미 설정 권한이 있어요."
        }
        if (!ShizukuShell.state.ready) return "Shizuku를 먼저 연결해 주세요."
        val pkg = context.packageName
        val command = buildString {
            append("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; ")
            append("appops set $pkg WRITE_SETTINGS allow >/dev/null 2>&1; ")
            append("appops set $pkg android:write_settings allow >/dev/null 2>&1")
        }
        val result = ShizukuShell.exec(command)
        refresh()
        return when {
            secureWrite -> "설정 권한을 받았어요."
            result is ShellResult.Ok && result.output.contains("SecurityException") ->
                "설정 권한을 받지 못했어요."
            else -> "설정 권한을 받지 못했어요."
        }
    }
}
