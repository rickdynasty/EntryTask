# ADR-012：Native 登录首帧后预热唯一 RN 引擎

日期：2026-10-21。状态：已实施；性能证据见 [预热性能验证](../development/rn-warmup-performance.md)。

实施步骤、命令输出、调整记录与代码清单见 [实施全过程与变更记录](../development/RN引擎预热实施全过程与变更记录.md)。

## 决策

- MainApplication 注册 AuthWarmupLifecycle，观察 Native AuthActivity 的首个提交帧；下一次动画回调才调用 RnWarmupController，复用 MainApplication.reactHost.start()。不在 Application.onCreate 或 MainActivity.onCreate 启动引擎。
- API29以上硬件绘制使用 FrameCommitCallback；它表示帧已提交到交换链，不等于物理显示。旧API/软件绘制退化为 draw traversal 后调度，并在诊断中标记区别。
- 不创建 ReactFragment/Surface，不调用 RN bootstrap、不构造业务 Store/VM、不预取首页 API；src/index.js 仍只注册 EntryTask，业务图在 App 挂载后创建。
- 预热每进程最多启动一次。已有页面请求引擎时不再预热；Native-only 首页、低RAM设备或已收到内存压力信号时跳过。认证暂停/销毁撤销尚未执行的帧监听和预热回调。
- 已经启动的共享引擎不随认证取消、Activity暂停或观察超时销毁；后续页面复用同一 Host。观察公开 TaskInterface 的轮询为可挂起、有界的25ms间隔/30秒上限，不阻塞主线程、不自动重试。
- RN默认Release异常回调会抛出错误；只在“预热已开始且尚无Surface请求”的范围隔离异常并记录安全错误码，保住Native认证。页面接管后恢复宿主原异常语义，不能用预热吞掉业务运行错误。RN框架负责失败实例的销毁/重建。

## 开关与验证边界

Gradle `entrytaskRnWarmup` 控制 BuildConfig.RN_WARMUP_ENABLED，默认false；本轮模拟器收益波动较大，未满足默认启用依据。可用true启用预热，false构建同变体对照。该开关不受外部Intent、账号或持久设置控制。`entrytaskTestBuildType=release` 与 `entrytaskPerformanceTests=true` 只用于显式构建性能测试APK，不新增产品flavor或第二个runtime。

成本是游客认证期间提前占用引擎内存；暂停后不会为了释放这部分内存破坏单宿主生命周期。性能结论限定设备/变体/样本，不外推到真机、完整账号输入、业务Mock加载完成或生产指标。后续在真机补齐收益、内存和输入帧数据后再决定默认开启；不通过切换网络或改变认证门禁补偿。
