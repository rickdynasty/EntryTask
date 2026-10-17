# EntryTask Android 工程开发与架构规范

> 需求基线更新（2026-10-17）：本文件为旧技术方案初稿。后续方案请先阅读[原型需求规格说明书](../../docs/requirements/01-prototype-requirements.md)和[接口与 Mock 数据需求](../../docs/requirements/02-api-mock-requirements.md)。本期明确由 RN 实现四 Tab 及全部业务子页，Native 提供认证、宿主、网络/存储/日志能力；本地 Mock 经 Native 网络拦截并按 API 使用 JSON，普通请求随机延迟 1～3 秒；iOS 本期延期。本文中原生业务 UI 优先、直接 Fake 数据、注册返回登录及强制 Room 样板等冲突项不再作为本期实施依据，待技术方案统一修订。

版本：1.0 · 日期：2026-10-17 · 状态：配套规范，随架构基线采纳后执行

适用：app、全部 biz_*、lib_*，以及未来为本 Android 应用新增的模块。本文描述目标规则，当前不符合处按[迁移路线](03-implementation-roadmap.md)整改，不要求在未改造前声称已满足。

## 1. 规则等级与适用方式

- **必须/禁止**：代码评审与 CI 阻断项；无法遵守时需在 ADR 说明具体原因、风险、替代措施和清理时点。
- **应/建议**：默认遵循；偏离时在 PR 中说明理由。
- **可**：按实际复杂度选择，不作为机械检查项。

增量实施：新代码立即按目标规范编写；修改已有功能时修复本次范围内的边界问题，避免无关大规模重构。规则例外必须具体，不能用“历史代码”永久豁免。产品确认可改变方案中的默认产品假设，但应同步文档与验收。

## 2. 模块、包与依赖规范

| 编号 | 强制规则 | 检查方式 |
| --- | --- | --- |
| ARC-001 | app 只承载组装、平台入口、根导航；业务页面与规则归 biz | 目录和 import 审查 |
| ARC-002 | lib 不依赖 app、biz 或 RN；lib_net 与 lib_storage 不互相依赖 | Gradle 依赖白名单 |
| ARC-003 | biz 实现模块之间禁止直接依赖；通过对应 biz_*_api 和 app 编排 | 项目依赖与 import 检查 |
| ARC-004 | lib_base 不依赖其他工程模块；业务 API 不依赖业务实现/技术实现 | 依赖图无环检查 |
| ARC-005 | 仅公开必要契约和入口；实现默认 internal，局部细节 private | 公开 API 审查 |
| ARC-006 | 默认 implementation；api 必须能说明公开签名对该依赖的需要 | Gradle 评审 |
| ARC-007 | 禁止通过反射类名、万能 EventBus、ServiceLocator 访问其他域实现 | 源码审查 |
| ARC-008 | 直接使用的工程模块/库必须显式声明，不依赖偶然传递 | 编译与依赖分析 |

命名约定：

| 对象 | 规则 | 示例 |
| --- | --- | --- |
| 业务 Gradle 模块 | biz_<domain> | biz_user、biz_wealth |
| 业务 API 模块 | biz_<domain>_api | biz_user_api |
| 技术模块 | lib_<capability> | lib_base、lib_net、lib_storage |
| 业务包 | com.entry.biz.<domain> | com.entry.biz.member |
| 技术包 | com.entry.lib.<capability> | com.entry.lib.storage |
| 类/接口 | 职责名，不使用无意义数字后缀 | LoginViewModel、AuthRepository |
| 领域/传输/存储/展示类型 | 依角色区分 | Product、ProductDto、ProductEntity、ProductUiModel |
| 资源 | <域>_<用途>_<名称> | user_login_title、member_sign_in_icon |
| 常量/属性/方法 | UPPER_SNAKE_CASE / lowerCamelCase | MAX_RETRY_COUNT、sessionState、submitLogin |

现有 stroage 拼写必须迁正；迁包同时核对 Manifest、namespace、import、测试和反射配置，但不得擅自变更 applicationId 导致安装和数据身份变化。不能因包名改了就假设旧 SP 已迁移。

业务模块内部按 `entry/presentation/domain/data/di` 分包；领域层不依赖 Android UI、Retrofit、Room。无独立复用或复杂规则时，不强制 UseCase，不为每个包创建 Gradle 模块。禁止建立 `CommonManager`、`Utils` 等没有明确边界的大型容器。

## 3. MVVM 与 UI 规范

