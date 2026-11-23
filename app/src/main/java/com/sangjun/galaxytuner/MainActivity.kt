package com.sangjun.galaxytuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB388FF),
    onPrimary = Color(0xFF1B1330),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color(0xFF00263A),
    background = Color(0xFF0E0B18),
    onBackground = Color(0xFFEDE7F6),
    surface = Color(0xFF191426),
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = Color(0xFF241C38),
    onSurfaceVariant = Color(0xFFB0A6C6),
    error = Color(0xFFFF8A80)
)

private val OkBg = Color(0xFF1C3A2A)
private val OkFg = Color(0xFF7BE39A)
private val WarnBg = Color(0xFF3A2F1B)
private val WarnFg = Color(0xFFFFCF80)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuShell.attach(applicationContext)
        Tuner.attach(applicationContext)
        setContent {
            MaterialTheme(colorScheme = DarkColors) {
                TunerScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuShell.refresh()
    }
}

@Composable
fun TunerScreen() {
    val scope = rememberCoroutineScope()
    val shizuku = ShizukuShell.state
    val snackbar = remember { SnackbarHostState() }

    var battery by remember { mutableStateOf<Loaded<BatteryInfo>?>(null) }
    var refresh by remember { mutableStateOf<Loaded<RefreshInfo>?>(null) }
    var display by remember { mutableStateOf<Loaded<DisplayInfo>?>(null) }
    var powerSave by remember { mutableStateOf<Loaded<PowerSaveInfo>?>(null) }
    var shutter by remember { mutableStateOf<Loaded<String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        loading = true
        battery = Tuner.readBattery()
        refresh = Tuner.readRefresh()
        display = Tuner.readDisplay()
        powerSave = Tuner.readPowerSave()
        shutter = Tuner.readShutter()
        loading = false
    }

    LaunchedEffect(shizuku.ready) {
        if (shizuku.ready && battery == null) loadAll()
    }

    /** Runs one settings change, refreshes the affected cards and reports the result. */
    fun act(
        reloadDisplay: Boolean = false,
        block: suspend () -> String
    ) {
        if (busy) return
        scope.launch {
            busy = true
            val result = block()
            refresh = Tuner.readRefresh()
            powerSave = Tuner.readPowerSave()
            if (reloadDisplay) display = Tuner.readDisplay()
            busy = false
            snackbar.showSnackbar(result)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(
                version = BuildConfig.VERSION_NAME,
                connected = shizuku.ready,
                onReload = {
                    ShizukuShell.refresh()
                    scope.launch { if (ShizukuShell.state.permissionGranted) loadAll() }
                }
            )

            if (!shizuku.ready) {
                ConnectionCard(
                    state = shizuku,
                    onRequest = { ShizukuShell.requestPermission() },
                    onCheck = {
                        ShizukuShell.refresh()
                        scope.launch { if (ShizukuShell.state.permissionGranted) loadAll() }
                    }
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            BatteryCard(
                loaded = battery,
                onReload = { scope.launch { battery = Tuner.readBattery() } }
            )

            RefreshCard(
                loaded = refresh,
                busy = busy || !shizuku.ready,
                onPick = { target, hz ->
                    act {
                        when (target) {
                            RateTarget.MIN -> Tuner.setMinRefreshRate(hz)
                            RateTarget.PEAK -> Tuner.setPeakRefreshRate(hz)
                            RateTarget.FIXED -> Tuner.setFixedRefreshRate(hz)
                        }
                    }
                }
            )

            DensityCard(
                loaded = display,
                busy = busy || !shizuku.ready,
                onApply = { dp -> act(reloadDisplay = true) { Tuner.setSmallestWidthDp(dp) } },
                onReset = { act(reloadDisplay = true) { Tuner.resetSmallestWidth() } }
            )

            PowerSaveCard(
                loaded = powerSave,
                busy = busy || !shizuku.ready,
                onEnable = { fixed -> act { Tuner.enablePowerSaveHighRefresh(fixed) } },
                onDisable = { act { Tuner.disablePowerSaveHighRefresh() } }
            )

            ShutterCard(
                loaded = shutter,
                busy = busy || !shizuku.ready,
                onSet = { value ->
                    act {
                        val result = Tuner.applyShutter(value)
                        shutter = Tuner.readShutter()
                        result
                    }
                }
            )

            Text(
                text = "값은 Shizuku 권한으로 적용됩니다. 기기와 One UI 버전에 따라 일부 항목은 표시되지 않거나 다르게 동작할 수 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Header(version: String, connected: Boolean, onReload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Galaxy Tuner",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "v$version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "갤럭시 설정을 한 화면에서",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (connected) {
            Box(
                modifier = Modifier
                    .background(OkBg, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("연결됨", style = MaterialTheme.typography.labelSmall, color = OkFg)
            }
            Spacer(Modifier.width(4.dp))
        }
        TextButton(onClick = onReload) { Text("새로고침") }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean, warn: Boolean = false) {
    val bg = when {
        ok -> OkBg
        warn -> WarnBg
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        ok -> OkFg
        warn -> WarnFg
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RowScope.Metric(label: String, value: String, accent: Color? = null) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ConnectionCard(state: ShizukuState, onRequest: () -> Unit, onCheck: () -> Unit) {
    SectionCard(
        title = "Shizuku 연결이 필요합니다",
        subtitle = when {
            !state.binderAlive -> "Shizuku 앱에서 무선 디버깅을 시작해 주세요."
            !state.permissionGranted -> "이 앱에 권한을 허용하면 설정을 읽고 바꿀 수 있습니다."
            else -> "연결을 준비하고 있습니다. 잠시만 기다려 주세요."
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(if (state.binderAlive) "Shizuku 실행 중" else "Shizuku 미실행", ok = state.binderAlive)
            StatusPill(if (state.permissionGranted) "권한 허용됨" else "권한 필요", ok = state.permissionGranted)
            if (state.permissionGranted && !state.serviceConnected) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (!state.binderAlive) {
            Text(
                text = "Shizuku 앱을 열고 '무선 디버깅으로 시작'을 누른 뒤 아래 버튼을 눌러 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.binderAlive && !state.permissionGranted) {
                Button(onClick = onRequest) { Text("권한 허용") }
            }
            OutlinedButton(onClick = onCheck) { Text("다시 확인") }
        }
    }
}

@Composable
private fun BatteryCard(loaded: Loaded<BatteryInfo>?, onReload: () -> Unit) {
    SectionCard(
        title = "배터리",
        subtitle = "사이클과 효율"
    ) {
        val info = loaded?.value
        if (info == null) {
            val error = loaded?.error
            if (error != null) ErrorText(error) else Text(
                text = "아직 읽지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onReload) { Text("읽기") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(
                    label = "효율",
                    value = info.efficiency?.let { "$it%" } ?: "-",
                    accent = MaterialTheme.colorScheme.primary
                )
                Metric(
                    label = "사이클",
                    value = info.cycles?.let { String.format("%.0f회", it) } ?: "-"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(label = "충전량", value = info.level?.let { "$it%" } ?: "-")
                Metric(
                    label = "온도",
                    value = info.temperatureC?.let { String.format("%.1f°C", it) } ?: "-"
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            InfoRow("충전 상태", info.status ?: "-")
            InfoRow("배터리 상태", info.health ?: "-")
            InfoRow("최초 사용일", info.firstUseDate ?: "-")
        }
    }
}

private enum class RateTarget(val label: String) {
    MIN("최소"), PEAK("최대"), FIXED("고정")
}

/** Selectable rates; 0 = 설정 해제(기본값). */
private val RATE_STEPS = listOf(0, 10, 24, 30, 48, 60, 80, 120)

/** A rate is shown as selected when the stored value matches it; 0 also matches "unset". */
private fun rateMatches(current: Int?, hz: Int): Boolean =
    if (hz <= 0) current == null || current <= 0 else current == hz

@Composable
private fun RefreshCard(
    loaded: Loaded<RefreshInfo>?,
    busy: Boolean,
    onPick: (RateTarget, Int) -> Unit
) {
    var target by remember { mutableStateOf(RateTarget.PEAK) }
    val info = loaded?.value
    val currentMin = rateOf(info?.min)
    val currentPeak = rateOf(info?.peak)

    SectionCard(
        title = "화면 주사율",
        subtitle = info?.let { "${it.modeLabel} · 최대 ${currentPeak?.let { hz -> "${hz}Hz" } ?: "기본값"}" }
    ) {
        if (info == null && loaded?.error != null) {
            ErrorText(loaded.error)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RateTarget.entries.forEach { option ->
                    FilterChip(
                        selected = target == option,
                        onClick = { target = option },
                        enabled = !busy,
                        label = { Text(option.label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            val isSelected: (Int) -> Boolean = { hz ->
                when (target) {
                    RateTarget.MIN -> rateMatches(currentMin, hz)
                    RateTarget.PEAK -> rateMatches(currentPeak, hz)
                    RateTarget.FIXED -> rateMatches(currentMin, hz) && rateMatches(currentPeak, hz)
                }
            }

            RATE_STEPS.chunked(4).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    line.forEach { hz ->
                        FilterChip(
                            selected = isSelected(hz),
                            onClick = { onPick(target, hz) },
                            enabled = !busy,
                            label = {
                                Text(if (hz <= 0) "해제" else "$hz", fontSize = 12.sp)
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            Text(
                text = when (target) {
                    RateTarget.MIN -> "화면이 내려갈 수 있는 가장 낮은 주사율입니다."
                    RateTarget.PEAK -> "화면이 올라갈 수 있는 가장 높은 주사율입니다."
                    RateTarget.FIXED -> "선택한 주사율로 고정합니다. 배터리 사용이 늘어납니다."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            InfoRow("지금", "최소 ${currentMin?.let { "${it}Hz" } ?: "기본"} · 최대 ${currentPeak?.let { "${it}Hz" } ?: "기본"}")
        }
    }
}

private val DP_PRESETS = listOf(360, 384, 411, 440, 480, 520)

@Composable
private fun DensityCard(
    loaded: Loaded<DisplayInfo>?,
    busy: Boolean,
    onApply: (Int) -> Unit,
    onReset: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    val info = loaded?.value

    SectionCard(
        title = "최소 너비 (dp)",
        subtitle = "화면에 담기는 내용의 양을 조절합니다"
    ) {
        if (info == null) {
            val error = loaded?.error
            if (error != null) ErrorText(error) else Text(
                text = "아직 읽지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            InfoRow("현재", info.smallestWidthDp?.let { "${it}dp" } ?: "-")
            InfoRow("해상도", if (info.widthPx != null && info.heightPx != null) "${info.widthPx} × ${info.heightPx}" else "-")
            InfoRow("밀도", info.densityDpi?.let { "$it dpi" } ?: "-")

            val isDefault = !info.isCustomized
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isDefault,
                    onClick = onReset,
                    enabled = !busy,
                    label = { Text("기본", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                DP_PRESETS.take(3).forEach { dp ->
                    DensityChip(dp, info.smallestWidthDp == dp && !isDefault, busy) { onApply(dp) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DP_PRESETS.drop(3).forEach { dp ->
                    DensityChip(dp, info.smallestWidthDp == dp && !isDefault, busy) { onApply(dp) }
                }
                Spacer(Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { text -> custom = text.filter { it.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("직접 입력 (dp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = { custom.toIntOrNull()?.let(onApply) },
                    enabled = !busy && custom.toIntOrNull()?.let { it in 200..1000 } == true,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("적용") }
            }

            Text(
                text = "개발자 옵션의 '최소 너비'와 같습니다. 재부팅하면 원래 값으로 돌아갑니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.DensityChip(
    dp: Int,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = !busy,
        label = { Text("$dp", fontSize = 12.sp) },
        modifier = Modifier.weight(1f),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun PowerSaveCard(
    loaded: Loaded<PowerSaveInfo>?,
    busy: Boolean,
    onEnable: (Boolean) -> Unit,
    onDisable: () -> Unit
) {
    val info = loaded?.value
    val applied = info?.overrideApplied == true

    SectionCard(
        title = "절전 모드 + 120Hz",
        subtitle = "절전 모드를 켜도 부드러운 화면을 유지합니다"
    ) {
        if (info == null) {
            val error = loaded?.error
            if (error != null) ErrorText(error) else Text(
                text = "아직 읽지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(if (info.psmOn) "절전 모드 켜짐" else "절전 모드 꺼짐", ok = info.psmOn, warn = !info.psmOn)
                Text(
                    text = when {
                        applied && info.psmOn -> "120Hz 유지 중"
                        applied -> "절전 모드를 켠 뒤 다시 적용해 주세요"
                        info.psmOn -> "60Hz로 제한됨"
                        else -> "적용 안 됨"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("절전 모드에서 120Hz 유지", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "배터리 절약은 그대로, 화면만 120Hz로",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = applied,
                    enabled = !busy,
                    onCheckedChange = { checked ->
                        if (checked) onEnable(false) else onDisable()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }

            if (applied) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onEnable(true) },
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("고정 120Hz로 유지", fontSize = 12.sp) }
                    TextButton(onClick = onDisable, enabled = !busy) { Text("해제", fontSize = 12.sp) }
                }
            }

            Text(
                text = "설정 > 배터리에서 절전 모드를 켠 상태에서 사용하세요. 실제 주사율은 개발자 옵션의 '화면 재생률 표시'로 확인할 수 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShutterCard(loaded: Loaded<String>?, busy: Boolean, onSet: (Int) -> Unit) {
    val value = loaded?.value
    val isForced = value == "1"
    val label = when (value) {
        "0" -> "꺼짐"
        "1" -> "켜짐 (강제)"
        null -> "기본값"
        else -> "알 수 없음"
    }

    SectionCard(title = "카메라 셔터음") {
        if (loaded?.error != null) ErrorText(loaded.error)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("강제 셔터음", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "현재: $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isForced,
                enabled = !busy,
                onCheckedChange = { checked -> onSet(if (checked) 1 else 0) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = "지역과 통신사 설정에 따라 카메라 앱이 이 설정을 무시할 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
