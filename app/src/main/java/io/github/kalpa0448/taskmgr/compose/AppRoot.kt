package io.github.kalpa0448.taskmgr.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kalpa0448.taskmgr.ProcCollector
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AppRoot(theme: ThemeState) {
    var tab by remember { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<ProcCollector.Snapshot?>(null) }
    var rootOk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootOk = ProcCollector.hasRoot()
        while (true) {
            val s = runCatching { ProcCollector.collect() }.getOrNull()
            if (s != null) snapshot = s
            delay(1500)
        }
    }

    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val winW = with(dens) { cfg.screenWidthDp.dp.roundToPx() }
    val winH = with(dens) { cfg.screenHeightDp.dp.roundToPx() }

    Box(Modifier.fillMaxSize().background(theme.bgColor)) {
        // 背景图（清晰）
        theme.bg?.let { b ->
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = b,
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(b.width, b.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    dstSize = androidx.compose.ui.unit.IntSize(winW, winH),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                )
            }
        }
        // 暗度蒙版
        if (theme.scrim > 0f) {
            Box(Modifier.fillMaxSize().background(theme.scrimColor()))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(theme, rootOk)
            Tabs(theme, tab) { tab = it }
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                when (tab) {
                    0 -> OverviewScreen(theme, snapshot)
                    1 -> ProcessScreen(theme, snapshot)
                    2 -> DetailsScreen(theme, snapshot)
                    else -> SettingsScreen(theme)
                }
            }
        }
    }
}

