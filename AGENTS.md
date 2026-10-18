# EntryTask 工程协作规则

适用整个仓库。默认使用中文沟通，代码标识沿用项目英文命名。此文件维护长期工程约束，不记录临时进度、账号、设备路径或未经验证的结论。

## 规则加载与资料入口

- 开始修改前查看 `git status --short`，保护用户已有改动；阅读将修改目录的 `AGENTS.md`。从根目录启动时，不假定子目录规则已自动加载。
- 修改 RN 源码，以及根目录 RN 配置、`specs/`、JS 测试时，读取 [RN 规则](src/AGENTS.md)；修改 Android 时读取 [Android 规则](android/AGENTS.md)；修改 iOS 时读取 [iOS 规则](ios/AGENTS.md)。共享契约或资源改动还需读取实际受影响平台的规则。
- 子目录规则细化对应目录；跨端不变量由本文件统一定义。遵循当前用户任务及更高优先级指令；发现规则冲突时说明具体冲突，不通过删除规则或跳过校验解决。
- 产品行为查阅 [原型需求](docs/requirements/01-prototype-requirements.md)、[API 与 Mock 需求](docs/requirements/02-api-mock-requirements.md)。总体边界查阅 [总体设计](docs/design/EtryTask%20项目设计方案.md)，按任务阅读三端方案。
- 当前运行方式查阅 [RN 工程说明](docs/development/rn-foundation.md)，初始化与日志查阅 [排障说明](docs/development/troubleshooting.md)。`android/docs/` 是历史初稿，冲突部分以新需求和 `docs/design/` 为准。
- 设计稿中的目标目录、API 总数、性能指标和规划模块不等于已实现。实际配置以源码和锁文件核实；需求以当前产品基线为准。记录偏差，按任务范围渐进落实设计，不以现有缺陷作为新代码范式，也不顺带迁移整个工程。

## 工程定位与现状

这是 RN 官方 CLI 创建的银行业务演示工程，采用按业务域和技术职责划分的模块化架构及 MVVM。当前 Android 先行，iOS 保留模板及入口适配，完整原生能力实施延期。

| 路径 | 职责 |
| --- | --- |
| `src/` | RN 组装、业务域、基础设施及平台适配 |
| `specs/Native*.ts` | 五个 TurboModule 的 Codegen 传输规范 |
| `contracts/` | 运行时桥接配置、API 策略及响应 schema |
| `mock/` | Native 本地网络使用的路由清单及分 API JSON |
| `android/` | Native 宿主、认证、网络、存储和日志实现 |
| `ios/` | iOS 工程及未来对等能力接入 |
| `tools/`、`__tests__/` | 资源与架构检查、校验器生成、JS 测试 |
| `docs/` | 需求、设计、开发及验证说明 |

当前已落地四个 RN 根页、共用组件和通用子页模板；Native 底栏驱动单个 RN Surface。RN 暴露34个本地演示API，不能据此声称37API全部场景或完整生产会话协议已验收。Android 模块为 `app`、`biz_user`、`lib_base`、`lib_net`、`lib_storage` 和 `demo_engine`，不为满足目录示意创建空模块。

## 跨端架构不变量

1. 每进程只使用一个 RN instance。Native 负责宿主、根 Tab、认证及会话权威；RN 负责四域页面和业务子页，优先复用 RN 控件。不能为页面、Tab 或登录创建第二个 runtime，也不能另建 RN 根底栏复制 Native 导航状态。
2. View 渲染，VM 生产状态和处理动作，领域契约定义业务，Repository 管数据，适配器实现 IO，组装根构造依赖。跨域只使用公开契约，禁止导入其他域的私有实现；按复杂度引入 UseCase，不机械添加空层。
3. RN 可以使用 Native 网络或自己的 HTTP 能力，由 EndpointRegistry 按 API 显式选择；禁止失败后自动切换通路。当前无后端的演示 API 使用 Native Mock，RN 不直接读取响应 fixture 冒充请求。
4. Native 持有银行凭证及持久化权威；RN 不复制凭证、持久账户库或另一套持久查询缓存。RN 查询缓存与客户端状态遵守 RN 子规则，跨端交互通过受控 Port / Codegen 适配。
5. RN Logger 必须能独立输出诊断；关键流程与 Native 通过 `flowId` / `requestId` 关联。初始化失败不能依赖“初始化成功后才可用”的日志链路。

## 启动与入口约定

以下命令在仓库根目录执行，使用 npm 和已有 `package-lock.json`，不顺带更换包管理器。依赖缺失时用 `npm ci`；新增依赖需明确用途、兼容性和锁文件变化，不做无关升级。

```sh
npx react-native run-android
npm run logs:android
```

第二条在独立终端运行；多设备可加 `-- emulator-5554`，实际 serial 以 `adb devices -l` 为准。Metro 按 `j` 打开 RN DevTools。构建终端没有 JS 输出不代表应用没有日志。

- JS 入口固定为 `src/index.js`，根组件 `src/app/App.tsx`；不恢复根目录 `index.js` 或另建入口兜底。
- 注册组件名为 `EntryTask`；Android `applicationId` / namespace 为 `com.entrytask`，Application 为 `.MainApplication`。不得在目录整理中误改安装身份或组件名。
- Android 开发入口为 `src/index`，Bundle 入口为 `../../src/index.js`；iOS 开发入口为 `src/index`，构建 `ENTRY_FILE=src/index.js`。涉及入口调整时核对 Metro、原生配置、Codegen 和组件注册的一致性。
- `HomeFragment.USE_REACT_NATIVE` 保留 Native-only 首页路径；常规 RN 修改不切换该开关来掩盖错误。

## 共享契约和运行资源