| 编号 | 强制规则 |
| --- | --- |
| MVVM-001 | View 负责渲染与平台交互；VM 负责状态生产和用户动作；Repository 管数据；DataSource 管具体 IO |
| MVVM-002 | ViewModel 对外暴露只读 StateFlow；MutableStateFlow 保持 private，UI 通过方法提交操作 |
| MVVM-003 | 页面状态定义在本域；Repository 结果不携带 Loading/Idle、Toast 或导航 |
| MVVM-004 | ViewModel 不持有 Activity、Fragment、View、NavController，也不直接调用 KV/DAO/Retrofit |
| MVVM-005 | 不把 UI lambda 保存或传入长期运行的业务层作为成功事实通知；关键结果必须进入状态 |
| MVVM-006 | 页面操作错误、表单字段错误、初始加载失败和刷新失败必须区分；刷新失败保留已有内容 |
| MVVM-007 | 密码/Token/完整卡号等不得进入 SavedStateHandle、Bundle、导航参数和持久化 UI 状态 |
| MVVM-008 | 登录成功、签到成功、领取结果不能仅通过可丢失的瞬时事件传递 |
| MVVM-009 | 不要求继承 BaseViewModel 才算 MVVM；不得引入没有用途的多级泛型继承 |

建议页面组合：`XxxRoute` 获取 VM、收集状态与协调 UI 副作用；`XxxScreen(state, callbacks)` 负责渲染；可复用组件只接收必要值与操作。登录、注册分开 VM，避免共享敏感输入。

Compose 必须采用生命周期感知收集；Fragment 必须使用 viewLifecycleOwner 收集并清理 ViewBinding。隐藏 Tab 应限制 maxLifecycle；禁止在组合函数主体直接发请求或显示 Toast。需要副作用时使用适当 Effect 并以稳定 Key 控制执行次数。

状态字段使用不可变集合或只读数据；并发更新用原子 update 或串行状态处理，避免先读后写覆盖。VM 若同时加载多个独立区块，应让区块状态独立；单一 Boolean 不得代表多个未协调的并发任务。

## 4. 协程、线程与错误规范

| 编号 | 强制规则 | 验证重点 |
| --- | --- | --- |
| COR-001 | CancellationException 必须继续抛出，不转为业务失败 | 取消任务不弹错误、不写旧状态 |
| COR-002 | 禁止在通用请求包装中吞 Throwable/JVM Error | 捕获预期异常并分类 |
| COR-003 | 禁止 GlobalScope 和页面无主协程；任务作用域与责任一致 | 页面销毁/退出后任务终止 |
| COR-004 | suspend 数据接口主线程安全；阻塞 IO 自行切调度器 | 主线程无磁盘/阻塞网络 |
| COR-005 | Dispatcher、Clock、必要随机源通过构造参数注入 | 测试可确定执行与推进时间 |
| COR-006 | 写操作有明确防重入策略，禁止只靠按钮外观防双击 | 连续提交只产生一次业务操作 |
| COR-007 | 会话相关返回值写入前校验 userId/generation | 旧用户响应不影响新用户 |

错误责任：网络库提供传输错误；数据层验证协议并映射领域错误；VM 将错误映射成可展示 UiError；View 只显示。禁止直接展示服务端堆栈、完整 response 或任意 Throwable.message。提示必须可定位操作，必要时提供重试；重试不得导致重复写入。

通用安全包装的原则示意：

```kotlin
try {
    // 调用挂起数据操作并返回 AppResult.Success
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    // 返回已映射的网络或存储错误，按操作上下文区分
} catch (e: Exception) {
    // 映射可诊断的未知错误；避免直接把 message 给 UI
}
```

该片段不替代具体的 HTTP/序列化/业务码映射。不要用多层包装重复记录同一次异常或重复显示提示。领域边界内应只有一处定义错误转换责任。

## 5. 网络与协议规范

| 编号 | 强制规则 |
| --- | --- |
| NET-001 | 用户业务 Service/DTO 在业务域；技术库不得识别 login/register 等业务路径 |
| NET-002 | 网络配置创建后不可变；缺少真实环境 baseUrl 立即失败，无示例域名兜底 |
| NET-003 | HTTP 状态、业务 code、解析失败、断网与超时分开处理 |
| NET-004 | 必填 DTO 字段通过 Mapper 校验，不依赖 Kotlin 非空声明推定 JSON 完整 |
| NET-005 | HTTPS 保留信任链与主机名校验；禁止 trust-all；mTLS 配置失败不得降级 |
| NET-006 | 凭证通过注入钩子获取，限定 host；公开请求不能自动带旧凭证 |
| NET-007 | 不在拦截器或 Repository 打开登录 UI；401 通知会话协调层 |
| NET-008 | 非幂等操作无明确协议不得自动重试；核查应用重试与 OkHttp 自动恢复 |
| NET-009 | 日志禁止输出认证 body、Token、Cookie 或个人资产响应 |
| NET-010 | demo/fake 与真实环境按源集或模块隔离；真实 release 不包含模拟实现 |