@Composable
private fun Header(theme: ThemeState, rootOk: Boolean) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
        Text("任务管理器", color = theme.textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50))
                .background(if (rootOk) theme.memColor else theme.warnColor))
            Spacer(Modifier.width(6.dp))
            Text(
                if (rootOk) "root 已授权（完整模式）" else "无 root（受限模式）",
                color = if (rootOk) theme.memColor else theme.warnColor, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Tabs(theme: ThemeState, current: Int, onChange: (Int) -> Unit) {
    val names = listOf("概览", "进程", "详情", "设置")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        names.forEachIndexed { i, n ->
            val sel = i == current
            Box(
                Modifier.weight(1f).height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) theme.accent else theme.cardTint().copy(alpha = 0.35f))
                    .clickable { onChange(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(n, color = if (sel) Color.Black else theme.textColor, fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/* ---------------- 概览 ---------------- */


@Composable
private fun winSize(): Pair<Int, Int> {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    return with(dens) {
        cfg.screenWidthDp.dp.roundToPx() to cfg.screenHeightDp.dp.roundToPx()
    }
}

@Composable
private fun OverviewScreen(theme: ThemeState, s: ProcCollector.Snapshot?) {
    val (winW, winH) = winSize()
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在采集…", color = theme.subColor)
        }
        return
    }
    val m = s.mem
    val memPct = if (m.memTotal > 0) m.used() * 100.0 / m.memTotal else 0.0

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(theme.bgBlur, theme.cardTint(), 20.dp, Modifier.fillMaxWidth(), dstW = winW, dstH = winH) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(format("%.1f%%", s.cpuPercent), color = theme.accent,
                            fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Pill(theme, "详情")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (s.cpuFromFreq) "由 CPU 频率推算（无 root）"
                        else "${s.cores} 核 · 负载 ${format("%.2f / %.2f / %.2f", s.load1, s.load5, s.load15)}",
                        color = theme.subColor, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Bar(theme, s.cpuPercent / 100.0, theme.accent)
                    Spacer(Modifier.height(12.dp))
                    val th = ProcCollector.cpuThermal(s)
                    if (th != null) {
                        Text("温度 ${format("%.1f", th.celsius)}°C", color = theme.subColor, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            GlassCard(theme.bgBlur, theme.cardTint(), 20.dp, Modifier.fillMaxWidth(), dstW = winW, dstH = winH) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "${format("%.1f", m.used() / 1073741824.0)} / ${format("%.1f", m.memTotal / 1073741824.0)} GB",
                        color = theme.memColor, fontSize = 26.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Bar(theme, memPct / 100.0, theme.memColor)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "可用 ${format("%.1f", m.memAvail / 1073741824.0)} GB · 占用 ${format("%.0f", memPct)}%",
                        color = theme.subColor, fontSize = 12.sp
                    )
                    if (m.swapTotal > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Swap ${format("%.1f", m.swapUsed() / 1073741824.0)} / ${format("%.1f", m.swapTotal / 1073741824.0)} GB",
                            color = theme.swapColor, fontSize = 12.sp
                        )
                    }
                }
            }
        }
        item {
            GlassCard(theme.bgBlur, theme.cardTint(), 20.dp, Modifier.fillMaxWidth(), dstW = winW, dstH = winH) {
                Column(Modifier.padding(18.dp)) {
                    Text("设备信息", color = theme.subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    InfoLine(theme, "型号", android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")")
                    InfoLine(theme, "内核", System.getProperty("os.version") ?: "?")
                    InfoLine(theme, "开机时长", format("%.1f 小时", s.uptime / 3600.0))
                    InfoLine(theme, "进程数", "${s.procs.size}")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ---------------- 进程 ---------------- */

@Composable
private fun ProcessScreen(theme: ThemeState, s: ProcCollector.Snapshot?) {
    val (winW, winH) = winSize()
    if (s == null) return
    val procs = s.procs.filter { it.name.isNotEmpty() }.take(60)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            GlassRow(theme.bgBlur, theme.cardTint().copy(alpha = 0.35f), dstW = winW, dstH = winH,
                modifier = Modifier.padding(bottom = 4.dp)) {
                Text("  共 ${s.procs.size} 个进程", color = theme.subColor,
                    fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
        items(procs) { p ->
            GlassRow(theme.bgBlur, theme.cardTint(), dstW = winW, dstH = winH) {
                Column(Modifier.weight(1f).padding(12.dp)) {
                    Text(p.name, color = theme.textColor, fontSize = 14.sp)
                    Text("PID ${p.pid} · ${ProcCollector.userName(p.uid)}",
                        color = theme.subColor, fontSize = 11.sp)
                }
                Text(format("%.1f%%", p.cpu), color = theme.accent, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                Text("${p.rssBytes / 1048576}M", color = theme.subColor, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/* ---------------- 详情 ---------------- */

@Composable
private fun DetailsScreen(theme: ThemeState, s: ProcCollector.Snapshot?) {
    val (winW, winH) = winSize()
    if (s == null) return
    val procs = s.procs.sortedByDescending { it.cpu }.take(40)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            GlassRow(theme.bgBlur, theme.cardTint().copy(alpha = 0.45f), dstW = winW, dstH = winH) {
                Text("名称", color = theme.subColor, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).padding(10.dp))
                Text("PID", color = theme.subColor, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                Text("CPU", color = theme.subColor, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                Text("内存", color = theme.subColor, fontSize = 12.sp,
                    modifier = Modifier.width(60.dp).padding(end = 6.dp))
            }
        }
        items(procs) { p ->
            GlassRow(theme.bgBlur, theme.cardTint(alt = true), dstW = winW, dstH = winH) {
                Text(p.name, color = theme.textColor, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                Text("${p.pid}", color = theme.subColor, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                Text(format("%.1f", p.cpu), color = theme.accent, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                Text("${p.rssBytes / 1048576}", color = theme.subColor, fontSize = 11.sp,
                    modifier = Modifier.width(60.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/* ---------------- 设置 ---------------- */

@Composable
private fun SettingsScreen(theme: ThemeState) {
    val (winW, winH) = winSize()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle(theme, "外观") }
        item { SettingRow(theme, "主题配色", "默认") }
        item { SettingRow(theme, "明暗", if (theme.dark) "深色" else "浅色") }
        item { SettingRow(theme, "图片背景", if (theme.bg != null) "已设置" else "未设置") }
        item { SectionTitle(theme, "通用") }
        item { SettingRow(theme, "语言", "中文") }
        item { SectionTitle(theme, "关于") }
        item { SettingRow(theme, "版本", "1.3.0") }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(theme: ThemeState, text: String) {
    Text(text, color = theme.subColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SettingRow(theme: ThemeState, title: String, value: String) {
    val (winW, winH) = winSize()
    GlassRow(theme.bgBlur, theme.cardTint(), dstW = winW, dstH = winH) {
        Text(title, color = theme.textColor, fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(14.dp))
        Text(value, color = theme.accent, fontSize = 13.sp,
            modifier = Modifier.padding(end = 14.dp))
    }
}

/* ---------------- 小组件 ---------------- */

@Composable
private fun Pill(theme: ThemeState, text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(theme.accent.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) { Text(text, color = theme.accent, fontSize = 12.sp) }
}

@Composable
private fun Bar(theme: ThemeState, ratio: Double, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (theme.dark) Color(0xFF33333A) else Color(0xFFE0E0E8))
    ) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0.0, 1.0).toFloat())
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
private fun InfoLine(theme: ThemeState, k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(k, color = theme.subColor, fontSize = 13.sp, modifier = Modifier.width(76.dp))
        Text(v, color = theme.textColor, fontSize = 13.sp)
    }
}

private fun format(fmt: String, vararg args: Any): String = String.format(Locale.ROOT, fmt, *args)
