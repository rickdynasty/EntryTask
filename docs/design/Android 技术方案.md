# Android 技术方案

版本：1.1 · 日期：2026-10-18 · 状态：待评审设计、待实施  
输入：[总体方案](EtryTask%20项目设计方案.md)、[RN方案](前端RN技术方案.md)、[需求](../requirements/01-prototype-requirements.md)

## 1. Android职责与现状迁移

Android实现Native Shell、四根Tab、单RN容器、Native认证、会话、五个TurboModule、网络/缓存/存储/日志和本地Demo引擎。四域及其子页不再新增Kotlin业务UI；MVVM用于Native认证和Shell状态，RN业务MVVM见RN方案。

| 现有实现 | 迁移决策 | 完成证据 |
| --- | --- | --- |
| MainActivity四Fragment占位、onCreate/onResume强制登录 | 一个稳定RN容器+Native底栏；只在受保护意图按需认证 | 游客浏览、四Tab同步、单Surface、无重复认证 |
| BaseFragment生成ViewId及每次replace RN片段 | 专用RnHostFragment稳定资源ID/tag，恢复已有Fragment；删除万能混合基类分支 | Activity重建/进程重启不重复add |
| UserViewModel回调、公开MutableStateFlow、注册只置Boolean | Login/Register分VM，统一AuthRepository和SessionCoordinator | 完整会话、取消/错误/重入状态测试 |
| UserRepository落Token与UserAuthManager Boolean | 单一会话事实，Token仅Native内存 | 旧Token删除、进程重启游客、退出无残留 |
| MockInterceptor内联JSON/存明文密码/未知放行 | demo_engine独立拦截器、JSON manifest、Room事务 | 37API、未知API拒绝、离线演示、无明文 |
| NetManager可变初始化/吞TLS错误 | AppContainer创建不可变NetworkClient | 初始化失败明确，demo不尝试公网 |
| StorageManager包stroage、Any静默类型 | 注册Key类型化DataStore/受控存储，迁包不改安装身份 | 迁移测试/失败可见 |
| app只依biz_user，借其api传递库 | app显式依赖使用模块，implementation默认 | 依赖白名单报告 |

旧`android/docs`三文件保留为历史初稿，本方案和总体方案取代其中与RN业务路径冲突的部分；原有通用规则在不冲突时继续采用，不实施旧稿的Native四域页面任务。

## 2. Gradle模块与依赖

| 模块 | 职责/公开面 | 依赖边界 |
| --- | --- | --- |
| app | Application/AppContainer、MainActivity、导航/能力组装、变体配置 | 显式依赖宿主/桥接/认证/技术实现，禁止业务DAO/规则 |
| lib_base | 纯Kotlin AppResult/AppError、Clock/RandomSource、Dispatcher接口与安全标识 | 不依Android UI、RN、其他工程模块；现有UI/生命周期迁出 |
| lib_contract | 纯Kotlin能力Port、bridge DTO/schema版本、API descriptor、Host/Session安全契约 | 依lib_base，可依coroutines；不依业务实现/RN/Retrofit/Room |
| lib_ui | Native基础主题、薄BaseActivity、底栏、错误/Loading基础组件 | 依lib_base/AndroidX；不依RN和业务 |
| lib_net | OkHttpClientFactory、请求注册/取消/超时、传输错误、公开缓存协调钩子 | 实现lib_contract的网络Port；不依lib_storage、demo或biz |
| lib_storage | Preferences DataStore、CacheStore实现、Key注册与安全材料存储 | 实现lib_contract的存储接口；不依lib_net/biz/RN |
| biz_user_api | 会话/认证入口、只读用户模型 | 纯Kotlin，依base/coroutines，不暴露Token给桥接 |
| biz_user | Compose认证UI/VM、AuthRepository、SessionCoordinator | 依user_api/contract/base/ui；IO通过Port注入 |
| lib_rn_host | ReactHost/Surface生命周期、RnHostFragment、Host事件/恢复协作 | 依RN、contract/base/ui；不依biz实现 |
| lib_bridge | 五个Codegen模块、序列化校验、Promise/监听生命周期 | 依RN、contract/base；不能直接读UserAuthManager或Activity全局单例 |
| demo_engine | JSON路由/场景/Clock、账号/会员/消息handler、Room Demo DB | 依contract/base/Room；网络拦截器适配可依OkHttp；不依RN/app/biz实现 |

