package com.sangjun.galaxytuner

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Snapshot of the Shizuku connection, observed by the UI. */
data class ShizukuState(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val serviceConnected: Boolean = false,
    val shizukuVersion: Int = -1
) {
    val ready: Boolean get() = binderAlive && permissionGranted && serviceConnected
}

sealed class ShellResult {
    data class Ok(val output: String) : ShellResult()
    data class Err(val message: String) : ShellResult()
}

/**
 * Talks to Shizuku: checks the binder / permission state and runs shell commands through a
 * user service bound with Shizuku.bindUserService (uid 2000).
 */
object ShizukuShell {

    const val PERMISSION_REQUEST_CODE = 4001

    var state by mutableStateOf(ShizukuState())
        private set

    private var appContext: Context? = null
    private var service: IShellService? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var bindRequested = false
    private var listenersRegistered = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IShellService.Stub.asInterface(binder)
            } else {
                null
            }
            publish()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bindRequested = false
            publish()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        bindRequested = false
        service = null
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        bindRequested = false
        service = null
        refresh()
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindRequested = false
                bind()
            }
            refresh()
        }

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (!listenersRegistered) {
            try {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionListener)
                listenersRegistered = true
            } catch (t: Throwable) {
                // Shizuku not installed / older version - state simply stays "not running".
            }
        }
        refresh()
    }

    fun refresh() {
        val alive = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
        val version = if (alive) {
            try {
                Shizuku.getVersion()
            } catch (t: Throwable) {
                -1
            }
        } else {
            -1
        }
        val granted = if (alive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (t: Throwable) {
                false
            }
        } else {
            false
        }
        state = ShizukuState(
            binderAlive = alive,
            permissionGranted = granted,
            serviceConnected = service != null,
            shizukuVersion = version
        )
        if (granted) {
            bind()
        } else {
            if (service != null) {
                unbind()
            }
            bindRequested = false
        }
    }

    fun requestPermission() {
        if (!state.binderAlive) return
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
            // Permission dialog cannot be shown; nothing else to do here.
        }
    }

    fun shouldShowRationale(): Boolean = try {
        state.binderAlive && Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    private fun bind() {
        if (bindRequested) return
        val context = appContext ?: return
        if (!state.permissionGranted) return
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
            serviceArgs = args
            Shizuku.bindUserService(args, connection)
            bindRequested = true
        } catch (t: Throwable) {
            bindRequested = false
        }
        publish()
    }

    private fun unbind() {
        val args = serviceArgs
        try {
            if (args != null) {
                Shizuku.unbindUserService(args, connection, true)
            }
        } catch (t: Throwable) {
            // ignore
        }
        service = null
    }

    private fun publish() {
        state = state.copy(serviceConnected = service != null)
    }

    private suspend fun awaitService(timeoutMs: Long = 10_000L): IShellService? {
        var waited = 0L
        while (waited < timeoutMs) {
            service?.let { return it }
            if (!state.binderAlive || !state.permissionGranted) return null
            bind()
            delay(100L)
            waited += 100L
            publish()
        }
        return service
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val svc = awaitService()
            ?: return@withContext ShellResult.Err(
                "Shizuku 셸 서비스에 연결되지 않았습니다. Shizuku 실행 상태와 권한을 확인한 뒤 새로고침 해 주세요."
            )
        try {
            ShellResult.Ok(svc.exec(command) ?: "")
        } catch (t: Throwable) {
            service = null
            bindRequested = false
            publish()
            ShellResult.Err("명령 실행 실패: ${t.javaClass.simpleName} ${t.message ?: ""}".trim())
        }
    }
}
