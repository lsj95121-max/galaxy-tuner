package com.sangjun.galaxytuner

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.min
import kotlin.math.roundToInt

/** Result of a read operation: either a value or a message explaining why there is none. */
data class Loaded<T>(val value: T?, val error: String? = null)

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
    val chargeFullDesignSys: String?,
    val raw: String
) {
    /** mSavedBatteryUsage is stored in 0.01% steps, so 12345 means 12,345% = 123.45 cycles. */
    val cycles: Double? get() = usageRaw?.let { it / 100.0 }

    /** The headline efficiency: BSOH when One UI reports it, otherwise ASOC. */
    val efficiency: Int? get() = bsoh ?: asoc

    val fullChargeMah: Int?
        get() {
            val full = chargeFullSys?.toLongOrNull() ?: return null
            if (full <= 0L) return null
            // Samsung reports charge_full in uAh on most One UI builds.
            return (if (full > 100_000L) full / 1000L else full).toInt()
        }
}

data class RefreshInfo(
    val mode: String?,
    val min: String?,
    val peak: String?
) {
    val modeLabel: String
        get() = when (mode) {
            "0" -> "표준 (60Hz)"
            "1" -> "최적화 (가변)"
            "2" -> "고정 (최대 주사율)"
            null, "null" -> "기본값"
            else -> "기본값"
        }
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

    /** Smallest width of the untouched panel, useful to tell "custom" from "default". */
    val physicalSmallestWidthDp: Int? get() = dpOf(physicalWidthPx, physicalHeightPx, physicalDensityDpi)

    val isCustomized: Boolean get() = overrideDensityDpi != null || overrideWidthPx != null || overrideHeightPx != null

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
    val peakHz: Int? get() = peak?.trim()?.toDoubleOrNull()?.roundToInt()

    /** True when the "keep 120Hz in power saving" override is written and 120Hz is allowed. */
    val overrideApplied: Boolean
        get() = refreshLimitEnabled == "0" && mode != "0" && (peakHz ?: 0) >= 120
}

object Tuner {

    private const val SETTINGS_MODE = "refresh_rate_mode"
    private const val SETTINGS_MIN = "min_refresh_rate"
    private const val SETTINGS_PEAK = "peak_refresh_rate"
    private const val SHUTTER_KEY = "csc_pref_camera_forced_shuttersound_key"

    // One UI power saving keys (Samsung specific, all in the global table).
    private const val PSM_REFRESH_LIMIT = "pms_settings_refresh_rate_enabled"
    private const val PSM_REFRESH_LIMIT_COVER = "pms_settings_refresh_rate_cover_enabled"
    private const val PSM_REFRESH_TAG = "psm_refresh_rate_tag"
    private const val PSM_REFRESH_TAG_COVER = "psm_refresh_rate_cover_tag"
    private const val PSM_REFRESH_TAG_STATE = "psm_refresh_rate_tag_power_mode_state"
    private const val PSM_MODE_REFRESH = "sem_power_mode_refresh_rate"
    private const val PSM_MODE_REFRESH_COVER = "sem_power_mode_refresh_rate_cover"

    /** refresh_rate_mode values One UI understands (stored in the secure table). */
    private const val MODE_STANDARD_60 = 0  // 표준: 60Hz
    private const val MODE_ADAPTIVE = 1     // 최적화: min ~ peak 가변
    private const val MODE_HIGH = 2         // 고정: peak_refresh_rate 유지

    private var prefs: SharedPreferences? = null

    fun attach(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("galaxy_tuner", Context.MODE_PRIVATE)
    }

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

