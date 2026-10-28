# iOS 子项目规则

适用于 `ios/`，继承根 [AGENTS.md](../AGENTS.md)。详细依据 [iOS 技术方案](../docs/design/iOS技术方案.md)。

## 当前范围

- iOS已实现Native Shell、认证、五个TurboModule和本地Mock，支持RN/SwiftUI四页双模式；当前实现与验证入口见[README](README.md)。以源码和实际验证确定完成范围。目录和锁文件存在不能证明能构建或能运行银行业务。
- 当前任务若仅涉及 RN/Android，不安装 Pods、不改 iOS 业务代码、不顺带引入 Swift Package 或重构 AppDelegate。共享契约变更说明iOS影响及实际实施状态；只有当前任务明确包含 iOS 实施或必要入口适配时才开展相应工作。
- 创建/维护协作与设计文档不代表业务代码已实现或已通过验证。也不得用 JS fake 模块掩盖 iOS 尚无原生能力。

## 保留现有集成

- AppDelegate创建进程组合根，App/SceneDelegate连接单窗口，RNRuntimeOwner使用 `RCTReactNativeFactory` / dependencyProvider；维护一个 factory/runtime，组件名 `EntryTask`，不启用多Scene。
- Debug Bundle root 为 `src/index`，Xcode 脚本 `ENTRY_FILE=src/index.js`；Release 从包内读取 `main.jsbundle`。同步入口时核对 Metro、打包路径、组件注册，不恢复旧根入口。
- 保留 Podfile 的 RN helper、`use_react_native!`、autolinking、post_install 和 Codegen 链；不通过删 Pods、禁用新架构或增加旧 RCTBridge 解决集成错误。
- 保持现有 bundle identifier、target/scheme 和签名身份。最低系统与 Xcode/Swift/Ruby/Pods 版本需按实际配置和依赖要求核查，不未经验证提高 deployment target。
- 不编辑 Pods、DerivedData 或生成头；不提交本机 `.xcode.env.local`、证书、provisioning 或路径。依赖更新有明确理由，保留 Podfile.lock / Gemfile 等既有约束，不通过 pod update 全量漂移。

## 实施架构与模块约束

按方案分阶段实现；只创建有实际职责和调用方的模块，不创建空壳或另一套业务页面。

- AppContainer 组装 Native Shell、认证、会话及平台能力。Xcode默认SwiftUI四根页，官方CLI默认RN四域页/子页，根Tab和认证共用Native Shell。DashboardFeature按四域MVVM复用NetworkPort，不复制账本、认证或另建根底栏。
- 首期为现有App target加六个Swift library target：CoreKit、UserFeature、NetworkKit、StorageKit、DemoEngine、DashboardFeature。认证公开契约在UserFeature/Public，技术Port、共用UI和日志在CoreKit内按目录隔离；不预建UserAPI、NativeUI、TelemetryKit、PlatformContracts独立target。技术契约不依UIKit/React/业务实现，各库通过Port及App适配器协作，不互相导入具体实现。
- AppContainer注入会话、网络和存储Port。认证及原型Mock统一归DemoEngine，NetworkKit只提供通用传输与拦截接点，StorageKit只提供受控持久化；DemoEngine通过App存储适配器读写，不让技术库反向依赖业务实现。Android工程保持现状，不将其重构作为iOS实施前置条件。
- 业务 ViewModel 用不可变状态和明确动作，不持有 VC/View；UI 操作在 MainActor，阻塞 IO/密码计算移出主线程。会话由 Native 统一管理，凭证不进入 JS、导航参数或普通 UserDefaults。
- Codegen 路径为 `specs/Native*.ts → 生成接口 → ObjC++ adapter → Swift facade/Port`。ETFactoryDelegate通过getModuleClassFromName/getModuleInstanceFromClass注入五模块，未知模块交回父类；不只返回getModuleProvider实例来假定完成注入。注册与签名以仓库 RN 版本生成物核实，不能将 React/C++ 强塞纯 Swift Package 或重复注册模块。
- async/await、Task、Promise 回调有明确所有者和取消路径；Promise 恰好完成一次，runtime 销毁注销监听/取消任务，回调不持有已销毁宿主。Swift/ObjC 边界使用可表示的安全值，不用全局 `@unchecked Sendable` 掩盖隔离问题。
- Native 网络使用受控 URLSession；本地 Mock 拦截只属于 demo client，不全局污染 Metro/RN HTTP。按 API manifest 读取共享 JSON，后台随机延迟 1～3s且可取消，未知 API 拒绝。
- 从仓库根 `mock/`、`contracts/` 经校验和声明输入/输出的构建步骤进入 Bundle；验证最终安装包，不依赖开发机文件。SPM 资源用显式 Bundle provider，不假定全部在 Bundle.main。
- Native typed 存储、Keychain/数据库分别按职责管理；敏感数据迁移、用户隔离、会话失效及事务按设计实现。不因为 Android 已有某个过渡实现就复制其安全缺陷。
- 单 RN instance / 单业务 Surface；child VC containment、前后台、safe area、手势、内存压力及监听释放完整配对。未来多 Scene 需单独设计，不直接复制 runtime。
- RN Logger 独立工作，Native 日志支持安全关联和错误诊断；初始化、请求、会话和取消阶段可追踪，禁止记录敏感载荷。

