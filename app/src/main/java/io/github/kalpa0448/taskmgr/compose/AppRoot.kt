package io.github.kalpa0448.taskmgr.compose

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import io.github.kalpa0448.taskmgr.CropActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kalpa0448.taskmgr.ProcCollector
import kotlinx.coroutines.delay
import java.util.Locale

/** 根容器实测像素尺寸（含状态栏、导航栏），背景与玻璃卡片共用 */
internal val RootSize = androidx.compose.runtime.staticCompositionLocalOf {
    androidx.compose.ui.unit.IntSize.Zero
}

@Composable
fun AppRoot(theme: ThemeState) {
    var tab by remember { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<ProcCollector.Snapshot?>(null) }
    var rootOk by remember { mutableStateOf(false) }
    var bgPage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootOk = ProcCollector.hasRoot()
        while (true) {
            val s = runCatching { ProcCollector.collect() }.getOrNull()
            if (s != null) snapshot = s
            delay(1500)
        }
    }

    var rootPx by remember { androidx.compose.ui.unit.IntSize.Zero.let { mutableStateOf(it) } }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned {
                rootPx = androidx.compose.ui.unit.IntSize(it.size.width, it.size.height)
            }
            .background(theme.bgColor)
    ) {
      androidx.compose.runtime.CompositionLocalProvider(RootSize provides rootPx) {
        val winW = rootPx.width
        val winH = rootPx.height
        // 背景图（整页模糊开启时直接用模糊图）
        val bgBitmap = if (theme.frostPage) (theme.bgBlur ?: theme.bg) else theme.bg
        bgBitmap?.let { b ->
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

        if (bgPage) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    BgSettingsScreen(theme) { bgPage = false }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(theme, rootOk)
                Tabs(theme, tab) { tab = it }
                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    when (tab) {
                        0 -> OverviewScreen(theme, snapshot)
                        1 -> ProcessScreen(theme, snapshot)
                        2 -> DetailsScreen(theme, snapshot)
                        else -> SettingsScreen(theme) { bgPage = true }
                    }
                }
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
    val r = RootSize.current
    if (r.width > 0 && r.height > 0) return r.width to r.height
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

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }      // 0 全部 1 系统 2 用户
    var sort by remember { mutableStateOf(0) }        // 0 CPU 1 内存 2 名称
    var detail by remember { mutableStateOf<ProcCollector.Proc?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val all = s.procs.filter { it.name.isNotEmpty() }
    val sysCount = all.count { it.isSystem() }
    val userCount = all.size - sysCount

    val shown = remember(all.size, query, filter, sort) {
        all.asSequence()
            .filter {
                when (filter) {
                    1 -> it.isSystem()
                    2 -> !it.isSystem()
                    else -> true
                }
            }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, true) ||
                    it.pid.toString().contains(query)
            }
            .toList()
            .let { list ->
                when (sort) {
                    1 -> list.sortedByDescending { it.rssBytes }
                    2 -> list.sortedBy { it.name.lowercase() }
                    else -> list.sortedByDescending { it.cpu }
                }
            }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // 搜索
        item {
            GlassRow(theme.bgBlur, theme.cardTint().copy(alpha = 0.35f), dstW = winW, dstH = winH) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = theme.textColor, fontSize = 13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                    modifier = Modifier.weight(1f).padding(12.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("搜索进程名 / PID", color = theme.subColor, fontSize = 13.sp)
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    Text("✕", color = theme.subColor, fontSize = 14.sp,
                        modifier = Modifier.clickable { query = "" }.padding(end = 14.dp))
                }
            }
        }

        // 筛选
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)) {
                Chip(theme, "全部(${all.size})", filter == 0) { filter = 0 }
                Chip(theme, "系统($sysCount)", filter == 1) { filter = 1 }
                Chip(theme, "用户($userCount)", filter == 2) { filter = 2 }
            }
        }

        // 排序
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Chip(theme, if (sort == 0) "CPU ↓" else "CPU", sort == 0) { sort = 0 }
                Chip(theme, if (sort == 1) "内存 ↓" else "内存", sort == 1) { sort = 1 }
                Chip(theme, if (sort == 2) "名称 ↑" else "名称", sort == 2) { sort = 2 }
            }
        }

        items(shown, key = { it.pid }) { p ->
            GlassRow(theme.bgBlur, theme.cardTint(), dstW = winW, dstH = winH,
                modifier = Modifier.clickable { detail = p }) {
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                    Text(p.name, color = theme.textColor, fontSize = 14.sp, maxLines = 1)
                    Text("PID ${p.pid} · ${ProcCollector.userName(p.uid)}",
                        color = theme.subColor, fontSize = 11.sp)
                }
                Text(format("%.1f%%", p.cpu), color = theme.accent, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                Text("${p.rssBytes / 1048576}M", color = theme.subColor, fontSize = 12.sp,
                    modifier = Modifier.width(52.dp))
                Text("结束", color = theme.warnColor, fontSize = 12.sp,
                    modifier = Modifier
                        .clickable {
                            val ok = runCatching { ProcCollector.kill(p.pid) }.getOrDefault(false)
                            android.widget.Toast.makeText(
                                ctx,
                                if (ok) "已结束 ${p.name}" else "结束失败（需要 root）",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }

        if (shown.isEmpty()) {
            item {
                Text("没有匹配的进程", color = theme.subColor, fontSize = 13.sp,
                    modifier = Modifier.padding(20.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    // 详情弹窗
    detail?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { detail = null }) {
                    Text("关闭", color = theme.accent)
                }
            },
            title = { Text(p.name, color = theme.textColor, fontSize = 16.sp) },
            text = {
                Column {
                    InfoLine(theme, "PID", "${p.pid}")
                    InfoLine(theme, "父进程 PPID", "${p.ppid}")
                    InfoLine(theme, "UID", "${p.uid} (${ProcCollector.userName(p.uid)})")
                    InfoLine(theme, "状态", p.state)
                    InfoLine(theme, "CPU", format("%.1f%%", p.cpu))
                    InfoLine(theme, "内存 RSS", "${p.rssBytes / 1048576} MB")
                    InfoLine(theme, "虚拟内存", "${p.vsize / 1048576} MB")
                    InfoLine(theme, "线程数", "${p.threads}")
                    InfoLine(theme, "架构", p.arch)
                }
            },
            containerColor = theme.cardTint().copy(alpha = 0.96f)
        )
    }
}

@Composable
private fun Chip(theme: ThemeState, text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) theme.accent else theme.cardTint().copy(alpha = 0.45f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = if (active) androidx.compose.ui.graphics.Color.White else theme.textColor,
            fontSize = 12.sp
        )
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
private fun SettingsScreen(theme: ThemeState, onOpenBg: () -> Unit = {}) {
    val (winW, winH) = winSize()
    var dialog by remember { mutableStateOf(0) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle(theme, "外观") }
        item {
            SettingRowAction(theme, "主题配色",
                if (theme.accentMode == "monet") "莫奈取色" else "默认") { dialog = 1 }
        }
        item {
            SettingRowAction(theme, "明暗", when (theme.darkMode) {
                "light" -> "浅色"; "dark" -> "深色"; else -> "自动"
            }) { dialog = 2 }
        }
        item {
            SettingRowAction(theme, "图片背景",
                if (theme.bg != null) "已设置" else "未设置") { onOpenBg() }
        }
        item { SectionTitle(theme, "通用") }
        item { SettingRow(theme, "语言", "中文") }
        item { SectionTitle(theme, "关于") }
        item { SettingRow(theme, "版本", "1.3.0") }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (dialog == 1) {
        ChoiceDialog(theme, "主题配色",
            listOf("default" to "默认", "monet" to "莫奈取色（跟随壁纸）"),
            theme.accentMode,
            { theme.setAccentMode(it); dialog = 0 }) { dialog = 0 }
    }
    if (dialog == 2) {
        ChoiceDialog(theme, "明暗",
            listOf("auto" to "自动", "light" to "浅色", "dark" to "深色"),
            theme.darkMode,
            { theme.setThemeMode(it); dialog = 0 }) { dialog = 0 }
    }
}

@Composable
private fun ChoiceDialog(theme: ThemeState, title: String, options: List<Pair<String, String>>,
                         current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textColor, fontSize = 15.sp) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(key) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = theme.textColor, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        if (key == current) Text("✓", color = theme.accent, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", color = theme.accent)
            }
        },
        containerColor = theme.cardTint().copy(alpha = 0.96f)
    )
}

