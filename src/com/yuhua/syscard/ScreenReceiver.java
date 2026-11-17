package com.yuhua.syscard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 亮屏就开始每分钟刷新，关屏就停；网络一变立刻刷一次。 */
public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || i.getAction() == null) return;
        String a = i.getAction();

        // 网络换了（WiFi 关掉、切到移动数据）要立刻刷一次，
        // 否则卡片会一直显示上一个连接方式，要等下一分钟才改。
        if ("android.net.conn.CONNECTIVITY_CHANGE".equals(a)) {
            CardProvider.立刻刷(c);
            return;
        }

        if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            CardProvider.排下一次(c);
            CardProvider.立刻刷(c);
        } else if (Intent.ACTION_SCREEN_OFF.equals(a)) {
            CardProvider.停(c);
        }
    }
}
