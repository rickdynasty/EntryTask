# Android 技术方案

版本：1.2 · 日期：2026-10-19 · 状态：当前实现核查 + 后续目标设计

输入：[总体方案](EtryTask%20项目设计方案.md)、[RN方案](前端RN技术方案.md)、[需求](../requirements/01-prototype-requirements.md)

本版以源码为当前基线，保留尚未完成的模块化和生产能力设计；“当前实现”不代表设备验收通过。最新产品要求为**Native 启动提前认证，RN 页面默认需要登录，仅帮助中心明确公开**，取代 V1.1 的游客浏览默认，决策见 [登录门禁 ADR](../adr/011-required-native-authentication.md)。

### 当前落地状态

| 领域 | 当前源码事实 | 后续边界/未完成项 |
| --- | --- | --- |
| 工程模块 | `app`、`biz_user`、`lib_base`、`lib_net`、`lib_storage`、`demo_engine` | 下文 contract/ui/host/bridge/user_api 是渐进拆分目标，尚未全部创建 |
| RN 宿主 | MainApplication 单 ReactHost；MainActivity 单 HomeFragment/ReactFragment，Native 四 Tab | 完整返回 ACK、runtime 恢复、预测返回与性能验收未完成 |
| 认证门禁 | MainActivity 提前检查 + Activity Result；RN 默认页面门禁；NativeSession/Host Bridge 校准会话 | 完整 SessionCoordinator/可恢复认证任务尚未完成；Native-only 文本页是开发分支，不作为 RN 登录业务验收路径 |
| Bridge | 五模块实现位于 `app/src/main/java/com/entrytask/bridge/`，继承 `specs/` 生成接口 | 抽 lib_bridge/lib_rn_host 需迁移依赖，不假定现已独立成库 |
| 应用信息代理 | `AppGlobals` + `AppContextInitializer` + Startup Provider；已替代旧稿 AppProxy 空类 | 未实现不可变 AppConfig、重复初始化冲突检查；不再新增并行 AppProxy 单例 |
| 网络/Mock | Native 认证使用 NetManager；RN 业务使用 MainApplication.prototypeNetwork + demo_engine；34 个 RN API | 不等于 37 API 全场景验收；两条现有 Native client 尚未统一为目标容器 |
| 下载 | lib_net/download 有任务管理、三并发信号量、Range 写文件和 StateFlow 状态 | 仍为进程内初版；取消、任务去重、Range 校验和完整性问题见 §7.1，未开放 RN Bridge |
| 资源/构建 | contracts/mock 通过每变体 generated-assets 任务打入 APK；当前为 debug/release | 未配置 demo flavor；release 演示签名、R8 未启用，不能称生产包 |

源码与运行入口见 [Android README](../../android/README.md)，共享业务实现见 [RN README](../../src/README.md)。

## 1. Android职责与现状迁移

Android实现Native Shell、四根Tab、单RN容器、Native认证、会话、五个TurboModule、网络/缓存/存储/日志和本地Demo引擎。四域及其子页不再新增Kotlin业务UI；MVVM用于Native认证和Shell状态，RN业务MVVM见RN方案。

| 现有实现 | 迁移决策 | 完成证据 |
| --- | --- | --- |
| MainActivity与RN容器 | 保留 singleTask 稳定宿主，Native 提前认证 + RN 页面门禁 | 未登录无私有业务/底栏，帮助中心公开，取消不循环弹窗 |
| BaseFragment生成ViewId及每次replace RN片段 | 专用RnHostFragment稳定资源ID/tag，恢复已有Fragment；删除万能混合基类分支 | Activity重建/进程重启不重复add |
| UserViewModel回调、公开MutableStateFlow、注册只置Boolean | Login/Register分VM，统一AuthRepository和SessionCoordinator | 完整会话、取消/错误/重入状态测试 |
| UserAuthManager内存会话 | 当前原子快照，后续统一为SessionCoordinator；Token仅Native | 进程重启重新认证，退出无残留，旧epoch不得回退新会话 |
| MockInterceptor内联JSON/存明文密码/未知放行 | demo_engine独立拦截器、JSON manifest、Room事务 | 37API、未知API拒绝、离线演示、无明文 |
| NetManager可变初始化/吞TLS错误 | AppContainer创建不可变NetworkClient | 初始化失败明确，demo不尝试公网 |
| StorageManager包stroage、Any静默类型 | 注册Key类型化DataStore/受控存储，迁包不改安装身份 | 迁移测试/失败可见 |
| app只依biz_user，借其api传递库 | app显式依赖使用模块，implementation默认 | 依赖白名单报告 |

旧`android/docs`三文件保留为历史初稿，本方案和总体方案取代其中与RN业务路径冲突的部分；原有通用规则在不冲突时继续采用，不实施旧稿的Native四域页面任务。

## 2. Gradle模块与依赖

以下为**目标拆分**，当前实际六模块见上表；按职责和变更需要迁移，不批量创建空模块。`lib_base` 当前为 Android library，不能将 AppGlobals/Startup、MLog 或 Android UI 描述成纯 Kotlin 代码。

| 模块 | 职责/公开面 | 依赖边界 |
| --- | --- | --- |
| app | Application/AppContainer、MainActivity、导航/能力组装、变体配置 | 显式依赖宿主/桥接/认证/技术实现，禁止业务DAO/规则 |
| lib_base | 轻量Android基础设施、AppGlobals/Startup、基础结果/调度接口；现有UI渐进迁出 | 不依RN、业务、网络/存储实现；纯领域契约不得依赖其Android类型 |
| lib_contract | 纯Kotlin能力Port、bridge DTO/schema版本、API descriptor、Host/Session安全契约 | 可依coroutines；不依Android lib_base、业务实现/RN/Retrofit/Room；必要的纯值类型随契约归属 |
| lib_ui | Native基础主题、薄BaseActivity、底栏、错误/Loading基础组件 | 依lib_base/AndroidX；不依RN和业务 |
| lib_net | OkHttpClientFactory、请求注册/取消/超时、传输错误、公开缓存协调钩子 | 实现lib_contract的网络Port；不依lib_storage、demo或biz |
| lib_storage | Preferences DataStore、CacheStore实现、Key注册与安全材料存储 | 实现lib_contract的存储接口；不依lib_net/biz/RN |
| biz_user_api | 会话/认证入口、只读用户模型 | 纯Kotlin，依无平台契约/coroutines，不依Android基础库，不暴露Token给桥接 |
| biz_user | Compose认证UI/VM、AuthRepository、SessionCoordinator | 依user_api/contract/base/ui；IO通过Port注入 |
| lib_rn_host | ReactHost/Surface生命周期、RnHostFragment、Host事件/恢复协作 | 依RN、contract/base/ui；不依biz实现 |
| lib_bridge | 五个Codegen模块、序列化校验、Promise/监听生命周期 | 依RN、contract/base；不能直接读UserAuthManager或Activity全局单例 |
| demo_engine | JSON路由/场景/Clock、账号/会员/消息handler、Room Demo DB | 依contract/base/Room；网络拦截器适配可依OkHttp；不依RN/app/biz实现 |

