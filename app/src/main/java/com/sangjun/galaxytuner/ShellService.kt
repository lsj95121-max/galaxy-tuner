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

    private var stream: Process? = null

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

    /**
     * Starts a command that reads its input from us.
     *
     * The APK lives in the app's private storage, which uid 2000 cannot open (SELinux), so the
     * bytes are pushed through the process' stdin instead of being read from a path:
     * `pm install -r --bypass-low-target-sdk-block -S <bytes>` takes the package on stdin.
     */
    override fun startStream(command: String) {
        stream?.destroy()
        stream = try {
            ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (t: Throwable) {
            null
        }
        if (stream == null) {
            throw IllegalStateException("START_ERROR")
        }
    }

    override fun writeStream(data: ByteArray) {
        val process = stream ?: throw IllegalStateException("NOT_STARTED")
        process.outputStream.write(data)
        process.outputStream.flush()
    }

    override fun finishStream(): String {
        val process = stream ?: return "ERROR: NOT_STARTED"
        stream = null
        return try {
            process.outputStream.close()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(180, TimeUnit.SECONDS)
            if (finished) output else "ERROR: timeout\n$output"
        } catch (t: Throwable) {
            "ERROR: ${t.javaClass.simpleName}: ${t.message}"
        } finally {
            try {
                process.destroy()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }
}
