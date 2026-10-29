# iOS：Xcode 使用与工程结构入门

面向第一次使用 Xcode 的 EntryTask 开发者。本文以当前仓库配置与本次提供的两张截图为依据；按钮位置、中文译名会随 Xcode 版本变化，找不到时按菜单功能名寻找，并在 Settings → Key Bindings 核对快捷键。本文只说明操作与工程映射，没有重新执行应用构建或设备验收。

先读本文完成“打开、找文件、运行、打断点”，再读 [Swift 与调试基础](iOS基础与调试新手指南.md)；页面架构见 [四页双模式与组件指南](ios-dashboard-modes.md)。

## 1. 先分清 Tab 与 Tab 内容

**首页、财富、会员、我的四个根 Tab，始终由 Native 实现。** `ShellViewController` 使用 UIKit 的 `UITabBar`，`ShellViewModel` 管理根 Tab 状态；SwiftUI 和 RN 都不再创建一套根底栏。

| 启动入口 | 根底栏 | Tab 内业务内容 | 登录注册 |
| --- | --- | --- | --- |
| Xcode 打开 workspace 后 Run，默认配置 | Native UIKit | SwiftUI 四页，DashboardFeature | Native SwiftUI |
| 根目录官方 `npx react-native run-ios`，默认配置 | Native UIKit | 单 RN Surface 中的四域页面及子页 | Native SwiftUI |

构建设置 `ENTRYTASK_PAGE_MODE=native|rn` 选择业务内容，显式设置优先。模式属于安装产物，不能只凭“现在用什么程序打开应用”判断。Native 模式不创建 RN runtime、不需要 Metro；RN 模式保持每进程最多一个 RN instance。RN 子页深度会同步给 Shell，以决定是否隐藏底栏。

## 2. 正确打开工程

1. Finder 找到仓库的 `ios/EntryTask.xcworkspace`，双击打开。也可在 Xcode 选择 File → Open，选该 workspace。
2. 左侧应看到两个工程：`EntryTask`、`Pods`；下方还有 `Package Dependencies`。
3. 工具栏选择 `EntryTask` scheme 和本机实际可用的 iPhone 模拟器。截图是 iPhone 18 Pro，不要求每个人安装同名设备。
4. 首次等待包解析、索引完成。依赖未安装时，按 [iOS README](../../ios/README.md) 准备 npm、Bundler 和 Pods，不盲目升级依赖。

`EntryTask.xcodeproj` 记录 App 的 target、源码引用、构建设置；`EntryTask.xcworkspace` 将它与 `Pods.xcodeproj` 组织在一起。本工程日常打开 workspace。名称相近，但二者职责不同；不要因缺依赖而再新建一个空工程。

## 3. 对照截图认识窗口

| 截图中的位置 | 名称 | 用途与操作 |
| --- | --- | --- |
| 顶部三角形、方形 | Run / Stop | 编译安装启动 / 停止本次运行；常用 Cmd+R / Cmd+. |
| 顶部 EntryTask、iPhone 18 Pro | Scheme / Destination | 选择构建运行方案、目标设备 |
| 左侧文件树 | Project Navigator | 浏览文件、分组和包；Cmd+1 |
| 左侧顶部放大镜 | Find Navigator | 项目范围查找字符串；Cmd+Shift+F |
| 左侧顶部三角叹号、顶部黄色数字 | Issue Navigator / 问题数量 | 查看 warning/error；Cmd+5。黄色 4 表示警告数，不等于四个编译错误 |
| 左侧的报告入口 | Report Navigator | 查看每次 Build、Run、Test 的完整记录；Cmd+9 |
| 中间 AppDelegate 代码 | Editor | 编辑文件；左侧行号栏可以添加断点 |
| 代码上方 EntryTask → AppDelegate → makeWindow | Jump Bar | 查看所在文件与方法，点击分段跳转 |
| 编辑器右侧缩略代码 | Minimap | 快速滚动定位，不是另一个文件 |
| 窗口右上侧栏开关 | Inspector 区域 | 查看文件路径、Target Membership 等；隐藏时通过 View → Inspectors 找回 |
| 底部区域 | Debug Area | 左侧通常显示暂停时的变量，右侧显示 Console；Cmd+Shift+Y 切换 |