Room用于**有事务需求的Demo数据库**，不是为了让RN每个只读产品列表都造一份业务Room仓库。公开缓存可使用lib_storage的独立Room cache库；二者数据库文件和清理策略分开。通用lib_net通过注入ResponseCache接口使用缓存，不反向依赖lib_storage实现。

目标依赖：`app → {biz_user,lib_rn_host,lib_bridge,lib_net,lib_storage,demo_engine,lib_ui}`；`biz_user → biz_user_api/lib_contract/lib_ui`；`lib_contract/biz_user_api` 保持无平台依赖。Android lib_base 不进入纯领域契约依赖图；当前 lib_net → lib_storage 等偏差保留为渐进迁移项。同层技术库不互相找单例。demo_engine是独立演示业务实现，不能被正式real依赖图加载。

模块内部按entry/presentation/domain/data/di分包，类默认internal；API模型与DTO分离。app负责接口到实现装配，初期手动构造注入和ViewModel Factory；团队规模与依赖复杂度增长再用ADR迁Hilt，不在同作用域并存两套容器。

### 2.1 AppProxy能力的当前实现：AppGlobals + Startup

当前类为 [AppGlobals.kt](../../android/lib_base/src/main/java/com/entry/lib/base/AppGlobals.kt) 和 [AppContextInitializer.kt](../../android/lib_base/src/main/java/com/entry/lib/base/AppContextInitializer.kt)。lib_base Manifest 将 Initializer 注册到 `${applicationId}.androidx-startup` Provider；Initializer 只保存 applicationContext，公开只读 `context`、`applicationId`、`isDebug`，其中 debug 标志来自宿主 ApplicationInfo，不使用库 BuildConfig 推断宿主。

这是应用信息代理，不承担 Session、网络、数据库、RN Host 或业务路由。下层库可以使用必要的应用信息；业务服务通过构造注入，禁止把 AppGlobals 扩为任意服务定位器。当前未初始化访问会明确失败；`initialize` 尚无线程可见性、重复冲突检查和独立测试，这些仍需补齐，不能宣称幂等初始化已验收。

后续约束：在代理内部再次归一化为 applicationContext；同一个 Application 的重复初始化幂等，不同 Application/配置直接报错；只读发布需具备线程可见性。若新增环境、版本或开关，使用不可变 AppInfo/AppConfig，由 app 显式注入，不存凭证、Activity 或可变业务对象，也不重新引入第二个 AppProxy。

### 2.2 启动依赖与性能

