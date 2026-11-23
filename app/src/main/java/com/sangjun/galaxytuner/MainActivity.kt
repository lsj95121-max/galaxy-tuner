package com.sangjun.galaxytuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    onSurfaceVariant = Color(0xFFB9AECF),
    error = Color(0xFFFF8A80)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuShell.attach(applicationContext)
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

    var battery by remember { mutableStateOf<Loaded<BatteryInfo>?>(null) }
    var refresh by remember { mutableStateOf<Loaded<RefreshInfo>?>(null) }
    var shutter by remember { mutableStateOf<Loaded<String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun loadAll() {
        loading = true
        battery = Tuner.readBattery()
        refresh = Tuner.readRefresh()
        shutter = Tuner.readShutter()
        loading = false
    }

    LaunchedEffect(shizuku.ready) {
        if (shizuku.ready && battery == null) {
            loadAll()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Galaxy Tuner",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Shizuku(무선 디버깅) 권한으로 갤럭시 설정을 조정합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ShizukuCard(
                state = shizuku,
                onRequest = { ShizukuShell.requestPermission() },
                onRefresh = {
                    ShizukuShell.refresh()
                    scope.launch {
                        if (ShizukuShell.state.permissionGranted) loadAll()
                    }
                }
            )

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("읽는 중…", style = MaterialTheme.typography.bodySmall)
                }
            }

            BatteryCard(
                loaded = battery,
                showRaw = showRaw,
                onToggleRaw = { showRaw = !showRaw },
                onReload = { scope.launch { battery = Tuner.readBattery() } }
            )

            RefreshCard(
                loaded = refresh,
                busy = busy,
                onMode = { mode ->
                    scope.launch {
                        busy = true
                        val result = Tuner.applyRefreshMode(mode)
                        refresh = Tuner.readRefresh()
                        message = result
                        busy = false
                    }
                },
                onMin = { hz ->
                    scope.launch {
                        busy = true
                        val result = Tuner.setMinRefreshRate(hz)
                        refresh = Tuner.readRefresh()
                        message = result
                        busy = false
                    }
                },
                onPeak = { hz ->
                    scope.launch {
                        busy = true
                        val result = Tuner.setPeakRefreshRate(hz)
                        refresh = Tuner.readRefresh()
                        message = result
                        busy = false
                    }
                },
                onFixed = { hz ->
                    scope.launch {
                        busy = true
                        val result = Tuner.setFixedRefreshRate(hz)
                        refresh = Tuner.readRefresh()
                        message = result
                        busy = false
                    }
                }
            )

            ShutterCard(
                loaded = shutter,
                busy = busy,
                onSet = { value ->
                    scope.launch {
                        busy = true
                        val result = Tuner.applyShutter(value)
                        shutter = Tuner.readShutter()
                        message = result
                        busy = false
                    }
                }
            )

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text(
                text = "값 변경은 Shizuku 셸(uid 2000)로 실행되며, 기기·One UI 버전에 따라 일부 항목이 표시되지 않을 수 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (ok) Color(0xFF1E3B2B) else Color(0xFF3B1E24),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) Color(0xFF7BE39A) else Color(0xFFFF8A80)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Metric(label: String, value: String, accent: Color? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ShizukuCard(
    state: ShizukuState,
    onRequest: () -> Unit,
    onRefresh: () -> Unit
) {
    SectionCard(title = "Shizuku 연결 상태") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = if (state.binderAlive) "Shizuku 실행 중" else "Shizuku 미실행",
                ok = state.binderAlive
            )
            StatusPill(
                text = if (state.permissionGranted) "권한 허용됨" else "권한 필요",
                ok = state.permissionGranted
            )
            StatusPill(
                text = if (state.serviceConnected) "셸 연결됨" else "셸 대기",
                ok = state.serviceConnected
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        InfoRow("실행 상태", if (state.binderAlive) "실행 중" else "미실행")
        InfoRow("권한", if (state.permissionGranted) "허용됨" else "필요")
        InfoRow("셸 서비스", if (state.serviceConnected) "연결됨 (uid 2000)" else "연결 대기")
        if (state.shizukuVersion > 0) {
            InfoRow("Shizuku 버전", "v${state.shizukuVersion}")
        }

        if (!state.binderAlive) {
            Text(
                text = "Shizuku 앱을 설치하고 무선 디버깅으로 서비스를 시작한 뒤 아래 새로고침을 눌러 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRequest,
                enabled = state.binderAlive && !state.permissionGranted
            ) {
                Text("권한 요청")
            }
            OutlinedButton(onClick = onRefresh) {
                Text("새로고침")
            }
        }
    }
}

