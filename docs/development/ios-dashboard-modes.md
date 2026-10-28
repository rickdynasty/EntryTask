# iOS 四页双模式与组件指南

## 运行约定

| 入口 | 默认业务页面 | 开发依赖 |
| --- | --- | --- |
| 根目录 `npx react-native run-ios` | RN四根页及业务子页 | Debug需要Metro |
| 根目录 `npx react-native build-ios` | 构建RN模式产物 | Release包含Hermes Bundle |
| Xcode打开EntryTask.xcworkspace并Run | SwiftUI Native四根页 | 不需要Metro，不创建RN runtime |
| 显式 `ENTRYTASK_PAGE_MODE=rn\|native` | 按指定值 | 优先于默认值 |

登录注册、会话、UIKit Shell及根Tab、URLSession Mock、GRDB和日志共用。模式属于构建产物，不是“当前从哪个程序点开应用”；安装后从桌面启动仍保持该产物模式。切换需重新构建覆盖安装，保留已有账号和演示账本。

```sh
# 仓库根：官方命令默认RN；已有Metro可加--no-packager
npx react-native run-ios

# 显式构建Native模式
npx react-native run-ios --extra-params 'ENTRYTASK_PAGE_MODE=native'
```

在Xcode Build Settings中查看User-Defined的ENTRYTASK_PAGE_MODE，工程默认native；需要在Xcode调试RN时可显式选rn，再重新构建。不要修改共享默认值后忘记恢复；日常按两种标准入口使用即可。

## 构建期选择怎样实现

1. [react-native.config.js](../../react-native.config.js)通过CLI支持的项目命令扩展包装已安装的run-ios/build-ios。
2. [ios-cli.cjs](../../tools/ios-cli.cjs)保留原命令的选项与构建链，缺省追加xcodebuild参数`ENTRYTASK_PAGE_MODE=rn`，显式参数优先，非法/冲突值拒绝。
3. App target默认设置native。[Info.plist](../../ios/EntryTask/Info.plist)以构建变量生成EntryTaskPageMode。
4. [BuildConfiguration](../../ios/EntryTask/App/BuildConfiguration.swift)读取产物中的枚举，遇到缺失/非法值不静默兜底。
5. [ShellViewController.render](../../ios/EntryTask/Shell/Presentation/ShellViewController.swift)认证后选择唯一内容child：RNContainer或UIHostingController(NativeDashboardView)。
6. Native不访问AppContainer.runtime的lazy属性，因而不创建factory/业务Surface；RN继续单instance、单Surface，不为Tab或认证新增runtime。

没有写入UserDefaults、临时模式文件或修改node_modules。CLI结束不会把rn模式“记住”为下次Xcode的默认值；模式切换由Xcode构建设置参与增量构建。

Native模式跳过不使用的业务JS Bundle，并删除同一产物路径中的遗留main.jsbundle；保留RN依赖、Pods、Codegen和模块实现。RN Release正常执行现有Bundle/Hermes脚本。

### 只有CoreSimulator而没有Simulator.app的开发环境

[Compatibility入口](../../tools/ios-headless.cjs)仅在macOS、无独立Simulator.app、Debug模拟器、非交互模式时启用：复用官方build-ios构建器，再用simctl boot/install/launch，不伪造Simulator.app或改依赖源码。

该路径支持实际Simulator名称/UDID、已有Metro及普通构建参数；输出会明确说明使用CoreSimulator。完整Simulator.app环境、真机、Release启动与交互式设备选择仍沿用官方run-ios路径。`--binary-path`不重新构建，已有产物必须与请求的页面模式一致，否则拒绝安装。

项目CLI包装依赖当前锁定的CLI公开命令结构；升级CLI时验证命令、参数、destination、安装与Metro流程，不能只检查help输出。

## 模块与依赖

App + 六个Swift Package library，共七个实施单元。新增的[DashboardFeature](../../ios/Packages/EntryModules/Sources/DashboardFeature)对应Android biz_dashboard的实际职责，其余五库保持原职责。

```text
DashboardFeature/
├── Domain/DashboardModels.swift          四域模型、Money、Repository契约
├── Data/NetworkDashboardRepository.swift 受控NetworkPort请求、解码和过期会话检查
├── Presentation/
│   ├── PageResource.swift               区域状态、取消、代次与基础页面生命周期
│   └── RootPageViewModels.swift          四域VM、签到、DashboardCoordinator
└── UI/
    ├── BankComponents.swift              基础组件与公共业务展示
    ├── BankCarousels.swift               海报/市场轮播
    ├── HomeScreen.swift
    ├── WealthScreen.swift
    ├── MemberScreen.swift
    ├── ProfileScreen.swift
    └── NativeDashboardView.swift         四页组装、可见性和演示弹层
```

