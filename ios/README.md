# iOS 工程

iOS 采用 UIKit 宿主、SwiftUI 登录注册、Swift MVVM 和单 React Native instance。应用提前认证后，CLI默认加载单个RN Surface，Xcode默认加载SwiftUI Native四根页。两种模式复用会话、根Tab、网络、Mock和存储；Android工程保持不变。模式、组件和验证说明见[双模式指南](../docs/development/ios-dashboard-modes.md)。

产品与架构依据：[总体设计](../docs/design/EtryTask%20项目设计方案.md)、[iOS 技术方案](../docs/design/iOS技术方案.md)、[协作规范](AGENTS.md)。RN 业务结构见 [RN README](../src/README.md)。当前没有真实后端，也不提供持久登录、真实银行凭证或生产认证服务。

没有 iOS 开发基础时，先读 [iOS 基础与调试新手指南](../docs/development/iOS基础与调试新手指南.md)：按首次运行、Xcode 界面、Swift 基础、源码数据流、日志和断点逐步学习。

## 模块与目录

| 单元 | 职责 | 主要入口 |
| --- | --- | --- |
| EntryTask App target | 组合根、单窗口 Shell、Tab、RN 宿主、桥接与适配 | [AppContainer](EntryTask/App/AppContainer.swift) |
| CoreKit | 技术 Port、错误、提交执行器、日志、公共品牌 UI | [Ports](Packages/EntryModules/Sources/CoreKit/Contracts/Ports.swift) |
| UserFeature | 内存会话、认证 Repository/VM/SwiftUI | [AuthScreen](Packages/EntryModules/Sources/UserFeature/Presentation/AuthScreen.swift) |
| NetworkKit | 专用 URLSession、请求登记/取消/限额、URLProtocol | [NetworkClient](Packages/EntryModules/Sources/NetworkKit/Client/NetworkClient.swift) |
| StorageKit | GRDB、事务、版本迁移、typed 偏好 | [LocalDatabase](Packages/EntryModules/Sources/StorageKit/Database/LocalDatabase.swift) |
| DashboardFeature | Native四页模型、Repository、四域VM、共用SwiftUI组件 | [DashboardCoordinator](Packages/EntryModules/Sources/DashboardFeature/Presentation/RootPageViewModels.swift) |
| DemoEngine | 认证拦截、KDF、API handler、签到/权益等业务状态转换 | [DemoHandler](Packages/EntryModules/Sources/DemoEngine/Routing/DemoHandler.swift) |

六个库在一个 [本地 Swift Package](Packages/EntryModules/Package.swift) 中分别编译。除 StorageKit 依赖 GRDB 外，各功能库只依赖 CoreKit；App 的 [适配器](EntryTask/Adapters/StorageAdapters.swift) 连接存储与模拟业务契约。库不导入 React，不反向依赖 App，不直接导入其他功能库实现。

```text
EntryTask/
├── AppDelegate.swift             进程依赖组装
├── App/                          单 Scene 连接、AppContainer
├── Shell/Presentation/           Shell 状态、认证展示、根 Tab
├── RNIntegration/                唯一 factory/runtime/root view
├── NativeModules/                五个 ObjC++ adapter 与 Swift facade
└── Adapters/                     会话/账号/账本存储接线
Packages/EntryModules/
├── Sources/{六个库}/             平台能力和认证业务
└── Tests/EntryModulesTests/      Swift 行为和网络集成测试
EntryTaskUITests/                 共用认证及Native/RN四页设备测试
```

## 启动与数据流

1. `AppDelegate.didFinishLaunchingWithOptions` 创建 AppContainer。Logger 立即可用，数据库懒打开，不在启动主线程执行 KDF/磁盘读取。
2. `SceneDelegate.scene(willConnectTo:)` 连接一个窗口和 ShellViewController。Info.plist 禁用多 Scene，进程只保留一个应用图；RN模式最多一个runtime，Native模式不创建runtime。
3. Shell 首次可见读取 Native 内存会话；未认证先展示全屏 AuthScreen，暂不挂载业务内容。登录界面不能下拉关闭，iOS 使用系统手势回到桌面，不模拟 Android 双击退出。
4. 登录/注册分别持有 AuthViewModel，AuthFormContent 只接收状态和动作。提交通过 NetworkAuthRepository → NetworkClient → 专用 DemoURLProtocol → DemoHandler → MockAccountStore → StorageKit。注册写库属于网络拦截行为。
5. 成功结果经 CommitExecutor 校验 epoch 并提交内存会话；AuthScreen 只在 active 时交付 onAuthSuccess。Shell 关闭认证自身，重读会话后，按构建模式挂载唯一RNContainer或NativeDashboardView。
6. RN模式通过bootstrap获取Native Shell/会话快照，默认页面门禁走Bridge；子页深度同步后隐藏Native Tab栏。Native模式通过DashboardCoordinator投影认证/epoch/Tab/前台状态，只激活当前页。两种模式退出均递增epoch，迟到响应不能覆盖新会话。

