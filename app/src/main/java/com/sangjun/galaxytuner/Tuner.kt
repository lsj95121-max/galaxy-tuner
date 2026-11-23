package com.sangjun.galaxytuner

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Result of a read operation: either a value or a message explaining why there is none. */
data class Loaded<T>(val value: T?, val error: String? = null)

/** Shared application context, attached once from MainActivity. */
object AppCtx {
    private var app: Context? = null

    val context: Context? get() = app

    fun attach(context: Context) {
        app = context.applicationContext
    }
}

data class BatteryInfo(
    val bsoh: Int?,
    val asoc: Int?,
    val firstUseDate: String?,
    val usageRaw: Int?,
    val level: Int?,
    val status: String?,
    val temperatureC: Double?,
    val voltageMv: Int?,
    val health: String?,
    val cycleCountSys: String?,
    val fgAsocSys: String?,
    val chargeFullSys: String?,
    val chargeFullDesignSys: String?
) {
    /** mSavedBatteryUsage is stored in 0.01% steps, so 12345 means 12,345% = 123.45 cycles. */
    val cycles: Double? get() = usageRaw?.let { it / 100.0 }

    /** The headline efficiency: BSOH when One UI reports it, otherwise ASOC. */
    val efficiency: Int? get() = bsoh ?: asoc
}

data class RefreshInfo(
    val mode: String?,
    val min: String?,
    val peak: String?,
    val user: String?
) {
    val modeLabel: String
        get() = when (mode) {
            "0" -> "표준 (60Hz)"
            "1" -> "최적화 (가변)"
            "2" -> "높음 (고정)"
            else -> "기본값"
        }

    val minHz: Int? get() = rateOf(min)
    val peakHz: Int? get() = rateOf(peak)
}

/**
 * Screen size as Android reports it through `wm`: the physical panel values plus an optional
 * override (what the developer-options "screen size"/"smallest width" switches set).
 */
data class DisplayInfo(
    val widthPx: Int?,
    val heightPx: Int?,
    val densityDpi: Int?,
    val physicalWidthPx: Int?,
    val physicalHeightPx: Int?,
    val physicalDensityDpi: Int?,
    val overrideWidthPx: Int?,
    val overrideHeightPx: Int?,
    val overrideDensityDpi: Int?
) {
    /** Smallest width in dp — the number the developer-options entry shows. */
    val smallestWidthDp: Int? get() = dpOf(widthPx, heightPx, densityDpi)

    /** The untouched panel values. */
    val physicalSmallestWidthDp: Int? get() = dpOf(physicalWidthPx, physicalHeightPx, physicalDensityDpi)

    val isSizeOverridden: Boolean get() = overrideWidthPx != null || overrideHeightPx != null
    val isDensityOverridden: Boolean get() = overrideDensityDpi != null
    val isCustomized: Boolean get() = isSizeOverridden || isDensityOverridden

    /** Share of the panel's native resolution that is currently in use (100 / 75 / 50 …). */
    val resolutionPercent: Int?
        get() {
            val w = widthPx ?: return null
            val pw = physicalWidthPx ?: return null
            if (pw <= 0) return null
            val ratio = w * 100.0 / pw
            return listOf(100, 75, 50).minByOrNull { kotlin.math.abs(it - when {
                ratio >= 95 -> 100
                ratio >= 60 -> 75
                else -> 50
            }) }
        }

    val matchesDefaultResolution: Boolean
        get() = overrideWidthPx == null && overrideHeightPx == null

    companion object {
        fun dpOf(width: Int?, height: Int?, dpi: Int?): Int? {
            if (width == null || height == null || dpi == null || dpi <= 0) return null
            return (min(width, height) * 160.0 / dpi).roundToInt()
        }
    }
}

/** One UI power saving mode plus the refresh-rate policy it applies. */
data class PowerSaveInfo(
    val psmOn: Boolean,
    val mode: String?,
    val min: String?,
    val peak: String?,
    val refreshLimitEnabled: String?
) {
    val peakHz: Int? get() = rateOf(peak)

    /** True when the "keep high refresh in power saving" override is written. */
    val overrideApplied: Boolean
        get() = refreshLimitEnabled == "0" && mode != "0" && (peakHz ?: 0) >= 120
}

/**
 * Everything that lives in Settings but does not deserve its own shell round-trip:
 * camera shutter, animation scales, extra dim, battery protection, adaptive battery,
 * master sync and Doze.
 */
data class ExtrasInfo(
    val shutter: String?,
    val windowScale: String?,
    val transitionScale: String?,
    val animatorScale: String?,
    val extraDim: String?,
    val protectMode: String?,
    val protectThreshold: String?,
    val adaptiveBattery: String?,
    val autoSync: Boolean?,
    val dozeEnabled: Boolean?
) {
    val animationScale: String? get() = windowScale ?: transitionScale ?: animatorScale

    val protectionLabel: String
        get() = when (protectMode) {
            "0", null -> "사용 안 함"
            "1" -> "최대 보호 ${protectThreshold?.toIntOrNull() ?: 80}%"
            "2" -> "최대 보호 (시스템 관리)"
            "3" -> "표준 (100% 충전 후 일시정지)"
            else -> "알 수 없는 값"
        }
}