@Composable
private fun SectionTitle(theme: ThemeState, text: String) {
    Text(text, color = theme.subColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SettingRow(theme: ThemeState, title: String, value: String,
                       onClick: (() -> Unit)? = null) {
    val (winW, winH) = winSize()
    GlassRow(theme.bgBlur, theme.cardTint(),
        modifier = Modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        dstW = winW, dstH = winH) {
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

/* ================= 图片背景二级页 ================= */

@Composable
fun BgSettingsScreen(theme: ThemeState, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val (winW, winH) = winSize()
    val blur = theme.bgBlur
    val hasBg = theme.bg != null

    // 裁切页返回后必须刷新，否则背景不更新
    val cropLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) theme.reload(ctx)
    }

    val pickBg = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (t: Throwable) { }
            cropLauncher.launch(
                android.content.Intent(ctx, io.github.kalpa0448.taskmgr.CropActivity::class.java)
                    .setData(uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            GlassRow(blur, theme.cardTint(), dstW = winW, dstH = winH,
                modifier = Modifier.clickable { onBack() }) {
                Text("←  图片背景", color = theme.textColor, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(14.dp))
            }
        }
        item { SectionTitle(theme, "背景图片") }
        item {
            // 选图需要外部 Activity，走 ActivityResult
        }
        item {
            SettingRowAction(theme, "选择图片", if (hasBg) "更换图片" else "未设置") {
                pickBg.launch(arrayOf("image/*"))
            }
        }
        if (hasBg) {
            item {
                SettingRowAction(theme, "删除背景", "") {
                    java.io.File(ctx.filesDir, "bg.png").delete()
                    theme.reload(ctx)
                }
            }
        }
        item { SectionTitle(theme, "效果") }
        item { SwitchRow(theme, "启用模糊", "对卡片区域做毛玻璃", "bgBlur", true, hasBg) { v -> theme.applyFog(v) } }
        item { SwitchRow(theme, "整页模糊", "开：整页背景都模糊；关：只有卡片区域模糊", "frostPage", false, hasBg) { v ->
            theme.frostPage = v
            theme.sp.edit().putBoolean("frostPage", v).apply()
        } }
        item { SliderRowLevel(theme, "模糊强度", theme.blurLevel, hasBg) { v -> theme.applyBlurLevel(v) } }
        item { SliderRowPct(theme, "卡片不透明度", (theme.cardAlpha * 100).toInt(), hasBg) { v -> theme.applyCardAlpha(v) } }
        item { SliderRowPct(theme, "背景暗度调节", (theme.scrim * 100).toInt(), hasBg) { v -> theme.applyScrim(v) } }
        item { SwitchRow(theme, "从背景图取色", "从背景图提取主题强调色", "bgSeed", true, hasBg) { v -> theme.applySeed(v) } }
        item { SwitchRow(theme, "高对比度模式", "在文字周围渲染阴影", "highContrast", false, true) { v ->
            theme.highContrast = v
            theme.sp.edit().putBoolean("highContrast", v).apply()
        } }
        if (!hasBg) {
            item {
                Text("先选择一张图片，以上选项才可调节。", color = theme.subColor, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp))
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingRowAction(theme: ThemeState, title: String, value: String, onClick: () -> Unit) {
    val (winW, winH) = winSize()
    GlassRow(theme.bgBlur, theme.cardTint(), dstW = winW, dstH = winH,
        modifier = Modifier.clickable { onClick() }) {
        Text(title, color = theme.textColor, fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(14.dp))
        if (value.isNotEmpty()) {
            Text(value, color = theme.accent, fontSize = 13.sp, modifier = Modifier.padding(end = 14.dp))
        }
    }
}

@Composable
private fun SwitchRow(theme: ThemeState, title: String, sub: String, key: String,
                      def: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val (winW, winH) = winSize()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = ctx.getSharedPreferences("tm", android.content.Context.MODE_PRIVATE)
    var checked by remember { mutableStateOf(sp.getBoolean(key, def)) }
    GlassRow(theme.bgBlur, if (enabled) theme.cardTint() else theme.cardTint().copy(alpha = 0.22f),
        dstW = winW, dstH = winH) {
        Column(Modifier.weight(1f).padding(14.dp)) {
            Text(title, color = if (enabled) theme.textColor else theme.subColor, fontSize = 14.sp)
            Text(sub, color = theme.subColor, fontSize = 11.sp)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { checked = it; onChange(it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = theme.accent,
                checkedBorderColor = theme.accent
            ),
            modifier = Modifier.padding(end = 10.dp)
        )
    }
}

@Composable
private fun SliderRowLevel(theme: ThemeState, title: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val (winW, winH) = winSize()
    var v by remember { mutableStateOf(value.toFloat()) }
    GlassRow(theme.bgBlur, if (enabled) theme.cardTint() else theme.cardTint().copy(alpha = 0.22f),
        dstW = winW, dstH = winH) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, color = if (enabled) theme.textColor else theme.subColor,
                    fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${v.toInt()}", color = theme.accent, fontSize = 13.sp)
            }
            androidx.compose.material3.Slider(
                value = v,
                onValueChange = { v = it },
                valueRange = 1f..10f,
                enabled = enabled,
                onValueChangeFinished = { onChange(v.toInt()) },
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = theme.accent,
                    activeTrackColor = theme.accent
                ),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
        }
    }
}

@Composable
private fun SliderRowPct(theme: ThemeState, title: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val (winW, winH) = winSize()
    var v by remember { mutableStateOf(value.toFloat()) }
    GlassRow(theme.bgBlur, if (enabled) theme.cardTint() else theme.cardTint().copy(alpha = 0.22f),
        dstW = winW, dstH = winH) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, color = if (enabled) theme.textColor else theme.subColor,
                    fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${v.toInt()}%", color = theme.accent, fontSize = 13.sp)
            }
            androidx.compose.material3.Slider(
                value = v,
                onValueChange = { v = it },
                valueRange = 0f..100f,
                enabled = enabled,
                onValueChangeFinished = { onChange(v.toInt()) },
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = theme.accent,
                    activeTrackColor = theme.accent
                ),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
        }
    }
}

