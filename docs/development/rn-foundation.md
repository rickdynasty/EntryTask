# RN 工程与四 Tab 运行说明

更新：2026-10-18。视觉及页面设计见 [四 Tab 设计](../ui/四Tab原型分析与RN页面设计.md)，验收证据见 [实现与验证记录](../ui/四Tab实现与验证记录.md)。

## 启动与诊断

从Application.attachBaseContext到RN页面挂载、会话Bridge和Native认证返回的逐步调用链，见 [启动与登录全链路源码详解](启动与登录全链路源码详解.md)，每一步均有当前源码方法/行号入口。

在仓库根目录执行：

```sh
npm ci
npx react-native run-android
```

根目录另开终端执行 `npm run logs:android`；多设备可加 `-- <serial>`。Metro 按 `j` 查看 RN DevTools Console。初始化失败页显示错误码与诊断编号，定位步骤见 [排障说明](troubleshooting.md)。

2026-10-19 登录基线：冷启动先通过 NativeSession Bridge 读取 Native 内存会话。未登录时 RN 不挂载首页或任何业务子页，并经 NativeHost Bridge 拉起认证；MainActivity 保持在栈内，登录成功只关闭 AuthActivity，宿主恢复后发布快照并放行 RN 首页。取消认证停留在登录门禁，可显式重试。会话仅进程内保存，杀进程后重新认证；当前使用本地注册账号。详见 [登录门禁 ADR](../adr/011-required-native-authentication.md)。

Android Studio 与 CLI 使用同一 `com.entrytask`。`HomeFragment.USE_REACT_NATIVE=true` 挂载 RN；改为 false 保留原 Native 文本首页。常规业务开发不修改此开关。

## 入口与宿主

| 项目 | 当前约定 |
| --- | --- |
| JS / 根组件 | `src/index.js` / `src/app/App.tsx` |
| AppRegistry 名 | `EntryTask` |
| Android Application / 包 | `.MainApplication` / `com.entrytask` |
| Android Debug / Bundle | `src/index` / `../../src/index.js` |
| iOS Debug / Bundle 入口 | `src/index` / `ENTRY_FILE=src/index.js`；本轮未修改 iOS |
| Codegen | `specs/Native*.ts`、`jsSrcsDir=specs`、`com.entrytask.specs` |
| Host 运行契约 | `contracts/bridge/runtime.json`，版本1.1；增加宿主快照、事件及受控命令 |

MainApplication 的唯一 ReactHost → MainActivity 的一个 HomeFragment → 一个 ReactFragment / RootHub Surface。四个 Native Tab 只改变 ShellRepository 选中状态（XML/VM 与 Bridge 共用），再由 `onHostEvent` 通知 RN；不创建四个 RN Fragment。首次 bootstrap 返回完整 Shell 快照，事件带递增 revision 以丢弃旧消息。

RN 根页首次访问才挂载，切 Tab 后保留已访问根页的滚动和分段，暂停非活跃查询。React Navigation native-stack 负责公共子页；RN 上报深度，Native 隐藏/恢复底栏。系统返回优先子页栈，其次回首页，再走原有退出提示。

会话 epoch 改变清理旧查询并重建对应资源投影；退出清除受保护子栈。游客完成认证可以回原入口；写操作不因认证完成自动重放。异常恢复、复杂配置恢复和生产会话协调仍需进一步专项验证。

## 目录和封装

- `src/app`：应用图、Shell VM、导航及子页契约组装。
- `features/home|wealth|member|profile`：各域模型、页面 VM、Screen、DI 和 public 入口。
- `features/services`：服务目录、搜索、统一说明/文档模板及规范化展示模型。
- `features/messaging`：未读查询、Native 已读写入与缓存失效。
- `infrastructure/state`：唯一 RTK Query ApiService、资源订阅引用计数、按 epoch 隔离查询和写后失效。不另建服务端数据仓；已移除原样板选中服务 slice。
- `shared/presentation`：库无关的资源 VM 生命周期原语及 React 订阅绑定。VM 只持有 query cache 的展示投影。
- `shared/ui`：主题、线性图标、工具栏、服务宫格、分段、卡片、按钮和状态组件。
- `shared/assets`：用户原型局部装饰图的本地映射；不使用整页截图代替界面。
- `platform/native`：唯一 Codegen 导入及桥接解码入口，协议异常明确返回，不提供 JS 假成功实现。

依赖锁定：React19.2.3 / RN0.86.0、Redux Toolkit2.13.0、Axios1.20.0、React Navigation native7.5.1 / native-stack7.21.0、screens4.30.0、svg15.16.0。safe-area 由现有 lockfile 锁定。iOS Pods 本轮未更新，不能据 JS 依赖已安装判断 iOS 可运行。

## 网络与 Mock

34个 RN API 在 `contracts/api/endpoints.json` 显式选择 `nativeMock`，每个有独立响应 schema 和 JSON；包括四根页内容、产品/资讯/活动、个人摘要/资产、服务/搜索、签到/权益/消息等。加既有 Native 认证2路由，manifest共36路由；退出是受控 Native 命令，不声称37个 HTTP API全部实现。

RN 经 EndpointRegistry → TransportRouter → NativeNetwork；Native 独立校验 apiId、方法、授权、ID和参数，再进入 `MainApplication.prototypeNetwork` 注入的 `demo_engine.PrototypeMockInterceptor`。该 client 不作用于 Metro、RN HTTP 或既有 Native 认证 client。普通请求后台随机等待1000～3000ms，支持取消；未知路径本地拒绝，不放行公网。

RN HTTP 保留独立 Axios 封装，只能按明确配置接入公开 HTTPS 服务，不携带银行凭证，不在 Native Mock 失败时自动回退。当前没有真实银行服务。

新增 API：

1. 定义 endpoint 的 apiId、方法、路径、权限、超时和 schema。
2. 在 mock 按 API 建 fixture，manifest 配置 handler；动态 ID 使用受控路径模板，参数走白名单。
3. 如需状态变更，在 demo_engine 实现权威状态转换；先保证业务唯一性再提交，RN 不改 fixture 或自增余额。
4. 运行 `npm run contracts:generate`、`npm run contracts:check`、相关行为测试和原生构建；核对最终 APK 中的资源。

契约/Mock 由 Gradle Variant API 注册的生成任务自动打包到 assets。不得退回仅登记生成路径、不关联 producer 的配置；历史上这会导致 clean 后漏资源而初始化失败。更新 JSON 必须重新构建安装，Reload 不能更新 APK assets。

当前 demo_engine 使用每个 Native 用户 scope 的独立 JSON 账本，经单锁和 SharedPreferences commit 原子写入；覆盖签到日期、能量记录、领取记录及消息已读。它是轻量演示实现，尚未完成总体设计的 Room、全局库存事务、固定 Clock 场景及完整幂等冲突模型。既有 Native 认证存储问题不在本轮整体迁移。

## 检查

根目录：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand --watchman=false
npm run bundle:android
```

Android目录：

```sh
./gradlew :demo_engine:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
```

Release仍使用已有演示签名，内置 Bundle，正式发行安全与混淆基线未在本轮处理。构建、设备验证、性能测量分别记录；不能把脚本成功等同所有产品场景已通过。
