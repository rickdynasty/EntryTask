# 前端RN技术方案

版本：1.1 · 日期：2026-10-18 · 状态：待评审设计，尚未实施  
适用：共享RN业务代码，Android本期运行，iOS后续复用

## 1. 目标与技术基线

输入为[需求规格](../requirements/01-prototype-requirements.md)、[API/Mock需求](../requirements/02-api-mock-requirements.md)与[总体设计](EtryTask%20项目设计方案.md)。本方案负责四Tab和全部RN子页、组件复用、MVVM、路由、能力Port、数据投影与性能测试；Native登录注册和根Tab不迁至JS。

| 项目 | 选型/约束 | 落地验证 |
| --- | --- | --- |
| React/RN | 保持19.2.3/0.86.0、Hermes、新架构 | G0锁工具链和release样板；不因设计擅自升级 |
| TypeScript | strict、noUncheckedIndexedAccess、exactOptionalPropertyTypes | 现有tsconfig增量启用，DTO未知输入用unknown |
| 页面导航 | @react-navigation/native + native-stack + react-native-screens | 精确版本G0验证锁定；不创建JS bottom-tabs |
| 状态与MVVM | Redux Toolkit封装客户端状态，RTK Query封装查询/变更；VM保持库无关 | 单runtime单Store/查询层；按feature/routeKey/epoch切片，禁止自研第二套资源仓 |
| Schema | JSON Schema为跨端定义，构建时生成TS类型/校验器 | 拟用Ajv构建期独立生成校验函数，避免设备端动态编译schema；Hermes release PoC |
| UI | RN基本组件、虚拟列表、安全区库；设计token与组件变体 | 不引入整套UI库替代设计系统；现有safe-area库兼容核查 |
| 测试 | Jest + React Native Testing Library；设备集成走原生UI测试 | 测试库版本须匹配React19；不把模板快照当覆盖 |
| 网络/持久化 | NetworkPort → TransportRouter → Axios RN HTTP / TurboModule Native网络；持久化仍Native | 禁止页面/VM直接用HTTP库/NativeModule；Axios只在RnHttpTransport；不直读fixture |