Room用于**有事务需求的Demo数据库**，不是为了让RN每个只读产品列表都造一份业务Room仓库。公开缓存可使用lib_storage的独立Room cache库；二者数据库文件和清理策略分开。通用lib_net通过注入ResponseCache接口使用缓存，不反向依赖lib_storage实现。

工程层次依赖：`app → {biz_user,lib_rn_host,lib_bridge,lib_net,lib_storage,demo_engine,lib_ui}`；`biz_user → biz_user_api/lib_contract/lib_ui`；`lib_contract,biz_user_api → lib_base`。同层技术库不互相找单例。demo_engine是独立演示业务实现，不能被正式real依赖图加载。

模块内部按entry/presentation/domain/data/di分包，类默认internal；API模型与DTO分离。app负责接口到实现装配，初期手动构造注入和ViewModel Factory；团队规模与依赖复杂度增长再用ADR迁Hilt，不在同作用域并存两套容器。

## 3. 构建与环境基线

现有Kotlin2.1.20、SDK36/min24、Gradle9.3.1、JVM目标17、NDK27.1.12297006来自文件，当前未解析AGP最终版本；先执行G0环境清单与debug/release构建，再选择兼容版本。JDK按解析AGP/RN要求锁定，不把“JVM target17”误写成“运行Gradle一定兼容JDK17”。

建立`gradle/libs.versions.toml`及渐进convention plugin统一SDK/JVM/Lifecycle/Compose；保留RN settings plugin、rootproject插件、autolinking和Hermes配置。跨Groovy/KTS可共存，不为统一DSL重写所有文件。compile/target/NDK调整必须单独验证RN原生库。

新增demo flavor，debug/release为buildType；demoDebug可Metro开发，`react.debuggableVariants`显式包含demoDebug；demoRelease内置Bundle/Hermes字节码/本地资源，用演示签名。real仅保留设计扩展点，本期不提供假real产物，缺真实配置直接构建失败。R8在demoRelease启用后检查Codegen注册、反射/序列化、Room和资源保留；本期不以全包keep代替精确规则。

构建缓存/并行配置先收集任务耗时再开，release按arm64真机与目标模拟器ABI分别验证；保留所需RN ABI，不以删架构掩盖原生链接错误。CI记录JDK、AGP、Gradle、Kotlin、NDK、SDK、Node、npm与依赖锁hash。

## 4. Shell MVVM与单RN宿主

### 4.1 生命周期所有权

MainApplication持有一个ReactHost（沿用本地getDefaultReactHost入口，G0核查RN0.86 API）；MainActivity持有ShellViewModel和单RnHostFragment。ShellUiState包含selectedTab、hostRevision、底栏策略、authOverlay、hostPhase/loading/error、rnReady；不持有页面产品/会员/资产数据。

ReactHost属于进程；Surface属于Fragment View生命周期；Codegen模块实例属于ReactContext/runtime；HostPort的Activity引用为可撤销的当前宿主引用，Activity销毁解除，不由模块长期强持有。FragmentManager恢复先findFragmentByTag，容器ID用R.id.rn_content，不生成随机ID；状态已保存时把导航意图排队到RESUMED，禁止commitAllowingStateLoss。

