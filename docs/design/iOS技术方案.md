# iOS技术方案

版本：1.1 · 日期：2026-10-18 · 状态：详细设计，实施延期  
输入：[总体方案](EtryTask%20项目设计方案.md)、[RN方案](前端RN技术方案.md)、[需求](../requirements/01-prototype-requirements.md)

## 1. 范围与技术基线

本文件提供可执行的iOS对等架构设计，供未来排期使用。**本期不修改iOS代码、不安装Pods、不运行Xcode构建或设备测试；所有iOS实施任务与运行验收均为延期。** Android先行不意味着iOS能力已存在。

未来iOS提供Native认证、四Tab/Shell、单RN容器、五个TurboModule、URLSession网络/本地拦截、存储/日志；复用同一RN四业务域及子页。iOS不以SwiftUI重写产品/资产/权益等RN页面，也不在RN另建登录页。

| 项目 | 静态现状 | 设计与验证责任 |
| --- | --- | --- |
| 宿主 | AppDelegate创建RCTReactNativeFactory及默认RN全屏窗口 | 改为NativeShellViewController + 单RN child，复用factory/runtime |
| 最低系统 | project.pbxproj声明15.1，Podfile取RN最低支持；本地RN helper为15.1 | G0-I以RN、screens、Swift依赖支持矩阵取交集，暂以15.1为目标，不未经验证提升 |
| Swift | SWIFT_VERSION5.0 | 先用Swift5语言模式并逐步严格并发检查；工具链支持后单独评审Swift6模式 |
| 构建 | CocoaPods use_react_native!/autolinking | 保留RN Pods/Codegen，业务模块用本地Swift Package；RN ObjC++桥接留Pods/Xcode target |
| 数据库 | 无本项目业务实现 | 采用SQLite+GRDB（SPM精确版本），G0-I验证最低系统与Swift工具链；不假定最新版本兼容 |
| UI | RN模板，无Native认证/Tab | UIKit Shell+SwiftUI认证MVVM，iOS15.1可用ObservableObject路径 |

Xcode、SDK、Swift编译器、Ruby/Bundler、CocoaPods、GRDB与screens精确版本在G0-I登记；保留Gemfile.lock/Podfile.lock/Package.resolved及JS lock。不能以deployment target字段推断当前Pods/Archive已通过。

## 2. 模块化与工程目录

```text
ios/
  EntryTask/
    App/                          AppDelegate / AppContainer / BuildInfo
    Shell/                        ShellVC / ShellViewModel / HostCoordinator
    RNIntegration/                factory / 容器VC / 生命周期适配
    NativeModules/                五个ObjC++ Codegen适配器与注册
    Resources/GeneratedMock/      构建期共享JSON产物，不手工编辑
  Packages/EntryModules/
    Package.swift
    Sources/
      AppCore/                    Result/Error/Clock/安全标识
      PlatformContracts/          Network/Storage/Session/Host/Telemetry协议
      UserAPI/                    认证入口/安全会话只读契约
      UserFeature/                Native认证MVVM/Repository/SessionCoordinator
      NativeUI/                   认证主题/通用控件
      NetworkKit/                 URLSession/取消/总超时/缓存接口
      StorageKit/                 typed偏好/Cache DB/Keychain封装
      DemoEngine/                 manifest/handler/场景/GRDB Demo DB
      TelemetryKit/               结构化日志/文件sink
    Tests/                        各模块单测与黄金向量
  EntryTaskTests/                  桥接/资源/容器集成
  EntryTaskUITests/                原生认证与RN主链路
```

AppCore无UIKit/React/业务依赖；PlatformContracts、UserAPI是纯Swift安全协议。UserFeature依赖UserAPI/PlatformContracts/NativeUI，NetworkKit与StorageKit通过协议协作而非互相import实现；DemoEngine独立包含业务handler/表，不进入未来real依赖图。NativeModules依赖React生成头和纯Swiftfacade，通过AppContainer注入Port，不import私有Repository/DAO。

RN生成代码依赖CocoaPods的target，不强行将React/ObjC++移入纯Swift Package造成头搜索/模块链接冲突；纯Swift模块通过本地SPM product链接到app。对象创建只在AppContainer，禁止模块通过UIApplication.shared遍历keyWindow寻找业务单例。

