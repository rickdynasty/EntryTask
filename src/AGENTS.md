# RN 子项目规则

适用于 `src/`；继承根 [AGENTS.md](../AGENTS.md)。根规则也要求修改 RN 配置、`specs/` 和 JS 测试时读取本文相应约定。不要新增 `rn/` 子工程或第二份 package.json 来迁移现有布局。

详细依据：[RN 技术方案](../docs/design/前端RN技术方案.md)、[当前工程说明](../docs/development/rn-foundation.md)。现有版本由根 `package.json` 和 lockfile 管理，React 19 / RN 0.86 / Hermes / 新架构基线不随业务任务升级。

## 目录与依赖

| 路径 | 所有权与边界 |
| --- | --- |
| `app/` | App、CompositionRoot、bootstrap、全局生命周期及后续导航组装 |
| `features/<domain>/presentation/` | Screen、域内组件、ViewModel 和客户端展示状态 |
| `features/<domain>/domain/` | 领域模型、Repository / ClientState 契约、必要的 UseCase |
| `features/<domain>/data/` | DTO 映射、Repository 实现及资源访问 |
| `features/<domain>/di/`、`public/` | 工厂和最小公开接口；跨域只从 public 接入 |
| `infrastructure/network/` | 接口策略、路由、错误与 schema 校验、双 Transport |
| `infrastructure/state/` | StoreFactory、RTK Query ApiService 和生命周期管理 |
| `infrastructure/logging/` | 独立 Logger、安全字段、缓冲和 Native sink |
| `platform/ports/`、`platform/native/` | 无实现依赖的 Port；Codegen 模块适配与协议解码 |
| `shared/` | 跨域基础 UI、主题、通用值与错误；不能容纳业务规则或万能服务 |

- 按 feature-first 扩展 `home`、`wealth`、`member`、`profile` 等实际需要的业务域；服务、消息有独立业务语义时按设计归域，不全部塞进 shared。
- View → VM → 领域契约；Data 实现领域契约并使用基础设施/平台 Port，依赖注入由 app/feature factory 完成。Domain 不导入 React、React Native、Redux、Axios、Codegen 或 Data 实现。
- Screen 接收状态和动作；Route / app 绑定 VM 生命周期与订阅。VM 不持有组件、导航实例、NativeModule，不直接调用 IO，不在 render 中创建 VM 或请求。
- 复用先域内，再提升确有跨域需求的无业务控件；使用 `shared/ui` 主题与状态组件。避免全局 components/services/utils 平铺及多层 barrel 循环依赖。

## 状态、网络与平台适配

- 使用已有 Redux Toolkit / RTK Query 封装。服务端资源由 query 缓存管理；slice 管客户端状态。不要把同份响应再复制到 VM、slice、自研资源仓或持久缓存。
- VM 通过 Repository / ClientState Port 访问状态，组件通过项目订阅/selector 绑定；不直接使用端点网络 Hook 绕过 VM。快照稳定且不可变，订阅按作用域释放。
- 当前 `createApplication` 为挂载的业务 Surface 创建应用图；保持唯一宿主与当前 Store / ApiService 生命周期，避免在组件 render、Tab 切换或认证时重复创建。扩展为 runtime 级所有者应按设计完整处理销毁，而不是叠加全局单例。
- 所有业务请求经 `EndpointRegistry → TransportRouter → NativeTransport / RnHttpTransport`。只有 RN HTTP transport 使用 Axios，页面不直接 `fetch` / XHR / Axios；禁止 Native 失败后走 RN HTTP 兜底。
- `rnHttp` 必须有明确 HTTPS 服务配置与认证策略；当前只支持独立公开服务，不带银行凭证，公开服务的 401 不触发银行会话退出。Mock 保留域名不能当真实服务。
- Native Mock 通过 apiId 调用，不从 RN import `mock/` 响应。共享 JSON 用 Metro 正常 JSON 模块解析；schema 校验器由工具生成，不在首屏运行 Ajv schema 编译。
- 只有 `platform/native/` 导入 `specs/Native*.ts` 并读取模块；现有 NativeTransport 通过该层暴露的模块适配。新增能力继续收敛在 Port / adapter，不向业务暴露原始 NativeModule。
- Bridge JSON 先解析、校验 outcome 和数据再使用；缺模块、坏 JSON、超时、取消、HTTP/业务错误分别归一。不能通过 `any`、强制断言或忽略类型检查接受未知数据。
- 存储通过 Native typed key 能力；不引入 AsyncStorage 保存业务事实或凭证。会话、用户 scope 由 Native 决定。