账号库与演示账本分别为 Application Support 下的 `entrytask_auth.db`、`entrytask_demo.db`。账号只保存哈希账号键和带随机盐的 KDF 校验值，不保存明文用户名/密码/Token。文件使用 iOS 数据保护且排除备份；GRDB/SQLite 本身不等于数据库加密。会话仅存内存，结束进程后需重新登录，注册账号仍可再次登录。

## 网络、Bridge 与资源

- 五个薄 Codegen adapter：NativeHost、NativeNetwork、NativeStorage、NativeSession、NativeTelemetry。入口为 [ETFactoryDelegate.mm](EntryTask/NativeModules/ETFactoryDelegate.mm)，实际调用集中到注入的 [ETBridgeServices](EntryTask/NativeModules/ETBridgeServices.swift)。未知模块由 RN dependencyProvider 正常处理。
- 原生网络仅接受清单中的 API；当前 37 条 Mock 路由，35 条允许 JS 使用，2 条登录注册仅 Native 使用。不存在失败转公网或自动切换 RN HTTP 的行为。
- 普通 Mock 随机延迟 1～3 秒，后台执行、支持取消；URLSession 保留 Native 请求上下文与原始 body，避免请求复制为 body stream 后丢失表单。
- 根 `contracts/`、`mock/` 只有一份源码。[构建脚本](../tools/prepare-ios-resources.cjs) 校验并打入 `.app`，生成 `runtime-resources.index.json`，删除已失效输出。新增/修改 Mock 后重新构建；不依赖 Metro 或开发机目录读取数据。
- RN模式Debug从Metro的`src/index`加载，Release由现有RN/Hermes脚本生成`main.jsbundle`；Native模式不依赖Metro或业务JS Bundle，组件注册名始终是 `EntryTask`。

## 环境与运行

要求 macOS、Xcode 与 iOS Simulator、Swift 6 编译器（应用 Swift 语言模式仍为 5）、Node >=22.11、仓库 Gemfile 对应的 Ruby/Bundler。GRDB 精确锁定 7.8.0，保留 Package.resolved 与 Podfile.lock。首次解析 SwiftPM/Pods 需要可访问依赖源，包括 GRDB 的 Git 子模块。仓库锁文件指向官方源，不提交本地镜像或缓存路径；遇到解析失败时参照下文 Missing package product 排障。

```sh
# 仓库根，依赖缺失时
npm ci
bundle install
cd ios
bundle exec pod install
xcodebuild -list -workspace EntryTask.xcworkspace
xcodebuild -showdestinations -workspace EntryTask.xcworkspace -scheme EntryTask
cd ..
npx react-native run-ios
```

Xcode 打开 `EntryTask.xcworkspace`，选择 `EntryTask` 和实际 destination。若Xcode安装缺少独立Simulator.app，项目CLI扩展在Debug模拟器模式下使用官方build-ios和CoreSimulator安装启动；完整Simulator.app环境沿用官方run-ios。模拟器免签不代表真机签名配置已完成。需要指定设备时使用 CLI 的 `--simulator` 或 `--udid`；已有 Metro 时可加 `--no-packager`，不重复占用 8081。

## Xcode 提示 Missing package product

如果 CoreKit、UserFeature、NetworkKit、StorageKit、DemoEngine 同时提示缺失，先检查 **Report Navigator 中的 Resolve Packages / 构建原始日志**。截图中的五个产品与新增DashboardFeature均属于同一个本地EntryModules，产品缺失可能是整个 Package Graph 未完成解析的连带结果，不应逐个删除或重建模块。