域规模扩大后，可独立拆UserFeature的子target和Demo各域handler，但本期不为每页创建framework；对外public最小化，默认internal，测试用@testable import。资源bundle由显式provider注入，不假定Bundle.main包含所有SPM资源。

## 3. NativeShell与RN生命周期

### 3.1 单runtime/单Surface

AppDelegate组装进程级RNRuntimeOwner、SessionCoordinator和服务；ShellViewController拥有原生底栏及一个RNContainerViewController child。底栏可用独立UITabBar（四UITabBarItem）或等效Native控件，**不使用四个UITabBarController子VC各自创建RN root**；选中项为ShellViewModel的单一投影。

RNRuntimeOwner保留一个RCTReactNativeFactory及dependencyProvider；创建EntryTask root view的具体factory入口在G0-I按RN0.86生成头确认，不能照抄旧RCTBridge/RCTRootView的API假定新架构兼容。rootView生命周期与child VC绑定，child containment按addChild/didMove和willMove/removeFromParent正确配对，防手势/安全区/appearance丢失。[RN0.86现有应用集成](https://reactnative.dev/docs/0.86/integration-with-existing-apps)

ShellViewModel为@MainActor可观察状态，只有selectedTab、hostRevision、底栏/认证覆盖、runtimePhase/error，不拥有RN领域数据。Native选择Tab→更新revision→事件→RN确认；RN跨Tab走Host command，不先变更JS选中态。RN子页depth>1隐藏Native底栏，转场完成回根再显示，布局按约束重算而非仅设透明造成触摸遮挡。

### 3.2 安全区、导航与手势

RN内容容器与Native底栏分别约束到合适区域，根页不重复加tabBar bottom inset；子页容器延伸到系统安全区，RN SafeAreaProvider处理home indicator。状态栏样式由Shell协调并使用childForStatusBarStyle等容器机制，不能多个VC同时全局控制。键盘由当前RN/认证VC处理，SafeArea与IME事件不可叠加两次。

Shell不在RN外包一层负责业务push的UINavigationController；RN native-stack承担业务子页和侧滑。Native认证作为fullScreen presentation覆盖Shell，认证内可用SwiftUI导航，关闭时回原RN目标。认证手势/取消显式回调，interactive dismiss若允许须发送cancel；不能让用户滑走但Promise仍等待。本期建议认证fullScreen且明确取消按钮，避免表单sheet半遮导致底栏可点击。

RN侧滑开始仅发布pendingTransition，didComplete才提交新depth；手势取消还原原depth/底栏。VoiceOver焦点在子页完成后移至TitleBar，返回时恢复来源可见元素；Native tab item具有准确label/selected状态。根页没有Android式双击退出逻辑，系统Home/后台属于iOS平台行为。

### 3.3 前后台和内存

进后台会话不失效，不启动业务轮询；暂停活跃请求策略按VM所有权执行，写事务已提交则保留；回前台先会话/Host snapshot再处理过期资源。页面包含个人资产时Native可在应用切换快照上遮盖敏感区域，恢复前台移除；这是展示保护，不改变会话规则。

didReceiveMemoryWarning/系统压力转为memoryPressure事件，RN清公开内存资源/非活跃Panel View；Native清图片/fixture模板缓存，可重新读本地bundle；不删Demo DB或退出会话。runtime异常可重建一次，旧bridge/facade任务取消、订阅清理、注册新runtimeId；仍失败显示Native重试页。系统jetsam无法在进程内可靠兜底，重启按新bootId游客首页恢复。

## 4. Swift认证MVVM和会话

View层为SwiftUI LoginView/RegisterView，由UIKit UIHostingController宿主；VM使用@MainActor ObservableObject、@Published private(set) uiState与动作方法，兼容当前15.1目标，不以iOS17 Observation为前提。VM只接AuthRepository/Session接口，输入错误和submitting分开；View负责焦点/键盘与显示。

AuthRepository通过NetworkPort调用A01/A02，账号trim1～32、密码原样1～64；禁止空串网络请求；KDF/JSON不在MainActor执行。每个VM持有可取消Task，离开认证取消查询/提交等待并清密码；Task cancellation单独处理，不在catch中统一Toast业务失败。不把密码放UserDefaults/Keychain/SwiftUI SceneStorage/恢复state。

SessionCoordinator用actor串行化会话与epoch：guest/authenticating/authenticated/invalidating；token只在Native内部私有模型存在，安全Snapshot无凭证。提交完整NativeAuthSession后先更新快照/epoch，再发布事件；注册与登录同路径，缺用户或凭证拒绝成功。opaqueUserId只供RN投影隔离，不用于请求身份指定。

并发authenticate合并一个展示，flowId对应终态；取消回来源页，不退出应用。认证成功只恢复安全route，不自动签到/领取。logout立即递增epoch清个人态，A03捕获旧凭证受控best-effort，失败不恢复；current-epoch401失效一次，旧epoch401忽略，403保留会话。退出不删除模拟服务端账号/领取/签到。

Actor方法中的await可能发生重入，网络响应返回后必须重新校验epoch/operationId，不能以“使用actor”代替整个异步流程的原子性。关键事件通过快照恢复，不依赖Combine瞬时publisher保证必达。

## 5. TurboModule与Swift并发边界

### 5.1 桥接路径

`specs/Native*.ts → RN Codegen ObjC协议/C++ wrapper → ObjC++模块(.mm) → @objc Swift Facade → Swift业务Port`。ObjC++类实现生成接口并提供getTurboModule等实际要求，类名/注册清单在G0-I以生成物确认；Swift不直接假装实现C++ TurboModule接口。

Swift Facade仅使用ObjC可表示的String/NSNumber/闭包等类型，解析为Swift Sendable value后调用actor/async服务；业务模型不携UIKit对象跨隔离域。依赖通过模块provider/adapter注入，配置RN factory/dependencyProvider与Codegen自动链接，避免注册两次模块。

### 5.2 任务与回调

| 边界 | 设计 |
| --- | --- |
| JS调用线程 | 立即校验长度/登记请求，再转异步；不假定所有模块方法都在主线程 |
| Host/Auth | @MainActor检查当前可见宿主，展示/关闭在主线程；缺宿主返回HOST_NOT_READY |
| 请求注册 | RequestRegistry actor保管Task/URLSessionTask、deadline、终态；每requestId完成一次 |
| serialize/DB | 后台工作执行，明确Sendable DTO，不用Task.detached逃避隔离检查 |
| Promise回调 | facade持有一次性resolver，终态后清引用，确认runtime有效；不将JSI对象跨任意线程访问 |
| invalidate | 取消当前runtime任务、清事件订阅/Promise，不清进程会话和已提交事务 |

网络/存储/Host错误统一总体BridgeReply schema；普通错误结构化，Promise reject仅不可处理的桥接异常。cancel-before-register有30秒有界墓碑，duplicate requestId不执行第二次。事件按固定枚举+revision发送，onHostEvent注册计数正确，在没有listener时保留快照而非无限积压事件。

Swift5语言模式先开并发警告，G0-I逐步提升至complete检查；跨ObjC回调/URLProtocol生命周期用受控锁/actor边界，不能全局@unchecked Sendable压制告警。[Apple严格并发迁移说明](https://developer.apple.com/documentation/swift/adoptingswift6)

## 6. URLSession网络与本地拦截

### 6.1 Client

NetworkKit持有专用ephemeral URLSession，关闭系统URLCache/自动Cookie，缓存由统一CacheStore负责；配置protocolClasses=[DemoURLProtocol.self]仅用于Demo client，不使用全局URLProtocol.registerClass影响RN/Metro/第三方。request timeout/总deadline默认10秒，待处理32/运行8，由请求协调器限制，URLSession连接数不等于整体并发上限。

API descriptor构造固定`https://entrytask.invalid` URL；JS无任意URL/Header，A01～A03仅Native调用。GET单飞/缓存/epoch规则同总体方案，包含个性资格的A21不进入公开缓存。HTTP状态与业务码、坏JSON分别上送RN；当前epoch401在Native先失效，bodyText保留故障测试数据但不记录日志。

URLSession支持在专用配置中接入自定义协议类，这是本地拦截的接点。[Apple protocolClasses](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/protocolclasses)

### 6.2 DemoURLProtocol生命周期

1. canInit对该专用Demo session的所有业务HTTP/HTTPS请求返回可处理；不只匹配已知API，否则未知路径可能落向真实网络。外部/错误Host返回受控拒绝，绝不转发。
2. startLoading解析上下文/manifest，创建有归属的Task，经DemoController读取场景/Clock；用可取消Task.sleep等待随机1000～3000ms，不阻塞delegate queue/主线程。
3. 调用handler生成Data和HTTPURLResponse，在一个串行DeliveryGate中依次调用didReceive、didLoad、urlProtocolDidFinishLoading；transport故障调用didFailWithError且只有一次终态。
4. stopLoading标记已停止、取消Task和底层操作；后续任何Data/终态回调需经过同一gate复核，确保停止后不再回调client。不能只cancel Task而忽略已经排队的回调。
5. 事务提交后stopLoading不反向删除事实；下一次查询/同key重试读取权威结果。超时由Registry主动cancel并归一Timeout，不把所有NSURLErrorCancelled都当用户取消。

URLProtocol方法无法直接await，异步Task与client回调需通过受控生命周期适配；不能保存一个强闭包循环`protocol→task→protocol`，完成/取消都释放。AppContainer注入专用DemoController时用明确的受控provider/request context，避免全局可变Mock配置污染测试；生产real session不装载DemoURLProtocol。

## 7. Demo数据库、存储与事务

### 7.1 SQLite方案

使用GRDB管理SQLite，Demo DB与PublicCache DB分开；SPM pin与iOS/Swift目标在G0-I核实。GRDB提供SQLite应用数据访问基础，具体并发与迁移API按锁定版本实现，不在设计稿中虚构已调用的函数签名。[GRDB官方仓库](https://github.com/groue/GRDB.swift)

表结构和业务唯一约束与Android逻辑一致：demo_users、assets/accounts/transactions、checkins唯一(user,businessDate)、energy_ledger唯一source、member_balances、benefits、claims唯一(user,benefit)、messages/reads、personal_items、idempotency唯一(user,api,key)、demo_meta。金额用Int64或规范整数字符串，服务层用Decimal/整数精确计算，不用Double累计金融金额。

DemoStateStore actor负责组织请求，但数据库事务由单writer连接执行，**事务闭包内不await网络/Task.sleep**；等待延迟在事务前完成。签到记录/余额/账本/幂等结果/revision同事务；领取条件减库存与claim插入同事务；消息已读条件更新。唯一冲突读取既有成功结果，其他错误回滚；deadline/epoch在写入前校验，提交临界区不被UI取消破坏原子性。

SQLite外键启用，支持版本迁移并记录seedVersion/schemaVersion；WAL文件和主库一起受文件保护/备份策略管理。损坏时显示故障及开发重置说明，不自动删除注册账号。只读产品/资讯/活动可读JSON模板，不能为跨端一致性复制一套不同seed。

### 7.2 存储分类

| 数据 | 实现/路径 | 失败和隔离 |
| --- | --- | --- |
| assetVisibility偏好 | PreferencesStore actor，Application Support内版本化JSON文件，写临时文件后原子替换 | typed Key，用户ID由Native推导；失败返回错误并保留旧文件；普通UserDefaults仅限无可靠写回要求的非敏感系统选项 |
| 公开缓存 | 独立SQLite cache文件，meta/body/hash/TTL300秒，初始20MiB上限 | 只允许公开API，损坏可清缓存，不动Demo DB |
| Demo业务 | Application Support/Demo命名空间SQLite | 跨进程保留，退出不删，重置显式确认 |
| Token | SessionCoordinator内存 | 不进Keychain/文件/SceneStorage；进程终止失效 |
| 验证材料密钥 | Keychain ThisDeviceOnly、WhenUnlocked | 无iCloud同步，错误可见；不因安全存储存在而持久登录 |

密码验证与Android相同PBKDF2-HMAC-SHA256、salt16字节、32字节输出、初始600,000次、参数版本化，用CommonCrypto或G0-I验证过的等效实现；固定跨端测试向量验证相同输入，随机salt避免相同密码相同verifier。计算限制并发并脱离MainActor；verifier可用Keychain管理的设备AES-GCM密钥封装后入库，salt/KDF参数可明文。密钥失效不能降级存密码，提示显式重建演示账号/数据。

Demo DB/密钥/个人偏好及其WAL、日志按敏感程度配置NSFileProtectionComplete与备份排除；本期无后台业务，受保护数据不可用时返回暂不可用，不无限重试。Keychain选项语义参考[WhenUnlockedThisDeviceOnly](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)，实际物理设备锁屏场景在未来集成测试核查。

### 7.3 共享JSON、场景与重置

构建脚本将根mock/contracts复制bundle，输入输出文件清单让Xcode增量构建可追踪；manifest/hash与Android一致，脚本失败阻断打包，不使用上次遗留资源。Bundle/Swift Package资源通过显式ResourceProvider获取，测试注入独立目录。

Clock/RandomSource/ScenarioController与Android语义一致：真实时间默认上海业务日，测试冻结/推进；next-call故障原子消费；参数排序、bodyHash、错误优先级由黄金向量规定。状态handler输出schema一致，不要求内部SQLite字节或随机ID相同；测试比较归一化结果及不变量。

开发菜单重置二次确认后停止新请求、epoch变化/取消旧任务、事务替换seed、清缓存/偏好/checkpoint并发demoStateReset；失败不混用版本。普通logout不删除Demo事实。场景切换清查询缓存，避免成功缓存遮住故障注入。

## 8. 日志、稳定性与性能

TelemetryKit提供OSLog安全结构化输出和有界本地文件sink，采用总体字段/队列/保留规则。隐私字段默认不输出，Native最终白名单过滤；不能因为OSLog支持privacy就把原始Token/body写入文件sink。emitBatch解析、文件滚动在后台串行执行，溢出丢低级事件并记录droppedCount，事件失败不阻断领取或认证。

| 风险 | 措施 | 未来验证 |
| --- | --- | --- |
| 主线程停顿 | UI/@MainActor仅轻量操作，KDF/DB/JSON/资源读取后台化 | Instruments Time Profiler，单主线程长任务定位 |
| 启动慢 | Shell先帧，RN按需启动、资源异步校验，非活跃Tab lazy | signpost分段，30次冷启动P95，总体预算同Android口径 |
| 动画/手势卡顿 | 单Surface、RN native-stack、图片解码尺寸、减少跨层事件 | Animation Hitches/Core Animation，手势完成/取消脚本 |
| 内存泄漏 | ObjC++/Swift facade/task引用断环，容器正确移除，订阅按runtime清理 | Allocations/Leaks/Memory Graph；20轮回收后增量目标≤20MiB |
| URLProtocol重复回调 | DeliveryGate统一检查终态/停止标记 | start/stop/timeout并发压力测试，无finish与fail双终态 |
| Actor重入竞态 | await后校验epoch、写事务无await、幂等唯一约束 | 并发401/退出再登/领取竞争向量 |
| RN加载失败 | Native兜底、一次重建、Bundle/hash/能力协商 | release无Metro启动、错主版本/损坏资源注入 |
| 锁屏保护数据不可用 | 暂停/错误反馈，不降级明文或重置数据库 | 真机锁屏/解锁、文件保护/Keychain错误 |

iOS内存测量记录physical footprint、堆/纹理及OS版本，不把Android PSS绝对值直接横比；关注同平台趋势与泄漏。Apple提供内存分析工作流，G0-I确定可用Instruments及Xcode版本。[Apple内存分析](https://developer.apple.com/documentation/xcode/gathering-information-about-memory-use)

未来试点可经单独评审接MetricKit/崩溃监控，当前没有远端监控服务，不伪造crash-free百分比。dSYM、JS source map与buildId同档，发布产物可符号化；RN ErrorBoundary不兜住EXC_BAD_ACCESS/系统终止等Native崩溃。

## 9. 构建、配置与发布

建立Demo-Debug/Demo-Release配置及共享Scheme，Demo-Debug可用Metro，Demo-Release必须RN脚本生成main.jsbundle/Hermes产物和本地资源，离线运行。真实配置未实现时不建立假Real可运行Scheme。AppDelegate运行环境由不可变BuildConfiguration注入，测试场景不靠修改全局UserDefaults让release意外启用。

RN及生成桥接留CocoaPods，纯Swift业务/GRDB使用SPM；所有依赖精确锁定，检查重复SQLite/RN symbol及use_frameworks链接组合，不能将动态/静态链接冲突靠随意切USE_FRAMEWORKS解决。验证模拟器arm64与真机arm64，禁止无限制EXCLUDED_ARCHS规避当前平台错误。

未来Archive验证Bundle/Codegen符号/资源清单、签名/provisioning、Info.plist隐私与备份、目标系统、dSYM。Debug与演示分发签名分离；本期无真实发行需求，不声明已可App Store发布。不引入远程热更新，JS/Native/contracts/fixtureVersion随App版本原子交付。

本期维持单窗口应用；未来如开启多Scene，需独立ADR说明一个runtime在多Scene的Surface、认证presentation和全局会话协调，不能默认将当前单Shell方案复制到多个窗口。

## 10. 测试与跨端一致性

| 测试层 | 内容 | 预期证据 |
| --- | --- | --- |
| Swift包单测 | Money/比率、Clock业务日、参数规范化、schema/DTO、KDF固定向量 | 测试报告与contracts版本 |
| actor/状态 | 注册完整会话、并发认证、401/旧epoch、logout、await重入、一次终态 | 可控调度器与重复压力结果 |
| URLProtocol | 正常随机延迟、固定种子、未知API拒绝、start/stop/超时/坏包、无socket | URLSession专用配置集成测试 |
| Demo DB | 签到周期/断签/3/7日、库存竞争、不同幂等键重复、冲突hash、迁移/回滚 | 临时DB、固定Clock、黄金向量 |
| 存储/安全 | 原子写失败、用户scope、Keychain/文件保护失败、备份排除 | 包测试+真机锁屏验证 |
| bridge | Codegen注册、五模块能力/hash、序列化/事件/取消/模块销毁 | Native/RN集成报告 |
| XCUITest | 四Tab/子页、认证返回、领取卡券、消息角标、侧滑取消、游客身份、低内存恢复 | 录像/截图/日志/设备与buildId |
| 生命周期 | 前后台、runtime重建、进程kill后游客、同账号事实保留 | 与Android同需求用例的结果 |
| release/性能 | 无Metro离线、Bundle、资源hash、Instruments预算、dSYM符号化 | 可安装包及性能/产物报告 |

黄金向量共享输入、Clock、场景、预期结果和业务不变量：字段schema、错误码、金额、状态/计数相同；traceId/随机operationId按占位归一，不要求两端字节级相同。Android使用Room、iOS使用SQLite访问层的不同内部实现，不应导致奖励周期、库存、403/401语义或缓存TTL不同。

未来执行前先`xcodebuild -list`与workspace/scheme清单核实，构建/测试命令使用实际Scheme和已安装模拟器目标；Pods通过项目Bundler执行，Swift包测试依赖目标平台选择xcodebuild或适用的swift test。设计期不写死不存在的Scheme或设备UDID，不在本期执行这些操作。

## 11. iOS分阶段任务（全部延期）

| 阶段/任务 | 依赖 | 完成条件 |
| --- | --- | --- |
| I0/IOS-00 兼容性PoC | 本期契约稳定+未来实施排期 | Xcode/Pods/SPM/RN0.86/screens/最低系统矩阵，单容器与ObjC++→Swift调用证据 |
| I1/IOS-01 基础模块/契约 | IOS00 | 模块边界、五模块Codegen、bootstrap/事件/取消、共享schema hash |
| I1/IOS-02 Native认证/会话 | IOS01 | SwiftUI MVVM、完整自动登录、取消/401/退出，Token不落盘 |
| I2/IOS-03 Network/Storage/Demo | IOS01/02 | URLProtocol、37API/JSON、GRDB事务、幂等/Clock/迁移、原子偏好 |
| I3/IOS-04 Shell/RN导航联调 | IOS02/03+共享RN | 四Tab、子栈/底栏/手势/安全区、单Surface生命周期 |
| I4/IOS-05 对等与故障验收 | IOS04 | AT-01～18的平台适用项、黄金向量、进程/锁屏/内存用例 |
| I5/IOS-06 优化演示包 | IOS05 | Demo-Release离线、性能/安全/Archive报告和已知限制 |

每任务未来登记负责人/PR/buildId/契约hash/设备/结果链接；本期只审设计清单，不能给表内任务填写“完成”。排期须按G0-I实际兼容性和人力估算，不以Android已完成推定iOS工作量为零。

## 12. 扩展与限制

当前仅RN factory模板，没有需迁移的本项目iOS旧用户库；首次未来接入创建版本化Demo数据。若实施前已有新代码/数据，重新核查迁移，不照本结论清用户目录。向后回滚检查DB schema，优先前向修复；卸载/重装是显式演示重置路径，不能伪装为无损升级。

未来真实网络使用不含Demo协议类的独立session和服务配置，新增真实鉴权/服务端幂等/风控要求；不把本地SQLite事务等同真实后端一致性。iPad/横屏/多窗口、持久登录、生物认证和远程配置需补需求及ADR；本期无这些隐含范围。

参考核查2026-10-17：RN、Apple URLSession/并发/安全存储、GRDB来源见正文。Apple部分页面依赖JS，具体API行为还应在G0-I以本机SDK头文件、官方文档和最小复现确认；本设计不据此声称编译或平台测试已通过。

## 13. V1.1 对等优化（2026-10-18，实施仍延期）

### 13.1 资源打包

共享mock/经验证/复制任务打入应用bundle的mock资源根，manifest/schema/37API/seed/hash可查，优化演示包离线读取，不依赖Metro/开发机文件；Bundle资源只读、变更写Demo DB。未来iOS验收Archive/安装资源hash与Android共享manifest一致，不能将源目录存在当打包成功。本期仅修订设计，未改Xcode资源配置。

### 13.2 两条网络通路隔离

本项目Native NetworkKit的ephemeral URLSession只服务NativeNetwork和Native认证，DemoURLProtocol只注册此session的protocolClasses。RN Axios/标准网络API走RN自己的系统网络通路，不经过自定义NativeModule，不能为满足Native Mock而全局URLProtocol.registerClass，亦不能把RCTSetCustomNSURLSessionConfigurationProvider配置为全局拦截所有RN HTTP。

Native Mock仍只覆盖本期37API并拒绝未知路径；RN自有HTTP按EndpointRegistry明确Host/transport/authScope，当前只允许已定义独立公开服务，未配置服务返回明确配置错误。银行Token仍仅Native，RN公共服务401不调用银行SessionCoordinator失效。TLS/ATS、Cookie、缓存、取消等两条路径分别验证；未来RN底层定制须明确影响范围，不能同时污染Metro和资源请求。

### 13.3 日志与单instance

Swift Telemetry facade接收RN独立Logger的核心/bridge事件，保留eventId/layer/transport/span父子关系、应用最终脱敏，按有界eventId窗口去重；RN开发console并不依赖Native日志完成。URLProtocol执行span与RN bridge调用span可关联，但各自独立计时，时钟不跨层直接相减；已完成事务事实不因RN日志发送失败回滚。

进程只有一个RNRuntimeOwner/factory对应的活动RN instance。Shell/认证/业务子页不得再new运行时；可恢复故障先销毁旧实例及其Facade/tasks/listeners再重建，活动实例计数始终≤1。iOS导航/前后台/低内存/交互返回不创建新的RN实例；RN根部也只有一个Redux Store与RTK Query ApiService。

### 13.4 未来任务更新

IOS00新增单instance计数、RN HTTP与专用URLSession隔离及Axios/RTK共享代码兼容PoC；IOS01新增Logger关联协议，IOS03新增Bundle Mock资产与去重实现；IOS05/06新增安装包离线/日志贯通/双通路分别验证。RN-P0目录/库封装由共享RN工程先做，未来iOS接入只补平台adapter，不复制feature和状态库实现。所有iOS编码/构建/运行任务继续标延期。
