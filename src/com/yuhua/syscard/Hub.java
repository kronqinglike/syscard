package com.yuhua.syscard;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 从云上的汇总口取三台设备的状态。
 *
 * 两条路都试：
 *   1. 127.0.0.1:8790 —— 手机上那条 ssh 隧道（语音球用的同一条），
 *      云上 8788 会把 /status 转给汇总口。
 *   2. 127.0.0.1:8795 —— 给"直连汇总口"准备的备用路。
 *
 * 哪条通走哪条；都不通就返回 null，卡片只显示本机——不报错、不弹窗。
 */
public class Hub {

    private static final String[] 地址们 = {
            "http://127.0.0.1:8790/status",
            "http://127.0.0.1:8795/status"
    };
    private static final String 令牌 = "YOUR_STATUS_TOKEN";   // ← 换成你自己的

    /** 取回来；两条路都不通返回 null。 */
    static JSONObject 取(Context c) {
        for (String 基 : 地址们) {
            JSONObject o = 取一条(基);
            if (o != null) return o;
        }
        return null;
    }


    /** 把自己这台报上去（POST /report，带令牌）。报不上就当没这回事。 */
    static void 报(String json) {
        byte[] 体;
        try {
            体 = json.getBytes("UTF-8");
        } catch (Exception e) {
            return;
        }
        for (String 基 : 地址们) {
            String 址 = 基.replace("/status", "/report");
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(址).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("X-Token", 令牌);
                c.getOutputStream().write(体);
                int 码 = c.getResponseCode();
                if (码 >= 200 && 码 < 300) return;
            } catch (Throwable ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }
    private static JSONObject 取一条(String 基) {
        HttpURLConnection conn = null;
        try {
            String u = 基 + "?token=" + URLEncoder.encode(令牌, "UTF-8");
            conn = (HttpURLConnection) new URL(u).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new JSONObject(bo.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        } finally {
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    /** 把字节数写成 "1.2K" 这种短写法。 */
    static String 短(double v) {
        if (v < 1024) return String.format("%.0fB", v);
        if (v < 1024 * 1024) return String.format("%.0fK", v / 1024);
        return String.format("%.1fM", v / 1024 / 1024);
    }

    /** 存储写成 "40/512G"。 */
    static String 存(JSONObject d) {
        try {
            JSONObject s = d.getJSONObject("storage");
            double total = s.getDouble("total") / 1e9;
            double free = s.getDouble("free") / 1e9;
            return String.format("%.0f/%.0fG", free, total);
        } catch (Exception e) {
            return "--";
        }
    }
}
