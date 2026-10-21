# ADR-011：Native 提前认证与 RN 默认页面门禁

日期：2026-10-19；修订：2026-10-21。状态：第一步已实施；后续引擎预热由 [ADR-012](012-rn-engine-warmup.md) 扩展。

## 背景

首次进入 MainActivity 应立即检查 Native 会话，未登录直接打开认证页，不再等待 RN Bundle、bootstrap 和页面 effect。后续 RN 页面默认需要认证，帮助中心是明确的公开例外。本修订取代先前“所有页面由 RN 全局门禁自动请求登录”的决定。

## 决策

1. MainActivity.onCreate → MainViewModel.onHostCreated → ShellRepository.refreshSession 读取 Native 内存权威；初次游客产生 Requested。Activity 仅在 RESUMED 确认并启动 AuthActivity。首次认证不经过 Bridge，也不创建新的 HomeFragment/ReactFragment。
2. 首次认证返回或启动失败后，允许挂载唯一 RN Surface；成功进入业务，取消显示门禁和帮助入口。已有 Fragment 按稳定 tag 恢复复用，不主动拆除。系统恢复的片段可能早于校验重建，因此 RN 页面门禁始终独立生效。
3. onResume 每次校准会话，不无条件发起认证。Requested/InFlight/Failed/Canceled 与首次检查标记、流程序号保存于 SavedStateHandle，只表示 UI 协调，不持久化登录事实。取消不自动重弹，手动重试建立新流程。已认证的受保护页面失去会话时，RN 请求认证；公开帮助页不会因此强制登录。
4. RN 导航容器可在游客态存在。RootRoute 在业务模型创建前门禁；DetailRoute 通过 routeAccess.ts 的集中策略默认鉴权，仅 helpCenter 公开。历史 Target.requiresAuth=false 不能绕过页面策略。受保护子页进入时经 HostPort/Bridge 请求一次认证，取消后停留门禁，成功后依 Native 会话放行原目标；不自动重放写操作。
5. ForegroundHostConnection 仅在 MainActivity RESUMED 时持有可撤销动作。认证展示期间仅保存流程编号，合并重复 Bridge 请求；其他没有前台端点的请求仍返回 HOST_NOT_READY。命令 ACK 不代表认证成功。
6. AuthActivity 成功先建立完整 Native 会话，再 setResult/finish；取消和成功结果均回传调用方流程编号。singleTask 重入可能移除上层认证页，MainViewModel.onNewEntry 建立新请求，迟到旧结果不能撤销新流程；系统清除可能返回空 data，单独记录并消费被替换的旧取消通知。流程编号在进程内不因新 VM 复用，旧 Activity 销毁只清理自己对应的编号。
7. MainActivity 保持 singleTask；普通认证往返不 finish 首页、不加 NEW_TASK/CLEAR_TOP，不新增 RN runtime。进程会话终止后失效；不承诺跨进程对象保活，完整进程恢复仍需专项设备验收。

## 边界

公开帮助页是本地说明，不请求个人数据。页面门禁不能替代 Network/Storage Bridge 的独立鉴权与 epoch 隔离。首次认证取消后才挂载 RN，因此 Metro 不可用不会阻止 Native 登录，但会影响后续 RN 门禁和公开帮助展示。

第一步不调用 ReactHost.start() 预热；第二步只增加引擎预热，始终不提前查询首页业务数据，不改 iOS 原生实现或 Codegen 签名。未来 iOS 应采用同等宿主提前认证与页面策略，不能用 JS 假登录适配。

## 验证

覆盖冷启动提前认证、已有会话、取消与手动重试、公开帮助、默认保护未知路由、命令 ACK 不放行、流程防重、迟到结果、singleTask 重入、Fragment/Activity 恢复及同一宿主往返。验证证据与尚未覆盖的场景见 [Android 技术方案 §5](../design/Android%20技术方案.md)。