## 双模式约束

- `ENTRYTASK_PAGE_MODE`是构建设置，Info.plist写入EntryTaskPageMode；只允许native/rn，不使用UserDefaults或启动参数覆盖。Xcode默认native，react-native.config.js使用CLI项目命令扩展传入rn，禁止修改node_modules或持久写本机开关。
- Native模式不创建RN factory/root view，不依赖Metro；保留RN依赖/Codegen，只跳过业务JS Bundle，清理切换后遗留Bundle。RN模式继续每进程单instance/Surface。
- DashboardFeature只依赖CoreKit，经组合根注入Repository；四个VM和区域资源独立，Shell协调已认证/epoch/前台/当前Tab，取消与迟到结果受版本约束。资产显隐为会话内共享状态，退出复位；签到不重放未知结果。
- 共用图片从src/shared/assets构建生成dashboard-art，不能手工复制到iOS源码。测试同时核对两种产物模式、Native无Bundle、RN Release有Hermes Bundle、JSON与13张素材完整。

## 验证与交付

1. 先核实实际 Node/RN、新架构、Xcode/SDK、Ruby/Bundler/CocoaPods、模拟器/真机与签名条件；工具链缺失时说明，不能填“通过”。
2. 在仓库根按 Gemfile 使用 Bundler；依赖需要时执行 `bundle install`，然后在 `ios/` 执行 `bundle exec pod install`，检查 lockfile 差异。不为普通 RN/Android 任务执行。
3. 在 `ios/` 用 `xcodebuild -list -workspace EntryTask.xcworkspace` 核实 scheme，再用 `xcodebuild -showdestinations -workspace EntryTask.xcworkspace -scheme EntryTask` 获取实际 destination。scheme 名仍需以输出确认。
4. 使用实际 workspace/scheme/destination 执行 build/test，并按改动验证根目录官方 `npx react-native run-ios`；不写死设备 UDID。Swift库测试为EntryModulesTests，Xcode scheme包含模块测试和AuthenticationUITests。运行 `node tools/check-ios-architecture.cjs` 检查依赖边界。
5. 对 NativeModule 验证生成/注册、bootstrap、协议错误、取消和销毁；对宿主验证认证往返、根 Tab、返回/手势、重建与唯一 runtime。
6. 资源/打包变化验证 Debug 和优化包内的 Bundle、契约及 fixture，必要时无 Metro 离线运行。Archive、签名与分发仅在任务要求时进行，不将模拟器 build 说成可正式发布。

只改文档时检查链接与范围表述，不执行 Pods/Xcode。交付逐项区分设计、源码改动、编译、设备验证和待实施项。

## Code Review Rules

- 阻止在 Android/RN 任务中无关启动 iOS 工程改造，或把模板/锁文件当作五模块与业务已经实现的证据。
- iOS 实施时重点检查第二个 RN runtime、旧桥并行接入、线程隔离/回调生命周期错误、Bundle 缺资源及敏感数据暴露；基于实际生成接口和构建证据评审。

## Swift实现细则

- 源文件用简洁中文说明职责与关键流程。跨模块使用Public契约，App适配器注入会话、账号/状态存储，不修改Android来迁就iOS。
- CoreKit技术契约不导入UIKit/SwiftUI；UI和Logging按目录隔离。数据库、密码派生、JSON和Mock延迟不在主线程执行。
- 登录/注册独立VM，成功和提示进入状态，当前页面可见且应用active时交付副作用；切页取消清密码，不保存恢复凭证。
- 会话提交/退出和Demo事务经提交协调器串行化，scope/epoch由Native捕获；取消不撤销已经提交事实。
- onHostEvent直接发Shell快照，ACK不是认证成功。UTF-8限长，Promise一次终态；Reload释放监听/任务，保留进程会话。
- 资源生成清理失效输出，失败阻断打包，从空构建目录验证.app资源及Release Bundle。
- 测试使用独立数据库/scope，不清正式账号库；保留锁文件，不执行无关依赖升级。
