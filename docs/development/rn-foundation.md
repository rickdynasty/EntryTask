# RN 工程整理与运行说明

日期：2026-10-18。当前交付为 RN-P0 工程基础及首页纵向样板，不是四 Tab 全部银行业务已实施。

## 运行

在项目根目录执行：

```sh
npm ci
npx react-native run-android
```

官方命令启动 Metro、构建/安装 `com.entrytask`，由 `.MainApplication` 提供唯一 ReactHost；Native 登录后进入首页。当前登录与注册以 Native 实现为准；最近的 Android 调整已采用本地注册账号校验，不应再依赖先前的种子演示账号。

Android Studio 直接启动同一 app：HomeFragment 的 `USE_REACT_NATIVE=true` 挂载 RN，需 Metro；改为 `false` 展示原 Native 文本，不挂载 RN 内容，其余 RN 工程配置无需改回。入口、包名及注册名不随该开关改变。底部其他三 Tab 仍为现有 Native 占位页，本次不冒充已实现四域业务。

Release 构建：`cd android` 后执行 `./gradlew :app:assembleRelease`。该包沿用项目现有演示签名，不用于正式发布；内置 Bundle，不依赖 Metro。本次未改 applicationId、namespace 或 Manifest Application 注册名。

## 入口约定

| 位置 | 当前值 |
| --- | --- |
| JS 注册入口 | `src/index.js`；根目录不保留第二个 index.js |
| RN 根组件 | `src/app/App.tsx` |
| package.json main | `src/index.js` |
| Metro projectRoot | 仓库根；src/specs/contracts 在同一模块图 |
| Android 开发入口 | MainApplication `jsMainModulePath="src/index"` |
| Android 打包入口 | app/build.gradle `react.entryFile=../../src/index.js` |
| Android 包/Activity | `com.entrytask` / `.MainActivity` |
| AppRegistry/Native componentName | `EntryTask`，与 app.json 一致 |
| Codegen | package.json `jsSrcsDir=specs`，生成包 `com.entrytask.specs` |
| iOS 开发/打包入口 | `src/index` / `ENTRY_FILE=src/index.js`；仅同步入口，未实现或构建iOS原生模块 |

Metro 兼容工具请求 `/index.bundle` 时重写为 `/src/index.bundle`；已经是 `/src/index.bundle` 的请求不再次重写。JSON 保持 Metro 默认 sourceExts，无需把 json 错配成二进制 assetExt。

## 工程边界

- `src/app`：组装根、bootstrap与生命周期。
- `src/features/home`：domain契约、data实现、MVVM展示、factory/public入口。
- `src/infrastructure/network`：EndpointRegistry、TransportRouter、NativeTransport、RnHttpTransport（真实 Axios 路径）；接口决定通路，失败无自动换路。
- `src/infrastructure/state`：单应用图的 Redux Toolkit Store / RTK Query ApiService；服务端data仅query缓存，客户端slice仅UI选择。
- `src/infrastructure/logging`：RN独立 Logger，有界内存、开发输出和Native关联sink。
- `src/platform/native`：唯一可导入 specs 的适配层；Native模块缺失明确错误，不用JS Fake伪造成功。
- `src/shared`：无域业务逻辑的控件、token与错误类型。
- `contracts`：运行时endpoint/bridge配置和JSON Schema，参与Metro模块解析及Android assets打包。
- `specs`：五个最小Codegen规范；Android实现置于app/bridge适配包，不改应用包名。
- `mock`：Native网络独享的JSON资源，RN业务不能直接import响应JSON。

当前提供 bootstrap、网络执行/取消、资产显隐typed存储、只读会话快照、关联日志五个模块的最小能力；完整认证往返、根Tab事件协议、成员事务库、页面导航等仍按设计后续任务推进。认证继续使用已有Native UI，本次补齐完整内存会话建立，不让RN持有凭证。尚未接入的能力不在bootstrap capabilities中宣称存在。

## Mock与新增API

当前注册4个Native路由：登录、注册、首页内容、产品分页；RN可调用后两项。此处只统计已实现路由，不宣称需求中的37API均已完成。

