# Android 项目说明

Android 是 EntryTask 当前的 Native 宿主与原型运行端。它提供登录注册、应用外壳与根 Tab、Native 会话和平台能力；四个业务 Tab 及其子页面由共享 RN 工程实现。工程是无后端的教学原型，业务 API 使用本地 Mock，不代表真实银行服务或生产级认证。

## 模块与职责

| 模块 | 当前职责 |
| --- | --- |
| `app` | 应用入口与组装、唯一 ReactHost、HomeFragment/RN Surface、Native 根 Tab、Codegen Bridge 适配、Mock/contract 资源打包 |
| `biz_user` | Native 登录注册 UI、用户业务 MVVM、Repository 与会话实现 |
| `lib_base` | AppGlobals/AndroidX Startup 应用信息代理、基础能力及现有 UI/MVVM 封装 |
| `lib_net` | Native 网络基础设施及进程内下载初版（可靠性边界见技术方案） |
| `lib_storage` | Native 存储封装 |
| `demo_engine` | RN 原型 API 专用 Mock 拦截与演示业务状态，与 Native 登录认证 Mock 分离 |

模块依赖由 `app` 组装，基础库不新增对业务 UI、`app` 或 RN 的反向依赖。架构目标与模块演进边界见 [Android 技术方案](../docs/design/Android%20技术方案.md)；当前已实施的 Native 设计说明见 [Android 架构文档](docs/01-architecture-design.md)。

## RN 宿主和运行约定

当前采用 Native 提前认证 + RN 默认页面门禁：MainActivity.onCreate 经 VM/Repository 先读 Native 会话，首次游客在 RESUMED 直接拉起认证，不等待 RN；显式启用时在认证首帧提交后预热唯一 RN 引擎和Bundle，不创建业务Surface或请求首页。返回或启动失败后再挂载 RN；RN 经 Session Bridge 校准会话，默认保护业务页面，仅帮助中心公开。后续认证通过 Host Bridge 请求，展示期间重复请求合并。AuthActivity 成功设置 Activity Result 后只关闭自身；singleTask MainActivity 通过结果回调及恢复时重读 Native 会话并发布快照。登录根页先收键盘，再按2秒双击退出整个任务；注册页系统返回登录。程序性取消仍保留门禁/重试协调。singleTask 重入使用流程编号及旧空结果消费隔离迟到取消，旧 Activity 清理不影响新流程。完整边界、AppGlobals/Startup 和下载能力现状见 [Android 技术方案 V1.2](../docs/design/Android%20技术方案.md)。

- 根目录 `npx react-native run-android` 是官方 CLI 开发路径；Android Studio 使用同一 `android/` 工程。
- `applicationId` / `namespace` 为 `com.entrytask`，Application 注册的 RN 根组件为 `EntryTask`。
- React Native JS 入口为仓库根下 `src/index.js`。Gradle RN 插件显式配置 `../../src/index.js`，Metro 与 Codegen 配置在仓库根目录。
- `MainApplication` 持有进程内唯一 ReactHost。Native Tab 切换同步给一个 RN Surface；不能在每个 Tab 或 Fragment 各自创建 RN 实例。
- `HomeFragment.USE_REACT_NATIVE` 保留 RN 首页与 Native-only 首页两条调试路径。常规开发使用 RN 路径；切换开关只影响首页内容，不改变 RN 工程结构。
- TurboModule TypeScript 契约位于根目录 `specs/Native*.ts`，`package.json` 的 Codegen 扫描 `specs/` 并生成 `com.entrytask.specs` 下的接口；Android Native 实现由 `EntryTaskPackage` 注册。修改契约需同步 JS adapter、Native 实现、注册与 Codegen 构建。

认证账号在Native Mock中写入SP并逐账号保存加盐校验值；进程重启只清内存会话，登录经同一拦截器重读SP校验。旧mock_reg/mock_db账号按需原子迁移，不在启动时删除。详见 [认证返回与账号持久化修复](../docs/development/认证返回与账号持久化修复.md)。

## 首页 MVVM 与分步迁移

