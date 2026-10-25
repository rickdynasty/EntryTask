# 新版四Tab双端实施记录

日期：2026-10-25。依据[双端技术方案](../design/新版首页与四Tab必做UI技术方案.md)，用户已授权编码。初版实现新版PRD的16个必做区域（首页后续增量见下文），保留四个Native根Tab、singleTask主Activity和唯一RN实例。iOS原生对等能力仍延期。

## 源码与职责

| 层 | RN | Android |
| --- | --- | --- |
| 组装/宿主 | CompositionRoot、AppNavigator | HomeFragment、MainShellView |
| 页面绑定 | features四域的*Route，管理资源订阅及动作绑定 | NativeDashboardRoute，生命周期感知收集状态 |
| 业务状态 | 四域VM、RTK Query资源与PageUiStatePort | 四域RootPageViewModel，独立PageResource区域状态 |
| 页面展示 | 四个*Screen及域内MemberRegions/ProfileRegions | RootScreens中的四页和业务区域 |
| 公共UI | DashboardComponents、ServiceGrid、Icon、Primitives | ui/compose/BankComponents、app的RootTabBar |
| IO | Repository/ResourceClient→TransportRouter→NativeTransport | 领域Repository→RootPageRepositories→NativeApiClient |
| 最终数据源 | Native Bridge→同一prototypeNetwork | prototypeNetwork→PrototypeMockInterceptor |

MainActivity的XML只保留稳定Fragment槽与Compose底栏槽。HomeFragment Native分支返回带稳定ID的ComposeView，RN分支沿用ReactFragment挂载逻辑，入口/注册名/包名不变。Compose编译器版本在根Gradle与Kotlin统一，biz_user/lib_base取消重复版本声明，没有升级Kotlin或RN。

RN只挂载当前Tab，VM与轻量会话UI状态保留；分类由Wealth VM通过ClientState Port更新，查询响应仍只保留在RTK Query缓存。页面关闭释放订阅与轮播计时器。Android各区域独立失败/重试，旧会话/分类请求不能回写新状态；同一会话共享资产快照。

资产显隐在两端均只保留于当前会话，默认隐藏，切会话重置。RN停止读写旧assetVisibility偏好，旧Native键保留，未清理用户数据。金额、精确进度及读屏标签同步打码，公开利率/计数正常显示。

初版首页不请求可选产品流（本次已恢复home场景product.list），财富仅显示最多3条分类摘要，会员不预加载活动/已领权益/能量记录列表，我的不加载可选服务聚合。非签到入口采用两端一致的演示说明层；既有RN子页代码和路由保留。

## 契约与Mock

新增`profile.financialOverview`：`GET /api/profile/financial-overview`，Native认证、nativeMock通路，包含月收支、可空信用卡账单和标记为营销演示的借款上限。该上限不是用户授信。

兼容扩展wealth.content的marketItems、member.content的benefitEntries、member/profile.summary的等级进度与低碳标签、签到接口的calendar/rewardRule。新字段同步schema、JSON、manifest、DTO及生成校验器。当前清单为35个RN可见API、37条Native路由（含认证），不代表37个完整业务场景。

所有Mock JSON随构建进入APK。普通请求由拦截器后台随机延迟1～3秒、支持取消，不依赖开发机文件或Metro。CheckinLedger返回真实最近7日历史，跨月也不以连续天数猜测；仍保留第3/7天30/100奖励，数据由用户scope隔离的原账本维护。

两端签到都先防重，再提交日期及幂等键；失败/超时先查状态，不自动重放。若状态也无法确认，下次操作先确认状态，不能直接发第二次写入。离页取消后重进重新读取账本。RN通用签到子页与任务卡共用CheckinRepository适配，成功后失效查询；当前ResourceClient保留有界的整组失效策略，未为本次另建缓存。

## 素材与视觉边界

PNG仍以src/shared/assets为单份源。线性图标新增同目录icons.json，RN直接导入路径，DashboardArtTask生成Android vector资源；通过AGP variant生成目录接入，不手工维护平台副本。页面是控件组合，没有嵌入整页截图。

