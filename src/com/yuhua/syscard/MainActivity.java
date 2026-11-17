package com.yuhua.syscard;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 打开这个应用只有一件事做：把卡片钉到桌面上。
 *
 * 为什么要有个界面——只做卡片的包在某些系统里会被"藏起来"，
 * 桌面的小部件列表里找不到。有个能点开的入口最稳妥。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView 标题 = new TextView(this);
        标题.setText("小鱼状态卡");
        标题.setTextSize(22);
        root.addView(标题);

        TextView 说明 = new TextView(this);
        说明.setText("\n一张 4×1 的桌面卡片：存储占用、上传/下载速度、网络状态。\n亮屏每分钟刷新一次，关屏就停。\n");
        说明.setTextSize(14);
        root.addView(说明);

        Button 钉 = new Button(this);
        钉.setText("放到桌面");
        钉.setOnClickListener(v -> 钉到桌面());
        root.addView(钉);

        TextView 备选 = new TextView(this);
        备选.setText("\n如果没弹出确认框：长按桌面空白 → 添加小部件 → 找「小鱼状态卡」。");
        备选.setTextSize(12);
        root.addView(备选);

        setContentView(root);
    }

    private void 钉到桌面() {
        if (Build.VERSION.SDK_INT >= 26) {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            ComponentName cn = new ComponentName(this, CardProvider.class);
            if (m.isRequestPinAppWidgetSupported()) {
                m.requestPinAppWidget(cn, null, null);
                return;
            }
        }
        // 系统不支持一键钉，就把卡片先刷一次，让人去小部件列表里手动加
        CardProvider.排下一次(this);
        android.widget.Toast.makeText(this, "这台系统不支持一键添加，请长按桌面 → 添加小部件", android.widget.Toast.LENGTH_LONG).show();
    }
}
