# 首页与四 Tab 技术方案

## 范围与实现边界

产品依据为 `prd/EntryTask UI PRD.docx` 和 `prd/UI原型/` 中的视觉稿。本方案说明当前Android/iOS Native与RN页面实现；iOS也提供SwiftUI Native四根页，构建模式与架构见[iOS双模式指南](../development/ios-dashboard-modes.md)。首页指第一个业务 Tab，主界面外壳指 MainActivity、内容宿主与根底栏，两者职责独立。

两种模式使用相同产品区域、契约、Mock 数据和素材来源，各自实现 UI、ViewModel 和页面生命周期。共享设计与数据协议，不跨框架共享可变视图状态。页面展示演示银行数据，多数入口通过演示弹层说明功能；不能据此视为完整交易、搜索或产品购买流程。

## 宿主、导航与认证

```text
MainActivity / MainViewModel
├── activity_home.xml：fragmentContainer + bottomChrome
├── MainShellView → Compose RootTabBar
└── RootContentHostFragment
    ├── Native：ComposeView → NativeDashboardRoute → 当前 Screen
    └── RN：BaseFragment → 唯一 RN Surface → AppNavigator → 当前 Route/Screen
```

- `ShellRepository` 是根 Tab、子页深度和 revision 的权威；底栏只发送动作，根据状态回写选中样式。
- 进程只有一个 ReactHost，四 Tab 不创建四个 Fragment 或四个 RN runtime。宿主使用稳定 tag 和容器 ID 进行恢复，不重复添加。
- 官方 CLI 默认 RN；Android Studio 默认 Native；显式 `-PentrytaskUiMode=rn|native` 优先。模式写入 APK，切换需重新构建安装，详见[运行模式](../development/native-dashboard.md)。
- MainActivity 提前校验 Native 会话，在 RESUMED 拉起认证；RN 默认再做页面门禁，HelpCenter 为公开例外。认证与会话细节见[登录注册技术方案](登录注册技术方案.md)。
- 切换 Tab 停用上一页资源、计时器和提交能力。会话 epoch 改变后重建用户页面状态，旧响应不得写入新会话。

## 四页区域与行为

| 页面 | 区域顺序及内容 | 状态与交互 |
| --- | --- | --- |
| 首页 Home | 搜索/消息头部、四个金色快捷入口、轮播、五列服务、快捷行、耀钻推广、财富精选分类与产品、特色/私行/尊享/投研等内容区域、品牌页脚 | 分类切换、产品展示、轮播与演示入口；财富精选最多展示三张完整产品卡 |
| 财富 Wealth | 资产隐私卡、行情轮播、五列服务、横向分类、产品摘要、品牌页脚 | 资产显隐、分类、行情切换；最多三条轻量产品摘要，不复用完整营销产品卡强行统一视觉 |
| 会员 Member | 蓝色 VIP 身份及等级进度、四列权益、签到任务/日历/规则、品牌页脚 | 签到是实际 Native Mock 写操作，包含请求中、已签到和失败状态；其余入口按演示范围处理 |
| 我的 Profile | 身份、五项统计、资产、月度收支、信用卡账单、借款推广、品牌页脚 | 资产显隐；金融数据资源供应收支、账单、借款三个区域 |

区域按稳定 ID 渲染，各自表达加载、错误、空数据和内容。刷新失败保留已有数据，取消不显示失败提示。当前部分 Native 重试会刷新整页，不保证所有区域都是独立重试。禁止以“整页数据全部成功”作为显示静态头部及已有内容的前提。

## 组件封装

先按视觉与交互共性抽取，再保留业务区域差异。基础组件不认识 API ID、不发请求、不持有会话；区域组件接收显式模型与动作，VM 组织资源和业务约束。

| 层级 | Native 实现 | RN 实现 | 复用范围 |
| --- | --- | --- | --- |
| 页面容器 | `BankPage`，单 LazyColumn | `PageFrame`，单 FlatList | 区域 key、间距、可见性、滚动、页脚 |
| 基础展示 | `ui/compose/BankComponents.kt` | `shared/ui/DashboardComponents.tsx` 及域内组件 | 卡片、标题、图标、图片、金额、显隐、区域状态 |
| 共性业务展示 | `AssetPrivacyCard`、`StandardGridMenu`、`BannerCarousel`、`MarketTickerCarousel`、`AppFooterLogo` | 共享 UI 中的对应区域控件 | 资产隐私、宫格、轮播、品牌页脚；保留列数及卡片变体 |
| 首页区域 | `HomeSections.kt` | `features/home/presentation/` | 快捷行、完整产品卡、编辑内容等首页专属结构 |
| 页面布局 | `RootScreens.kt` 中四个 Screen 函数 | 四域 Screen | 区域顺序及模型映射，不承担 IO |
| 演示反馈 | `DemoActionSheet` | `DemoActionSheet` | 根页演示入口的统一说明 |

