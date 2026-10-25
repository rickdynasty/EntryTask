# EntryTask RN 工程说明

> 新版必做 UI：RN 与 Android Compose 双端实现及验证见[实施记录](../docs/development/新版四Tab实施记录.md)；旧 XML 根页说明属于前一版实现。

EntryTask 是由 React Native Community CLI 创建的原生宿主银行业务原型。RN 承载首页、财富、会员、我的四个业务域及其详情子页；Android 原生宿主持有唯一 React Native 实例、底部 Tab、登录注册、会话、网络 Mock、存储和 Native 日志能力。iOS 保留工程入口，业务桥接尚未实施。

## 工程架构

```text
src/index.js
    └─ app/App.tsx                 应用根、启动状态、错误呈现
       └─ app/CompositionRoot.ts   依赖组装、单份业务图、日志/网络/资源缓存
          ├─ app/navigation/       Native Shell 同步与 RN native-stack 子页
          ├─ features/<domain>/    业务域：presentation/domain/data/di/public
          ├─ infrastructure/       Logger、API Registry、TransportRouter、RTK Query
          ├─ platform/             Port 契约与 Codegen NativeModule 适配
          └─ shared/               通用 UI、展示状态、领域错误、格式化与素材

specs/                             TurboModule Codegen 契约
contracts/api/                     API 路由和响应 Schema
contracts/bridge/                  Native/RN Runtime 协议
mock/                              仅供 Android Native Mock 拦截器读取的逐 API JSON
```

四根页由各业务域的`*Route`管理资源订阅与生命周期，`*Screen`只接收展示状态和动作；公共列表/轮播/隐私卡/页尾在`shared/ui/DashboardComponents`，分类与滚动状态通过`PageUiStatePort`连接同一Redux Store的客户端slice。

业务页面采用 MVVM。Screen 负责 UI 呈现和事件转发；ViewModel 负责视图状态、选择和动作流程；Domain 定义模型及 Port；Data/基础设施访问 Native 或 RN HTTP 能力；`di/` 与 `CompositionRoot` 负责依赖组装；`public/` 是跨域入口。共享 UI 不引入业务规则，跨域依赖只能通过公开接口。

Native Shell 是根导航和身份会话的权威来源。MainActivity 启动先校验 Native 会话并直接打开认证页，不等待 RN；Native 认证首帧后可预热唯一引擎/Bundle（默认关闭，构建时显式启用），首次认证返回/失败后才挂载 RN，RN 经 NativeSession Bridge 再校准会话。页面默认登录后访问，集中策略仅公开帮助中心；受保护子页经 NativeHost Bridge 请求认证，取消留门禁、手动重试。认证成功由 Native 发布快照后放行业务模型；Native 底部 Tab 驱动同一个 RN Surface，RN native-stack 展示共用子页。未登录和子页状态隐藏 Native 底栏；revision 与 sessionEpoch 均拒绝旧状态回退。详见 [登录门禁 ADR](../docs/adr/011-required-native-authentication.md)。

业务数据通过 `EndpointRegistry → TransportRouter → NativeTransport / RnHttpTransport`。每个 API 明确选择传输方式，错误时不自动切换通道。本原型的 API 走 Android 独立本地 Mock 网络客户端，按 API ID 选择 APK 中 `../mock` JSON；RN 不直接导入响应 Fixture。响应经生成的 Schema validator 校验，再进入 RTK Query 缓存。签到、领取和消息已读由 Native Mock 保存事实，RN 在成功后使相关查询失效并重新读取。

RN Logger 采用白名单字段，只记录事件、模块、错误码、耗时和关联 ID；关键网络流程通过 `requestId`、启动流程通过 `flowId` 和 Native 日志对照。不得记录关键词、账号、凭证、请求体或个人资产值。日志可在 Metro/RN Console、Android Studio `ReactNativeJS`/`EntryTask.RN` 查看。

关键日志事件覆盖 `bootstrap.started/completed/failed`、`target.opened`、`authentication.required`、`session.epoch.changed`、`request.started/completed/failed/cancelled`、分类/分段选择、Native 命令状态、签到/领取动作状态及消息已读。业务日志只带受控 `pageId`，网络日志额外带 `apiId`、`transport`、`requestId`、`flowId` 和耗时；搜索词、用户 ID、资源 ID、请求体和金额不进入日志。

## 源码结构

```text
src/
├── index.js
├── app/
│   ├── App.tsx
│   ├── CompositionRoot.ts
│   └── navigation/
├── features/
│   ├── home/        # 首页
│   ├── wealth/      # 财富与资讯
│   ├── member/      # 会员、签到、权益
│   ├── profile/     # 我的、身份和资产摘要
│   ├── messaging/   # 消息未读及已读动作
│   └── services/    # 目录、搜索、通用详情和受控目标
├── infrastructure/
│   ├── logging/
│   ├── network/
│   │   ├── transports/
│   │   └── generated/ # 由 Schema 工具生成，禁止手工编辑
│   └── state/
├── platform/
│   ├── ports/
│   └── native/
└── shared/
    ├── assets/
    ├── domain/
    ├── formatting/
    ├── presentation/
    └── ui/

specs/Native*.ts    # Codegen TurboModule 类型契约，不放 Native 实现
```

当前 RN/API 基线由根 `../package.json` 与 `package-lock.json` 锁定：React Native 0.86、React 19、TypeScript、Hermes、新架构、Redux Toolkit/RTK Query、React Navigation native-stack、react-native-screens、react-native-svg。根目录保留 RN CLI、Metro、Babel、TypeScript 和 Jest 配置；Metro、Android Bundle 与 iOS Bundle 入口统一为 `src/index.js`。Codegen 扫描 `specs/`，生成包名为 `com.entrytask.specs`。

## 开发与检查

```sh
npm ci
npm start
# 另一终端：
npx react-native run-android
```

常用静态检查和资源检查：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm run contracts:generate  # 修改 API Schema 后重新生成 validator
npm test -- --runInBand
npm run logs:android         # 查看 Native/ReactNativeJS 日志
```

Android 也可在 Android Studio 打开 `../android`。普通 RN 热更新不会更新 Native Codegen、Android 资源或 Manifest；Native Bridge/API 资源变化后须重新构建安装。业务包名为 `com.entrytask`，注册组件名为 `EntryTask`。`HomeFragment.usesReactNative()`读取构建期配置：官方CLI使用RN页面，Android Studio使用Compose Native四根页；切换需重新构建安装，详见[双页面模式](../docs/development/native-dashboard.md)。

Mock API、数据规则、设备截图和已知差异见 [四 Tab 实现与验证记录](../docs/ui/四Tab实现与验证记录.md)；逐页原型拆解见 [四 Tab 页面设计](../docs/ui/四Tab原型分析与RN页面设计.md)；运行及日志排障见 [RN 工程说明](../docs/development/rn-foundation.md)。

## 边界

本仓库是教学原型，没有真实银行后端。示例收益、卡券、资产及金融操作都不代表真实账户或服务。Android 是当前已实现的 Native 宿主；iOS 业务桥接、正式认证与会话、安全存储、生产网络服务仍需后续按技术方案实施。
