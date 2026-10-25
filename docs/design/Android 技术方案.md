# Android 技术方案

## 工程与边界

Android 是当前 Native 实施端，模块为 app、biz_user、biz_dashboard、lib_base、lib_net、lib_storage、demo_engine。app 负责组装，业务按领域划分，技术库提供不依赖 UI 的能力。未创建的规划模块不能作为现有依赖使用，不通过新增空模块满足目录示意。

应用身份固定 `com.entrytask`，Application 为 `.MainApplication`，RN 注册组件为 `EntryTask`。保留 RN Gradle 插件、Autolinking、Codegen、新架构与 Hermes。开发入口 `src/index`，Bundle 入口 `../../src/index.js`，Codegen 扫描根 `specs/`，生成包 `com.entrytask.specs`。

## 模块职责与依赖

| 模块 | 当前职责 | 约束 |
| --- | --- | --- |
| app | 宿主、Shell MVVM、桥接适配、资源生成、存储适配与依赖组装 | 不写银行业务规则或直接操作 DAO |
| biz_user | Compose 认证、AuthRepository、UserRepository、UserAuthManager | VM 依赖领域接口，不依赖存储实现 |
| biz_dashboard | Compose 四页、共性区域、分域 VM/Repository | 通过 NativeApiClient 使用原型 API |
| lib_base | AppGlobals、Startup、日志及基础 UI/MVVM 封装 | 不成为万能服务定位器 |
| lib_net | OkHttp/Retrofit、认证 Mock、下载初版 | 通过 MockAccountStorage Port 注入认证存储 |
| lib_storage | 普通偏好、RoomStorage 与 CredentialStore | 不认识 RN 页面或认证导航 |
| demo_engine | 原型 Mock 路由及按用户隔离的账本 | 与认证 Mock 分开，不回落公网 |

依赖由组合根注入，公开签名需要时才使用 api，其余 implementation。源码包名保留实际 `com.entry.*` 和 `com.entry.lib.stroage`，不在普通业务任务中改安装身份或进行全局改名。

## Application 初始化

1. 系统创建并 attach Application；MainApplication 没有 attachBaseContext 业务初始化覆盖。
2. AndroidX Startup 的 AppContextInitializer 将 Application 上下文存入 AppGlobals；此时早于 Application.onCreate。
3. MainApplication.onCreate 初始化 MLog、StorageManager 普通偏好、注入认证存储的 NetManager Mock，以及 RN 集成；注册 AuthWarmupLifecycle。
4. Room 默认库按需创建，首次认证存储访问在网络后台线程打开，不在启动主线程提前读账号表。

AppGlobals 只代理 context、applicationId、isDebug，不负责网络、认证或数据库依赖查找。isDebug 从 ApplicationInfo 标志读取；当前实现不宣称具备完整并发重复初始化协调协议。

## Shell MVVM 与单宿主

```text
MainActivity → MainViewModel → Shell/Session 契约
                 ↓                ↑ data adapter
             MainUiState     ShellRepository / UserAuthManager
                 ↓
MainShellView + RootContentHostFragment + Host Bridge 事件
```

MainActivity 在 super.onCreate 前安装 RNScreensFragmentFactory，保留 activity_home.xml 的稳定容器。MainShellView 使用 Compose RootTabBar；页面业务内容通过 RootContentHostFragment 承载。外层 tag 为 `entrytask-root-hub`，内层 RN tag 为 `entrytask-rn-root`、容器 `rn_content`；恢复时优先复用。

MainApplication 持有进程唯一 ReactHost，不按页面创建 runtime。RootContentHostFragment.usesReactNative 读取 BuildConfig：显式 entrytaskUiMode 优先，其次 IDE Native、CLI 端口标记 RN、默认 Native。Native 分支通过 ComposeView 渲染 biz_dashboard，组合随 viewLifecycleOwner 销毁。

ShellRepository 权威保存 Tab/depth/revision；HostFragmentHandle 与 ForegroundHostConnection 管理宿主引用和前台连接。MainViewModel 不持有 Activity/View。根返回顺序为键盘、RN/弹层、子页、其他 Tab 回首页、首页双击退出；当前不声明完整自定义返回 ACK 或预测返回协议。

页面区域、组件和资源生命周期见[首页与四 Tab 方案](首页与四Tab技术方案.md)。

## 认证与会话

MainActivity 创建即查询 Native 会话，在 RESUMED 发起认证展示，不等待 RN。AuthActivity 成功只返回结果并 finish 自身；singleTask MainActivity 在正常认证流程留在后台，返回及恢复时重新读取权威快照。

认证协调使用明确状态和 flowId，合并重入，隔离迟到取消；RN 页面仍默认认证，通过 NativeSession.getSnapshot 校准，通过 NativeHost.command 请求认证。成功命令 ACK 不等于会话建立。只有公开帮助可跳过页面门禁。