/* ---------------- 图片背景 二级页 ---------------- */

@Composable
private fun BgPageHeader(theme: ThemeState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("←", color = theme.textColor, fontSize = 22.sp,
            modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp, vertical = 4.dp))
        Text("图片背景", color = theme.textColor, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun BgScreen(theme: ThemeState) {
    val ctx = LocalContext.current

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) theme.reload(ctx)
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            cropLauncher.launch(
                Intent(ctx, CropActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    val hasBg = theme.bg != null

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle(theme, "背景图片") }
        item {
            SettingRow(theme, "选择图片", if (hasBg) "更换图片" else "未设置") {
                pickLauncher.launch(arrayOf("image/*"))
            }
        }
        if (hasBg) {
            item { SettingRow(theme, "删除背景", "") { theme.deleteBg() } }
        }

        item { SectionTitle(theme, "效果") }
        item {
            SwitchRow(theme, "启用模糊", "对背景做模糊处理",
                "bgBlur", theme.sp.getBoolean("bgBlur", true), hasBg)
        }
        item {
            SwitchRow(theme, "整页模糊", "开：整页背景都模糊；关：只有卡片区域模糊",
                "frostPage", theme.frostPage, hasBg)
        }
        item {
            SliderRow(theme, "模糊强度", theme.blurLevel, 1f..10f, hasBg) {
                theme.updateBlur(it.toInt())
            }
        }
        item {
            SliderRow(theme, "卡片不透明度", (theme.cardAlpha * 100).toInt(), 0f..100f, hasBg) {
                theme.updateCardAlpha(it.toInt())
            }
        }
        item {
            SliderRow(theme, "背景暗度调节", (theme.scrim * 100).toInt(), 0f..100f, hasBg) {
                theme.updateScrim(it.toInt())
            }
        }
        item {
            SwitchRow(theme, "从背景图取色", "从背景图提取主题强调色",
                "bgSeed", theme.bgSeed, hasBg)
        }
        item {
            SwitchRow(theme, "高对比度模式", "在文字周围渲染阴影",
                "highContrast", theme.highContrast, true)
        }
        if (!hasBg) {
            item {
                Text("先选择一张图片，以上选项才可调节。",
                    color = theme.subColor, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp))
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SwitchRow(theme: ThemeState, title: String, sub: String,
                      key: String, checked: Boolean, enabled: Boolean) {
    val (winW, winH) = winSize()
    GlassRow(theme.bgBlur, theme.cardTint(), dstW = winW, dstH = winH) {
        Column(Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 14.dp)) {
            Text(title, fontSize = 14.sp,
                color = if (enabled) theme.textColor else theme.subColor)
            Text(sub, fontSize = 11.sp, color = theme.subColor)
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) theme.setSwitch(key, it) },
            enabled = enabled,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
private fun SliderRow(theme: ThemeState, title: String, value: Int,
                      range: ClosedFloatingPointRange<Float>, enabled: Boolean,
                      onChange: (Float) -> Unit) {
    val (winW, winH) = winSize()
    GlassRow(theme.bgBlur, theme.cardTint(), dstW = winW, dstH = winH) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 14.sp,
                    color = if (enabled) theme.textColor else theme.subColor,
                    modifier = Modifier.weight(1f))
                Text("$value", fontSize = 13.sp, color = theme.accent)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { if (enabled) onChange(it) },
                valueRange = range,
                enabled = enabled
            )
        }
    }
}