页尾银行标志当前沿用工程线稿并组合银行中英文名/标语，会员蓝卡用组件绘制；尚未获得独立正式品牌Logo和完整VIP纹理。不能把这些实现声称为正式品牌素材或原型像素级验收。最终视觉需按原型在360/390/412dp及1.0/1.3倍字体下人工对照。

旧Native XML渲染类暂保留为前版实现参考，但HomeFragment不再调用；完成最终视觉确认后可单独删除，避免在用户未提交基线上直接销毁旧文件。

## 本次验证

本次执行使用JDK17、Android17的16KB页arm64模拟器，构建定向ABI为arm64-v8a。没有测量或承诺Release首屏/帧率指标。

| 验证 | 结果与边界 |
| --- | --- |
| RN typecheck / lint / architecture:check | 通过 |
| Jest（watchman=false） | 10个suite、32项通过；不依赖本机Watchman服务 |
| contracts:generate / contracts:check | 通过，35个RN endpoint、37条Native路由 |
| Android app / dashboard / demo_engine单测 | 分别22 / 7 / 4项，合计33项通过，无跳过 |
| dashboard Lint | 通过 |
| 干净Debug与Codegen | 从空app/dashboard生成目录构建通过 |
| Debug APK实际资源 | 含新增财务schema/fixture、37路由manifest、31个共享vector图标 |
| Release构建及离线资源 | 定向ABI构建通过，APK含离线Bundle和新增财务schema/JSON；最终增量复核覆盖最后修正；全部contracts/mock与源码逐文件比对一致 |
| 官方CLI | 构建、覆盖安装并启动同一com.entrytask成功；选择RN模式 |
| RN独立离线Bundle | 通过，Metro可解析src/index.js、契约JSON及图片资源 |
| Native Compose设备测试 | 1个综合用例通过：四Tab、跨Tab显隐、无卡态、Activity重建、品牌页尾可达与无ReactFragment |
| RN设备测试 | 1个综合用例通过：真实ReactFragment四页、七日签到读取、会员与财务Mock数据、跨Tab资产显隐 |
| 最终视觉 | 已查看实现截图；正式Logo/VIP纹理、三档宽度及1.3倍字体仍需人工最终确认，不等于像素级验收 |

首次Compose设备测试被旧Espresso3.5.1调用已移除的InputManager.getInstance阻断，未进入业务断言。测试侧采用Espresso3.7.0、Runner/Core1.7.0修复，没有升级应用运行库。[官方修复说明](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)记录了使用getSystemService替代该反射调用。

随后设备验证发现认证占位分支从Compose key作用域直接返回，触发组合运行时Start/end imbalance。已将门禁分支移至key外，修复后重新构建并通过设备测试。无卡断言也改为等待财务请求完成，不能将卡片标题出现等同数据已就绪。

当前新区域使用会话内资源与区域错误状态；Native重试目前由页面VM统一刷新其区域，RN提供资源级重试。签到写后RN沿用整组资源失效保证正确性；后续有规模/频率证据时再收敛精准失效，不新增一套查询缓存。

复现命令（仓库根，设备已启动；已有Metro可加--no-packager）：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand --watchman=false
npm run bundle:android
npx react-native run-android --active-arch-only --extra-params="--max-workers=2"
```

Native构建与测试包（android目录）：

```sh
./gradlew :app:clean :biz_dashboard:clean :app:assembleDebug :app:assembleDebugAndroidTest :app:generateCodegenArtifactsFromSchema :app:testDebugUnitTest :biz_dashboard:testDebugUnitTest :demo_engine:testDebugUnitTest :biz_dashboard:lintDebug -PentrytaskUiMode=native -PreactNativeArchitectures=arm64-v8a --max-workers=2
./gradlew :app:assembleRelease -PentrytaskUiMode=native -PreactNativeArchitectures=arm64-v8a --max-workers=2
```

已有数据的设备只覆盖安装，不运行可能卸载应用的connected任务：

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.entrytask.home.NativeDashboardTest com.entrytask.test/androidx.test.runner.AndroidJUnitRunner
```