/** App theme override; stored in SharedPreferences. */
enum class AppTheme(val label: String, val storageValue: String) {
    SYSTEM("시스템 기본", "system"),
    LIGHT("라이트 모드", "light"),
    DARK("다크 모드", "dark");

    companion object {
        fun from(value: String?): AppTheme =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object Tuner {

    private const val PREFS = "galaxy_tuner"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_PSM_BACKUP = "psm_refresh_backup"
    private const val ABSENT = "__absent__"

    // Refresh rate (Samsung One UI).
    private const val SETTINGS_MODE = "refresh_rate_mode"          // secure
    private const val SETTINGS_MIN = "min_refresh_rate"            // system
    private const val SETTINGS_PEAK = "peak_refresh_rate"          // system
    private const val SETTINGS_USER = "user_refresh_rate"          // system

    private const val SHUTTER_KEY = "csc_pref_camera_forced_shuttersound_key" // system

    // One UI power saving keys (Samsung specific, all in the global table).
    private const val PSM_REFRESH_LIMIT = "pms_settings_refresh_rate_enabled"
    private const val PSM_REFRESH_LIMIT_COVER = "pms_settings_refresh_rate_cover_enabled"
    private const val PSM_REFRESH_TAG = "psm_refresh_rate_tag"
    private const val PSM_REFRESH_TAG_COVER = "psm_refresh_rate_cover_tag"
    private const val PSM_REFRESH_TAG_STATE = "psm_refresh_rate_tag_power_mode_state"
    private const val PSM_MODE_REFRESH = "sem_power_mode_refresh_rate"
    private const val PSM_MODE_REFRESH_COVER = "sem_power_mode_refresh_rate_cover"

    // Samsung battery protection (One UI 6.1+: global table).
    private const val PROTECT_MODE = "protect_battery"                 // 0 off / 1 최대 / 2 시스템 관리 / 3 표준
    private const val PROTECT_THRESHOLD = "battery_protection_threshold"

    /** refresh_rate_mode values One UI understands (stored in the secure table). */
    private const val MODE_STANDARD_60 = 0
    private const val MODE_ADAPTIVE = 1
    private const val MODE_HIGH = 2

    private var prefs: SharedPreferences? = null

    fun attach(context: Context) {
        AppCtx.attach(context)
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ---------------------------------------------------------------------------------------
    // Preferences (theme)
    // ---------------------------------------------------------------------------------------

    fun theme(): AppTheme = AppTheme.from(prefs?.getString(KEY_THEME, null))

    fun setTheme(theme: AppTheme) {
        prefs?.edit()?.putString(KEY_THEME, theme.storageValue)?.apply()
    }

    // ---------------------------------------------------------------------------------------
    // Settings plumbing: direct contentResolver when WRITE_SECURE_SETTINGS is granted,
    // shell `settings` as the fallback while only Shizuku is available.
    // ---------------------------------------------------------------------------------------

    private data class KeySpec(val ns: String, val key: String, val alias: String)

    private sealed class Op {
        data class Put(val ns: String, val key: String, val value: String) : Op()
        data class Delete(val ns: String, val key: String) : Op()
    }

    private fun resolver(): ContentResolver? = AppCtx.context?.contentResolver

    /** Reads one settings value without any permission; null means unset or not readable. */
    private fun readDirect(ns: String, key: String): String? {
        val cr = resolver() ?: return null
        return try {
            when (ns) {
                "global" -> Settings.Global.getString(cr, key)
                "secure" -> Settings.Secure.getString(cr, key)
                "system" -> Settings.System.getString(cr, key)
                else -> null
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** Writes (or deletes, with a null value) one settings key directly. */
    private fun writeDirect(ns: String, key: String, value: String?): Boolean {
        val cr = resolver() ?: return false
        return try {
            when (ns) {
                "global" -> Settings.Global.putString(cr, key, value)
                "secure" -> Settings.Secure.putString(cr, key, value)
                "system" -> Settings.System.putString(cr, key, value)
                else -> false
            }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Reads several keys at once. The content resolver answers most keys without a permission,
     * and whatever stays unanswered is asked again through the Shizuku shell (some Samsung keys
     * are only visible to shell).
     */
    private suspend fun readValues(specs: List<KeySpec>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        specs.forEach { spec ->
            val value = readDirect(spec.ns, spec.key)
            if (value != null) map[spec.alias] = value
        }
        val missing = specs.filter { !map.containsKey(it.alias) }
        if (missing.isNotEmpty() && ShizukuShell.state.ready) {
            val command = buildString {
                missing.forEach { append("printf '${it.alias}='; settings get ${it.ns} ${it.key}; ") }
            }
            val out = (ShizukuShell.exec(command) as? ShellResult.Ok)?.output.orEmpty()
            Regex("(?m)^([a-z0-9_]+)=(.*)$").findAll(out).forEach { match ->
                val value = match.groupValues[2].trim()
                if (value.isNotEmpty() && value != "null") map[match.groupValues[1]] = value
            }
        }
        return map
    }

    /**
     * Applies a batch of settings changes. With WRITE_SECURE_SETTINGS the app writes them
     * itself (no Shizuku needed); otherwise it falls back to the shell.
     */
    private suspend fun applyOps(ops: List<Op>): Boolean {
        if (ops.isEmpty()) return true
        if (Access.secureWrite) {
            var ok = true
            ops.forEach { op ->
                val result = when (op) {
                    is Op.Put -> writeDirect(op.ns, op.key, op.value)
                    is Op.Delete -> writeDirect(op.ns, op.key, null)
                }
                ok = ok && result
            }
            return ok
        }
        if (!ShizukuShell.state.ready) return false
        val command = buildString {
            ops.forEach { op ->
                when (op) {
                    is Op.Put -> append("settings put ${op.ns} ${op.key} ${op.value}; ")
                    is Op.Delete -> append("settings delete ${op.ns} ${op.key}; ")
                }
            }
        }
        val result = ShizukuShell.exec(command)
        if (result !is ShellResult.Ok) return false
        val out = result.output
        return !out.contains("ERROR") && !out.contains("Exception") && !out.contains("Permission denial")
    }

    private fun canWriteSettings(): Boolean = Access.secureWrite || ShizukuShell.state.ready

    private fun needsShizuku(): String = "이 기능은 Shizuku 연결이 필요해요."

    // ---------------------------------------------------------------------------------------
    // Battery health
    // ---------------------------------------------------------------------------------------

    private fun field(raw: String, key: String): String? {
        val regex = Regex("(?m)^\\s*" + Regex.escape(key) + ":\\s*(.+?)\\s*$")
        return regex.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Reads a numeric dumpsys field.
     *
     * One UI prints some fields as `mSavedBatteryAsoc: [100]` and others as `mSavedBatteryBsoh: 95`,
     * so the digits are picked out of the value instead of converting the whole string.
     */
    private fun numField(raw: String, key: String): Int? {
        val value = field(raw, key) ?: return null
        return Regex("-?\\d+").find(value)?.value?.toIntOrNull()
    }

    /** `battery FirstUseDate: [20250609]` -> 2025-06-09 (any other shape is kept as printed). */
    private fun firstUseDate(raw: String): String? {
        val digits = Regex("\\d{8}").find(field(raw, "battery FirstUseDate") ?: "")?.value
        return if (digits != null) {
            "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        } else {
            field(raw, "battery FirstUseDate")
        }
    }

    private fun keyValue(raw: String, key: String): String? {
        val regex = Regex("(?m)^" + Regex.escape(key) + "=(.*)$")
        return regex.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun batteryStatusLabel(value: String?): String? = when (value) {
        "1" -> "알 수 없음"
        "2" -> "충전 중"
        "3" -> "방전 중"
        "4" -> "충전 안 함"
        "5" -> "충전 완료"
        else -> value
    }

    private fun batteryHealthLabel(value: String?): String? = when (value) {
        "1" -> "알 수 없음"
        "2" -> "정상"
        "3" -> "과열"
        "4" -> "수명 다함"
        "5" -> "과전압"
        "6" -> "고장"
        "7" -> "저온"
        else -> value
    }

    suspend fun readBattery(): Loaded<BatteryInfo> {
        if (!ShizukuShell.state.ready) return Loaded(null, needsShizuku())
        val dumpsys = ShizukuShell.exec("dumpsys battery")
        val raw = when (dumpsys) {
            is ShellResult.Ok -> dumpsys.output
            is ShellResult.Err -> return Loaded(null, dumpsys.message)
        }
        if (raw.isBlank()) return Loaded(null, "배터리 정보를 읽지 못했어요.")
        if (raw.startsWith("ERROR")) return Loaded(null, raw.trim())

        val sysfsCmd = buildString {
            append("printf 'cycle_count='; cat /sys/class/power_supply/battery/cycle_count 2>/dev/null; ")
            append("printf '\\nfg_asoc='; cat /sys/class/power_supply/battery/fg_asoc 2>/dev/null; ")
            append("printf '\\ncharge_full='; cat /sys/class/power_supply/battery/charge_full 2>/dev/null; ")
            append("printf '\\ncharge_full_design='; cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null; echo")
        }
        val sysfs = (ShizukuShell.exec(sysfsCmd) as? ShellResult.Ok)?.output.orEmpty()

        val temperature = field(raw, "temperature")?.toDoubleOrNull()?.let { it / 10.0 }
        val info = BatteryInfo(
            bsoh = numField(raw, "mSavedBatteryBsoh"),
            asoc = numField(raw, "mSavedBatteryAsoc"),
            firstUseDate = firstUseDate(raw),
            usageRaw = numField(raw, "mSavedBatteryUsage"),
            level = numField(raw, "level"),
            status = batteryStatusLabel(field(raw, "status")),
            temperatureC = temperature,
            voltageMv = field(raw, "voltage")?.toIntOrNull(),
            health = batteryHealthLabel(field(raw, "health")),
            cycleCountSys = keyValue(sysfs, "cycle_count"),
            fgAsocSys = keyValue(sysfs, "fg_asoc"),
            chargeFullSys = keyValue(sysfs, "charge_full"),
            chargeFullDesignSys = keyValue(sysfs, "charge_full_design")
        )
        if (info.asoc == null && info.usageRaw == null && info.level == null && info.bsoh == null) {
            return Loaded(null, "이 기기에서는 배터리 사이클 값을 제공하지 않아요.")
        }
        return Loaded(info, null)
    }

    // ---------------------------------------------------------------------------------------
    // Refresh rate
    // ---------------------------------------------------------------------------------------

    private val refreshKeys = listOf(
        KeySpec("secure", SETTINGS_MODE, "mode"),
        KeySpec("system", SETTINGS_MIN, "min"),
        KeySpec("system", SETTINGS_PEAK, "peak"),
        KeySpec("system", SETTINGS_USER, "user")
    )

    suspend fun readRefresh(): Loaded<RefreshInfo> {
        val values = readValues(refreshKeys)
        if (values.isEmpty() && !canWriteSettings()) return Loaded(null, needsShizuku())
        return Loaded(
            RefreshInfo(values["mode"], values["min"], values["peak"], values["user"]),
            null
        )
    }

    /**
     * Sets min_refresh_rate alone.
     *
     * 0 Hz means "no override": the key is deleted so the One UI default floor applies again.
     * min/peak are only honoured in the adaptive mode, so the mode is switched to 1 first and the
     * peak is raised when needed so that min never ends up above peak.
     */
    suspend fun setMinRefreshRate(hz: Int): String {
        if (!canWriteSettings()) return needsShizuku()
        val current = readRefresh().value
        val peak = current?.peakHz
        val ops = mutableListOf<Op>()
        ops += Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString())
        ops += rateOp(SETTINGS_MIN, hz)
        if (hz > 0 && peak != null && peak in 1 until hz) ops += rateOp(SETTINGS_PEAK, hz)
        return if (applyOps(ops)) "최소 주사율 ${rateLabel(hz)} 적용 완료" else "최소 주사율을 바꾸지 못했어요."
    }

    /** Sets peak_refresh_rate alone, in adaptive mode. */
    suspend fun setPeakRefreshRate(hz: Int): String {
        if (!canWriteSettings()) return needsShizuku()
        val current = readRefresh().value
        val minHz = current?.minHz
        val ops = mutableListOf<Op>()
        ops += Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString())
        ops += rateOp(SETTINGS_PEAK, hz)
        if (hz > 0 && minHz != null && minHz > hz) ops += rateOp(SETTINGS_MIN, hz)
        return if (applyOps(ops)) "최대 주사율 ${rateLabel(hz)} 적용 완료" else "최대 주사율을 바꾸지 못했어요."
    }

    /**
     * Pins the panel to one rate by writing the same value to min_refresh_rate and peak_refresh_rate.
     *
     * 60 uses the standard mode (0), 60+ uses the high mode (2), below 60 stays adaptive with
     * min == peak because One UI has no fixed mode there. 0 clears the keys back to default.
     */
    suspend fun setFixedRefreshRate(hz: Int): String {
        if (!canWriteSettings()) return needsShizuku()
        val ops = if (hz <= 0) {
            listOf(
                Op.Delete("system", SETTINGS_MIN),
                Op.Delete("system", SETTINGS_PEAK),
                Op.Delete("secure", SETTINGS_MODE)
            )
        } else {
            val mode = when {
                hz == 60 -> MODE_STANDARD_60
                hz > 60 -> MODE_HIGH
                else -> MODE_ADAPTIVE
            }
            listOf(
                Op.Put("secure", SETTINGS_MODE, mode.toString()),
                Op.Put("system", SETTINGS_MIN, fmt(hz.toDouble())),
                Op.Put("system", SETTINGS_PEAK, fmt(hz.toDouble()))
            )
        }
        return if (applyOps(ops)) "고정 주사율 ${rateLabel(hz)} 적용 완료" else "고정 주사율을 바꾸지 못했어요."
    }

    private fun rateOp(key: String, hz: Int): Op =
        if (hz <= 0) Op.Delete("system", key) else Op.Put("system", key, fmt(hz.toDouble()))

    private fun rateLabel(hz: Int): String = if (hz <= 0) "해제" else "${hz}Hz"

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    // ---------------------------------------------------------------------------------------
    // Screen refresh rate overlay (the developer-options "show refresh rate" indicator)
    // ---------------------------------------------------------------------------------------

    suspend fun readRefreshOverlay(): Boolean? = withContext(Dispatchers.IO) { RefreshRateOverlay.isEnabled() }

    suspend fun setRefreshOverlay(on: Boolean): String = RefreshRateOverlay.set(on)

    // ---------------------------------------------------------------------------------------
    // Display: resolution (wm size) and smallest width (wm density)
    // ---------------------------------------------------------------------------------------

    suspend fun readDisplay(): Loaded<DisplayInfo> {
        if (!ShizukuShell.state.ready) return Loaded(null, needsShizuku())
        val out = when (val r = ShizukuShell.exec("wm size; echo '#SPLIT#'; wm density")) {
            is ShellResult.Ok -> r.output
            is ShellResult.Err -> return Loaded(null, r.message)
        }
        if (out.startsWith("ERROR")) return Loaded(null, out.trim())
        val parts = out.split("#SPLIT#")
        val sizeText = parts.getOrNull(0).orEmpty()
        val densityText = parts.getOrNull(1).orEmpty()

        val sizeRegex = Regex("(Physical|Override) size:\\s*(\\d+)x(\\d+)")
        val densityRegex = Regex("(Physical|Override) density:\\s*(\\d+)")
        val sizes = sizeRegex.findAll(sizeText).associateBy { it.groupValues[1] }
        val densities = densityRegex.findAll(densityText).associateBy { it.groupValues[1] }

        fun px(group: MatchResult?): Pair<Int?, Int?> =
            group?.groupValues?.let { it[2].toIntOrNull() to it[3].toIntOrNull() } ?: (null to null)

        val (physicalW, physicalH) = px(sizes["Physical"])
        val (overrideW, overrideH) = px(sizes["Override"])
        val physicalDensity = densities["Physical"]?.groupValues?.get(2)?.toIntOrNull()
        val overrideDensity = densities["Override"]?.groupValues?.get(2)?.toIntOrNull()

        if (physicalW == null || physicalDensity == null) {
            return Loaded(null, "화면 크기 정보를 읽지 못했어요.")
        }
        return Loaded(
            DisplayInfo(
                widthPx = overrideW ?: physicalW,
                heightPx = overrideH ?: physicalH,
                densityDpi = overrideDensity ?: physicalDensity,
                physicalWidthPx = physicalW,
                physicalHeightPx = physicalH,
                physicalDensityDpi = physicalDensity,
                overrideWidthPx = overrideW,
                overrideHeightPx = overrideH,
                overrideDensityDpi = overrideDensity
            ),
            null
        )
    }

    /**
     * Switches the panel to a share of its native resolution, the way Samsung's own
     * "화면 해상도" setting does: the density is scaled with the size so that the layout
     * (smallest width in dp) stays exactly the same.
     */
    suspend fun applyResolution(percent: Int): String {
        if (!ShizukuShell.state.ready) return needsShizuku()
        val info = readDisplay().value ?: return "화면 정보를 읽지 못해 바꾸지 못했어요."
        val pw = info.physicalWidthPx ?: return "화면 크기를 읽지 못해 바꾸지 못했어요."
        val ph = info.physicalHeightPx ?: return "화면 크기를 읽지 못해 바꾸지 못했어요."
        val dp = info.smallestWidthDp ?: info.physicalSmallestWidthDp
            ?: return "화면 밀도를 읽지 못해 바꾸지 못했어요."
        val physicalDensity = info.physicalDensityDpi

        if (percent >= 100) {
            val dpi = (min(pw, ph) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
            val command = if (physicalDensity != null && dpi == physicalDensity) {
                "wm size reset; wm density reset"
            } else {
                "wm size reset; wm density $dpi"
            }
            return runChange(command, "해상도 원본 복원")
        }

        if (percent !in 40..95) return "지원하지 않는 해상도예요."
        val w = ((pw * percent / 100.0).roundToInt() / 2) * 2
        val h = ((ph * percent / 100.0).roundToInt() / 2) * 2
        if (w < 480 || h < 480) return "해상도가 너무 낮아 적용하지 않았어요."
        val dpi = (min(w, h) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
        return runChange("wm size ${w}x$h; wm density $dpi", "해상도 ${percent}% 적용")
    }

    /**
     * Sets the smallest width the way the developer-options switch does: the density is chosen so
     * that min(screen width, height) * 160 / density equals the requested dp.
     */
    suspend fun setSmallestWidthDp(dp: Int): String {
        if (!ShizukuShell.state.ready) return needsShizuku()
        if (dp !in 200..1000) return "최소 너비는 200~1000dp 사이로 입력해 주세요."
        val info = readDisplay().value ?: return "화면 정보를 읽지 못해 적용하지 못했어요."
        val width = info.widthPx ?: return "화면 크기를 읽지 못해 적용하지 못했어요."
        val height = info.heightPx ?: return "화면 크기를 읽지 못해 적용하지 못했어요."
        val targetDpi = (min(width, height) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
        return runChange("wm density $targetDpi", "최소 너비 ${dp}dp 적용")
    }

    suspend fun resetSmallestWidth(): String {
        if (!ShizukuShell.state.ready) return needsShizuku()
        return runChange("wm density reset", "화면 밀도 기본값 복원")
    }

    // ---------------------------------------------------------------------------------------
    // Animation scales
    // ---------------------------------------------------------------------------------------

    private val animKeys = listOf(
        KeySpec("global", "window_animation_scale", "window"),
        KeySpec("global", "transition_animation_scale", "transition"),
        KeySpec("global", "animator_duration_scale", "animator")
    )

    suspend fun setAnimationScale(which: String, scale: Double): String {
        if (!canWriteSettings()) return needsShizuku()
        val value = String.format(java.util.Locale.US, "%.2f", scale)
        val targets = if (which == "all") animKeys else animKeys.filter { it.alias == which }
        if (targets.isEmpty()) return "알 수 없는 대상이에요."
        val ops = targets.map { Op.Put("global", it.key, value) }
        val label = if (which == "all") "애니메이션 배율" else "애니메이션 배율"
        return if (applyOps(ops)) "$label ${trimScale(value)}x 적용 완료" else "애니메이션 배율을 바꾸지 못했어요."
    }

    fun trimScale(value: String): String =
        value.trimEnd('0').trimEnd('.').ifEmpty { "0" }

    // ---------------------------------------------------------------------------------------
    // Extra dim (reduce bright colors)
    // ---------------------------------------------------------------------------------------

    suspend fun setExtraDim(on: Boolean): String {
        if (!canWriteSettings()) return needsShizuku()
        val ops = listOf(
            Op.Put("secure", "reduce_bright_colors_activated", if (on) "1" else "0"),
            Op.Put("secure", "reduce_bright_colors_persist_across_reboots", if (on) "1" else "0")
        )
        return if (applyOps(ops)) {
            if (on) "추가 어둡게 켜짐" else "추가 어둡게 꺼짐"
        } else {
            "추가 어둡게를 바꾸지 못했어요."
        }
    }

    // ---------------------------------------------------------------------------------------
    // Power saving mode + high refresh
    // ---------------------------------------------------------------------------------------

    private val powerSaveKeys = listOf(
        KeySpec("global", "low_power", "psm"),
        KeySpec("global", "low_power_mode", "psm_mode"),
        KeySpec("secure", SETTINGS_MODE, "mode"),
        KeySpec("system", SETTINGS_MIN, "min"),
        KeySpec("system", SETTINGS_PEAK, "peak"),
        KeySpec("global", PSM_REFRESH_LIMIT, "limit"),
        KeySpec("global", PSM_REFRESH_TAG, "tag")
    )

    suspend fun readPowerSave(): Loaded<PowerSaveInfo> {
        val values = readValues(powerSaveKeys)
        if (values.isEmpty() && !canWriteSettings()) return Loaded(null, needsShizuku())
        val psmOn = values["psm"] == "1" || values["psm_mode"] == "1"
        return Loaded(
            PowerSaveInfo(
                psmOn = psmOn,
                mode = values["mode"],
                min = values["min"],
                peak = values["peak"],
                refreshLimitEnabled = values["limit"]
            ),
            null
        )
    }

    /**
     * One UI switches the panel to 60Hz while power saving is on. To keep 120Hz the PSM override
     * keys are cleared, then the motion-smoothness mode is re-applied (0 -> 1) so the display
     * policy recalculates while the battery saver itself stays on.
     */
    suspend fun enablePowerSaveHighRefresh(fixed: Boolean): String {
        if (!canWriteSettings()) return needsShizuku()
        backupPowerSaveState()
        val min = if (fixed) "120.0" else "0.0"
        val label = if (fixed) "절전 모드 + 고정 120Hz 적용" else "절전 모드 + 120Hz 유지 적용"

        // Step 1: drop the power-saving display limits.
        val clear = listOf(
            Op.Put("global", PSM_REFRESH_LIMIT, "0"),
            Op.Put("global", PSM_REFRESH_LIMIT_COVER, "0"),
            Op.Put("global", PSM_REFRESH_TAG, "0"),
            Op.Put("global", PSM_REFRESH_TAG_COVER, "0"),
            Op.Put("global", PSM_REFRESH_TAG_STATE, "0"),
            Op.Delete("global", PSM_MODE_REFRESH),
            Op.Delete("global", PSM_MODE_REFRESH_COVER),
            Op.Put("secure", SETTINGS_MODE, MODE_STANDARD_60.toString())
        )
        if (!applyOps(clear)) return "$label 실패"

        // Step 2: let the display policy recalculate into the adaptive (or fixed) mode.
        delay(250)
        val apply = listOf(
            Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString()),
            Op.Delete("secure", "refresh_rate"),
            Op.Put("secure", "user_refresh_rate", "120"),
            Op.Put("system", SETTINGS_PEAK, "120.0"),
            Op.Put("system", "user_refresh_rate", "120"),
            Op.Put("system", SETTINGS_MIN, min)
        )
        if (!applyOps(apply)) return "$label 실패"

        val psmOn = readPowerSave().value?.psmOn == true
        return if (psmOn) "$label 완료" else "$label 완료 · 절전 모드를 켠 뒤 다시 눌러 주세요"
    }

    /** Puts back the refresh-rate and power-saving values that were in place before the override. */
    suspend fun disablePowerSaveHighRefresh(): String {
        if (!canWriteSettings()) return needsShizuku()
        val saved = prefs?.getString(KEY_PSM_BACKUP, null)
        val ops = if (saved.isNullOrBlank()) {
            listOf(
                Op.Delete("global", PSM_REFRESH_LIMIT),
                Op.Delete("global", PSM_REFRESH_LIMIT_COVER),
                Op.Delete("global", PSM_REFRESH_TAG),
                Op.Delete("global", PSM_REFRESH_TAG_COVER),
                Op.Delete("global", PSM_REFRESH_TAG_STATE),
                Op.Delete("global", PSM_MODE_REFRESH),
                Op.Delete("global", PSM_MODE_REFRESH_COVER),
                Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString())
            )
        } else {
            restoreOps(saved)
        }
        return if (applyOps(ops)) {
            if (!saved.isNullOrBlank()) prefs?.edit()?.remove(KEY_PSM_BACKUP)?.apply()
            "절전 모드 120Hz 유지 해제 완료"
        } else {
            "절전 모드 120Hz 유지 해제 실패"
        }
    }

    private suspend fun backupPowerSaveState() {
        val store = prefs ?: return
        if (store.getString(KEY_PSM_BACKUP, null) != null) return
        val specs = listOf(
            KeySpec("secure", SETTINGS_MODE, "secure_mode"),
            KeySpec("secure", "refresh_rate", "secure_refresh"),
            KeySpec("secure", "user_refresh_rate", "secure_user"),
            KeySpec("system", SETTINGS_MIN, "min"),
            KeySpec("system", SETTINGS_PEAK, "peak"),
            KeySpec("system", "user_refresh_rate", "sys_user"),
            KeySpec("global", PSM_REFRESH_LIMIT, "limit"),
            KeySpec("global", PSM_REFRESH_LIMIT_COVER, "limit_cover"),
            KeySpec("global", PSM_REFRESH_TAG, "tag"),
            KeySpec("global", PSM_REFRESH_TAG_COVER, "tag_cover"),
            KeySpec("global", PSM_REFRESH_TAG_STATE, "tag_state"),
            KeySpec("global", PSM_MODE_REFRESH, "mode_refresh"),
            KeySpec("global", PSM_MODE_REFRESH_COVER, "mode_refresh_cover")
        )
        val values = readValues(specs)
        if (values.isEmpty()) return
        val lines = specs.map { spec ->
            val value = values[spec.alias]
            "${spec.ns}\t${spec.key}\t${value ?: ABSENT}"
        }
        store.edit().putString(KEY_PSM_BACKUP, lines.joinToString("\n")).apply()
    }

    private fun restoreOps(saved: String): List<Op> {
        val ops = mutableListOf<Op>()
        saved.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split("\t")
            if (parts.size == 3) {
                val (namespace, key, value) = parts
                ops += if (value == ABSENT) {
                    Op.Delete(namespace, key)
                } else {
                    Op.Put(namespace, key, value)
                }
            }
        }
        ops += Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString())
        return ops
    }

    // ---------------------------------------------------------------------------------------
    // Extras: shutter, animation scales, extra dim, battery protection, adaptive battery,
    // master sync, Doze
    // ---------------------------------------------------------------------------------------

    private val extrasKeys = listOf(
        KeySpec("system", SHUTTER_KEY, "shutter"),
        KeySpec("global", "window_animation_scale", "window"),
        KeySpec("global", "transition_animation_scale", "transition"),
        KeySpec("global", "animator_duration_scale", "animator"),
        KeySpec("secure", "reduce_bright_colors_activated", "extra_dim"),
        KeySpec("global", PROTECT_MODE, "protect"),
        KeySpec("global", PROTECT_THRESHOLD, "threshold"),
        KeySpec("global", "adaptive_battery_management_enabled", "adaptive")
    )

    suspend fun readExtras(): Loaded<ExtrasInfo> {
        val values = readValues(extrasKeys)
        val autoSync = autoSyncEnabled()
        val doze = readDozeEnabled()
        if (values.isEmpty() && autoSync == null && doze == null && !canWriteSettings()) {
            return Loaded(null, needsShizuku())
        }
        return Loaded(
            ExtrasInfo(
                shutter = values["shutter"],
                windowScale = values["window"],
                transitionScale = values["transition"],
                animatorScale = values["animator"],
                extraDim = values["extra_dim"],
                protectMode = values["protect"],
                protectThreshold = values["threshold"],
                adaptiveBattery = values["adaptive"],
                autoSync = autoSync,
                dozeEnabled = doze
            ),
            null
        )
    }

    suspend fun applyShutter(value: Int): String {
        if (!canWriteSettings()) return needsShizuku()
        return if (applyOps(listOf(Op.Put("system", SHUTTER_KEY, value.toString())))) {
            if (value == 0) "셔터음 끄기 적용 완료" else "강제 셔터음 적용 완료"
        } else {
            "셔터음을 바꾸지 못했어요."
        }
    }

    /**
     * One UI battery protection. `protect_battery` holds the mode
     * (0 사용 안 함 / 1 최대 보호 / 2 시스템 관리 / 3 표준) and
     * `battery_protection_threshold` the charge limit used by mode 1 (default 80).
     * The threshold is written first so the mode change is the last observable step.
     */
    suspend fun setBatteryProtection(mode: Int, threshold: Int?): String {
        if (!canWriteSettings()) return needsShizuku()
        val ops = mutableListOf<Op>()
        if (threshold != null) ops += Op.Put("global", PROTECT_THRESHOLD, threshold.toString())
        ops += Op.Put("global", PROTECT_MODE, mode.toString())
        if (!applyOps(ops)) return "배터리 보호를 바꾸지 못했어요."
        val now = readValues(listOf(KeySpec("global", PROTECT_MODE, "mode")))[ "mode" ]
        if (now != null && now != mode.toString()) {
            return "이 기기에서는 설정이 반영되지 않았어요. 설정 앱에서 바꿔 주세요."
        }
        return when (mode) {
            0 -> "배터리 보호 꺼짐"
            3 -> "배터리 보호 표준 모드 적용"
            else -> "배터리 보호 최대 ${threshold ?: 80}% 적용"
        }
    }

    suspend fun setAdaptiveBattery(on: Boolean): String {
        if (!canWriteSettings()) return needsShizuku()
        return if (applyOps(listOf(Op.Put("global", "adaptive_battery_management_enabled", if (on) "1" else "0")))) {
            if (on) "적응형 배터리 켜짐" else "적응형 배터리 꺼짐"
        } else {
            "적응형 배터리를 바꾸지 못했어요."
        }
    }

    // Master sync is a public API, so no shell is needed; WRITE_SYNC_SETTINGS is a normal
    // permission granted at install time.
    fun autoSyncEnabled(): Boolean? = try {
        ContentResolver.getMasterSyncAutomatically()
    } catch (t: Throwable) {
        null
    }

    suspend fun setAutoSync(on: Boolean): String = try {
        ContentResolver.setMasterSyncAutomatically(on)
        val now = ContentResolver.getMasterSyncAutomatically()
        if (now == on) {
            if (on) "자동 동기화 켜짐" else "자동 동기화 꺼짐"
        } else {
            "자동 동기화를 바꾸지 못했어요."
        }
    } catch (t: Throwable) {
        "이 기기에서는 자동 동기화를 바꿀 수 없어요."
    }

    /** Doze state is only visible through dumpsys, so it needs Shizuku. */
    private suspend fun readDozeEnabled(): Boolean? {
        if (!ShizukuShell.state.ready) return null
        val out = (ShizukuShell.exec("dumpsys deviceidle") as? ShellResult.Ok)?.output ?: return null
        Regex("mEnabled=(true|false)").find(out)?.let { return it.groupValues[1] == "true" }
        Regex("(?m)^\\s*enabled[:=]\\s*(true|false)").find(out)?.let { return it.groupValues[1] == "true" }
        return null
    }

    suspend fun setDozeEnabled(enabled: Boolean): String {
        if (!ShizukuShell.state.ready) return needsShizuku()
        val command = if (enabled) {
            "dumpsys deviceidle enable"
        } else {
            "dumpsys deviceidle disable"
        }
        val label = if (enabled) "잠자기 모드 켜기" else "잠자기 모드 끄기"
        val result = runChange(command, label)
        if (!result.endsWith("완료")) return result
        val now = readDozeEnabled()
        return when (now) {
            enabled -> "$label 완료"
            null -> "$label 완료 · 상태는 기기에서 확인해 주세요"
            else -> "$label 실패"
        }
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun runChange(command: String, label: String): String {
        if (!ShizukuShell.state.ready) return needsShizuku()
        return when (val r = ShizukuShell.exec(command)) {
            is ShellResult.Ok -> {
                val out = r.output.trim()
                val failed = out.startsWith("ERROR") ||
                    out.startsWith("Error") ||
                    out.contains("Exception") ||
                    out.contains("Permission denial") ||
                    out.contains("Unknown command")
                if (failed) "$label 실패: $out" else "$label 완료"
            }
            is ShellResult.Err -> "$label 실패: ${r.message}"
        }
    }

    // ---------------------------------------------------------------------------------------
    // Device header
    // ---------------------------------------------------------------------------------------

    fun deviceModel(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "갤럭시"

    fun androidVersion(): String = "Android ${Build.VERSION.RELEASE}"

    /**
     * One UI version. Android 13+ exposes it as an int on Build.VERSION.SEM_PLATFORM
     * (60100 -> 6.1.0); older builds carry a string.
     */
    fun oneUiVersion(): String? = try {
        val field = Build.VERSION::class.java.getField("SEM_PLATFORM")
        when (val value = field.get(null)) {
            is Int -> {
                val major = value / 10000
                val minor = (value / 100) % 100
                val patch = value % 100
                if (major <= 0) null
                else if (patch > 0) "One UI $major.$minor.$patch"
                else if (minor > 0) "One UI $major.$minor"
                else "One UI $major"
            }
            is String -> value.takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("One UI")) it else "One UI $it"
            }
            else -> null
        }
    } catch (t: Throwable) {
        null
    }
}

/** `60.0` / `60` / null(=설정 없음) -> 60; unreadable values are treated as 없음. */
internal fun rateOf(raw: String?): Int? = raw?.trim()?.toDoubleOrNull()?.roundToInt()
