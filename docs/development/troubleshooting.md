# Android / RN 初始化与日志排障

适用当前 RN 0.86、Native 登录及单 ReactHost 工程。日期：2026-10-18。

## 本次故障及修复

模拟器进程日志已记录 `bootstrap.failed code=HOST_RESOURCE_INVALID`。从设备拉取的已安装 APK 中，`assets/contracts/` 和 `assets/mock/` 全部缺失。NativeHost 打开 `contracts/bridge/runtime.json` 失败，被 catch 转为统一错误；RN 又将它显示为“原生能力暂不可用”。重复握手无法补回安装包资源。

原 Gradle 配置 `android.sourceSets.main.assets.srcDir(prepareRuntimeResources)` 虽登记了输出目录，本工程 AGP 的实际 `mergeDebugAssets` 任务图却没有资源生成任务。此前生成目录尚在时可能成功；目录被清理后，构建仍成功但资源缺失。现在通过 `androidComponents.onVariants` 和 `variant.sources.assets.addGeneratedSourceDirectory`，为每个变体登记有明确输入/输出的生成任务，将生成任务依赖传递给资源消费者。输入 JSON 仍先经 `validateRuntimeResources` 校验，不复制资源到源码目录。

修复后必须重新构建并安装 APK；仅 Reload / Fast Refresh / 重启 Metro 不会更新 APK 内的 assets：

```sh
npx react-native run-android
```

NativeHost 现在区分 `HOST_RESOURCE_MISSING`、`HOST_RESOURCE_INVALID`、`PROTOCOL_VERSION_MISMATCH`，记录失败阶段、资源路径、异常类型及 flowId；Debug 记录异常堆栈。页面显示错误码和可选取的诊断编号。RN 与 Native 握手日志使用相同 flowId。

## 三个日志入口

1. **VS Code 集成终端或任意终端**：在工程根目录单独执行 `npm run logs:android`。它查找已安装应用的 UID，读取该应用所有标签、所有等级的 Logcat，应用进程重启后仍持续。需 Android SDK / adb 和已连接设备；多设备执行 `npm run logs:android -- emulator-5554`。不清空设备日志、不清应用数据。命令从最近一条记录开始，先开日志终端，再复现问题。Ctrl+C 退出。
2. **RN DevTools Console**：Metro 终端按 `j` 打开，查看 `[EntryTask:RN]`。RN 的 Console 不应依赖 Metro 构建终端转发；官方将 DevTools Console 作为 JS 日志入口。Debug 才支持 DevTools。
3. **Android Studio Logcat**：选择正确设备，在查询框输入 `package:com.entrytask`，清除旧标签/进程/等级限制。初始化可加 `tag:EntryTask.Bridge`，RN 经 Native 投递的日志是 `tag:EntryTask.RN`，JS 直接输出是 `tag:ReactNativeJS`。错误现在按 Error 输出，不再一律 Info。

没有 npm 工具时，可直接使用 SDK 的 adb（确保它在 PATH 中）：

```sh
adb devices -l
adb logcat -v threadtime 'EntryTask.Bridge:V' 'EntryTask.RN:V' 'ReactNativeJS:V' 'AndroidRuntime:E' '*:S'
```

该标签命令适合聚焦初始化和 RN；`npm run logs:android` 还包含现有 Native 登录、网络等各自标签。不要把“构建终端没打印”当作“应用没产生日志”。

## 最短排查路径

| 观察 | 下一步 |
| --- | --- |
| 页面有错误码和诊断编号 | 用编号搜索 Bridge 与 RN 日志，先看 Native 的失败阶段/错误码 |
| `HOST_RESOURCE_MISSING` | 检查 APK 中 `assets/contracts/bridge/runtime.json`，重新构建安装，不能只刷新 JS |
| `HOST_RESOURCE_INVALID` | 看 Native Debug 堆栈；检查 JSON 内容、编码及必填字段 |
| `PROTOCOL_VERSION_MISMATCH` / `INVALID_BRIDGE_DATA` | 核对 Metro 当前项目与安装 APK 的契约、组件名，重新构建安装 |
| `NATIVE_HOST_UNAVAILABLE` | 检查 Codegen、EntryTaskPackage 注册与安装包是否更新；iOS 当前尚未实现该模块 |
| `telemetry.sink.failed` | NativeTelemetry 不可用或拒绝批次；查看 RN Console / ReactNativeJS，以及 Native `telemetry.batch.failed`；日志不会通过失败 sink 递归发送 |
| 没有任何应用日志 | 先确认 adb 设备、包是否安装和运行；移除 Android Studio 旧过滤条件，再复现；确认不是连接到另一设备/Metro 项目 |
| 初始化成功，但页面请求失败 | 按 RN `requestId` 对照 Native Mock 请求日志，核对 apiId、JSON 模板、schema；不打印请求体、凭证或完整业务数据 |

Logger 在宿主握手前连接 NativeTelemetry，因此 Host 失败也能投递错误。RN 的独立 Console 与有界内存缓冲不依赖 NativeTelemetry 成功。sink 失败只向 Console 发一次告警，成功后才恢复下一次告警资格；不会无限积压或递归日志。Release 保留 RN warn/error Console 及 Native warn/error，不提供 DevTools，也尚未实现落盘日志/远程日志上传。

## 构建回归检查

仅“Gradle 成功”不能证明资源完整。改动资源生成配置后，需要验证从空生成目录构建，以及最终 APK 的文件内容：

```sh
cd android
./gradlew :app:clean :app:assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk 'assets/contracts/*' 'assets/mock/*'
```

Release 也应构建并检查资源；不能用手工跑过生成任务的工作目录代替干净构建验证。当前 Mock 登录/注册实现仍沿用现有 Native 代码，本次修复不改变其校验与存储行为。

参考：[Android 官方生成资源示例](https://github.com/android/gradle-recipes/tree/agp-9.0/addGeneratedSourceFolder)、[RN 0.86 调试指南](https://reactnative.dev/docs/0.86/debugging)、[RN DevTools](https://reactnative.dev/docs/0.86/react-native-devtools)。

## 本次修复验证记录

- 根目录 `npx react-native run-android`：全 ABI Debug 构建、安装、启动成功。
- `:app:clean :app:assembleDebug --max-workers=2 -PreactNativeArchitectures=arm64-v8a`：空 app 构建目录重新构建成功；最终 Debug APK 的 15 个 contracts / mock 文件与仓库逐字节一致。
- 模拟器 Native 注册并自动登录后，RN 首页显示欢迎语、常用服务和产品列表；`bootstrap.completed`、`home.content` / `product.list` 的 `request.completed` 均可在终端读取。
- `npm run logs:android`：确认能读取应用完整日志，包含现有 Native 认证标签及新 Bridge / RN 标签。
- `:app:mergeReleaseAssets :app:lintVitalAnalyzeRelease`：成功；本次未安装 Release 包。
- TypeScript、ESLint、架构检查及 Jest 5 suites / 10 tests 通过。Jest 可加 `--watchman=false` 避免受本机 Watchman 权限影响。
- UI 验证期间模拟器曾出现 ANR；重启应用后继续完成验证。本次未对该 ANR 做原因分析，不将它归因于初始化资源故障。
- 本地 Mock 账号与偏好测试前备份、测试后恢复，并用 `cmp` 验证一致；不清应用数据、不输出凭证。
