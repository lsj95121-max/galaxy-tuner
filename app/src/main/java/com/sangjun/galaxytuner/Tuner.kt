package com.sangjun.galaxytuner

/** Result of a read operation: either a value or a message explaining why there is none. */
data class Loaded<T>(val value: T?, val error: String? = null)

data class BatteryInfo(
    val asoc: Int?,
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
            "0" -> "60Hz 고정"
            "1" -> "최대 주사율 고정"
            "2" -> "적응형 (가변)"
            null, "null" -> "설정 없음"
            else -> "알 수 없음 ($mode)"
        }
}

object Tuner {

    private const val SETTINGS_MODE = "refresh_rate_mode"
    private const val SETTINGS_MIN = "min_refresh_rate"
    private const val SETTINGS_PEAK = "peak_refresh_rate"
    private const val SHUTTER_KEY = "csc_pref_camera_forced_shuttersound_key"

    private fun field(raw: String, key: String): String? {
        val regex = Regex("(?m)^\\s*" + Regex.escape(key) + ":\\s*(.+?)\\s*$")
        return regex.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
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
            return Loaded(null, "dumpsys battery 출력이 비어 있습니다.")
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
            asoc = field(raw, "mSavedBatteryAsoc")?.toIntOrNull(),
            usageRaw = field(raw, "mSavedBatteryUsage")?.toIntOrNull(),
            level = field(raw, "level")?.toIntOrNull(),
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
        if (info.asoc == null && info.usageRaw == null && info.level == null) {
            return Loaded(null, "삼성 배터리 필드(mSavedBatteryAsoc / mSavedBatteryUsage)를 찾지 못했습니다. 아래 원본 출력을 확인해 주세요.")
        }
        return Loaded(info, null)
    }

    suspend fun readRefresh(): Loaded<RefreshInfo> {
        val cmd = "settings get system $SETTINGS_MODE; " +
            "settings get system $SETTINGS_MIN; " +
            "settings get system $SETTINGS_PEAK"
        val out = when (val r = ShizukuShell.exec(cmd)) {
            is ShellResult.Ok -> r.output
            is ShellResult.Err -> return Loaded(null, r.message)
        }
        if (out.startsWith("ERROR")) return Loaded(null, out.trim())
        val lines = out.lines().map { it.trim() }
        val mode = lines.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val min = lines.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val peak = lines.getOrNull(2)?.takeIf { it.isNotEmpty() }
        return Loaded(RefreshInfo(mode, min, peak), null)
    }

    /** Applies a refresh_rate_mode (0 = 60Hz fixed, 1 = max fixed, 2 = adaptive) with min/peak values. */
    suspend fun applyRefreshMode(mode: Int): String {
        val min: String
        val peak: String
        when (mode) {
            0 -> {
                min = "60.0"
                peak = "60.0"
            }
            1 -> {
                min = "120.0"
                peak = "120.0"
            }
            else -> {
                min = "60.0"
                peak = "120.0"
            }
        }
        val cmd = "settings put system $SETTINGS_MODE $mode; " +
            "settings put system $SETTINGS_MIN $min; " +
            "settings put system $SETTINGS_PEAK $peak"
        return runChange(cmd, "주사율 모드 ${modeLabelFor(mode)} 적용")
    }

    /**
     * Applies one of the Hz presets (60 / 80 / 96 / 120).
     *
     * peak_refresh_rate is the value One UI actually honours, so the preset sets it directly and
     * keeps min_refresh_rate inside it. 60Hz also switches refresh_rate_mode to 0 (fixed 60),
     * the others use adaptive mode 2 so the panel can drop down when the screen is idle.
     */
    suspend fun applyPreset(hz: Int): String {
        val peak = hz.toDouble()
        val min = if (peak < 60.0) peak else 60.0
        val mode = if (hz <= 60) 0 else 2
        val cmd = "settings put system $SETTINGS_MODE $mode; " +
            "settings put system $SETTINGS_MIN ${fmt(min)}; " +
            "settings put system $SETTINGS_PEAK ${fmt(peak)}"
        return runChange(cmd, "주사율 ${hz}Hz 적용")
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    private fun modeLabelFor(mode: Int): String = when (mode) {
        0 -> "60Hz 고정"
        1 -> "최대 주사율 고정"
        else -> "적응형 120Hz"
    }

    suspend fun readShutter(): Loaded<String> {
        val out = when (val r = ShizukuShell.exec("settings get system $SHUTTER_KEY")) {
            is ShellResult.Ok -> r.output.trim()
            is ShellResult.Err -> return Loaded(null, r.message)
        }
        if (out.startsWith("ERROR")) return Loaded(null, out)
        return Loaded(out.takeIf { it.isNotEmpty() }, null)
    }

    suspend fun applyShutter(value: Int): String =
        runChange("settings put system $SHUTTER_KEY $value", if (value == 0) "셔터음 끄기 적용" else "강제 셔터음 적용")

    private suspend fun runChange(command: String, label: String): String {
        return when (val r = ShizukuShell.exec(command)) {
            is ShellResult.Ok ->
                if (r.output.startsWith("ERROR")) "$label 실패: ${r.output.trim()}"
                else "$label 완료"
            is ShellResult.Err -> "$label 실패: ${r.message}"
        }
    }
}