DashboardFeature只依赖CoreKit；AppContainer注入同一个NetworkClient及Native会话上下文闭包。它不导入UserFeature/StorageKit/DemoEngine的实现，不创建另一个账号库或持久响应缓存。

MVVM数据流：

```text
SwiftUI Screen（状态/动作）
  → 四域VM / PageResource
  → Domain Repository契约
  → NetworkDashboardRepository
  → CoreKit.NetworkPort
  → 同一个NetworkClient / DemoURLProtocol
  → 同一个DemoHandler / App存储适配器 / GRDB
```

Native根页不绕一圈调用自己的JS Bridge。RN根页仍通过五个TurboModule使用相同平台能力，两种调用路径在Native网络和模拟服务端汇合。

## 四页区域及复用

产品基线为PRD标记的必做区域，同时沿用Android/RN已经展示的首页运营内容。营销入口保持演示弹层范围，不虚构真实交易。

| 页面 | 实现区域 | 交互 |
| --- | --- | --- |
| 首页 | 搜索/扫码/消息/头像、四快捷、海报、五列服务、横向快捷、耀钻推广、财富精选、特色/私行/尊享/投研等运营区域、品牌页脚 | 海报切换、分类请求、最多三张完整产品卡、演示弹层 |
| 财富 | 资产隐私卡、市场解读、五列服务、横向品类、最多三条轻量产品摘要、页脚 | 资产显隐、每五秒行情向上切换、品类切换 |
| 会员 | VIP等级与资产进度、四列权益、升级入口、签到卡/七日日历/规则、页脚 | 实际checkin.status/submit，防重、取消、结果待确认 |
| 我的 | 头像昵称/低碳/等级、五项统计、资产、月度收支、信用卡账单、借款营销、页脚 | 共享隐私、演示服务、Native安全退出并重回认证 |

组件按职责复用：

| 组件 | 使用位置 | 边界 |
| --- | --- | --- |
| BankPage | 四页 | 每页单ScrollView+LazyVStack、页脚、刷新入口 |
| BankCard / SectionHeader | 四页 | 统一背景、圆角、间距与标题 |
| ResourceRegion | 四页各数据区 | 首载/刷新/错误和重试，不隐藏已成功内容 |
| StandardGridMenu | 首页/财富/会员 | 动态列数、角标、金色/蓝色样式、统一动作 |
| AssetPrivacyCard / MoneyText | 财富/我的；会员复用同一隐私状态 | 金额与收益一致遮罩 |
| BannerCarousel / MarketTickerCarousel | 首页/财富 | 有界数据、五秒轮播、触摸/后台/停用/减少动态效果暂停 |
| FinancialProductCard | 首页 | 保留收益、期限、门槛、风险和代销信息 |
| VipProfileCard / InteractiveTaskCard | 会员 | 专属展示接收纯模型与动作，不自行写库 |
| UserCardHeader / 收支/账单/借款组件 | 我的 | 模型驱动，缺卡/缺数据有独立呈现 |
| BankArtwork / AppFooterLogo | 四页 | 共用素材白名单、后台文件读取、统一品牌收尾 |
| DemoActionSheet | 各演示入口 | 明确本地演示范围，不执行真实交易 |

首页运营只接受menu/banner/cards/news四种既有布局；不解释远端组件代码。图标使用系统符号映射，快捷入口、海报、头像和品牌沿用共享素材/现有品牌组件；当前不是逐像素一致的iOS视觉验收，系统底栏、字体和图标存在平台差异。

## 生命周期、状态与稳定性

- Shell是tabId/depth/revision/认证权威。DashboardCoordinator只接收投影，不建立另一套可自由写入的根导航状态。
- 只激活当前、已认证、前台页面。创建四个VM不等于同时请求四页；切Tab、后台暂停请求和轮播。
- 四个ScrollView身份随Tab保留，epoch变化清空资源/选择/隐私并重建页面身份；不同用户不恢复前一用户数据或滚动位置。
- PageResource每次请求、取消、clear都更新代次；回调检查代次与取消。刷新失败保留旧内容，首载失败显示重试，空产品列表显示空态。
- Repository在发起前拒绝guest，发起时捕获Native context，响应返回后再核对epoch/scope；不接受页面自填身份。
- 隐私是会话内共享状态，默认隐藏；财富、会员资产进度、我的资产/收支/账单/营销额度一致处理，退出重置，不通过持久偏好恢复。
- 签到同步防重，捕获活动状态/epoch/写代次。切页取消后不交付旧结果；写结果未知时先查询事实，使用确认后的业务日期，不自动重放。
- 金额用Decimal从最小货币单位转换，比例展示时再受控转换。借款必须marketingOnly，不把营销额度当授信。
- Native安全退出共用SessionStore/提交协调器，重置Shell并拉起原认证，保留根控制器，不创建RN或另一个会话。
- 日志使用既有白名单Logger，只记录阶段、pageId、API/请求/流程编号和安全错误码，不记录整个模型或响应。

