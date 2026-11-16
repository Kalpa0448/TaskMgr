package com.kalpa.taskmgr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    /* 主题色（实例字段，可在设置里切换） */
    private int C_BG, C_CARD, C_TEXT, C_SUB, C_CPU, C_MEM, C_WARN, C_TRACK, C_SWAP, C_CARD2,
            C_CHIP, C_DANGER_BG;

    private static final int CAPACITY = 120;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = true;
    private volatile long intervalMs = 1500;

    private SharedPreferences sp;

    /* 视图 */
    private LinearLayout content, list, coreBox, settingsBox;
    private ScrollView pageMonitor, pageProcess, pageDetails, pageSettings;
    private TextView tabMonitor, tabProcess, tabDetails, tabSettings;
    private LinearLayout detailList;
    private String detailSort = "cpu";
    private String viewMode = "flat";   // flat=平铺（默认） / tree=树形
    private final Map<Integer, String> labelCache = new HashMap<>();
    private TextView tvCpu, tvCpuSub, tvMem, tvMemSub, tvSwap, tvProc, tvStatus, tvModeHint, tvTemp;
    private View barCpu, barMem;
    private FrameLayout trackCpu, trackMem;
    private Sparkline sparkCpu, sparkMem;
    private EditText search;
    private TextView chipCpu, chipMem, chipName, chipAll, chipSys, chipApp, chipAgg, chipPer,
            chipFlat, chipTree;
    private TextView dChipAll, dChipSys, dChipApp;

    /* 状态 */
    private String sortBy = "cpu", filterMode = "all", keyword = "", cpuMode = "agg", tab = "monitor";
    private int tick = 0;
    private ProcCollector.Snapshot last;
    private boolean rootOk;
    private final int selfPid = android.os.Process.myPid();
    private final List<CoreRow> cores = new ArrayList<>();
    private final Set<Integer> expanded = new HashSet<>();
    private final Map<String, TextView> devValues = new java.util.LinkedHashMap<>();
    private String pkgCache = null;
    private long pkgCacheAt = 0;

    private static class CoreRow {
        TextView name, pct;
        FrameLayout track;
        View fill;
        Sparkline spark;
    }

    /* ================= 生命周期 ================= */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("tm", MODE_PRIVATE);
        boolean dark = sp.getBoolean("dark", true);
        // 让系统对话框（含详情弹窗）也跟随应用主题，避免深色界面弹出白框
        setTheme(dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        applyTheme(dark);
        sortBy = sp.getString("sort", "cpu");
        filterMode = sp.getString("filter", "all");
        cpuMode = sp.getString("cpuMode", "agg");
        viewMode = sp.getString("viewMode", "flat");
        tab = sp.getString("tab", "monitor");
        intervalMs = sp.getInt("interval", 1500);

        setContentView(build());

        new Thread(() -> {
            final boolean r = ProcCollector.hasRoot();
            ui.post(() -> {
                rootOk = r;
                tvStatus.setText(r ? "● root 已授权（完整模式）" : "● 无 root（受限模式）");
                tvStatus.setTextColor(r ? C_MEM : C_WARN);
                new Thread(() -> {
                }, "noop").start();
            });
        }, "root").start();
        startLoop();
    }

    @Override
    protected void onDestroy() {
        running = false;
        super.onDestroy();
    }

    /* ================= 主题 ================= */

    private void applyTheme(boolean dark) {
        if (dark) {
            C_BG = 0xFF1B1B1D; C_CARD = 0xFF262629; C_CARD2 = 0xFF212124;
            C_TEXT = 0xFFECECEC; C_SUB = 0xFF9A9A9A; C_TRACK = 0xFF3A3A3D;
            C_CPU = 0xFF4FC3F7; C_MEM = 0xFF81C784; C_WARN = 0xFFEF5350; C_SWAP = 0xFFFFB74D;
            C_CHIP = 0xFF40404A;        // 未选中 chip：明显亮于卡片
            C_DANGER_BG = 0xFF43262A;
        } else {
            C_BG = 0xFFF4F4F7; C_CARD = 0xFFFFFFFF; C_CARD2 = 0xFFF0F0F4;
            C_TEXT = 0xFF1A1A1A; C_SUB = 0xFF6B6B6B; C_TRACK = 0xFFE2E2E8;
            C_CPU = 0xFF0288D1; C_MEM = 0xFF2E7D32; C_WARN = 0xFFC62828; C_SWAP = 0xFFEF6C00;
            C_CHIP = 0xFFDCDCE6;        // 浅色下未选中 chip（对比度更高）
            C_DANGER_BG = 0xFFFBE4E4;
        }
        try {
            boolean isDark = (C_BG & 0xFFFFFF) < 0x808080;
            getWindow().setStatusBarColor(C_BG);
            getWindow().setNavigationBarColor(C_BG);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int f = getWindow().getDecorView().getSystemUiVisibility();
                if (isDark) f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                getWindow().getDecorView().setSystemUiVisibility(f);
            }
        } catch (Throwable ignored) { }
    }

    /* ================= 骨架 ================= */

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(14), dp(14), dp(14), 0);

        // 标题
        TextView t = new TextView(this);
        t.setText("任务管理器");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_TEXT);
        root.addView(t);

        tvStatus = new TextView(this);
        tvStatus.setText("正在请求 root…");
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setTextColor(C_SUB);
        tvStatus.setPadding(0, dp(4), 0, dp(10));
        root.addView(tvStatus);

        // 选项卡
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(8));
        tabMonitor = tabBtn("监控");
        tabProcess = tabBtn("进程");
        tabDetails = tabBtn("详情");
        tabSettings = tabBtn("设置");
        tabMonitor.setOnClickListener(v -> switchTab("monitor"));
        tabProcess.setOnClickListener(v -> switchTab("process"));
        tabDetails.setOnClickListener(v -> switchTab("details"));
        tabSettings.setOnClickListener(v -> switchTab("settings"));
        tabs.addView(tabMonitor, tabLp());
        tabs.addView(tabProcess, tabLp());
        tabs.addView(tabDetails, tabLp());
        tabs.addView(tabSettings, tabLp());
        root.addView(tabs);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        pageMonitor = buildMonitor();
        pageProcess = buildProcess();
        pageDetails = buildDetails();
        pageSettings = buildSettings();
        content.addView(pageMonitor, new LinearLayout.LayoutParams(-1, -1));
        content.addView(pageProcess, new LinearLayout.LayoutParams(-1, -1));
        content.addView(pageDetails, new LinearLayout.LayoutParams(-1, -1));
        content.addView(pageSettings, new LinearLayout.LayoutParams(-1, -1));
        syncChips();          // 启动时必须调一次，否则所有 chip 没有背景色
        switchTab(tab);
        return root;
    }

    private TextView tabBtn(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(9), 0, dp(9));
        return tv;
    }

    private LinearLayout.LayoutParams tabLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.rightMargin = dp(5);
        return p;
    }

    private void switchTab(String t) {
        tab = t;
        sp.edit().putString("tab", t).apply();
        pageMonitor.setVisibility(t.equals("monitor") ? View.VISIBLE : View.GONE);
        pageProcess.setVisibility(t.equals("process") ? View.VISIBLE : View.GONE);
        pageDetails.setVisibility(t.equals("details") ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(t.equals("settings") ? View.VISIBLE : View.GONE);
        styleTab(tabMonitor, t.equals("monitor"));
        styleTab(tabProcess, t.equals("process"));
        styleTab(tabDetails, t.equals("details"));
        styleTab(tabSettings, t.equals("settings"));
        if (t.equals("settings")) refreshSettings();
        if (t.equals("details")) {
            if (last != null) renderTree(last);   // 启动时 last 可能为 null
            renderDetails(last);
        }
        ProcCollector.wantArch = t.equals("details");
    }

    private void styleTab(TextView tv, boolean on) {
        tv.setTextColor(on ? 0xFF101012 : C_TEXT);
        tv.setBackground(round(on ? C_CPU : C_CHIP, dp(10)));
        tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /* ================= 监控页 ================= */

    private ScrollView buildMonitor() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(24));

        LinearLayout cpuCard = card();
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        tvCpu = new TextView(this);
        tvCpu.setText("0.0%");
        tvCpu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        tvCpu.setTypeface(Typeface.DEFAULT_BOLD);
        tvCpu.setTextColor(C_CPU);
        r1.addView(tvCpu, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView cpuDetail = pill("详情", C_CPU, C_CHIP);
        cpuDetail.setOnClickListener(v -> showCpuDetail());
        r1.addView(cpuDetail);
        cpuCard.addView(r1);

        tvModeHint = new TextView(this);
        tvModeHint.setText("");
        tvModeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvModeHint.setTextColor(C_SWAP);
        cpuCard.addView(tvModeHint);

        trackCpu = track(C_CPU);
        barCpu = trackCpu.getChildAt(0);
        cpuCard.addView(trackCpu, lp(-1, dp(6), 0, dp(10), 0, 0));

        LinearLayout modeRow = new LinearLayout(this);
        chipAgg = chip("合并曲线");
        chipPer = chip("分核心");
        chipAgg.setOnClickListener(v -> { cpuMode = "agg"; sp.edit().putString("cpuMode", "agg").apply(); syncChips(); applyCpuMode(); });
        chipPer.setOnClickListener(v -> { cpuMode = "per"; sp.edit().putString("cpuMode", "per").apply(); syncChips(); applyCpuMode(); });
        modeRow.addView(chipAgg);
        modeRow.addView(chipPer);
        cpuCard.addView(modeRow, lp(-1, -2, 0, dp(10), 0, dp(4)));

        sparkCpu = new Sparkline(this, C_CPU, CAPACITY);
        cpuCard.addView(sparkCpu, lp(-1, dp(46), 0, 0, 0, 0));

        coreBox = new LinearLayout(this);
        coreBox.setOrientation(LinearLayout.VERTICAL);
        coreBox.setVisibility(View.GONE);
        cpuCard.addView(coreBox, lp(-1, -2, 0, dp(2), 0, 0));

        tvCpuSub = new TextView(this);
        tvCpuSub.setText("—");
        tvCpuSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvCpuSub.setTextColor(C_SUB);
        tvCpuSub.setPadding(0, dp(6), 0, 0);
        cpuCard.addView(tvCpuSub);

        tvTemp = new TextView(this);
        tvTemp.setText("温度 —");
        tvTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvTemp.setTextColor(C_SUB);
        tvTemp.setPadding(0, dp(3), 0, 0);
        cpuCard.addView(tvTemp);
        box.addView(cpuCard, lp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout memCard = card();
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        tvMem = new TextView(this);
        tvMem.setText("—");
        tvMem.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvMem.setTypeface(Typeface.DEFAULT_BOLD);
        tvMem.setTextColor(C_MEM);
        r2.addView(tvMem, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView memDetail = pill("详情", C_MEM, C_CHIP);
        memDetail.setOnClickListener(v -> showMemDetail());
        r2.addView(memDetail);
        memCard.addView(r2);

        trackMem = track(C_MEM);
        barMem = trackMem.getChildAt(0);
        memCard.addView(trackMem, lp(-1, dp(6), 0, dp(10), 0, 0));

        sparkMem = new Sparkline(this, C_MEM, CAPACITY);
        memCard.addView(sparkMem, lp(-1, dp(46), 0, dp(6), 0, 0));

        tvMemSub = new TextView(this);
        tvMemSub.setText("—");
        tvMemSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvMemSub.setTextColor(C_SUB);
        memCard.addView(tvMemSub);

        tvSwap = new TextView(this);
        tvSwap.setText("—");
        tvSwap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSwap.setTextColor(C_SWAP);
        tvSwap.setPadding(0, dp(4), 0, 0);
        memCard.addView(tvSwap);
        box.addView(memCard, lp(-1, -2, 0, 0, 0, dp(12)));

        box.addView(buildDeviceCard(), lp(-1, -2, 0, 0, 0, 0));

        sc.addView(box);
        applySparkPoints();
        return sc;
    }

    private LinearLayout buildDeviceCard() {
        LinearLayout c = card();
        TextView title = new TextView(this);
        title.setText("设备信息");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_SUB);
        title.setPadding(0, 0, 0, dp(8));
        c.addView(title);

        String[] keys = {"型号", "Android", "内核", "开机时长", "软件包", "存储", "IP 地址", "电池"};
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(C_TRACK);
                c.addView(line, lp(-1, 1, 0, dp(6), 0, dp(6)));
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView k = new TextView(this);
            k.setText(keys[i]);
            k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            k.setTextColor(C_SUB);
            k.setWidth(dp(72));
            row.addView(k);

            TextView v = new TextView(this);
            v.setText("—");
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            v.setTextColor(C_TEXT);
            v.setTextIsSelectable(true);
            row.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));

            devValues.put(keys[i], v);
            c.addView(row);
        }
        return c;
    }

    private void updateDeviceInfo() {
        ProcCollector.Snapshot s = last;
        set("型号", Build.MANUFACTURER + " " + Build.MODEL + "  (" + Build.DEVICE + ")");
        set("Android", "Android " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT
                + (Build.VERSION.SECURITY_PATCH != null && !Build.VERSION.SECURITY_PATCH.isEmpty()
                ? " · 补丁 " + Build.VERSION.SECURITY_PATCH : ""));
        set("内核", System.getProperty("os.version", "?") + " · " + Build.HARDWARE);
        set("开机时长", s != null && s.uptime > 0
                ? fmtDuration((long) s.uptime)
                : fmtDuration(SystemClock.elapsedRealtime() / 1000));
        set("软件包", packages());
        set("存储", storage());
        set("IP 地址", ipAddress());
        set("电池", battery());
    }

    private void set(String k, String v) {
        TextView tv = devValues.get(k);
        if (tv != null) tv.setText(v == null ? "—" : v);
    }

    /** 软件包数量：缓存 60 秒 */
    private String packages() {
        long now = System.currentTimeMillis();
        if (pkgCache != null && now - pkgCacheAt < 60000) return pkgCache;
        try {
            List<PackageInfo> list = getPackageManager().getInstalledPackages(0);
            int sys = 0, user = 0, dis = 0;
            for (PackageInfo pi : list) {
                if (pi.applicationInfo == null) continue;
                boolean isSys = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isDis = (pi.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) == 0;
                if (isDis) dis++;
                else if (isSys) sys++;
                else user++;
            }
            pkgCache = String.format(Locale.ROOT, "系统 %d · 用户 %d · 共 %d", sys, user, sys + user);
        } catch (Throwable t) {
            pkgCache = "读取失败（缺少包可见性权限）";
        }
        pkgCacheAt = now;
        return pkgCache;
    }

    private String storage() {
        try {
            StatFs f = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = f.getTotalBytes(), free = f.getAvailableBytes();
            long used = total - free;
            return String.format(Locale.ROOT, "%.1f / %.1f GB（可用 %.1f GB）",
                    used / 1073741824.0, total / 1073741824.0, free / 1073741824.0);
        } catch (Throwable t) {
            return "读取失败";
        }
    }

    private String ipAddress() {
        // 优先用 ConnectivityManager（Android 10+ 才可靠）
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network n = cm.getActiveNetwork();
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                if (lp != null) {
                    for (LinkAddress la : lp.getLinkAddresses()) {
                        InetAddress a = la.getAddress();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                            String name = lp.getInterfaceName();
                            return (name == null ? "" : name + " ") + a.getHostAddress();
                        }
                    }
                }
            }
        } catch (Throwable logged) {
            android.util.Log.d("TaskMgr", "ip via CM failed: " + logged);
        }
        // 兜底：NetworkInterface
        try {
            List<NetworkInterface> nis = Collections.list(NetworkInterface.getNetworkInterfaces());
            String fallback = null;
            for (NetworkInterface ni : nis) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip == null) continue;
                    String name = ni.getName();
                    if (name.startsWith("wlan")) return name + " " + ip;
                    if (fallback == null) fallback = name + " " + ip;
                }
            }
            return fallback == null ? "未连接网络" : fallback;
        } catch (Throwable t) {
            android.util.Log.d("TaskMgr", "ip via NI failed: " + t);
            return "未连接 / 读取失败";
        }
    }

    private String battery() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return "读取失败";
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = scale > 0 ? level * 100 / scale : -1;
            int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

            String st;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING: st = "充电中"; break;
                case BatteryManager.BATTERY_STATUS_FULL: st = "已充满"; break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING: st = "放电中"; break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING: st = "未充电"; break;
                default: st = "未知";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(pct >= 0 ? pct + "%" : "—");
            sb.append(" · ").append(st);
            if (temp != 0) sb.append(String.format(Locale.ROOT, " · %.1f°C", temp / 10.0));
            if (volt > 0) sb.append(String.format(Locale.ROOT, " · %.2fV", volt / 1000.0));
            if (tech != null && !tech.isEmpty()) sb.append(" · ").append(tech);

            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                long nowUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (nowUa != 0 && nowUa != Long.MIN_VALUE) {
                    // 各厂商 current_now 符号约定相反，统一为：充电=正，放电=负
                    boolean chargingNow = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;
                    double ma = nowUa / 1000.0;
                    if (chargingNow && ma < 0) ma = -ma;
                    if (!chargingNow && ma > 0) ma = -ma;
                    sb.append(String.format(Locale.ROOT, " · %+.0f mA", ma));
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "读取失败";
        }
    }

    private void applySparkPoints() {
        int pts = sp.getInt("points", 60);
        if (sparkCpu != null) sparkCpu.setMaxPoints(pts);
        if (sparkMem != null) sparkMem.setMaxPoints(pts);
        for (CoreRow c : cores) if (c.spark != null) c.spark.setMaxPoints(pts);
    }

    private void applyCpuMode() {
        sparkCpu.setVisibility(cpuMode.equals("agg") ? View.VISIBLE : View.GONE);
        coreBox.setVisibility(cpuMode.equals("per") ? View.VISIBLE : View.GONE);
        if (last != null) renderCores(last);
    }

    private void renderCores(ProcCollector.Snapshot s) {
        if (!cpuMode.equals("per") || s == null) return;
        if (s.coreList.isEmpty()) {
            coreBox.removeAllViews();
            cores.clear();
            TextView hint = new TextView(this);
            hint.setText("无法读取每核数据（需要 root 或该机型不暴露 cpufreq）");
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            hint.setTextColor(C_SUB);
            hint.setPadding(0, dp(8), 0, dp(8));
            coreBox.addView(hint);
            return;
        }
        if (cores.size() != s.coreList.size()) {
            cores.clear();
            coreBox.removeAllViews();
            for (ProcCollector.Core c : s.coreList) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(3), 0, dp(3));

                CoreRow cr = new CoreRow();
                cr.name = new TextView(this);
                cr.name.setText("C" + c.id);
                cr.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                cr.name.setTextColor(C_SUB);
                cr.name.setWidth(dp(26));
                row.addView(cr.name);

                cr.track = new FrameLayout(this);
                cr.track.setBackground(round(C_TRACK, dp(2)));
                cr.fill = new View(this);
                cr.fill.setBackground(round(C_CPU, dp(2)));
                cr.track.addView(cr.fill, new FrameLayout.LayoutParams(0, -1));
                row.addView(cr.track, new LinearLayout.LayoutParams(0, dp(5), 1f));

                cr.pct = new TextView(this);
                cr.pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                cr.pct.setTextColor(C_CPU);
                cr.pct.setGravity(Gravity.END);
                cr.pct.setWidth(dp(40));
                cr.pct.setPadding(dp(6), 0, 0, 0);
                row.addView(cr.pct);

                cr.spark = new Sparkline(this, C_CPU, CAPACITY);
                cr.spark.setMaxPoints(sp.getInt("points", 60));
                LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(dp(56), dp(16));
                sp2.leftMargin = dp(6);
                row.addView(cr.spark, sp2);

                cores.add(cr);
                coreBox.addView(row);
            }
        }
        for (int i = 0; i < s.coreList.size() && i < cores.size(); i++) {
            ProcCollector.Core c = s.coreList.get(i);
            CoreRow cr = cores.get(i);
            cr.pct.setText(String.format(Locale.ROOT, "%.0f%%", c.pct));
            cr.spark.push((float) c.pct);
            final int pct = (int) c.pct;
            cr.track.post(() -> {
                FrameLayout.LayoutParams l2 = (FrameLayout.LayoutParams) cr.fill.getLayoutParams();
                l2.width = cr.track.getWidth() * pct / 100;
                cr.fill.setLayoutParams(l2);
            });
        }
    }

    /* ================= 进程页 ================= */

    private ScrollView buildProcess() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(24));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        chipAll = chip("全部"); chipSys = chip("系统"); chipApp = chip("用户");
        chipAll.setOnClickListener(v -> { filterMode = "all"; sp.edit().putString("filter", "all").apply(); syncChips(); render(last); });
        chipSys.setOnClickListener(v -> { filterMode = "system"; sp.edit().putString("filter", "system").apply(); syncChips(); render(last); });
        chipApp.setOnClickListener(v -> { filterMode = "app"; sp.edit().putString("filter", "app").apply(); syncChips(); render(last); });
        filters.addView(chipAll); filters.addView(chipSys); filters.addView(chipApp);
        box.addView(filters, lp(-1, -2, 0, 0, 0, dp(8)));

        LinearLayout sorts = new LinearLayout(this);
        sorts.setOrientation(LinearLayout.HORIZONTAL);
        sorts.setGravity(Gravity.CENTER_VERTICAL);
        chipCpu = chip("CPU↓"); chipMem = chip("内存↓"); chipName = chip("名称");
        chipCpu.setOnClickListener(v -> { sortBy = "cpu"; sp.edit().putString("sort", "cpu").apply(); syncChips(); render(last); });
        chipMem.setOnClickListener(v -> { sortBy = "mem"; sp.edit().putString("sort", "mem").apply(); syncChips(); render(last); });
        chipName.setOnClickListener(v -> { sortBy = "name"; sp.edit().putString("sort", "name").apply(); syncChips(); render(last); });
        sorts.addView(chipCpu); sorts.addView(chipMem); sorts.addView(chipName);
        chipFlat = chip("平铺");
        chipTree = chip("树形");
        chipFlat.setOnClickListener(v -> setViewMode("flat"));
        chipTree.setOnClickListener(v -> setViewMode("tree"));
        sorts.addView(chipFlat); sorts.addView(chipTree);
        tvProc = new TextView(this);
        tvProc.setText("进程");
        tvProc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvProc.setTextColor(C_SUB);
        tvProc.setGravity(Gravity.END);
        sorts.addView(tvProc, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(sorts, lp(-1, -2, 0, 0, 0, dp(8)));

        search = new EditText(this);
        search.setHint("搜索进程名 / PID");
        search.setHintTextColor(C_SUB);
        search.setTextColor(C_TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        search.setSingleLine(true);
        search.setBackground(round(C_CARD, dp(10)));
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) {
                keyword = e.toString().trim().toLowerCase(Locale.ROOT);
                render(last);
            }
        });
        box.addView(search, lp(-1, -2, 0, 0, 0, dp(10)));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list, new LinearLayout.LayoutParams(-1, -2));
        sc.addView(box);
        return sc;
    }

    /* ================= 设置页 ================= */

    /* ================= 详情页（Win11 风格表格） ================= */

    private static final int W_NAME = 108, W_PID = 44, W_STATE = 50, W_USER = 74,
            W_CPU = 44, W_MEM = 46, W_ARCH = 36;

    private ScrollView buildDetails() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(20));

        LinearLayout dFilters = new LinearLayout(this);
        dFilters.setOrientation(LinearLayout.HORIZONTAL);
        dChipAll = chip("全部");
        dChipSys = chip("系统");
        dChipApp = chip("用户");
        dChipAll.setOnClickListener(v -> setFilter("all"));
        dChipSys.setOnClickListener(v -> setFilter("system"));
        dChipApp.setOnClickListener(v -> setFilter("app"));
        dFilters.addView(dChipAll);
        dFilters.addView(dChipSys);
        dFilters.addView(dChipApp);
        box.addView(dFilters, lp(-1, -2, 0, 0, 0, dp(8)));

        EditText et = new EditText(this);
        et.setHint("搜索名称 / PID");
        et.setHintTextColor(C_SUB);
        et.setTextColor(C_TEXT);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setSingleLine(true);
        et.setBackground(round(C_CARD, dp(10)));
        et.setPadding(dp(12), dp(8), dp(12), dp(8));
        et.setText(keyword);
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) {
                keyword = e.toString().trim().toLowerCase(Locale.ROOT);
                renderDetails(last);
            }
        });
        box.addView(et, lp(-1, -2, 0, 0, 0, dp(8)));

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(true);
        detailList = new LinearLayout(this);
        detailList.setOrientation(LinearLayout.VERTICAL);
        hsv.addView(detailList, new ViewGroup.LayoutParams(-2, -2));
        box.addView(hsv, new LinearLayout.LayoutParams(-1, -2));

        sc.addView(box);
        return sc;
    }

    private TextView cell(String text, int widthDp, int color, float sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setWidth(dp(widthDp));
        tv.setPadding(dp(3), dp(6), dp(3), dp(6));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private void renderDetails(ProcCollector.Snapshot s) {
        if (detailList == null) return;
        detailList.removeAllViews();

        // 表头
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBackground(round(C_CARD2, dp(8)));
        head.addView(headCell("名称", W_NAME, "name"));
        head.addView(headCell("PID", W_PID, "pid"));
        head.addView(headCell("状态", W_STATE, null));
        head.addView(headCell("用户名", W_USER, null));
        head.addView(headCell("CPU", W_CPU, "cpu"));
        head.addView(headCell("内存", W_MEM, "mem"));
        head.addView(headCell("体系", W_ARCH, null));
        head.addView(cell("描述", 116, C_SUB, 11, true));
        detailList.addView(head);

        if (s == null) return;
        if (s.error != null && !s.error.isEmpty() && s.procs.isEmpty()) {
            detailList.addView(errorView(s.error));
            return;
        }

        List<ProcCollector.Proc> ps = new ArrayList<>();
        for (ProcCollector.Proc p : s.procs) {
            if ("system".equals(filterMode) && !p.isSystem()) continue;
            if ("app".equals(filterMode) && p.isSystem()) continue;
            if (!keyword.isEmpty()
                    && !p.name.toLowerCase(Locale.ROOT).contains(keyword)
                    && !String.valueOf(p.pid).equals(keyword)) continue;
            ps.add(p);
        }
        switch (detailSort) {
            case "name": ps.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT))); break;
            case "pid": ps.sort(Comparator.comparingInt(a -> a.pid)); break;
            case "mem": ps.sort((a, b) -> Long.compare(b.rssBytes, a.rssBytes)); break;
            default: ps.sort((a, b) -> Double.compare(b.cpu, a.cpu));
        }

        int cap = Math.min(ps.size(), 140);
        for (int i = 0; i < cap; i++) detailList.addView(detailRow(ps.get(i), i));
        if (ps.size() > cap) {
            TextView more = new TextView(this);
            more.setText("… 共 " + ps.size() + " 项，已显示前 " + cap + " 项");
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            more.setTextColor(C_SUB);
            more.setPadding(dp(6), dp(8), dp(6), dp(8));
            detailList.addView(more);
        }
    }

    private TextView headCell(String text, int w, String sortKey) {
        String arrow = "";
        if (sortKey != null && sortKey.equals(detailSort)) arrow = "↓";
        TextView tv = cell(text + arrow, w, sortKey == null ? C_SUB : C_CPU, 11, true);
        if (sortKey != null) {
            tv.setOnClickListener(v -> {
                if (detailSort.equals(sortKey)) {
                    // 再次点击切换方向：简单起见只在 cpu/mem 间循环，name/pid 固定升序
                    detailSort = sortKey;
                } else detailSort = sortKey;
                renderDetails(last);
            });
        }
        return tv;
    }

    private View detailRow(ProcCollector.Proc p, int idx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(idx % 2 == 0 ? C_CARD : C_CARD2);

        TextView n = cell(p.name, W_NAME, p.pid == selfPid ? C_CPU : C_TEXT, 12, false);
        row.addView(n);
        row.addView(cell(String.valueOf(p.pid), W_PID, C_SUB, 11, false));
        row.addView(cell(stateShort(p.state), W_STATE, stateColor(p.state), 11, false));
        row.addView(cell(ProcCollector.userName(p.uid), W_USER, C_SUB, 11, false));
        row.addView(cell(String.format(Locale.ROOT, "%.1f", p.cpu), W_CPU,
                p.cpu > 20 ? C_WARN : C_CPU, 11, false));
        row.addView(cell(p.rssBytes >= 1073741824L
                ? String.format(Locale.ROOT, "%.1fG", p.rssBytes / 1073741824.0)
                : String.valueOf(p.rssBytes / 1048576L), W_MEM, C_SUB, 11, false));
        row.addView(cell(p.arch, W_ARCH, C_SUB, 11, false));
        row.addView(cell(labelFor(p), 116, C_SUB, 11, false));

        final ProcCollector.Proc pp = p;
        row.setOnClickListener(v -> showDetail(pp));
        return row;
    }

    private String stateShort(String st) {
        if (st == null || st.isEmpty()) return "?";
        switch (st.charAt(0)) {
            case 'R': return "运行";
            case 'S': return "睡眠";
            case 'D': return "IO等待";
            case 'Z': return "僵尸";
            case 'T': case 't': return "停止";
            case 'X': return "退出";
            case 'I': return "空闲";
            default: return st;
        }
    }

    private int stateColor(String st) {
        if (st == null || st.isEmpty()) return C_SUB;
        char c = st.charAt(0);
        if (c == 'R') return C_MEM;
        if (c == 'Z' || c == 'D') return C_WARN;
        return C_SUB;
    }

    /** 应用名（描述列）；无包名则显示 — */
    private String labelFor(ProcCollector.Proc p) {
        if (p.uid < 10000) return "—";
        String cached = labelCache.get(p.uid);
        if (cached != null) return cached;
        String label = "—";
        try {
            String[] pkgs = getPackageManager().getPackagesForUid(p.uid);
            if (pkgs != null && pkgs.length > 0) {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkgs[0], 0);
                CharSequence cs = getPackageManager().getApplicationLabel(ai);
                if (cs != null) label = cs.toString();
            }
        } catch (Throwable ignored) { }
        labelCache.put(p.uid, label);
        return label;
    }

    private ScrollView buildSettings() {
        ScrollView sc = new ScrollView(this);
        settingsBox = new LinearLayout(this);
        settingsBox.setOrientation(LinearLayout.VERTICAL);
        settingsBox.setPadding(0, 0, 0, dp(24));
        sc.addView(settingsBox);
        refreshSettings();
        return sc;
    }

    private void refreshSettings() {
        if (settingsBox == null) return;
        settingsBox.removeAllViews();

        sectionTitle("外观");
        settingRow("主题", sp.getBoolean("dark", true) ? "深色" : "浅色", () ->
                choose("主题", new String[]{"深色", "浅色"}, sp.getBoolean("dark", true) ? 0 : 1, i -> {
                    sp.edit().putBoolean("dark", i == 0).apply();
                    recreate();
                }));

        settingRow("曲线点数", String.valueOf(sp.getInt("points", 60)), () -> {
            int[] vals = {30, 60, 120};
            int cur = 1;
            for (int i = 0; i < vals.length; i++) if (vals[i] == sp.getInt("points", 60)) cur = i;
            choose("曲线点数（个）", new String[]{"30", "60", "120"}, cur, i -> {
                sp.edit().putInt("points", vals[i]).apply();
                applySparkPoints();
                refreshSettings();
            });
        });

        sectionTitle("刷新");
        settingRow("刷新间隔", intervalLabel(), () -> {
            int[] vals = {1000, 1500, 2000, 3000, 5000};
            String[] labels = {"1 秒", "1.5 秒", "2 秒", "3 秒", "5 秒"};
            int cur = 1;
            for (int i = 0; i < vals.length; i++) if (vals[i] == intervalMs) cur = i;
            choose("刷新间隔", labels, cur, i -> {
                intervalMs = vals[i];
                sp.edit().putInt("interval", vals[i]).apply();
                refreshSettings();
            });
        });

        sectionTitle("进程列表");
        settingRow("默认排序", sortLabel(), () ->
                choose("默认排序", new String[]{"CPU", "内存", "名称"},
                        sortBy.equals("cpu") ? 0 : (sortBy.equals("mem") ? 1 : 2), i -> {
                            sortBy = i == 0 ? "cpu" : (i == 1 ? "mem" : "name");
                            sp.edit().putString("sort", sortBy).apply();
                            syncChips();
                            render(last);
                            refreshSettings();
                        }));

        settingRow("默认分类", filterLabel(), () ->
                choose("默认分类", new String[]{"全部", "系统", "用户"},
                        filterMode.equals("all") ? 0 : (filterMode.equals("system") ? 1 : 2), i -> {
                            filterMode = i == 0 ? "all" : (i == 1 ? "system" : "app");
                            sp.edit().putString("filter", filterMode).apply();
                            syncChips();
                            render(last);
                            refreshSettings();
                        }));

        settingRow("CPU 视图", cpuMode.equals("agg") ? "合并曲线" : "分核心", () ->
                choose("CPU 视图", new String[]{"合并曲线", "分核心"},
                        cpuMode.equals("agg") ? 0 : 1, i -> {
                            cpuMode = i == 0 ? "agg" : "per";
                            sp.edit().putString("cpuMode", cpuMode).apply();
                            syncChips();
                            applyCpuMode();
                            refreshSettings();
                        }));

        settingRow("进程视图", viewMode.equals("flat") ? "平铺" : "树形", () ->
                choose("进程视图", new String[]{"平铺", "树形"}, viewMode.equals("flat") ? 0 : 1, i -> {
                    viewMode = i == 0 ? "flat" : "tree";
                    sp.edit().putString("viewMode", viewMode).apply();
                    syncChips();
                    render(last);
                    refreshSettings();
                }));

        settingRow("隐藏内核线程", sp.getBoolean("hideKernel", false) ? "开" : "关", () -> {
            boolean v = !sp.getBoolean("hideKernel", false);
            sp.edit().putBoolean("hideKernel", v).apply();
            render(last);
            refreshSettings();
        });

        settingRow("隐藏本应用", sp.getBoolean("hideSelf", false) ? "开" : "关", () -> {
            boolean v = !sp.getBoolean("hideSelf", false);
            sp.edit().putBoolean("hideSelf", v).apply();
            render(last);
            refreshSettings();
        });

        sectionTitle("关于");
        TextView about = new TextView(this);
        about.setText("Android 任务管理器\n"
                + "监控 CPU / 内存 / 进程树；有 root 时功能完整，\n"
                + "无 root 时 CPU 由 cpufreq 频率推算。");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        about.setTextColor(C_SUB);
        about.setLineSpacing(dp(3), 1f);
        about.setPadding(dp(14), dp(8), dp(14), dp(8));
        settingsBox.addView(about, lp(-1, -2, 0, 0, 0, dp(10)));
    }

    private void sectionTitle(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(C_SUB);
        tv.setPadding(dp(6), dp(14), dp(6), dp(6));
        settingsBox.addView(tv);
    }

    private void settingRow(String title, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(round(C_CARD, dp(11)));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(C_TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView v = new TextView(this);
        v.setText(value + "  ›");
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(C_CPU);
        row.addView(v);

        row.setOnClickListener(x -> onClick.run());
        settingsBox.addView(row, lp(-1, -2, 0, 0, 0, dp(6)));
    }

    private void choose(String title, String[] items, int cur, IntConsumer cb) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, cur, (d, which) -> {
                    d.dismiss();
                    cb.accept(which);
                })
                .show();
    }

    private interface IntConsumer { void accept(int i); }

    private String intervalLabel() {
        if (intervalMs == 1000) return "1 秒";
        if (intervalMs == 1500) return "1.5 秒";
        if (intervalMs == 2000) return "2 秒";
        if (intervalMs == 3000) return "3 秒";
        return "5 秒";
    }

    private String sortLabel() {
        return sortBy.equals("cpu") ? "CPU" : (sortBy.equals("mem") ? "内存" : "名称");
    }

    private String filterLabel() {
        return filterMode.equals("all") ? "全部" : (filterMode.equals("system") ? "系统" : "用户");
    }

    /* ================= 数据循环 ================= */

    private void startLoop() {
        new Thread(() -> {
            while (running) {
                long t0 = System.currentTimeMillis();
                ProcCollector.Snapshot s = ProcCollector.collect();
                ui.post(() -> render(s));
                long cost = System.currentTimeMillis() - t0;
                try {
                    Thread.sleep(Math.max(200, intervalMs - cost));
                } catch (InterruptedException e) { return; }
            }
        }, "collector").start();
    }

    private void render(ProcCollector.Snapshot s) {
        if (s == null) return;
        last = s;
        ProcCollector.MemInfo m = s.mem;
        double memPct = m.memTotal > 0 ? m.used() * 100.0 / m.memTotal : 0;

        tvCpu.setText(String.format(Locale.ROOT, "%.1f%%", s.cpuPercent));
        tvModeHint.setText(s.cpuFromFreq ? "由 CPU 频率推算（无 root）" : "");
        setBar(trackCpu, barCpu, s.cpuPercent, C_CPU);
        sparkCpu.push((float) s.cpuPercent);
        tvCpuSub.setText(s.cpuFromFreq
                ? String.format(Locale.ROOT, "%d 核 · 频率推算模式", s.cores)
                : String.format(Locale.ROOT, "%d 核 · 负载 %.2f / %.2f / %.2f",
                s.cores, s.load1, s.load5, s.load15));
        renderCores(s);

        ProcCollector.Thermal th = ProcCollector.cpuThermal(s);
        if (th != null) {
            tvTemp.setText(String.format(Locale.ROOT, "温度 %.1f°C  ·  %s", th.celsius, th.type));
            tvTemp.setTextColor(th.celsius >= 60 ? C_WARN
                    : (th.celsius >= 45 ? C_SWAP : C_MEM));
        } else {
            tvTemp.setText("温度 —（未找到 thermal 节点）");
            tvTemp.setTextColor(C_SUB);
        }

        if (m.memTotal > 0) {
            tvMem.setText(String.format(Locale.ROOT, "%.1f / %.1f GB",
                    m.used() / 1073741824.0, m.memTotal / 1073741824.0));
            setBar(trackMem, barMem, memPct, C_MEM);
            sparkMem.push((float) memPct);
            tvMemSub.setText(String.format(Locale.ROOT, "可用 %.1f GB · 占用 %.0f%%",
                    m.memAvail / 1073741824.0, memPct));
            StringBuilder sw = new StringBuilder();
            if (m.swapTotal > 0) {
                sw.append(String.format(Locale.ROOT, "Swap %.1f / %.1f GB",
                        m.swapUsed() / 1073741824.0, m.swapTotal / 1073741824.0));
            } else sw.append("无 Swap");
            for (ProcCollector.Zram z : s.zrams) {
                sw.append(String.format(Locale.ROOT, " · %s[%s] %.1f×", z.dev, z.algo, z.ratio()));
            }
            tvSwap.setText(sw.toString());
        }

        if (s.error != null && !s.error.isEmpty() && s.procs.isEmpty()) {
            list.removeAllViews();
            list.addView(errorView(s.error));
            return;
        }
        renderTree(s);
        if ("details".equals(tab)) renderDetails(s);
        if ((tick++ % 5) == 0) updateDeviceInfo();
    }

    /* ================= 树形列表 ================= */

    private Comparator<ProcCollector.Proc> cmp() {
        if (sortBy.equals("cpu")) return (a, b) -> Double.compare(b.cpu, a.cpu);
        if (sortBy.equals("mem")) return (a, b) -> Long.compare(b.rssBytes, a.rssBytes);
        return Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT));
    }

    private void renderTree(ProcCollector.Snapshot s) {
        List<ProcCollector.Proc> all = s.procs;
        filterCache.clear();
        for (ProcCollector.Proc p : all) filterCache.add(p);

        int sysN = 0, appN = 0;
        for (ProcCollector.Proc p : all) { if (p.isSystem()) sysN++; else appN++; }
        chipAll.setText("全部(" + all.size() + ")");
        chipSys.setText("系统(" + sysN + ")");
        chipApp.setText("用户(" + appN + ")");
        if (dChipAll != null) {
            dChipAll.setText("全部(" + all.size() + ")");
            dChipSys.setText("系统(" + sysN + ")");
            dChipApp.setText("用户(" + appN + ")");
        }
        tvProc.setText("共 " + all.size() + " 个进程");

        boolean hideKernel = sp.getBoolean("hideKernel", false);
        boolean hideSelf = sp.getBoolean("hideSelf", false);

        List<ProcCollector.Proc> base = new ArrayList<>();
        for (ProcCollector.Proc p : all) {
            if (filterMode.equals("system") && !p.isSystem()) continue;
            if (filterMode.equals("app") && p.isSystem()) continue;
            if (hideKernel && p.ppid == 2) continue;
            if (hideSelf && p.pid == selfPid) continue;
            if (!keyword.isEmpty()
                    && !p.name.toLowerCase(Locale.ROOT).contains(keyword)
                    && !String.valueOf(p.pid).equals(keyword)) continue;
            base.add(p);
        }

        Set<Integer> pids = new HashSet<>();
        for (ProcCollector.Proc p : base) pids.add(p.pid);

        Map<Integer, List<ProcCollector.Proc>> children = new HashMap<>();
        List<ProcCollector.Proc> roots = new ArrayList<>();
        for (ProcCollector.Proc p : base) {
            if (pids.contains(p.ppid) && p.ppid != p.pid) {
                List<ProcCollector.Proc> l = children.get(p.ppid);
                if (l == null) { l = new ArrayList<>(); children.put(p.ppid, l); }
                l.add(p);
            } else roots.add(p);
        }
        Comparator<ProcCollector.Proc> c = cmp();
        roots.sort(c);
        for (List<ProcCollector.Proc> l : children.values()) l.sort(c);

        list.removeAllViews();
        if (viewMode.equals("flat")) {
            renderFlat(base);
            return;
        }
        if (roots.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(keyword.isEmpty() ? "此分类下没有进程" : "没有匹配「" + keyword + "」的进程");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setTextColor(C_SUB);
            empty.setPadding(dp(4), dp(16), dp(4), dp(16));
            list.addView(empty);
            return;
        }
        int[] budget = {180};
        for (ProcCollector.Proc r : roots) addNode(r, 0, children, budget);
        if (budget[0] <= 0) {
            TextView more = new TextView(this);
            more.setText("… 已截断，可用搜索或选项卡缩小范围");
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            more.setTextColor(C_SUB);
            more.setPadding(dp(4), dp(8), dp(4), dp(8));
            list.addView(more);
        }
    }

    private final List<ProcCollector.Proc> filterCache = new ArrayList<>();

    private void renderFlat(List<ProcCollector.Proc> ps) {
        if (ps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(keyword.isEmpty() ? "此分类下没有进程" : "没有匹配「" + keyword + "」的进程");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setTextColor(C_SUB);
            empty.setPadding(dp(4), dp(16), dp(4), dp(16));
            list.addView(empty);
            return;
        }
        List<ProcCollector.Proc> sorted = new ArrayList<>(ps);
        sorted.sort(cmp());
        int cap = Math.min(sorted.size(), 200);
        for (int i = 0; i < cap; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            list.addView(row(sorted.get(i), 0, false, false, 0), lp);
        }
        if (sorted.size() > cap) {
            TextView more = new TextView(this);
            more.setText("… 共 " + sorted.size() + " 个，已显示前 " + cap + " 个");
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            more.setTextColor(C_SUB);
            more.setPadding(dp(4), dp(8), dp(4), dp(8));
            list.addView(more);
        }
    }

    private void addNode(ProcCollector.Proc p, int depth,
                         Map<Integer, List<ProcCollector.Proc>> children, int[] budget) {
        if (budget[0]-- <= 0) return;
        List<ProcCollector.Proc> kids = children.get(p.pid);
        boolean hasKids = kids != null && !kids.isEmpty();
        boolean open = expanded.contains(p.pid);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(4);
        list.addView(row(p, depth, hasKids, open, hasKids ? kids.size() : 0), lp);
        if (hasKids && open) for (ProcCollector.Proc k : kids) addNode(k, depth + 1, children, budget);
    }

    private View row(ProcCollector.Proc p, int depth, boolean hasKids, boolean open, int childCount) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(10), dp(8), dp(8), dp(8));
        wrap.setBackground(round(depth == 0 ? C_CARD : C_CARD2, dp(10)));

        if (depth > 0) {
            View indent = new View(this);
            wrap.addView(indent, new LinearLayout.LayoutParams(dp(14 * depth), 1));
        }

        TextView arrow = new TextView(this);
        arrow.setText(hasKids ? (open ? "▾" : "▸") : "");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        arrow.setTextColor(hasKids ? C_CPU : C_SUB);
        arrow.setWidth(dp(18));
        arrow.setGravity(Gravity.CENTER);
        if (hasKids) {
            arrow.setOnClickListener(v -> {
                if (expanded.contains(p.pid)) expanded.remove(p.pid);
                else expanded.add(p.pid);
                render(last);
            });
        }
        wrap.addView(arrow);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(p.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(p.pid == selfPid ? C_CPU : C_TEXT);
        name.setSingleLine(true);
        left.addView(name);

        TextView sub = new TextView(this);
        StringBuilder sb = new StringBuilder("PID ").append(p.pid);
        if (p.pid == selfPid) sb.append(" · 本应用");
        else sb.append(" · uid ").append(p.uid);
        if (hasKids) sb.append(" · 子进程 ").append(childCount);
        sub.setText(sb);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        sub.setTextColor(C_SUB);
        left.addView(sub);
        wrap.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView cpu = new TextView(this);
        cpu.setText(String.format(Locale.ROOT, "%5.1f%%", p.cpu));
        cpu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cpu.setTextColor(p.cpu > 20 ? C_WARN : C_CPU);
        cpu.setGravity(Gravity.END);
        cpu.setWidth(dp(54));
        wrap.addView(cpu);

        TextView mem = new TextView(this);
        mem.setText(p.rssBytes >= 1073741824L
                ? String.format(Locale.ROOT, "%.1fG", p.rssBytes / 1073741824.0)
                : String.format(Locale.ROOT, "%dM", p.rssBytes / 1048576L));
        mem.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mem.setTextColor(C_SUB);
        mem.setGravity(Gravity.END);
        mem.setWidth(dp(52));
        wrap.addView(mem);

        TextView kill = new TextView(this);
        kill.setText("结束");
        kill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        kill.setTextColor(C_WARN);
        kill.setPadding(dp(7), dp(4), dp(7), dp(4));
        kill.setBackground(round(C_DANGER_BG, dp(10)));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-2, -2);
        klp.leftMargin = dp(5);
        wrap.addView(kill, klp);
        kill.setOnClickListener(v -> confirmKill(p));

        wrap.setOnClickListener(v -> showDetail(p));
        return wrap;
    }

    /* ================= 详情 ================= */

    private void showCpuDetail() {
        ProcCollector.Snapshot s = last;
        if (s == null) return;
        ProcCollector.CpuTimes t = s.cpuTimes;
        long sum = Math.max(1, t.sum());

        StringBuilder sb = new StringBuilder();
        if (!rootOk) sb.append("⚠ 未获得 root：以下 CPU 时间为 0，频率来自 cpufreq\n\n");
        sb.append("芯片：").append(socName()).append('\n');
        sb.append("核心数：").append(s.cores).append('\n');
        if (!s.coreList.isEmpty()) {
            sb.append("频率范围：").append(ProcCollector.ghz(s.coreList.get(0).minKhz))
                    .append(" ~ ").append(ProcCollector.ghz(s.coreList.get(0).maxKhz)).append("\n\n");
            sb.append("【每核当前频率 / 占用】\n");
            for (ProcCollector.Core c : s.coreList) {
                sb.append("  CPU").append(c.id).append("  ")
                        .append(ProcCollector.ghz(c.curKhz)).append("  ")
                        .append(String.format(Locale.ROOT, "%.1f%%", c.pct)).append('\n');
            }
            sb.append('\n');
        }
        sb.append("【CPU 时间构成】\n");
        sb.append(fmtShare("user     用户态", t.user, sum));
        sb.append(fmtShare("nice     低优先级", t.nice, sum));
        sb.append(fmtShare("system   内核态", t.system, sum));
        sb.append(fmtShare("idle     空闲", t.idle, sum));
        sb.append(fmtShare("iowait   等待 IO", t.iowait, sum));
        sb.append(fmtShare("irq      硬中断", t.irq, sum));
        sb.append(fmtShare("softirq  软中断", t.softirq, sum));
        sb.append(fmtShare("steal    虚拟化占用", t.steal, sum));
        sb.append('\n');
        if (!s.thermals.isEmpty()) {
            sb.append("【温度】\n");
            int n = 0;
            for (ProcCollector.Thermal th : s.thermals) {
                if (n++ >= 12) break;
                sb.append(String.format(Locale.ROOT, "  %-22s %.1f°C\n", th.type, th.celsius));
            }
            sb.append('\n');
        }
        sb.append("【系统活动】\n");
        sb.append("  上下文切换：").append(fmtNum(t.ctxt)).append('\n');
        sb.append("  中断次数：").append(fmtNum(t.intr)).append('\n');
        sb.append("  已创建进程：").append(fmtNum(t.procsCreated)).append('\n');
        sb.append("  运行中 / 阻塞：").append(t.procsRunning).append(" / ").append(t.procsBlocked).append('\n');
        sb.append("  负载 1/5/15：").append(String.format(Locale.ROOT, "%.2f / %.2f / %.2f",
                s.load1, s.load5, s.load15)).append('\n');
        sb.append("  开机时长：").append(fmtDuration((long) s.uptime)).append('\n');

        showLongDialog("CPU 详细信息", sb.toString());
    }

    private void showMemDetail() {
        ProcCollector.Snapshot s = last;
        if (s == null) return;
        ProcCollector.MemInfo m = s.mem;
        StringBuilder sb = new StringBuilder();
        sb.append("【物理内存】\n");
        sb.append(kv("总量", gb(m.memTotal)));
        sb.append(kv("已用", gb(m.used())));
        sb.append(kv("可用", gb(m.memAvail)));
        sb.append(kv("空闲", gb(m.memFree)));
        sb.append(kv("Buffers", gb(m.buffers)));
        sb.append(kv("Cached", gb(m.cached)));
        sb.append(kv("Active", gb(m.active)));
        sb.append(kv("Inactive", gb(m.inactive)));
        sb.append(kv("AnonPages", gb(m.anonPages)));
        sb.append(kv("Mapped", gb(m.mapped)));
        sb.append(kv("Shmem", gb(m.shmem)));
        sb.append(kv("Slab", gb(m.slab)));
        sb.append(kv("PageTables", gb(m.pageTables)));
        sb.append(kv("内核栈", gb(m.kernelStack)));

        sb.append("\n【虚拟内存】\n");
        sb.append(kv("VmallocTotal", gb(m.vmallocTotal)));
        sb.append(kv("VmallocUsed", gb(m.vmallocUsed)));
        sb.append(kv("VmallocChunk", gb(m.vmallocChunk)));
        sb.append(kv("CommitLimit", gb(m.commitLimit)));
        sb.append(kv("Committed_AS", gb(m.committedAs) + "（可超售，大于 limit 属正常）"));

        sb.append("\n【Swap】\n");
        sb.append(kv("总量", gb(m.swapTotal)));
        sb.append(kv("已用", gb(m.swapUsed())));
        sb.append(kv("空闲", gb(m.swapFree)));
        sb.append(kv("SwapCached", gb(m.swapCached)));
        if (s.swaps != null && !s.swaps.isEmpty()) {
            sb.append("\n【/proc/swaps】\n");
            for (String ln : s.swaps.split("\n")) sb.append("  ").append(ln).append('\n');
        }
        if (!s.zrams.isEmpty()) {
            sb.append("\n【zram】\n");
            for (ProcCollector.Zram z : s.zrams) {
                sb.append("  · ").append(z.dev).append('\n');
                sb.append("    压缩算法：").append(z.algo).append('\n');
                sb.append("    设备大小：").append(gb(z.disksize)).append('\n');
                sb.append("    原始数据：").append(gb(z.origData)).append('\n');
                sb.append("    压缩后  ：").append(gb(z.comprData)).append('\n');
                sb.append("    实际占用：").append(gb(z.memUsed)).append('\n');
                sb.append("    压缩比  ：").append(String.format(Locale.ROOT, "%.2f×", z.ratio())).append('\n');
                if (z.origData > 0) {
                    sb.append(String.format(Locale.ROOT, "    节省    ：%.1f%%\n",
                            (1 - (double) z.comprData / z.origData) * 100));
                }
            }
        } else {
            sb.append("\n【zram】\n  ").append(rootOk
                    ? "未检测到 zram 设备" : "需要 root 才能读取 /sys/block/zram*").append('\n');
        }
        showLongDialog("内存详细信息", sb.toString());
    }

    private void showLongDialog(String title, String content) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        // 不指定颜色：跟随 Activity 主题（浅色主题 = 深字，深色主题 = 浅字）
        tv.setPadding(dp(20), dp(16), dp(20), dp(16));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        sv.addView(tv, new ViewGroup.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle(title).setView(sv)
                .setPositiveButton("关闭", null).show();
    }

    private String kv(String k, String v) { return String.format(Locale.ROOT, "  %-18s %s\n", k, v); }

    private String fmtShare(String label, long v, long sum) {
        return String.format(Locale.ROOT, "  %-18s %6.2f%%  (%s)\n", label, v * 100.0 / sum, fmtTicks(v));
    }

    private String fmtTicks(long ticks) {
        long s = ticks / 100;
        return s >= 3600 ? String.format(Locale.ROOT, "%dh%02dm", s / 3600, (s % 3600) / 60)
                : String.format(Locale.ROOT, "%dm%02ds", s / 60, s % 60);
    }

    private String fmtNum(long v) {
        if (v > 1000000000L) return String.format(Locale.ROOT, "%.2f G", v / 1e9);
        if (v > 1000000L) return String.format(Locale.ROOT, "%.2f M", v / 1e6);
        if (v > 1000L) return String.format(Locale.ROOT, "%.1f K", v / 1e3);
        return String.valueOf(v);
    }

    private String gb(long b) {
        if (b <= 0) return "—";
        double g = b / 1073741824.0;
        return g >= 0.1 ? String.format(Locale.ROOT, "%.2f GB", g)
                : String.format(Locale.ROOT, "%.0f MB", b / 1048576.0);
    }

    private String fmtDuration(long sec) {
        return String.format(Locale.ROOT, "%d 天 %d 小时 %d 分",
                sec / 86400, (sec % 86400) / 3600, (sec % 3600) / 60);
    }

    private String socName() {
        try {
            if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null
                    && !Build.SOC_MODEL.isEmpty() && !Build.SOC_MODEL.equals("unknown")) {
                return Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL;
            }
        } catch (Throwable ignored) { }
        return Build.HARDWARE + " / " + Build.BOARD;
    }

    /* ================= 交互 ================= */

    private void confirmKill(ProcCollector.Proc p) {
        if (!rootOk) { toast("需要 root"); return; }
        if (p.pid <= 1) { toast("不能结束系统核心进程"); return; }
        if (p.pid == selfPid) { toast("不能结束自己"); return; }
        new AlertDialog.Builder(this).setTitle("结束进程")
                .setMessage(p.name + "\nPID " + p.pid + " · uid " + p.uid + "\n\n强制结束可能导致异常退出。")
                .setPositiveButton("结束", (d, w) -> new Thread(() -> {
                    boolean ok = ProcCollector.kill(p.pid);
                    ui.post(() -> toast(ok ? "已结束 " + p.name : "结束失败"));
                }).start())
                .setNegativeButton("取消", null).show();
    }

    private void showDetail(ProcCollector.Proc p) {
        String sb = "PID：" + p.pid + '\n' +
                "父进程：" + p.ppid + '\n' +
                "UID：" + p.uid + (p.isSystem() ? "（系统）" : "（应用）") + '\n' +
                "状态：" + ProcCollector.stateName(p.state) + '\n' +
                "线程数：" + p.threads + '\n' +
                "优先级：" + p.priority + "  nice：" + p.nice + '\n' +
                "CPU：" + String.format(Locale.ROOT, "%.1f%%", p.cpu) + '\n' +
                "内存(RSS)：" + gb(p.rssBytes) + '\n' +
                "虚拟内存(VSZ)：" + gb(p.vsize) + '\n' +
                "运行时长：" + ProcCollector.fmtUptime(last, p.startTicks);
        new AlertDialog.Builder(this).setTitle(p.name).setMessage(sb)
                .setPositiveButton("关闭", null).show();
    }

    /* ================= 小组件 ================= */

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(C_CARD, dp(14)));
        return c;
    }

    private TextView pill(String s, int fg, int bg) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(fg);
        tv.setPadding(dp(11), dp(6), dp(11), dp(6));
        tv.setBackground(round(bg, dp(16)));
        return tv;
    }

    private FrameLayout track(int color) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(round(C_TRACK, dp(3)));
        View v = new View(this);
        v.setBackground(round(color, dp(3)));
        f.addView(v, new FrameLayout.LayoutParams(0, -1));
        return f;
    }

    private TextView chip(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void setFilter(String m) {
        filterMode = m;
        sp.edit().putString("filter", m).apply();
        syncChips();
        render(last);
        renderDetails(last);
    }

    private void setViewMode(String m) {
        viewMode = m;
        sp.edit().putString("viewMode", m).apply();
        syncChips();
        render(last);
    }

    private void syncChips() {
        chipCpu.setText(sortBy.equals("cpu") ? "CPU↓" : "CPU");
        chipMem.setText(sortBy.equals("mem") ? "内存↓" : "内存");
        chipName.setText(sortBy.equals("name") ? "名称↓" : "名称");
        style(chipCpu, sortBy.equals("cpu"));
        style(chipMem, sortBy.equals("mem"));
        style(chipName, sortBy.equals("name"));
        style(chipAll, filterMode.equals("all"));
        style(chipSys, filterMode.equals("system"));
        style(chipApp, filterMode.equals("app"));
        if (dChipAll != null) {
            style(dChipAll, filterMode.equals("all"));
            style(dChipSys, filterMode.equals("system"));
            style(dChipApp, filterMode.equals("app"));
        }
        style(chipAgg, cpuMode.equals("agg"));
        style(chipPer, cpuMode.equals("per"));
        if (chipFlat != null) {
            style(chipFlat, viewMode.equals("flat"));
            style(chipTree, viewMode.equals("tree"));
        }
    }

    private void style(TextView tv, boolean on) {
        tv.setTextColor(on ? 0xFF101012 : C_TEXT);
        tv.setBackground(round(on ? C_CPU : C_CHIP, dp(13)));
    }

    private void setBar(FrameLayout track, View fill, double pct, int color) {
        track.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
            lp.width = (int) (track.getWidth() * Math.max(0, Math.min(100, pct)) / 100.0);
            fill.setLayoutParams(lp);
            fill.setBackground(round(color, dp(3)));
        });
    }

    private View errorView(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(C_WARN);
        tv.setPadding(dp(4), dp(12), dp(4), dp(12));
        return tv;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(r);
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
