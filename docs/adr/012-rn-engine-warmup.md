# ADR-012：认证首帧后预热唯一 RN 引擎

状态：能力采用，默认关闭。

## 决策

AuthWarmupLifecycle 观察 Native AuthActivity 首个提交帧，下一动画回调调用 RnWarmupController.afterAuthFrame，复用 MainApplication.reactHost.start()。API29以上硬件绘制使用 FrameCommitCallback，其他路径记录 draw traversal 口径。

预热不创建 Surface、不 bootstrap、不构造 Store/VM、不请求首页。每进程最多启动一次，页面已需要引擎、Native模式、低RAM或内存压力时跳过；认证暂停/销毁撤销尚未执行的回调。已启动的共享Host不随取消或观察超时销毁。

仅在预热已开始且页面尚未接管时隔离异常并记录；页面接管后遵循原宿主异常语义。READY只代表公开start任务完成，不等于JS执行完毕或业务可交互。

## 取舍与验证

Gradle entrytaskRnWarmup 控制开关，默认false。成本为认证期间提前占用内存；是否启用需目标真机同变体A/B测量，包含返回首页帧、认证输入流畅性、内存及恢复，不以旧版本模拟器结果作通用收益承诺。

[实现与测量方法](../development/rn-warmup-performance.md)