ReactFragment/ReactSurface的具体托管路径在G0基于本地RN0.86选择一种固定实现，默认使用官方Fragment集成；不得同时由ReactFragment和自定义Surface重复attach同一Root。将root挂载/前后台/销毁的调用链和线程写入PoC记录后再扩页。[RN0.86宿主集成](https://reactnative.dev/docs/0.86/integration-with-existing-apps)

### 4.2 导航与布局

ShellCoordinator拥有底栏和selectedTab，每次真实变化递增hostRevision；Native按钮直接更新RootHub命令并显示切换中，RN ACK成功完成；失败重取快照，拒绝持续错位。子页中Native底栏隐藏且不可点击；RN子栈不再在Android创建额外Activity承载每个页面。

WindowInsetsCompat在宿主处读系统栏/IME，发布内容区域insets；RN处理自身内容安全区，原生底栏仅根页占高。当前edgeToEdgeEnabled=false不等于target36下无需适配；自定义Activity需按平台实测处理系统边到边，禁止靠模板flag当完成证据。旋转重建、键盘弹起、系统导航模式均有独立用例。

### 4.3 返回协议

OnBackPressedDispatcher只有一个Shell协调入口。认证Activity优先处理自己的返回；Shell发backRequested(commandId)，RN按弹层/键盘/子栈返回consumed/notConsumed，Native等待确认，不同时调用super和RN返回。RN报告根页且未消费后，非首页切首页；首页按现有2秒双击返回退出，窗口单调时间计时，RN子页返回不启动该窗口。

500ms无ACK则重同步snapshot，不能推定未消费并退出；2秒无响应显示Native可恢复提示，可选择重建RN或返回首页，不自动退出掩盖卡死。Android预测返回的注册与native-stack交互在G0验证：预览不提交状态，commit才请求实际pop，cancel保留原depth；不支持的组合在兼容报告写明，不伪称手势完整支持。

### 4.4 启动与故障

初始化顺序：基础配置/安全日志 → 作用域容器/轻量会话 → Shell首帧 → 异步存储迁移与Demo资源校验 → RN runtime/Surface → bootstrap就绪 → 查询数据。DB迁移期间能显示骨架/明确准备态，禁止主线程等待I/O；迁移失败阻断写操作、允许可解释故障页。

Bundle读取失败/注册缺失/主版本不符由Native错误视图处理；可恢复runtime最多重建一次，释放旧模块/监听后重握手；仍失败由用户显式重试，不循环创建ReactHost。Native崩溃不是宿主错误视图能兜底的情况，必须保留符号化诊断与修复。

## 5. Native认证MVVM与会话

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
| AND-02 认证/会话迁移 | biz_user/API、旧SP清理 | 完整自动登录、取消/401/退出/冷启动用例 |
| AND-03 基础网络/存储/日志 | lib_net/storage/bridge | typed异步能力、取消/总超时/安全日志和Key隔离 |
| AND-04 Demo引擎 | manifest/JSON/Room/Clock | 37API、事务/幂等/重置/断网，跨端黄金向量 |
| AND-05 Shell/导航联调 | MainActivity/RnHostFragment | 四Tab、单子栈、底栏ACK、配置/runtime/进程恢复 |
| AND-06 RN业务闭环联调 | RN纵向样板→全量 | AT-01～17覆盖并记录buildId/设备/日志 |
| AND-07 性能/打包 | 优化演示包、门禁 | 性能/内存/签名/R8报告，未达项有明确处理 |

每项初始未开始，负责人/PR/证据在实施看板登记；拆迁按“接口适配→单条链路切换→删除旧路径”执行，同一功能不得长期双写会话或数据。回滚源码可保留兼容adapter，已删除明文密码不可恢复；数据库升级不保证旧APK可读，优先前向修复，不用git回退等同数据回退。

扩展参考：模块化遵[Android官方模式](https://developer.android.com/topic/modularization/patterns)；DataStore用于异步偏好，Room用于关系及事务数据，二者职责见[DataStore](https://developer.android.com/topic/libraries/architecture/datastore)和[Room](https://developer.android.com/training/data-storage/room)。引用核查2026-10-17，具体依赖版本由G0锁定，不宣称文档示例已经编译通过。

## 13. V1.1 优化补充（2026-10-18）

### 13.1 Mock资源随APK打包的硬门禁

本期不是“运行时访问项目根mock目录”：根mock/是源码，构建依赖链必须为`validateMockContracts → generateMockAssets → merge<Variant>Assets → package`，通过AGP锁定版本的generated-assets/SourceSet能力接入。生成目录在build内，禁止每次手工复制到src/main/assets造成旧文件残留；任务输入/输出声明涵盖JSON/schema/manifest与hash，依赖变化触发重打包。

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