@Composable
private fun BatteryCard(
    loaded: Loaded<BatteryInfo>?,
    showRaw: Boolean,
    onToggleRaw: () -> Unit,
    onReload: () -> Unit
) {
    SectionCard(title = "배터리 사이클 · 효율 (BSOH)") {
        val info = loaded?.value

        if (info == null) {
            Text(
                text = loaded?.error ?: "아직 읽지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = if (loaded?.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Metric(
                    label = "효율 (BSOH)",
                    value = info.efficiency?.let { "$it%" } ?: "-",
                    accent = MaterialTheme.colorScheme.primary
                )
                Metric(
                    label = "사이클",
                    value = info.cycles?.let { String.format("%.2f회", it) } ?: "-"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Metric(label = "충전량", value = info.level?.let { "$it%" } ?: "-")
                Metric(
                    label = "온도",
                    value = info.temperatureC?.let { String.format("%.1f°C", it) } ?: "-"
                )
                Metric(label = "전압", value = info.voltageMv?.let { "$it mV" } ?: "-")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            InfoRow("충전 상태", info.status ?: "-")
            InfoRow("배터리 상태", info.health ?: "-")
            InfoRow("ASOC", info.asoc?.let { "$it%" } ?: "-")
            InfoRow("BSOH (dumpsys)", info.bsoh?.let { "$it%" } ?: "-")
            InfoRow("최초 사용일", info.firstUseDate ?: "-")
            InfoRow("원본 Usage", info.usageRaw?.let { "$it (÷100 회)" } ?: "없음 (dumpsys)")
            info.cycleCountSys?.let { InfoRow("sysfs cycle", it) }
            info.fgAsocSys?.let { InfoRow("sysfs fg_asoc", it) }
            info.fullChargeMah?.let { InfoRow("충전 용량", "$it mAh") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleRaw) {
                    Text(if (showRaw) "원본 dumpsys 접기" else "원본 dumpsys 보기")
                }
                TextButton(onClick = onReload) {
                    Text("다시 읽기")
                }
            }

            if (showRaw) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = info.raw,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        if (info == null && loaded?.error == null) {
            TextButton(onClick = onReload) { Text("읽기") }
        }
    }
}

@Composable
private fun RefreshCard(
    loaded: Loaded<RefreshInfo>?,
    busy: Boolean,
    onMode: (Int) -> Unit,
    onMin: (Int) -> Unit,
    onPeak: (Int) -> Unit,
    onFixed: (Int) -> Unit
) {
    SectionCard(title = "화면 주사율") {
        val info = loaded?.value
        val currentMin = rateOf(info?.min)
        val currentPeak = rateOf(info?.peak)

        if (info == null) {
            Text(
                text = loaded?.error ?: "아직 읽지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = if (loaded?.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            InfoRow("현재 모드", "${info.modeLabel}")
            InfoRow("refresh_rate_mode", info.mode ?: "null")
            InfoRow("min_refresh_rate", info.min ?: "null")
            InfoRow("peak_refresh_rate", info.peak ?: "null")
        }

        RateSection(
            title = "최소 주사율 (min_refresh_rate)",
            isSelected = { hz -> rateMatches(currentMin, hz) },
            busy = busy,
            onPick = onMin
        )
        RateSection(
            title = "최대 주사율 (peak_refresh_rate)",
            isSelected = { hz -> rateMatches(currentPeak, hz) },
            busy = busy,
            onPick = onPeak
        )
        RateSection(
            title = "고정 주사율 (min = peak)",
            isSelected = { hz -> rateMatches(currentMin, hz) && rateMatches(currentPeak, hz) },
            busy = busy,
            onPick = onFixed
        )
        Text(
            text = "0Hz는 해당 설정을 지워 One UI 기본값으로 되돌립니다. 최소/최대만 바꿔도 적응형" +
                "(refresh_rate_mode=2)으로 전환되며, 고정 주사율은 60이면 mode 0, 60 초과면 mode 1," +
                " 그 미만은 min·peak를 같게 맞춘 적응형으로 적용됩니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("모드 전환", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onMode(0) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
            ) { Text("60 고정") }
            OutlinedButton(
                onClick = { onMode(2) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
            ) { Text("120 적응") }
            OutlinedButton(
                onClick = { onMode(1) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
            ) { Text("120 고정") }
        }
    }
}

/** The eight selectable rates, in the order the brief asks for. 0 = 설정 해제(기본값). */
private val RATE_STEPS = listOf(0, 10, 24, 30, 48, 60, 80, 120)

/** `60.0` / `60` / null(=설정 없음) -> 60; unreadable values are treated as 없음. */
private fun rateOf(raw: String?): Int? = raw?.trim()?.toDoubleOrNull()?.roundToInt()

/** A rate is shown as selected when the stored value matches it; 0 also matches "unset". */
private fun rateMatches(current: Int?, hz: Int): Boolean =
    if (hz <= 0) current == null || current <= 0 else current == hz

@Composable
private fun RateSection(
    title: String,
    isSelected: (Int) -> Boolean,
    busy: Boolean,
    onPick: (Int) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    RATE_STEPS.chunked(4).forEach { line ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            line.forEach { hz ->
                RateButton(
                    hz = hz,
                    selected = isSelected(hz),
                    enabled = !busy,
                    onClick = { onPick(hz) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RateButton(
    hz: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = "$hz"
    val padding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = padding) {
            Text(label, fontSize = 13.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = padding) {
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShutterCard(
    loaded: Loaded<String>?,
    busy: Boolean,
    onSet: (Int) -> Unit
) {
    SectionCard(title = "카메라 셔터음") {
        val value = loaded?.value
        val isForced = value == "1"
        val label = when (value) {
            "0" -> "꺼짐 (0)"
            "1" -> "강제 켜짐 (1)"
            "null", null -> "설정 없음 (기본값)"
            else -> value
        }

        if (loaded?.error != null) {
            Text(
                text = loaded.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(200.dp)) {
                Text("강제 셔터음", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "현재: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isForced,
                enabled = !busy,
                onCheckedChange = { checked -> onSet(if (checked) 1 else 0) }
            )
        }

        InfoRow("csc_pref...key", value ?: "null")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSet(0) }, enabled = !busy) { Text("셔터음 끄기 (0)") }
            OutlinedButton(onClick = { onSet(1) }, enabled = !busy) { Text("강제 셔터음 (1)") }
        }

        Text(
            text = "지역/통신사 펌웨어에 따라 카메라 앱이 이 설정을 무시할 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
