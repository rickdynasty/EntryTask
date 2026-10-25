# Room 认证存储设计

## 数据边界

认证 Mock 使用 Native Room 保存账号校验记录；当前登录会话在 UserAuthManager 内存中，普通偏好通过 StorageManager 管理。账号存在、已登录、当前表单内容分别由不同所有者管理，不能互相替代。

| 项目 | 当前实现 |
| --- | --- |
| 模块与包 | `android/lib_storage`，包名 `com.entry.lib.stroage.room` |
| 数据库 | `entrytask_auth.db`，StorageDatabase，版本 1 |
| 表 | `auth_credentials` |
| 主键 | `account_key`：`mock_auth_v1.` + 用户名 trim 后 SHA-256 十六进制摘要 |
| 校验字段 | `password_verifier`：版本、算法、次数、随机盐、派生值 |
| 敏感数据边界 | 不保存明文用户名、密码、Token；不向 RN 暴露 DAO 或校验记录 |

## 封装与依赖

```text
biz_user：AuthRepository → Retrofit 请求
lib_net：MockInterceptor → MockAccountStore → MockAccountStorage Port
app：RoomMockAccountStorage（Port 的实现）
lib_storage：RoomStorage → CredentialStore → DAO → StorageDatabase
```

`app` 在组合根注入存储实现，网络层依赖 Port，不通过业务 UI 获取数据库。RoomStorage 提供进程内默认数据库与测试命名库；生产库按需创建，首次认证 IO 在网络后台线程打开。lib_storage 负责结构、事务与访问能力，不判断登录成功、不启动页面。

CredentialStore 提供查询与 insertIfAbsent。批量插入在事务内使用 INSERT IGNORE，返回实际新增 key 集合；主键提供最终唯一性保证。异常向上抛出，不能把存储失败解释为注册成功。测试使用独立数据库名并关闭、清理自己的库。

## 认证校验

MockAccountStore 归一用户名并计算 key，密码保持原样。每个账号使用 16 字节随机盐、120000 次 PBKDF2 和 32 字节派生值；优先 SHA-256，旧 API 支持场景使用现有 SHA-1 分支。记录携带算法信息，比较采用常量时间方式。

注册经 MockInterceptor 创建记录，写完后才返回模板响应；重复注册返回冲突。登录读取同一存储并验证派生值。RoomMockAccountStorage 校验 key 格式并将业务 Port 映射为 CredentialStore；不维护第二份账号事实。

事务提交后即使请求被取消，已经写入的账号仍可能存在。因此取消不等于回滚完成，不能自动重放注册请求。

## 线程、版本与维护

Room 查询、写入和密码派生不在主线程执行。当前认证调用由 OkHttp 后台拦截链承载；其他业务接入 suspend API 也必须核实阻塞操作的 Dispatcher，不能认为 suspend 自动切线程。

当前不启用 allowMainThreadQueries 或 destructive migration。结构版本变化需提交 schema、显式 Migration 并验证升级后数据；算法版本变化也需单独定义可读写策略。数据库打开失败、查询异常和写失败必须向请求层反馈，不清账号库掩盖问题。

普通偏好与演示引擎的轻量账本各有存储边界，接入 Room 不表示所有 Mock 状态都已数据库化。

## 验证与阅读

验证注册后同库登录、重复插入不覆盖、错误密码、并发唯一性、存储异常、进程结束后重开及数据库升级。涉及真实设备时保护正式演示账号，不卸载或清应用数据。

- [lib_storage 使用说明](../../android/lib_storage/README.md)
- [Room 新手指南](Room新手使用指南.md)
- [登录注册技术方案](../design/登录注册技术方案.md)
