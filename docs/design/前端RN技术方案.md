# 前端 RN 技术方案

## 范围与入口

RN 实现首页、财富、会员、我的四域页面及业务子页；认证、根 Tab、会话权威与受控 Native 能力由宿主提供。每进程一个 RN runtime，一个业务 Surface，不增加 RN 根底栏。

入口固定 `src/index.js`，根组件 `src/app/App.tsx`，注册名 EntryTask；Codegen 规范在根 specs。仓库根保留 package.json、锁文件及 Metro/Babel/TS/Jest 配置。版本以 package-lock.json 为准，业务开发不顺带升级 React/RN 或更换状态库。

src/index.js 只注册组件，Bundle 求值阶段不初始化 Store、请求业务数据或拉起认证。App 通过 lazy useState(createApplication) 为当前挂载 Surface 创建应用图，组合根统一组装依赖。

## 目录与依赖方向

| 目录 | 职责 |
| --- | --- |
| app | CompositionRoot、bootstrap、全局生命周期、导航与会话边界 |
| features/<domain>/presentation | Route、Screen、VM 和域内展示组件 |
| features/<domain>/domain | 领域模型、Repository/ClientState 契约 |
| features/<domain>/data | DTO 映射、Repository 实现与资源访问 |
| features/<domain>/di、public | 工厂、最小跨域公开接口 |
| infrastructure/network | EndpointRegistry、TransportRouter、错误与响应验证 |
| infrastructure/state | StoreFactory、RTK Query 与客户端状态封装 |
| infrastructure/logging | 独立 Logger、白名单、有界缓冲、Native sink |
| platform/ports、platform/native | 无实现依赖契约、Codegen 适配与协议解码 |
| shared | 基础 UI、主题、值对象、格式、素材；不承载跨域业务规则 |

依赖为 View → VM → Domain，Data 实现 Domain，app/feature factory 组装实现。Domain 不依赖 React、Redux、Axios、Codegen；业务不直接导入 NativeModule、fixture 或其他域私有实现。复杂流程按需使用 UseCase，不增加空转发层。

## MVVM 与应用图生命周期

Route 绑定 VM 订阅、激活/停用和导航副作用；Screen 接收状态及动作，只负责区域布局。VM 处理展示状态和业务动作，通过 Repository/Port 访问数据，不持有组件或导航对象。

RootHub 使用 useRootChannels(application, epoch)，在 effect 中创建/清理域通道；不在 render/useMemo 中执行带客户端状态初始化的工厂。绑定仅在 epoch 匹配时有效，准备期间不展示旧用户页面。RootPages 按 epoch 隔离四页状态。

ResourceViewModel 使用请求代次、活动状态和取消隔离旧响应；资源替换保留原激活状态，非活动分类切换不主动 IO。会员提交仅在当前活动且已认证时允许，切页/退出后的旧异常不能修改新提交状态。结果不确定先读取签到状态，不自动重放写入。

当前根页 VM 的资源职责：Home 为内容/产品，Wealth 为内容/产品/资产，Member 为内容/摘要/签到，Profile 为身份/资产/金融数据。避免为未使用的资源订阅或保留冗余模型。

## 状态与缓存

| 类型 | 所有者 | 生命周期 |
| --- | --- | --- |
| Native 会话、scope、epoch、根 Tab | Native 权威，ShellViewModel 只读镜像 | 按宿主快照同步 |
| 服务端资源 | RTK Query sharedApi | 资源 key 含 API、排序参数、epoch |
| 分类、滚动位置等客户端状态 | Redux Toolkit slice 的受控 Port | 页面/会话作用域 |
| 加载、错误、提交等展示投影 | VM 稳定快照 | 激活/取消/销毁 |
| 密码显隐、弹层等局部呈现 | 对应 UI 或页面状态 | 不持久化账号事实 |

当前 createAppStore 的 keepUnusedDataFor 为全局 60 秒，并未实现逐敏感端点差异化 TTL。会话切换仍须释放旧资源，不能只依赖 TTL 保护隐私。