Startup 用于表达轻量组件依赖，Initializer 在需要先初始化的组件列表中声明依赖；它不是后台任务框架。保持自动初始化只注入 Context，日志/存储/网络/RN 的组装仍由 MainApplication 管理，后续通过显式 AppContainer 拆分，禁止在 Initializer 同步加载 fixture、迁移数据库、建立连接或预热 RN。[Android Startup 官方说明](https://developer.android.com/topic/libraries/app-startup)

当前顺序为 `Startup Context → MainApplication/MLog → StorageManager/旧键清理 → NetManager.initMock → loadReactNative → MainActivity/VM 提前读会话 → RESUMED 展示认证 → 认证返回后按需 ReactHost/Surface → Bridge bootstrap/Session 校准 → 页面门禁 → 业务查询`。Startup 早于 MLog 初始化的异常需有安全平台日志，不能依赖尚未初始化的日志组件。合并 Manifest 必须核对 Provider authority 和 Initializer meta-data；测试覆盖普通启动、未初始化、重复初始化、宿主 debug 属性，以及组件测试手动初始化。

## 3. 构建与环境基线

现有Kotlin2.1.20、SDK36/min24、Gradle9.3.1、JVM目标17、NDK27.1.12297006来自文件，当前未解析AGP最终版本；先执行G0环境清单与debug/release构建，再选择兼容版本。JDK按解析AGP/RN要求锁定，不把“JVM target17”误写成“运行Gradle一定兼容JDK17”。

建立`gradle/libs.versions.toml`及渐进convention plugin统一SDK/JVM/Lifecycle/Compose；保留RN settings plugin、rootproject插件、autolinking和Hermes配置。跨Groovy/KTS可共存，不为统一DSL重写所有文件。compile/target/NDK调整必须单独验证RN原生库。

当前只有 debug/release。未来需要隔离真实服务时再新增 demo flavor：demoDebug 的 `react.debuggableVariants` 显式配置，demoRelease 内置 Bundle/Hermes/本地资源；real 缺配置直接构建失败。R8 启用后检查 Codegen 注册、反射/序列化、Room 和资源保留。现在继续使用 `assembleDebug`/`assembleRelease`，不提前使用不存在的 demo 任务。

构建缓存/并行配置先收集任务耗时再开，release按arm64真机与目标模拟器ABI分别验证；保留所需RN ABI，不以删架构掩盖原生链接错误。CI记录JDK、AGP、Gradle、Kotlin、NDK、SDK、Node、npm与依赖锁hash。

## 4. Shell MVVM与单RN宿主

2026-10-21 调整为分步实施：[首页MVVM与Compose分步方案](Android首页Compose与MVVM改造方案.md)。当前完成 XML + MVVM 与混合宿主适配，后续单独转换 Compose；本节只描述当前实现。

### 4.1 生命周期所有权与状态

MainApplication 懒加载 ShellGraph，构造 DefaultShellRepository、UserAuthSessionAdapter、ForegroundHostConnection 和 ShellBridgeAdapter；唯一 ReactHost 仍使用 getDefaultReactHost。Activity 作用域 MainViewModel 与 SavedStateHandle 负责只读 MainUiState，MainActivity 在 STARTED 区间收集并交给 MainShellView 渲染。VM 不依赖 XML/Compose、Activity、Fragment、JSON 或凭证。

Repository 是 tabId/depth/revision 的唯一事实源，UserAuthManager 是认证权威。真实变化发布 revision，恢复和显式同步允许强制发布，旧 epoch 不回退。Bridge 与 VM 使用同一来源，移除旧 ShellCoordinator。

MainActivity.setContentView 后调用 MainViewModel.onHostCreated 检查会话；首次游客先启动认证，不新建 HomeFragment。认证成功、取消或启动失败后，在 RESUMED 且 FragmentManager 未保存状态时按 entrytask-root-hub 查找 HomeFragment，缺失才添加到稳定 R.id.fragmentContainer；复用原 XML 版本的恢复约定。BaseFragment 保留 entrytask-rn-root / R.id.rn_content 的内层 ReactFragment。Tab/认证不替换片段，Activity 重建可重建 Surface，但不新建 Application 的 ReactHost。

### 4.2 XML渲染、导航与布局

MainShellView 初始化现有 BottomTabBar 并只上报 Tab 点击；render(MainUiState) 控制选中外观和整组底栏/分割线显隐。控件选中缓存只服务绘制，不在点击时独立改变状态。游客和子页隐藏底栏，XML 容器始终存在；RN 首次挂载延迟到认证返回/失败，之后页面门禁可继续经 Bridge 请求登录。

ViewCompat Insets 监听在根预留系统底部导航区，在 RN 容器预留顶部/侧边 systemBars/displayCutout；RN SafeAreaView 在裁剪后的视口中计算剩余量，不重复硬编码系统高度。HostFragmentHandle 仅在 Activity/View 作用域保留当前宿主并在销毁清理。

后续 Compose 任务只替换 XML/View 渲染及必要的宿主互操作，不改 VM/Repository/Bridge 协议，必须独立验收外层 tag 迁移和 Insets，不能保留两个并行 Fragment 管理者。

### 4.3 返回协议

保留单个 OnBackPressedDispatcher：先收键盘，再调用当前 ReactFragment.onBackPressed；RN 未消费后通过 DefaultHardwareBackBtnHandler 回到 MainActivity.invokeDefaultOnBackPressed。RootBackPolicy 使用 SystemClock.elapsedRealtime：子页深度冲突只重同步，其他根 Tab 回首页，首页沿用原有 500ms 双击退出窗口。RN AppNavigator 在收到不一致快照时按实际栈深度纠正，防止陈旧 depth 导致卡住或误退。

当前沿用 RN 官方返回路径，未新增 backRequested/ACK 协议或确认超时机制；不声称覆盖 RN 完全无响应时的恢复及预测返回全部手势场景。

### 4.4 Bridge 与认证展示

HostModule 使用模块作用域协程和容量64的单消费者命令队列：Default 线程解析请求和读取 runtime contract，Main.immediate 按受理顺序调用 ShellBridgeAdapter；队列满返回 HOST_COMMAND_BUSY，不无界缓存；StateFlow 订阅独立于 UI 订阅，invalidate 时取消。DTO 边界验证长度、类型、Tab 和 depth 范围，传输协议维持原样，不修改 specs/。

ForegroundHostConnection 在 MainActivity RESUMED 时绑定 VM 的认证动作，在 onPause 解除；认证展示中仅保留流程编号，重复请求直接合并；其他无前台端点的认证请求返回 HOST_NOT_READY，非 UI 命令不需要 Activity。VM 保存 Requested/InFlight/Failed/Canceled 展示状态，Activity 在 RESUMED 收集并确认请求后通过 Activity Result 启动 AuthActivity。ACK 仅表示受理。认证成功先由 Repository 建立 Native 会话，再 setResult/finish；结果回调及 onResume 重读会话并发布。

### 4.5 当前边界

MainViewModel 保存非敏感 Tab、首次检查标记及认证 UI 阶段/序号；不保存凭证或登录事实。配置/任务恢复通过 SavedStateHandle 与 Activity Result 协调在途认证；没有 Native 会话时仍阻断私有页面。singleTask 重入通过 onNewIntent → onNewEntry 建立新请求，旧结果按编号隔离；系统清除产生的空data旧取消结果先单独消费，不撤销新请求，完整进程恢复需专项验收。不承诺进程永久保活。认证表单 VM 的 Channel 效果与安全存储遗留问题仍按第5节专项治理；本次未扩展 iOS 或网络下载协议。

## 5. Native认证MVVM与会话

### 5.1 当前登录门禁与职责

首次路径为 `MainActivity.onCreate → MainViewModel.onHostCreated → NativeSessionPort → Requested → RESUMED/Activity Result → AuthActivity`，不依赖 RN。认证返回/启动失败后挂载 RN，经 `bootstrap → NativeSession.getSnapshot` 校准会话。受保护 RN 子页走 `DetailRoute → AuthenticationGate → HostPort → NativeHost.command(authenticate) → 同一 Native 认证协调`。Native 是会话权威，RN 只消费安全布尔值和 epoch；查询缓存、SharedPreferences 或 JS 变量都不能独立证明已登录。Host bootstrap 和独立 Session 请求可能乱序，因此 RN 同时按 Shell revision 与 sessionEpoch 拒绝回退；Session 响应只更新会话字段，不覆盖新 Tab、导航深度或 revision。

| 能力 | 当前实现与结果语义 |
| --- | --- |
| 查询登录态 | NativeSession.getSnapshot；UserAuthManager 在同一锁内读取 authenticated/epoch，不返回账号或凭证 |
| 展示认证页 | Native 首次检查或 RN authenticate 命令汇合到同一 VM；仅在主线程且 MainActivity RESUMED 时启动，展示期间重复请求合并，启动异常可重试 |
| 命令 ACK | 成功只表示已受理/无需再次展示，**不代表用户认证成功**；HOST_NOT_READY、INVALID_HOST_COMMAND、HOST_COMMAND_FAILED 分别进入安全诊断 |
| 认证结果 | 登录/注册 Repository 建立完整会话后 AuthActivity setResult(RESULT_OK) 并 finish 自身；MainActivity 的 Activity Result 回调及 onResume 重读会话并发布 Shell 快照，RN 据此放行 |
| 未登录 UI | 导航容器允许存在；RootRoute/DetailRoute 在创建受保护 VM/资源订阅前拦截，底栏隐藏，门禁提供登录/帮助入口，仅帮助中心公开 |
| 取消/失败 | 表单失败留在认证页；登录根页先收键盘，再2秒双击退出任务，注册页返回登录；系统/程序性取消仍留门禁，不循环弹认证 |
| 退出登录 | Native 清会话/递增 epoch，根 Tab 回首页、深度归零、底栏隐藏；RN 清旧查询及私有路由，受保护页请求认证一次；当前公开帮助页可继续显示。取消后等待手动重试 |

RN 门禁是页面访问策略，不替代 Native API 鉴权；受保护 API 仍在 Network/Storage Bridge 检查 Native 会话及发起 epoch。公开 fixture/API 的 `requiresAuth=false` 只表示数据接口本身公开，不豁免 RN 默认页面门禁；只有 routeAccess.ts 明确登记的 helpCenter 页面公开。

登录和注册按各自 NavBackStackEntry 创建独立 UserViewModel 实例，只复用状态类型、Factory 与 Repository，不共用输入或事件流。进入注册页为空表单；返回登录只保留登录页自己的账号，密码在离页、宿主销毁和成功提交时清空，不放入 SavedStateHandle/Bundle。离页取消本页请求，取消不作为失败提示；请求版本防止旧 finally 覆盖新请求的 loading。AuthActivity 不再持有表单级 VM。

本次表单隔离回归：4个新增VM单元测试覆盖失败后隔离、离页清理/取消、重复提交及旧请求终态；1个API37设备用例验证真实切页与重建，0失败/跳过。失败响应由Fake服务提供；设备仅输入合成文本、不提交认证、不修改账号。Debug构建和Lint通过。设备任务的本机Maven TLS故障通过临时官方依赖缓存处理，工程仓库配置不变。

### 5.2 singleTask与认证页生命周期

MainActivity 保持 singleTask；正常栈为 `[MainActivity] → [MainActivity, AuthActivity] → [MainActivity]`。从首页使用 Activity Context 的普通 Intent 启动认证页，不添加 NEW_TASK/CLEAR_TOP，不在成功回调重新启动 MainActivity，也不 finish 首页。这样认证期间首页暂停/停止，成功后恢复已有首页和 RN Host。[Android 任务与返回栈说明](https://developer.android.com/guide/components/activities/tasks-and-back-stack)

singleTask 不能保证对象在内存中永远存活；系统回收/进程终止仍需恢复。重新向 singleTask 首页发送 Intent 还可能移除其上层页面，因此重新点桌面图标、外部 Intent、认证页旋转与进程重启必须单独验收。当前 session 仅进程内存在：后台恢复仍有效，进程终止后必须重新认证；“保活”指业务流程不主动销毁首页，不采用静态 Activity 引用或伪后台保活。

当前认证展示状态由 MainViewModel 管理，经 Activity Result + RESUMED 生命周期协调；不作为登录事实，不缓存 Activity/Promise，也不在每次 onResume 强制重开认证页。跨进程认证任务与表单结果的完整恢复仍待专项验证；Native-only 文本调试分支同样会先做 Native 启动检查，但不能代替 RN 页面门禁验收。

### 5.3 核心日志与验收

RN 记录 `session.snapshot.checked → authentication.required/requested → command.started/completed/failed → session.epoch.changed`；Native 记录 `session.snapshot.completed/failed`、Host 命令受理/失败、认证页创建/成功关闭及 MainActivity 恢复。命令成功与认证成功分开定位，关联字段仅沿用日志白名单。Native 启动/返回日志带认证流程编号，AuthActivity 结果原样回传以隔离迟到结果；RN 命令日志仍沿用现有 Bridge 关联机制，本轮不新增跨端 authFlowId 字段，不能宣称全链路关联已完成。

必须覆盖冷启动未登录、进程内已有会话、注册成功、表单失败、返回取消/重试、连续点击、命令启动异常、后台时拒绝展示、退出后重新登录、迟到 Session/Host 事件、singleTask 重入、Activity 恢复及进程重启。验收分别记录源码、Jest、Native 构建和设备证据；构建通过不能替代认证往返设备测试。

### 5.4 后续会话领域演进

```text
LoginRoute/RegisterRoute
  → LoginViewModel/RegisterViewModel
  → AuthUseCase（认证结果与会话提交编排）
  → AuthRepository
  → NetworkPort(A01/A02) → Native Network/Demo
  → SessionCoordinator.commit(完整NativeAuthSession)
```

NativeAuthSession凭证实现留在biz_user内部；对外SessionSnapshot无Token。SessionCoordinator串行化guest/authenticating/authenticated/invalidating状态转换；epoch每次有效转换递增，epoch比较与会话清理在同一协调器临界区。Flow只读，UI用collectAsStateWithLifecycle，Fragment用repeatOnLifecycle。关键结果为状态，非replay=0事件保证。

VM字段私有MutableStateFlow，表单uiState只读；每页独立密码状态，离开认证流程/提交完成后清密码，不进SavedStateHandle/Bundle/日志。空输入本地字段错误；trim账号1～32，密码原样1～64；重复提交single-flight；CancellationException向上传播，不吞取消。提交失败留页可重试，返回取消结果给SessionPort，不重进MainActivity触发强制认证。

注册成功响应若缺用户或可用凭证视为协议错误，不建立假会话；登录/注册都调用同一commit流程。A01/A02供Native内部调用，不被NativeNetwork JS白名单暴露。多个RN保护入口请求认证时同一个AuthActivity，pendingIntent按flowId独立登记，目标仅安全route，认证结束精确返回。

logout/current-epoch401都通过Coordinator，立即清会话、旧请求/缓存/RN个人投影；A03 best-effort失败也清本地。旧epoch的401不能退出新用户。RN Runtime重建从Native快照恢复登录，进程终止不从SP恢复；旧UserAuthManager迁为短期只读委托后删除Boolean/setLoginSuccess公开入口，不能双写两套状态。

## 6. TurboModule实现

RN specs经Codegen生成Android基类，`lib_bridge`实现NativeHost/Network/Storage/Session/Telemetry；通过ReactPackage/TurboModule注册接入现有PackageList，注册细节G0按实际生成产物核验，不手写虚构基类名。桥接模块只做参数schema校验、上下文登记、线程切换、Port调用和结果序列化。

| 资源 | 线程/生命周期 | 保护机制 |
| --- | --- | --- |
| 参数解析/序列化 | bridge工作协程/Default | 限载荷、catch预期解析错误，业务DTO不强转 |
| 网络 | 独立OkHttp Dispatcher | 最大8运行/32待处理；总超时10秒；终态CAS |
| KV/数据库 | IO/Room自身调度 | 不在主线程或JS调用栈同步读盘 |
| Host/Auth展示 | Main Dispatcher | Activity处于可展示生命周期；无宿主返回HOST_NOT_READY |
| 事件/Promise | 模块封装的安全回调路径 | 检查ReactContext有效、runtimeId匹配、一次终态 |

bridge注册表管理能力调用的Promise终态，lib_net的Call表只管理底层传输，二者通过requestId关联，不各自竞争向JS回调。模块`invalidate`取消runtime查询、注销所有订阅、清Promise引用；不能取消进程级SessionCoordinator或删除已提交业务事实。cancel-before-register存30秒有界墓碑；requestId重复返回DUPLICATE_REQUEST，不重复执行。反序列化失败不输出原始敏感JSON，记录字段路径与安全错误码。

`HostPort/SessionPort`在lib_contract中为安全接口，app中的adapter连接Shell和biz_user_api；lib_bridge不能直接依赖biz_user实现。事件为总体定义的有限枚举，不能开放一个任意方法名反射调用网关。Android与未来iOS使用同协议hash与黄金向量。

## 7. 网络与本地拦截

NetworkClient不可变构建，apiId由descriptor映射为method/path/参数schema，内部Host=`https://entrytask.invalid`。Native认证也走该Client；A01/A02表单编码保留既有接口习惯，其他POST JSON，序列化使用受控库，不能字符串插值拼JSON。禁用自动业务POST重试，Demo客户端retryOnConnectionFailure=false，不缓存Cookie、不接受JS任意Header。

拦截链：上下文/鉴权 → 安全计时日志 → Demo application interceptor → 禁止出网终止器。Demo已知和未知路径都构建本地Response，任何路径不能chain.proceed到socket；额外终止器防未来代码遗漏。网络库通过工厂注入Demo interceptor而不import demo_engine；公开缓存位于Client协调层，实际请求才进入拦截链。

OkHttp拦截器是同步接口，**不能在intercept内假定suspend delay可用**。本期用专属有界OkHttp worker执行可取消等待：记录monotonic deadline，分片等待≤25ms检查Call.isCanceled、总超时与runtime关闭；取消抛受控IOException，恢复中断标志，不吞InterruptedException继续业务。保持最多8个并发，避免无界sleep线程池；不在拦截器runBlocking占用主/bridge线程。未来高并发模拟需求可另ADR引入异步Transport层，但仍须经过Native网络拦截边界。

总超时由RequestRegistry调度，含排队/人工延迟，不能仅依readTimeout覆盖Demo等待；OkHttp callTimeout设置为剩余deadline并配合取消。Body必须close，重复回调由终态原子门控；500/坏JSON为合成Response，取消/超时为TransportError。HTTP401先交SessionCoordinator按发起epoch判断，403不退出。GET单飞/缓存算法遵总体§7.1，不对有会话的A21作公开缓存。

本期无真实TLS连接。现有mTLS证书流加载/ignoreCert未生效/吞异常全部移出Demo初始化路径；未来real引入后配置必须校验失败即终止创建，禁止trust-all/hostname放行。不得为了运行Mock要求用户安装测试CA。

### 7.1 lib_net下载能力

#### 当前接口与实现范围

源码位于 [download/](../../android/lib_net/src/main/java/com/entry/lib/net/download)：`DownloadRequest(url,targetPath,tag)` 定义请求；`DownloadManager.enqueue(request,client)` 返回 DownloadTask；Manager 使用进程内 SupervisorJob/IO scope、ConcurrentHashMap 和 Semaphore(3)；Task 用 OkHttp 同步流、8KiB buffer、RandomAccessFile 与 Range 尝试续传，通过只读 StateFlow 暴露 Idle/Downloading/Success/Paused/Failed。它没有任务数据库、WorkManager/前台服务、跨进程恢复或 RN 下载 Bridge。

client 由调用方显式传入，下载实现不能自行读取认证凭证、NativeModule 或业务单例。当前 NetManager client 装有本地 MockInterceptor，不能用于真实文件下载；`ReadMe.txt` 中的 `NetManager.getOkHttpClient()` 并不存在，只能视为过期示例。后续由 app 注入独立受控下载 client，或注入明确的文件 Mock client，均不改变 RN HTTP 和银行 API client。

#### 代码审查发现的缺口（尚未修复）

| 触发条件 | 当前问题与后果 | 必须落实的设计 |
| --- | --- | --- |
| 同 tag 并发 enqueue/cancel/重新 enqueue | containsKey/get/put 不是原子操作，旧任务 finally.remove(tag) 可能移除新任务；取消后旧任务还可能继续写同一路径 | 原子注册，按任务身份 compare-remove，同 tag/同目标路径互斥；同 tag 不同参数明确冲突 |
| 排队取消、连接中取消或读取阻塞 | cancel 仅置 Boolean，不取消 Job/Call；排队任务仍可能联网，阻塞 read 无法及时退出 | 任务持有 Job/Call，排队和执行都可取消；终态至多一次，不能取消后继续成功 |
| 协程取消 | CancellationException 被捕获后不重抛 | 清理后继续传播取消；暂停/取消/失败语义分离，恢复重建执行句柄 |
| 本地已有片段但服务端返回 200 | 文件重写但 downloadedBytes 从旧文件长度开始，进度可能超过100% | 200 时文件、计数和摘要一起归零；下载状态从实际写入量计算 |
| 服务端返回206/416或文件内容已变化 | 未校验 Content-Range、ETag/Last-Modified 和偏移，可能拼接不同版本文件 | 保存 validator/总长/offset；校验范围，仅在一致时追加；416 只有本地长度/校验确认完整才成功 |
| 非成功响应或取 body 前抛异常 | Response 未统一 use 关闭；磁盘和资源异常处理不完整 | Response/stream/文件全路径关闭；父目录、空间、写失败、超时分别映射安全错误 |
| 任务完成 | 没有预期长度/摘要校验，直接把目标文件视为成功 | 使用 .part，校验后同文件系统原子提交到目标；失败不暴露半文件为完成文件 |
| 签名 URL/私有路径 | 当前日志打印完整 URL、路径和异常消息 | 仅记录 taskId/受控资源类别/字节数/耗时/安全码，移除 URL query 和完整私有路径 |

#### 目标执行协议

状态机建议为 `Queued → Running → Verifying → Succeeded`，分支为 `Paused / Cancelled / Failed`；恢复产生新的 executionId，保留逻辑 taskId，旧执行不可写入新状态。队列必须有上限，三并发是本地初始值，不能认为 OkHttp Dispatcher 对同步 execute 自动提供同样的并发限制。进度限频且字节计数单调，未知总长使用不确定进度。

Range 仅在 validator 与偏移匹配时续传；200 重下，206 验证 Content-Range，416 走长度/完整性检查。HTTP 语义依据 [RFC 9110 Range Requests](https://www.rfc-editor.org/rfc/rfc9110.html#name-range-requests)，业务必须额外定义摘要、最大文件大小和允许资源类型。

下载 URL 仅允许业务配置的 HTTPS Host，重定向每跳重新校验；bank Token 不转发到任意地址。目标由 app 的存储策略产生，限制应用私有目录，禁止页面/RN 传任意绝对路径。需要长时后台/进程恢复时另立实施任务，按用户可见性和系统限制选择持久调度方式；当前进程内下载不得宣称支持这些能力。未来向 RN 暴露时新增 typed Port/Codegen 契约，返回 taskId 和受控文件标识，事件有频率/数量上限并随 runtime 注销监听。

验收以 MockWebServer/临时文件覆盖200覆盖旧文件、有效/错误206、内容变更、416、未知总长、截断响应、网络/磁盘失败、排队/连接/读取取消、同tag竞争、同路径竞争与Response关闭；额外记录大文件内存、磁盘空间、后台及重启行为。现有下载目录没有对应行为测试，尚不能称可靠下载子系统。

## 8. Demo引擎、数据库与事务

### 8.1 JSON与资源打包

Gradle任务将根`mock/`和contracts复制到generated assets，任务输入包含JSON/hash，输出只在build目录；CI先校验37API覆盖与schema再打包。manifestVersion/fixtureVersion/contractHash写入BuildInfo。运行时首次初始化异步解析并缓存schema/fixture模板，接口按路径模板精确匹配，URL解码一次、query排序、Body规范化；未知接口或资源错误按需求返回。

FaultController支持全局默认、API覆盖、next-call，Clock支持真实时间/冻结/推进；调试入口位于开发菜单，不放普通设置。场景改变递增scenarioVersion、取消旧场景查询、清关联缓存。普通响应delay均1000～3000ms，缺文件/未知API等亦按普通模式延迟后反馈，桥接入参无效可立即失败；超时注入单独记录。

### 8.2 数据库定义

使用Room构建`demo_server_v1.db`，不是用户页面缓存；Database/Entity/DAO归demo_engine，lib_storage不持有会员表。以下为关键约束设计，具体索引名由实现固定。

| 表 | 主键/唯一约束 | 核心字段与用途 |
| --- | --- | --- |
| demo_users | userId PK，normalizedUsername UNIQUE | passwordSalt/封装后verifier/KDF参数、昵称/脱敏展示资料/level；无明文密码 |
| user_assets/accounts/transactions | 业务ID PK，userId索引 | 资产/账户/流水种子及归属；金融金额存Long或精确字符串，不用Float |
| checkins | (userId,businessDate) UNIQUE | cycleId、consecutiveDays、awardedEnergy、createdAt |
| energy_ledger | entryId PK，(userId,sourceType,sourceId) UNIQUE | delta、balanceAfter、时间；避免同签到重复记账 |
| member_balances | userId PK | balance、会员规则必要字段；与账本同事务更新 |
| benefits | benefitId PK | 库存、资格门槛、有效期、ruleVersion |
| claims | claimId PK，(userId,benefitId) UNIQUE | claimedAt、status、有效期快照 |
| messages/message_reads | messageId / (userId,messageId) PK | 内容/归属/已读状态，与unreadCount同源 |
| personal_items | itemId PK，userId/type索引 | 待办/积分/订单种子；只读 |
| idempotency | (userId,apiId,key) UNIQUE | payloadHash、resultJson、committedRevision、createdAt |
| demo_meta | key PK | seedVersion、schemaVersion、resourceRevision、clock配置 |

产品/资讯/活动静态内容可从JSON模板读，不强制全部入表；动态权益库存、签到和已读必须入库。用户注册时事务创建唯一用户及初始0/空状态，不复制demo_a数据。A/B seed与所有详情引用建立外键/校验约束。

### 8.3 事务边界

用Room `withTransaction`把签到记录、奖档判断、余额/账本、幂等记录、revision递增合并为一次提交；领取事务以`remainingStock>0`条件UPDATE及影响行数保证库存不负，再插claim，唯一冲突读取既有结果；消息false→true条件更新后计算未读。SQLite写串行不等于可以省略唯一约束；跨调用线程/重启也必须幂等。

事务进入前检查deadline/取消/sessionEpoch；提交临界区中不得因JS不再等结果回滚已确认事实。桥接取消只是用户等待终态，后续查询读实际结果。事务异常完整回滚，STORAGE_ERROR映射安全提示，不生成成功claimId。Room migration导出schema并测试从每个受支持版本升级；不使用fallbackToDestructiveMigration静默删Demo用户数据。

重置动作在开发菜单显式二次确认：暂停新请求→递增epoch/清会话→取消旧任务→事务替换种子/Clock→清公开缓存/用户偏好/导航checkpoint→广播demoStateReset。重置失败保留可诊断状态，不部分种子混用；普通logout不触发重置。

## 9. 存储、安全与旧数据迁移

| 存储类别 | 技术/位置 | 保留和错误策略 |
| --- | --- | --- |
| 用户偏好 | Preferences DataStore，注册Key命名空间v1 | assetVisibility按Native用户scope；失败返回STORAGE_ERROR，默认隐藏 |
| 公开缓存 | 独立Room CacheDB，key/meta/body/schemaHash/fetchedAt | TTL300秒、LRU/磁盘上限初始20MiB；损坏可删除公开缓存，不能连Demo DB一起删 |
| Demo业务 | demo_engine Room | 跨进程保留、明确版本迁移；损坏提示重置，不静默丢账号 |
| 会话Token | Native内存 | 退出/进程终止清除，不进SP/DataStore/Bundle |
| 密钥材料 | Android Keystore保护加密密钥，账号verifier放Demo DB | 备份排除；密钥丢失提示重建Demo账号/数据，不降级明文 |

演示密码验证采用带随机salt的PBKDF2-HMAC-SHA256，参数版本化（初始600,000次、salt16字节、输出32字节），常量时间比较，计算在CPU工作线程限并发；G0在minAPI和中端设备验证耗时后锁定参数，不能为UI响应把KDF放主线程或存明文。verifier以Keystore保护的AES-GCM密钥封装后落库，salt/KDF参数可明文；密钥失效返回明确错误，不回退明文。此为离线Demo账号实现，未来真实认证由后端负责，不复用客户端密码库当生产认证。

迁移旧`app_default_pref`时优先删除mock_db_password、mock_db_username、key_token等旧认证信息，现有单账号数据不安全转换为新凭证；升级后要求重新注册/登录演示用户。旧偏好若可识别可单向迁移，记录migrationVersion；迁包stroage→storage不更改applicationId。删除操作通过确认成功的持久化方式完成，不能仅靠异步apply发出后立即声称已删除；失败阻断恢复旧凭证。

AndroidManifest及备份规则排除敏感验证材料/个人数据；检查allowBackup/dataExtractionRules/fullBackupContent在目标版本实际效果。演示日志最终脱敏，无密码/Token/完整手机号卡号/资产body；release不打印HTTP BODY。库安全能力不可用时返回明确错误，不把Base64称加密。

## 10. 日志、性能和稳定性

MLog迁为TelemetrySink门面，接收结构化事件；lib_base只定义接口，Android日志/文件实现独立装配，避免base引入存储或网络。采用总体日志字段与5MiB×2/7天/500条队列上限，日志File I/O在专用串行worker。敏感字段在sink二次白名单过滤；错误堆栈保留内部安全摘要，不能直接把请求对象toString写盘。

| 风险 | 实施措施 | 工具/指标 |
| --- | --- | --- |
| 冷启动阻塞 | Shell先帧、数据库/fixture按需异步；初始化Trace分段 | Macrobenchmark/Perfetto，30次冷启动P95 |
| RN/主线程卡顿 | 网络等待/JSON/IO不在Main，底栏/返回确认有时限 | Perfetto/UI帧与JS性能标记，掉帧<5%目标 |
| Activity/RN泄漏 | 当前Host可撤引用、module invalidate、Surface销毁与Host分离 | Android Studio堆分析；调试可选LeakCanary，禁止进release |
| 内存增长 | 单runtime、图片尺寸、onTrimMemory转发清非活跃资源 | 20轮脚本，回收后增量≤20MiB目标 |
| ANR/线程耗尽 | 8运行/32排队，短片等待取消检查，日志有界 | 线程快照/队列长度/超时数，不仅看网络耗时 |
| 业务假成功 | Room事务/幂等/epoch校验 | 故障注入+重复/超时后的权威查询 |
| Bundle/桥接故障 | Native兜底、一次重建、版本/hash检查 | 无无限重启，AT-14证据 |

渲染分析区分慢帧和冻结帧；工具口径遵Android官方说明，不用截图主观描述“流畅”。[Android渲染性能](https://developer.android.com/topic/performance/issues/render)

## 11. 测试与质量门禁

| 测试对象 | 内容 | 执行层 |
| --- | --- | --- |
| 基础协议 | envelope校验、主次版本、载荷、cancel-before-register、唯一终态 | JVM单测+生成spec集成 |
| Session/VM | 注册缺Token拒绝、字段错误/重入/取消、并发401/旧epoch | coroutine-test虚拟时间；NativeUI设备用例 |
| Startup/AppGlobals | Provider合并、未初始化/重复初始化、Context归一化、宿主debug属性、无重IO | JVM可测试契约+合并Manifest检查+设备启动 |
| 下载 | Range/validator、200重下、416、取消、并发tag/路径、原子文件提交、资源关闭 | MockWebServer+临时文件；后台/重启另行设备验证 |
| 网络 | 37API路由、规范化参数、delay范围/可取消、未知禁止公网、Body关闭 | JVM/MockWebServer协议测试；demo真拦截器设备验证 |
| 数据库 | 奖档/断签/周期/跨日、库存竞争、幂等hash冲突、失败回滚、迁移 | Room设备测试，注入Clock/RandomSource |
| 存储/迁移 | typed Key/用户隔离、旧凭证删除、DataStore错误/备份检查 | 单测+设备/产物检查 |
| 宿主 | ActivityScenario重建、进程kill重启、runtime重建、返回与底栏 | UIAutomator/Espresso+固定演示脚本 |
| release | 内置Bundle、R8、native模块注册、离线演示、资源hash、签名 | demoRelease真机运行 |

先记录当前命令（在android目录）：`./gradlew --version`、`./gradlew :app:buildEnvironment`、`./gradlew :app:assembleDebug`、`:app:lintDebug`；拆模块/flavor后运行`:app:tasks --all`核验真实任务。目标CI：`:app:assembleDemoDebug`、`:app:assembleDemoRelease`、`:app:lintDemoDebug`和实际模块unit/device test任务；任务名以配置后的列表为准，不宣称现在已经存在。CI不得因为设备缺失而把connected测试标通过。

## 12. Android实施任务与退出条件

| 任务 | 依赖/主要修改 | 完成条件 |
| --- | --- | --- |
| AND-00 基线PoC | 根Gradle、RN集成 | AGP/JDK/RN矩阵、单Surface/Codegen/返回/优化包运行证据 |
| AND-01 模块边界与容器 | lib_base/contract/ui、新适配模块 | 无环，app显式依赖，lib_net/storage互不依赖 |
| AND-02 认证/会话迁移 | biz_user/API、旧SP清理 | 全局门禁、完整会话提交、取消/401/退出/冷启动；不从磁盘自动恢复凭证 |
| AND-03 基础网络/存储/日志 | lib_net/storage/bridge | typed异步能力、取消/总超时/安全日志和Key隔离 |
| AND-04 Demo引擎 | manifest/JSON/Room/Clock | 37API、事务/幂等/重置/断网，跨端黄金向量 |
| AND-05 Shell/导航联调 | MainActivity/RnHostFragment | 四Tab、单子栈、底栏ACK、配置/runtime/进程恢复 |
| AND-06 RN业务闭环联调 | RN纵向样板→全量 | AT-01～17覆盖并记录buildId/设备/日志 |
| AND-07 性能/打包 | 优化演示包、门禁 | 性能/内存/签名/R8报告，未达项有明确处理 |

任务表定义目标退出条件，当前部分能力已实施，完成范围以本文开头状态表和实际验证记录为准，不能整项标为“全部完成”或“全部未开始”。增加 AND-08 下载可靠性（§7.1）和 AND-09 AppGlobals 初始化契约（§2.1）；分别以协议/取消/竞争测试和 Startup/幂等/Manifest 验证退出。负责人/PR/证据在实施看板登记；拆迁按“接口适配→单条链路切换→删除旧路径”执行，同一功能不得长期双写会话或数据。回滚源码可保留兼容adapter，已删除明文密码不可恢复；数据库升级不保证旧APK可读，优先前向修复，不用git回退等同数据回退。

扩展参考：模块化遵[Android官方模式](https://developer.android.com/topic/modularization/patterns)；DataStore用于异步偏好，Room用于关系及事务数据，二者职责见[DataStore](https://developer.android.com/topic/libraries/architecture/datastore)和[Room](https://developer.android.com/training/data-storage/room)。引用核查2026-10-17，具体依赖版本由G0锁定，不宣称文档示例已经编译通过。

## 13. V1.1 优化补充（2026-10-18）

### 13.1 Mock资源随APK打包的硬门禁

根 mock/ 是资源源码，当前真实任务链为 `validateRuntimeResources → prepare<Variant>RuntimeResources → merge<Variant>Assets → package`，使用 `androidComponents.onVariants` 和 `variant.sources.assets.addGeneratedSourceDirectory` 注册生产任务。不得退回只把输出目录加入 SourceSet 的实现；历史上该做法导致 clean 后 APK 漏资源。生成目录在 build 内，禁止手工复制到 src/main/assets；输入/输出声明覆盖共享 JSON/manifest，hash/BuildInfo 清单仍是后续目标。

生成资产根固定`assets/mock/`，包含manifest、37API request/response及场景JSON、seeds、schema、版本/hash清单。demoDebug/demoRelease都包含；variant只收所需演示场景，未来real不依赖demo_engine/Mock资源。失败/缺API/坏JSON立即阻断打包，不用“上次生成成功的目录”兜底。

验收同时看APK ZIP条目、安装后AssetManager资源访问、hash/schema版本与buildId；关闭Metro、飞行模式运行demoRelease完成读取/签到/领取并重启核对。AAB另检最终安装split，不以工程目录或APK大小增加推断资源已入包。assets只读，仅初始化数据库；本地注册账号/用户状态不反写assets或打入后续APK。

### 13.2 Native网络与RN自有HTTP并存

lib_net创建本项目专用OkHttpClient，Demo拦截器只装在该client，由NativeNetwork与Native认证调用；RN Axios使用RN标准网络栈，不通过本项目自定义NativeModule服务。禁止向RN全局OkHttpClientProvider注入Demo interceptor来“省事”，否则会混淆两条通路、影响外部服务/资源或Metro；需定制RN底层网络的场景单独ADR和测试。

接口策略由共享EndpointRegistry声明，Native端仍独立校验自身exposure/auth白名单，不相信JS传来的apiId即有权限。RN HTTP公开服务当前不取银行Token，不触发银行401失效；Native bank接口保留原会话/证书/缓存/Mock职责。两种client的cookie/缓存/重试/日志不通过全局可变配置串用。本期37API默认nativeMock，缺真实服务的rnHttp由RN配置层报错，不在Android提供一个伪装HTTP直连的桥接替代。

nativeMock正常人工延迟1000～3000ms只适用于本项目专用Mock client；真实HTTP不额外sleep。业务指定Native能力失败即返回其错误，不自动放行socket或让JS换路重放POST。

### 13.3 RN日志与Native日志关联

RN Logger独立开发console/内存sink；NativeTelemetry接收core/bridge级事件并校验eventId/layer/flowId/requestId/spanId/parentSpanId/transport及安全属性。RN调用span与Native执行span分开计时/终态，Native不得覆盖RN eventId或把同一核心事件每次批次重试写成新事件。

TelemetrySink增加有界近期eventId去重（按runtime+eventId，初始1000条/10分钟，先到上限先淘汰），无持久化全量事件表；队列拥塞/bridge销毁返回安全ACK/droppedCount，不影响业务。RN故障日志不依赖Native可用才能在开发环境查看；日志失败禁止递归emit。release严禁Axios/RN/Native网络body日志、Redux完整state/action转储，Native最终脱敏。

### 13.4 单RN instance验证与任务更新

进程唯一RNRuntimeOwner/ReactHost；页面、Tab、认证Activity都不能new第二个Host或独立runtime。异常恢复先invalidate旧模块/取消请求/注销订阅/释放Surface，确认旧runtime不可再执行后重建；同一时刻activeInstanceCount≤1。只保留一个RN业务Surface是本项目额外简化，不将Surface数量与runtime数量混为同一概念。

开发诊断增加runtimeCreated/destroyed及活动计数，压力测试20轮四Tab+认证/Activity重建/runtime恢复计数不增加；Shell重建不重建Redux Store/RTK Query服务，只有真正runtime重建才新建。

任务调整：AND00加入单实例计数及独立RN HTTP不被Native Mock误拦截的PoC；AND04加入APK资产与离线优化包验收；AND03加入日志span/去重和client隔离；RN-P0在正式业务页之前完成。全部新增验证仍为待实施，不宣称本次运行过Gradle或安装包。

## 14. V1.2 登录审查与验证记录（2026-10-19）

结论：Native 权威会话、RN 全局门禁、singleTask 首页上方展示认证页、成功只关闭认证页的方向合理。它适用于当前演示工程；复杂认证任务恢复、统一 SessionCoordinator 和生产会话失效协议仍是目标设计。

| 本轮发现 | 影响 | 本轮处理 |
| --- | --- | --- |
| 启动 Session 响应直接合并到最新 Shell，未检查旧 epoch | 迟到响应可能恢复已退出的登录态或覆盖新会话 | 增加 acceptSession 及双版本防回退；补乱序回归测试 |
| RN 已显示门禁但 Native 底栏仍显示 | 用户可点击 Native Tab，外壳状态与门禁不一致 | 按认证/深度统一计算底栏；退出重置Tab和深度 |
| 登录页启动异常未解除 authRequested | 后续“前往登录”请求被持续忽略 | 异常恢复防重标记，明确失败码 |
| Host命令转到UI线程期间生命周期可能变化 | 后台或已销毁宿主仍可能尝试展示认证页 | UI线程重查Activity与RESUMED状态；不展示时安全失败 |

本轮运行 `npm run typecheck`、`npm run lint`、`npm run architecture:check` 全部通过；`npm test -- --runInBand --watchman=false` 为7套/18项通过，使用参数避开本机Watchman状态目录限制；`./gradlew :app:generateCodegenArtifactsFromSchema :app:assembleDebug` 通过。ADB设备列表为空，未执行CLI设备启动、登录往返、singleTask重入及恢复验证。未修改下载/AppGlobals实现，也未新增其行为测试，§2与§7中的可靠性缺口仍需独立实施。


## 15. Native 提前认证与 RN 页面门禁实施记录（2026-10-21）

第一步已实现：onCreate 原生会话校验、RESUMED 展示认证、首次认证返回后延迟挂载 RN、页面默认保护/公开帮助、取消不循环、singleTask 重入及迟到结果隔离。XML/MVVM、EntryTask 注册名、com.entrytask、稳定 Fragment tag 与单 ReactHost 保持原约定；没有引入引擎预热或首页数据预取。

- RN：typecheck、lint、architecture:check 通过；Jest 8套/25项通过（使用 `--watchman=false` 避开本机 Watchman 状态目录限制）。覆盖默认保护、公开帮助、取消后revision刷新不重弹、ACK不放行、Native会话确认及失效清理。
- Native：`:app:testDebugUnitTest` 17项、`:biz_user:testDebugUnitTest` 5项，零失败/跳过；Codegen、Debug构建、Lint及设备测试编译通过。构建使用 `-PreactNativeArchitectures=arm64-v8a --max-workers=2`，本轮仅验证该ABI。
- 设备：API37 Pixel_10模拟器，StartupAuthenticationTest两项 + ShellHostTest四项共6项通过。验证首次认证前没有HomeFragment、重复Bridge合并、取消/帮助/重试、singleTask重入空结果隔离、认证返回原MainActivity、根Tab/返回与Activity重建。成功分支用测试内存会话，不提交账号注册/密码认证、不改已有账号。
- 官方CLI：根目录 `npx react-native run-android --no-packager --active-arch-only`，使用已运行的Metro完成构建、安装和启动。

首轮设备测试发现并修复两处竞态：singleTask清除认证页的空data取消结果覆盖新请求；旧MainActivity销毁清除新流程防重状态。修复后完整重跑6项设备用例通过。

未验证：跨进程认证任务恢复、真机/其他ABI、Release性能、iOS原生对等能力。没有以本轮Debug设备结果承诺性能指标；完整引擎预热属于后续独立阶段。


## 16. RN 引擎预热（第二步，2026-10-21）

沿用§15提前认证，预热能力默认关闭（模拟器A/B未证明稳定收益），可用entrytaskRnWarmup=true显式启用。MainApplication注册AuthWarmupLifecycle，在Native认证首帧提交之后的下一动画回调调用唯一ReactHost.start()。入口只注册组件，不挂载Surface/业务图、不请求首页数据；MainActivity仍在认证返回/失败后挂载RN内容。首帧旧API退化语义、生命周期清理、低内存跳过、失败隔离与构建开关见 [ADR-012](../adr/012-rn-engine-warmup.md)。

RnWarmupController为进程级纯状态协调，注入引擎启动、作用域、单调时钟和诊断；不持有Activity/View。AuthWarmupLifecycle拥有可撤销帧回调，暂停即撤销待启动工作；已启动共享引擎不随页面取消销毁。真实Surface请求后不再隔离运行异常。公开TaskInterface用25ms异步有界观察，30秒超时仅停止观察，不破坏共享引擎。

性能使用同Release变体开关A/B、固定认证停留、独立进程和交替顺序，首页可见与Mock数据完成分开统计。可复现命令、原始样本摘要和未验证范围见 [预热性能验证](../development/rn-warmup-performance.md)。


## 17. 登录返回与SP认证账号修复（2026-10-22已实施）

登录根页不直接finish返回首页：IME优先，之后2秒双击finishAffinity退出任务；注册页系统返回登录。退出任务前清Native会话/递增epoch、清除Activity Result载荷以满足Android约束；Repository原子核对发起时epoch，拒绝退出后的迟到认证成功。正常成功仍回传flowId并只关闭AuthActivity。系统/程序性取消与singleTask重入继续采用原协调协议。

Native认证Mock改用manifest精确method/path及现有分API JSON；UserService内部错误码为400/401/409/500数字，不改变RN错误契约。注册与登录共用MockAccountStore，SP按用户名哈希索引独立账号，后台原子commit落盘校验记录，重复账号不覆盖；启动不再删除mock_db账号。完整mock_reg/mock_db旧键对在认证后台访问时迁移，成功写入后同事务删除旧明文，不清其他SP。

当前增量实现使用版本化PBKDF2校验值、16字节salt、32字节输出、120000次，优先HMAC-SHA256，旧Provider兼容HMAC-SHA1；未实施§9目标Room/Keystore封装及600000次参数，不能当作目标方案全部验收。账号持久化与内存会话分开，重启仍需Native校验登录。实现、迁移与验证详见 [ADR-013](../adr/013-auth-back-and-mock-persistence.md) 和 [修复记录](../development/认证返回与账号持久化修复.md)。