每个接口契约必须说明：方法/路径、认证要求、字段与空值、成功条件、错误码、幂等要求、分页、时间单位/时区、金额/百分比精度、超时、重试与缓存策略。没有后端契约时，用明确标识的 demo fixture 与设计假设，不把模拟协议称为服务端事实。

DTO 不直接跨域传递，不直接用于 UI；业务数据经领域模型，再按需要转 UiModel。搜索/推荐不得直接执行服务器下发的任意 route、Intent 或外部 URL，必须白名单校验。

## 6. 存储、模型与数据规范

| 编号 | 强制规则 |
| --- | --- |
| DATA-001 | KV 使用类型化 Key 和命名空间；不支持的类型不得静默忽略 |
| DATA-002 | 不用 Float/Double 保存金额；Double 不得降精度写 Float |
| DATA-003 | Token、密码不进普通 SP/DataStore/Room；Base64 不作为加密 |
| DATA-004 | 用户数据以 userId 隔离，退出清用户数据，不能顺带清全局设置 |
| DATA-005 | Repository 明确每类数据的唯一事实来源、TTL、刷新与失败策略 |
| DATA-006 | Room Entity/DAO/数据库/Migration 归业务域，lib_storage 仅提供通用支持 |
| DATA-007 | Room 不允许主线程查询；事务写入有原子性；重要数据禁止破坏式升级 |
| DATA-008 | Schema 入库，每次版本变更带迁移与迁移测试 |
| DATA-009 | 旧 KV 迁移先写入验证再清源；中断可恢复；敏感遗留字段按策略删除 |
| DATA-010 | 金额、收益、时间格式只在展示边界生成，不用已格式化文本继续计算 |

金额模型必须包含币种与精度策略；百分比必须定义 `0.0435` 还是 `4.35`，不能靠 UI 猜测。时间存储用明确单位的时间戳/日期模型，业务日按约定时区处理；测试跨日边界。

公开产品列表可使用 Room 缓存；个人资产默认不落盘。离线内容带更新时间与陈旧标识，不能把缓存余额显示为实时。跨域共享只读领域快照或稳定 ID，不传 Entity、DAO 或可变对象。

## 7. 会话、导航与平台生命周期规范

| 编号 | 强制规则 |
| --- | --- |
| AUTH-001 | 登录状态只有一个 SessionStore，不同时维护独立布尔值、Token 和 UI 登录标记 |
| AUTH-002 | 登录必须有完整有效会话；注册行为遵循已确认规则，禁止仅 setLoginSuccess(true) |
| AUTH-003 | logout 清凭证、用户内存、旧任务与用户缓存，旧请求回写必须隔离 |
| AUTH-004 | 401 失效按 generation 校验并合并，多请求不得打开多个认证页面 |
| AUTH-005 | 没有 refresh 协议时不得自行虚构刷新机制 |
| NAV-001 | 跨域导航由 app 协调，目标类型和参数可验证，只传必要 ID |
| NAV-002 | 登录守卫按入口执行；取消登录返回公开页；认证子页不能直接退出全应用 |
| NAV-003 | Fragment 从管理器按稳定 tag 恢复，容器 ID 稳定，禁止恢复后重复创建 |
| NAV-004 | 普通导航禁止 commitAllowingStateLoss；state saved 时排队到安全时点 |
| NAV-005 | 敏感目的地与深链重新鉴权；exported 组件最小化 |
| RN-001 | RN 生命周期和桥接仅在 app/独立适配模块，不能进入 base |
| RN-002 | 同一业务不能在 JS 和原生维护两套凭证、缓存和不一致规则；桥接订阅必须注销 |

入口鉴权与数据接口鉴权都需要存在：隐藏入口不代表权限校验；恢复页面时也须检查会话。外部输入必须校验目的地和 ID，不能信任 Intent 中任意 Parcelable Tab 配置。

## 8. UI、资源与可访问性规范

- 共用颜色、字号、间距、圆角与按钮状态从主题 token 获取；业务素材留业务模块，资源名加域前缀。
- 页面具备首载、内容、空、错误、刷新、提交等适用状态；不把长期转圈作为错误处理。
- 表单在提交前展示明确校验错误；提交禁重入，异常结束后恢复可操作状态；取消不提示失败。
- 可滚动内容使用列表组件；图片限制尺寸并提供占位/失败状态；不以整张截图代替真实 UI。
- 图标提供有意义的无障碍描述；装饰性图片可无描述；交互区域和对比度按设计验收。
- 验证系统字体放大、窄屏、系统栏 inset、键盘遮挡、TalkBack；不写死截图像素尺寸。
- 资产默认掩码；进入后台后的敏感内容处理与截图限制按页面风险确定，并在任务中说明。

