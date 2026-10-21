# RN 引擎预热实现与性能验证

日期：2026-10-21。本轮只预热引擎与Bundle，保持Native提前认证和RN页面门禁。[决策与边界](../adr/012-rn-engine-warmup.md)。

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

先在android目录构建两份APK，分别另存，避免后一次构建覆盖前一次。默认命令仍为根目录 `npx react-native run-android`，预热默认关闭。显式启用可运行 `npx react-native run-android --extra-params=-PentrytaskRnWarmup=true`；不改变包名、入口或组件注册。

```sh
./gradlew :app:assembleRelease -PentrytaskRnWarmup=false -PentrytaskTestBuildType=release -PreactNativeArchitectures=arm64-v8a --max-workers=2
cp app/build/outputs/apk/release/app-release.apk /tmp/entrytask-warmup-baseline.apk
./gradlew :app:assembleRelease :app:assembleReleaseAndroidTest -PentrytaskRnWarmup=true -PentrytaskTestBuildType=release -PentrytaskPerformanceTests=true -PreactNativeArchitectures=arm64-v8a --max-workers=2
cp app/build/outputs/apk/release/app-release.apk /tmp/entrytask-warmup-enabled.apk
```

回到根目录，serial以 `adb devices -l` 为准；以下会交替覆盖安装上述演示包并运行专用测试，不清数据。结束后可用官方CLI装回默认Debug包。

```sh
python3 tools/benchmark-rn-warmup.py --serial emulator-5554 --baseline-apk /tmp/entrytask-warmup-baseline.apk --warmup-apk /tmp/entrytask-warmup-enabled.apk --test-apk android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk --runs 5 --output /tmp/entrytask-warmup-results.json
```

性能用例放在 `src/benchmark/java`，只有显式打开entrytaskPerformanceTests才加入androidTest源集；不会混进常规功能回归。每次采样要求新进程，否则直接失败。正式采样不剔除目标停留内尚未READY的慢样本；记录当时阶段。补充场景用 `--runs 3 --dwell-ms 8000`，输出到另一文件，不能混合统计。结果通过instrumentation状态返回，不写账号或业务数据。

## 实测与限制

设备：Pixel_10 Android17/API37模拟器，arm64-v8a；同宿主共享负载，未控制OS页缓存、温度或其他进程，保留全部样本，不删除离群值。所有认证帧均使用硬件FrameCommit。两份Release包均检查包含Bundle、43个Mock资源文件及36个契约文件。

3秒目标停留，每组5个独立进程，按AB/BA顺序交替。中位数与全样本范围如下：

| 指标 | 关闭预热 | 开启预热 |
| --- | --- | --- |
| MainActivity请求到认证提交帧 | 8268ms（7491～8532） | 8814ms（6666～9870） |
| 登录返回到首页布局后提交帧 | 6181ms（5936～8424） | 5755ms（5524～8607） |
| 认证阶段进程PSS | 62.9MiB（59.2～63.5） | 69.6MiB（65.9～72.5） |
| 返回时start任务已READY | 不适用 | 1/5；其余4次RUNNING |

首页指标中位数减少426ms（6.9%），但分布明显重叠；PSS中位数增加6.6MiB。认证帧发生在预热启动之前，不能把组间首帧差异解释为预热的因果效果；它也说明当前环境噪声较大。

预实验曾使用Accessibility树查询，存在明显RPC等待；该组结果未纳入上表。正式指标改为应用内布局检查后的下一帧提交；不能把这次测量修正误称为应用性能优化。

补充8秒目标停留，每组3个独立进程，单独统计：

| 指标 | 关闭预热 | 开启预热 |
| --- | --- | --- |
| MainActivity请求到认证提交帧 | 8689ms（7754～8802） | 7697ms（7471～7711） |
| 登录返回到首页布局后提交帧 | 5140ms（3576～7253） | 4945ms（4685～5771） |
| 认证阶段进程PSS | 63.1MiB（55.9～63.2） | 68.1MiB（67.9～71.6） |
| 返回时start任务已READY | 不适用 | 3/3；任务完成耗时2075～3570ms |