当前首页保持 XML：MainActivity.setContentView 加载 activity_home.xml，MainShellView 只渲染 MainViewModel 的只读 MainUiState。Activity 在 STARTED 区间收集状态，在 RESUMED 区间执行认证展示。后续再单独转换 Compose；认证模块已有的 Compose 保持原状。

```text
XML 点击 → MainViewModel → ShellRepository ← ShellBridgeAdapter ← HostModule ← RN
                                  ↓ StateFlow
                      XML 渲染 + HostModule.onHostEvent → RN
```

- `app/.../shell/domain` 定义 Shell/会话契约和返回策略；`data` 适配 Native 会话；`presentation` 放 VM 和 XML 渲染；`host` 管宿主句柄与前台端点；`di/ShellGraph` 在 Application 组装。
- Repository 是 Tab/depth/revision 唯一来源，UserAuthManager 是会话权威。BottomTabBar 点击只上报动作，选中外观由 UiState 回写。
- MainActivity 复用 `entrytask-root-hub` 对应的 HomeFragment；首次认证返回/失败后不存在才添加（系统恢复既有片段仍按框架时序），内层保持 `entrytask-rn-root` / `R.id.rn_content`，Tab 与认证不替换宿主。
- XML 根预留系统底部安全区，RN 容器预留顶部/侧边，RN 按裁剪后的视口计算剩余 Insets；游客/子页隐藏分割线与底栏，始终保留 XML 内容槽，首次认证结束后挂载 RN 门禁容器。
- 认证展示使用 VM 防重状态、前台端点和 Activity Result；结果通知后重读 Native 会话，不根据 RESULT_OK 直接设置登录。
- 返回路径为 IME → RN 子栈 → Native 根 Tab → 首页500ms双击退出。深度冲突先同步，以 RN 实际栈纠正。

## 环境要求

- Node.js `>=22.11.0` 与 npm，供 RN CLI、Metro、Codegen 和资源校验任务使用。
- Android SDK Platform/Build Tools 36、NDK `27.1.12297006`；版本来自 `android/build.gradle`。
- Gradle Wrapper 9.3.1，Kotlin 2.1.20。
- 安装兼容当前解析出的 Android Gradle Plugin 与 RN Gradle Plugin 的 JDK。仓库当前没有将 AGP/JDK 矩阵锁定为一个可在此 README 中保证的固定版本；可先在 Android Studio Gradle 设置中选择 JDK，再于 `android/` 执行 `./gradlew --version` 检查 Gradle 实际使用版本。不要把 JVM 编译目标 17 当成 Gradle 必须使用 JDK 17 的证据。
- 可用 Android 模拟器或开启 USB 调试的 Android 设备。

