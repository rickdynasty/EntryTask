# ADR-012：先以 XML 完成 Shell MVVM，再独立转换 Compose

日期：2026-10-21。状态：采纳；第一阶段实施，Compose 延期。

## 背景

MainActivity 同时承担 XML 渲染、会话、导航状态与 Bridge 命令。先前将 MVVM、混合宿主适配与 Compose 一起改动，扩大了 UI 转换的评审和回归范围。当前按用户要求分阶段执行。

## 决策

第一阶段保留 activity_home.xml、BottomTabBar、稳定根 Fragment tag 和 RN 集成，以 MainShellView 纯渲染只读 MainUiState。MainViewModel 管状态/动作，ShellRepository 是 Tab/depth/revision 唯一来源，NativeSessionPort 适配 UserAuthManager 会话权威。

Bridge 只依赖类型化契约，通过可撤销前台端点与 VM 协调认证；Activity Result、生命周期、Insets、返回和单宿主恢复在 XML 阶段完成。物理系统安全区由 Native 宿主预留，RN 读取裁剪视口的剩余 Insets。

第二阶段只替换 UI/互操作层：可复用 VM/Repository/Bridge 协议和测试，不重新设计认证与会话。尚未启用首页 Compose、AndroidFragment 或并行双渲染器，认证模块原有 Compose 保留。

## 后果与边界

可独立验证“架构改造”与“UI技术替换”的收益和风险。当前不删除稳定 XML、不增加首页 Compose 依赖，未来转换必须另外验收 tag 恢复及安全区。

新进程不恢复凭证或私有子页；普通认证往返保留 MainActivity，系统仍可回收它。未新增跨进程认证任务和返回 ACK 协议。

实现与本次验证见[分步方案](../design/Android首页Compose与MVVM改造方案.md)，源码调用链见[启动与登录](../development/启动与登录全链路源码详解.md)。
