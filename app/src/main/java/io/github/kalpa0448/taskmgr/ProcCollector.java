package io.github.kalpa0448.taskmgr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 采集器 v3：在 v2 基础上增加
 *  - 每核 CPU 占用 + 频率
 *  - CPU 时间构成 / 上下文切换 / 中断
 *  - 物理内存明细、虚拟内存(Vmalloc/Commit)、Swap、zram(含压缩算法)
 */
public final class ProcCollector {

    public static class Proc {
        public int pid;
        public String name = "?";
        public String state = "?";
        public int ppid, uid, threads, priority, nice;
        public long vsize, rssBytes, startTicks;
        public double cpu;
        public String arch = "—";   // 32 / 64 / —
        public boolean isSystem() { return uid >= 0 && uid < 10000; }
    }

    public static class Core {
        public int id;
        public double pct;
        public long curKhz, minKhz, maxKhz;
    }

    public static class Zram {
        public String dev = "zram0";
        public long disksize, origData, comprData, memUsed;
        public String algo = "?";
        public double ratio() { return comprData > 0 ? (double) origData / comprData : 0; }
    }

    public static class CpuTimes {
        public long user, nice, system, idle, iowait, irq, softirq, steal;
        public long ctxt, intr, procsCreated, procsRunning, procsBlocked;
        public long sum() { return user + nice + system + idle + iowait + irq + softirq + steal; }
        public long busy() { return sum() - idle - iowait; }
    }

    public static class MemInfo {
        public long memTotal, memFree, memAvail, buffers, cached, swapCached,
                active, inactive, anonPages, mapped, shmem, slab, sreclaimable,
                kernelStack, pageTables, vmallocTotal, vmallocUsed, vmallocChunk,
                commitLimit, committedAs, swapTotal, swapFree;
        public long used() { return Math.max(0, memTotal - memAvail); }
        public long cachedTotal() { return cached + buffers; }
        public long swapUsed() { return Math.max(0, swapTotal - swapFree); }
        public double commitRatio() { return commitLimit > 0 ? committedAs * 100.0 / commitLimit : 0; }
    }

    public static class Thermal {
        public String type = "?";
        public double celsius;
    }

    public static class Snapshot {
        public boolean root;
        public String error;
        public double cpuPercent;
        public boolean complete = true;   // 本轮采集是否完整（防止截断数据污染 UI）
        public boolean loading = false;   // 首轮数据还没就绪
        public boolean cpuFromFreq;   // true=由 cpufreq 推算（无 root）
        public int cores;
        public CpuTimes cpuTimes = new CpuTimes();
        public List<Core> coreList = new ArrayList<>();
        public MemInfo mem = new MemInfo();
        public List<Zram> zrams = new ArrayList<>();
        public List<Thermal> thermals = new ArrayList<>();
        public String swaps = "";
        public double load1, load5, load15;
        public double uptime;
        public int procCount;
        public List<Proc> procs = new ArrayList<>();
        public double diskReadBps, diskWriteBps, netRxBps, netTxBps;
        public long diskUsed, diskTotal, netRxTotal, netTxTotal;
    }

    private static final int PAGE = 4096, HZ = 100;
    private static final String SEP = "@@@@";
    private static Boolean rootCache = null;
    private static List<Proc> lastGood = null;

    private static long prevTotal = -1, prevIdle = -1;
    private static final Map<Integer, Long> prevProc = new HashMap<>();
    private static final Map<Integer, long[]> prevCore = new HashMap<>(); // id -> {total, idle}
    private static final Map<Integer, String> archCache = new HashMap<>();
    private static final java.util.Set<Integer> archTried = new java.util.HashSet<>();

    /* ---------------- root ---------------- */

    public static synchronized boolean hasRoot() {
        if (rootCache != null) return rootCache;
        try {
            String out = su("id");
            rootCache = out != null && out.contains("uid=0");
        } catch (Throwable t) { rootCache = false; }
        return rootCache;
    }