环境安装细节与 RN 版本相匹配的配置，参考 [React Native Android 环境搭建](https://reactnative.dev/docs/set-up-your-environment?platform=android)。不要提交本机 `local.properties` 或绝对 SDK/JDK 路径。

## 从命令行运行

在仓库根目录执行：

```sh
npm ci
npx react-native run-android
```

CLI 会启动/连接 Metro、调用 Gradle 构建、安装并启动应用。也可以把 Metro 和安装运行拆成两个终端：

```sh
# 终端一（仓库根目录）
npm start

# 终端二（仓库根目录）
npx react-native run-android
```

多个设备连接时，先用 `adb devices` 确认目标设备，CLI 可通过 `--device <serial>` 指定。常用 RN 静态检查在仓库根目录执行：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand
```

## 从 Android Studio 构建

1. 在 Android Studio 打开仓库的 `android/` 目录，而不是单独打开 `app/` 模块。
2. 等待 Gradle Sync 完成，确认 IDE 使用的 Gradle JDK、Android SDK 与本机安装一致。
3. 选择 `app` 运行配置及 `debug` variant，连接模拟器/设备并运行。

等价的命令行构建在 `android/` 目录执行：

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
# 连接设备并启动 Metro 后运行XML/宿主集成测试
./gradlew :app:connectedDebugAndroidTest
```

生成 APK 位于 `android/app/build/outputs/apk/debug/`。`release` 当前仍沿用演示签名，且不是经过签名、混淆和发布验收的生产包。

## Native Mock 与 APK 资源

`contracts/` 与 `mock/` 位于仓库根目录，是运行时资源源文件。Android 构建流程先执行根目录 `tools/validate-resources.cjs`，再由 `android/app/build.gradle` 注册的变体资源任务将 contracts 和各 API JSON 同步到 Gradle `build/` 生成目录，最终由 AGP 打包入 APK 的 assets。运行时从 APK assets 读取，离线可用，不访问开发机源码路径。

API Mock 由 Native 网络客户端按 manifest/API 路由本地拦截，读取对应 JSON fixture；未注册 API 应失败，不回落到公网。普通模拟请求延迟 1～3 秒。登录注册的既有 Native Mock 与 `demo_engine` 的 RN 业务 API Mock 是不同职责，不应把 RN 页面数据硬编码到认证模块。变更 Mock、contract 或资源打包配置后重新构建安装；定位打包遗漏时可执行：

```sh
./gradlew :app:clean :app:assembleDebug
```

再检查最终 APK ZIP 中 `assets/mock/` 与 `assets/contracts/` 条目，而不是仅确认 `build/` 下有生成文件。Gradle 构建任务关系和资源约束见 [Android 子项目 AGENTS.md](AGENTS.md)。

## 日志与问题定位

建议先在仓库根目录启动专用日志工具：

```sh
npm run logs:android
```

Android Studio Logcat 可按 `package:com.entrytask` 过滤；排查 RN JS Console 时关注 `ReactNativeJS`，应用 Native 诊断关注 `EntryTask.RN`、`EntryTask.Bridge` 和 MLog 相关标签。Metro 输出由 `npm start` 的终端提供，Metro DevTools 可查看 RN Console。出现 Native 能力初始化失败时，按以下顺序采集证据：

1. 记录页面错误码与诊断/flow ID，确认设备和进程确实是当前安装的 `com.entrytask`。
2. 查看 Metro 是否成功加载 `src/index.js`，并在 Logcat 检查 Native Host 创建、Codegen 模块注册、Bridge bootstrap 的阶段日志。
3. 确认 `EntryTaskPackage` 注册的 Native 模块与 `specs/` 契约一致；核对包名、组件名 `EntryTask` 和宿主只初始化一次。
4. 对资源类错误，检查 Gradle 的 `validateRuntimeResources`、`prepare<Variant>RuntimeResources`、`merge<Variant>Assets` 任务和最终 APK assets。
5. 继续按 [运行故障排查](../docs/development/troubleshooting.md) 的错误码与日志路径处理，不先用清缓存掩盖构建或注册问题。

日志必须脱敏：禁止输出密码、Token/Cookie、请求体及完整个人资产数据。RN Logger 不依赖 Bridge 才能初始化；Native Bridge 日志与 JS 日志通过受控 `flowId`/`requestId` 关联。

## 相关文档

- [根目录工程总览](../README.md)
- [RN 项目 README](../src/README.md)
- [全工程总体设计](../docs/design/EtryTask%20项目设计方案.md)
- [Android 技术方案](../docs/design/Android%20技术方案.md)
- [首页 MVVM、混合宿主与后续 Compose 分步方案](../docs/design/Android首页Compose与MVVM改造方案.md)（当前XML+MVVM，Compose延期；验证见方案第5节）
- [RN 技术方案](../docs/design/前端RN技术方案.md)
- [iOS 技术方案与延期范围](../docs/design/iOS技术方案.md)
- [Android 架构文档](docs/01-architecture-design.md)、[Android 工程规范](docs/02-engineering-standards.md)、[实施路线图](docs/03-implementation-roadmap.md)
- [RN 启动与宿主基线](../docs/development/rn-foundation.md)、[运行故障排查](../docs/development/troubleshooting.md)
- [启动与登录全链路源码详解](../docs/development/启动与登录全链路源码详解.md)：从 attachBaseContext、Startup 到 RN Bridge 登录与首页请求，逐步对应方法和源码行号。

预热默认关闭（当前模拟器未证明稳定收益）；用 `-PentrytaskRnWarmup=true` 显式启用。配置、Release A/B复现与实测限制见 [RN预热性能验证](../docs/development/rn-warmup-performance.md)。