V1.1采纳成熟库封装：客户端状态采用Redux Toolkit，网络资源采用RTK Query；Route经项目hook/selector取状态，VM经StorePort/Repository操作，无React/Redux实现依赖。Redux建议按feature组织逻辑，本项目在feature内部进一步按MVVM职责分层，不把整套业务堆进全局store。[Redux官方结构建议](https://redux.js.org/style-guide/)

## 2. 目录、模块与依赖

```text
src/
  app/
    composition/                 AppContainer、FeatureFactory、作用域
    bootstrap/                   握手、能力就绪、运行时错误边界
    navigation/                  RootStack、RootHub、RouteRegistry、协调器
    session/                     只读SessionProjection及失效编排
  infrastructure/
    network/                     TransportRouter / NativeTransport / RnHttpTransport / Axios
    state/                       configureStore / 统一ApiService / VM状态适配 / 失效协调
    logging/                     RN Logger / console与内存sink / Native关联sink
  features/
    home/                        public / presentation / domain / data / di
    wealth/                      产品、资产、账户/流水、资讯
    member/                      会员、活动、签到、权益、能量
    profile/                     我的、资料、设置、个人事项
    services/                    服务目录、搜索、演示说明
    messaging/                   消息/角标（跨Tab通用业务域）
  platform/
    ports/                       Network/Storage/Session/Host/TelemetryPort
    native/                      五个Codegen模块adapter，仅此处导入specs
    contracts/                   schema生成物、错误与版本适配
  shared/
    ui/                          token、无业务规则的基础控件
    presentation/                ResourceState、分页/Disposable辅助
    domain/                      Money、AppError、Result等最小通用类型
    assets/                      本地图标/Banner及resourceKey映射
    formatting/                  金额/日期/百分比纯函数
specs/Native*.ts                  Codegen规范，位于工程根specs
```

每域的`public/index.ts`仅导出route descriptor、factory、只读Repository契约或安全领域模型。`presentation`依赖本域domain及shared；data实现domain Repository并依赖platform Ports与infrastructure的稳定facade；domain不导入React/RN。app composition组装实现，不允许业务代码import AppContainer单例反查。所有跨域依赖通过public：home→wealth产品读取/推荐，profile→wealth资产、member摘要、messaging未读；反向依赖仅由app导航路由注册解决，不形成环。

财富域首期含账户/流水因需求复用资产模型；中大型扩展到独立资金业务时拆account域，保留原路由兼容映射。服务/消息是有业务语义的通用域，不放入无边界shared目录。工程先单package，ESLint restricted-imports/依赖图做静态门禁；域独立发布才升级workspace，不制造本期多包发布负担。

## 3. MVVM与状态生命周期

### 3.1 职责及作用域

| 对象 | 作用域 | 责任与释放 |
| --- | --- | --- |
| Platform adapters/容器 | runtime | 注册bridge事件、协调bootstrap/HTTP实例/Store/Logger；destroy时取消订阅/请求并清Promise表 |
| SessionProjection/域Repository | runtime内按epoch | 脱敏快照、Repository暴露RTK Query资源、失效/合并；epoch变化清个人查询/客户端slice，不另存资源副本 |
| 根页VM | tabId+epoch或游客公开scope | 保留滚动/筛选/区块状态；inactive暂停请求；低内存可释放View、保留非敏感状态 |
| 子页VM | routeKey | 详情/分页/提交状态；路由最终移除时dispose，不能在每次render重新创建 |
| 基础组件状态 | React组件 | 焦点、展开、动画等展示状态；不持有业务事实 |

Route负责从FeatureFactory获取VM、绑定活跃/可见性、订阅快照、注入导航；Screen只接state及actions。VM不接React Navigation实例、NativeModule或UI引用；动作结果以`NavigationIntent`状态交由Route消费，包含intentId和ack，避免丢失或重复跳转。Toast属于可丢短提示，不能用Toast作为签到成功的唯一记录。

```ts
// 设计接口示例，省略类型导入和实现。
interface ViewModel<S> {
  getSnapshot(): Readonly<S>;
  subscribe(listener: () => void): () => void;
  activate(): void;
  deactivate(): void;
  dispose(): void;
}
type ResourceState<T> = {
  data: T | null;
  phase: 'idle' | 'loading' | 'ready' | 'empty' | 'error';
  refreshing: boolean; error: UiError | null;
  fetchedAt: string | null; stale: boolean;
};
type SubmitState<T> =
  | { status: 'idle' }
  | { status: 'submitting'; operationId: string; idempotencyKey: string }
  | { status: 'succeeded'; result: T }
  | { status: 'failed'; error: UiError; idempotencyKey: string };
```

上述ViewModel接口是库无关的消费面，底层以RTK slice/RTK Query selector实现getSnapshot/subscribe，不再手写独立响应式store。客户端slice只存筛选/弹层/操作ID/导航intent等，服务端data留在RTK Query；VM通过createSelector生成稳定投影，Route用封装selector hook，禁止每次getSnapshot创建新对象。Promise/AbortController/Native实例/密码不进Redux；route移除释放该routeKey的客户端slice，避免长期累积。

### 3.2 异步控制

每次查询捕获`{runtimeId,sessionEpoch,queryVersion,routeKey}`；同资源刷新采取latest-wins，分页在同queryVersion内串行追加。切筛选递增queryVersion、取消旧分页，结果写入前再次比较上下文。提交采用single-flight，不以防抖当幂等；operationId对应本逻辑操作，重试保留idempotencyKey。

VM使用AbortController表达取消，经NetworkPort根据transport映射Native cancel或Axios AbortSignal；取消为Result.Cancelled，不生成错误Toast。VM dispose会取消未完成查询并清监听；在详情提交后返回，事务可能已完成，回到相关列表必须失效重取。catch预期错误，finally仅在当前操作版本匹配时复原loading，避免旧任务finally清掉新任务状态。

刷新保留旧data；首次空数组进入empty；0金额为ready；必填data缺失进入ProtocolError而非empty。多区块使用独立ResourceState和Promise.allSettled隔离故障，不能一个全页loading布尔值串行控制全部请求。

## 4. 页面与数据编排

| 根/页面组 | VM/Repository | 首次有效请求与重用 | 失效/交互 |
| --- | --- | --- | --- |
| H-01 | HomeVM + HomeRepository；Wealth公开产品契约 | A04/A05/A06当前推荐并发；目录资源单飞复用 | 分类换A06，营销失败仅区块降级 |
| W-01 | WealthVM、AssetsResource、ArticlesRepository | A08/A06；登录再A09；A14仅资讯分段/市场摘要需要时 | 眼睛走Preference；资讯切换不改Tab |
| M-01 | MemberVM、MemberRepository | A16/A20；登录再A17；活动A26/已领A23按分段加载 | 签到/领取成功失效摘要和已领 |
| P-01 | ProfileVM +跨域只读资源 | 游客A30；登录A28/A29，与A09/A17已有资源共享 | 退出清所有个人slice，保留允许公开资源 |
| W-02/03 | ProductList/DetailVM | A06/A07 | 类型参数+分页查询键，返回滚动锚点 |
| W-04/05/06 | Assets/Account/TransactionVM | A09～A13 | 全保护；方向筛选重置分页，详情权限再次校验 |
| W-07 | ArticleList/DetailVM | A14/A15 | 纯RN段落渲染 |
| M-02 | ActivityList/DetailVM | A26/A27 | 只允许注册CTA目标，活动状态来自数据 |
| M-03 | CheckinVM | A18/A19 | UI不计算奖励、不假增余额 |
| M-04/05/06 | Benefit/Claim/EnergyVM | A20～A25 | 游客只看公开，领用后以权威结果更新 |
| P-02/03/04 | Profile/Settings/PersonalItemsVM | A29、StoragePort、SessionPort、A31/A32 | 设置无任意KV，退出走Native；事项只读 |
| C-01/02 | Services/SearchVM | A05/A37 | 搜索显式提交，trim校验1～30字、A37禁磁盘缓存/离页清词、不搜个人 |
| C-03 | MessageList/DetailVM、UnreadResource | A33～A36 | 进入详情提交已读，返回权威count |
| C-04/05 | 说明模板 | 本地静态说明+受控服务目录名称 | 不虚构真实服务完成 |

同一活跃用户多个页面经Repository订阅同一RTK Query资源；AssetsResource等名称指只读facade，不另建data拷贝。跨API摘要采用`minResourceRevision`失效标记：写A22得到r=12后，A17/A28迟到r=11结果丢弃并重取；本期全Demo支持revision。未来真实后端无revision时需重新定义一致性策略，不能擅自依赖不存在字段。

## 5. 路由与宿主协调

RootStack仅一个NavigationContainer，初始为RootHub；RootHub惰性挂载home/wealth/member/profile Panel，以active参数控制可见和副作用，Native选中态为唯一根来源。native-stack通过原生导航组件实现转场与手势，但页面视图和自定义标题仍由RN组件渲染。[React Navigation native-stack](https://reactnavigation.org/docs/native-stack-navigator/)

| 路由族 | 页面ID | 允许参数 | 保护 |
| --- | --- | --- | --- |
| RootHub | H/W/M/P-01 | selectedTab只读投影、当前分段 | 混合权限 |
| ProductList/Detail | W-02/03 | category/scene、productId | 公开 |
| AssetOverview/AccountList/AccountDetail/TransactionList/Detail | W-04～06 | view、accountId、transactionId、direction | 登录 |
| ArticleList/Detail | W-07 | category、articleId | 公开 |
| ActivityList/Detail | M-02 | category、activityId | 公开 |
| Checkin | M-03 | 无用户ID/业务日期路由参数 | 登录 |
| BenefitList/Detail | M-04 | category、benefitId | 浏览公开、领取登录 |
| ClaimList/Detail、Energy | M-05/06 | claimId、可选分页筛选 | 登录 |
| ProfileDetail/Settings/PersonalItems/ItemDetail | P-02～04 | type、itemId | Settings公开，其余登录 |
| ServiceDirectory/Search/MessageList/Detail/Explanation | C-01～05 | group、scope、messageId、serviceId | 消息及受保护服务需登录 |

RouteRegistry按白名单解析，不接受任意组件名/函数/URL；routeKey由导航层生成，重复同目的地+参数的快速点击合并。金额、手机号、完整产品对象不放导航参数。目标验证失败进入RouteError可返回页。

push前发布pendingTransition隐藏Native底栏；onStateChange/transitionEnd发布提交快照，包含depth/rootTab/canGoBack/modalVisible/keyboardVisible但不含业务数据。Native命令和RN子栈revision分开；在认证覆盖和系统中断时，重连snapshot完成后再执行积压且仍有效的命令。

Android返回由Native协调为单个backRequested(requestId)；RN先关闭自管理modal/键盘、再goBack并返回consumed；根页返回notConsumed，Native按非首页切首页/首页双击退出处理。native-stack默认BackHandler与自定义处理不可同时各消费一次，G0必须验证并明确唯一入口；iOS只保留RN native-stack子页交互手势，NativeShell不再外包第二层业务导航栈。手势取消只回滚pending状态，转场完成才提交depth。

会话事件先清个人快照、取消旧epoch任务，再去掉受保护route并切需要的根页；注册/登录成功只消费一次导航intent。RootHub当前分段因认证取消保持原值，不能认证取消后停在半个已领权益空页。

## 6. 能力适配与接口模型

### 6.1 Adapter边界

`platform/native`是唯一导入`specs/Native*.ts`的目录；业务只注入NetworkPort等接口。统一TransportRouter按descriptor选择RnHttpTransport（Axios实例）或NativeTransport（自定义NativeModule）；Native指定API能力缺失时明确失败，不在catch里改用另一通路。主要语义接口：

```ts
interface RequestHandle<T> {
  readonly requestId: string;
  readonly result: Promise<AppResult<T>>;
  cancel(): void;
}
interface NetworkPort {
  request<T>(input: ApiRequest, decoder: Decoder<T>): RequestHandle<NetworkResponse<T>>;
}
interface StoragePort {
  read<T>(key: RegisteredKey<T>): Promise<AppResult<T | null>>;
  write<T>(key: RegisteredKey<T>, value: T): Promise<AppResult<void>>;
  remove(key: RegisteredKey<unknown>): Promise<AppResult<void>>;
}
interface SessionPort {
  snapshot(): Promise<AppResult<SessionSnapshot>>;
  authenticate(target: SafeRouteTarget, flowId: string): Promise<AuthOutcome>;
  logout(flowId: string): Promise<AppResult<void>>;
}
```

ApiRequest仅apiId、受控pathParams/query/body、cacheMode、flowId、timeoutMs白名单值，不由页面传transport。Router按descriptor解析Host/通路/认证/缓存策略；Native通路校验bridge envelope，RN通路使用Axios响应适配，再统一校验业务schema及DTO→domain；调用前生成requestId与当前context，禁止`as Product[]`绕过运行时校验。非2xx先按HTTP分类；200+业务非200为BusinessError；业务成功却data缺失为ProtocolError。cancel幂等，Native终态与JS Abort并发只完成一次Promise。

存储注册Key本期只有`assetVisibility`（user scope、boolean、schemaVersion1）及非敏感应用选项；Key字面值由生成注册表约束。scope里的真实userId由Native从当前会话决定，不允许传某账户名读他人数据。未登录读取用户偏好返回AUTH_REQUIRED，Root页保持默认隐藏；存储读失败保持安全默认并记录诊断。

### 6.2 模型与必填规则

JSON对象不带问号的字段均必填；下表`?`表示可选/可空，schema实现时显式声明null与缺省差异。HTTP业务包`code:number,message:string,data:T|null,traceId:string`；分页`items:T[],page:int,pageSize:int,total:int,hasMore:boolean`。所有ID为非空string；计数为非负int；opaqueUserId不能用作请求授权参数。

| 模型 | 核心字段/规则 |
| --- | --- |
| Money | currency:'CNY', minorUnit:2, amountMinor:整数字符串，可负仅收益/允许负资产的明确字段 |
| SessionSnapshot | status:guest/authenticated, epoch:int, opaqueUserId?:string, displayName?:string, maskedPhone?:string；无凭证 |
| Service | id,title,iconKey,group,requiresAuth:boolean,target:{kind,id?,params?}；target由路由schema约束 |
| Content | version,searchHint?,sections:[{id,type,items}]；type受控且未知可选区块可跳过，必需区块未知报协议错误 |
| ProductSummary | id,name,category,tags:string[],indicator?:{ratio,label,period},minimumAmount:Money,termText,riskLevel,riskText,isDemo:true |
| ProductDetail | ProductSummary+description:Paragraph[],distributionLabel?,status:available/unavailable |
| AssetSummary | totalAssets,totalLiabilities,netAssets,latestIncome:Money,incomeDate,asOf,categories:[{id,title,amount:Money}],resourceRevision |
| Account | id,maskedNumber,type,title,balance:Money,status；详情加description及currency，禁止原卡号 |
| Transaction | id,accountId,direction:in/out,amount:Money,balance:Money,occurredAt,type,summary；详情加maskedCounterparty |
| Article | id,title,summary,publishedAt,coverKey?,category；详情加source,paragraphs:Paragraph[] |
| MemberSummary | level,energyBalance:int,checkedToday:boolean,consecutiveDays:int,claimCount:int,businessDate,resourceRevision |
| CheckinStatus | businessDate,cycleId,consecutiveDays,checkedDates:string[],rewardedMilestones:(3/7)[],energyBalance,resourceRevision |
| CheckinResult | CheckinStatus+alreadyChecked:boolean,awardedEnergy:int,operationId |
| Benefit | id,title,type,requiredLevel,startsAt,endsAt,remainingStock:int,rules:Paragraph[]；详情+eligibility:loginRequired/eligible/ineligible,claimStatus,claimId? |
| Claim | id,benefitId,title,claimedAt,validUntil,status:valid/expired,usageText；无真实兑换码 |
| ClaimResult | claim:Claim,alreadyClaimed:boolean,claimCount:int,resourceRevision |
| EnergyRecord | id,delta:int,balanceAfter:int,businessType,occurredAt,relatedCheckinId?；列表外层附当前balance |
| Activity | id,title,startsAt,endsAt,status:notStarted/active/ended,coverKey,summary；详情+rules/paragraphs/ctaTarget? |
| Profile | nickname,maskedPhone,avatarKey,level,lowCarbonLabel?；统计独立summary |
| ProfileSummary | bankCardCount,todoCount,couponCount,points,energyBalance:int,resourceRevision |
| Marketing | creditCard:{title,summary,assetKey,target},loan:{maximumExampleAmount:Money,annualRatio,rateLabel,isPersonalOffer:false,target},assetCampaigns |
| PersonalItem | id,type:todo/points/orders,title,status,occurredAt,summary；详情+paragraphs,delta?，积分列表外层balance |
| Message | id,title,summary,createdAt,isRead；详情+paragraphs；read结果isRead/unreadCount/resourceRevision |
| SearchItem | type,id,title,summary,target；目标类型必须匹配scope |
| Paragraph | type:text/image/heading/linkToInternal, text?/assetKey?/safeTarget?；不接受HTML/script/远程链接 |

金额格式用字符串算法完成千分位和小数位，计算使用精确整数/十进制工具，禁止把任意大amountMinor转Number。比率十进制字符串按统一规则显示百分比，精度默认2位，产品如需3位由displayPrecision显式指定（0～4）；不从截图猜口径。日期由明确时区格式化，签到结算不在JS进行。

### 6.3 37API的技术绑定

请求为最终拟定基线，所有枚举/类型映射schema，未知值拒绝或走兼容分支；共同分页参数page/pageSize。A01～A03是Native专用，RN不能调用NetworkPort绕认证；下表其余接口均经同一NetworkPort及策略Router；当前37API默认Native Mock，rnHttp可用于后续明确的独立公开服务。

| API | 请求约束（共同分页省略） | 成功data模型/额外要求 |
| --- | --- | --- |
| A01/A02 | username trim1～32、password原样1～64；现有Form表单编码由Native适配 | NativeAuthSession，不生成JS DTO |
| A03 | 无业务body，Native捕获退出上下文 | `{loggedOut:true}`，失败不阻止本地清理 |
| A04 | 无 | Home Content，service/product/activity引用合法 |
| A05 | group?=wealth/account/member/other | Service目录数组，非分页 |
| A06 | category合法产品分类、scene=home/wealth/list、tag?用于优选/热门等筛选 | Page<ProductSummary> |
| A07 | productId非空 | ProductDetail |
| A08 | 无 | Wealth Content |
| A09 | 无 | AssetSummary |
| A10/A11 | 列表分页；详情accountId | Page<Account> / Account详情 |
| A12 | accountId?、direction=all/in/out | Page<Transaction> |
| A13 | transactionId | Transaction详情 |
| A14/A15 | category?；详情articleId | Page<Article> / Article详情 |
| A16 | 无 | Member Content |
| A17/A18 | 无 | MemberSummary / CheckinStatus |
| A19 | expectedBusinessDate有效YYYY-MM-DD、idempotencyKey非空 | CheckinResult |
| A20/A21 | category?；详情benefitId | Page<Benefit> / Benefit详情；A21禁止公共缓存 |
| A22 | benefitId、idempotencyKey | ClaimResult |
| A23/A24 | 列表分页；详情claimId | Page<Claim> / Claim详情 |
| A25 | 分页 | Page<EnergyRecord>+balance |
| A26/A27 | category?；详情activityId | Page<Activity> / Activity详情 |
| A28/A29/A30 | 无 | ProfileSummary / Profile / Marketing |
| A31/A32 | type=todo/points/orders；详情itemId | Page<PersonalItem>+balance? / PersonalItem详情 |
| A33/A34 | 列表分页；详情messageId | Page<Message> / Message详情 |
| A35 | messageId、idempotencyKey | `{isRead:true,unreadCount,resourceRevision}` |
| A36 | 无 | `{unreadCount:int,resourceRevision}` |
| A37 | keyword trim1～30、scope=services/products/articles/activities | Page<SearchItem> |

schema源在contracts/api/v1，TS类型和运行时decoder由同源构建生成，不能人工修改生成物。A06的tag为本方案对推荐场景的显式细化，需要T01同步需求API附录，保持名称一致；category覆盖finance/fund/deposit/privateFund/insurance/gold/cashPlus/xinxinFamily，债券基金用category=fund、tag=bond，钻石理财用category=finance、tag=diamond。Mock manifest须覆盖所有这些变体。

## 7. 写操作与共享资源一致性

签到：CheckinVM读取A18 → 用户点击 → 若游客先认证并停留原详情 → 用户再次点击A19 → 返回状态/奖励/版本更新CheckinResource → 失效MemberSummary/ProfileSummary/EnergyRecords。reward为0也算签到成功，不擅自增加基础奖励。

领取：A21判断资格用于展示，不能以客户端显示代替Native权威校验 → 点击A22 → ClaimResult更新权益详情/已领资源，失效A17/A23/A28；请求超时显示“结果待确认，可查询/重试”，保留幂等键，重读A21/A23或同key重试，不能直接宣称失败未领取。

消息：详情A34成功渲染后提交A35，失败保留未读并给重试入口；服务端权威count更新UnreadResource。重新进入同详情不再次产生有效减计数；多个页的count按resourceRevision取较新。

退出：SessionProjection变guest即同步清个人资源，NavigationCoordinator移除保护路由，相关VM提交态失效；公开内容可继续展示。个人数据不得在UI过渡期间通过旧props闪现，RootHub以sessionEpoch为个人子树key重新创建。

## 8. 组件与视觉工程

设计token置于shared/ui/tokens，包括语义色、文字等级、间距、圆角、阴影、触控最小区域；四域不能各写相同金色/红色常量。资产与收益使用不同语义，负值不只靠颜色。基础组件包含Page/TitleBar/SegmentedControl/SearchBar/UnreadBadge/ServiceGrid/Banner/Card/Button/StatItem/MaskedMoney/AsyncSection/PagedFooter/ConfirmDialog；ProductCard/BenefitCard等业务组件放对应域public UI，不使shared反向依赖产品模型。

控件props使用可序列化展示模型与onPress，不接Repository或VM；有基础尺寸/强调/禁用/加载变体。卡片图片是装饰还是操作入口需提供accessibilityLabel/role；TalkBack/未来VoiceOver可读。字体1.3倍和360/412dp检查，文本优先换行/省略并提供详情；不将所有字号按屏宽比例缩放。

SafeAreaProvider仅根部一个；Native提供内容实际安全区上下文，RN使用实测insets：根页底部已被Native底栏占据，不再重复加底栏高度；子页底栏隐藏后再应用系统bottom inset。状态栏样式由Host协调，RN业务区不随意全局修改；Android edge-to-edge和iOS手势区分别验收。native-stack headerShown=false，自定义TitleBar，转场与返回手势仍由库管理。

图片resourceKey静态映射到require资源，不做动态字符串require；尺寸分级并限制解码像素。文章段落使用受控RN renderer，链接只允许内部路由；不能以WebView加载HTML获得未审查权限。模拟数据提示为共享Notice组件，在资产/收益/营销/权益上下文可见。

## 9. 性能与稳定性实施

| 项目 | 具体措施 | 观测与退出条件 |
| --- | --- | --- |
| 启动 | bootstrap并行准备非敏感静态资源，握手后再查个人；首页优先，其他根页lazy挂载 | 达总体首帧/骨架预算；Trace不得有同步磁盘 |
| 列表 | FlatList/SectionList、稳定key、按需渲染；分页10、最多50；windowSize等以画像调优 | 固定12/100/1000条性能fixture，只测试fixture不扩大正常业务数据 |
| 渲染 | snapshot稳定，资源slice独立；昂贵派生memo；React.memo只用于测量确有收益的卡片 | Profiler不因角标刷新全RootHub |
| 计算 | JSON≤256KiB，schema生成校验、金额纯函数；大段内容分块 | JS长任务定位与拆分，不把requestIdleCallback当线程池 |
| 网络 | RTK Query查询共享，Native或RN按descriptor执行，VM latest-wins，进入页面只查有效资源 | 隐藏Tab无轮询，重复挂载无请求倍增 |
| 内存 | VM有dispose，查询/订阅明确归属，RTK Query未订阅条目60秒清理/100键预算，个人条目无订阅立即清理，详情数据离开按策略释放 | 20轮回收后增量≤20MiB，泄漏有堆证据 |
| 故障 | 根/区块ErrorBoundary；异步错误归一；超时/取消/BUSY反馈 | 无无限retry/渲染错误循环/能力缺失JS降级 |

Native发memoryPressure时清非活跃Panel View、图片/公开内存资源，保留白名单筛选/滚动锚点；恢复时重建VM依赖并重查，不保留已销毁VM的subscription。低内存事件不是退出，不删Native会话。

## 10. 测试、CI与可跟进任务

| 层 | 必测案例 | 方式 |
| --- | --- | --- |
| VM | 首载/空/失败、刷新保留内容、分页取消、旧finally/旧响应、重入与dispose | Jest Fake Ports，注入Clock/ID，不使用真实1～3秒等待 |
| 数据映射 | 37API decoder、必填/可空、Money/ratio、未知枚举/坏JSON、协议主版本不匹配 | 同contracts/test-vectors |
| 导航 | 根Tab revision、pending/rollback、认证恢复一次、跨域回来源、手势取消 | coordinator单测+设备集成 |
| 写操作 | 签到/领取超时重试复用key、版本防回退、已读count | VM测试+Native真实Mock集成 |
| 组件 | 游客不泄露身份、加载禁用、负收益/0、错误重试、可访问性标签 | RN Testing Library行为测试，少量视觉基准 |
| 设备 | Native认证↔RN、飞行模式、Activity/runtime/进程恢复、异常与性能 | Android设备测试/手工脚本；iOS延期 |

RN CI预期命令：`npm ci`、`npx tsc --noEmit`、`npm run lint`、`npm test -- --runInBand`；新增`contracts:check`、`architecture:check`、`bundle:check`脚本由T01/T04实施后才可执行。契约JSON不代表测试可绕过已指定通路；单测Fake Port仅隔离测试，本期37API演示默认走Native Mock；rnHttp用临时HTTP测试夹具验证实际Axios通路，无产品后端时不伪造直连成功。

| RN任务 | 依赖 | 交付及验收 |
| --- | --- | --- |
| RN-00 工具链与Codegen/native-stack PoC | G0 | 双向事件/取消/返回/优化Bundle构建证据 |
| RN-01 契约生成与Ports | T01 | 37API类型/decoder、五能力adapter，坏包/版本用例 |
| RN-P0 开发前结构/库封装 | RN00及契约初稿 | src分层、别名/边界、Axios/RTK/Logger样板先完成，再开发正式业务页 |
| RN-02 VM与RootHub/导航 | RN01+RN-P0 | snapshot/生命周期、四根页、revision/ACK、认证返回 |
| RN-03 token与组件样板 | RN02 | ProductCard/AsyncSection/TitleBar等，窄屏字体与安全区样板 |
| RN-04 纵向权益闭环 | Native基础/Mock+RN03 | 首页→认证→权益领取→我的卡券，同flowId证据 |
| RN-05 H/W/M/P/C全页 | RN04 | 页面与API清单逐项打勾，AT-01～10 |
| RN-06 故障/恢复/性能 | RN05 | AT-11～17、基线对比、Bundle/资源检查 |

每任务登记负责人、PR、需求ID、测试证据、buildId、剩余风险；默认全部未开始。未来iOS接入主要改platform adapter/安全区和手势测试，共享domain/VM/Screen；禁止在feature内散落Platform.OS判断认证或收益规则。

## 11. 迁移与评审清单

保留AppRegistry的EntryTask组件名，注册入口移到src/index.js，src/app/App.tsx作为组合根入口；先接Native能力和一个真实RN根Panel，再扩四域。删除NewAppScreen仅在样板可运行后进行，不改applicationId/appName制造安装身份变化。现有App.test模板随入口重构替换为有意义的bootstrap/错误态验证。

评审必须能回答：View是否纯渲染、VM是否可脱离React测试、个人数据是否随epoch清理、一次点击能否追到Native日志、快速切分类是否可能旧结果回写、取消/超时是否有唯一终态、共享卡片是否存在复制、RN是否遵守接口级通路/认证策略、是否绕过统一封装或Native持久化、子页返回是否恢复原列表、Android/iOS是否使用同一逻辑schema。

参考核查于2026-10-18：Redux/RTK Query、Axios和React Navigation官方文档见正文及V1.1章节。导航/测试/schema生成依赖的精确版本、性能和两端编译结果必须在G0后登记，本文件不宣称已通过。

## 12. V1.1 开发前工程整理门禁 RN-P0

当前App.tsx/index.js仍是CLI模板，设计目录并不等于已整理。**本次只优化方案；真正迁移属于RN-P0，必须在四域业务开发前完成。** 保留根package.json、app.json、metro.config.js、babel.config.js、tsconfig.json、android/ios/及specs/contracts/mock位置；按2026-10-18实施要求，将根index.js/App.tsx移至src/index.js与src/app/App.tsx，同步Metro及Native入口，AppRegistry组件名EntryTask不变。

目标目录在本文件§2基础上补齐：

```text
src/app/                  App/Providers/CompositionRoot/bootstrap/navigation
src/features/<domain>/
  public/                 稳定类型/只读契约/路由入口
  presentation/           screens/components/viewmodels/selectors/clientSlice
  domain/                 models/repositories/usecases（必要时）
  data/                   dto/mappers/endpoints/repository-impl
  di/                     feature factory
src/infrastructure/
  network/                EndpointRegistry/TransportRouter/HttpClient/错误和计时
    transports/           RnHttpTransport / NativeTransport
  state/                  StoreFactory/ApiService/生命周期与epoch失效
  logging/                Logger/Redactor/ConsoleSink/MemorySink/NativeSink
src/platform/ports/       无库实现类型的Port
src/platform/native/      五个NativeModule adapter
src/shared/               ui/tokens/assets/formatting/errors
```

前端采用feature-first，域内按MVVM分层；不是全工程components/hooks/services/utils平铺。`@app/@features/@infra/@platform/@shared`别名需TS、Metro/Babel和Jest一致配置并通过解析测试，不只改tsconfig.paths。公共export有边界，无大范围barrel链导致循环加载；未访问页面可延迟注册，入口不能一次执行所有feature副作用。

| RN-P0步骤 | 完成标准 |
| --- | --- |
| 入口与目录整理 | CLI启动组件名不变，根App薄、业务在src，基础目录与导出规范可审查 |
| 依赖选择/锁版本 | Axios、Redux Toolkit、react-redux/RTK Query、导航、测试工具在React19/RN0.86/Hermes验证，lockfile入库 |
| 网络封装 | 双Transport真实适配、EndpointRegistry、取消/总超时、错误/日志/schema，共同NetworkPort |
| 状态封装 | 单runtime单configureStore/ApiService，VM StorePort、selector、query和mutation样板，不复制服务端data |
| Logger封装 | 独立console/内存输出，跨端关联sink、脱敏、队列背压、bridge未就绪可调试 |
| 规则与样板 | ESLint依赖边界、禁止页面直用库、tsc/Jest/alias、一个原型API+一个HTTP测试API、一个VM/Screen通过 |
| 集成门禁 | Android优化包、single instance计数、bootstrap/生命周期/取消测试，iOS只维护兼容设计 |

结构参考Redux官方feature folders和RN官方工程集成边界；MVVM、Ports和目录颗粒度为本项目取舍，没有一份官方模板能自动保证“中大型”架构。成熟方案是采用有维护的库、清晰域所有权及自动检查，避免复制一个巨型starter带入无需求依赖。

## 13. Axios与双Transport详细封装

### 13.1 责任与策略

`NetworkPort`是统一消费接口，`TransportRouter`只解析注册策略，`RnHttpTransport`调用专属Axios实例，`NativeTransport`调用NativeNetwork；RN HTTP与Native桥接不是互相伪装的同一实现。页面/VM/UseCase/Repository不import axios或Codegen模块，data层通过库无关facade消费。

```ts
// 语义示例；EndpointDescriptor由注册表生成，不让页面选择通路。
type TransportKind = 'nativeMock' | 'native' | 'rnHttp';
type EndpointPolicy = {
  apiId: string; environment: 'demo' | 'test' | 'real';
  transport: TransportKind; authScope: 'none' | 'nativeBank';
  baseUrlKey?: string; cacheOwner: 'native' | 'rnMemory' | 'none';
  timeoutMs: number; policyRevision: number;
};
// invariant: rnHttp + nativeBank 无效，当前不把银行凭证传JS。
```

初始化时校验全部descriptor组合：nativeMock必须有fixture/handler、rnHttp必须有HTTPS Host/schema且authScope=none、nativeBank必须走Native；缺配置报ENDPOINT_NOT_CONFIGURED，不直到点击才悄悄默认走任一路径。未来独立服务认证需扩契约/需求，不用裸string token字段临时绕过。

本期A01～A37维持nativeMock。rnHttp支持能力现在进入工程设计/库封装范围，但暂无产品真实服务；其可用性用测试HTTP夹具验证，不能靠另写Axios Mock adapter返回JSON充当“真实RN通路验证”。Native Mock的1～3秒只属于nativeMock，RN实际HTTP使用服务响应耗时，不人为加延迟。

### 13.2 Axios HttpClient

按service/baseUrlKey创建并复用axios.create实例，禁止共享全局axios.defaults；仅transport模块可引用实例。拦截器在构建时注册一次，热重载/销毁eject，避免每挂载一页叠加header/日志。统一规则：

1. 请求：解析白名单Host与相对path、规范化query/body、默认10秒及外层总deadline、关联flowId/requestId/spanId；仅在服务契约允许时添加安全追踪header，不带银行Token/Cookie或原用户ID。
2. 执行：使用Axios XHR/该RN版本验证过的标准adapter，不安装“所有请求转NativeModule”的自定义adapter。AbortSignal传播，取消为Cancelled，不打印失败Toast；适配器接手deadline/cancel及唯一终态。
3. 响应：保持HTTP状态和业务码分开，保留原始受限bodyText后由共享schema校验器解析，禁止Axios自动宽松JSON解析吞掉malformed场景；业务data不能强制类型断言。
4. 错误：归一Http/Network/Timeout/Cancelled/Protocol/Business/Configuration错误；公开独立服务401不通知银行SessionCoordinator，重试由上层明确策略决定，默认不自动重试POST。
5. 日志：只打印apiId/Host别名/状态/耗时与安全错误，不dump config/request/response；AxiosError转safeError，防敏感对象递归泄露。
6. 边界：域名策略是工程约束，不宣称JS白名单能抵御恶意Bundle；未来银行凭证/专用TLS依旧必须Native，重定向/跨域行为在目标服务的RN平台适配测试中验证。

Axios支持实例、拦截器和AbortController取消，具体API组合需G0验证锁版。[实例](https://axios-http.com/docs/instance)、[拦截器](https://axios-http.com/docs/interceptors)、[取消](https://axios-http.com/docs/cancellation)

## 14. Redux Toolkit / RTK Query与MVVM封装

### 14.1 状态分类和事实来源

| 状态 | 所有者/存储 | 禁止重复 |
| --- | --- | --- |
| 焦点/展开/输入等短暂展示 | React组件局部state | 不把所有控件状态塞全局Redux |
| 筛选/分段/route客户端流程/导航intent | RTK feature slice，按routeKey/tabId隔离 | 不再建一个私有可变store复制同一客户端状态 |
| Native安全会话/根Tab投影 | RTK受控只读projection slice | 不保存Token，不把projection当Native事实来源 |
| 产品/资产/权益/消息等查询 | RTK Query api slice | VM/客户端slice不复制整份服务端data，不另建AssetsResource store |
| 账户/签到/库存等权威事实 | Native Demo DB/未来服务端 | RTK Query仅内存观察与共享，不自行计算奖励 |
| 持久偏好/公开缓存 | descriptor指定Native storage/cache | 不使用redux-persist，不把Query缓存持久化为第二套库 |

每个RN runtime一个StoreFactory产物/一个RTK Query ApiService（其内按域injectEndpoints），Provider只在根建立；feature首次注册受幂等保护，不能render时反复inject/注册middleware。Root四Tab共享Store和session projection；runtime销毁释放Store订阅、请求、定时器和Logger，新建runtime先取Native快照，不沿用旧epoch资源。

VM只依赖`ClientStatePort/Repository`等契约，RTK实现负责action/selector、endpoint initiate/select和订阅；无React Hook依赖，VM可用Fake Port测试。Route使用项目hook绑定VM及memo selector；Screen只接展示模型与action回调。领域层不importRedux，组件不直接使用feature endpoint生成的网络Hook；使用库不应破坏MVVM边界。

### 14.2 自定义baseQuery与查询共享

统一ApiService以自定义baseQuery接NetworkPort，捕获所有异常并返回RTK要求的`{data}`或`{error}`（meta只含可序列化安全信息）；配置decoder、transport、schema、session context，不让RTK默认fetchBaseQuery偷偷绕开Router。通过官方initiate/select接口在Repository中使用，不需要VM直接调用Hooks。[自定义baseQuery](https://redux-toolkit.js.org/rtk-query/usage/customizing-queries)、[Hook之外调用](https://redux-toolkit.js.org/rtk-query/usage/usage-without-react-hooks)

queryKey包含apiId、规范化参数、environment、transport、policyRevision、authScope及必要sessionEpoch。每页本地queryVersion拦旧UI回写；旧epoch结果还须在baseQuery/Repository校验后才能进入当前cache key。分页page/filter为显式参数，数据按ID去重；不靠把服务器列表复制进UI slice来做分页。

公共未订阅结果`keepUnusedDataFor=60s`，个人/搜索结果设0且无订阅即清；个人页重新进入显式forceRefetch，并用Native已有结果/重查策略避免错误延长敏感数据时效。设置只控制RN内存，不覆盖Native公开缓存300秒的TTL；显式下拉networkOnly并刷新两层投影。RTK按endpoint+参数共享缓存，保留时间应按接口配置。[RTK Query缓存说明](https://redux-toolkit.js.org/rtk-query/usage/cache-behavior)

取消订阅不等于立即中断RTK的共享请求：VM离开先unsubscribe并阻止本页回写；仅当封装层确认最后一个消费者释放时，才abort它拥有的底层查询。不能对任意组件的handle.abort直接取消其他页面仍用的共享资源。AbortController/Promise/任务表放adapter闭包，不进Redux state，测试覆盖两个消费者其中一个取消的情况。

### 14.3 写操作、失效和Native事件

RTK mutation只调统一Repository，幂等键归逻辑操作，超时保留key以便核实/重试。返回权威结果后，更新相关query projection并以tags失效关联摘要；tags按用户/业务资源隔离，例如MemberSummary、Claims、ProfileSummary、UnreadCount。跨域通过一个ApiService tag体系协调，不各建互不失效的query service。

sessionChanged先中止/隔离旧epoch、重置个人api state与client slices，再应用新快照；demoReset/场景/策略revision变化清受影响缓存并重建订阅。resourceRevision拒绝旧快照回退。默认关闭无条件refetchOnFocus/refetchOnReconnect与自动重试，前台恢复由HostCoordinator按可见性/过期统一调度，避免RN和Native同时重复请求；飞行模式不阻止nativeMock运行。

新增依赖权衡：RTK比少量自研store有包体和概念成本，但提供明确action/selector、可控middleware、查询缓存/失效与测试工具，适合本项目中大型演进；不再同时引入Zustand/TanStack Query/Saga承载同一类状态。release关闭Redux DevTools和完整action日志，敏感data不通过调试插件持久化。

## 15. RN Logger、故障排查与新增验收

LoggerPort定义`debug/info/warn/error/span`及child context，独立于NetworkPort和NativeModule。基础实现含Redactor、开发console sink、有界内存sink，NativeTelemetry仅是可选关联sink；bridge初始化之前也能输出安全bootstrap错误。Route/VM/用例、两个Transport和异常Boundary通过同一Logger facade，禁止散落console.log(JSON.stringify(response/state))。

| 场景 | RN日志 | Native关联 |
| --- | --- | --- |
| RN HTTP | request start/end/cancel/timeout、decode error、apiId/transport/耗时 | 核心流程摘要可汇总；不要求伪造不存在的Native网络span |
| Native调用 | bridge call start、ACK/return/error/timeout、请求大小 | 相同flowId/requestId，Native子span独立执行耗时 |
| 签到/领取/已读 | 用户意图→资格展示→提交→待确认/成功/失败→失效刷新 | 权威事务/幂等/错误码与同一操作链关联 |
| 导航/会话/恢复 | 命令revision、消费/超时、epoch清理、runtime切换 | Native Shell/Session/Host日志 |
| RN异常 | 渲染/异步安全堆栈、pageId、buildId/sourceMap标识 | bridge可用时安全写入诊断文件 |

同一事件仅创建一次eventId；console和Native落盘是sink分发，不生成不同事件假装新动作。每层span独立终态，core/bridge事件进入Native sink，普通verbose不跨bridge。队列/背压/重试/脱敏预算遵总体§13.3；无日志服务或bridge失败不阻断业务，诊断无法落盘时保留有界内存并反馈droppedCount。

新增测试：两个真实Transport与策略无回退；公共RN HTTP 401不触发银行退出；RTK query key隔离/标签失效/共享取消；Logger脱敏和bridge未就绪输出；RN-P0别名/架构规则；单instance/Store/ApiService初始化计数；APK assets和优化包离线运行。全部是待实施/待验证目标，本次未安装依赖或移动源代码。

实施记录（2026-10-18）：RN-P0基础和首页纵向样板见[运行说明](../development/rn-foundation.md)。此记录不代表四域全部页面、37API或全部宿主协议完成。
