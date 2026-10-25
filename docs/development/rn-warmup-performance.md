# RN 引擎预热实现与性能验证

仅预热引擎与Bundle，保持Native提前认证和RN页面门禁。[决策与边界](../adr/012-rn-engine-warmup.md)。

## 实现调用链

`MainApplication.onCreate → registerActivityLifecycleCallbacks → AuthWarmupLifecycle.onActivityResumed → DecorView附着 → 首帧提交 → 下一动画回调 → RnWarmupController.afterAuthFrame → 唯一ReactHost.start()`。

首帧观察和延迟回调属于当前认证Activity，暂停/销毁即清理。READY仅表示RN公开start任务完成（实例初始化、Bundle已调度），不等于JS执行、bootstrap或业务页面绘制均已完成；框架JS队列仍可能继续执行。引擎与有界观察属于进程；已开始后即使登录取消也不销毁Host。MainActivity.bindRootHost先标记surfaceWillAttach，避免页面已加载时再开始预热，并界定异常隔离范围。

源码：[生命周期适配](../../android/app/src/main/java/com/entrytask/shell/runtime/AuthWarmupLifecycle.kt)、[控制器](../../android/app/src/main/java/com/entrytask/shell/runtime/RnWarmupController.kt)、[Application组装](../../android/app/src/main/java/com/entrytask/MainApplication.kt)。调试日志tag为 `EntryTask.RNStartup`，阶段包含running/ready/failed/canceled/timed_out/skipped_memory，记录阶段与耗时，不记录认证载荷。

## 测量口径

- 同设备、同Release演示配置、arm64-v8a、Hermes、内置Bundle，R8保持工程既有关闭状态。Release仍使用演示签名，不能据此声称生产发布验收。
- 两组仅 `entrytaskRnWarmup=true/false` 不同；每个样本独立instrumentation进程启动，AB/BA轮换；不清应用数据、不注册账号。
- instrumentation先创建Application，再从请求启动MainActivity开始计时到认证首帧提交。这不是包含Application初始化的完整用户冷启动时间；两组均无预先启动的RN引擎。
- 认证首帧后以相同目标停留（3000ms或补充的8000ms）计时，再用测试内存会话模拟Native登录成功并关闭AuthActivity。不因预热READY提前或延后放行；提交前的宿主检查、主线程调度和PSS采样还会产生额外耗时，8000ms场景另记录actualDwellMs。本口径不含输入和真实密码认证，也不用于证明输入流畅性。
- 登录返回到首页耗时：从测试提交会话/请求关闭认证，到应用内视图树确认RN首页“搜索”控件已布局后下一次帧提交。包含Activity恢复、Surface创建、bootstrap和25ms轮询观测开销；强制一次绘制后用FrameCommitCallback记录提交时间，是首页提交帧的上界代理，不等于物理屏幕呈现或可交互时间。本用例要求API29以上硬件绘制。
- 首页数据仍经Native Mock，人工随机延迟1～3秒。本实验不等待业务数据完成，不能把随机Mock耗时计为引擎收益。
- 认证阶段PSS：固定停留后、登录返回前调用Debug.getMemoryInfo的进程总PSS，含instrumentation开销；只是同一测试工具下的内存对比，不等于引擎独占内存或峰值。

## 复现命令

先在android目录构建两份APK，分别另存，避免后一次构建覆盖前一次。默认命令仍为根目录 `npx react-native run-android`，预热默认关闭。显式启用使用下方Gradle参数；不改变包名、入口或组件注册。

```sh
./gradlew :app:assembleRelease -PentrytaskUiMode=rn -PentrytaskRnWarmup=false -PentrytaskTestBuildType=release -PreactNativeArchitectures=arm64-v8a --max-workers=2
cp app/build/outputs/apk/release/app-release.apk /tmp/entrytask-warmup-baseline.apk
./gradlew :app:assembleRelease :app:assembleReleaseAndroidTest -PentrytaskUiMode=rn -PentrytaskRnWarmup=true -PentrytaskTestBuildType=release -PentrytaskPerformanceTests=true -PreactNativeArchitectures=arm64-v8a --max-workers=2
cp app/build/outputs/apk/release/app-release.apk /tmp/entrytask-warmup-enabled.apk
```

回到根目录，serial以 `adb devices -l` 为准；以下会交替覆盖安装上述演示包并运行专用测试，不清数据。结束后可用官方CLI装回默认Debug包。

```sh
python3 tools/benchmark-rn-warmup.py --serial emulator-5554 --baseline-apk /tmp/entrytask-warmup-baseline.apk --warmup-apk /tmp/entrytask-warmup-enabled.apk --test-apk android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk --runs 5 --output /tmp/entrytask-warmup-results.json
```

性能用例放在 `src/benchmark/java`，只有显式打开entrytaskPerformanceTests才加入androidTest源集；不会混进常规功能回归。每次采样要求新进程，否则直接失败。正式采样不剔除目标停留内尚未READY的慢样本；记录当时阶段。补充场景用 `--runs 3 --dwell-ms 8000`，输出到另一文件，不能混合统计。结果通过instrumentation状态返回，不写账号或业务数据。

## 评估与启用条件

预热默认关闭。每次评估独立记录设备、系统、ABI、构建变体、样本数、原始数据、中位数和分布，不删慢样本；不使用过去版本的测量替代当前包验证。

收益需与认证阶段PSS、峰值内存、输入帧抖动、低端机表现及系统回收恢复一起评估。READY不等于业务可交互，Mock随机延迟不能算成引擎收益。只有在目标真机上稳定改善目标指标且成本可接受时，才考虑修改默认值。

功能回归还需验证：认证取消/后台不销毁共享Host、Native模式不预热、页面接管后异常语义正常、无第二runtime、Bundle求值不初始化业务图。
