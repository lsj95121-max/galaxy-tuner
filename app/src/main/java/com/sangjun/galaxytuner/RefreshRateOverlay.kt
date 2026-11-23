package com.sangjun.galaxytuner

import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The developer-options "show refresh rate" indicator.
 *
 * Android does not keep this in a Settings key: the toggle is a SurfaceFlinger transaction
 * (code 1034, the same one Settings' ShowRefreshRatePreferenceController uses). Writing to
 * SurfaceFlinger borrows no state from the app, so it can be done from the app process, and
 * the current state can be read back with the "query" argument.
 */
object RefreshRateOverlay {

    private const val SERVICE = "SurfaceFlinger"
    private const val INTERFACE = "android.ui.ISurfaceComposer"
    private const val CODE = 1034
    private const val QUERY = 2
    private const val ON = 1
    private const val OFF = 0

    private fun surfaceFlinger(): IBinder? = try {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        getService.invoke(null, SERVICE) as? IBinder
    } catch (t: Throwable) {
        null
    }

    /** null when this device does not answer (unsupported or blocked). */
    fun isEnabled(): Boolean? = queryNow()

    private fun queryNow(): Boolean? {
        val binder = surfaceFlinger() ?: return null
        return try {
            if (!binder.pingBinder()) {
                null
            } else {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(INTERFACE)
                    data.writeInt(QUERY)
                    if (binder.transact(CODE, data, reply, 0)) reply.readBoolean() else null
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun write(binder: IBinder, on: Boolean): Boolean = try {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE)
            data.writeInt(if (on) ON else OFF)
            binder.transact(CODE, data, null, 0)
            true
        } finally {
            data.recycle()
        }
    } catch (t: Throwable) {
        false
    }

    suspend fun set(on: Boolean): String = withContext(Dispatchers.IO) {
        val binder = surfaceFlinger()
        if (binder != null && write(binder, on)) {
            val state = isEnabled()
            return@withContext when {
                state == on -> if (on) "화면 재생률 표시 켜짐" else "화면 재생률 표시 꺼짐"
                state == null -> "화면 재생률 표시를 보냈어요. 화면에서 확인해 주세요."
                else -> "화면 재생률 표시가 그대로예요. 기기에서 지원하지 않을 수 있어요."
            }
        }
        // Same transaction through the shell when the app process may not call it.
        if (ShizukuShell.state.ready) {
            val command = "service call $SERVICE $CODE i32 ${if (on) ON else OFF}"
            val out = (ShizukuShell.exec(command) as? ShellResult.Ok)?.output.orEmpty()
            if (out.contains("Result: Parcel")) {
                return@withContext if (on) "화면 재생률 표시 켜짐" else "화면 재생률 표시 꺼짐"
            }
        }
        "이 기기에서는 화면 재생률 표시를 바꿀 수 없어요."
    }
}
