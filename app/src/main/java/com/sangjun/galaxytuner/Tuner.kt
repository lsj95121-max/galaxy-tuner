package com.sangjun.galaxytuner

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.delay
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
    val refreshLimitEnabled: String?,
    val restrictedPerformance: String?
) {
    val peakHz: Int? get() = rateOf(peak)

    /** True when the "keep high refresh in power saving" override is written. */
    val overrideApplied: Boolean
        get() = refreshLimitEnabled == "0" && mode != "0" && (peakHz ?: 0) >= 120

    /** `restricted_device_performance` = "1,1" is One UI's CPU 70% limit. */
    val cpuLimited: Boolean get() = restrictedPerformance?.replace(" ", "") == CPU_LIMIT_ON
}

/** `restricted_device_performance` (global) value that pins the CPU to 70%. */
internal const val CPU_LIMIT_ON = "1,1"

/**
 * Everything that lives in Settings but does not deserve its own shell round-trip:
 * camera shutter, animation scales, extra dim, battery protection, adaptive battery,
 * master sync and the battery saver constants Quick-Doze rides on.
 */
data class ExtrasInfo(
    val shutter: String?,
    val windowScale: String?,
    val transitionScale: String?,
    val animatorScale: String?,
    val extraDim: String?,
    val extraDimLevel: String?,
    val protectMode: String?,
    val protectThreshold: String?,
    val adaptiveBattery: String?,
    val autoSync: Boolean?,
    val batterySaverConstants: String?
) {
    val animationScale: String? get() = windowScale ?: transitionScale ?: animatorScale

    /** Extra dim intensity 0..100; null when the firmware keeps no level. */
    val extraDimPercent: Int? get() = extraDimLevel?.trim()?.toIntOrNull()?.coerceIn(0, 100)

    /** True while `battery_saver_constants` carries the quick-doze flag written by [QuickDoze]. */
    val quickDoze: Boolean get() = QuickDoze.hasQuickDoze(batterySaverConstants)

    val protectionLabel: String
        get() = when (protectMode) {
            "0", null -> "사용 안 함"
            "1" -> "최대 보호 ${protectThreshold?.toIntOrNull() ?: 80}%"
            "2" -> "최대 보호 (시스템 관리)"
            "3" -> "기본 (100% 충전 후 일시정지)"
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
    private const val KEY_PERF_BACKUP = "restricted_performance_backup"
    private const val KEY_NATIVE_DENSITY = "native_density_dpi"
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

    // Samsung power saving CPU limit (global table): "1,1" = CPU 70%.
    private const val RESTRICTED_PERF = "restricted_device_performance"

    // Battery saver constants (global table) that carry the quick-doze flag, and the extra dim
    // intensity (secure table). `device_idle_constants` is deprecated on Android 12+.
    private const val BATTERY_SAVER_CONSTANTS = "battery_saver_constants"
    private const val EXTRA_DIM_LEVEL = "reduce_bright_colors_level"

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
        if (missing.isNotEmpty() && Shell.ready) {
            val command = buildString {
                missing.forEach { append("printf '${it.alias}='; settings get ${it.ns} ${it.key}; ") }
            }
            val out = (Shell.exec(command) as? ShellResult.Ok)?.output.orEmpty()
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
        var pending = ops
        if (Access.secureWrite) {
            val refused = mutableListOf<Op>()
            ops.forEach { op -> if (!writeOp(op)) refused += op }
            if (refused.isEmpty()) return true
            // Some keys are refused to apps even with the permission; try them through the shell.
            pending = refused
        }
        // The Settings.System table only needs WRITE_SETTINGS. The sdk22 build targets API 22, so
        // the phone grants that at install time and min/peak refresh rate changes work with no
        // Shizuku and no ADB connection at all.
        if (pending.isNotEmpty() && Access.systemWrite) {
            val (systemOps, others) = pending.partition { it.ns == "system" }
            val refused = systemOps.filter { !writeOp(it) }
            if (refused.isEmpty() && others.isEmpty()) return true
            pending = others + refused
        }
        if (pending.isEmpty()) return true
        if (!Shell.ready) return false
        val command = buildString {
            pending.forEach { op ->
                when (op) {
                    is Op.Put -> append("settings put ${op.ns} ${op.key} ${op.value}; ")
                    is Op.Delete -> append("settings delete ${op.ns} ${op.key}; ")
                }
            }
        }
        val result = Shell.exec(command)
        if (result !is ShellResult.Ok) return false
        val out = result.output
        return !out.contains("ERROR") && !out.contains("Exception") && !out.contains("Permission denial")
    }

    /** The settings table an op writes to, used to route the sdk22 Settings.System writes. */
    private val Op.ns: String
        get() = when (this) {
            is Op.Put -> ns
            is Op.Delete -> ns
        }

    private fun writeOp(op: Op): Boolean = when (op) {
        is Op.Put -> writeDirect(op.ns, op.key, op.value)
        is Op.Delete -> writeDirect(op.ns, op.key, null)
    }

    private fun canWriteSettings(): Boolean = Access.canWriteSettings

    /** The secure table (refresh_rate_mode) is only writable with WRITE_SECURE_SETTINGS or a shell. */
    private fun canWriteSecureTable(): Boolean = Access.secureWrite || Shell.ready

    private fun needsShizuku(): String = "Shizuku 또는 무선 디버깅(ADB) 연결이 필요해요."

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
        if (!Shell.ready) return Loaded(null, needsShizuku())
        val dumpsys = Shell.exec("dumpsys battery")
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
        val sysfs = (Shell.exec(sysfsCmd) as? ShellResult.Ok)?.output.orEmpty()

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
        if (canWriteSecureTable()) ops += Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString())
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
        if (canWriteSecureTable()) ops += Op.Put("secure", SETTINGS_MODE, MODE_ADAPTIVE.toString())
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
            buildList {
                add(Op.Delete("system", SETTINGS_MIN))
                add(Op.Delete("system", SETTINGS_PEAK))
                if (canWriteSecureTable()) add(Op.Delete("secure", SETTINGS_MODE))
            }
        } else {
            val mode = when {
                hz == 60 -> MODE_STANDARD_60
                hz > 60 -> MODE_HIGH
                else -> MODE_ADAPTIVE
            }
            buildList {
                if (canWriteSecureTable()) add(Op.Put("secure", SETTINGS_MODE, mode.toString()))
                add(Op.Put("system", SETTINGS_MIN, fmt(hz.toDouble())))
                add(Op.Put("system", SETTINGS_PEAK, fmt(hz.toDouble())))
            }
        }
        return if (applyOps(ops)) "고정 주사율 ${rateLabel(hz)} 적용 완료" else "고정 주사율을 바꾸지 못했어요."
    }

    private fun rateOp(key: String, hz: Int): Op =
        if (hz <= 0) Op.Delete("system", key) else Op.Put("system", key, fmt(hz.toDouble()))

    private fun rateLabel(hz: Int): String = if (hz <= 0) "해제" else "${hz}Hz"

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    // ---------------------------------------------------------------------------------------
    // Display: resolution (size) and smallest width (density)
    //
    // The change is tried through the hidden IWindowManager first (WRITE_SECURE_SETTINGS is
    // enough for it), so Shizuku can stay closed; `wm` is the fallback for firmwares that
    // refuse the direct call.
    // ---------------------------------------------------------------------------------------

    /**
     * Reads the panel and the current override.
     *
     * `wm` is asked first while the shell is connected, because it prints the physical values
     * beside the overrides. With Shizuku closed the same numbers come from DisplayManager, where
     * the density the panel shipped with is only known from an earlier shell read.
     */
    suspend fun readDisplay(): Loaded<DisplayInfo> {
        readDisplayShell()?.let { info ->
            saveNativeDensity(info.physicalDensityDpi)
            return Loaded(info, null)
        }
        DisplayBridge.read(AppCtx.context, nativeDensity())?.let { return Loaded(it, null) }
        return Loaded(
            null,
            if (Shell.ready) {
                "화면 크기 정보를 읽지 못했어요."
            } else {
                "화면 정보를 읽지 못했어요. Shizuku를 연결해 주세요."
            }
        )
    }

    /** The `wm size` / `wm density` view; null when the shell is closed or the output is unusable. */
    private suspend fun readDisplayShell(): DisplayInfo? {
        if (!Shell.ready) return null
        val out = (Shell.exec("wm size; echo '#SPLIT#'; wm density") as? ShellResult.Ok)?.output ?: return null
        if (out.startsWith("ERROR")) return null
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

        if (physicalW == null || physicalDensity == null) return null
        return DisplayInfo(
            widthPx = overrideW ?: physicalW,
            heightPx = overrideH ?: physicalH,
            densityDpi = overrideDensity ?: physicalDensity,
            physicalWidthPx = physicalW,
            physicalHeightPx = physicalH,
            physicalDensityDpi = physicalDensity,
            overrideWidthPx = overrideW,
            overrideHeightPx = overrideH,
            overrideDensityDpi = overrideDensity
        )
    }

    /** Panel density remembered from a shell read; null until one happened. */
    private fun nativeDensity(): Int? = prefs?.getInt(KEY_NATIVE_DENSITY, 0)?.takeIf { it > 0 }

    private fun saveNativeDensity(dpi: Int?) {
        if (dpi == null || dpi <= 0) return
        val store = prefs ?: return
        if (store.getInt(KEY_NATIVE_DENSITY, 0) == dpi) return
        store.edit().putInt(KEY_NATIVE_DENSITY, dpi).apply()
    }

    /**
     * Switches the panel to a share of its native resolution, the way Samsung's own
     * "화면 해상도" setting does: the density is scaled with the size so that the layout
     * (smallest width in dp) stays exactly the same.
     */
    suspend fun applyResolution(percent: Int): String {
        val info = readDisplay().value ?: return "화면 정보를 읽지 못해 바꾸지 못했어요."
        val pw = info.physicalWidthPx ?: return "화면 크기를 읽지 못해 바꾸지 못했어요."
        val ph = info.physicalHeightPx ?: return "화면 크기를 읽지 못해 바꾸지 못했어요."
        val dp = info.smallestWidthDp ?: info.physicalSmallestWidthDp
            ?: return "화면 밀도를 읽지 못해 바꾸지 못했어요."
        val physicalDensity = info.physicalDensityDpi

        if (percent >= 100) {
            val dpi = (min(pw, ph) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
            // Keep the current dp when it was set apart from the panel's own density.
            val keepDensity = physicalDensity == null || dpi != physicalDensity
            return changeDisplay(
                label = "해상도 원본 복원",
                direct = {
                    DisplayBridge.clearSize()
                        ?: if (keepDensity) DisplayBridge.setDensity(dpi) else null
                },
                shell = if (keepDensity) "wm size reset; wm density $dpi" else "wm size reset; wm density reset",
                verify = { after ->
                    !after.isSizeOverridden &&
                        (after.smallestWidthDp?.let { kotlin.math.abs(it - dp) <= 1 } ?: true)
                }
            )
        }

        if (percent !in 40..95) return "지원하지 않는 해상도예요."
        val w = ((pw * percent / 100.0).roundToInt() / 2) * 2
        val h = ((ph * percent / 100.0).roundToInt() / 2) * 2
        if (w < 480 || h < 480) return "해상도가 너무 낮아 적용하지 않았어요."
        val dpi = (min(w, h) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
        return changeDisplay(
            label = "해상도 ${percent}% 적용",
            direct = { DisplayBridge.setSize(w, h) ?: DisplayBridge.setDensity(dpi) },
            shell = "wm size ${w}x$h; wm density $dpi",
            verify = { after -> after.widthPx == w && after.heightPx == h }
        )
    }

    /**
     * Sets the smallest width the way the developer-options switch does: the density is chosen so
     * that min(screen width, height) * 160 / density equals the requested dp.
     */
    suspend fun setSmallestWidthDp(dp: Int): String {
        if (dp !in 200..1000) return "최소 너비는 200~1000dp 사이로 입력해 주세요."
        val info = readDisplay().value ?: return "화면 정보를 읽지 못해 적용하지 못했어요."
        val width = info.widthPx ?: return "화면 크기를 읽지 못해 적용하지 못했어요."
        val height = info.heightPx ?: return "화면 크기를 읽지 못해 적용하지 못했어요."
        val targetDpi = (min(width, height) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
        if (info.densityDpi == targetDpi) return "이미 최소 너비 ${dp}dp예요."
        return changeDisplay(
            label = "최소 너비 ${dp}dp 적용",
            direct = { DisplayBridge.setDensity(targetDpi) },
            shell = "wm density $targetDpi",
            verify = { after -> after.smallestWidthDp?.let { kotlin.math.abs(it - dp) <= 1 } == true }
        )
    }

    suspend fun resetSmallestWidth(): String {
        val native = readDisplay().value?.physicalDensityDpi ?: nativeDensity()
        return changeDisplay(
            label = "화면 밀도 기본값 복원",
            direct = { DisplayBridge.clearDensity() },
            shell = "wm density reset",
            verify = { after -> native == null || after.densityDpi == native }
        )
    }

    /**
     * Runs one display change: the direct call first, `wm` when that is refused, and a read-back
     * so a call that returned without changing anything is not reported as done.
     */
    private suspend fun changeDisplay(
        label: String,
        direct: () -> String?,
        shell: String,
        verify: (DisplayInfo) -> Boolean
    ): String {
        val directError: String? = direct()
        val refused = directError != null
        if (!refused) {
            delay(350)
            val after = readDisplay().value
            if (after == null) return "$label 적용했어요. 화면에서 확인해 주세요."
            if (verify(after)) return "$label 완료"
        }
        if (!Shell.ready) {
            return if (!refused) {
                "이 기기에서는 앱이 직접 바꾸지 못했어요."
            } else {
                "이 기기에서는 앱이 직접 바꿀 수 없어요. Shizuku를 연결하면 바꿀 수 있어요."
            }
        }
        return runChange(shell, label)
    }

    // ---------------------------------------------------------------------------------------
    // Animation scales
    // ---------------------------------------------------------------------------------------

    private val animKeys = listOf(
        KeySpec("global", "window_animation_scale", "window"),
        KeySpec("global", "transition_animation_scale", "transition"),
        KeySpec("global", "animator_duration_scale", "animator")
    )

    /**
     * Reads a scale the user typed: `0.45`, `0.45x` and `45%` all mean 0.45.
     * Returns null when the text is not a number in 0.05..10.
     */
    fun parseAnimationScale(text: String): Double? {
        val raw = text.trim().removeSuffix("x").removeSuffix("X").removeSuffix("%")
            .let { it.trim() }
        if (raw.isEmpty()) return null
        val percent = text.trim().endsWith("%")
        val number = raw.toDoubleOrNull() ?: return null
        val scale = if (percent) number / 100.0 else number
        return scale.takeIf { it in 0.05..10.0 }
    }

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

    /** Extra dim intensity, the slider under the toggle (100% = as dim as the panel allows). */
    suspend fun setExtraDimLevel(percent: Int): String {
        if (!canWriteSettings()) return needsShizuku()
        val level = percent.coerceIn(0, 100)
        if (!applyOps(listOf(Op.Put("secure", EXTRA_DIM_LEVEL, level.toString())))) {
            return "추가 어둡게 강도를 바꾸지 못했어요."
        }
        return when (readDirect("secure", EXTRA_DIM_LEVEL)?.trim()?.toIntOrNull()) {
            level -> "추가 어둡게 강도 ${level}% 적용"
            null -> "추가 어둡게 강도 ${level}%를 보냈어요. 화면에서 확인해 주세요."
            else -> "이 기기에서는 강도가 반영되지 않았어요."
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
        KeySpec("global", PSM_REFRESH_TAG, "tag"),
        KeySpec("global", RESTRICTED_PERF, "restricted")
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
                refreshLimitEnabled = values["limit"],
                restrictedPerformance = values["restricted"]
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

    /**
     * One UI's "CPU 속도 70% 제한": `restricted_device_performance` = "1,1".
     * The value in place before is kept in the app, and turning the switch off writes it back
     * (or "0,0" when nothing was there) so the CPU returns to full speed.
     */
    suspend fun setCpuLimit(on: Boolean): String {
        if (!canWriteSettings()) return needsShizuku()
        val backup = prefs?.getString(KEY_PERF_BACKUP, null)
        if (on && backup == null) {
            val current = readDirect("global", RESTRICTED_PERF)
            prefs?.edit()?.putString(KEY_PERF_BACKUP, current ?: ABSENT)?.apply()
        }
        val target = if (on) {
            CPU_LIMIT_ON
        } else {
            backup?.takeIf { it != ABSENT } ?: "0,0"
        }
        if (!applyOps(listOf(Op.Put("global", RESTRICTED_PERF, target)))) {
            return "CPU 속도 제한을 바꾸지 못했어요."
        }
        val now = readDirect("global", RESTRICTED_PERF)?.replace(" ", "")
        val applied = when {
            now == null -> null
            on -> now == CPU_LIMIT_ON
            else -> now != CPU_LIMIT_ON
        }
        return when (applied) {
            null -> "CPU 속도 70% 제한을 보냈어요. 설정 > 배터리에서 확인해 주세요."
            false -> "이 기기에서는 CPU 제한이 반영되지 않았어요."
            true -> {
                if (!on) prefs?.edit()?.remove(KEY_PERF_BACKUP)?.apply()
                if (on) "CPU 속도 70% 제한 켜짐" else "CPU 속도 70% 제한 꺼짐"
            }
        }
    }

    private suspend fun backupPowerSaveState() {        val store = prefs ?: return
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
        KeySpec("secure", EXTRA_DIM_LEVEL, "extra_dim_level"),
        KeySpec("global", PROTECT_MODE, "protect"),
        KeySpec("global", PROTECT_THRESHOLD, "threshold"),
        KeySpec("global", "adaptive_battery_management_enabled", "adaptive"),
        KeySpec("global", BATTERY_SAVER_CONSTANTS, "bsaver")
    )

    suspend fun readExtras(): Loaded<ExtrasInfo> {
        val values = readValues(extrasKeys)
        val autoSync = autoSyncEnabled()
        if (values.isEmpty() && autoSync == null && !canWriteSettings()) {
            return Loaded(null, needsShizuku())
        }
        return Loaded(
            ExtrasInfo(
                shutter = values["shutter"],
                windowScale = values["window"],
                transitionScale = values["transition"],
                animatorScale = values["animator"],
                extraDim = values["extra_dim"],
                extraDimLevel = values["extra_dim_level"],
                protectMode = values["protect"],
                protectThreshold = values["threshold"],
                adaptiveBattery = values["adaptive"],
                autoSync = autoSync,
                batterySaverConstants = values["bsaver"]
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
            3 -> "배터리 보호 기본 모드 적용"
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

    /**
     * 잠자기 모드 (Quick-Doze).
     *
     * `device_idle_constants` is deprecated from Android 12 on and recent One UI builds no longer
     * store it, so the switch works through `battery_saver_constants`' `quick_doze_enabled` flag and
     * the small resident service in [QuickDoze] that turns battery saver on while the screen is off
     * and puts it back when the screen comes on. Everything is a plain Settings.Global write, so the
     * one-time WRITE_SECURE_SETTINGS grant is all it needs.
     */
    suspend fun setQuickDoze(on: Boolean): String {
        val context = AppCtx.context ?: return "앱 정보를 읽지 못했어요."
        if (on) {
            if (!Access.secureWrite) {
                return "잠자기 모드는 설정 권한(1회)이 있어야 켤 수 있어요. 기타 탭에서 설정 권한을 받은 뒤 다시 켜 주세요."
            }
            QuickDoze.enable(context, canWrite = true)?.let { return it }
            return if (QuickDoze.hasQuickDoze(readDirect("global", BATTERY_SAVER_CONSTANTS))) {
                "잠자기 모드 켜짐 · 화면이 꺼지면 절전을 잠깐 켜서 바로 잠자기로 들어가요"
            } else {
                "이 기기에서는 잠자기 모드 설정이 반영되지 않았어요."
            }
        }
        QuickDoze.disable(context, canWrite = Access.secureWrite)?.let { return it }
        return if (QuickDoze.hasQuickDoze(readDirect("global", BATTERY_SAVER_CONSTANTS))) {
            "잠자기 모드를 껐지만 기기 설정에 아직 남아 있어요. 한 번 더 눌러 주세요."
        } else {
            "잠자기 모드 꺼짐 · 기기 기본 대기 간격으로 돌아왔어요"
        }
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun runChange(command: String, label: String): String {
        if (!Shell.ready) return needsShizuku()
        return when (val r = Shell.exec(command)) {
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
