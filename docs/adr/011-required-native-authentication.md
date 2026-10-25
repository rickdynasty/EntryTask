# ADR-011：Native 提前认证与 RN 默认门禁

状态：采用。

## 决策

Native 是会话权威。MainActivity 创建时经 VM 读取会话，未认证则在 RESUMED 拉起 Native AuthActivity；正常流程保留 singleTask MainActivity。认证成功先原子提交会话，再返回结果，MainActivity 重读快照。

RN 根页和业务子页默认保护，仅公开白名单 HelpCenter 例外。RN 通过 NativeSession 获取快照，通过 Host command 请求认证；命令 ACK 不代表成功。认证展示防重，flowId 隔离重入/迟到取消，epoch 隔离旧会话结果。

取消或展示失败后不循环自动拉登录，公开帮助及显式重试仍可使用。登录根页返回先隐藏键盘，再双击退出整个任务；注册返回登录，不暴露未认证首页。

## 取舍

Native 提前认证避免等待 RN 初始化，RN 门禁继续保护后续导航。两层复用同一会话与认证入口，不维护两套登录事实。系统仍可能回收进程，恢复必须重新确认会话。

[登录注册技术方案](../design/登录注册技术方案.md) · [启动源码详解](../development/启动与登录全链路源码详解.md)
