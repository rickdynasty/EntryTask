# EntryTask

EntryTask 是一个基于 React Native Community CLI 创建的银行业务教学原型，用于验证 Android Native 宿主与 RN 业务页面协作、分层架构和本地模拟业务流程。项目没有真实后端，不连接真实银行服务；页面中的账户、资产、收益、活动和操作结果均为演示数据。

本仓库采用 Android Native + React Native 的当前交付路径：Native 负责登录注册、应用外壳、根 Tab、会话与设备能力；RN 在同一个 React Native 实例中实现首页、财富、会员、我的四个业务域及其子页面。iOS 工程保留 React Native CLI 集成入口，业务 Native 能力尚未实施，详细范围见 iOS 技术方案。

## 工程架构

```text
EntryTask
├── Android Native
│   ├── app             应用组装、单 RN Host、根 Tab、Native Bridge、Mock 资源打包
│   ├── biz_user        登录注册与会话相关 Native 业务
│   ├── lib_base        基础能力与 UI/MVVM 封装
│   ├── lib_net         Native 网络基础设施
│   ├── lib_storage     Native 存储封装
│   └── demo_engine     RN 原型 API 的本地 Mock 与演示状态
├── React Native (src/) 应用组合根、业务域、共享 UI、网络/状态/日志基础设施
├── specs/              RN 与 Native 的 Codegen TurboModule 契约
├── contracts/          API、响应 Schema 与 Bridge 运行时契约
├── mock/               按 API 组织的 JSON fixture，由 Android 构建打入 APK
├── ios/                RN CLI 原生工程入口；iOS 业务 Native 能力待实施
└── docs/               需求、总体设计、端侧技术方案、UI 与开发排障文档
```

业务页面按业务域组织并采用 MVVM：RN Screen 负责呈现和事件转发，ViewModel 组织视图状态与用户动作，Domain 定义模型及能力契约，Data/基础设施负责数据访问，CompositionRoot/DI 负责组装。共享 UI 和格式化能力集中在 `src/shared/`；跨域功能通过公开契约复用。Android Native 侧按宿主组装、用户业务、基础设施与演示引擎划分模块，新增依赖遵循自上而下的职责边界。

Native 是认证、根导航、会话和受控设备能力的权威方。应用进程内只创建一个 React Native Host/运行时和一个业务 Surface；Native Tab 状态同步给 RN，RN 使用 native-stack 承载业务子页面。四个业务 Tab 不各自创建 RN 实例。

业务 API 通过 RN `EndpointRegistry → TransportRouter` 明确选择 Native 或 RN HTTP 通道；选择由 API 描述和业务需求决定，不对失败请求静默切换通道。当前原型 API 使用 Android Native 网络客户端本地拦截，按 API ID 从 `mock/` 读取 JSON，Mock 与 contracts 在构建时打入 APK，因此不依赖项目源码目录或后端。普通模拟请求随机延迟 1～3 秒。RN 自有日志与 Native 日志通过受控关联字段协助排障；日志不得记录凭证或敏感业务载荷。

## 环境准备

### React Native / Android

- Node.js `>=22.11.0`（以根 `package.json` engines 为准）和 npm。
- Android Studio、Android SDK Platform/Build Tools 36、NDK `27.1.12297006`；Gradle Wrapper 使用仓库指定的 Gradle 9.3.1。
- 安装与当前 Android Gradle Plugin、React Native Gradle Plugin 相匹配的 JDK。当前技术方案尚未锁定最终 AGP/JDK 兼容矩阵；以 Android Studio 的 Gradle JDK 设置和 `./gradlew --version` 实际结果核实，不要仅依据 Kotlin JVM target 推断 Gradle JDK。
- Android 模拟器或已启用开发者选项与 USB 调试的设备。

从仓库根目录安装 JS 依赖并运行：

```sh
npm ci
npx react-native run-android
```

CLI 会构建、安装并启动 `com.entrytask`。Metro 若未运行会由 CLI 启动；也可以在一个终端先运行 `npm start`，再在另一个终端运行 `npx react-native run-android`。Native 代码、Codegen、Android 配置或 Mock 资源变化后需要重新构建安装；纯 JS 改动可使用 Fast Refresh。

Android Studio 方式：用 Android Studio 打开仓库的 `android/` 目录，等待 Gradle Sync，选择 `app` 的 `debug` 配置并运行。两种方式共用同一个 Gradle 工程、Application 和应用包名。Android 详细说明见 [Android 项目 README](android/README.md)。

### iOS

iOS 业务 Native Shell、认证、Bridge、存储和 Mock 引擎尚未落地，不能按完整业务端验收。后续启用 iOS 时需按仓库 Gemfile/Podfile 安装 CocoaPods，并核实 Xcode、Ruby、Pods 与 RN 版本；入口保持 `src/index.js` 和组件名 `EntryTask`。架构边界及验证要求见 [iOS 技术方案](docs/design/iOS技术方案.md)。

## 开发检查与日志

在仓库根目录按改动范围执行：

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand
```

Android Native 构建及日志命令见 [Android 项目 README](android/README.md)。`npm run logs:android` 可查看设备上的应用日志；Metro/React Native JS Console 可通过 DevTools 查看。出现“原生能力暂不可用”等错误时，先记录错误码和诊断 ID，按 [排障说明](docs/development/troubleshooting.md) 检查 NativeModule 注册、Bridge bootstrap、APK contracts/mock 资源及设备日志。

## 文档导航

| 内容 | 文档 |
| --- | --- |
| RN 工程结构、MVVM、基础设施与开发命令 | [RN 项目 README](src/README.md) |
| Android 工程模块、构建、运行、Mock 资源和日志 | [Android 项目 README](android/README.md) |
| 全工程总体设计 | [EtryTask 项目设计方案](docs/design/EtryTask%20项目设计方案.md) |
| RN 端技术方案 | [前端 RN 技术方案](docs/design/前端RN技术方案.md) |
| Android 端技术方案 | [Android 技术方案](docs/design/Android%20技术方案.md) |
| iOS 端技术方案及实施范围 | [iOS 技术方案](docs/design/iOS技术方案.md) |
| 原型与 API/Mock 需求 | [原型需求](docs/requirements/01-prototype-requirements.md)、[API 与 Mock 需求](docs/requirements/02-api-mock-requirements.md) |
| 四 Tab 页面设计 | [原型分析与 RN 页面设计](docs/ui/四Tab原型分析与RN页面设计.md)、[实现与验证记录](docs/ui/四Tab实现与验证记录.md) |
| RN 工程整理与启动基线 | [RN foundation](docs/development/rn-foundation.md) |
| 启动、RN挂载、Bridge与登录逐步源码时序 | [启动与登录全链路源码详解](docs/development/启动与登录全链路源码详解.md) |
| 运行异常排查 | [Troubleshooting](docs/development/troubleshooting.md) |
| Android 早期架构/规范/路线资料 | [Android docs 索引](android/docs/README.md) |

需求、设计方案和当前源码实现是不同层次的材料：设计方案描述目标架构，项目 README 与源码描述当前实现；端侧未完成的能力会明确标为待实施，不以目标设计代替交付证据。
