---
name: luckytool-apk-adapt
description: >-
  Adapt LuckyTool hooks to the newest installed Android apps. Use when the
  user gives a LuckyTool backup config, asks to adapt a new app version,
  mentions MT Manager MCP, connecting mcp.json, setting an APK target, or
  says 适配新版、提取 apk、配置、关闭系统更新、连接 MCP、设为目标.
---

# LuckyTool 新版适配

只改配置里已经打开的功能。旧匹配保留，新版对不上时再加一条回退。

## 配置

备份是 Base64 的 JSON。解码后读 `ModulePrefs` 里值为 `true` 的键，以及 `com.*` / `android` 上记录的 `versionName`、`versionCode`、`versionCommit`。

用这些键去 `app/src/main/java` 对上 Hook 和界面。包名在对应 Fragment 的 `scopes`，或 `YukiEntry` 的 `loadApp`。

配置键可能改过名。Hook 要同时认旧键和新键，例如 `dsiable_start_app_detail` 与 `disable_start_app_detail`。

## 提取最新安装包

先 `adb devices`，用当前在线的那一台。序列号不要从 MCP 地址猜。多台设备时加 `-s <serial>`。设备上用 `adb shell su -c`。目标目录是 MT 管理器的 MCP 目录：

`/storage/emulated/0/MT2/mcp/`

对每个相关包执行 `pm path <package>`，把当前安装的 apk（含 base）拷进该目录，文件名用 `<package>__<basename>`。已有同名文件时覆盖，保证是手机上正在运行的最新包。

系统框架里的类（核心破解、ADB 安装确认）在 `/system/framework/services.jar` 和 `oplus-services.jar`，用 `toybox strings` 核对类名和方法名，不必当成 apk 打开。

## 连接 MT MCP

适配只用 Cursor 用户配置里的 MT 管理器。文件是 `C:\Users\Administrator\.cursor\mcp.json`，服务器名 `MT`。地址以里面的 `url` 为准，不要写死 IP 或端口。本会话的命名空间是 `user-MT`。不要用 `luahook`、`ida-pro-mcp*`、`decx`、`算法助手` 打开这些 apk。

两种常见写法：

- 局域网直连。`url` 的 host 是手机 IP，例如 `http://192.168.5.6:8787/mcp`。电脑能直接访问该地址，不要再做转发。这个 IP 每次都可能变。
- 本机转发。`url` 的 host 是 `127.0.0.1` 或 `localhost`，例如 `http://127.0.0.1:8787/mcp`。MT 在手机上监听，电脑要先把本机端口转到设备：

```
adb forward tcp:<端口> tcp:<端口>
```

端口取 `url` 里的端口，常见是 `8787`。多台设备时加 `-s <serial>`，序列号用 `adb devices` 里在线的那台。转发之后再连 `127.0.0.1`。换设备或 adb 断开后转发会丢，调用 MCP 前先看 `adb forward --list`，没有对应条目就重新 forward。

调用前用 `GetDynamicTools` 看 `user-MT` 的参数，再用 `CallDynamicTool` 调用。不要猜参数。

1. `mt_file_access_policy` 传 `{}`，确认 Home。拷进去的 apk 必须在 Home 下面，一般是 `/storage/emulated/0/MT2/mcp/`。
2. `mt_apk_list_available_apks`，`prefix` 传包名（如 `com.heytap.market`），`limit` 用 50。`items[].path` 是相对 Home 的路径，原样留给下一步。
3. 把这个 `path` 传给 `mt_apk_open`，`temporary` 为 false。返回的 `workspaceId` 就是这次适配的目标。后面的搜索、类结构、方法体、引用都带这个 `workspaceId`。
4. 只有用户明确说「当前 apk」时才用 `mt://current-apk`。按配置提取的包以第 3 步打开的 workspace 为准，不要把 MT 里正在看的别的包当成目标。

## 在目标上核对

`mt_apk_search` 的 `workspaceId` 用上面打开的目标，`editSessionId` 传空字符串，表示搜原始包。

1. `target` 用 `dex_names`、`dex_strings`、`dex_string_members`、`smali`。`queryType` 用 `literal`，`matchMode` 用 `contains`，`prefix` 无过滤时传 `""`。
2. 类结构用 `mt_apk_dex_outline_class`，方法体用 `mt_apk_read_text`，调用方用 `mt_apk_dex_xref`。
3. locator 必须原样复制工具返回的 `dex_class:` / `dex_method:` 字符串。

DexKit 的 `usingStrings` 是常量全字匹配。日志从 `getSplashData` 变成 `getSplashData finished: ...` 时，旧字符串匹配失败。

## 改 Hook

- 先跑旧查询。结果恰好 1 条才用旧逻辑。
- 旧查询为空时再跑新查询。两条都空就返回，不要调用 `single()`。
- 结果为空时不要调用 `checkDataList`。它会打 `findMethod isNullOrEmpty`。
- KavaRef 的 `firstMethodOrNull` 加上 `optional()` 仍会打 `No method found`。先用 `declaredMethods` 确认方法存在，再交给 KavaRef。
- 类名用 `toClassOrNull()`。不存在就跳过，不要让整个 `onHook` 抛出去。
- 旧版路径留着。乐划锁屏 `40.9` 及更早仍用原来的 `File`/`Handler` 查询；只有该类不存在时才走 `40.10` 的保存路径。
- 调用方会回收返回的 `Bitmap` 时，返回原图的副本，不要把入参那张图直接设成返回值。
- 分享和水印保存若共用绘制方法，用线程标记限定只改保存路径。

## 关闭系统更新

开关在软件更新页，键 `disable_system_update`。`DisableSystemUpdate` 在 `HookOplusOta` 的 DexKit 块里加载。

新版本提示的来源：`QueryOTAUpdateRunnable` 请求 `/update/v6`，回包的 `body` 是 AES 密文，`ResponseParser` 解密后写进 `state_info`（`update_state=1` 表示有新版本）。软件更新再用 `ContentResolver.insert` 往设置的 `content://com.android.settings.outward.provider/message_entries` 插一行，`package_name=com.oplus.ota`。设置首页直接读这张表，软件更新不运行也会显示。清软件更新的数据删不掉这一行。

现在的处理：

- DexKit 找名为 `run`、用到字符串 `QueryOTAUpdateRunnable` 的方法并拦住，自动和手动检查都不再请求。
- 拦 `ContentResolver.insert`：目标是 `message_entries` 且 `package_name` 是软件更新时丢弃。
- 主进程 `onCreate` 时从 `state_info` 删掉 `update_state` 等新版本字段，再删掉设置表里软件更新那一行。`:ui` 进程的 `state_info` 由主进程代理，不要在那里改。
- `OTAService$d` 的 `startQueryUpdate`、`startDownload`、`startInstall`、`startABUpdate` 照旧拦住。`LoadingTextView` 的文字改成资源名 `no_update`（已是最新版本），并隐藏进度圈。

不要写死 `o4.d`、`com.oplus.common.a`、`com.oplus.ota.query.h` 这类混淆名，每个版本都会变。

验证：`su -c "content query --uri content://com.android.settings.outward.provider/message_entries"`，看软件更新那一行还在不在。拉起软件更新要用 `su -c "am start -a com.oplus.ota.MAIN -p com.oplus.ota"`，shell 身份没有 `OPLUS_COMPONENT_SAFE` 权限。

## 更新日志

改完功能后，在 `app/changelog.md` 的当前版本节按现有格式补一行：`[适配]` 或 `[添加]`，写功能名、应用版本和 commit。不要另写长说明。

不要提交 `keystore/`。
