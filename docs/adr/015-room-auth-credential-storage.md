# ADR-015：Room 保存 Mock 认证校验记录

状态：采用。

## 决策

lib_storage 通过 RoomStorage、CredentialStore 封装 Room 数据库、查询和事务。app 的 RoomMockAccountStorage 实现网络持久化 Port，在 Application 组合根注入；lib_net 不直接依赖存储实现，biz_user 不操作 DAO。

注册与登录都经 Retrofit 和 MockInterceptor，拦截器承担模拟后台账号读写。entrytask_auth.db 版本1的 auth_credentials 表保存哈希 account_key 和加盐 PBKDF2 password_verifier；唯一主键与事务插入确保重复注册不覆盖原密码，存储失败向请求层反馈。

Native 会话仅在内存，账号库不作为自动登录凭证；普通偏好及原型签到账本保持各自边界。Room 结构升级必须显式 Migration 并保留 schema，不进行破坏式重建。

## 取舍

Room 提供事务、唯一性和受控查询，并非加密容器或生产银行认证方案。接入真实服务须另外设计会话安全协议与凭证管理，不将客户端模拟账号库继续作为后端权威。

[Room存储设计](../development/Room认证存储设计.md) · [认证方案](../design/登录注册技术方案.md)
