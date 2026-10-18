# Android 子项目规则

适用于 `android/`，继承根 [AGENTS.md](../AGENTS.md)。依据 [Android 技术方案](../docs/design/Android%20技术方案.md)；`android/docs/` 中旧 Native 业务页方案不再适用。业务任务默认保留现有 CLI、包名及构建链，不重新初始化工程。

## 模块边界与渐进实施

| 当前模块 | 当前职责与新增代码约束 |
| --- | --- |
| `app` | MainApplication、MainActivity、根 Tab、Fragment / RN 宿主、五个 Bridge 适配和资源打包组装 |
| `biz_user` | Native 认证 UI、MVVM、Repository 与会话实现 |
| `lib_base` | 既有基础能力及 UI/MVVM 封装；不继续加入业务/RN 代码 |
| `lib_net` | 现有 OkHttp / Retrofit 与过渡 Mock 实现；新增通用网络不识别业务 UI |
| `lib_storage` | 现有 Native 存储封装；禁止向 RN 开放任意 key/path 或凭证 |

设计中的 `lib_contract`、`lib_ui`、`biz_user_api`、`lib_rn_host`、`lib_bridge`、`demo_engine` 等尚未全部创建。按任务需要迁移到目标职责，不能假定存在，也不能为了单个修复一次拆完所有模块。

- app 是组装入口，不增加银行业务规则和 DAO；四域业务页面及子页由 RN 实现。认证和 Native Shell 使用 MVVM。
- 新代码遵循 View → VM → domain 契约 → data 实现，按业务域组织。技术库不新增对 app、biz、RN 的反向依赖；已有 `lib_net → lib_storage` 等偏差不作为新依赖范例，相关迁移通过接口注入解除。
- 跨业务实现不直接依赖，使用公开契约；默认 `implementation`，只有公开签名确有需要才用 `api`。不要借偶然传递依赖省略实际使用模块的声明。
- 保持现有 `com.entrytask`、`com.entry.biz.user`、`com.entry.lib.*` 路径。`com.entry.lib.stroage` 为现有拼写遗留；只有范围明确的迁移任务才整体改名并验证 import/存储兼容，不零散“纠错”。
- Kotlin 为主，沿用相关模块风格；Groovy/KTS 可共存，不为统一格式重写构建脚本。不生成无职责的 Manager、万能 Base 类或 ServiceLocator。

## MVVM、线程与数据

- View 负责渲染及平台副作用，VM 暴露只读状态/动作，不持有 Activity、Fragment、View 或 NavController。已有契约/基类先核实再扩展，不增加公开可变状态或把成功回调传入 Repository 作为业务事实。
- Compose 使用生命周期感知状态收集；Fragment 在 viewLifecycleOwner 范围订阅，销毁时清理引用。关键认证结果进入可恢复观察的状态；提交防重入，失败恢复可操作状态。
- 使用结构化协程或可取消请求，作用域明确；CancellationException 继续抛出，不吞 Throwable，不用 GlobalScope。阻塞 IO、Mock 等待、密码派生不在主线程。
- 会话权威在 Native。注册成功必须建立完整会话；RN 不接触凭证。退出、会话更换、超时和取消应使旧任务结果失效，不靠多个 Boolean 各自代表登录状态。
- 金额不用 Float/Double 作为持久精确值。持久化变更说明 key/scope、类型和迁移；不以清数据、破坏式升级或把 Base64 当加密解决问题。测试不得覆盖用户现有演示账号而不恢复。

## 单 RN 宿主与 Codegen

- `MainApplication.reactHost` 是进程内唯一 Host，沿用 `getDefaultReactHost`、RN 新架构和 Hermes。不要在 Fragment、Tab 或认证 Activity 创建第二个 Host / runtime。
- 当前 `app/.../base/BaseFragment` 承载 RN 集成；使用稳定 `R.id.rn_content` 和 child Fragment tag 恢复，先查找已有 Fragment，不重复 add/attach。不使用 `commitAllowingStateLoss` 掩盖生命周期错误。
- 保留 `HomeFragment.USE_REACT_NATIVE` 的两种模式。修改 RN 默认路径不得破坏 Native-only 内容；测试时临时修改开关必须恢复并说明验证范围。
- 当前原生适配在 `app/src/main/java/com/entrytask/bridge/`，继承根 `specs/` 生成的 `com.entrytask.specs.Native*Spec`，由 `EntryTaskPackage` 注册。未来抽模块需按设计完整迁移，不能新增旧桥双实现绕过 Codegen。
- 改签名联动 `specs/`、package.json Codegen 配置、RN adapter、Native 实现/注册及测试；生成源码留 build 目录。bootstrap 返回实际支持的能力，不宣称尚未实现的方法。
- Bridge 边界验证 JSON、长度、apiId/exposure、key 和数据形状；协议错误结构化返回。Promise 终态不能重复，runtime invalidate 时取消请求并释放监听/宿主引用。