首页指标中位数减少195ms（3.8%），PSS中位数增加5.0MiB；实际停留（含提交前检查/采样开销）8045～8096ms。READY只对应公开start任务完成，不能据此断言JS队列和业务初始化都已完成。

**决策：预热能力保留，默认关闭。** 两种场景没有表现出足够稳定的收益，内存增加则可观测；不能把中位数的小幅变化当成已证实的性能优化。用构建开关显式开启后可继续在真机验证，补齐输入帧抖动、峰值内存和低端机数据，再决定默认启用。不将当前模拟器数秒级启动结果外推为真机指标。

前述16个正式样本全部成功；无失败/跳过，无离群值删除。真机长时输入、帧抖动、峰值内存、低端机和系统回收恢复需后续专项验证。

### 全部正式样本摘要

下表保留各组所有样本；PSS为总进程KiB。原始输出文件属于本机临时验证产物，不提交设备日志或账号资料。

| 目标停留ms | 轮次 | 预热 | 认证帧ms | 返回首页帧ms | PSS KiB | 返回时任务阶段 |
| --- | --- | --- | --- | --- | --- | --- |

| 3000 | 1 | 关 | 7491 | 6551 | 64995 | IDLE |
| 3000 | 1 | 开 | 9592 | 7305 | 67463 | RUNNING |
| 3000 | 2 | 开 | 8814 | 5722 | 68818 | RUNNING |
| 3000 | 2 | 关 | 8268 | 6009 | 64449 | IDLE |
| 3000 | 3 | 关 | 8318 | 8424 | 64656 | IDLE |
| 3000 | 3 | 开 | 6666 | 5524 | 74250 | RUNNING |
| 3000 | 4 | 开 | 9870 | 8607 | 71240 | RUNNING |
| 3000 | 4 | 关 | 7714 | 6181 | 61545 | IDLE |
| 3000 | 5 | 关 | 8532 | 5936 | 60581 | IDLE |
| 3000 | 5 | 开 | 7648 | 5755 | 73353 | READY |
| 8000 | 1 | 关 | 8689 | 3576 | 64659 | IDLE |
| 8000 | 1 | 开 | 7697 | 5771 | 69496 | READY |
| 8000 | 2 | 开 | 7471 | 4685 | 69782 | READY |
| 8000 | 2 | 关 | 7754 | 7253 | 57201 | IDLE |
| 8000 | 3 | 关 | 8802 | 5140 | 64666 | IDLE |
| 8000 | 3 | 开 | 7711 | 4945 | 73331 | READY |


## 验证与追踪

- RN：typecheck、lint、architecture:check通过；Jest 9套/26项通过，包含入口加载不创建业务图、不调用Native握手的回归；Watchman受本机状态目录限制，使用`--watchman=false`。
- Native：app 22项、biz_user 5项单元测试通过；Codegen、Debug/Release构建与Debug Lint通过。开启预热执行6项认证/宿主设备回归，另对最后补充的恢复Fragment需求标记定向复验1项，通过。它们使用内存测试会话，不改已有账号。
- 官方CLI：默认配置与`--extra-params=-PentrytaskRnWarmup=true`均完成构建、安装、启动。开启时观察到先Native认证、后warmup.running/ready；默认配置最终装回Debug。
- 本轮仅验证API37模拟器及arm64-v8a。未完成真机、其他ABI、iOS、完整跨进程恢复、真实账号输入和输入帧率/峰值内存验证。
- 性能采样后只补充了恢复Fragment时的真实页面需求标记；该恢复分支没有命中冷启动性能样本。表中性能只对应下列采样APK，未来发布候选包仍需独立复测。

采样包SHA-256（仅作为临时验证产物指纹，不提交APK）：

- `entrytask-warmup-baseline.apk`：`4b333735ee2896556cc97fd490cfe7962f2d1d4130f70e23d9d46e23b568ea6e`
- `entrytask-warmup-enabled.apk`：`9f7c24d777a53076beb2e2d5c336976fd7b42772707d9ca64f37ac5fff373ecb`
