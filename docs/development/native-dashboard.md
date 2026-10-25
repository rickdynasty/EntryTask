# Android 双页面模式与 Native 四根页

> 宿主命名已更新：RootContentHostFragment位于app的shell/host目录，承载四Tab而非仅首页；旧文本占位Fragment已删除。类名恢复兼容及清理范围见[重命名记录](根内容宿主重命名与清理.md)。


本次需求新增 Native 首页、财富、会员、我的四个根页，前版曾基于旧PRD实现XML；当前按`prd/EntryTask UI PRD.docx`与`prd/UI原型/*.jpg`必做区域，双端同步改版。RN页面及其子页继续保留；iOS不变。原型中的第五个“借钱”Tab仍不增加。

## 启动方式

| 方式 | 构建输入 | 页面 |
| --- | --- | --- |
| 根目录 `npx react-native run-android` | 当前CLI自动传 `reactNativeDevServerPort` | RN |
| Android Studio打开`android/`，运行app Debug | IDE构建标记，或无CLI端口属性 | Native Compose |
| Android目录 `./gradlew :app:installDebug` | 无来源属性 | Native Compose |
| 显式参数 `-PentrytaskUiMode=rn` / `native` | 最高优先级 | 指定模式 |

配置在`android/app/build.gradle`计算，并写入`BuildConfig.USE_REACT_NATIVE_PAGES`；`RootContentHostFragment.usesReactNative()`统一读取。IDE标记`android.injected.invoked.from.ide=true`优先于CLI端口属性。显式覆盖值只允许`rn`或`native`，错误值构建失败。

这区分的是**生成APK的构建方式**。同一APK安装后从桌面重开仍保持原模式；不是运行时检测是哪个程序发起启动。切换方式必须重新构建、覆盖安装，不能只Reload。当前CLI 20.1.0的默认构建会注入端口属性；`--binary-path`直接安装现有APK不重新选择模式。不要在`gradle.properties`中永久保存`reactNativeDevServerPort`或覆盖参数，否则会改变来源识别。未来升级CLI需复核本约定。

两个模式共用`com.entrytask`、MainApplication、singleTask MainActivity、认证和Native底栏。未删除RN Gradle插件、Codegen和autolinking；Native模式不挂载ReactFragment、不触发RN预热。Release直接Gradle构建默认Native，需要RN产物时显式传`-PentrytaskUiMode=rn`。

## 当前实现

RN与Android四页均按新版16项必做区域组装。Android业务页为Compose+分域MVVM，RN为React+MVVM/Query缓存；共用认证、Native根Tab、网络Mock和签到账本。详细源码、范围与本次验证见[新版实施记录](新版四Tab实施记录.md)，设计见[技术方案](../design/新版首页与四Tab必做UI技术方案.md)。

签到已是实际Native Mock写操作；其他未实现入口使用说明层。旧XML类暂保留参考但不再进入渲染路径。该历史实现与验证记录不作为新版验收证据。

## 前版XML实现记录（已退出当前渲染路径）

### 模块与数据流

```text
app / RootContentHostFragment（组装、Shell状态订阅、平台弹层）
  → biz_dashboard / DashboardView（XML渲染、动作上报）
  → DashboardViewModel（Tab、加载、错误、资产显隐、分类）
  → domain / DashboardRepository（类型化投影）
  → data / MockDashboardRepository（API路由、DTO映射）
  → MainApplication.prototypeNetwork（同一个OkHttp Mock client）
  → PrototypeMockInterceptor → APK内mock/分API JSON
```

ShellRepository是唯一根Tab和会话状态源，Native页面不另建底栏状态。MainActivity仍在挂载内容前认证，RN模式继续执行RN门禁。Native内容由RootContentHostFragment在viewLifecycleOwner的STARTED区间收集；无会话清空页面数据，重试走现有foreground认证协调器。对话框随View销毁或会话失效关闭。

Repository只读manifest路由配置，响应必须经过OkHttp请求及Mock拦截器，普通随机延迟1000～3000ms沿用现有实现。加载使用可取消Call，失败不切换通路；校验会话epoch和scope，VM另以请求代次拒绝旧响应。失败刷新保留旧内容，首次失败展示重试。资产默认隐藏，会话变化恢复隐藏；不新增凭证或持久查询缓存。