## 9. 构建与依赖治理规范

| 编号 | 强制规则 |
| --- | --- |
| BUILD-001 | 统一插件、Kotlin、SDK/JVM 和核心库版本，不在子模块随意独立声明版本 |
| BUILD-002 | 版本升级记录理由与兼容验证；禁止动态 + 版本进入正式分发依赖基线 |
| BUILD-003 | 保留并验证 RN 自动链接和构建链，Android 模块化不得随意破坏模板插件 |
| BUILD-004 | demo/real 与 debug/release 含义分离；真实 release 不含 Mock/Fake/私有调试 CA |
| BUILD-005 | 正式分发不使用 debug 签名，密钥与密码不提交仓库 |
| BUILD-006 | 正式 release 校验网络策略、日志、备份、导出组件及最终合并 Manifest |
| BUILD-007 | R8/资源压缩启用后验证反射序列化和导航，不能只测 debug |

Version Catalog 管版本；convention plugin 管共用配置，按规模引入。不要为统一 DSL 一次性重写全部构建文件。当前已有 wrapper/AGP/RN 组合必须先验证，再决定是否调整，不能在文档中当成兼容已通过。

## 10. 测试、CI 与质量门禁

测试围绕行为与风险，不为了覆盖率镜像 getter、常量和简单组件实现。

| 层级 | 必需覆盖 | 推荐方式 |
| --- | --- | --- |
| 纯逻辑 | 金额精度、业务日、Mapper 空值、错误分类 | JUnit |
| ViewModel | 字段错误、提交/失败/取消、防重入、状态只读 | coroutine-test + FakeRepository |
| 会话 | 登录/注册/退出/过期、旧响应、并发 401、账号隔离 | 可控调度器与 generation 场景 |
| HTTP | Header、公开端点、业务码、坏 JSON、超时、禁止重放 | MockWebServer |
| 存储 | Key 类型/隔离、迁移可恢复、加密失败降级为游客 | 单测 + 设备测试 |
| Room | schema 与支持版本迁移、事务、用户过滤 | Room migration 测试 |
| UI/宿主 | 游客守卫、返回目标、Tab 恢复、错误与空态 | Compose/Espresso/ActivityScenario |
| 混合容器（启用时） | Host 恢复、返回键、订阅注销 | 原生/RN 集成验证 |

所有 PR 执行相关单测、Lint 与目标变体编译；涉及认证、数据库、构建或导航的修改必须运行对应集成验证。CI 目标：结构白名单 → 静态检查 → JVM 单测 → demoDebug 组装 → 设备集成 → 有配置时真实 release 验证。检查项尚未接入 CI 时，PR 中提供人工证据，不得标记为自动门禁已生效。

性能无未经测量的固定承诺；记录相同设备、构建变体和数据规模的基线。新增全局初始化、缓存、列表或 RN 页面时，检查启动阻塞、请求次数、滚动和资源释放。

## 11. 开发、评审与文档流程

1. 需求写清原型 ID、游客/登录行为、接口/fixture、异常态与范围外内容。
2. 确认归属业务域和依赖，必要时先增加最小契约。
3. 定义领域模型、数据事实来源、UiState 和操作方法，再实现数据与 UI。
4. 补充关键行为测试，执行目标变体构建、Lint 和必要设备验证。
5. PR 说明问题、结果、迁移/回滚、验证与未决项；修改契约/数据库/会话/架构同时更新文档。
6. 评审通过并完成目标环境验证后合入；后续清理任务有负责人和时点。

PR 评审必须回答：模块归属是否正确；是否违反依赖白名单；状态是否单一来源；取消/防重/生命周期是否可靠；个人数据是否隔离；网络/数据库错误是否可见；是否引入不必要抽象；是否有足以验证本次风险的证据。

建议提交粒度：基础库修复、会话迁移、样板页、其他域迁移分别提交。一个 PR 不混合无关 UI 改色、依赖大升级和会话重构。架构影响通过 ADR 记录，不只存在聊天记录或提交标题里。

## 12. 参考与解释边界

本规范的具体包名、默认会话策略和门禁是项目决策。以下官方资料作为设计依据，不代表官方要求逐字采用本工程模块划分：

- [模块化模式](https://developer.android.com/topic/modularization/patterns)：模块边界、API/实现隔离、最小公开面。
- [UI 事件](https://developer.android.com/topic/architecture/ui-layer/events)：关键业务结果通过状态表达。
- [协程最佳实践](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)：主线程安全、调度器注入与取消传播。
- [Room 迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)：版本升级与迁移验证。
- [Keystore](https://developer.android.com/privacy-and-security/keystore)：平台密钥管理。

参考核查日期：2026-10-17；依赖升级和平台策略变化时重新核查。