1. 在 `contracts/api/endpoints.json` 增加稳定apiId、method/path、transport、authScope、timeout及schema；本地演示用nativeMock。
2. 新建对应响应schema，运行 `npm run contracts:generate`，生成无运行时schema编译的校验函数；生成物需提交。
3. 在 `mock/<domain>/<api>/response.*.json` 新建数据，在manifest注册精确method+path、handler与exposure；不能仅改Kotlin内联字符串。
4. 自定义状态操作新增Native handler及校验；静态只读API可复用fixture handler。带动态详情路径的API应新增明确路径模板匹配，当前样板只实现静态路径，禁止误称已支持任意模板。
5. 运行资源/架构/单测并构建。Gradle Variant API登记的generated assets会让普通CLI/Android Studio/Release构建自动校验并打包，缺文件/坏schema阻断构建。

APK中可查看 `assets/mock/`、`assets/contracts/`。assets只读：新注册账号验证数据落Native模拟存储，不反写随包JSON。登录注册响应字段从对应JSON模板安全填充，非字符串拼接；本期网络库内已有账号Mock仍是过渡实现，尚未完成方案中的独立demo_engine/Room迁移。

Native Mock随机等待1000～3000ms，等待位于OkHttp worker且可取消；未知API返回错误、不放行公网。RN HTTP独立使用Axios，当前没有产品真实服务端点；不可随意把本地Mock的保留域名改成rnHttp。注册表可为后续批准的公开HTTPS服务增加rnHttp，银行凭证不可发给该通路。

## 质量检查

```sh
npm run typecheck
npm run lint
npm run contracts:generate
npm run contracts:check
npm run architecture:check
npm test -- --runInBand
npm run bundle:android
```

Jest使用与RN0.86配套的官方preset，ESM转换覆盖Redux依赖，别名与Babel/TS/Metro一致。单测覆盖无原生能力的可重试初始化、双通路无回退、接口策略、schema拒绝坏数据、RTK查询共享/取消订阅、Logger脱敏/背压。

当前Native桥接返回的是最小`outcome/data/error`包；完整boot/runtime/session上下文、事件协议和生产级超时/缓存/会话协调仍是总体设计的后续阶段。不要把基础样板当作所有中大型稳定性目标已完成。

## 本次验证记录

环境：Node22.23.3、JDK17、RN0.86、Android API37 arm64模拟器。验证日期2026-10-18。

| 项目 | 实际结果 |
| --- | --- |
| `npx react-native config` | 识别applicationId/packageName=com.entrytask、MainActivity=.MainActivity |
| 根目录 `npx react-native run-android` | 构建/安装/启动成功；Native演示账号登录后看到RN首页和产品JSON数据 |
| Codegen | `:app:generateCodegenArtifactsFromSchema`成功，五个spec生成到com.entrytask.specs |
| TypeScript / ESLint / 架构边界 / 资源校验 | 通过 |
| Jest | 4个suite、8个测试通过 |
| Debug/Release构建 | 均通过；最后一轮按arm64构建，之前Debug默认全ABI构建也通过 |
| APK资源 | contracts和mock字节与源码一致；Release包含Hermes字节码及11个Mock JSON |
| Release离线 | 停止Metro、模拟器开启飞行模式后看到RN首页及Mock产品；确认包不可调试；验后恢复原飞行模式关闭状态 |
| Native-only Home切换 | 保留一处USE_REACT_NATIVE开关及原XML路径；未单独安装false变体回归，不把它记成已设备验证 |
| iOS | 只同步入口，未安装Pods/构建/运行；原生能力仍延期 |

Release沿用既有debug签名配置、R8关闭等发布基线，当前产物仅作演示。现有Gradle弃用提示、SDK工具XML版本提示和Watchman recrawl提示未作为本次无关升级处理；均未阻断上述实际验证。

## 初始化失败与日志

排障入口与2026-10-18资源缺失修复见 [Android / RN 初始化与日志排障](./troubleshooting.md)。终端独立运行 `npm run logs:android`，再复现问题。此前验证记录不代表空生成目录构建已经正确；本次修复明确补上生成任务依赖并重新验证 APK。