| 根页 | 请求 |
| --- | --- |
| 首页 | home.content、product.list |
| 财富 | wealth.content、asset.summary、product.list |
| 会员 | member.content、member.summary |
| 我的 | profile.marketing、profile.summary、asset.summary |

产品分类继续请求product.list；资产和统计以Mock handler的实际结果为准，新账号资产为0，不将截图金额冒充真实资产。金额用BigDecimal处理最小单位，不使用浮点金额。基金示意柱值来自本地产品快照，并展示模拟及风险提示。

### XML和组件复用

- `dashboard_screen.xml`：加载/错误/重试与滚动容器。
- `dashboard_home/wealth/member/profile.xml`：各页布局结构，静态文案来自strings。
- `BankHeaderView`：顶部工具栏；`ServiceGridView`：入口、角标、快捷图片；`ServiceIconView`：Canvas线性图标。
- `PromotionCardView`：标题、说明、装饰图及点击；依据宽度适配横卡与窄列卡。
- `SummaryStatsView`：等宽统计条；`FundChartView`：按Mock数据绘制示意柱状图；`DashboardView`复用以上控件及分类、资产绑定。

装饰图片沿用`src/shared/assets/*.png`单份源码。`DashboardArtTask`通过AGP variant.sources.res注册生成任务，将文件名转换为Android资源名并输出到build目录，不维护平台副本。业务文字和交互是原生控件，不使用整页截图；原始局部素材的清晰度仍受PRD截图限制。

### 本轮范围

已实现四根页视觉、Native Tab驱动、Mock加载/失败/重试、资产显隐、产品分类与入口反馈。服务、活动、签到、权益、消息、申卡和借款等入口弹出明确的Native演示范围说明，**本轮不实现这些Native业务子页或写操作**。完整业务流程继续在RN模式验收，不能据Native根页的存在声称Native全场景已完成。

### 验证方法

在android目录：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :biz_dashboard:testDebugUnitTest :biz_dashboard:lintDebug -PentrytaskUiMode=native -PreactNativeArchitectures=arm64-v8a --max-workers=2
```

既有账号设备使用`adb install -r`覆盖安装应用和测试APK，再直接运行：

```sh
adb shell am instrument -w -r -e class com.entrytask.home.NativeDashboardTest com.entrytask.test/androidx.test.runner.AndroidJUnitRunner
```

测试只建立临时内存会话、GET Mock并退出，不注册账号、不清数据。不要用可能卸载应用的Gradle connected任务替代。RN路径使用根目录官方CLI重新构建启动并验证认证、RN页面和请求日志。构建模式及资源检查应从空生成目录执行，验证最终APK，而非只查看源素材存在。


### 本次验证记录（2026-10-24）

设备：Pixel_10 AVD、arm64模拟器；构建仅覆盖arm64-v8a，不能据此声称多ABI或真机性能验收。

- RN：typecheck、lint、architecture:check通过；Jest 9个套件、26项通过（使用`--watchman=false`，不依赖本机Watchman）。
- Android：宿主22项单测、新模块3项单测通过；新模块Lint通过。首次空生成目录Debug构建通过，最终还检查Debug/Release产物资源。
- 启动：真实执行根目录`npx react-native run-android --no-packager --active-arch-only`，Metro单独启动，CLI日志显示renderer=rn，构建安装启动成功；RN设备用例验证Native内存会话后唯一React Surface及首页Mock文案。
- IDE路径：使用`android.injected.invoked.from.ide=true`构建输入验证Native；与CLI端口同时存在也优先Native。未在Android Studio GUI中实际点击Run，不能表述成已完成IDE操作实测。
- Native设备用例遍历四Tab、确认不挂载ReactFragment，并检查资产显隐；另执行2项既有认证返回用例。通过覆盖安装和直接instrument运行，不卸载、不清账号数据。
- 素材核对：APK含全部13张共享PNG、36份contracts JSON、43份Mock JSON；JSON与仓库源码逐文件比对。装饰图与自绘图标的细节仍有近似，本轮不宣称像素级还原或完整Native业务流程。

Release会缩短res文件路径，资源核对需用Android SDK的`aapt2 dump resources`查找drawable/dashboard_*到PNG的映射，再核对ZIP条目及尺寸，不能单凭ZIP里是否保留原文件名判断漏资源。Debug/Release两种产物均已完成此检查。