资产显隐是当前会话内展示偏好，不持久化为账号事实；两种构建模式不通过 Bridge 同步该偏好。页面私密字段必须一致遮罩，不能只隐藏总资产数字。

## Native MVVM

源码入口为 `android/biz_dashboard/src/main/java/com/entry/biz/dashboard/`。

```text
ui/compose：纯展示、状态绑定和动作转发
    ↓
presentation/pages：分域 ViewModel、PageResource、提交与取消协调
    ↓
domain/pages：页面模型与 Repository 契约
    ↑ 实现
data/pages：RootPageRepositories → NativeApiClient → demo_engine
```

`RootContentHostFragment` 从 Fragment ViewModelStore 获取四个页面 VM。宿主在 STARTED 范围收集 Shell 快照，协调器先依据认证/epoch/当前 Tab 激活、停用 VM，再发布 RenderSession，结束收集时停用资源。只允许当前已认证页活跃；创建 VM 不等于立即请求四页数据。

页面资源使用请求代次抵御迟到响应。资产 Repository 不另建永久响应缓存。签到额外校验 active、loading、submitting、checked、writeVersion 和 epoch；取消或切页后的异常不能回写新状态。写入结果不确定时先读状态确认，不自动重放非幂等写入。

ComposeView 使用 `DisposeOnViewTreeLifecycleDestroyed`。列表状态通过会话 key 与 Tab SaveableStateHolder 隔离，避免不同用户恢复私密页面滚动状态。

## RN MVVM

```text
RootHub → useRootChannels(application, epoch) → 域工厂
Route：订阅、激活/停用、导航及平台副作用
Screen：区域布局和动作
ViewModel：展示状态、动作、防重、资源生命周期
Repository / ClientState Port → ResourceClient / Store → NetworkPort
```

`useRootChannels` 在 effect 中创建并清理域通道，epoch 不匹配期间不返回旧绑定。Route 使用受控订阅读取稳定快照；Screen 不导入 NativeModule、Axios 或 fixture。MemberRoute 根据活动页及认证状态控制签到能力，不只依赖组件卸载。

RTK Query 是服务端响应缓存；客户端分类、滚动位置由封装的客户端状态管理。VM 快照是 UI 投影，不建立另一套持久响应缓存。资源 key 包含 API、排序后的参数及 epoch；最后一个订阅释放时取消请求。具体实现见[RN 技术方案](前端RN技术方案.md)。

## 数据、素材与视觉约束

- 当前演示请求经 Native Mock：RN `EndpointRegistry → TransportRouter → NativeTransport → NativeNetwork`，Native 页面经 `NativeApiClient`。两者由共享契约和 `demo_engine` 提供原型数据。
- `contracts/`、`mock/manifest.json` 及分 API JSON 是唯一资源源码，Gradle 生成任务打入 APK。运行时不依赖开发机目录或 Metro 提供 fixture。
- 普通 Mock 请求后台随机延迟 1000～3000ms，可取消；认证 Mock 与原型数据引擎分开组装。
- 共用素材来源为 `src/shared/assets`，Native `DashboardArtTask` 生成变体资源。资源映射使用白名单；根底栏使用 app 自身图标。
- Native 样式集中在共性与区域 Compose 组件；RN 使用共享主题及 StyleSheet。对齐颜色、字号、圆角、留白、图片比例与区域层次，同时验证窄屏、字体放大和系统 Insets。
- 页面只保留一个纵向虚拟列表，横向区域按需嵌套。轮播默认约五秒，只有可见且活动的页面运行，触摸时暂停；RN 还处理减少动态效果设置。

## 验证与验收

验收同时覆盖 RN 与 Native 模式：四页区域完整、素材比例、底栏选中与返回、未登录门禁、登录返回、会话切换、资源加载/空/错/刷新、签到防重及切页取消。视觉检查对照 PRD，不用构建成功替代截图验收。

RN 按变更执行 typecheck、lint、architecture:check 和相关 Jest；Native 执行 biz_dashboard/app 对应测试与编译。资源打包变更还需空生成目录构建及 APK 内容检查。命令与设备测试数据保护见[Android README](../../android/README.md)。此文不保存某次执行记录或性能结论。

源码阅读可继续看[首页 UI 新手指引](../development/首页UI新手指引.md)。
