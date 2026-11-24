package com.sangjun.galaxytuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Color scheme
// onSurfaceVariant was too washed out to read comfortably, so both palettes carry a stronger
// variant color and a slightly darker (light) / lighter (dark) outline.
// ---------------------------------------------------------------------------------------------

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF00214D),
    primaryContainer = Color(0xFF23405F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF9FD3FF),
    onSecondary = Color(0xFF00344E),
    background = Color(0xFF101013),
    onBackground = Color(0xFFEDEEF2),
    surface = Color(0xFF1A1B1F),
    onSurface = Color(0xFFEDEEF2),
    surfaceVariant = Color(0xFF2C2D34),
    onSurfaceVariant = Color(0xFFC7C9D2),
    outline = Color(0xFF4A4C55),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5FD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E4FF),
    onPrimaryContainer = Color(0xFF001A3D),
    secondary = Color(0xFF1E6FC4),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF16171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16171A),
    surfaceVariant = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF45484F),
    outline = Color(0xFFC0C3CC),
    error = Color(0xFFB3261E)
)

/** Nothing below 12sp anywhere in the app. */
private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
)

private val OkBgDark = Color(0xFF1C3A2A)
private val OkFgDark = Color(0xFF8DEBAA)
private val WarnBgDark = Color(0xFF3A2F1B)
private val WarnFgDark = Color(0xFFFFD48F)
private val InfoBgDark = Color(0xFF25344A)
private val InfoFgDark = Color(0xFFC3D7F8)
private val OkBgLight = Color(0xFFDDF3E4)
private val OkFgLight = Color(0xFF0F5A31)
private val WarnBgLight = Color(0xFFFDEFD2)
private val WarnFgLight = Color(0xFF744900)
private val InfoBgLight = Color(0xFFE2EBFA)
private val InfoFgLight = Color(0xFF1A4487)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tuner.attach(applicationContext)
        Access.attach(applicationContext)
        ShizukuShell.attach(applicationContext)
        setContent {
            var theme by remember { mutableStateOf(Tuner.theme()) }
            val dark = when (theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            val colors = if (dark) DarkColors else LightColors
            LaunchedEffect(dark) {
                val background = colors.background.toArgb()
                window.statusBarColor = background
                window.navigationBarColor = background
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            MaterialTheme(colorScheme = colors, typography = AppTypography) {
                TunerApp(
                    theme = theme,
                    onThemeChange = { Tuner.setTheme(it); theme = it }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuShell.refresh()
        Access.refresh()
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    REFRESH("주사율", R.drawable.ic_tab_refresh),
    DISPLAY("디스플레이", R.drawable.ic_tab_display),
    BATTERY("배터리", R.drawable.ic_tab_battery),
    MORE("기타", R.drawable.ic_tab_more)
}

private enum class RateTarget(val label: String) {
    MIN("최소"), PEAK("최대"), FIXED("고정")
}

/** Selectable rates; 0 = 설정 해제(기본값). */
private val RATE_STEPS = listOf(0, 10, 24, 30, 48, 60, 80, 120)

private fun rateMatches(current: Int?, hz: Int): Boolean =
    if (hz <= 0) current == null || current <= 0 else current == hz

private val DP_PRESETS = listOf(360, 384, 411, 440, 480, 520)
private val ANIM_PRESETS = listOf(0.25, 0.5, 0.75, 1.0)
private val ANIM_TARGETS = listOf("all" to "전체", "window" to "창", "transition" to "전환", "animator" to "애니메이터")

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun TunerApp(theme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    val scope = rememberCoroutineScope()
    val shizuku = ShizukuShell.state
    val secureWrite = Access.secureWrite
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(Tab.REFRESH) }
    var showTheme by remember { mutableStateOf(false) }
    var battery by remember { mutableStateOf<Loaded<BatteryInfo>?>(null) }
    var refresh by remember { mutableStateOf<Loaded<RefreshInfo>?>(null) }
    var display by remember { mutableStateOf<Loaded<DisplayInfo>?>(null) }
    var power by remember { mutableStateOf<Loaded<PowerSaveInfo>?>(null) }
    var extras by remember { mutableStateOf<Loaded<ExtrasInfo>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val canWrite = shizuku.ready || secureWrite

    suspend fun loadAll() {
        loading = true
        battery = Tuner.readBattery()
        refresh = Tuner.readRefresh()
        display = Tuner.readDisplay()
        power = Tuner.readPowerSave()
        extras = Tuner.readExtras()
        loading = false
    }

    LaunchedEffect(shizuku.ready) {
        if (shizuku.ready && !Access.secureWrite) {
            Access.grant()
            Access.refresh()
        }
    }

    LaunchedEffect(shizuku.ready, secureWrite) {
        if ((shizuku.ready || secureWrite) && battery == null) loadAll()
    }

    /** Runs one change, refreshes what it touched, and reports the result. */
    fun act(
        reloadDisplay: Boolean = false,
        block: suspend () -> String
    ) {
        if (busy) return
        scope.launch {
            busy = true
            val message = try {
                block()
            } catch (t: Throwable) {
                "실행하지 못했어요."
            }
            refresh = Tuner.readRefresh()
            power = Tuner.readPowerSave()
            extras = Tuner.readExtras()
            if (reloadDisplay) display = Tuner.readDisplay()
            busy = false
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            PillNavBar(selected = tab, onSelect = { tab = it })
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
        ) {
            AppHeader(onThemeClick = { showTheme = true })

            if (!shizuku.ready) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                ) {
                    if (secureWrite) {
                        StatusPill(
                            text = "설정 권한 있음 · 배터리 정보만 Shizuku 필요",
                            tone = Tone.INFO
                        )
                    } else {
                        StatusPill(
                            text = "Shizuku 미연결 · 일부 기능만 사용 가능",
                            tone = Tone.WARN
                        )
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    Tab.REFRESH -> RefreshTab(
                        refresh = refresh,
                        power = power,
                        canWrite = canWrite,
                        busy = busy,
                        onPickRate = { target, hz ->
                            act {
                                when (target) {
                                    RateTarget.MIN -> Tuner.setMinRefreshRate(hz)
                                    RateTarget.PEAK -> Tuner.setPeakRefreshRate(hz)
                                    RateTarget.FIXED -> Tuner.setFixedRefreshRate(hz)
                                }
                            }
                        },
                        onPowerSave = { fixed -> act { Tuner.enablePowerSaveHighRefresh(fixed) } },
                        onDisablePowerSave = { act { Tuner.disablePowerSaveHighRefresh() } },
                        onCpuLimit = { on -> act { Tuner.setCpuLimit(on) } }
                    )

                    Tab.DISPLAY -> DisplayTab(
                        display = display,
                        extras = extras,
                        busy = busy,
                        onResolution = { percent -> act(reloadDisplay = true) { Tuner.applyResolution(percent) } },
                        onDp = { dp -> act(reloadDisplay = true) { Tuner.setSmallestWidthDp(dp) } },
                        onResetDp = { act(reloadDisplay = true) { Tuner.resetSmallestWidth() } },
                        onAnimation = { which, scale -> act { Tuner.setAnimationScale(which, scale) } },
                        onExtraDim = { on -> act { Tuner.setExtraDim(on) } },
                        onExtraDimLevel = { percent -> act { Tuner.setExtraDimLevel(percent) } }
                    )

                    Tab.BATTERY -> BatteryTab(
                        battery = battery,
                        extras = extras,
                        ready = shizuku.ready,
                        busy = busy,
                        onProtect = { mode, threshold ->
                            act { Tuner.setBatteryProtection(mode, threshold) }
                        },
                        onAdaptive = { on -> act { Tuner.setAdaptiveBattery(on) } },
                        onDoze = { on -> act { Tuner.setQuickDoze(on) } },
                        onAutoSync = { on -> act { Tuner.setAutoSync(on) } },
                        onReloadBattery = { scope.launch { battery = Tuner.readBattery() } }
                    )

                    Tab.MORE -> MoreTab(
                        extras = extras,
                        shizuku = shizuku,
                        secureWrite = secureWrite,
                        busy = busy,
                        onShutter = { value -> act { Tuner.applyShutter(value) } },
                        onGrant = {
                            scope.launch {
                                busy = true
                                val message = Access.grant()
                                Access.refresh()
                                busy = false
                                snackbar.showSnackbar(message)
                            }
                        },
                        onCheck = {
                            ShizukuShell.refresh()
                            Access.refresh()
                            scope.launch {
                                loadAll()
                                snackbar.showSnackbar("상태를 다시 확인했어요.")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("앱 테마") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeedBadge(Need.NONE)
                    AppTheme.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onThemeChange(option); showTheme = false }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = theme == option,
                                onClick = { onThemeChange(option); showTheme = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (theme == option) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTheme = false }) { Text("닫기") }
            }
        )
    }
}

@Composable
private fun AppHeader(onThemeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = Tuner.deviceModel(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = listOfNotNull(Tuner.oneUiVersion(), Tuner.androidVersion()).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onThemeClick) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "앱 테마",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Floating pill navigation
// ---------------------------------------------------------------------------------------------

/**
 * The bottom navigation: one floating capsule with side and bottom margins, a soft shadow and a
 * thin border, and the selected tab filled with a pill of its own so the choice is unmistakable.
 */
@Composable
private fun PillNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Tab.entries.forEach { entry ->
                PillNavItem(
                    tab = entry,
                    selected = selected == entry,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}

@Composable
private fun PillNavItem(
    tab: Tab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = tab.label,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------------------------

/**
 * What a feature needs before it can run, shown as a badge on its card and row.
 *
 * ALWAYS  - the shell (dumpsys / wm / sysfs) is used, so Shizuku has to be connected every time.
 * MAYBE   - the app tries the call itself first and falls back to the shell; some devices need it.
 * ONCE    - a plain Settings write. WRITE_SECURE_SETTINGS (granted once through Shizuku) is
 *           enough afterwards, so Shizuku can be closed.
 * NONE    - a public API or a local preference; no permission of any kind.
 */
private enum class Need { ALWAYS, MAYBE, ONCE, NONE }

private fun needLabel(need: Need): String = when (need) {
    Need.ALWAYS -> "Shizuku 연결 필요"
    Need.MAYBE -> "기기에 따라 Shizuku"
    Need.ONCE -> "Shizuku 1회 설정"
    Need.NONE -> "권한 없이 동작"
}

@Composable
private fun NeedBadge(need: Need) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = when (need) {
        Need.ALWAYS -> if (dark) WarnBgDark else WarnBgLight
        Need.MAYBE -> if (dark) WarnBgDark else WarnBgLight
        Need.ONCE -> if (dark) InfoBgDark else InfoBgLight
        Need.NONE -> if (dark) OkBgDark else OkBgLight
    }
    val fg = when (need) {
        Need.ALWAYS -> if (dark) WarnFgDark else WarnFgLight
        Need.MAYBE -> if (dark) WarnFgDark else WarnFgLight
        Need.ONCE -> if (dark) InfoFgDark else InfoFgLight
        Need.NONE -> if (dark) OkFgDark else OkFgLight
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = needLabel(need),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One line that says why the control above is not usable right now. */
@Composable
private fun NeedNotice(need: Need) {
    val text = when (need) {
        Need.ALWAYS -> "Shizuku를 연결해야 이 기능을 쓸 수 있어요. 기타 탭에서 연결 상태를 확인하세요."
        Need.MAYBE -> "이 기기에서는 Shizuku를 연결해야 동작할 수 있어요."
        Need.ONCE -> "Shizuku를 한 번 연결해 설정 권한을 받으면, 그 뒤엔 Shizuku를 꺼도 동작해요."
        Need.NONE -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TabContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    need: Need? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (need != null) NeedBadge(need)
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
private fun StatusPill(text: String, tone: Tone) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = when (tone) {
        Tone.OK -> if (dark) OkBgDark else OkBgLight
        Tone.WARN -> if (dark) WarnBgDark else WarnBgLight
        Tone.INFO -> if (dark) InfoBgDark else InfoBgLight
        Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (tone) {
        Tone.OK -> if (dark) OkFgDark else OkFgLight
        Tone.WARN -> if (dark) WarnFgDark else WarnFgLight
        Tone.INFO -> if (dark) InfoFgDark else InfoFgLight
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

private enum class Tone { OK, WARN, INFO, NEUTRAL }

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
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RowScope.Metric(label: String, value: String, accent: Color? = null) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    need: Need? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (need != null) NeedBadge(need)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

private data class Choice<T>(val value: T, val label: String, val sub: String? = null)

/**
 * The one selectable box used everywhere: same 12dp corners, same 44/60dp height, same border and
 * the same selected fill, so 주사율·디스플레이·배터리·기타 all look like one app. Labels never
 * wrap onto a second line; a long label is shortened with an ellipsis instead of breaking.
 */
@Composable
private fun RowScope.ChoiceBox(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    height: Dp,
    sub: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val container = when {
        selected -> colors.primary
        enabled -> colors.surfaceVariant
        else -> colors.surface
    }
    val borderColor = when {
        selected -> colors.primary
        enabled -> colors.outline
        else -> colors.outline.copy(alpha = 0.55f)
    }
    val labelColor = when {
        selected -> colors.onPrimary
        enabled -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .height(height)
            .clip(shape)
            .background(container, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (sub != null) {
            Text(
                text = sub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (selected) colors.onPrimary.copy(alpha = 0.9f) else colors.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun <T> ChoiceGrid(
    options: List<Choice<T>>,
    columns: Int,
    isSelected: (T) -> Boolean,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    // One height per grid so a row never mixes a one-line and a two-line box.
    val height = if (options.any { it.sub != null }) 60.dp else 44.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                line.forEach { choice ->
                    ChoiceBox(
                        label = choice.label,
                        sub = choice.sub,
                        height = height,
                        selected = isSelected(choice.value),
                        enabled = enabled,
                        onClick = { onSelect(choice.value) }
                    )
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LoadingHint(loaded: Loaded<*>?, need: Need? = null) {
    val error = loaded?.error
    when {
        error != null -> ErrorText(error)
        loaded == null && need == Need.ALWAYS -> Hint("Shizuku를 연결하면 값을 읽고 바꿀 수 있어요.")
        loaded == null -> Hint("아직 읽지 않았어요.")
        else -> Hint("아직 읽지 않았어요.")
    }
}

// ---------------------------------------------------------------------------------------------
// Tab 1 — 주사율
// ---------------------------------------------------------------------------------------------

@Composable
private fun RefreshTab(
    refresh: Loaded<RefreshInfo>?,
    power: Loaded<PowerSaveInfo>?,
    canWrite: Boolean,
    busy: Boolean,
    onPickRate: (RateTarget, Int) -> Unit,
    onPowerSave: (Boolean) -> Unit,
    onDisablePowerSave: () -> Unit,
    onCpuLimit: (Boolean) -> Unit
) {
    val info = refresh?.value
    val currentMin = info?.minHz
    val currentPeak = info?.peakHz

    TabContent {
        SectionCard(title = "지금 상태", subtitle = info?.modeLabel, need = Need.NONE) {
            if (info == null) {
                LoadingHint(refresh)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(
                        label = "최소",
                        value = currentMin?.let { "${it}Hz" } ?: "기본",
                        accent = MaterialTheme.colorScheme.primary
                    )
                    Metric(label = "최대", value = currentPeak?.let { "${it}Hz" } ?: "기본")
                    Metric(label = "모드", value = info.modeLabel.substringBefore(" ("))
                }
            }
        }

        SectionCard(
            title = "주사율 설정",
            subtitle = "화면이 움직이는 부드러움을 조절해요",
            need = Need.ONCE
        ) {
            if (info == null) {
                LoadingHint(refresh)
            } else {
                var target by remember { mutableStateOf(RateTarget.PEAK) }
                ChoiceGrid(
                    options = RateTarget.entries.map { Choice(it, it.label) },
                    columns = 3,
                    isSelected = { it == target },
                    enabled = !busy,
                    onSelect = { target = it }
                )

                val isSelected: (Int) -> Boolean = { hz ->
                    when (target) {
                        RateTarget.MIN -> rateMatches(currentMin, hz)
                        RateTarget.PEAK -> rateMatches(currentPeak, hz)
                        RateTarget.FIXED -> rateMatches(currentMin, hz) && rateMatches(currentPeak, hz)
                    }
                }

                ChoiceGrid(
                    options = RATE_STEPS.map { hz -> Choice(hz, if (hz <= 0) "해제" else "${hz}Hz") },
                    columns = 4,
                    isSelected = isSelected,
                    enabled = !busy && canWrite,
                    onSelect = { hz -> onPickRate(target, hz) }
                )

                if (!canWrite) {
                    NeedNotice(Need.ONCE)
                } else {
                    Hint(
                        when (target) {
                            RateTarget.MIN -> "화면이 내려갈 수 있는 가장 낮은 주사율이에요."
                            RateTarget.PEAK -> "화면이 올라갈 수 있는 가장 높은 주사율이에요."
                            RateTarget.FIXED -> "선택한 주사율로 고정해요. 배터리 사용이 늘어나요."
                        }
                    )
                }
            }
        }

        PowerSaveCard(
            loaded = power,
            busy = busy || !canWrite,
            canWrite = canWrite,
            onEnable = onPowerSave,
            onDisable = onDisablePowerSave,
            onCpuLimit = onCpuLimit
        )
    }
}

@Composable
private fun PowerSaveCard(
    loaded: Loaded<PowerSaveInfo>?,
    busy: Boolean,
    canWrite: Boolean,
    onEnable: (Boolean) -> Unit,
    onDisable: () -> Unit,
    onCpuLimit: (Boolean) -> Unit
) {
    val info = loaded?.value
    val applied = info?.overrideApplied == true
    val cpuLimited = info?.cpuLimited == true

    SectionCard(
        title = "절전 모드 120Hz · CPU 제한",
        subtitle = "배터리 절약은 그대로, 화면은 부드럽게 · 필요하면 CPU는 70%로",
        need = Need.ONCE
    ) {
        if (info == null) {
            LoadingHint(loaded)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (info.psmOn) "절전 모드 켜짐" else "절전 모드 꺼짐",
                    tone = if (info.psmOn) Tone.OK else Tone.NEUTRAL
                )
                Text(
                    text = when {
                        applied && info.psmOn -> "120Hz 유지 중"
                        applied -> "절전 모드를 켠 뒤 다시 눌러 주세요"
                        info.psmOn -> "60Hz로 제한된 상태"
                        else -> "적용 안 됨"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            SwitchRow(
                title = "절전 모드에서 120Hz 유지",
                subtitle = "가변 주사율로 유지해요",
                checked = applied,
                enabled = !busy,
                onCheckedChange = { checked -> if (checked) onEnable(false) else onDisable() }
            )

            if (applied) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onEnable(true) },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text("고정 120Hz로 유지", fontSize = 13.sp) }
                    TextButton(
                        onClick = onDisable,
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("해제", fontSize = 13.sp) }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SwitchRow(
                title = "CPU 속도 70% 제한 적용",
                subtitle = if (cpuLimited) {
                    "켜짐 · CPU가 70%로 제한돼 있어요"
                } else {
                    "끄면 기본 속도로 돌아가요"
                },
                checked = cpuLimited,
                enabled = !busy,
                onCheckedChange = onCpuLimit
            )

            if (!canWrite) {
                NeedNotice(Need.ONCE)
            } else {
                Hint("120Hz 유지는 설정 > 배터리에서 절전 모드를 켠 상태에서 사용하세요. CPU 제한은 절전 모드와 상관없이 적용되고, 끄면 적용 전 값으로 돌아가요.")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab 2 — 디스플레이
// ---------------------------------------------------------------------------------------------

private fun panelName(width: Int?, height: Int?): String {
    val shortest = listOfNotNull(width, height).minOrNull() ?: return "화면"
    return when {
        shortest >= 1300 -> "QHD+"
        shortest >= 1000 -> "FHD+"
        shortest >= 680 -> "HD+"
        else -> "${shortest}p"
    }
}

private fun evenSize(physical: Int?, percent: Int): Int? {
    val base = physical ?: return null
    return ((base * percent / 100.0).roundToInt() / 2) * 2
}

@Composable
private fun DisplayTab(
    display: Loaded<DisplayInfo>?,
    extras: Loaded<ExtrasInfo>?,
    busy: Boolean,
    onResolution: (Int) -> Unit,
    onDp: (Int) -> Unit,
    onResetDp: () -> Unit,
    onAnimation: (String, Double) -> Unit,
    onExtraDim: (Boolean) -> Unit,
    onExtraDimLevel: (Int) -> Unit
) {
    val info = display?.value

    TabContent {
        SectionCard(
            title = "화면 해상도",
            subtitle = "선명함과 배터리 사용량을 함께 조절해요",
            need = Need.MAYBE
        ) {
            if (info == null) {
                LoadingHint(display)
                NeedNotice(Need.MAYBE)
            } else {
                InfoRow(
                    "현재",
                    "${info.widthPx} × ${info.heightPx} px  ·  ${info.densityDpi} dpi"
                )
                InfoRow("최소 너비", info.smallestWidthDp?.let { "${it}dp" } ?: "-")

                val percent = when {
                    info.matchesDefaultResolution -> 100
                    else -> {
                        val current = info.widthPx
                        val physical = info.physicalWidthPx
                        if (current != null && physical != null && physical > 0) {
                            ((current * 100.0 / physical).roundToInt() / 5 * 5)
                        } else {
                            100
                        }
                    }
                }
                val options = listOf(100, 75, 50).map { pct ->
                    val w = evenSize(info.physicalWidthPx, pct)
                    val h = evenSize(info.physicalHeightPx, pct)
                    Choice(
                        value = pct,
                        label = "${panelName(w, h)} $pct%",
                        sub = if (w != null && h != null) "${w}×${h}" else null
                    )
                }
                ChoiceGrid(
                    options = options,
                    columns = 3,
                    isSelected = { pct -> kotlin.math.abs(pct - percent) <= 3 },
                    enabled = !busy,
                    onSelect = onResolution
                )
                Hint(
                    if (info.isSizeOverridden) {
                        "지금은 ${info.widthPx}×${info.heightPx} 로 줄어 있어요. 최소 너비(dp)는 그대로 유지돼요."
                    } else {
                        "기기 원본 해상도예요. 낮추면 배터리를 아끼고, 화면은 조금 덜 선명해져요."
                    }
                )
            }
        }

        DensityCard(
            loaded = display,
            busy = busy,
            onApply = onDp,
            onReset = onResetDp
        )

        AnimationCard(
            loaded = extras,
            busy = busy,
            onApply = onAnimation
        )

        ExtraDimCard(
            loaded = extras,
            busy = busy,
            onToggle = onExtraDim,
            onLevel = onExtraDimLevel
        )
    }
}

@Composable
private fun ExtraDimCard(
    loaded: Loaded<ExtrasInfo>?,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onLevel: (Int) -> Unit
) {
    val sheet = loaded?.value

    SectionCard(
        title = "추가 어둡게",
        subtitle = "화면을 한 단계 더 어둡게, 밤에 눈이 편해요",
        need = Need.ONCE
    ) {
        if (sheet == null) {
            LoadingHint(loaded)
        } else {
            val on = sheet.extraDim == "1"
            SwitchRow(
                title = "추가 어둡게",
                subtitle = if (on) "켜짐" else "꺼짐",
                checked = on,
                enabled = !busy,
                onCheckedChange = onToggle
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // The slider keeps the value the user is dragging; the remote value re-seeds it after
            // every confirmed change.
            var level by remember(sheet.extraDimPercent) {
                mutableStateOf((sheet.extraDimPercent ?: 50).toFloat())
            }
            val shown = level.roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "강도",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$shown%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = level,
                onValueChange = { level = it.roundToInt().toFloat() },
                valueRange = 0f..100f,
                enabled = !busy,
                onValueChangeFinished = { onLevel(shown) }
            )
            Hint(
                if (!on) {
                    "추가 어둡게를 켜면 이 강도로 적용돼요. 기본값은 50%예요."
                } else {
                    "화면을 밝게 볼 수 없을 만큼 어두울 때는 강도를 낮춰 주세요."
                }
            )
        }
    }
}

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
        subtitle = "화면에 담기는 내용의 양을 조절해요",
        need = Need.MAYBE
    ) {
        if (info == null) {
            LoadingHint(loaded)
            NeedNotice(Need.MAYBE)
        } else {
            val isDefault = !info.isDensityOverridden
            InfoRow("현재", info.smallestWidthDp?.let { "${it}dp" } ?: "-")

            ChoiceGrid(
                options = listOf(Choice(-1, "기본")) + DP_PRESETS.map { Choice(it, "$it") },
                columns = 4,
                isSelected = { dp ->
                    if (dp == -1) isDefault else !isDefault && info.smallestWidthDp == dp
                },
                enabled = !busy,
                onSelect = { dp -> if (dp == -1) onReset() else onApply(dp) }
            )

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
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("직접 입력 (dp)", fontSize = 13.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = { custom.toIntOrNull()?.let(onApply) },
                    enabled = !busy && custom.toIntOrNull()?.let { it in 200..1000 } == true,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("적용", fontSize = 14.sp) }
            }

            Hint("개발자 옵션의 '최소 너비'와 같아요. 재부팅하면 원래 값으로 돌아가요.")
        }
    }
}

@Composable
private fun AnimationCard(
    loaded: Loaded<ExtrasInfo>?,
    busy: Boolean,
    onApply: (String, Double) -> Unit
) {
    val sheet = loaded?.value
    var target by remember { mutableStateOf("all") }

    SectionCard(
        title = "애니메이션 배율",
        subtitle = "화면 전환 속도를 조절해요",
        need = Need.ONCE
    ) {
        if (sheet == null) {
            LoadingHint(loaded)
        } else {
            InfoRow(
                "현재",
                listOfNotNull(
                    sheet.windowScale?.let { "창 ${Tuner.trimScale(it)}x" },
                    sheet.transitionScale?.let { "전환 ${Tuner.trimScale(it)}x" },
                    sheet.animatorScale?.let { "애니메이터 ${Tuner.trimScale(it)}x" }
                ).joinToString(" · ").ifEmpty { "-" }
            )

            ChoiceGrid(
                options = ANIM_TARGETS.map { (key, label) -> Choice(key, label) },
                columns = 2,
                isSelected = { it == target },
                enabled = !busy,
                onSelect = { target = it }
            )

            val current = when (target) {
                "window" -> sheet.windowScale
                "transition" -> sheet.transitionScale
                "animator" -> sheet.animatorScale
                else -> sheet.windowScale
            }
            ChoiceGrid(
                options = ANIM_PRESETS.map { scale ->
                    Choice(
                        scale,
                        "${Tuner.trimScale(scale.toString())}x",
                        if (scale == 1.0) "기본" else null
                    )
                },
                isSelected = { scale -> tunerScaleEquals(current, scale) },
                enabled = !busy,
                columns = 4,
                onSelect = { scale -> onApply(target, scale) }
            )

            // Anything between the presets: 0.45x or 45% both mean 0.45.
            var typed by remember { mutableStateOf("") }
            val parsed = Tuner.parseAnimationScale(typed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { text ->
                        typed = text.filter { it.isDigit() || it == '.' || it == '%' || it == 'x' || it == 'X' }
                            .take(6)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("직접 입력 (0.45 또는 45%)", fontSize = 13.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = typed.isNotEmpty() && parsed == null
                )
                Button(
                    onClick = { parsed?.let { onApply(target, it) } },
                    enabled = !busy && parsed != null,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("적용", fontSize = 14.sp) }
            }

            Hint(
                when {
                    typed.isNotEmpty() && parsed == null -> "0.05에서 10 사이로 입력해 주세요. 45%처럼 퍼센트로도 넣을 수 있어요."
                    parsed != null -> "${Tuner.trimScale(String.format(java.util.Locale.US, "%.2f", parsed))}x 로 바뀌어요. 0.05~10 사이만 받아요."
                    else -> "'전체'를 고르면 세 가지가 한 번에 바뀌어요. 1.0x가 기본 속도예요."
                }
            )
        }
    }
}

private fun tunerScaleEquals(current: String?, scale: Double): Boolean {
    val value = current?.trim()?.toDoubleOrNull() ?: return false
    return kotlin.math.abs(value - scale) < 0.01
}

// ---------------------------------------------------------------------------------------------
// Tab 3 — 배터리
// ---------------------------------------------------------------------------------------------

@Composable
private fun BatteryTab(
    battery: Loaded<BatteryInfo>?,
    extras: Loaded<ExtrasInfo>?,
    ready: Boolean,
    busy: Boolean,
    onProtect: (Int, Int?) -> Unit,
    onAdaptive: (Boolean) -> Unit,
    onDoze: (Boolean) -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onReloadBattery: () -> Unit
) {
    val info = battery?.value
    val sheet = extras?.value

    TabContent {
        SectionCard(
            title = "배터리 건강도",
            subtitle = "사이클과 효율",
            need = Need.ALWAYS
        ) {
            if (info == null) {
                LoadingHint(battery, Need.ALWAYS)
                NeedNotice(Need.ALWAYS)
                if (ready) TextButton(onClick = onReloadBattery) { Text("다시 읽기") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(
                        label = "효율 (BSOH)",
                        value = info.efficiency?.let { "$it%" } ?: "-",
                        accent = MaterialTheme.colorScheme.primary
                    )
                    Metric(label = "사이클", value = info.cycles?.let { String.format("%.0f회", it) } ?: "-")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(label = "충전량", value = info.level?.let { "$it%" } ?: "-")
                    Metric(
                        label = "온도",
                        value = info.temperatureC?.let { String.format("%.1f°C", it) } ?: "-"
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                InfoRow("ASOC", info.asoc?.let { "$it%" } ?: "-")
                InfoRow("충전 상태", info.status ?: "-")
                InfoRow("배터리 상태", info.health ?: "-")
                InfoRow("최초 사용일", info.firstUseDate ?: "-")
            }
        }

        SectionCard(
            title = "배터리 보호",
            subtitle = "충전을 멈춰 배터리 수명을 지켜요",
            need = Need.ONCE
        ) {
            if (sheet == null) {
                LoadingHint(extras)
            } else {
                InfoRow("현재", sheet.protectionLabel)
                ChoiceGrid(
                    options = listOf(
                        Choice(0 to null, "사용 안 함", "제한 없음"),
                        Choice(1 to 80, "최대", "80%"),
                        Choice(1 to 85, "최대", "85%"),
                        Choice(1 to 90, "최대", "90%"),
                        Choice(1 to 95, "최대", "95%"),
                        Choice(3 to null, "기본", "100%")
                    ),
                    isSelected = { option ->
                        val (mode, threshold) = option
                        val currentMode = sheet.protectMode ?: "0"
                        if (mode != 1) {
                            currentMode == mode.toString()
                        } else {
                            currentMode == "1" &&
                                (sheet.protectThreshold?.toIntOrNull() ?: 80) == (threshold ?: 80)
                        }
                    },
                    enabled = !busy,
                    columns = 3,
                    onSelect = { option -> onProtect(option.first, option.second) }
                )
                Hint("'기본'(One UI 8의 '표준')은 100%까지 충전한 뒤 95% 아래로 내려가면 다시 충전해요. One UI 8 이상에서 확인된 동작이라 그 이전 버전에서는 다르게 보일 수 있어요.")
            }
        }

        SectionCard(title = "절전 옵션", subtitle = "백그라운드에서 쓰는 전력을 줄여요") {
            if (sheet == null) {
                LoadingHint(extras)
            } else {
                SwitchRow(
                    title = "적응형 배터리",
                    subtitle = "자주 쓰는 앱을 학습해 전력을 배분해요",
                    checked = sheet.adaptiveBattery != "0",
                    enabled = !busy,
                    need = Need.ONCE,
                    onCheckedChange = onAdaptive
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SwitchRow(
                    title = "잠자기 모드 (빠른 진입)",
                    subtitle = if (sheet.quickDoze) {
                        "켜짐 · 화면이 꺼진 뒤 1분 안에 잠자기로 들어가요"
                    } else {
                        "꺼짐 · 기기 기본 대기 간격을 써요"
                    },
                    checked = sheet.quickDoze,
                    enabled = !busy,
                    need = Need.ONCE,
                    onCheckedChange = onDoze
                )
                Hint("화면이 꺼진 뒤 잠자기 진입 간격을 줄여 대기 전력을 아낍니다. 끄면 기본값으로 돌아갑니다.")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SwitchRow(
                    title = "자동 동기화",
                    subtitle = "계정 메일·연락처를 자동으로 받아와요",
                    checked = sheet.autoSync == true,
                    enabled = !busy && sheet.autoSync != null,
                    need = Need.NONE,
                    onCheckedChange = onAutoSync
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab 4 — 기타
// ---------------------------------------------------------------------------------------------

@Composable
private fun MoreTab(
    extras: Loaded<ExtrasInfo>?,
    shizuku: ShizukuState,
    secureWrite: Boolean,
    busy: Boolean,
    onShutter: (Int) -> Unit,
    onGrant: () -> Unit,
    onCheck: () -> Unit
) {
    val sheet = extras?.value

    TabContent {
        SectionCard(title = "카메라", subtitle = "셔터 소리를 끌 수 있어요", need = Need.ONCE) {
            if (sheet == null) {
                LoadingHint(extras)
            } else {
                val forced = sheet.shutter == "1"
                SwitchRow(
                    title = "강제 셔터음",
                    subtitle = when (sheet.shutter) {
                        "1" -> "켜짐 · 촬영할 때 소리가 나요"
                        "0" -> "꺼짐 · 소리 없이 촬영해요"
                        else -> "기본값"
                    },
                    checked = forced,
                    enabled = !busy,
                    onCheckedChange = { on -> onShutter(if (on) 1 else 0) }
                )
                Hint("통신사·지역 설정에 따라 카메라 앱이 이 설정을 무시할 수 있어요.")
            }
        }

        SectionCard(
            title = "권한과 연결",
            subtitle = "Shizuku는 설정을 바꿀 때만 쓰여요"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (shizuku.binderAlive) "Shizuku 실행 중" else "Shizuku 꺼짐",
                    tone = if (shizuku.binderAlive) Tone.OK else Tone.WARN
                )
                StatusPill(
                    text = if (secureWrite) "설정 권한 있음" else "설정 권한 없음",
                    tone = if (secureWrite) Tone.OK else Tone.WARN
                )
                if (shizuku.permissionGranted && !shizuku.serviceConnected) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!shizuku.binderAlive) {
                Hint("Shizuku 앱을 열고 '무선 디버깅으로 시작'을 누른 뒤 아래 '다시 확인'을 눌러 주세요.")
            } else if (!shizuku.permissionGranted) {
                Hint("Shizuku 권한을 허용하면 설정 권한을 자동으로 받아요.")
                Button(onClick = { ShizukuShell.requestPermission() }, shape = RoundedCornerShape(12.dp)) {
                    Text("권한 허용")
                }
            } else if (!secureWrite) {
                Hint("설정 권한을 한 번만 받으면 주사율·애니메이션·배터리 보호·셔터음은 Shizuku를 꺼도 동작해요.")
                Button(onClick = onGrant, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                    Text("설정 권한 받기")
                }
            } else {
                Hint("설정 권한이 있어요. 설정 변경은 Shizuku 없이 동작하고, 해상도·최소 너비는 기기에 따라, 배터리 정보만 항상 Shizuku 연결이 필요해요.")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) { Text("다시 확인", fontSize = 14.sp) }
            }
        }

        SectionCard(
            title = "기능별 Shizuku 필요 여부",
            subtitle = "기능마다 필요한 연결이 달라요"
        ) {
            NeedGroup(
                need = Need.ALWAYS,
                items = listOf("배터리 건강도 조회 (dumpsys)"),
                note = "Shizuku가 켜져 있어야 할 때마다 동작해요."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            NeedGroup(
                need = Need.MAYBE,
                items = listOf("화면 해상도 변경", "최소 너비(dp) 변경"),
                note = "앱이 숨겨진 IWindowManager로 먼저 시도하고, 기기가 막으면 Shizuku로 시도해요."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            NeedGroup(
                need = Need.ONCE,
                items = listOf(
                    "주사율 최소·최대·고정",
                    "절전 모드에서 120Hz 유지",
                    "CPU 속도 70% 제한",
                    "애니메이션 배율",
                    "추가 어둡게 · 강도",
                    "배터리 보호",
                    "적응형 배터리",
                    "잠자기 모드 (빠른 진입)",
                    "카메라 셔터음 무음"
                ),
                note = "Shizuku로 한 번만 설정 권한을 받으면 그 뒤엔 Shizuku를 꺼도 동작해요."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            NeedGroup(
                need = Need.NONE,
                items = listOf("자동 동기화", "앱 테마", "지금 상태 조회"),
                note = "권한 없이 바로 동작해요."
            )
        }

        Hint("값을 바꾸면 바로 적용돼요. 해상도·최소 너비는 재부팅하면 원래대로 돌아가요.")
        Hint("일부 항목은 기기와 One UI 버전에 따라 제공되지 않을 수 있어요.")
    }
}

@Composable
private fun NeedGroup(need: Need, items: List<String>, note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        NeedBadge(need)
        items.forEach { item ->
            Text(
                text = "· $item",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Hint(note)
    }
}