## 素材与构建产物

[prepare-ios-resources.cjs](../../tools/prepare-ios-resources.cjs)从共享src/shared/assets生成13张白名单PNG至.app/dashboard-art，并写入dashboard-art.index.json。每次替换生成目录，删除源码素材不会遗留旧平台副本。运行时不访问开发机或Metro图片目录。

contracts/mock仍为81个JSON，索引与Native素材索引分开。两种模式都包含平台能力运行资源；Native不创建RN页面，不等于删除RN编译依赖。

在同一构建目录交替构建native→rn→native，再检查Info.plist、Bundle与资源，防止“独立目录各自成功”掩盖切换残留。

[产物校验器](../../tools/verify-ios-artifact.cjs)核对实际.app模式、JSON路径集合及SHA-256、13张素材和JS Bundle。构建后执行：

```sh
# 仓库根；路径换成实际产物，模式与配置必须对应本次构建
node tools/verify-ios-artifact.cjs /path/to/EntryTask.app native release
node tools/verify-ios-artifact.cjs /path/to/EntryTask.app rn release
```

## 验证入口与已知边界

```sh
# 仓库根
node tools/check-ios-architecture.cjs
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand

# ios/Packages/EntryModules
swift test
```

Xcode scheme共用UI测试，读取测试产物模式并核对真实业务root。Native额外断言隐私跨页同步与退出复位；RN保留bootstrap后的四Tab和签到链路。测试使用Debug隔离存储，不清用户库。

```sh
# ios/：destination替换为本机实际设备
xcodebuild -workspace EntryTask.xcworkspace -scheme EntryTask -configuration Debug \
  -destination 'platform=iOS Simulator,name=<实际设备>' \
  -only-testing:EntryTaskUITests -parallel-testing-enabled NO -collect-test-diagnostics never test

# 同样的测试，显式使用RN构建模式；需已有Metro
xcodebuild -workspace EntryTask.xcworkspace -scheme EntryTask -configuration Debug \
  -destination 'platform=iOS Simulator,name=<实际设备>' ENTRYTASK_PAGE_MODE=rn \
  -only-testing:EntryTaskUITests -parallel-testing-enabled NO -collect-test-diagnostics never test
```

### 本次验证结果

2026-10-28，Apple Silicon、Xcode 27、iPhone 18 Pro / iOS 27模拟器；Swift包测试运行于macOS。构建均为arm64模拟器产物，不是Archive/正式签名包。

| 验证 | 结果与范围 |
| --- | --- |
| RN静态检查 | typecheck、lint、architecture:check通过 |
| JS行为测试 | 12个suite、41项通过；包含CLI模式与模拟器选择回归 |
| SwiftPM | 18项通过，含6项Dashboard模型/取消/会话/防重/跨日确认/guest门禁测试 |
| Native Debug UI | 2项通过，注册/认证、四Tab、签到、跨Tab隐私与退出重置；关闭Metro后复验通过 |
| RN Debug UI | 2项通过，实际Bridge bootstrap、四Tab、签到及收支展示 |
| Release产物 | 空构建目录Native成功，同目录切换RN再回Native均成功 |
| 资源 | 两种模式81个JSON、13张PNG路径与SHA-256一致；旧资源删除回归通过 |
| JS Bundle | RN Release为Hermes字节码（2,669,035字节）；Native无业务Bundle |
| 官方CLI | run-ios构建、安装、启动成功；本机使用CoreSimulator兼容路径 |

Release矩阵可按以下顺序复现；需要正常解析固定Swift依赖，路径可使用自己的临时目录：

```sh
# ios/；首次使用尚不存在的构建目录，以下三步复用同一目录
xcodebuild -workspace EntryTask.xcworkspace -scheme EntryTask -configuration Release \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/EntryTask-Modes \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build

# 显式切RN，再执行上面默认Native命令验证切回及清理
xcodebuild -workspace EntryTask.xcworkspace -scheme EntryTask -configuration Release \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/EntryTask-Modes \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES ENTRYTASK_PAGE_MODE=rn build
```

每步完成后从仓库根调用上文产物校验器，路径为`/tmp/EntryTask-Modes/Build/Products/Release-iphonesimulator/EntryTask.app`。Intel开发机调整ARCHS；不要通过删除既有产物模拟模式切换验证。

首次构建/测试可能重新访问GitHub和SQLiteLib子模块；网络受限时沿用[固定依赖核验与恢复](iOS编译失败处理记录-Missing-package-product.md)，不提交本地镜像，不跳过失败用例。

已完成Native/RN核心实现；完整旧系统/真机、字体与窄屏矩阵、内存趋势、无障碍及逐像素视觉验收仍需专项验证。SwiftPM/模拟器结果不等同生产银行安全或性能验收。Android与现有RN业务源码不因新增Native四页而重写。