- `specs/` 只定义薄传输类型，`package.json` 的 Codegen 扫描目录保持 `specs`，生成包保持 `com.entrytask.specs`。签名变化需同步 RN adapter、Android 实现和测试，标明 iOS 对等实现是否延期；不手工修改生成接口。
- API 新增或变更同步检查 `contracts/api/endpoints.json`、schema、`mock/manifest.json`、分 API JSON、校验器和受影响消费者。契约必须明确认证、空值、分页、超时及错误语义；不虚构真实后端行为。
- `contracts/` 中的运行时 JSON 由 Metro 解析；Native Mock 资源必须进入 APK / 未来 iOS Bundle。Native 不能运行时访问开发机仓库，也不能依赖 Metro 提供 mock 文件。
- 普通 Native Mock 人工延迟按需求为随机 1000～3000ms，可取消，运行在后台；不得把延迟加入真实 RN HTTP，也不得在主线程等待。现有实现偏差应明确记录，在相关任务中修正。
- 共享资源只有一份源码；通过生成任务打包到构建目录，禁止手工维护平台副本。改变打包配置需验证空生成目录构建和最终产物资源；历史上曾出现构建成功但 APK 漏资源，不能只检查源文件存在。
- 修改 schema 后运行 `npm run contracts:generate`，提交生成的校验器并检查差异；禁止手改生成物或放宽 schema 来掩盖坏数据。

## 性能、稳定性与数据约束

- 请求、订阅、计时器与页面/runtime 生命周期配对清理；取消独立于失败。并发写入防重，旧会话或旧页面响应不能回写新状态；非幂等操作无明确契约不自动重放。
- 主线程不做阻塞网络、磁盘或密集密码计算；列表、图片、缓存及日志有界。性能结论须注明设备、变体和数据规模，不凭 Debug 体验承诺生产指标。
- 日志只记录必要白名单字段、阶段、错误码、耗时和关联编号；不打印密码、Token、Cookie、完整卡号、请求体或个人资产响应。不能为排障打开全量 body 日志。
- 当前演示工程中的认证/存储遗留问题不视为生产安全方案；不得新增明文敏感数据。修复敏感存储需设计迁移，不能默默清应用数据。
- 不编辑 `node_modules`、Pods、build 生成文件来修功能；不提交本机 SDK 路径、凭证、签名材料或设备日志。保留已有锁文件和用户工作区改动。

## 按变更选择验证

命令以实际 `package.json` / Gradle tasks 为准；执行前核对工作目录。只改文档时检查链接、路径、命令和 `git diff --check`，无需启动应用。

| 改动 | 应执行的验证 |
| --- | --- |
| RN 逻辑、组件、基础设施 | `npm run typecheck`、`npm run lint`、`npm run architecture:check`，相关 Jest 行为测试 |
| 共享 API / schema / fixture | `npm run contracts:check`，必要时生成校验器；受影响消费端测试和资源打包检查 |
| Bridge / Codegen / 入口 / Metro / Gradle | RN 检查、Android Codegen 与构建、官方 CLI 启动和设备主链路；打包变更加干净构建及 APK 内容核对 |
| Native 认证 / 导航 / 存储 | Android 对应模块测试及编译，按风险验证取消、重入、恢复和迁移 |
| 明确要求的 iOS 实施 | 遵守 iOS 子规则，核实实际 workspace / scheme / destination 后验证 |

JS 全量测试命令为 `npm test -- --runInBand`；本机 Watchman 不可用时可加 `--watchman=false`，须说明环境限制，不能通过跳过失败用例获得“通过”。架构脚本目前只覆盖部分 RN import 规则，不代表原生依赖图、循环依赖和跨域边界已全部自动检查。

新增测试聚焦错误、取消、竞态、协议及回归行为，不为纯文案或简单样式镜像实现。检查通过后无需无理由重复；构建或设备不可用时，完成其余检查并如实写明未验证项。

## 交付与规则维护

- 先完成已授权范围的实现与验证；普通可逆修改无需额外确认。只有影响需求的关键歧义或超出授权的动作才向用户说明具体问题。
- 架构、公开契约、数据迁移和运行命令变化同步相关文档；重大边界取舍记录于 `docs/adr/`，按需创建，不要求每个修复写 ADR。
- 最终说明修改结果、验证证据、未完成项和实际风险；区分“构建通过”“设备验证”“设计待实施”。不把历史测试记录当成本次结果。
- 未被请求时不提交、推送、发布或清空数据。交付前检查 diff，避免混入依赖重装、全仓格式化或无关重构。
- `AGENTS.md` 保持精简、可执行，不复制整份设计。官方加载按根至工作目录形成指令链；同目录 `AGENTS.override.md` 会优先于本文件，不把临时 override 当共享规范提交。新会话用于确认启动加载；不要修改用户级 Codex 配置来强制生效。参考 [Codex 官方说明](https://learn.chatgpt.com/docs/agent-configuration/agents-md)。

## Code Review Rules

- 阻止入口/包名/组件名漂移、第二个 RN runtime，以及为解决加载问题而移除 RN 插件或 Codegen 的改动；应修复真实配置并提供启动证据。
- 阻止业务页直接访问 NativeModule、Axios、KV 或 fixture，以及 Native 基础库新增对 RN/业务 UI 的依赖；应通过已有 Port / Repository 或任务内明确的新契约接入。
- 阻止仅靠已有 generated 目录通过的资源打包改动、跨通路错误回退，以及在日志中暴露敏感载荷的排障改动。
- 仅报告本次引入或本次范围直接相关的可执行问题，给出路径、触发条件、影响及修复方向；格式问题交给已有工具，不以待实施目标目录判定所有现存代码不合格。
