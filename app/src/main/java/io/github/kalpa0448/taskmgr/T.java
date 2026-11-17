package io.github.kalpa0448.taskmgr;

import java.util.HashMap;
import java.util.Map;

/**
 * 极简翻译层。
 * 代码里直接写中文，英文模式时按表查找替换。
 * 查不到就原样返回，所以漏翻译不会崩，只会显示中文。
 */
public final class T {

    private T() {}

    private static boolean en = false;

    public static void setEnglish(boolean v) { en = v; }

    public static boolean isEnglish() { return en; }

    public static String s(String zh) {
        if (!en) return zh;
        String v = EN.get(zh);
        return v != null ? v : zh;
    }

    /** 便捷：s("共 ") + n + s(" 个进程") */
    public static String fmt(String zh, Object... args) {
        return String.format(s(zh), args);
    }

    private static final Map<String, String> EN = new HashMap<>(256);

    private static void put(String k, String v) { EN.put(k, v); }

    static {
        // 进程状态
        put("读取失败: ", "Read failed: ");
        put("无 root：进程列表/swap/zram 不可用；CPU 由频率推算。",
                "No root: process list / swap / zram unavailable; CPU is estimated from cpufreq.");
        put("本应用", "This app");
        put("失败: ", "Failed: ");
        put("命令为空", "Command is empty");
        put("运行中 (R)", "Running (R)");
        put("可中断睡眠 (S)", "Sleeping (S)");
        put("不可中断 (D)", "Uninterruptible (D)");
        put("僵尸 (Z)", "Zombie (Z)");
        put("已停止 (T)", "Stopped (T)");
        put("跟踪停止 (t)", "Traced (t)");
        put("已退出 (X)", "Dead (X)");
        put("空闲内核线程 (I)", "Idle kthread (I)");
        put("%d 小时 %d 分", "%dh %dm");
        put("%d 分 %d 秒", "%dm %ds");
        put("%d 天 %d 小时 %d 分", "%dd %dh %dm");

        // 顶栏 / 选项卡
        put("● root 已授权（完整模式）", "● root granted (full mode)");
        put("● 无 root（受限模式）", "● no root (limited mode)");
        put("任务管理器", "Task Manager");
        put("正在请求 root…", "Requesting root…");
        put("概览", "Overview");
        put("监控", "Monitor");
        put("进程", "Processes");
        put("详情", "Details");
        put("设置", "Settings");

        // 概览页
        put("合并曲线", "Combined");
        put("分核心", "Per-core");
        put("温度 —", "Temp —");
        put("由 CPU 频率推算（无 root）", "Estimated from CPU frequency (no root)");
        put("%d 核 · 频率推算模式", "%d cores · frequency estimate");
        put("%d 核 · 负载 %.2f / %.2f / %.2f", "%d cores · load %.2f / %.2f / %.2f");
        put("温度 %.1f°C  ·  %s", "%.1f°C  ·  %s");
        put("温度 —（未找到 thermal 节点）", "Temp — (no thermal node found)");
        put("可用 %.1f GB · 占用 %.0f%%", "free %.1f GB · %.0f%% used");
        put("无 Swap", "no swap");
        put("无法读取每核数据（需要 root 或该机型不暴露 cpufreq）",
                "Per-core data unavailable (needs root, or cpufreq not exposed)");

        // 设备信息
        put("设备信息", "Device info");
        put("型号", "Model");
        put("内核", "Kernel");
        put("开机时长", "Uptime");
        put("软件包", "Packages");
        put("存储", "Storage");
        put("IP 地址", "IP address");
        put("电池", "Battery");
        put(" · 补丁 ", " · patch ");
        put("系统 %d · 用户 %d · 共 %d", "system %d · user %d · total %d");
        put("读取失败（缺少包可见性权限）", "Read failed (missing package visibility)");
        put("%.1f / %.1f GB（可用 %.1f GB）", "%.1f / %.1f GB (%.1f GB free)");
        put("读取失败", "Read failed");
        put("未连接网络", "Not connected");
        put("未连接 / 读取失败", "Not connected / read failed");
        put("充电中", "Charging");
        put("已充满", "Full");
        put("放电中", "Discharging");
        put("未充电", "Not charging");
        put("未知", "Unknown");

        // 进程页
        put("全部", "All");
        put("系统", "System");
        put("用户", "User");
        put("内存↓", "Memory↓");
        put("内存", "Memory");
        put("名称", "Name");
        put("名称↓", "Name↓");
        put("平铺", "Flat");
        put("树形", "Tree");
        put("搜索进程名 / PID", "Search name / PID");
        put("搜索名称 / PID", "Search name / PID");
        put("状态", "State");
        put("用户名", "User name");
        put("体系", "Arch");
        put("描述", "Description");
        put("运行", "Run");
        put("睡眠", "Sleep");
        put("IO等待", "IO wait");
        put("僵尸", "Zombie");
        put("停止", "Stop");
        put("退出", "Dead");
        put("空闲", "Idle");
        put("此分类下没有进程", "No processes in this category");
        put("没有匹配「", "No process matching \"");
        put("」的进程", "\"");
        put("… 已截断，可用搜索或选项卡缩小范围", "… truncated; narrow it down with search or tabs");
        put(" · 本应用", " · this app");
        put(" · 子进程 ", " · children ");
        put("结束", "End");
        put("共 ", "Total ");
        put(" 个进程", " processes");
        put(" 项，已显示前 ", " items, showing first ");
        put(" 项", " items");
        put(" 个，已显示前 ", " processes, showing first ");
        put(" 个", " processes");

        // 设置页
        put("外观", "Appearance");
        put("主题", "Theme");
        put("深色", "Dark");
        put("浅色", "Light");
        put("默认", "Default");
        put("莫奈取色", "Monet colors");
        put("背景图片", "Background image");
        put("卡片模糊", "Blur cards");
        put("卡片毛玻璃", "Frosted cards");
        put("蒙版浓度", "Backdrop dim");
        put("数值越大，背景越暗，文字越清晰。", "Higher value = darker backdrop, clearer text.");
        put("确定", "OK");
        put("曲线点数", "Chart points");
        put("曲线点数（个）", "Chart points");
        put("刷新", "Refresh");
        put("刷新间隔", "Refresh interval");
        put("1 秒", "1 s");
        put("1.5 秒", "1.5 s");
        put("2 秒", "2 s");
        put("3 秒", "3 s");
        put("5 秒", "5 s");
        put("进程列表", "Process list");
        put("默认排序", "Default sort");
        put("默认分类", "Default category");
        put("CPU 视图", "CPU view");
        put("进程视图", "Process view");
        put("隐藏内核线程", "Hide kernel threads");
        put("隐藏本应用", "Hide this app");
        put("通用", "General");
        put("语言", "Language");
        put("数据", "Data");
        put("导出进程列表（Excel）", "Export process list (Excel)");
        put("关于", "About");
        put("开", "On");
        put("关", "Off");
        put("选择背景图", "Choose background image");
        put("恢复默认背景", "Reset background");

        // 关于
        put("Android 任务管理器  v", "Android Task Manager  v");
        put("监控 CPU / 内存 / 进程树；有 root 时功能完整，\n",
                "Monitor CPU / memory / process tree; full features with root,\n");
        put("无 root 时 CPU 由 cpufreq 频率推算。\n\n",
                "CPU is estimated from cpufreq without root.\n\n");
        put("开源地址  https://github.com/Kalpa0448/TaskMgr\n",
                "Source  https://github.com/Kalpa0448/TaskMgr\n");
        put("本程序以 MIT 协议开源，欢迎提交 Issue 与 PR。\n",
                "Released under the MIT License. Issues and PRs are welcome.\n");

        // 详情弹窗
        put("⚠ 未获得 root：以下 CPU 时间为 0，频率来自 cpufreq\n\n",
                "⚠ No root: CPU times below are 0; frequency comes from cpufreq\n\n");
        put("芯片：", "SoC: ");
        put("核心数：", "Cores: ");
        put("频率范围：", "Frequency: ");
        put("【每核当前频率 / 占用】\n", "[Per-core frequency / usage]\n");
        put("【CPU 时间构成】\n", "[CPU time breakdown]\n");
        put("user     用户态", "user     user");
        put("nice     低优先级", "nice     low priority");
        put("system   内核态", "system   kernel");
        put("idle     空闲", "idle     idle");
        put("iowait   等待 IO", "iowait   io wait");
        put("irq      硬中断", "irq      hard irq");
        put("softirq  软中断", "softirq  soft irq");
        put("steal    虚拟化占用", "steal    virtualization");
        put("【温度】\n", "[Temperature]\n");
        put("【系统活动】\n", "[System activity]\n");
        put("  上下文切换：", "  Context switches: ");
        put("  中断次数：", "  Interrupts: ");
        put("  已创建进程：", "  Processes created: ");
        put("  运行中 / 阻塞：", "  Running / blocked: ");
        put("  负载 1/5/15：", "  Load 1/5/15: ");
        put("  开机时长：", "  Uptime: ");
        put("CPU 详细信息", "CPU details");
        put("【物理内存】\n", "[Physical memory]\n");
        put("总量", "Total");
        put("已用", "Used");
        put("可用", "Available");
        put("内核栈", "Kernel stack");
        put("\n【虚拟内存】\n", "\n[Virtual memory]\n");
        put("（可超售，大于 limit 属正常）", " (over-commit is normal)");
        put("    压缩算法：", "    Algorithm: ");
        put("    设备大小：", "    Device size: ");
        put("    原始数据：", "    Original: ");
        put("    压缩后  ：", "    Compressed: ");
        put("    实际占用：", "    In use: ");
        put("    压缩比  ：", "    Ratio: ");
        put("    节省    ：%.1f%%\n", "    Saved: %.1f%%\n");
        put("未检测到 zram 设备", "No zram device found");
        put("需要 root 才能读取 /sys/block/zram*", "Root required to read /sys/block/zram*");
        put("内存详细信息", "Memory details");
        put("关闭", "Close");
        put("取消", "Cancel");

        // 进程操作
        put("需要 root", "Root required");
        put("不能结束系统核心进程", "Cannot kill core system process");
        put("不能结束自己", "Cannot kill this app");
        put("结束进程", "End process");
        put("\n\n强制结束可能导致异常退出。", "\n\nForcibly ending it may cause the app to crash.");
        put("已结束 ", "Ended ");
        put("结束失败", "Failed to end");
        put("父进程：", "Parent: ");
        put("（系统）", " (system)");
        put("（应用）", " (app)");
        put("状态：", "State: ");
        put("线程数：", "Threads: ");
        put("优先级：", "Priority: ");
        put("内存(RSS)：", "Memory (RSS): ");
        put("虚拟内存(VSZ)：", "Virtual (VSZ): ");
        put("运行时长：", "Running for: ");

        // 导出
        put("已导出", "Exported");
        put("导出失败", "Export failed");
        put("没有可导出的进程数据", "No process data to export");
        put("正在导出…", "Exporting…");
        put("打开", "Open");
        put("无法打开文件管理器", "No file manager available");
        put("全部(", "All (");
        put("系统(", "System (");
        put("用户(", "User (");
        put("内存 (MB)", "Memory (MB)");
        put("父进程", "Parent");
        put("线程", "Threads");
        put("… 共 ", "… total ");
        put("下载/", "Download/");
        put("导出失败: ", "Export failed: ");
        put("莫奈取色（需 Android 12+）", "Monet colors (Android 12+)");
        put("中文", "中文");
        put("恢复默认背景", "Reset to default");
        put("配色", "Color source");
        put("明暗", "Brightness");
        put("跟随系统", "Follow system");
        put("裁切背景", "Crop background");
        put("裁切方式", "Crop mode");
        put("应用内裁切", "In-app crop");
        put("外部裁切", "System crop");
        put("应用", "Apply");
        put("旋转角度", "Rotation");
        put("自定义背景", "Custom background");
        put("选择一张图片作为应用背景", "Use an image as the app background");
        put("启用模糊", "Enable blur");
        put("对背景图做高斯模糊", "Apply gaussian blur to the background");
        put("卡片不透明度", "Card opacity");
        put("卡片越不透明，文字越清晰", "More opaque = clearer text");
        put("背景暗度调节", "Background dim");
        put("数值越大，背景越暗", "Higher value = darker background");
        put("从背景图取色", "Color from background");
        put("从背景图提取主题强调色", "Use the background image as the accent color");
        put("高对比度模式", "High contrast");
        put("在文字周围渲染阴影", "Render shadows around text");
        put("正在处理…", "Processing…");
        put("选择图片", "Choose image");
        put("更换图片", "Change image");
        put("删除背景", "Remove background");
        put("模糊强度", "Blur strength");
        put("整页模糊", "Blur whole page");
        put("开：整页背景都模糊；关：只有卡片区域模糊", "On: blur the whole page; Off: only card areas");
        put("图片背景", "Background image");
        put("先选择一张图片，以上选项才可调节。", "Choose an image first to unlock these options.");
        put("进程导出", "Process export");
        put("正在加载进程列表…", "Loading process list…");
        put("无法创建目标文件", "Cannot create target file");
        put("无法打开相册", "Cannot open gallery");
        put("已设置", "Set");
        put("未设置", "Not set");
        put("莫奈取色需要 Android 12 及以上", "Monet colors require Android 12+");
        put("xlsx", "xlsx");
    }
}