本工程需要解析 `https://github.com/groue/GRDB.swift.git` 的固定版本，以及其 Git 子模块 `https://github.com/swiftlyfalling/SQLiteLib.git`。任何一项下载失败，都可能阻断本地六产品的解析。首先解决依赖源的网络访问，再在 Xcode 的 File → Packages → Resolve Package Versions（名称以当前版本为准）重新解析；不要选择升级所有版本。

也可以在 `ios/` 目录使用相同 workspace 的标准解析命令：

```sh
xcodebuild -resolvePackageDependencies -workspace EntryTask.xcworkspace -scheme EntryTask
```

命令应成功列出 GRDB 与本地 EntryModules，再点击 Run。如果 Xcode 仍显示旧错误，关闭并重新打开当前 workspace 后重试。不要一开始清空全局缓存：这会触发重新下载，网络不通时只会扩大问题。

网络受限环境可由维护者从官方源准备并核验相同提交的依赖缓存；它仅解决本机依赖准备，不作为源码替换、升级或绕过子模块的手段。缓存清空或换机器后，仍需要完整依赖源可达或同等经过验证的缓存。独立 DerivedData 构建通过，不代表 Xcode 默认目录也已准备好依赖。

本次故障的分析证据、实际命令、缓存核验及修复后操作见 [完整处理记录](../docs/development/iOS编译失败处理记录-Missing-package-product.md)。

## 检查与测试

仓库根：

```sh
node tools/check-ios-architecture.cjs
npm run contracts:check
npm run typecheck
npm run lint
npm run architecture:check
npm test -- --runInBand
```

Swift 行为测试可在 `ios/Packages/EntryModules/` 执行 `swift test`。Xcode scheme 已包含模块测试和 UI 测试，在 `ios/` 使用查询到的 destination：

```sh
xcodebuild -workspace EntryTask.xcworkspace -scheme EntryTask \
  -configuration Debug -destination 'platform=iOS Simulator,name=<实际设备名称>' test
xcodebuild -workspace EntryTask.xcworkspace -scheme EntryTask \
  -configuration Release -destination 'generic/platform=iOS Simulator' build
```

测试使用临时数据库与固定业务时钟。设备 UI 测试通过 **仅 Debug 有效** 的启动参数生成隔离目录，不清空用户账号库。模块测试覆盖账号重复/重开、事务回滚、KDF/空白字符向量、取消/过期会话、typed 偏好、签到规则，以及真实 URLSession→Mock→SQLite 集成。UI 测试覆盖提前认证、表单隔离、注册返回当前构建模式的首页、四Tab、财富内容、签到写入、月度收支。

当前已在 iPhone 18 Pro / iOS 27 模拟器验证上述业务链路；macOS SwiftPM共18项测试，Xcode 共 10 项模块测试和 2 项 UI 测试。DashboardFeature的6项及主机网络集成的2项由SwiftPM执行，不计入Xcode的10项。Debug和从空DerivedData生成的Release模拟器构建均通过；Native/RN产物内81个运行时JSON及13张共用PNG的路径与SHA-256均对齐源码；RN Release包含Hermes main.jsbundle，Native Release无业务JS Bundle。同一构建目录native→rn→native切换及旧资源删除回归通过。物理设备、iOS 15.1 等旧系统、完整错误/Reload/内存压力矩阵与性能基线需继续验证；模拟器结果不能代替这些验收。

## 日志与排障

Native OSLog subsystem 为 `EntryTask`，category 为 `Native`，事件名区分启动、认证、网络、桥接。Xcode Console 按 `EntryTask` / `request.started` / `bridge.failed` 过滤。RN Console 使用 Metro `j` 打开的 RN DevTools；构建终端没有 JS 输出不能说明没有日志。

```sh
xcrun simctl spawn booted log stream --level debug --style compact \
  --predicate 'subsystem == "EntryTask"'
```

出现原生能力不可用时，先记录 RN 安全错误码/诊断编号，再检查 bootstrap、模块注入、安装包 contracts/mock 和请求关联 ID。只记录白名单字段、耗时和阶段；禁止输出密码、Token、body 或资产响应。Native Logger 不依赖 Bridge 初始化，RN Logger 也能独立输出。

本次双模式验证包含Native/RN各2项UI用例、Native跨Tab隐私和退出复位、真实签到及CLI安装启动。Release产物验证见双模式指南。旧的单模式测试数量不代表所有错误、恢复和性能场景已验收。

正式签名/分发、真实后端/生产凭证、持久登录、下载、预热、远程日志与专项性能优化均不在本次交付声明内。
