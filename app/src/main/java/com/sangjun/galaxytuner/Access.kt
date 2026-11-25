package com.sangjun.galaxytuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the app is allowed to do right now.
 *
 * - `WRITE_SECURE_SETTINGS` (granted once through Shizuku or ADB) lets the app write
 *   Settings.Global/Secure/System by itself, with no shell running.
 * - `WRITE_SETTINGS` (the normal app-op) is enough for the Settings.System table. The sdk22 build
 *   targets API 22, so the phone grants it at install time and the refresh-rate keys
 *   (`min_refresh_rate`, `peak_refresh_rate`) can be written with no Shizuku and no ADB at all.
 * - Shell-only features (dumpsys, wm, sysfs) still need Shizuku or wireless debugging.
 */
object Access {

    private const val SECURE_WRITE = Manifest.permission.WRITE_SECURE_SETTINGS

    /** Observed by the UI. */
    var secureWrite by mutableStateOf(false)
        private set

    /** Settings.System writes allowed (WRITE_SETTINGS app-op). */
    var systemWrite by mutableStateOf(false)
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

    private fun hasSystemWrite(): Boolean {
        val context = app ?: return false
        return try {
            // On the sdk22 build every Settings.System write is allowed at install time.
            Settings.System.canWrite(context) || BuildConfig.COMPAT_TARGET_SDK <= 22
        } catch (t: Throwable) {
            BuildConfig.COMPAT_TARGET_SDK <= 22
        }
    }

    fun refresh() {
        secureWrite = hasSecureWrite()
        systemWrite = hasSystemWrite()
    }

    /** Any route that can change a settings value. */
    val canWriteSettings: Boolean
        get() = secureWrite || systemWrite || Shell.ready

    /**
     * Grants the settings permission from a shell (Shizuku or wireless debugging), the same way
     * `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` does.
     * The write-settings app-op is allowed as well so Settings.System writes are never
     * blocked on firmwares that ignore WRITE_SECURE_SETTINGS for the system table.
     */
    suspend fun grant(): String {
        val context = app ?: return "앱 정보를 읽지 못했어요."
        refresh()
        if (secureWrite) return "이미 설정 권한이 있어요."
        if (!Shell.ready) return Shell.NO_SHELL
        val pkg = context.packageName
        val command = buildString {
            append("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; ")
            append("appops set $pkg WRITE_SETTINGS allow >/dev/null 2>&1; ")
            append("appops set $pkg android:write_settings allow >/dev/null 2>&1")
        }
        val result = Shell.exec(command)
        refresh()
        return when {
            secureWrite -> "설정 권한을 받았어요."
            result is ShellResult.Ok && result.output.contains("SecurityException") ->
                "설정 권한을 받지 못했어요."
            else -> "설정 권한을 받지 못했어요."
        }
    }

    /** Opens the screen where the user allows Settings.System writes (sdk34 build). */
    fun openWriteSettingsScreen(): String {
        val context = app ?: return "앱 정보를 읽지 못했어요."
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "설정 수정 권한 화면을 열었어요. 이 앱을 허용해 주세요."
        } catch (t: Throwable) {
            "설정 화면을 열지 못했어요. 설정 > 앱 > 특수 접근 권한에서 직접 허용해 주세요."
        }
    }

    /** Opens the "install unknown apps" screen for this app (used by the update card). */
    fun openInstallUnknownScreen(): String {
        val context = app ?: return "앱 정보를 읽지 못했어요."
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "알 수 없는 앱 설치 허용 화면을 열었어요."
        } catch (t: Throwable) {
            "설정 화면을 열지 못했어요. 설정 > 앱 > 특수 접근 권한에서 직접 허용해 주세요."
        }
    }

    fun canInstallPackages(): Boolean = try {
        app?.packageManager?.canRequestPackageInstalls() == true
    } catch (t: Throwable) {
        true
    }
}
