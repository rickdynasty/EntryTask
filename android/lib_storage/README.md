# lib_storage

保留既有 `com.entry.lib.stroage` 包名。普通偏好继续使用 `StorageManager`；新增 Room 封装用于认证 Mock 的持久校验记录，不开放任意数据库/凭据给 RN。

## Room 封装

- `room/RoomStorage.kt`：构建和关闭数据库、暴露 `CredentialStore`，隔离 Room/DAO 实现。
- `StorageDatabase`：数据库版本 1，默认文件 `entrytask_auth.db`；由 Application 的 lazy 属性持有唯一实例，页面不得自行构建。
- `CredentialEntity` / `CredentialDao`：`auth_credentials` 表，`account_key` 唯一主键、`password_verifier` 校验记录。没有用户名、明文密码、Token 列。
- `CredentialStore.read`：按主键读取。`insertIfAbsent`：整批事务写入，返回本次新增主键；冲突忽略，绝不覆盖原校验值。数据库错误抛出，不假装成功。

```kotlin
// Application组装时持有；实际SQL由OkHttp后台请求触发。
private val authDatabase by lazy { RoomStorage.open(this) }
// 在后台线程或withContext(Dispatchers.IO)中调用：
val created = key in authDatabase.credentials.insertIfAbsent(mapOf(key to verifier))
val persisted = authDatabase.credentials.read(key)
```

以上 `key` 必须由调用方生成用户名哈希索引，`verifier` 必须为已派生的校验记录；基础库不负责密码派生或认证决定。当前同步 Port 用于同步 OkHttp 拦截器，禁止 UI 主线程调用，未启用 `allowMainThreadQueries`。独立实例/测试可指定不含路径的 `.db` 文件名，并由拥有者 `close()`；正式进程实例在进程存活期间保留。

## 依赖和升级

使用 Room 2.7.2 与 Kotlin 2.1.20 对应的 KSP 2.1.20-1.0.32，不升级项目 Kotlin/AGP/RN。KSP 导出 [版本1 schema](schemas/com.entry.lib.stroage.room.StorageDatabase/1.json)，只提交 schema，不提交 build 下的生成实现。

这是第一个 Room 版本；SP→Room 是应用适配层的数据导入，不是 SQLite 的 0→1 Migration。未来改表必须：保留旧 schema、升版本、增加显式 `Migration` 并注册到 builder、补充真实旧数据库升级测试；不使用 `fallbackToDestructiveMigration`。当前未引入数据库加密/Keystore，不把 Room 或密码哈希等同于生产认证方案。

认证接入、幂等迁移与设备验证见 [Room认证存储实施说明](../../docs/development/Room认证存储实施说明.md)。

入门学习见 [Room新手使用指南](../../docs/development/Room新手使用指南.md)，覆盖基础概念、分层、封装、普通业务接入、迁移、测试与排障。
