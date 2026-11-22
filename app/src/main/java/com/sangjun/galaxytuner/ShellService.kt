package com.sangjun.galaxytuner

import java.util.concurrent.TimeUnit

/**
 * Shizuku user service.
 *
 * Started by Shizuku (uid 2000, shell) through Shizuku.bindUserService, so every command
 * executed here runs with shell privileges - enough for `dumpsys`, `settings` and reading
 * /sys/class/power_supply nodes, without root.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        System.exit(0)
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroy()
                output + "\n[timeout]"
            } else {
                output
            }
        } catch (t: Throwable) {
            "ERROR: ${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
