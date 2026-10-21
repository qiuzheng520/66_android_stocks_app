# 66_android_stocks_app

android手机股市加密币行情应用

纯 Java + View/XML 的 Android 原生 App（无 Kotlin），minSdk 24。
爬取解析 [TradingEconomics 中文站](https://zh.tradingeconomics.com/) 的公开行情页面，展示 **商品 / 股市 / 币种 / 加密货币 / 债券** 五类行情。

> **免责声明**：本项目仅用于个人学习。网站没有官方 API，页面解析会因为网站改版而失效；
> 高频抓取存在触发反爬机制、导致 IP 被封禁的风险。请勿用于商业用途，抓取行为的后果需自行承担。

## 功能

- TabLayout + ViewPager2 + RecyclerView 多分类行情列表
- SwipeRefreshLayout 下拉刷新（强制绕过缓存走网络）
- SearchView 本地搜索（按品种名称过滤，不发网络请求）
- 列表 Item：名称、国旗（股市/币种按国家或地区显示）、现价、涨跌、涨跌幅，**红跌绿涨**
- 点击 Item 用 WebView 打开品种详情页
- OkHttp 异步请求（子线程回调）+ Jsoup 解析 HTML
- 请求限流（1.5s 最小间隔 + 每分钟 20 次配额）
- 60 秒 LRU 内存缓存，命中缓存不发请求
- 网络异常 / 限流 / 解析失败均有提示
- 运行日志与崩溃堆栈落盘到本地文件，可用其它应用打开（无需 adb）
- Manifest 仅申请 `INTERNET` 权限

## 分包结构

```
app/src/main/java/com/example/stocks/
├── StocksApplication（Application：初始化日志与崩溃处理器）
├── model/     MarketCategory（分类枚举）、MarketItem（行情条目）
├── net/       HttpClient（OkHttp 单例）、HtmlParser（Jsoup 解析）、
│              QuoteRepository（缓存+限流+异步加载）、RateLimiter、MemoryCache
├── utils/     NumberUtils（数字解析/格式化）、ColorUtils（涨跌配色）、
│              AppLog（文件日志）、CrashHandler（崩溃捕获）、
│              SearchQuery（搜索关键字）、ShareUtil（用其它应用打开/分享文件）
├── ui/        MainActivity、QuoteFragment、DetailActivity、
│              LogActivity（日志查看）、CrashActivity（崩溃提示）
└── adapter/   QuoteAdapter、CategoryPagerAdapter
```

## 运行日志与崩溃抓取（无 adb 时排查问题）

应用会把运行日志与崩溃堆栈写到手机本地文件：

```
/sdcard/Android/data/com.example.stocks/files/logs/stocks-log.txt
```

外部存储不可用时会退回内部目录：`/data/data/com.example.stocks/files/logs/stocks-log.txt`。

查看方式（三选一）：

1. **应用内**：首页右上角「⋮」→ 日志，可查看 / 复制 / 清空 / 分享；
2. **用其它应用打开**：日志页点「用其它应用打开」，会弹出系统选择器
   （文件管理器、文本编辑器、微信、邮件等都能选），通过 FileProvider 临时授权，无需存储权限；
3. **崩溃后自动弹窗**：崩溃时 `CrashHandler` 会同步写盘并拉起崩溃提示页，
   点「用其它应用打开日志文件」即可把日志交给其它应用；
   若提示页没被拉起，下次启动首页会弹「检测到上次崩溃」提示。

日志文件超过 512KB 自动轮转为 `stocks-log-prev.txt`，只保留最近两份。

## 构建

环境要求：JDK 17、Android SDK 36（android-36 平台）、AGP 9.3.2 + Gradle 9.5.1。

```bash
# Android Studio 直接 Open 本目录即可
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
```

本仓库根目录已附带编译好的 `stocks-debug.apk`（debug 签名，可直接安装）。
本机 SDK 路径记录在 `local.properties`（`sdk.dir=D:\android_studio`），换机器需改成本机 SDK 路径。

## 解析原理

列表页地址（2026 年新版路径，旧的 `stock-market` / `currency` / `bond` 已 404）：

```
https://zh.tradingeconomics.com/{commodities|stocks|currencies|crypto|bonds}
```

页面里每张行情表的 id 是随机的（`commodity-1019842321` 之类），不再有 `id="table"`，
`HtmlParser` 改为按下面规则提取：

1. 数据行：`tr[data-symbol]`（例如 `data-symbol="CL1:COM"`）；
2. 名称与链接：`td.datatable-item-first` 里的 `<a>`（`<b>` 是名称，旁边 `<div>` 是单位）；
3. 现价 `td#p`、涨跌额 `td#nch`、涨跌幅 `td#pch`（债券页没有 `%Chg` 列，用现价反推涨跌幅）；
4. 符号还原：负数在页面上只显示绝对值，真实符号取自单元格的 `data-value` 属性，
   没有该属性时看 `market-negative-image` 样式；
5. 找不到上述结构时，退回按单元格顺序解析（兼容旧版页面）。

页面一旦改版（表格结构调整、加验证页、封禁 UA），列表会出现“解析失败，页面结构可能已改版”的提示，
需要自行调整 `HtmlParser`；排查时可用应用内「日志」页导出抓到的 HTML 长度与 HTTP 状态码。

## 限流策略

5 个 Tab 会同时发起请求，限流器不再直接报错，而是返回需要等待的毫秒数，
请求按 1.5 秒一个依次排队发出（每分钟最多 20 次），因此切换 Tab 只会慢一点，不会弹“请求过于频繁”。

