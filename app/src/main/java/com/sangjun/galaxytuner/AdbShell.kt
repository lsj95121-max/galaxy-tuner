package com.sangjun.galaxytuner

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.KeyPair as JavaKeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** What the built-in wireless debugging (ADB) connection looks like right now. */
data class AdbState(
    val connected: Boolean = false,
    val host: String? = null,
    val port: Int = -1
)

/**
 * Shell access through the phone's own wireless debugging daemon (adbd), without Shizuku.
 *
 * Flow, the same one Android Studio uses:
 *  1. `pair(host, port, code)` - SPAKE2 over TLS with the 6-digit code from 무선 디버깅 > 페어링 코드로
 *     기기 페어링. After this the phone has our RSA key in /data/misc/adb/adb_keys.
 *  2. `connect(host, port)` - TLS handshake using that key to the (different) connect port, then
 *     open `shell:` streams and run commands as uid 2000.
 *
 * The key pair and its certificate are generated once and kept in app storage, because the phone
 * remembers the public key and pairing would be needed again if it changed.
 */
object AdbShell {

    var state by mutableStateOf(AdbState())
        private set

    private var manager: AdbConnectionManager? = null
    private var app: Context? = null

    /**
     * Remembers the app context. The connection manager (and its RSA key) is created on first use,
     * off the main thread, so opening the app never waits for key generation.
     */
    fun attach(context: Context) {
        AppCtx.attach(context)
        app = context.applicationContext
        val existing = manager
        if (existing != null && !existing.isConnected) {
            state = AdbState(false)
        }
    }

    private fun ensureManager(): AdbConnectionManager? {
        val context = app ?: return null
        manager?.let { return it }
        return runCatching { AdbConnectionManager.getInstance(context) }
            .onSuccess { manager = it }
            .getOrNull()
    }

    /** The Wi-Fi address of this phone, the host adbd is reached on. */
    fun localAddress(): String {
        val context = app ?: return "127.0.0.1"
        return try {
            AndroidUtils.getHostIpAddress(context)
        } catch (t: Throwable) {
            "127.0.0.1"
        }
    }

