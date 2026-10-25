# ADR-013：登录根页退出与本地认证账号持久化

日期：2026-10-22。状态：已实施；补充并修订 [ADR-011](011-required-native-authentication.md) 的用户返回交互，保留其会话/Bridge取消协调。

> 2026-10-25：账号SP持久化与迁移部分已由 [ADR-015](015-room-auth-credential-storage.md) 替代；本文保留为历史决策，返回和内存会话规则继续有效。

## 决策

- 登录根页返回先收起IME；无IME时第一次提示，2秒内再次返回退出整个同affinity任务，不关闭认证页后展示首页。注册页系统返回由NavHost回到登录；切页/进入后台重置退出计时。
- 双击退出时清Native内存会话并递增epoch；Repository在提交会话前检查取消并原子核对发起时epoch，拒绝迟到认证成功回写。
- AuthActivity退出整个任务前用 `setResult(RESULT_CANCELED)` 清除结果数据，再 `finishAffinity()`；否则Android拒绝携带Activity Result的finishAffinity。正常认证成功仍携带flowId，只finish认证页，保留singleTask MainActivity。
- 程序性取消、宿主失败与singleTask清除仍按原协议处理；不删RN页面门禁或迟到结果隔离，也不新增runtime。
- Native认证Mock使用manifest精确method/path和已有分API JSON模板，Native内部错误码适配为数字400/401/409/500，与UserService DTO一致；不修改RN错误契约。
- 注册与登录由同一MockAccountStore读写 `app_default_pref`。账号按规范化用户名SHA-256索引，独立保存版本化盐/算法/迭代次数/密码校验值；一次后台commit成功后才返回注册成功。重复账号409，不再覆盖其他账号或原密码。
- 注册、登录都trim用户名、保留密码原样。新记录不保存明文密码；优先PBKDF2-HMAC-SHA256，Provider不支持时使用兼容minSdk24/25的SHA1版，120000次、16字节随机盐、32字节校验值，常量时间比较。
- 两版旧 `mock_reg_*`、`mock_db_*` 账号在认证后台访问时迁移；写入校验记录与删除完整旧键对在同一次commit。当前mock_reg优先；已有新版记录优先；不清其他SP。启动只清旧会话Token，不删除注册账号键。

## 范围与取舍

账号持久化不等于会话持久化。进程重启后仍需登录，登录时经Native拦截器读取SP校验；RN不读取账号库或凭证。

这是对当前SP演示认证的渐进修复，尚未实施技术方案目标的Room、Keystore封装、完整SessionCoordinator或生产后端认证。KDF参数与目标方案不同，后续升级必须设计版本迁移，不通过清数据恢复。完整旧键对才迁移，缺失一半或已被早期版本删除的数据无法自动重建。

实施过程、命令与验证边界见 [修复记录](../development/认证返回与账号持久化修复.md)。
