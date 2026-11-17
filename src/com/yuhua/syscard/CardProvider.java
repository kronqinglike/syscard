package com.yuhua.syscard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * 小鱼状态卡：一张表看三台设备。
 *
 * 按他 2026-11-17 那张表的规矩：
 *   - 顶部一行表头：电量 / 网络 / 存储 / 温度（四列，竖线隔开）
 *   - 下面三行：小米17ProMax / 红米K40 / 电脑
 *   - 在线绿、离线红
 *   - 每台各自上报到云上，卡片从云上取全部
 *   - 点一下卡片立刻刷新一次
 *
 * 两个教训（2026-11-17 都有血）：
 *   1. **网络请求不能放主线程**——会抛 NetworkOnMainThreadException，
 *      被 catch 吞掉就成了"永远没数据"。所以刷新从前台挪到了后台线程。
 *   2. RemoteViews 的布局只能用很有限的控件：LinearLayout / TextView / View，
 *      没有 TableLayout，所以"表格"是用横向 LinearLayout + 1dp 的 View 画出来的。
 */
public class CardProvider extends AppWidgetProvider {

    public static final String TICK = "com.yuhua.syscard.TICK";
    private static final long 间隔毫秒 = 60_000L;

    private static final int 绿 = 0xFF7FD1B9;
    private static final int 红 = 0xFFFF5252;
    private static final int 白 = 0xFFB9C6D4;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        后台刷(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (TICK.equals(i.getAction())) {
            后台刷(c);
        }
    }

    /** 刷新一次：先把自己报上去，再把云上那份取回来画出来。全在后台线程里。 */
    private static void 后台刷(final Context c) {
        排下一次(c);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Stats.自报(c);
                    JSONObject 汇 = Hub.取(c);
                    AppWidgetManager m = AppWidgetManager.getInstance(c);
                    int[] ids = m.getAppWidgetIds(new ComponentName(c, CardProvider.class));
                    if (ids != null && ids.length > 0) {
                        RemoteViews v = new CardProvider().画(c, 汇);
                        for (int id : ids) m.updateAppWidget(id, v);
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "card-refresh");
        t.start();
    }

    private RemoteViews 画(Context c, JSONObject 汇) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.card);

        JSONObject 设备 = null;
        try {
            设备 = 汇 == null ? null : 汇.getJSONObject("devices");
        } catch (Exception ignored) {
        }

        // 本机那一行：云上有就用云的；没有就现读（电量/网络/存储/温度分别填）
        校(v, R.id.n1, "小米17PM", 白);
        JSONObject 本机 = 取设备(设备, "mi17");
        if (本机 != null && 本机.optBoolean("online", false)) {
            填(v, 1, 本机, 绿);
        } else {
            本机现读(v, c);
        }

        // 另外两台
        JSONObject k40 = 取设备(设备, "k40");
        校(v, R.id.n2, "红米K40", 白);
        if (k40 != null && k40.optBoolean("online", false)) {
            填(v, 2, k40, 绿);
        } else {
            填空(v, 2, k40);
        }

        JSONObject pc = 取设备(设备, "pc");
        校(v, R.id.n3, "电脑", 白);
        if (pc != null && pc.optBoolean("online", false)) {
            填(v, 3, pc, 绿);
        } else {
            填空(v, 3, pc);
        }

        // 点一下卡片就立刻刷新一次
        Intent 点 = new Intent(c, CardProvider.class).setAction(TICK);
        PendingIntent pi = PendingIntent.getBroadcast(c, 2, 点,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.r1, pi);
        v.setOnClickPendingIntent(R.id.r2, pi);
        v.setOnClickPendingIntent(R.id.r3, pi);
        v.setOnClickPendingIntent(R.id.rh, pi);

        return v;
    }

    private JSONObject 取设备(JSONObject 设备, String 名) {
        try {
            if (设备 != null && 设备.has(名)) return 设备.getJSONObject(名);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void 校(RemoteViews v, int id, String 文字, int 色) {
        v.setTextViewText(id, 文字);
        v.setTextColor(id, 色);
    }

    /** 把某一行四个格子填满。 */
    private void 填(RemoteViews v, int 行, JSONObject d, int 色) {
        String 电 = d.has("battery") && !d.isNull("battery")
                ? d.optInt("battery") + "%" : "--";

        String 网 = d.optString("net", "");
        if (网.length() == 0) 网 = "--";

        String 存 = "--";
        try {
            JSONObject st = d.optJSONObject("storage");
            if (st != null) {
                long 总 = st.optLong("total", 0);
                long 闲 = st.optLong("free", 0);
                if (总 > 0) 存 = Math.round((总 - 闲) * 100.0 / 总) + "%";
            }
        } catch (Exception ignored) {
        }

        int 温 = d.optInt("cpu_temp", 0);
        if (温 <= 0) 温 = d.optInt("gpu_temp", 0);
        String 温字 = 温 > 0 ? 温 + "℃" : "--";

        放(v, 行, 电, 网, 存, 温字, 色);
    }

    /** 这台没数据：四格都填 --，名字标红。 */
    private void 填空(RemoteViews v, int 行, JSONObject d) {
        String 提示 = "--";
        if (d != null) {
            int 秒 = d.optInt("age", 0);
            提示 = 秒 > 0 ? 秒 + "s前" : "--";
        }
        放(v, 行, 提示, 提示, 提示, 提示, 红);
        int 名id = 行 == 1 ? R.id.n1 : (行 == 2 ? R.id.n2 : R.id.n3);
        v.setTextColor(名id, 红);
    }

    private void 本机现读(RemoteViews v, Context c) {
        try {
            String 存 = Stats.存储(c)[0].replace("存储 ", "");
            int 电 = Stats.电量(c);
            String 网 = Stats.网络(c)[0];
            int 温 = Stats.温度(c);
            放(v, 1, 电 + "%", 网, 存, 温 > 0 ? 温 + "℃" : "--", 绿);
        } catch (Exception e) {
            填空(v, 1, null);
        }
    }

    /** 四个格子 + 颜色。列 id 按行取：a1..d1 / a2..d2 / a3..d3。 */
    private void 放(RemoteViews v, int 行, String 电, String 网, String 存, String 温, int 色) {
        int[] a = 行 == 1 ? new int[]{R.id.a1, R.id.b1, R.id.c1, R.id.d1}
                : 行 == 2 ? new int[]{R.id.a2, R.id.b2, R.id.c2, R.id.d2}
                : new int[]{R.id.a3, R.id.b3, R.id.c3, R.id.d3};
        String[] 值 = {电, 网, 存, 温};
        for (int i = 0; i < 4; i++) {
            v.setTextViewText(a[i], 值[i]);
            v.setTextColor(a[i], 色);
        }
    }

    /** 马上刷一次（网络变了、亮屏了），不等下一分钟。 */
    static void 立刻刷(Context c) {
        try {
            后台刷(c);
        } catch (Exception ignored) {
        }
    }

    static void 排下一次(Context c) {
        if (!Stats.屏幕亮着(c)) return;
        android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, CardProvider.class).setAction(TICK);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            long 等 = Stats.有样本(c) ? 间隔毫秒 : 3000L;
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 等, pi);
        } catch (Exception ignored) {
        }
    }

    static void 停(Context c) {
        android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, CardProvider.class).setAction(TICK);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.cancel(pi);
        } catch (Exception ignored) {
        }
    }
}