截图底部的系统键盘/Scene 文本只是日志片段。单凭它不能判断业务出错；要结合是否能复现、EntryTask 自身日志、异常栈和构建报告。

Apple 的 [项目窗口说明](https://developer.apple.com/documentation/xcode/configuring-the-xcode-project-window)介绍导航、Jump Bar 和 Minimap；[导航区说明](https://help.apple.com/xcode/mac/current/en.lproj/dev60244b45c.html)列出不同 Navigator 的用途。

## 4. 为什么磁盘目录与 Xcode 文件树不同

第一张截图显示磁盘目录，第二张显示工程配置中的**逻辑分组**。当前 App 使用 `PBXGroup` 和文件引用，分组名不必等于文件夹名；`Native Implementation` 就是逻辑分组。不要按图标颜色推断是否真实目录，查看 File Inspector 的路径更可靠。

还会遇到这些差异：

- Xcode 可能隐藏扩展名：`AppDelegate` 就是 `AppDelegate.swift`，`Info` 是 `Info.plist`，`Images` 是 `Images.xcassets`。
- VS Code 的 `Shell / Presentation` 紧凑显示对应磁盘两层目录，未必是一个带斜杠的文件夹。
- Package 的文件按 `Sources/<TargetName>/` 归属模块，在 `Package Dependencies` 下显示，不全放在顶层 EntryTask 分组。
- Finder 中的 `Pods/`、`build/` 等目录，并不会在 Xcode 原样展开；它们也不是日常修改业务的入口。

### 4.1 磁盘文件 → Xcode 位置速查

下表中 Xcode 路径的第一个 `EntryTask` 指左侧蓝色工程节点，不是磁盘文件夹。

| 磁盘位置（相对仓库根） | 当前 Xcode 入口 | 作用 |
| --- | --- | --- |
| `ios/EntryTask/AppDelegate.swift` | EntryTask → EntryTask → AppDelegate | 进程启动、创建组合根、窗口组装 |
| `ios/EntryTask/App/BuildConfiguration.swift` | EntryTask → EntryTask → BuildConfiguration | 读取构建产物页面模式 |
| `ios/EntryTask/App/AppContainer.swift` | EntryTask → Native Implementation → AppContainer | 组装六库依赖 |
| `ios/EntryTask/App/SceneDelegate.swift` | EntryTask → Native Implementation → SceneDelegate | 连接 Scene 与窗口 |
| `ios/EntryTask/Adapters/StorageAdapters.swift` | EntryTask → Native Implementation → StorageAdapters | 存储协议适配 |
| `ios/EntryTask/Shell/Presentation/ShellViewController.swift` | EntryTask → Native Implementation → ShellViewController | 根宿主、Native 底栏、认证呈现、内容选择 |
| `ios/EntryTask/Shell/Presentation/ShellViewModel.swift` | EntryTask → Native Implementation → ShellViewModel | 根导航和认证协调状态 |
| `ios/EntryTask/RNIntegration/RNRuntimeOwner.swift` | EntryTask → Native Implementation → RNRuntimeOwner | 唯一 RN factory 与 Surface |
| `ios/EntryTask/NativeModules/ETBridgeServices.swift` | EntryTask → Native Implementation → ETBridgeServices | Bridge 接入平台服务 |
| `ios/EntryTask/NativeModules/ETFactoryDelegate.h`、`.mm` | EntryTask → Native Implementation → 同名文件 | ObjC++ TurboModule 注入；不是 Swift 文件 |
| `ios/EntryTask/EntryTask-Bridging-Header.h` | EntryTask → Native Implementation → 同名文件 | Swift 与 Objective-C 可见性接点 |
| `ios/EntryTask/Info.plist` | EntryTask → EntryTask → Info | Scene、方向及模式等配置 |
| `ios/EntryTask/Images.xcassets` | EntryTask → EntryTask → Images | 原生资源目录 |
| `ios/EntryTask/LaunchScreen.storyboard` | EntryTask → EntryTask → LaunchScreen | 系统启动画面，不是首页或登录页 |
| `ios/EntryTask/PrivacyInfo.xcprivacy` | EntryTask → EntryTask → PrivacyInfo | 隐私清单 |
| `ios/EntryTaskUITests/AuthenticationUITests.swift` | EntryTask → Tests → AuthenticationUITests | 认证及双模式设备 UI 测试 |
| `ios/Packages/EntryModules/Tests/EntryModulesTests/ArchitectureBehaviorTests.swift` | EntryTask → Tests，也在本地包 Tests 中 | 同一源码被不同测试目标引用，不是两份文件 |
| `ios/Packages/EntryModules/Package.swift` | Package Dependencies → EntryModules local → Package | 六库与依赖声明 |
| `ios/Packages/EntryModules/Sources/…` | Package Dependencies → EntryModules local → Sources → 模块名 | 平台库和业务库源码 |
| `ios/Podfile` | Pods 工程 → Podfile | CocoaPods 集成声明 |
| `ios/EntryTask.xcodeproj/project.pbxproj` | 点蓝色 EntryTask 工程进入设置；通常不直接编辑该文件 | 工程图、分组、引用和构建配置 |
| 根 `src/`、`specs/`、`contracts/`、`mock/`、`tools/` | 当前未整体加入 Project Navigator | 用仓库编辑器阅读；未出现在树中不等于未参与构建 |

`Libraries` 是模板遗留的空分组；`Frameworks` 是链接引用；`Products` 是构建产品；App 工程下 `Pods` 分组主要包含生成的 xcconfig。另一个蓝色 `Pods` 工程展示依赖 target。不要把这些节点当作业务源码目录。

### 4.2 六个 Swift 库到哪里找

展开 `Package Dependencies → EntryModules local → Sources`：

| 模块 | 职责 | 新人先读哪里 |
| --- | --- | --- |
| CoreKit | 技术 Port、错误、提交协调、日志、共用 UI | Contracts、Logging |
| UserFeature | 登录注册、认证 Repository、会话 | Presentation、Public、Data |
| DashboardFeature | Native 四页与公共组件 | UI、Presentation、Domain、Data |
| NetworkKit | 网络、超时、取消、拦截接点 | Client |
| StorageKit | GRDB 数据库与 typed 存储 | 按 Package 文件树浏览 |
| DemoEngine | Mock 认证及业务模拟服务 | 按 API/业务 handler 阅读 |

Package 内 `Sources` 是库代码，`Tests` 是测试；GRDB 是依赖，不是需要修改的本项目业务库。包的目录与 target 归属由 [Package.swift](../../ios/Packages/EntryModules/Package.swift)决定。

### 4.3 四个页面的精确入口

以下文件均在 `EntryModules → Sources → DashboardFeature` 下：

| 想看什么 | 文件 |
| --- | --- |
| 四页组装、可见性、演示弹层 | `UI/NativeDashboardView.swift` |
| 首页 / 财富 / 会员 / 我的 | `UI/HomeScreen.swift` / `WealthScreen.swift` / `MemberScreen.swift` / `ProfileScreen.swift` |
| 卡片、宫格、金额、隐私、品牌页脚 | `UI/BankComponents.swift` |
| 海报和市场轮播 | `UI/BankCarousels.swift` |
| 四域 VM、签到、会话内隐私协调 | `Presentation/RootPageViewModels.swift` |
| 区域加载、错误、取消、过期结果保护 | `Presentation/PageResource.swift` |
| 模型与 Repository 契约 | `Domain/DashboardModels.swift` |
| 复用 Native 网络的 Repository | `Data/NetworkDashboardRepository.swift` |

根底栏在 App 的 `ShellViewController`，不在上述四个 Screen 文件中。想改“财富 Tab 标题/图标”和“财富页资产卡片”，应去不同位置。

## 5. 找到文件、定义和真实路径

### 已知文件名

1. Cmd+Shift+O 打开 Open Quickly，输入 `SceneDelegate` 或 `HomeScreen`。
2. 根据结果路径区分同名项，回车打开。
3. Cmd+Shift+J 执行 Reveal in Project Navigator，定位左侧所在分组。若无结果，先清空左侧底部 Filter 及仅显示修改文件等筛选。
4. 打开 File Inspector（常用 Cmd+Option+1），查看 Identity and Type 中的 Location / Full Path；也可右键文件 → Show in Finder。

**Open Quickly 找到的是工程/索引中的项，不保证搜索整个仓库磁盘。** 找 `mock/` 或 RN 的 TS 文件时，可用仓库编辑器；File → Open 也能临时打开文件，但打开不等于加入编译目标。

### 已知类名或某句文字

- 当前文件查找：Cmd+F。
- 工程范围查找：Cmd+Shift+F，检查搜索 scope 是否覆盖本地包；结果缺失时直接浏览 Package Sources。
- 类型/方法的定义：右键 Jump to Definition，常用 Cmd+点击；Option+点击查看 Quick Help。
- 当前方法列表：点击 Jump Bar 末端的方法名，常用 Ctrl+6。
- 跳到指定行：Cmd+L；看完定义后用编辑器顶部后退按钮返回调用处。

Apple 的 [文件与分组说明](https://developer.apple.com/documentation/xcode/managing-files-and-folders-in-your-xcode-project)可用于进一步理解项目树组织。

## 6. 查看工程信息与构建设置

点左侧最上面的蓝色 `EntryTask` 工程节点，编辑器会切换为设置页。先区分左侧列表中的 **PROJECT**（工程默认）与 **TARGETS → EntryTask**（App 目标）。测试目标有自己的设置，不要选错。

| 页面 | 主要看什么 | EntryTask 中的用途 |
| --- | --- | --- |
| General | 应用身份、版本、支持设备、最低系统、链接库 | 查看实际配置；不要为运行临时改安装身份 |
| Signing & Capabilities | 开发团队、签名和系统能力 | 真机需要有效签名，模拟器成功不代表真机已配置 |
| Info | 应用属性、Scene 等配置 | 部分键来自 Info.plist，部分由构建生成 |
| Build Settings | 编译器、搜索路径、自定义变量等 | 搜索 `ENTRYTASK_PAGE_MODE`、`SWIFT_VERSION`、`IPHONEOS_DEPLOYMENT_TARGET`、`SWIFT_OBJC_BRIDGING_HEADER` |
| Build Phases | 源码编译、链接、复制资源、脚本 | 找 Compile Sources、Bundle React Native code and images、Prepare EntryTask Runtime Resources |
| Package Dependencies（工程级） | Swift 包引用 | 本地 EntryModules 及其依赖关系 |

Build Settings 看不到变量时切换 All，必要时使用 Levels 查看值来自哪个层级。CLI 的 `ENTRYTASK_PAGE_MODE=rn` 是本次 xcodebuild 参数，不会永久把工程默认 native 改成 rn。

想看 Run/Test 用什么配置：工具栏 scheme 菜单 → Edit Scheme，检查 Run → Info 的 Build Configuration 和 Debug executable；Arguments 页面设置启动参数/环境变量。不要把测试专用参数加到日常共享运行配置，也不要粘贴凭证。

当前 App 最低系统、包 tools 版本、锁定依赖等解释见基础指南；实际值以当前工程和锁文件为准。配置页中的值与最终 Info.plist 生成值可能不同，模式排障可用 [产物校验器](../../tools/verify-ios-artifact.cjs)检查真正的 `.app`。

## 7. 第一次运行与理解状态

1. 选择 EntryTask scheme、可用模拟器，确认 Xcode 默认 native 模式。
2. Cmd+B 只构建；Cmd+R 构建、安装、启动并通常连接调试器。
3. 首次需要登录；没有演示账号时注册专用测试账号。认证成功后进入 SwiftUI 首页，使用 Native 底栏切换四页。
4. Mock 延迟 1～3 秒；某个区域加载中不代表整个应用卡住。Native 模式无需启动 Metro。
5. Cmd+. 停止应用，再次 Run 需要重新认证；当前会话仅保存在内存，注册账号仍在数据库。

顶部的 Indexing / Resolving Packages 与编译运行是不同阶段。Build Succeeded 只证明构建成功；设备能启动、登录成功和业务正确还需分别观察。运行中点击 Stop 不会自动卸载应用，也不会清空数据库。

## 8. 查看日志、定位错误与调试

### 构建失败：从 Report 开始

Cmd+9 选择最近失败的 Build，展开第一个失败任务，查看原始错误与完整命令。Cmd+5 适合快速跳错误文件，但多个报错可能来自同一根因。Missing package product 的处理见 [故障记录](iOS编译失败处理记录-Missing-package-product.md)，不要先删除模块、Pods 或全局缓存。

### 运行异常：看 Console 与断点

1. Cmd+Shift+Y 展开 Debug Area，确认 Console 显示的是本应用输出；清空 Filter 后再按 `EntryTask`、`request.started`、`dashboard.mounted` 等事件筛选。
2. 工程日志使用 OSLog，subsystem 为 `EntryTask`。Xcode 未附加调试器时，按基础指南通过系统日志工具查看；RN JS 日志在 RN DevTools，Native 模式没有 RN Console 属于正常情况。
3. 点击 `ShellViewController.render()` 内可执行行的行号栏，添加断点。函数声明、注释和空行不一定有可停位置。
4. Run 并完成认证，命中后观察 `state.tabId`、`state.authenticated`、调用栈与当前线程，避免展开凭证或完整个人数据。
5. Continue 继续；Step Over 执行当前行；Step Into 进入函数；Step Out 返回调用方。悬停调试按钮看功能名；常用 Step Over 为 F6，笔记本可能需 Fn。
6. LLDB 可用 `p state.tabId`、`po state.authenticated` 查看安全状态。表达式可能执行代码，初学时以只读变量为主，不打印整个 Session/请求/响应。

断点不命中时检查：是否运行当前 App、是否 Debug、Debug executable 是否开启、所有断点是否被禁用、是否进入该分支。**Native 模式不会进入 RNRuntimeOwner.rootView()**；这不表示断点坏了。SwiftUI 的 body 可能多次求值，在这里停下不能说明页面反复创建 runtime。

Apple 的 [断点指南](https://developer.apple.com/documentation/xcode/setting-breakpoints-to-pause-your-running-app)与[执行控制说明](https://help.apple.com/xcode/mac/current/en.lproj/dev2f7e98457.html)介绍暂停和单步操作。

## 9. 添加文件与日常使用技巧

- App target 新增 Swift 文件：选择合适分组，用 File → New → File 创建；确认保存目录、File Inspector 路径和 Target Membership，再检查 App 的 Compile Sources。现有工程使用显式文件引用，不能假设磁盘中新建就自动编译。
- Package 新增源码：放到 `Sources/<正确模块>/`，由 SwiftPM 自动纳入对应 target；通常不需要 App Target Membership，也不要重复拖入 App 的 Compile Sources。
- 磁盘改名/移动后出现红色引用：修复引用路径，确认构建目标；不要复制一份同名源码来“补上”。
- 删除项目引用与删除磁盘文件不同，确认对话框动作；日常整理分组不应意外删除用户源码。
- 左侧 Filter 搜文件名，Cmd+Shift+O 快开文件，编辑器后退按钮回调用处，比反复展开文件树更快。
- 想同时读 View 和 VM，用编辑器右上添加编辑器按钮，或菜单中的分栏功能；修改仍是同一个磁盘文件。
- SwiftUI Preview 只对提供 Preview 定义且依赖可构建的视图有效；打开 Canvas 不会自动为所有页面生成预览，也不能代替登录/网络主链路验证。
- Test Navigator（常用 Cmd+6）选择测试；Cmd+U 按 scheme 运行测试。UI 测试会控制设备，运行期间不要手动操作同一模拟器。
- Clean Build Folder 不等于卸载应用或清数据库。先依据错误选择处理，不把 Clean、重装依赖、清数据作为固定起手式。
- 修改前后查看 Git diff，尤其留意 project.pbxproj、scheme 和锁文件；不提交机器路径、账号、签名材料、xcuserdata 或生成目录。

## 10. 半小时练习路线

1. 打开 workspace，辨认 App 工程、Pods 工程、Package Dependencies。
2. 用 Open Quickly 找 SceneDelegate，再 Reveal in Project Navigator，Show in Finder 比对磁盘位置。
3. 找到 Native Implementation 下的 ShellViewController，定位 UITabBar 与 render。
4. 在 DashboardFeature 打开 HomeScreen 与 RootPageViewModels，对照 View → VM → Repository 阅读。
5. 查看 App target 的 ENTRYTASK_PAGE_MODE，不修改默认值，运行并注册演示账号。
6. 切财富 Tab，在 Shell 的状态处理行打断点，观察 tabId 后继续。
7. 打开最近一次 Build 报告，找到 Prepare EntryTask Runtime Resources，理解 Mock/图片怎样进入包内。

完成这些练习后，再阅读 [基础与调试指南](iOS基础与调试新手指南.md)的 Swift、MVVM 和日志章节，以及 [iOS 技术方案](../design/iOS技术方案.md)。
