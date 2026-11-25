package com.sangjun.galaxytuner

import java.io.File

/**
 * One way to run shell commands, whichever is connected first.
 *
 * Shell access can come from two places, and the app does not care which:
 * - **Shizuku** (uid 2000 through a bound user service)
 * - **Wireless debugging (ADB)**: the app pairs with its own adbd and opens `shell:` streams
 *
 * Everything that needs `dumpsys`, `settings`, `wm`, `pm` or `sysfs` goes through here, so a
 * feature keeps working as long as *either* connection is up.
 */
object Shell {

    const val NO_SHELL = "Shizuku 또는 무선 디버깅(ADB)을 먼저 연결해 주세요."

    /** True while at least one shell provider is usable. */
    val ready: Boolean get() = ShizukuShell.state.ready || AdbShell.state.connected

    /** "Shizuku" / "무선 디버깅" / null, for status lines. */
    val provider: String?
        get() = when {
            ShizukuShell.state.ready -> "Shizuku"
            AdbShell.state.connected -> "무선 디버깅"
            else -> null
        }

    suspend fun exec(command: String): ShellResult {
        if (ShizukuShell.state.ready) {
            val result = ShizukuShell.exec(command)
            if (result is ShellResult.Ok) return result
            // A dead Shizuku service should not mask a working ADB connection.
            if (!AdbShell.state.connected) return result
        }
        if (AdbShell.state.connected) return AdbShell.exec(command)
        return ShellResult.Err(NO_SHELL)
    }

    /** Runs a command with [file] pushed into its stdin (used for `pm install -S <bytes>`). */
    suspend fun execStream(
        command: String,
        file: File,
        onProgress: ((Long) -> Unit)? = null
    ): ShellResult {
        if (ShizukuShell.state.ready) {
            val result = ShizukuShell.execStream(command, file, onProgress)
            if (result is ShellResult.Ok) return result
            if (!AdbShell.state.connected) return result
        }
        if (AdbShell.state.connected) return AdbShell.execStream(command, file, onProgress)
        return ShellResult.Err(NO_SHELL)
    }
}
