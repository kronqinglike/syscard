package com.yuhua.syscard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import java.util.Locale;

/** 三样数据的取法都在这儿：存储、网速、网络状态。 */
public class Stats {

    

    /** 返回两行：主行（百分数）、副行（用了多少 / 一共多少）。 */
    static String[] 存储(Context c) {
        try {
            StatFs f = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long 总 = f.getTotalBytes();
            long 闲 = f.getAvailableBytes();
            long 用 = 总 - 闲;
            int 百分比 = (int) Math.round(用 * 100.0 / Math.max(1, 总));
            String 主 = "存储 " + 百分比 + "%";
            String 副 = String.format(Locale.CHINA, "%.0f / %.0f GB", 用 / 1e9, 总 / 1e9);
            return new String[]{主, 副};
        } catch (Exception e) {
            return new String[]{"存储 ?", "--"};
        }
    }

    // 上一次的样本必须写进 SharedPreferences，不能放静态变量：
    // 每分钟那次更新常常是重新拉起进程来跑的，静态变量一重置，
    // 就永远只有"第一次"，速度永远显示 --。（2026-11-16 装机后就是这么卡住的）
    private static final String 存档 = "syscard_stats";

    private static long[] 读样本(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences(存档, 0);
        return new long[]{sp.getLong("rx", -1), sp.getLong("tx", -1), sp.getLong("ts", 0)};
    }

    private static void 存样本(Context c, long 收, long 发, long 时) {
        c.getSharedPreferences(存档, 0).edit()
                .putLong("rx", 收).putLong("tx", 发).putLong("ts", 时).apply();
    }

    static boolean 有样本(Context c) {
        long[] s = 读样本(c);
        return s[0] >= 0 && s[2] > 0;
    }

    /** 下载 / 上传速度。没有上一次的样本时返回 "--"。 */
    static String 网速(Context c) {
        long 收 = TrafficStats.getTotalRxBytes();
        long 发 = TrafficStats.getTotalTxBytes();
        long 现在 = SystemClock.elapsedRealtime();
        long[] 旧 = 读样本(c);
        String 结果 = "↓ -- ↑ --";
        if (旧[0] >= 0 && 旧[2] > 0 && 现在 > 旧[2]) {
            double 秒 = (现在 - 旧[2]) / 1000.0;
            double 下 = Math.max(0, (收 - 旧[0]) / 秒);
            double 上 = Math.max(0, (发 - 旧[1]) / 秒);
            结果 = "↓ " + 读数(下) + " ↑ " + 读数(上);
        }
        存样本(c, 收, 发, 现在);
        return 结果;
    }

    private static String 读数(double 字节每秒) {
        if (字节每秒 < 1024) return String.format(Locale.CHINA, "%.0f B/s", 字节每秒);
        if (字节每秒 < 1024 * 1024) return String.format(Locale.CHINA, "%.1f K/s", 字节每秒 / 1024);
        return String.format(Locale.CHINA, "%.1f M/s", 字节每秒 / 1024 / 1024);
    }

    /** 返回 {显示文字, 标记}；标记为 "断" 时卡片标红。 */
    static String[] 网络(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return new String[]{"未知", "断"};
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return new String[]{"断网", "断"};
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return new String[]{"WiFi", "通"};
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return new String[]{"数据", "通"};
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return new String[]{"有线", "通"};
            return new String[]{"网络", "通"};
        } catch (Exception e) {
            return new String[]{"未知", "断"};
        }
    }

    static int 电量(Context c) {
        try {
            android.content.Intent i = c.registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1;
            int 级 = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int 总量 = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            return 总量 > 0 ? (int) (级 * 100.0 / 总量) : 级;
        } catch (Exception e) {
            return -1;
        }
    }


    /** CPU 温度：读系统里的温度节点，读不到返回 -1。 */
    static int 温度(Context c) {
        String[] 候选 = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/class/thermal/thermal_zone2/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
        };
        for (String p : 候选) {
            try {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(p));
                String s = r.readLine();
                r.close();
                if (s == null) continue;
                int v = Integer.parseInt(s.trim());
                if (v > 1000) v = v / 1000;
                if (v > 0 && v < 120) return v;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /** 把这台自己的状态报给云上（从 8790 那条代理进去，云上转给汇总口）。 */
    static void 自报(Context c) {
        try {
            StatFs f = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long 总 = f.getTotalBytes();
            long 闲 = f.getAvailableBytes();

            org.json.JSONObject o = new org.json.JSONObject();
            o.put("id", "mi17");
            o.put("name", "小米17ProMax");
            o.put("battery", 电量(c));
            o.put("net", 网络(c)[0]);
            org.json.JSONObject st = new org.json.JSONObject();
            st.put("total", 总);
            st.put("free", 闲);
            o.put("storage", st);
            int 温 = 温度(c);
            if (温 > 0) o.put("cpu_temp", 温);

            Hub.报(o.toString());
        } catch (Throwable ignored) {
        }
    }
    static boolean 屏幕亮着(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }
}