## 生命周期、UI 与日志

- 激活/停用、订阅、请求取消、timer 和 dispose 必须配对；清理可重复调用。异步结果检查活动状态/会话版本，旧响应不覆盖新状态。
- 页面覆盖首载、空态、错误、刷新和适用的提交状态；刷新失败保留已有内容，取消不当作操作失败。写操作防重入，关键结果进入状态而非只发 Toast。
- 长列表使用合适的虚拟列表；避免无界渲染、重复派生大对象、全 store 订阅和同步重计算。按测量结果优化，不为小组件机械添加 memo。
- 控件处理 safe area、键盘、字体放大、可点击区域与 accessibilityLabel；RN 业务内容不复制 Native 根 Tab / 认证 UI。
- 使用独立 Logger 记录 bootstrap、页面及核心动作、Native 调用、请求开始/完成/失败/取消；沿用白名单字段和关联 ID。错误用对应 severity，禁止输出原始 Error/request/state 作为字段。
- NativeTelemetry 在 Host 握手前连接；其失败不能阻塞业务或递归投递日志。保留有界缓冲和 Console 回退，初始化错误展示安全提示、错误码及诊断编号；不用重试按钮掩盖不可修复的安装资源缺失。

## 修改配置时必须联动

- 入口仍为 `src/index.js`；根目录保留 package.json、app.json、Metro/Babel/TS/Jest 配置，不能创建根 `index.js`。
- 新增别名同步核对 TS paths、Babel/Metro 实际解析和 Jest mapper。Metro 仍以仓库根解析 `src/`、`specs/`、`contracts/`；不把 JSON 改为无法 import 的二进制资源。
- `Native*.ts` 留在根 `specs/`，Codegen `jsSrcsDir=specs`；签名变化读取 Android 子规则并构建原生生成物。当前 iOS 未实现不能以 JS 假模块伪造成功。
- 新增原生依赖先核对 RN/React/新架构兼容和 autolinking；不同时引入 Zustand、另一套 Query 或导航库承载相同职责。设计中的 React Navigation 尚未接入，相关任务再按兼容验证引入，不假定已安装。

## 验证与交付

以下命令在仓库根执行：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm test -- --runInBand
```

- 根据改动运行相关行为测试；基础设施、共享状态或 bootstrap 变化运行全量 Jest。测试使用 Fake Port / Repository，覆盖状态与调用语义，不在测试中依赖真实 Native 或直接复制实现。
- 修改 schema / endpoint 时运行 `npm run contracts:generate`（schema 需要时）及 `npm run contracts:check`；提交生成物并检查确定性差异。
- 修改入口、别名、依赖或 Metro 时补 `npm run bundle:android` 和官方 `npx react-native run-android`；Native 改动须重新安装，仅 Fast Refresh 不算验证。
- Android 登录后验证 RN 页面、真实 Native Mock 请求和关键日志；保留 Native-only 首页分支。没有设备时明确未验证，不能把 Jest 通过写成混合集成成功。
- 纯文档/样式修改按风险选择检查，不要求无关全量原生构建；性能指标只报告实测值。

## Code Review Rules

- 检查 UI/VM 是否跨过 Repository / Port 直接 IO、跨域导入私有实现，以及 query 响应是否重复保存；应沿既有封装修正。
- 检查取消/卸载后回写、重复 store/VM/订阅、错误吞掉、敏感日志和网络失败换路；提供具体触发场景，不以库名本身作为问题。
