# Room 认证存储实施说明

日期：2026-10-25。替代原注册/登录的 SP 持久化方式，普通偏好不迁移。设计取舍见 [ADR-015](../adr/015-room-auth-credential-storage.md)，基础库用法见 [lib_storage README](../../android/lib_storage/README.md)。

## 完整数据流

```text
MainApplication.onCreate
  → NetManager.initMock(注入MockInterceptor及lazy账号Provider)
  → 未打开数据库、未派生密码、未迁移账号

用户提交表单
  → UserViewModel → UserRepository → UserService(Retrofit)
  → OkHttp后台 MockInterceptor.handleAuthentication
  → lazy创建同一个MockAccountStore / RoomStorage
  → RoomMockAccountStorage迁移旧SP校验记录（如有）
  → MockAccountStore.migrateLegacy转换旧明文键对（如有）
  → 注册：查重 → 派生校验值 → create → Room事务 INSERT OR IGNORE
     插入成功返回200；主键冲突返回409，不覆盖原密码
  → 登录：Room按account_key读取 → 按记录算法/盐派生 → 常量时间比较
     正确返回200；错误或不存在返回401
  → MockInterceptor从分API JSON模板生成响应
  → UserRepository检查取消/epoch → UserAuthManager提交Native内存会话
  → AuthActivity关闭 → 保留原singleTask MainActivity及原Native/RN首页路径
```

数据库查询/事务及 PBKDF2 均在 OkHttp 后台线程。Application 只构造拦截器和持有 lazy 实例，不在启动主线程打开 Room。同步 `CredentialStore` 是为了适配同步拦截器；其他协程调用方必须切 IO。Room 默认拒绝主线程查询，未添加 `allowMainThreadQueries`。

## 表、版本和隔离

| 项目 | 实现 |
| --- | --- |
| 文件 | 应用私有 databases/entrytask_auth.db（及SQLite维护的WAL/SHM） |
| 版本 | 1，KSP导出并保留schemas/…/1.json |
| 表 | auth_credentials |
| 唯一主键 | account_key = mock_auth_v1.<SHA-256(trim用户名)> |
| 校验字段 | password_verifier = v1:算法:120000:saltHex:verifierHex |
| KDF | 保留既有PBKDF2-HMAC-SHA256，旧Provider兼容SHA1；16字节盐、32字节输出 |
| 登录态 | 仍只在UserAuthManager内存；进程重启重新登录，数据库记录保留 |
| 不保存 | 明文密码、用户名、Token、个人资产响应 |
| 退出登录 | 不删除数据库或账号；只清内存会话 |

RoomStorage 包装 Room builder、内部实体/DAO 与批量事务，不向业务暴露原始数据库。SQL主键保护覆盖多连接竞争；MockAccountStore的进程锁继续保护本进程的派生/旧键迁移顺序。查询/提交异常走既有认证失败响应，不走 SP 兜底或真实网络兜底。