ResourceClient 的 open 使用 forceRefetch 与引用计数；最后一个订阅释放时 abort/unsubscribe。read/mutate 经 NetworkPort 和生成校验器；mutate 当前失效通用 Resource tag，未实施所有域细粒度 tag。VM 快照只是响应投影，不再复制一套持久查询缓存。

## 导航与默认门禁

Native 底栏驱动四根页；React Navigation native-stack 承载业务子页。RootRoute/DetailRoute 默认认证，HelpCenter 为公开白名单；调用方传 requiresAuth=false 不能绕过白名单策略。

启动优先由 MainActivity 提前认证，RN 挂载后仍经 NativeSession.getSnapshot 校准。未认证时通过 NativeHost.command 请求认证，ACK 不代表成功，最终以 Native 会话快照为准。取消/展示失败显示门禁及显式重试，不在 effect 中自动循环登录。

RootPages 的多数演示入口显示 DemoActionSheet；实际 HelpCenter 与通用子页使用现有导航。不能将演示弹层描述为完整银行业务链路。首页区域与共性组件见[四 Tab 方案](首页与四Tab技术方案.md)。

## 网络封装与 Mock

```text
Repository → ResourceClient / NetworkPort
 → EndpointRegistry → TransportRouter
    ├── NativeTransport → platform/native → NativeNetwork.execute/cancel
    └── RnHttpTransport → Axios
 → 统一错误归一与生成响应校验器 → 展示投影
```

每个 API 显式指定通路，失败不自动切换。当前演示 API 经 Native 本地 Mock，请求 ID 与 epoch/scope 受控；RN 不 import mock JSON 假装发送请求。独立 rnHttp 仅用于明确 HTTPS 配置的公开服务，不接触银行凭证，公开服务 401 不触发银行退出。

共享 contracts JSON 由 Metro 正常解析。响应 schema 在工具阶段生成校验器，首屏不运行 Ajv 编译；端点、schema、manifest、fixture、消费者变更需同步验证。资源最终随 APK 打包，运行时不依赖开发机。

取消独立于超时、HTTP/业务错误和解析错误。写操作防重且无明确幂等契约时不自动重试。

## Native 能力适配

仅 platform/native 读取 specs 模块。NativeHost 提供 bootstrap/command/onHostEvent；NativeSession 提供 getSnapshot；NativeNetwork 提供 execute/cancel；NativeStorage 提供 typed read/write/remove；NativeTelemetry 提供 emitBatch。

adapter 解析并验证 JSON、outcome、能力与响应形状，缺模块、协议错误、超时和取消分别归一；业务只依赖 Port。当前 iOS Native 对等实现待实施，不通过 JS 假成功绕过能力握手。

## 组件、性能与日志

四根页使用 PageFrame 的单 FlatList，稳定区域 key、页脚、可见性和滚动恢复统一封装；ready 控制恢复时机，不作为整个页面渲染门槛。资产、宫格、产品、轮播和品牌区域按共性复用，业务规则留在域内。

资源、订阅、timer 在活动页和 runtime 生命周期内配对清理。轮播可见时运行、触摸暂停，处理减少动态效果。控件覆盖安全区、键盘、字体放大及可访问标签；不为小组件机械添加 memo。

Logger 独立输出 Console，bootstrap 前即可诊断；NativeTelemetry 在握手前连接，失败不阻断业务、不递归投递。记录 bootstrap、门禁、页面激活、Bridge、请求阶段及安全关联 ID，不输出原始 Error、请求体、Token 或资产响应。日志缓冲有界。

## 开发与验证

仓库根运行 npm ci、npx react-native run-android；Native 代码、Bridge、入口或 assets 变化必须重新构建安装。静态与行为检查：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm test -- --runInBand
npm run contracts:check
```

schema 改动先 contracts:generate；入口/Metro/Codegen 改动补 bundle:android、Android 生成/构建和官方 CLI 设备主链路。架构脚本只覆盖部分 import 规则，不代表完整依赖图已自动验证。设备场景包含认证、四页、切页取消、签到防重、会话隔离和公开帮助；不以 Jest 通过替代混合集成验证。

运行与排障见[工程说明](../development/rn-foundation.md)、[RN README](../../src/README.md)和[排障指南](../development/troubleshooting.md)。