biz_user 使用独立导航条目 VM、共用 AuthRoute 和纯 AuthFormContent；UserViewModel 依赖 AuthRepository，成功事实与提示保存在只读状态。Repository 校验响应并按 epoch 原子提交 UserAuthManager 会话。Room 只保存 Mock 账号校验记录，Token 留在 Native 内存。详细设计见[登录注册方案](登录注册技术方案.md)。

## Bridge 协议

| Codegen 模块 | 当前职责 |
| --- | --- |
| NativeHost | bootstrap、command、onHostEvent；宿主命令和快照协调 |
| NativeSession | getSnapshot；读取安全会话信息 |
| NativeNetwork | execute、cancel；受控 API 请求 |
| NativeStorage | read、write、remove；typed key 存储 |
| NativeTelemetry | emitBatch；接收脱敏日志批次 |

适配位于 app/bridge，由 EntryTaskPackage 注册并继承生成 Spec。边界校验 JSON、协议版本、apiId/exposure、长度、key 和响应形状；Promise 只能终止一次，runtime invalidate 取消任务、释放监听。iOS 对等实现待实施，不用 JS 假模块伪造握手成功。

## 网络、Mock 与资源生成

认证请求经 NetManager 的 Retrofit/OkHttp，原型页面经 prototypeNetwork 与 demo_engine；当前没有将所有客户端强行合并成一个 NetworkClient。

认证 Mock 内执行注册写 Room、登录读 Room；原型 Mock 从 manifest 和分 API JSON 生成页面数据，写入轻量用户账本。两者精确路由，普通请求后台随机延迟 1～3 秒，可取消，未匹配 API 拒绝。RN HTTP 公开服务是独立显式通路，不被 Mock 全局拦截。

RuntimeAssetsTask 通过 androidComponents 为每个变体注册 assets 生成目录，依赖链为 validateRuntimeResources → prepare<Variant>RuntimeResources → merge<Variant>Assets。contracts/mock 只有根目录一份源码，输出在 build，不能手工维护平台副本。DashboardArtTask 从共享素材生成 Native 图片资源，使用白名单。

打包配置变更必须空生成目录构建并检查最终 APK，不能只检查源文件存在。Debug/Release 当前都是演示包，不能因 Release 名称移除 Mock。普通 JS Reload 不更新原生代码或 APK assets。

## Room 与普通存储

RoomStorage 管理数据库，CredentialStore 封装查询与事务唯一插入；app 的 RoomMockAccountStorage 适配网络 Port，业务页不拿 DAO。认证库 entrytask_auth.db、auth_credentials 表保存哈希 key 与加盐 PBKDF2 校验值。普通偏好由 StorageManager 管理，与内存会话及原型账本分开。

不启用主线程数据库和破坏式迁移；异常传播到请求层。版本变更需 schema、显式 Migration 和保留数据的升级测试。见[Room 存储设计](../development/Room认证存储设计.md)。

## 下载能力与完善边界

lib_net/download 当前为进程内下载初版：DownloadRequest、DownloadManager、DownloadTask，SupervisorJob/IO、任务表、Semaphore(3)、8KiB 缓冲、RandomAccessFile 与 Range，StateFlow 暴露 Idle/Downloading/Success/Paused/Failed。

当前没有 WorkManager、前台服务、任务持久化恢复或 RN 下载 Bridge，不能按稳定后台下载服务验收。需独立完善以下问题：

- tag 注册/移除原子性、每次执行身份与有界等待队列。
- Job/OkHttp Call 取消联动、CancellationException 传播及 Response 关闭。
- 200 重下进度、206/416、ETag/Last-Modified 与续传验证。
- `.part` 文件、长度/摘要校验与原子提交；失败清理策略。
- URL/目标路径约束和日志脱敏，避免记录完整 URL 或私有路径。

这些是能力边界，不属于首页与认证已完成的可靠性保证。

## 日志、性能与验证

Native 使用 MLog，Bridge 诊断标签 EntryTask.Bridge，RN 批次 EntryTask.RN，JS 自身 Console 独立。日志只记录阶段、错误码、耗时和关联 ID。主线程不等待网络、数据库或密码派生；取消、请求代次及 epoch 防止旧结果回写。

RN 预热默认关闭，启用时仅在认证首帧后启动唯一 Host，不创建 Surface 或预取数据。评估方法见[预热指南](../development/rn-warmup-performance.md)。

构建、模块测试、Codegen、双模式启动、设备测试与数据保护命令见[Android README](../../android/README.md)。资源改动补 clean 构建和 APK 检查，认证/宿主改动补取消、重入、恢复及 RN 门禁。Release 当前为演示签名且未启用 R8，不视为正式发布配置；iOS、真机性能与其他 ABI 需要各自验证。