## 网络、Mock 与打包

- Native 网络是提供给 RN 的能力，不是 RN 所有 HTTP 的强制唯一入口；不拦截 RN 公共 HTTP、Metro 或任意全局网络请求。
- 业务经当前 NetManager/受控 client 请求，Mock 本地终止且拒绝未知 API，不因未命中访问公网。新 API 使用 manifest 精确匹配和对应 JSON，不新增 Kotlin 内联响应或 `contains(path)` 路由。
- 现有认证 Mock 是过渡实现，不能因它仍有内联数据/存储偏差而继续复制；与本次任务相关时迁移到设计的 API fixture / handler 边界，避免破坏既有账号行为。
- 普通 Mock 延迟 1～3s，后台等待、支持取消。超时、取消、HTTP、业务错误与解析错误分开；非幂等写操作不自动重放。
- `app/build.gradle` 通过 `androidComponents.onVariants` / `variant.sources.assets.addGeneratedSourceDirectory` 注册每个变体的 RuntimeAssetsTask；`validateRuntimeResources → prepare<Variant>RuntimeResources → merge<Variant>Assets` 依赖必须真实存在。
- **不得退回仅将生成目录传给 SourceSet 的配置**：本工程曾因此 clean 后漏打包、运行时出现 `HOST_RESOURCE_INVALID`。生成任务声明输入输出，资源留 build，不手动复制到 src/main/assets。
- APK 必须包含根 `contracts/` 与 `mock/` 的运行资源，离线从 assets 读取；文件变化应触发重打包。debug/release 都是当前演示包，不因为名称 release 就移除 Mock；未来 real 变体才隔离模拟引擎。
- 保留 RN settings/rootproject 插件、autolinking、`react.entryFile` 及 Codegen。现有变体为 debug/release，未引入 flavor 前不要使用 `demoDebug` / `demoRelease` 命令。

## 日志与故障诊断

- Native 使用 MLog；Application 初始化早期配置日志。Host 初始化直接记录阶段、错误码、flowId，不能只返回泛化错误并吞异常；Debug 可输出安全的异常堆栈。
- `EntryTask.Bridge` 标记初始化/桥接诊断，`EntryTask.RN` 承接 RN 批量日志，`ReactNativeJS` 是独立 JS Console。保持等级和关联编号，不因 telemetry 自身失败递归投递。
- 优先 `npm run logs:android`，Android Studio 查询 `package:com.entrytask`。遇到不可用页先找错误码和 flowId，再核对 APK 资源/模块注册，不先清缓存或反复重启。
- 网络日志不能包含认证 body、Token/Cookie、完整个人数据。发布型日志按等级、大小和脱敏控制，不以加全量日志换取排障便利。

## 构建与验证

根目录执行官方路径：`npx react-native run-android`。它必须继续构建、安装并启动同一 `com.entrytask`，登录后加载 RN。Android Studio 使用相同 app 配置。

以下命令在 `android/` 执行，按改动选择，不要求每个任务全部运行：

```sh
./gradlew :app:generateCodegenArtifactsFromSchema
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :biz_user:testDebugUnitTest :lib_net:testDebugUnitTest
```

- 测试模块按实际修改替换/补充，确认存在测试且执行了用例；`NO-SOURCE` 不算行为测试通过。设备测试仅在对应场景和设备可用时执行。
- 入口、Bridge、依赖或宿主变化：原生构建 + CLI 启动 + Native 登录到 RN 首页；按风险验证重建、返回、Tab 切换、取消及资源释放。普通 JS Reload 不更新 Native 代码/assets。
- 资源打包变化必须验证 `./gradlew :app:clean :app:assembleDebug` 和最终 APK `assets/contracts/`、`assets/mock/` 的清单/内容，并检查 Release：`./gradlew :app:assembleRelease`。Release Bundle 内置、不依赖 Metro，必要时离线验证。
- 可以按设备使用 `-PreactNativeArchitectures=arm64-v8a --max-workers=2` 做定向检查，但注明只覆盖该 ABI，不删默认 ABI 来掩盖链接错误，也不代替交付要求的完整构建。
- 使用仓库 wrapper；JDK、SDK、NDK 配置从实际工程核对，不提交 local.properties。缺依赖/权限时报告具体阻塞，不删除插件、跳过生成任务或随意升级工具链规避。
- 当前 Release 沿用演示签名且 R8 未启用，不能声称已满足正式发布。签名、混淆和发布基线变更须单独验证，不在普通修复中顺带调整。

## Code Review Rules

- 重点检查资源任务依赖是否在 clean 后有效、Host/Surface 是否重复创建、Fragment 恢复是否重复挂载，以及 Native-only 分支是否被意外删除。
- 检查主线程阻塞、取消被吞、Promise 重复完成/悬挂、旧会话结果回写、凭证流入 JS 和认证状态不完整；只报告有具体场景依据的问题。