RN测试须先由CLI安装RN APK，再用同样RN模式构建测试包（android目录执行`./gradlew :app:assembleDebugAndroidTest -PreactNativeDevServerPort=8081 -PreactNativeArchitectures=arm64-v8a --max-workers=2`）。安装测试APK后，执行同一instrument命令，将class替换为`com.entrytask.home.RnRequiredDashboardTest`。不能拿Native模式内联的BuildConfig运行RN测试并把Assumption跳过写作通过。

测试只建立临时内存会话，不注册/覆盖用户账号，不清SP。日志保留flowId/requestId、阶段、耗时和错误码，实施记录不提交设备原始日志或个人响应。

最终设备综合用例总耗时：Native 11.691秒，RN 15.589秒；这些是自动化用例耗时，不是页面首屏或性能指标。Release会缩短res文件路径，已通过aapt2资源表核实31个dashboard_icon名称均指向APK内实际文件，不能仅按ZIP文件名搜索原资源名判定漏包。


## 首页缺失内容修正（2026-10-25）

用户在Android Studio运行后发现首页只有顶部、四入口、轮播和Logo。排查确认是原16区域范围裁剪造成，并非IDE构建模式或网络失败。本轮按首页完整视觉稿恢复信息层级，RN同步更新，其他Tab范围不扩展。

- 首页顺序：导航→四快捷入口→营销轮播→十项服务→横向快捷条→耀钻空间→财富精选→特色单品→私行服务→尊享服务与权益→投研论道→私行入口→品牌页尾。
- home.content保留原字段，新增sections，限定4种展示类型、最多12区、每区最多12项；契约校验器重新生成。新增内容位于分API JSON，经现有Native Mock 1～3s延迟返回并打包APK，不在View中伪造网络数据。
- Android：HomeContent/Repository补齐既有菜单映射和运营分区；Home VM增加分类产品资源，默认all，取消、分类竞态和会话切换复用PageResource；HomeSections封装快捷条、金融产品卡、运营分区。
- RN：HomeRoute恢复产品订阅；Home VM接入PageUiStatePort，分类随Tab保留、换会话清除；复用ProductCard/ServiceGrid/CampaignCard，新增HomeEditorialSection。ServiceGrid允许三列；产品指标列允许文本换行，避免小屏溢出。
- 视觉限制：未新增正式品牌或资讯照片，现有金色素材、线性图标及近似卡片色块仅作原型表达；浮动财富广场、真实交易和资讯详情未扩展。

本轮已完成的验证（Debug，arm64-v8a，emulator-5554）：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand --watchman=false
npm run bundle:android

# android/目录
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :biz_dashboard:testDebugUnitTest :biz_dashboard:lintDebug -PentrytaskUiMode=native -PreactNativeArchitectures=arm64-v8a --max-workers=2

# 根目录；复用已运行的Metro，附加构建设备测试APK
npx react-native run-android --no-packager --active-arch-only --extra-params=":app:assembleDebugAndroidTest --max-workers=2"
```

- TypeScript、ESLint（0 warning）、架构、共享契约检查通过；10套Jest共33项通过。Watchman按当前环境禁用，未跳过测试。
- biz_dashboard共8项单测通过，其中新增首页分类迟到响应、Tab保留及换会话重置测试；Android Debug和测试APK构建通过。Lint为0错误、11条既有警告（图标资源反射、Compose指针处理、历史XML等），未以抑制规则消除。
- Native真机框架测试1项通过（68.151s，包含完整首页和四Tab）；使用临时内存会话，不写持久测试账号。首页逐区定位智慧存款、财富精选、产品、特色单品、尊享服务、投研和私行中心，保存分段截图进行人工核对。
- Debug APK内assets/mock/home/content/response.success.json及assets/contracts/api/home.content.schema.json与共享源文件逐字节一致，新增内容随APK离线提供。
- 官方CLI构建/安装/启动成功；RN生产模式JS Bundle生成成功，包含32个图片资源。RN设备回归1项通过（82.828s），覆盖完整首页内容、真实ReactFragment、跨Bridge数据及四Tab。设备测试耗时包含截图/无障碍定位，不作为启动或页面性能数据。

测试APK通过adb install -r保留安装后，使用am instrument -w -e class分别指定NativeDashboardTest和RnRequiredDashboardTest执行，没有使用可能卸载应用的connected测试。本轮未重跑Release/iOS，也不将历史Release结果作为本轮证据。