Room 2.7.2 与 Kotlin 2.1.20 对应的 KSP 2.1.20-1.0.32 为本次新增依赖，不升级 Kotlin/AGP/RN；版本依据见[Room官方发布记录](https://developer.android.com/jetpack/androidx/releases/room#2.7.2)、[KSP对应版本](https://github.com/google/ksp/releases/tag/2.1.20-1.0.32)。当前 schemaLocation 使用 KSP 参数导出；未来改表必须增加实际 Room Migration 和旧版本升级测试，不能删除 schema 或破坏性回退。

## 三种旧数据与失败恢复

旧文件为 app_default_pref，仅由 SharedPreferencesLegacyAccounts 读取或按白名单删键，不再向其中创建账号记录。

1. 新版SP加盐记录 `mock_auth_v1.*`：整批事务 INSERT OR IGNORE，Room成功提交后删对应SP键。已有Room记录优先。保留格式和salt，不再次派生、不改密码。
2. 较早版本完整 `mock_reg_username/password`、`mock_db_username/password`：MockAccountStore按原优先级将明文派生为校验值，再交给Room适配器事务写入，成功后才删除完整旧键对。mock_reg优先；Room或已导入新版记录始终优先。
3. 普通偏好和残缺旧键对：不清除，不猜测密码。已经被历史版本清除或覆盖的账号无法恢复。

| 中断点 | 保留事实与下次行为 |
| --- | --- |
| Room写入失败 | SP不删；请求失败，下次重试 |
| Room提交后进程终止，SP未删 | 两份仍在；下次主键冲突忽略，删旧SP后继续 |
| SP清理失败 | 请求失败并保留SP；Room记录不撤销、不覆盖；下次重试清理 |
| 旧明文派生/提交失败 | 不删完整旧键对 |
| Room损坏/版本不匹配 | 请求失败；不自动删库、不回退SP、不自动重建 |

两个介质没有分布式事务，不声称整体原子。账号迁移发生在首次认证访问，因此完成迁移前旧版本SP仍可能保留其原有明文；不会新增明文，也不会在启动阶段无条件删账号。仅记录迁移阶段/错误类别，不记录用户名、hash索引、校验值、密码或数据库内容。

## 源码导航

| 路径 | 职责 |
| --- | --- |
| [RoomStorage](../../android/lib_storage/src/main/java/com/entry/lib/stroage/room/RoomStorage.kt) | 构建、事务、受控存储Port |
| [StorageDatabase](../../android/lib_storage/src/main/java/com/entry/lib/stroage/room/StorageDatabase.kt) | 版本、schema和内部DAO |
| [RoomMockAccountStorage](../../android/app/src/main/java/com/entrytask/storage/RoomMockAccountStorage.kt) | Room接入与跨介质迁移顺序 |
| [LegacyAccountSource](../../android/app/src/main/java/com/entrytask/storage/LegacyAccountSource.kt) | SP仅作为迁移源 |
| [MockAccountStore](../../android/lib_net/src/main/java/com/entry/lib/net/mock/MockAccountStore.kt) | 规范化、查重、KDF、比较及旧明文转换 |
| [MainApplication](../../android/app/src/main/java/com/entrytask/MainApplication.kt) | 进程实例与依赖注入 |
| [AuthPersistenceTest](../../android/app/src/androidTest/java/com/entrytask/auth/AuthPersistenceTest.kt) | 分进程seed/verify，真实网络拦截注册/登录 |
| [RoomStorageTest](../../android/app/src/androidTest/java/com/entrytask/storage/RoomStorageTest.kt) | 真实数据库持久化/主键/并发/回滚/线程/旧SP |
| [RoomMockAccountStorageTest](../../android/app/src/test/java/com/entrytask/storage/RoomMockAccountStorageTest.kt) | 故障注入及迁移重试 |

## 验证

本轮已执行并通过（Debug、arm64-v8a、emulator-5554；不代表Release/iOS/其他ABI验收）：

```sh
# android目录：完整编译、账号相关单测、存储Lint
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :lib_net:testDebugUnitTest :biz_user:testDebugUnitTest :lib_storage:lintDebug -PentrytaskUiMode=native -PreactNativeArchitectures=arm64-v8a --max-workers=2

# 根目录：原始官方入口，加上本轮相关测试任务；复用已运行Metro
npx react-native run-android --no-packager --active-arch-only --extra-params=":app:assembleDebugAndroidTest :app:testDebugUnitTest :lib_net:testDebugUnitTest --max-workers=2"
npm run typecheck
npm run lint
npm run architecture:check
npm test -- --runInBand --watchman=false

# 保留安装（应用/测试APK均使用adb install -r），顺序启动独立测试进程
adb -s emulator-5554 shell am instrument -w -e class com.entrytask.auth.AuthPersistenceTest -e authPhase seed com.entrytask.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class com.entrytask.auth.AuthPersistenceTest -e authPhase verify com.entrytask.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class com.entrytask.storage.RoomStorageTest,com.entrytask.home.RnDashboardSmokeTest com.entrytask.test/androidx.test.runner.AndroidJUnitRunner
```

- 本地Android单测：app 27项（含新增5项迁移故障测试）、lib_net 7项、biz_user 13项，共47项通过，0跳过。
- Room真实设备测试5项：跨连接持久读取、并发主键冲突、触发器故障导致整批回滚、主线程访问拒绝、旧SP加盐记录及旧明文导入。最后与RN冒烟一起运行共6项通过（83.901s，含设备初始化、JS启动和无障碍查询；不是性能指标）。
- 分进程认证：seed 1项通过（3.852s）、verify 1项通过（13.623s）；真实Retrofit/Mock请求完成注册、错误密码401、正确登录200、重复注册409及原密码保留。新注册不写SP；直接SQL检查测试表只有索引及v1校验值。
- 官方CLI构建安装启动通过；真实ReactFragment/单Surface首页及Native Mock内容就绪。最终模拟器恢复保留安装Native构建。
- RN类型、Lint、架构及10套33项Jest通过。Watchman依当前环境禁用，未跳过用例。
- lib_storage Lint为0错误、10警告：依赖新版本提示（含所选Room固定版本）与既有StorageManager KTX建议；未通过升级无关依赖或抑制规则消除。
- KSP已生成Room实现与版本1 schema；文档本地链接和git diff --check通过。依赖新增未改npm锁文件、包名、RN入口、Codegen或Bridge签名。

未进行真实磁盘耗尽/硬件掉电、Release或iOS验证；存储失败/清理失败由单测替身注入，SQLite回滚由真实数据库触发器验证。所有设备用例使用独占测试数据库/SP文件；seed阶段保留测试库给下一进程verify，verify后删除测试文件；不清app_default_pref、不删除既有演示账号、不使用connected测试卸载应用。
