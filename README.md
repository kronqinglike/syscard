# 状态卡（SysCard）

一个桌面小部件：**三台设备一张表**，一眼看完电量、网络、存储、温度。

```
设备       | 电量 | 网络      | 存储 | 温度
小米17PM   | 52%  | WiFi      | 62%  | 51℃
红米K40    | 78%  | 数据+热点  | 88%  | 45℃
电脑       | 96%  | WiFi      | 91%  | 69℃
```

- **在线绿、离线红**
- **点一下卡片立刻刷新**（不用等下一分钟）
- 每台设备各自把状态上报到云上一个汇总口，卡片从汇总口取全部
- 亮屏时每分钟刷一次，关屏就停（省电）

## 架构

```
电脑  ──┐
K40   ──┼→ POST /report {id,name,battery,net,storage,cpu_temp} → 汇总口
手机  ──┘                                                        │
                                          GET /status ←──────────┘
                                               ↓
                                       状态卡（任意设备）
```

这台手机自己也上报（id 固定 `mi17`），所以三台里它那行也是从云上取的。

## 服务端（汇总口）

一个几十行的 HTTP 服务，两个接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/report` | 带 `X-Token` 头；body 是 JSON；按 `id` 存最新一份 |
| GET | `/status` | 带 token（`?token=` 或头）；返回 `{"now":…, "devices":{id:{…, age, online}}}` |

`online` 由服务器算：**上报时间超过 150 秒就算离线**。

report 字段：

```json
{
  "id": "pc",
  "name": "电脑",
  "battery": 96,
  "net": "WiFi",
  "storage": { "total": 511986102272, "free": 41927000064 },
  "cpu_temp": 44
}
```

（`battery` 可以是 `null`；`cpu_temp` 没有就不填。）

## 编译

和语音球那套一样，不依赖 Gradle：

```
powershell -ExecutionPolicy Bypass -File build-card.ps1
```

需要 JDK + Android SDK（build-tools 34、platform android-34），改脚本顶部 4 个路径即可。

## 要改的配置

`src/com/yuhua/syscard/Hub.java` 里两处：

```java
private static final String[] 地址们 = {
    "http://127.0.0.1:8790/status",   // 走隧道
    "http://127.0.0.1:8795/status"    // 直连（如果汇总口开在别处）
};
private static final String 令牌 = "YOUR_STATUS_TOKEN";
```

## 踩过的坑（每一个都让卡片白屏或显示"没数据"）

1. **网络请求绝不能放主线程**：`NetworkOnMainThreadException` 被 catch 吞掉，表现就是永远"没数据"。
   所以刷新全部挪到后台线程。
2. **超时不能太短**：原来连接 1.2 秒、读 2 秒，走隧道到境外经常超时。放宽到 5 秒 / 8 秒。
3. **桌面的 RemoteViews 不支持 `<View>`**：用它画竖线会直接
   "载入窗口小部件时出现问题"。改用 1dp 宽的 `TextView` 当竖线。
4. **RemoteViews 也没有 `TableLayout`**：表格是横向 `LinearLayout` + `layout_weight` 拼出来的。
5. **中文过 PowerShell 的 ssh 管道会变成问号**：上报端改成 base64 传输再解码。
6. **百分比别用整除**：`(总-闲)*100/总` 会把 92% 显示成 91%，要 `Math.round`。

## 其他设备的接入

- **Windows 电脑**：一个 PowerShell 脚本读电量/存储/网速，走 ssh 把 JSON 递进云上（公网只开 22 时很实用）
- **旧安卓手机**：如果装不上 App（比如 MIUI 拦 USB 安装），可以由**插着它的那台电脑**用 adb 替它读、
  替它报——代价是拔线就离线

## 许可

MIT。