    public static String su(String cmd) throws IOException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException ignored) { }
        try { p.waitFor(20, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        p.destroy();
        return sb.toString();
    }

    /* ---------------- 采集命令 ---------------- */

    private static final String AWK =
            "awk 'function lp(s,  i,idx){idx=0;for(i=1;i<=length(s);i++)if(substr(s,i,1)==\")\")idx=i;return idx}"
                    + "FNR==1{"
                    + "split(FILENAME,p,\"/\");pid=p[3];"
                    + "name=\"\";cf=\"/proc/\" pid \"/comm\";if((getline nm<cf)>0)name=nm;close(cf);"
                    + "uid=-1;sf=\"/proc/\" pid \"/status\";"
                    + "while((getline sl<sf)>0){if(sl ~ /^Uid:/){split(sl,u,\" \");uid=u[2]+0;break}}close(sf);"
                    + "idx=lp($0);rest=substr($0,idx+2);n=split(rest,f,\" \");"
                    + "printf \"%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\t%s\\n\","
                    + "pid,name,f[1],f[2],f[12],f[13],f[16],f[17],f[18],f[20],f[21],f[22],uid"
                    + "}' /proc/[0-9]*/stat 2>/dev/null";

    private static final String ZRAM_CMD =
            "for z in /sys/block/zram*; do [ -d \"$z\" ] || continue; "
                    + "echo \"Z:${z##*/}\"; "
                    + "echo \"D:$(cat $z/disksize 2>/dev/null)\"; "
                    + "echo \"A:$(cat $z/comp_algorithm 2>/dev/null)\"; "
                    + "echo \"M:$(cat $z/mm_stat 2>/dev/null)\"; "
                    + "done";

    private static final String FREQ_CMD =
            "for c in /sys/devices/system/cpu/cpu[0-9]*; do [ -d \"$c\" ] || continue; "
                    + "echo \"C:${c##*/cpu}\"; "
                    + "echo \"F:$(cat $c/cpufreq/scaling_cur_freq 2>/dev/null)\"; "
                    + "echo \"N:$(cat $c/cpufreq/cpuinfo_min_freq 2>/dev/null)\"; "
                    + "echo \"X:$(cat $c/cpufreq/cpuinfo_max_freq 2>/dev/null)\"; "
                    + "done";

    private static final String THERMAL_CMD =
            "for z in /sys/class/thermal/thermal_zone*; do [ -d \"$z\" ] || continue; "
                    + "t=$(cat $z/type 2>/dev/null); v=$(cat $z/temp 2>/dev/null); "
                    + "[ -n \"$v\" ] && echo \"T:$t|$v\"; done";

    private static final String ONE_SHOT =
            "cat /proc/stat; echo '" + SEP + "'; "
                    + "cat /proc/meminfo; echo '" + SEP + "'; "
                    + "cat /proc/loadavg; echo '" + SEP + "'; "
                    + "cat /proc/uptime; echo '" + SEP + "'; "
                    + "cat /proc/swaps; echo '" + SEP + "'; "
                    + ZRAM_CMD + "; echo '" + SEP + "'; "
                    + FREQ_CMD + "; echo '" + SEP + "'; "
                    + THERMAL_CMD + "; echo '" + SEP + "'; "
                    + AWK
                    + "; echo '@@@END@@@'";

    /* ---------------- 采集 ---------------- */

    public static Snapshot collect() {
        Snapshot s = new Snapshot();
        s.root = hasRoot();
        s.cores = Runtime.getRuntime().availableProcessors();

        String[] seg = new String[9];
        if (s.root) {
            try {
                String out = su(ONE_SHOT);
                if (out == null || !out.contains("@@@END@@@")) {
                    s.complete = false;      // 被截断（超时/进程被杀）
                }
                String[] p = out.split(SEP, -1);
                for (int i = 0; i < Math.min(p.length, 9); i++) seg[i] = p[i];
            } catch (Throwable t) {
                s.error = T.s("读取失败: ") + t;
            }
        } else {
            // 某些 ROM 允许直接读，先试；读不到（SELinux 拒绝）返回 null
            seg[0] = ReadFile("/proc/stat", 65536);
            seg[1] = ReadFile("/proc/meminfo", 32768);
            seg[2] = ReadFile("/proc/loadavg", 128);
            seg[3] = ReadFile("/proc/uptime", 128);
            seg[4] = ReadFile("/proc/swaps", 2048);
            if (seg[0] == null || seg[0].trim().isEmpty()) {
                s.cpuFromFreq = true;      // 走 cpufreq 推算
            }
            s.error = T.s("无 root：进程列表/swap/zram 不可用；CPU 由频率推算。");
        }

        /* ---- CPU 总与每核 ---- */
        long total = 0, idle = 0;
        if (seg[0] != null) {
            for (String ln : seg[0].split("\n")) {
                ln = ln.trim();
                if (ln.startsWith("cpu ")) {
                    long[] t = parseCpuLine(ln);
                    total = t[0]; idle = t[1];
                    s.cpuTimes = parseTimes(ln);
                } else if (ln.matches("^cpu\\d+\\s.*")) {
                    int id = Integer.parseInt(ln.substring(3, ln.indexOf(' ')));
                    long[] t = parseCpuLine(ln);
                    Core c = new Core();
                    c.id = id;
                    long[] prev = prevCore.get(id);
                    if (prev != null && t[0] > prev[0]) {
                        long dt = t[0] - prev[0];
                        long di = Math.max(0, t[1] - prev[1]);
                        if (dt > 0) c.pct = clamp((dt - di) * 100.0 / dt);
                    }
                    prevCore.put(id, t);
                    s.coreList.add(c);
                } else if (ln.startsWith("ctxt ")) s.cpuTimes.ctxt = parseLong(ln.substring(5));
                else if (ln.startsWith("intr ")) s.cpuTimes.intr = parseLong(ln.substring(5).split("\\s+")[0]);
                else if (ln.startsWith("processes ")) s.cpuTimes.procsCreated = parseLong(ln.substring(10));
                else if (ln.startsWith("procs_running ")) s.cpuTimes.procsRunning = parseLong(ln.substring(14));
                else if (ln.startsWith("procs_blocked ")) s.cpuTimes.procsBlocked = parseLong(ln.substring(14));
            }
        }
        long deltaTotal = 0;
        if (prevTotal > 0 && total > prevTotal) {
            deltaTotal = total - prevTotal;
            long dIdle = Math.max(0, idle - prevIdle);
            if (deltaTotal > 0) s.cpuPercent = clamp((deltaTotal - dIdle) * 100.0 / deltaTotal);
        }
        prevTotal = total;
        prevIdle = idle;

        if (s.cpuFromFreq) fillFromFreq(s);

        /* ---- 内存 ---- */
        if (seg[1] != null) {
            MemInfo m = new MemInfo();
            for (String ln : seg[1].split("\n")) {
                String[] f = ln.trim().split("\\s+");
                if (f.length < 2) continue;
                long v = parseLong(f[1]) * 1024;
                switch (f[0]) {
                    case "MemTotal:": m.memTotal = v; break;
                    case "MemFree:": m.memFree = v; break;
                    case "MemAvailable:": m.memAvail = v; break;
                    case "Buffers:": m.buffers = v; break;
                    case "Cached:": m.cached = v; break;
                    case "SwapCached:": m.swapCached = v; break;
                    case "Active:": m.active = v; break;
                    case "Inactive:": m.inactive = v; break;
                    case "AnonPages:": m.anonPages = v; break;
                    case "Mapped:": m.mapped = v; break;
                    case "Shmem:": m.shmem = v; break;
                    case "Slab:": m.slab = v; break;
                    case "SReclaimable:": m.sreclaimable = v; break;
                    case "KernelStack:": m.kernelStack = v; break;
                    case "PageTables:": m.pageTables = v; break;
                    case "VmallocTotal:": m.vmallocTotal = v; break;
                    case "VmallocUsed:": m.vmallocUsed = v; break;
                    case "VmallocChunk:": m.vmallocChunk = v; break;
                    case "CommitLimit:": m.commitLimit = v; break;
                    case "Committed_AS:": m.committedAs = v; break;
                    case "SwapTotal:": m.swapTotal = v; break;
                    case "SwapFree:": m.swapFree = v; break;
                }
            }
            s.mem = m;
        }

        /* ---- 负载 / uptime ---- */
        if (seg[2] != null && !seg[2].trim().isEmpty()) {
            try {
                String[] f = seg[2].trim().split("\\s+");
                s.load1 = Double.parseDouble(f[0]);
                if (f.length > 1) s.load5 = Double.parseDouble(f[1]);
                if (f.length > 2) s.load15 = Double.parseDouble(f[2]);
            } catch (Throwable ignored) { }
        }
        if (seg[3] != null && !seg[3].trim().isEmpty()) {
            try { s.uptime = Double.parseDouble(seg[3].trim().split("\\s+")[0]); } catch (Throwable ignored) { }
        }

        /* ---- swap 设备 ---- */
        if (seg[4] != null) s.swaps = seg[4].trim();

        /* ---- zram ---- */
        if (seg[5] != null) {
            Zram z = null;
            for (String ln : seg[5].split("\n")) {
                ln = ln.trim();
                if (ln.startsWith("Z:")) {
                    z = new Zram();
                    z.dev = ln.substring(2);
                    s.zrams.add(z);
                } else if (z != null) {
                    if (ln.startsWith("D:")) z.disksize = parseLong(ln.substring(2));
                    else if (ln.startsWith("A:")) {
                        String a = ln.substring(2).trim();
                        // 形如 "[lz4] lz4hc deflate zstd"，方括号内为当前算法
                        int l = a.indexOf('['), r = a.indexOf(']');
                        z.algo = (l >= 0 && r > l) ? a.substring(l + 1, r) : a;
                    } else if (ln.startsWith("M:")) {
                        String[] f = ln.substring(2).trim().split("\\s+");
                        if (f.length >= 3) {
                            z.origData = parseLong(f[0]);
                            z.comprData = parseLong(f[1]);
                            z.memUsed = parseLong(f[2]);
                        }
                    }
                }
            }
        }

        /* ---- 每核频率 ---- */
        if (seg[6] != null) {
            Core cur = null;
            for (String ln : seg[6].split("\n")) {
                ln = ln.trim();
                if (ln.startsWith("C:")) {
                    int id = (int) parseLong(ln.substring(2));
                    cur = null;
                    for (Core c : s.coreList) if (c.id == id) { cur = c; break; }
                    if (cur == null) { cur = new Core(); cur.id = id; s.coreList.add(cur); }
                } else if (cur != null) {
                    if (ln.startsWith("F:")) cur.curKhz = parseLong(ln.substring(2));
                    else if (ln.startsWith("N:")) cur.minKhz = parseLong(ln.substring(2));
                    else if (ln.startsWith("X:")) cur.maxKhz = parseLong(ln.substring(2));
                }
            }
            s.coreList.sort((a, b) -> Integer.compare(a.id, b.id));
        }

        /* ---- 温度 ---- */
        if (s.root && seg[7] != null) {
            for (String ln : seg[7].split("\n")) {
                ln = ln.trim();
                if (!ln.startsWith("T:")) continue;
                int bar = ln.indexOf('|');
                if (bar < 0) continue;
                String type = ln.substring(2, bar).trim();
                double v = parseLong(ln.substring(bar + 1)) / 1000.0;
                if (v <= 0 || v > 200) continue;
                Thermal t = new Thermal();
                t.type = type.isEmpty() ? "?" : type;
                t.celsius = v;
                s.thermals.add(t);
            }
        } else if (!s.root) {
            for (int i = 0; i < 40; i++) {
                String base = "/sys/class/thermal/thermal_zone" + i + "/";
                String type = ReadFile(base + "type", 64);
                String val = ReadFile(base + "temp", 32);
                if (type == null || val == null) continue;
                double v = parseLong(val) / 1000.0;
                if (v <= 0 || v > 200) continue;
                Thermal t = new Thermal();
                t.type = type.trim();
                t.celsius = v;
                s.thermals.add(t);
            }
        }
        s.thermals.sort((a, b) -> Double.compare(b.celsius, a.celsius));

        /* ---- 进程 ---- */
        if (seg[8] != null) {
            Map<Integer, Long> now = new HashMap<>();
            long dTotal = Math.max(1, deltaTotal);
            for (String ln : seg[8].split("\n")) {
                if (ln.isEmpty()) continue;
                String[] f = ln.split("\t", -1);
                if (f.length < 13) continue;
                int pid = (int) parseLong(f[0]);
                if (pid <= 0) continue;
                long jif = parseLong(f[4]) + parseLong(f[5]);
                now.put(pid, jif);

                Proc p = new Proc();
                p.pid = pid;
                p.name = f[1].trim().isEmpty() ? ("pid " + pid) : f[1].trim();
                p.state = f[2].trim();
                p.ppid = (int) parseLong(f[3]);
                p.priority = (int) parseLong(f[6]);
                p.nice = (int) parseLong(f[7]);
                p.threads = (int) parseLong(f[8]);
                p.startTicks = parseLong(f[9]);
                p.vsize = parseLong(f[10]);
                p.rssBytes = Math.max(0, parseLong(f[11])) * PAGE;
                p.uid = (int) parseLong(f[12]);

                Long before = prevProc.get(pid);
                if (before != null && deltaTotal > 0) p.cpu = clamp((jif - before) * 100.0 / dTotal);
                s.procs.add(p);
            }
            prevProc.clear();
            prevProc.putAll(now);
            s.procCount = s.procs.size();

            if (!s.complete) {
                if (lastGood != null && !lastGood.isEmpty()) {
                    // 本轮被截断：沿用上一轮的完整列表，避免列表“先少后多”地跳
                    s.procs = new ArrayList<>(lastGood);
                    s.procCount = s.procs.size();
                } else {
                    // 首轮就不完整：宁可显示“加载中”，也不显示残缺列表
                    s.procs.clear();
                    s.procCount = 0;
                    s.loading = true;
                }
            }
        } else if (lastGood != null && lastGood.size() > 5) {
            // 本轮取不到，沿用上一轮，避免列表忽多忽少
            s.procs.addAll(lastGood);
            s.complete = false;
            s.procCount = s.procs.size();
        } else {
            Proc self = new Proc();
            self.pid = android.os.Process.myPid();
            String nm = ReadFile("/proc/self/comm", 128);
            self.name = (nm == null || nm.trim().isEmpty()) ? T.s("本应用") : nm.trim();
            self.uid = android.os.Process.myUid();
            self.threads = Thread.activeCount();
            String st = ReadFile("/proc/self/statm", 256);
            if (st != null) {
                String[] f = st.trim().split("\\s+");
                if (f.length > 1) self.rssBytes = parseLong(f[1]) * PAGE;
            }
            s.procs.add(self);
            s.procCount = 1;
        }
        if (s.complete && !s.loading && s.procs.size() > 5) lastGood = new ArrayList<>(s.procs);
        resolveArch(s);
        fillIo(s);
        return s;
    }

    /* ---------- 无 root：用 cpufreq 推算负载 ---------- */

    private static final java.util.Map<Integer, long[]> prevFisTicks = new java.util.HashMap<>();

    private static void fillFromFreq(Snapshot s) {
        s.coreList.clear();
        double sum = 0;
        int n = 0;
        for (int i = 0; i < s.cores; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            Core c = new Core();
            c.id = i;
            c.curKhz = parseLong(ReadFile(base + "scaling_cur_freq", 64));
            c.maxKhz = parseLong(ReadFile(base + "cpuinfo_max_freq", 64));
            c.minKhz = parseLong(ReadFile(base + "cpuinfo_min_freq", 64));

            double load = loadFromTimeInState(i, base + "stats/time_in_state");
            if (load < 0 && c.curKhz > 0 && c.maxKhz > 0) {
                load = clamp(c.curKhz * 100.0 / c.maxKhz);   // 退化为瞬时频率比
            }
            if (load >= 0) {
                c.pct = load;
                sum += load;
                n++;
            }
            s.coreList.add(c);
        }
        if (n > 0) s.cpuPercent = clamp(sum / n);
    }

    /** 由 time_in_state 的时间分布算平均频率占比（需要两次采样） */
    private static double loadFromTimeInState(int core, String path) {
        String content = ReadFile(path, 16384);
        if (content == null || content.trim().isEmpty()) return -1;
        String[] lines = content.trim().split("\n");
        long[] freqs = new long[lines.length];
        long[] ticks = new long[lines.length];
        long maxF = 0;
        for (int i = 0; i < lines.length; i++) {
            String[] p = lines[i].trim().split("\\s+");
            if (p.length < 2) continue;
            freqs[i] = parseLong(p[0]);
            ticks[i] = parseLong(p[1]);
            if (freqs[i] > maxF) maxF = freqs[i];
        }
        long[] prev = prevFisTicks.get(core);
        prevFisTicks.put(core, ticks);
        if (prev == null || prev.length != ticks.length || maxF <= 0) return -1;

        long dTotal = 0, dWeighted = 0;
        for (int i = 0; i < ticks.length; i++) {
            long d = Math.max(0, ticks[i] - prev[i]);
            dTotal += d;
            dWeighted += d * freqs[i];
        }
        if (dTotal <= 0) return -1;
        return clamp((double) dWeighted / dTotal * 100.0 / maxF);
    }

    /** 读 /proc/pid/exe 的 ELF EI_CLASS（1=32位 2=64位），按 pid 缓存，每轮最多补 60 个 */
    public static volatile boolean wantArch = false;   // 由 UI 控制，只有详情页需要

    private static void resolveArch(Snapshot s) {
        if (!s.root || !wantArch) return;
        StringBuilder pids = new StringBuilder();
        int n = 0;
        for (Proc p : s.procs) {
            String cached = archCache.get(p.pid);
            if (cached != null) { p.arch = cached; continue; }
            if (archTried.contains(p.pid)) continue;
            if (n++ >= 60) continue;
            pids.append(' ').append(p.pid);
            archTried.add(p.pid);
        }
        if (pids.length() == 0) return;
        try {
            String out = su("for p in" + pids + "; do c=$(od -An -tu1 -j4 -N1 /proc/$p/exe 2>/dev/null | tr -d ' '); echo \"$p:$c\"; done");
            for (String ln : out.split("\n")) {
                int i = ln.indexOf(':');
                if (i <= 0) continue;
                int pid = (int) parseLong(ln.substring(0, i));
                String v = ln.substring(i + 1).trim();
                String a = "1".equals(v) ? "32" : ("2".equals(v) ? "64" : "—");
                if (pid > 0) archCache.put(pid, a);
            }
        } catch (Throwable ignored) { }
        for (Proc p : s.procs) {
            String c = archCache.get(p.pid);
            if (c != null) p.arch = c;
        }
    }

    /** uid → 可读用户名 */
    // ---- 磁盘 / 网络 速率采样 ----
    private static long ioLastT = 0, ioLastRead = -1, ioLastWrite = -1, ioLastRx = -1, ioLastTx = -1;

    /** 读取 /proc/diskstats 与 /proc/net/dev 的累计值，算出速率填进 Snapshot */
    public static void fillIo(Snapshot s) {
        try {
            long readSec = 0, writeSec = 0;
            java.util.List<String> dlines;
            if (hasRoot()) {
                String out = su("cat /proc/diskstats");
                dlines = new java.util.ArrayList<>();
                for (String l : out.split("\n")) dlines.add(l);
            } else {
                dlines = readAll("/proc/diskstats");
            }
            for (String line : dlines) {
                String[] f = line.trim().split("\\s+");
                if (f.length < 10) continue;
                String dev = f[2];
                // 只统计主存储设备，避免分区重复计数
                if (!dev.matches("(sd[a-z]+|vd[a-z]+|mmcblk\\d+|nvme\\d+n\\d+)")) continue;
                try {
                    readSec += Long.parseLong(f[5]);
                    writeSec += Long.parseLong(f[9]);
                } catch (NumberFormatException ignored) { }
            }
            long rx = 0, tx = 0;
            // 优先用官方 API：Android 10+ 已限制应用读 /proc/net/dev
            try {
                long r = android.net.TrafficStats.getTotalRxBytes();
                long t = android.net.TrafficStats.getTotalTxBytes();
                if (r > 0 || t > 0) { rx = r; tx = t; }
            } catch (Throwable ignored) { }

            if (rx == 0 && tx == 0) for (String line : readAll("/proc/net/dev")) {
                int i = line.indexOf(':');
                if (i < 0) continue;
                String name = line.substring(0, i).trim();
                if (name.equals("lo") || name.startsWith("ifb")) continue;
                String[] g = line.substring(i + 1).trim().split("\\s+");
                if (g.length < 9) continue;
                try {
                    rx += Long.parseLong(g[0]);
                    tx += Long.parseLong(g[8]);
                } catch (NumberFormatException ignored) { }
            }
            long now = System.currentTimeMillis();
            if (readSec == 0 && writeSec == 0) {
                s.diskReadBps = -1; s.diskWriteBps = -1;
            } else if (ioLastT > 0 && now > ioLastT) {
                double dt = (now - ioLastT) / 1000.0;
                s.diskReadBps = Math.max(0, (readSec - ioLastRead) * 512.0 / dt);
                s.diskWriteBps = Math.max(0, (writeSec - ioLastWrite) * 512.0 / dt);
                s.netRxBps = Math.max(0, (rx - ioLastRx) / dt);
                s.netTxBps = Math.max(0, (tx - ioLastTx) / dt);
            }
            ioLastT = now; ioLastRead = readSec; ioLastWrite = writeSec;
            ioLastRx = rx; ioLastTx = tx;
            s.netRxTotal = rx; s.netTxTotal = tx;

            android.os.StatFs sf = new android.os.StatFs("/data");
            s.diskTotal = sf.getTotalBytes();
            s.diskUsed = s.diskTotal - sf.getAvailableBytes();
        } catch (Throwable ignored) { }
    }

    private static java.util.List<String> readAll(String path) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String l;
            while ((l = br.readLine()) != null) out.add(l);
        } catch (Throwable ignored) { }
        return out;
    }

    public static String userName(int uid) {
        if (uid == 0) return "root";
        if (uid == 1000) return "system";
        if (uid == 1013) return "media";
        if (uid == 2000) return "shell";
        if (uid >= 10000 && uid < 19999) return "u0_a" + (uid - 10000);
        if (uid >= 90000) return "isolated";
        if (uid >= 1000 && uid < 10000) return "sys_" + uid;
        return String.valueOf(uid);
    }

    /** 从热区里挑 CPU 温度：优先名字像 CPU 的，取其中最高 */
    public static Thermal cpuThermal(Snapshot s) {
        if (s == null || s.thermals.isEmpty()) return null;
        String[] keys = {"cpu", "soc", "tsens", "ap", "cluster", "core"};
        Thermal best = null;
        for (Thermal t : s.thermals) {
            String n = t.type.toLowerCase(Locale.ROOT);
            for (String k : keys) {
                if (n.contains(k)) {
                    if (best == null || t.celsius > best.celsius) best = t;
                    break;
                }
            }
        }
        return best != null ? best : s.thermals.get(0);
    }

    /* ---------------- 操作 ---------------- */

    public static boolean kill(int pid) {
        try {
            String r = su("kill -9 " + pid + " 2>&1; echo rc=$?");
            return r != null && r.contains("rc=0");
        } catch (Throwable t) { return false; }
    }

    public static String forceStop(String pkg) {
        try { return su("am force-stop " + pkg + " 2>&1"); }
        catch (Throwable t) { return T.s("失败: ") + t; }
    }

    public static String runBackground(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return T.s("命令为空");
        try {
            return su("nohup sh -c '" + cmd.replace("'", "'\\''") + "' >/dev/null 2>&1 & echo started");
        } catch (Throwable t) { return T.s("失败: ") + t; }
    }

    public static int cleanBackground(List<Proc> procs, int selfPid) {
        StringBuilder sb = new StringBuilder("n=0; for p in");
        int count = 0;
        for (Proc p : procs) {
            if (p.uid >= 10000 && p.uid < 90000 && p.pid != selfPid && p.pid > 1) {
                sb.append(' ').append(p.pid);
                count++;
            }
        }
        if (count == 0) return 0;
        sb.append("; do kill -9 $p 2>/dev/null && n=$((n+1)); done; echo killed=$n");
        try {
            String r = su(sb.toString());
            int idx = r == null ? -1 : r.indexOf("killed=");
            if (idx >= 0) return (int) parseLong(r.substring(idx + 7).trim().split("\\s+")[0]);
            return count;
        } catch (Throwable t) { return 0; }
    }

    /* ---------------- 工具 ---------------- */

    private static long[] parseCpuLine(String ln) {
        String[] f = ln.trim().split("\\s+");
        long total = 0, idle = 0;
        for (int i = 1; i < f.length && i <= 8; i++) {
            long v = parseLong(f[i]);
            total += v;
            if (i == 4 || i == 5) idle += v;
        }
        return new long[]{total, idle};
    }

    private static CpuTimes parseTimes(String ln) {
        String[] f = ln.trim().split("\\s+");
        CpuTimes t = new CpuTimes();
        if (f.length > 1) t.user = parseLong(f[1]);
        if (f.length > 2) t.nice = parseLong(f[2]);
        if (f.length > 3) t.system = parseLong(f[3]);
        if (f.length > 4) t.idle = parseLong(f[4]);
        if (f.length > 5) t.iowait = parseLong(f[5]);
        if (f.length > 6) t.irq = parseLong(f[6]);
        if (f.length > 7) t.softirq = parseLong(f[7]);
        if (f.length > 8) t.steal = parseLong(f[8]);
        return t;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    public static String ReadFile(String path, int max) {
        StringBuilder sb = new StringBuilder();
        try (java.io.FileInputStream in = new java.io.FileInputStream(path)) {
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < max) {
                sb.append(new String(buf, 0, n, "UTF-8"));
                total += n;
            }
        } catch (Throwable ignored) { return null; }
        return sb.toString();
    }

    public static String stateName(String s) {
        if (s == null || s.isEmpty()) return "?";
        switch (s.charAt(0)) {
            case 'R': return T.s("运行中 (R)");
            case 'S': return T.s("可中断睡眠 (S)");
            case 'D': return T.s("不可中断 (D)");
            case 'Z': return T.s("僵尸 (Z)");
            case 'T': return T.s("已停止 (T)");
            case 't': return T.s("跟踪停止 (t)");
            case 'X': return T.s("已退出 (X)");
            case 'I': return T.s("空闲内核线程 (I)");
            default: return s;
        }
    }

    public static String fmtUptime(Snapshot s, long startTicks) {
        if (s == null || s.uptime <= 0 || startTicks <= 0) return "—";
        long sec = (long) s.uptime - startTicks / HZ;
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60;
        return h > 0 ? String.format(T.s("%d 小时 %d 分"), h, m) : String.format(T.s("%d 分 %d 秒"), m, sec % 60);
    }

    public static String ghz(long khz) {
        // 部分环境（模拟器）的 cpufreq 是伪造占位值，<1MHz 视为无效
        if (khz < 1000) return "—";
        return String.format("%.2f GHz", khz / 1000000.0);
    }
}