    /** The pairing port advertised over mDNS while the pairing dialog is open; -1 when not found. */
    suspend fun findPairingPort(timeoutMs: Long = 8_000L): Int = withContext(Dispatchers.IO) {
        val context = app ?: return@withContext -1
        val port = AtomicInteger(-1)
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, found ->
            if (found > 0) port.set(found)
            latch.countDown()
        }
        try {
            mdns.start()
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            // fall through with -1
        } finally {
            runCatching { mdns.stop() }
        }
        port.get()
    }

    /** Pairs with the pairing port and the 6-digit code shown in 무선 디버깅. */
    suspend fun pair(host: String, port: Int, code: String): String = withContext(Dispatchers.IO) {
        val instance = ensureManager() ?: return@withContext "ADB를 준비하지 못했어요. 앱을 다시 열어 주세요."
        if (port !in 1..65535) return@withContext "페어링 포트를 확인해 주세요."
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return@withContext "6자리 페어링 코드를 입력해 주세요."
        }
        try {
            instance.pair(host, port, code)
            "페어링 성공 · 이제 연결을 눌러 주세요."
        } catch (t: Throwable) {
            "페어링 실패: ${describe(t)}"
        }
    }

    /**
     * Connects to the connect port. The pairing port and the connect port are different, so when
     * no port is given the port is discovered over mDNS first and 5555 is the last resort.
     */
    suspend fun connect(host: String, port: Int = -1): String = withContext(Dispatchers.IO) {
        val instance = ensureManager() ?: return@withContext "ADB를 준비하지 못했어요. 앱을 다시 열어 주세요."
        val context = app ?: return@withContext "앱 정보를 읽지 못했어요."
        val target = if (port in 1..65535) host to port else null
        try {
            val ok = if (target != null) {
                instance.connect(target.first, target.second)
            } else {
                var connected = false
                try {
                    connected = instance.connectTls(context, 8_000L)
                } catch (e: AdbPairingRequiredException) {
                    return@withContext "이 기기와 아직 페어링되지 않았어요. 6자리 코드로 페어링해 주세요."
                } catch (e: Throwable) {
                    // mDNS may be unavailable; try the classic port below.
                }
                if (!connected) connected = instance.connect(host, 5555)
                connected
            }
            if (ok || instance.isConnected) {
                val usedPort = if (target != null) target.second else -1
                state = AdbState(true, instance.getHostAddress(), usedPort)
                "무선 디버깅에 연결했어요."
            } else {
                "연결하지 못했어요. 무선 디버깅이 켜져 있는지, 같은 Wi-Fi인지 확인해 주세요."
            }
        } catch (e: AdbPairingRequiredException) {
            "이 기기와 아직 페어링되지 않았어요. 6자리 코드로 페어링해 주세요."
        } catch (t: Throwable) {
            "연결 실패: ${describe(t)}"
        }
    }

    fun disconnect() {
        runCatching { manager?.disconnect() }
        state = AdbState(false)
    }

    /** Runs one command through a fresh `shell:` stream and returns its combined output. */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val instance = ensureManager() ?: return@withContext ShellResult.Err("ADB가 연결되지 않았어요.")
        if (!instance.isConnected) return@withContext ShellResult.Err("ADB가 연결되지 않았어요.")
        val stream = try {
            instance.openStream("shell,raw:$command")
        } catch (t: Throwable) {
            if (!instance.isConnected) state = AdbState(false)
            return@withContext ShellResult.Err("ADB 명령을 열지 못했어요: ${describe(t)}")
        }
        try {
            ShellResult.Ok(readAll(stream, 25_000L))
        } catch (t: Throwable) {
            ShellResult.Err("ADB 명령 실행 실패: ${describe(t)}")
        } finally {
            runCatching { stream.close() }
        }
    }

    /**
     * Runs a command whose input comes from [file].
     *
     * The APK is in app-private storage the shell cannot open, so it is written into the shell
     * stream instead: `pm install -r --bypass-low-target-sdk-block -S <bytes>` reads it from stdin.
     */
    suspend fun execStream(
        command: String,
        file: File,
        onProgress: ((Long) -> Unit)? = null
    ): ShellResult = withContext(Dispatchers.IO) {
        val instance = ensureManager() ?: return@withContext ShellResult.Err("ADB가 연결되지 않았어요.")
        if (!instance.isConnected) return@withContext ShellResult.Err("ADB가 연결되지 않았어요.")
        val stream = try {
            instance.openStream("shell,raw:$command")
        } catch (t: Throwable) {
            return@withContext ShellResult.Err("ADB 명령을 열지 못했어요: ${describe(t)}")
        }
        // The output is read on its own thread while the APK is written, because ADB carries both
        // directions on one socket and a full output buffer would block the write.
        val output = ByteArrayOutputStream()
        val reader = Thread {
            runCatching {
                val input = stream.openInputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    synchronized(output) { output.write(buffer, 0, read) }
                }
            }
        }
        try {
            reader.start()
            stream.openOutputStream().use { sink ->
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        sink.flush()
                        sent += read
                        onProgress?.invoke(sent)
                    }
                }
            }
            reader.join(180_000L)
            ShellResult.Ok(synchronized(output) { output.toString("UTF-8") })
        } catch (t: Throwable) {
            ShellResult.Err("ADB 설치 명령 실패: ${describe(t)}")
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun readAll(stream: AdbStream, timeoutMs: Long): String {
        val output = ByteArrayOutputStream()
        val reader = Thread {
            runCatching {
                val input = stream.openInputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    synchronized(output) { output.write(buffer, 0, read) }
                }
            }
        }
        reader.start()
        reader.join(timeoutMs)
        return synchronized(output) { output.toString("UTF-8") }.replace("\r\n", "\n")
    }

    private fun describe(t: Throwable): String {
        val name = t.javaClass.simpleName
        val message = t.message?.takeIf { it.isNotBlank() }
        return if (message == null) name else "$name · $message"
    }

    // -------------------------------------------------------------------------------------------
    // The connection manager: one RSA key and certificate, generated once and kept in app storage.
    // -------------------------------------------------------------------------------------------

    class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

        private val privateKey: PrivateKey
        private val certificate: Certificate
        private val deviceName: String

        init {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(15, TimeUnit.SECONDS)
            deviceName = if (BuildConfig.COMPAT_TARGET_SDK <= 22) "GalaxyTuner-sdk22" else "GalaxyTuner-sdk34"
            val keyFile = File(context.filesDir, "adb_private_key.der")
            val certFile = File(context.filesDir, "adb_certificate.der")
            if (keyFile.exists() && certFile.exists()) {
                val storedKey = runCatching {
                    KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                }.getOrNull()
                val storedCert = runCatching {
                    FileInputStream(certFile).use {
                        CertificateFactory.getInstance("X.509").generateCertificate(it)
                    }
                }.getOrNull()
                if (storedKey != null && storedCert != null) {
                    privateKey = storedKey
                    certificate = storedCert
                } else {
                    val generated = generate(context)
                    privateKey = generated.first
                    certificate = generated.second
                }
            } else {
                val generated = generate(context)
                privateKey = generated.first
                certificate = generated.second
            }
        }

        private fun generate(context: Context): Pair<PrivateKey, Certificate> {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair: JavaKeyPair = generator.generateKeyPair()
            val cert = SelfSignedCert.create(pair, "Galaxy Tuner")
            runCatching {
                val keyFile = File(context.filesDir, "adb_private_key.der")
                val certFile = File(context.filesDir, "adb_certificate.der")
                keyFile.writeBytes(pair.private.encoded)
                certFile.writeBytes(cert.encoded)
            }
            return pair.private to cert
        }

        override fun getPrivateKey(): PrivateKey = privateKey

        override fun getCertificate(): Certificate = certificate

        override fun getDeviceName(): String = deviceName

        companion object {
            @Volatile
            private var instance: AdbConnectionManager? = null

            fun getInstance(context: Context): AdbConnectionManager =
                instance ?: synchronized(this) {
                    instance ?: AdbConnectionManager(context.applicationContext).also { instance = it }
                }
        }
    }
}
