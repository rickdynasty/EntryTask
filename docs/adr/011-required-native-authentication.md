# ADR-011：Native 会话驱动全应用登录门禁

日期：2026-10-19。状态：已按用户要求采用；源码已落地，设备场景待验收。

## 背景

V1.1 需求和技术方案采用游客浏览、受保护操作按需认证的默认假设。用户随后明确要求：RN 首页和所有子页面仅在登录后可访问；RN 查询登录状态与打开登录页必须经 Native Bridge；MainActivity 保持 singleTask，认证期间留在栈内，成功只关闭认证页。

## 决策

1. Native 持有认证事实和 epoch；RN 启动调用 NativeSession.getSnapshot，并监听 NativeHost Shell 事件。会话响应和事件均不得使 epoch 回退。
2. 未认证不挂载 RN 业务导航/资源订阅，Native 根底栏隐藏。RN 门禁经 NativeHost authenticate 请求展示 AuthActivity；命令 ACK 不表示认证成功。
3. Activity Context 使用普通 Intent 打开认证页，不附加 NEW_TASK/CLEAR_TOP；成功提交完整会话后只 finish 认证页。MainActivity 恢复时发布快照，RN 放行业务。
4. 取消返回登录门禁，允许显式重试；不循环弹认证页。退出清会话、清私有查询、重置导航后再次进入门禁。
5. 进程内会话有效，进程终止后重新登录。系统仍可回收 Activity；正常业务流程不主动销毁 MainActivity，不承诺跨进程保留同一对象。

## 影响与范围

旧文档的“公开页面/游客模板”不再代表可绕过登录；API requiresAuth 策略不随页面门禁机械改写，Native 受保护能力仍独立鉴权。RN 继续使用同一个 runtime。Native-only 文本首页是调试分支，本决策的业务验收使用 RN 模式；iOS 原生能力仍延期，不通过 JS 假登录适配。

当前 MainActivity 防重标记仅用于正常前台展示；完整 Activity Result、认证任务恢复、authFlowId 和 SessionCoordinator 状态机为后续设计，不把它们描述为已实现。

## 验证

以 [Android 技术方案 §5](../design/Android%20技术方案.md) 的冷启动、取消、失败、退出、乱序快照、singleTask 重入和进程恢复矩阵验收；分别记录静态检查、行为测试、Native 编译与设备结果。下载与 AppGlobals 的可靠性改进另按技术方案跟进。
