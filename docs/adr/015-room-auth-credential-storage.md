# ADR-015：认证账号迁移到 Room

日期：2026-10-25。状态：已实施，验证见[实施说明](../development/Room认证存储实施说明.md)。本决策替代 ADR-013 的 SP 账号持久化部分，不修改其认证返回、内存会话和迟到结果隔离约束。

## 决策

1. 按当前用户要求，在 lib_storage 实施 Room 的构建、实体、DAO、事务与受控 Port。仅持有技术凭据校验表，不加入银行业务规则、会员表、RN 或网络依赖。应用持有进程级数据库实例，首次认证请求在后台打开数据库。
2. 注册/登录仍经 UserService→MockInterceptor→MockAccountStore。拦截器拥有模拟后台写入事实，Repository 只提交认证结果和 Native 内存会话，不另写账号库。实际生产后端接入时应移除客户端认证账本，不双写本地权威账号。
3. app 的 RoomMockAccountStorage 连接网络侧持久化 Port 与存储侧 CredentialStore；MainApplication 组装并注入拦截器。NetManager 不再自行定位 Application/存储，移除 lib_net→lib_storage 依赖。没有扩大 RN Bridge 或新增 runtime。
4. 新文件 entrytask_auth.db、版本1、表 auth_credentials，唯一主键 account_key 延续 mock_auth_v1.<用户名SHA-256>，password_verifier 保留现有 v1 KDF 格式。首次导入和新注册使用 Room 事务及主键约束，冲突不覆盖。PBKDF2 参数不在本次静默升级，无明文密码或 Token 新增落盘。
5. 迁移先提交 Room，再删除旧 SP 键。两个介质无法原子提交：中间崩溃时重试导入，Room 已有记录优先；数据库失败保留 SP，清理失败保留持久记录和 SP 以重试，不回退到 SP 校验，也不清其他偏好。完整 mock_reg/mock_db 明文键对先经既有 KDF 转换再写入 Room；不把明文导入数据库。
6. 本次保留其他普通偏好使用 SP，以及现有 demo_engine 签到账本；不顺带迁移全部数据。§9 目标 demo_server 数据库仍未全面实施。独立认证数据库不能被公开缓存清理或退出登录删除。

## 取舍

Room 不是加密容器；本次迁移改善事务、唯一性和查询边界，未实现 Keystore、完整生产会话协议或生产认证参数升级。schema 从版本1开始导出，后续升级必须显式迁移，禁止破坏性重建。跨介质失败只能保证可重试/不丢已提交账号，不能声称 SP 与 SQLite 同一事务。