    /** dumpsys battery + Samsung sysfs nodes (fg_asoc, cycle_count, charge_full...). */
    suspend fun readBattery(): Loaded<BatteryInfo> {
        val dumpsys = ShizukuShell.exec("dumpsys battery")
        val raw = when (dumpsys) {
            is ShellResult.Ok -> dumpsys.output
            is ShellResult.Err -> return Loaded(null, dumpsys.message)
        }
        if (raw.isBlank()) {
            return Loaded(null, "배터리 정보를 읽지 못했습니다.")
        }
        if (raw.startsWith("ERROR")) {
            return Loaded(null, raw.trim())
        }

        val sysfsCmd = buildString {
            append("printf 'cycle_count='; cat /sys/class/power_supply/battery/cycle_count 2>/dev/null; ")
            append("printf '\\nfg_asoc='; cat /sys/class/power_supply/battery/fg_asoc 2>/dev/null; ")
            append("printf '\\ncharge_full='; cat /sys/class/power_supply/battery/charge_full 2>/dev/null; ")
            append("printf '\\ncharge_full_design='; cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null; echo")
        }
        val sysfs = (ShizukuShell.exec(sysfsCmd) as? ShellResult.Ok)?.output ?: ""

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
            chargeFullDesignSys = keyValue(sysfs, "charge_full_design"),
            raw = raw.trimEnd()
        )
        if (info.asoc == null && info.usageRaw == null && info.level == null && info.bsoh == null) {
            return Loaded(null, "이 기기에서는 배터리 사이클 값을 제공하지 않습니다.")
        }
        return Loaded(info, null)
    }

    // ---------------------------------------------------------------------------------------
    // Settings helpers
    // ---------------------------------------------------------------------------------------

    private data class KeySpec(val namespace: String, val key: String, val label: String)

    /**
     * Reads several settings keys with one shell round-trip.
     * Each line comes back as `label=value`; "null" (key not set) is returned as null.
     */
    private suspend fun readValues(specs: List<KeySpec>): Map<String, String>? {
        val command = buildString {
            specs.forEach { spec ->
                append("printf '${spec.label}='; settings get ${spec.namespace} ${spec.key}; ")
            }
        }
        val out = when (val r = ShizukuShell.exec(command)) {
            is ShellResult.Ok -> r.output
            is ShellResult.Err -> return null
        }
        if (out.startsWith("ERROR")) return null
        val map = HashMap<String, String>()
        val regex = Regex("(?m)^([a-z_]+)=(.*)$")
        regex.findAll(out).forEach { match ->
            val value = match.groupValues[2].trim()
            if (value.isNotEmpty() && value != "null") map[match.groupValues[1]] = value
        }
        return map
    }

    // ---------------------------------------------------------------------------------------
    // Refresh rate
    // ---------------------------------------------------------------------------------------

    private val refreshKeys = listOf(
        KeySpec("secure", SETTINGS_MODE, "secure_mode"),
        KeySpec("system", SETTINGS_MODE, "system_mode"),
        KeySpec("system", SETTINGS_MIN, "min"),
        KeySpec("system", SETTINGS_PEAK, "peak")
    )

    suspend fun readRefresh(): Loaded<RefreshInfo> {
        val values = readValues(refreshKeys)
            ?: return Loaded(null, "주사율 정보를 읽지 못했습니다. Shizuku 연결을 확인해 주세요.")
        // Samsung One UI keeps refresh_rate_mode in the secure table; older builds used system.
        val mode = values["secure_mode"] ?: values["system_mode"]
        return Loaded(RefreshInfo(mode, values["min"], values["peak"]), null)
    }

    /**
     * Sets min_refresh_rate alone.
     *
     * 0 Hz means "no override": the key is deleted so the One UI default floor applies again.
     * min/peak are only honoured in the adaptive mode, so the mode is switched to 1 first and the
     * peak is raised when needed so that min never ends up above peak.
     */
    suspend fun setMinRefreshRate(hz: Int): String {
        val current = readRefresh().value
        val peak = rateOf(current?.peak)
        val needsPeak = hz > 0 && peak != null && peak in 1 until hz
        val cmd = buildString {
            append("settings put secure $SETTINGS_MODE $MODE_ADAPTIVE; ")
            append(rateCommand(SETTINGS_MIN, hz))
            if (needsPeak) {
                append("; ")
                append(rateCommand(SETTINGS_PEAK, hz))
            }
        }
        return runChange(cmd, "최소 주사율 ${rateLabel(hz)} 적용")
    }

    /** Sets peak_refresh_rate alone, in adaptive mode. */
    suspend fun setPeakRefreshRate(hz: Int): String {
        val current = readRefresh().value
        val min = rateOf(current?.min)
        val needsMin = hz > 0 && min != null && min > hz
        val cmd = buildString {
            append("settings put secure $SETTINGS_MODE $MODE_ADAPTIVE; ")
            append(rateCommand(SETTINGS_PEAK, hz))
            if (needsMin) {
                append("; ")
                append(rateCommand(SETTINGS_MIN, hz))
            }
        }
        return runChange(cmd, "최대 주사율 ${rateLabel(hz)} 적용")
    }

    /**
     * Pins the panel to one rate by writing the same value to min_refresh_rate and peak_refresh_rate.
     *
     * 60 uses the standard mode (0), 60+ uses the high mode (2), below 60 stays adaptive with
     * min == peak because One UI has no fixed mode there. 0 clears the keys back to default.
     */
    suspend fun setFixedRefreshRate(hz: Int): String {
        val cmd = if (hz <= 0) {
            "settings delete system $SETTINGS_MIN; " +
                "settings delete system $SETTINGS_PEAK; " +
                "settings delete secure $SETTINGS_MODE"
        } else {
            val mode = when {
                hz == 60 -> MODE_STANDARD_60
                hz > 60 -> MODE_HIGH
                else -> MODE_ADAPTIVE
            }
            val value = fmt(hz.toDouble())
            "settings put secure $SETTINGS_MODE $mode; " +
                "settings put system $SETTINGS_MIN $value; " +
                "settings put system $SETTINGS_PEAK $value"
        }
        return runChange(cmd, "고정 주사율 ${rateLabel(hz)} 적용")
    }

    /** Writes one rate key, or deletes it for 0 Hz (= system default). */
    private fun rateCommand(key: String, hz: Int): String =
        if (hz <= 0) "settings delete system $key"
        else "settings put system $key ${fmt(hz.toDouble())}"

    private fun rateLabel(hz: Int): String = if (hz <= 0) "해제" else "${hz}Hz"

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    // ---------------------------------------------------------------------------------------
    // Smallest width (developer options / wm density)
    // ---------------------------------------------------------------------------------------

    suspend fun readDisplay(): Loaded<DisplayInfo> {
        val out = when (val r = ShizukuShell.exec("wm size; echo '#SPLIT#'; wm density")) {
            is ShellResult.Ok -> r.output
            is ShellResult.Err -> return Loaded(null, r.message)
        }
        if (out.startsWith("ERROR")) return Loaded(null, out.trim())
        val parts = out.split("#SPLIT#")
        val sizeText = parts.getOrNull(0) ?: ""
        val densityText = parts.getOrNull(1) ?: ""

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
            return Loaded(null, "화면 크기 정보를 읽지 못했습니다.")
        }
        val info = DisplayInfo(
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
        return Loaded(info, null)
    }

    /**
     * Sets the smallest width the way the developer-options switch does: the density is chosen so
     * that min(screen width, height) * 160 / density equals the requested dp.
     */
    suspend fun setSmallestWidthDp(dp: Int): String {
        if (dp !in 200..1000) return "최소 너비는 200~1000dp 사이로 입력해 주세요."
        val info = readDisplay().value ?: return "화면 정보를 읽지 못해 적용하지 못했습니다."
        val width = info.widthPx ?: return "화면 크기를 읽지 못해 적용하지 못했습니다."
        val height = info.heightPx ?: return "화면 크기를 읽지 못해 적용하지 못했습니다."
        val targetDpi = (min(width, height) * 160.0 / dp).roundToInt().coerceIn(72, 1000)
        return runChange("wm density $targetDpi", "최소 너비 ${dp}dp 적용")
    }

    suspend fun resetSmallestWidth(): String = runChange("wm density reset", "화면 밀도 기본값 복원")

    // ---------------------------------------------------------------------------------------
    // Power saving mode + 120Hz
    // ---------------------------------------------------------------------------------------

    private val powerSaveKeys = listOf(
        KeySpec("global", "low_power", "psm"),
        KeySpec("global", "low_power_mode", "psm_mode"),
        KeySpec("secure", SETTINGS_MODE, "secure_mode"),
        KeySpec("system", SETTINGS_MIN, "min"),
        KeySpec("system", SETTINGS_PEAK, "peak"),
        KeySpec("global", PSM_REFRESH_LIMIT, "limit"),
        KeySpec("global", PSM_REFRESH_TAG, "tag"),
        KeySpec("global", PSM_REFRESH_TAG_STATE, "tag_state")
    )

    suspend fun readPowerSave(): Loaded<PowerSaveInfo> {
        val values = readValues(powerSaveKeys)
            ?: return Loaded(null, "절전 모드 상태를 읽지 못했습니다. Shizuku 연결을 확인해 주세요.")
        val psmOn = values["psm"] == "1" || values["psm_mode"] == "1"
        return Loaded(
            PowerSaveInfo(
                psmOn = psmOn,
                mode = values["secure_mode"],
                min = values["min"],
                peak = values["peak"],
                refreshLimitEnabled = values["limit"]
            ),
            null
        )
    }

    /**
     * One UI switches the panel to 60Hz while power saving is on: its power service writes the
     * refresh policy keys and the display falls back to the standard mode. To keep 120Hz the
     * PSM override keys are cleared, then the motion-smoothness mode is re-applied (0 -> 1) so the
     * display policy recalculates while the battery saver itself stays on.
     */
    suspend fun enablePowerSaveHighRefresh(fixed: Boolean): String {
        backupPowerSaveState()
        val min = if (fixed) "120.0" else "0.0"
        val cmd = buildString {
            append("settings put global $PSM_REFRESH_LIMIT 0; ")
            append("settings put global $PSM_REFRESH_LIMIT_COVER 0; ")
            append("settings put global $PSM_REFRESH_TAG 0; ")
            append("settings put global $PSM_REFRESH_TAG_COVER 0; ")
            append("settings put global $PSM_REFRESH_TAG_STATE 0; ")
            append("settings delete global $PSM_MODE_REFRESH; ")
            append("settings delete global $PSM_MODE_REFRESH_COVER; ")
            append("settings delete global max_fps; settings delete global min_fps; ")
            append("settings put secure $SETTINGS_MODE $MODE_STANDARD_60; ")
            append("sleep 0.2; ")
            append("settings put secure $SETTINGS_MODE $MODE_ADAPTIVE; ")
            append("settings delete secure refresh_rate; ")
            append("settings put secure user_refresh_rate 120; ")
            append("settings put system $SETTINGS_PEAK 120.0; ")
            append("settings put system user_refresh_rate 120; ")
            append("settings put system $SETTINGS_MIN $min; ")
            append("settings delete system display_min_refresh_rate; ")
            append("settings delete system custom_refresh_rate; ")
            append("settings delete system display_refresh_rate")
        }
        val label = if (fixed) "절전 모드 + 고정 120Hz 적용" else "절전 모드 + 120Hz 유지 적용"
        val result = runChange(cmd, label)
        if (result.contains("완료")) {
            val psmOn = readPowerSave().value?.psmOn == true
            if (!psmOn) return "$result · 절전 모드를 켠 뒤 다시 눌러 주세요"
        }
        return result
    }

    /** Puts back the refresh-rate and power-saving values that were in place before the override. */
    suspend fun disablePowerSaveHighRefresh(): String {
        val saved = prefs?.getString(KEY_PSM_BACKUP, null)
        val cmd = if (saved.isNullOrBlank()) {
            "settings delete global $PSM_REFRESH_LIMIT; " +
                "settings delete global $PSM_REFRESH_LIMIT_COVER; " +
                "settings delete global $PSM_REFRESH_TAG; " +
                "settings delete global $PSM_REFRESH_TAG_COVER; " +
                "settings delete global $PSM_REFRESH_TAG_STATE; " +
                "settings delete global $PSM_MODE_REFRESH; " +
                "settings delete global $PSM_MODE_REFRESH_COVER; " +
                "settings put secure $SETTINGS_MODE $MODE_ADAPTIVE"
        } else {
            buildRestoreCommand(saved)
        }
        val result = runChange(cmd, "절전 모드 120Hz 유지 해제")
        if (result.contains("완료")) prefs?.edit()?.remove(KEY_PSM_BACKUP)?.apply()
        return result
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
            KeySpec("global", PSM_MODE_REFRESH_COVER, "mode_refresh_cover"),
            KeySpec("global", "max_fps", "max_fps"),
            KeySpec("global", "min_fps", "min_fps")
        )
        val values = readValues(specs) ?: return
        val lines = specs.map { spec ->
            val value = values[spec.label]
            "${spec.namespace}\t${spec.key}\t${value ?: ABSENT}"
        }
        store.edit().putString(KEY_PSM_BACKUP, lines.joinToString("\n")).apply()
    }

    private fun buildRestoreCommand(saved: String): String = buildString {
        saved.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split("\t")
            if (parts.size == 3) {
                val (namespace, key, value) = parts
                if (value == ABSENT) {
                    append("settings delete $namespace $key; ")
                } else {
                    append("settings put $namespace $key $value; ")
                }
            }
        }
        append("settings put secure $SETTINGS_MODE $MODE_ADAPTIVE")
    }

    // ---------------------------------------------------------------------------------------
    // Camera shutter sound
    // ---------------------------------------------------------------------------------------

    suspend fun readShutter(): Loaded<String> {
        val out = when (val r = ShizukuShell.exec("settings get system $SHUTTER_KEY")) {
            is ShellResult.Ok -> r.output.trim()
            is ShellResult.Err -> return Loaded(null, r.message)
        }
        if (out.startsWith("ERROR")) return Loaded(null, out)
        return Loaded(out.takeIf { it.isNotEmpty() && it != "null" }, null)
    }

    suspend fun applyShutter(value: Int): String =
        runChange("settings put system $SHUTTER_KEY $value", if (value == 0) "셔터음 끄기 적용" else "강제 셔터음 적용")

    // ---------------------------------------------------------------------------------------

    private suspend fun runChange(command: String, label: String): String {
        return when (val r = ShizukuShell.exec(command)) {
            is ShellResult.Ok -> {
                val out = r.output.trim()
                val failed = out.startsWith("ERROR") ||
                    out.startsWith("Error") ||
                    out.contains("Exception") ||
                    out.contains("Permission denial")
                if (failed) "$label 실패: $out" else "$label 완료"
            }
            is ShellResult.Err -> "$label 실패: ${r.message}"
        }
    }

    private const val KEY_PSM_BACKUP = "psm_refresh_backup"
    private const val ABSENT = "__absent__"
}

/** `60.0` / `60` / null(=설정 없음) -> 60; unreadable values are treated as 없음. */
internal fun rateOf(raw: String?): Int? = raw?.trim()?.toDoubleOrNull()?.roundToInt()
