package com.sangjun.galaxytuner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One GitHub release, reduced to what the update card needs. */
data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val assetName: String?,
    val assetUrl: String?,
    val assetSize: Long,
    val htmlUrl: String
)

/**
 * Checks the app's own GitHub releases and installs the matching APK.
 *
 * The asset is chosen by the flavor that is running: the sdk22 build picks `...-sdk22-...apk` and
 * the sdk34 build picks `...-sdk34-...apk`, so the two side-by-side apps never overwrite each
 * other with the wrong build.
 *
 * Installation:
 *  - Shizuku or wireless debugging connected: the APK is streamed into
 *    `pm install -r --bypass-low-target-sdk-block -S <bytes>` (stdin), which installs silently and
 *    avoids the SELinux problem of uid 2000 reading the app's private cache.
 *  - Otherwise on sdk34: the normal Android installer is launched through a FileProvider.
 *  - Otherwise on sdk22: the user is told that sdk22 needs Shizuku/ADB on Android 14+ (the system
 *    installer refuses a targetSdk 22 package there).
 */
object Update {

    private const val REPO = "lsj95121-max/galaxy-tuner"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val USER_AGENT = "GalaxyTuner-${BuildConfig.VERSION_NAME}"

    /** The asset suffix this build looks for in a release. */
    private val assetSuffix: String
        get() = if (BuildConfig.COMPAT_TARGET_SDK <= 22) "-sdk22" else "-sdk34"

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Reads the newest release. Never throws; the message explains a failure. */
    suspend fun latest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val body = request(API_LATEST)
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val version = tag.removePrefix("v")
            if (version.isBlank()) throw IllegalStateException("릴리즈 태그를 읽지 못했어요.")
            val assets = json.optJSONArray("assets")
            var assetName: String? = null
            var assetUrl: String? = null
            var assetSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.contains(assetSuffix, ignoreCase = true) && name.endsWith(".apk")) {
                        assetName = name
                        assetUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        assetSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            ReleaseInfo(
                tag = tag,
                version = version,
                notes = json.optString("body", ""),
                assetName = assetName,
                assetUrl = assetUrl,
                assetSize = assetSize,
                htmlUrl = json.optString("html_url", "https://github.com/$REPO/releases")
            )
        }
    }

    /** True when [latest] is a newer version than the running one. */
    fun isNewer(latest: String, current: String = currentVersion): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parseVersion(text: String): List<Int> =
        text.removePrefix("v")
            .substringBefore('-')
            .split('.')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /** Downloads the release APK into the app cache. */
    suspend fun download(
        context: Context,
        release: ReleaseInfo,
        onProgress: ((Long, Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val url = release.assetUrl ?: throw IllegalStateException("내려받을 APK가 릴리즈에 없어요.")
        val name = release.assetName ?: "GalaxyTuner-${release.version}.apk"
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, name)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("다운로드 실패 (HTTP $code)")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.assetSize
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress?.invoke(done, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    /** Installs a downloaded APK; returns a message for the snackbar. */
    suspend fun install(
        context: Context,
        apk: File,
        onProgress: ((Long) -> Unit)? = null
    ): String {
        if (!apk.exists() || apk.length() <= 0) return "다운로드한 APK를 찾지 못했어요."
        if (Shell.ready) {
            val result = installWithShell(apk, onProgress)
            return result
        }
        if (BuildConfig.COMPAT_TARGET_SDK <= 22) {
            return "sdk22판은 Android 14 이상에서 시스템 설치 화면으로 설치할 수 없어요. " +
                "Shizuku나 무선 디버깅을 연결한 뒤 다시 눌러 주세요."
        }
        return installWithSystemInstaller(context, apk)
    }

    private suspend fun installWithShell(apk: File, onProgress: ((Long) -> Unit)? = null): String {
        val bypass = if (Build.VERSION.SDK_INT >= 34 && BuildConfig.COMPAT_TARGET_SDK <= 22) {
            "--bypass-low-target-sdk-block "
        } else {
            ""
        }
        val command = "pm install -r ${bypass}-S ${apk.length()}"
        val result = Shell.execStream(command, apk, onProgress)
        val output = when (result) {
            is ShellResult.Ok -> result.output
            is ShellResult.Err -> return result.message
        }
        return when {
            output.contains("Success") -> "업데이트를 설치했어요. 잠시 뒤 앱이 새 버전으로 다시 열려요."
            output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") ->
                "지금보다 낮은 버전이라 설치하지 않았어요."
            output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ->
                "서명이 다른 앱이 이미 설치되어 있어요. 기존 앱을 지운 뒤 설치해 주세요."
            output.contains("Permission denial") || output.contains("SecurityException") ->
                "설치 권한이 없어 실패했어요. Shizuku 권한을 확인해 주세요."
            output.isBlank() -> "설치 명령을 보냈지만 결과를 읽지 못했어요. 잠시 뒤 버전을 확인해 주세요."
            else -> "설치 실패: ${output.trim().take(160)}"
        }
    }

    private fun installWithSystemInstaller(context: Context, apk: File): String {
        if (!Access.canInstallPackages()) {
            return Access.openInstallUnknownScreen()
        }
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "설치 화면을 열었어요. 설치를 눌러 주세요."
        } catch (t: Throwable) {
            "설치 화면을 열지 못했어요: ${t.javaClass.simpleName}"
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1fMB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> "${bytes / 1024L}KB"
        else -> "${bytes}B"
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(
                    when (code) {
                        403, 429 -> "GitHub 요청 한도를 넘었어요. 잠시 뒤 다시 시도해 주세요."
                        404 -> "릴리즈를 찾지 못했어요."
                        else -> "GitHub 응답 오류 (HTTP $code)"
                    }
                )
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
