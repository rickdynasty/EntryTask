# EntryTask 接口与 Mock 数据需求

从属文档：[原型需求规格说明书](01-prototype-requirements.md)

本文定义页面所需的数据、请求行为和可验收模拟结果。以下是**拟定的逻辑 API 契约**，不代表当前源码已有接口，不限定 bridge 方法名或 Native 实现库。除现有 `/api/user/login`、`/api/user/register` 外，路径均为建议基线；后续接口方案可统一调整，但必须保留 API ID 与页面映射，不能遗漏能力。

## 1. 通用数据约定

| 项目 | 需求 |
| --- | --- |
| 请求通路 | RN DataSource → NetworkPort/TransportRouter → Native 网络能力或 RN HTTP。HTTP项默认Native Mock；A03为Native退出命令，Native认证也走该通路；未配置实际服务的RN直连明确拒绝。禁止JS直读fixture、失败后切换通路或偷偷改Mock。 |
| API 标识 | apiId 为稳定逻辑名；method/path/query/body 形成唯一匹配；GET 参数规范化后匹配，POST 业务字段需校验，不能只看路径。 |
| 认证 | 公开接口不要求会话；受保护接口由 Native 注入会话；RN 不传 Token/userId 来伪造身份。opaqueUserId 只用于展示域隔离，不能作为授权依据。 |
| 请求标识 | 每次实际网络调用独立 requestId/traceId；核心操作 flowId 贯穿导航、bridge、网络和结果；幂等键另设，不把 requestId 与业务幂等键混为一谈。 |
| 响应 | HTTP 状态与业务码分开；正常业务包为 `{code,message,data,traceId}`，成功 code=200，与当前 Android 保持口径；失败 data=null，安全文案可展示。 |
| 分页 | 列表 data 为 `{items,page,pageSize,total,hasMore}`，page 从 1 开始，pageSize 默认10、最大50；越界返回空 items/hasMore=false；total 为当前筛选总数。 |
| ID 与空值 | 所有业务 ID 为稳定字符串，跨列表/详情/变更保持一致；必填字段缺失视为协议错误；可选字段 null 有定义，不默认转为 0 或空串。 |
| 金额 | `{currency:"CNY",minorUnit:2,amountMinor:"123456"}` 表示 ¥1,234.56；金额最小单位整数以字符串传递，RN 不以 Float 计算金额；余额可为0，收益允许负。 |
| 比率 | ratio 使用十进制字符串，如 `"0.0435"` 显示4.35%；同时给 indicatorLabel/period，不允许 UI 猜测或重复乘100。 |
| 时间 | 瞬时时间 ISO 8601 含时区/UTC；业务日期 `YYYY-MM-DD`，业务时区 Asia/Shanghai；业务日由 Mock Clock 给出；界面不能把截图07:06当业务时间。 |
| 文案与素材 | 标题、营销文案、入口目录、产品说明来自对应 JSON；图片为本地资源 key，RN 渲染映射，不提供任意远程 URL。路由目标为受控目标类型+ID，不执行服务端任意脚本。 |
| 信息脱敏 | 手机号/账户号在 Native 输出前脱敏；接口不返回真实身份证、真实银行卡或个人金融数据。认证响应含演示凭证时，仅 Native 内消费，不发往 JS。 |

HTTP 状态拟定：正常与业务校验失败用200+业务码；无会话用401+AUTH_REQUIRED；无权限用403+FORBIDDEN；未知 API 用404+MOCK_NOT_FOUND；已定义 API 下的资源不存在用404+RESOURCE_NOT_FOUND；Mock 故障场景可返回500。HTTP 非2xx、业务非200、JSON解析失败、桥接失败、取消和超时必须可区分。配套错误语义见第5节。

## 2. API 目录与页面覆盖

P=公开，L=Native 会话必需。所有 API 正常响应必须有独立 fixture 或明确指向对应 API fixture 的参数变体；下表的“关键返回”是最小字段要求，最终 schema 在技术方案细化。

| API ID | 方法与建议路径 | 权限/主要参数 | 关键返回/状态效果 | 覆盖页面 |
| --- | --- | --- | --- | --- |
| A01 auth.login | POST `/api/user/login` | P；username,password；沿用现有表单语义 | Native专用会话、用户ID、脱敏资料；建立完整会话 | N-01 |
| A02 auth.register | POST `/api/user/register` | P；username,password | 新用户、完整会话；重复账号错误；走统一自动登录 | N-01 |
| A03 auth.logout | Native Host `logout`命令，无HTTP路由 | L；无业务body | 清内存会话、递增epoch、清理私密页面；不删除注册账号 | P-03 |
| A04 home.content | GET `/api/home/content` | P | 搜索默认词、四快捷入口、主视觉/活动Banner、宫格、服务条、营销与浮动入口 | H-01 |
| A05 service.catalog | GET `/api/services` | P；group可选 | 服务ID、名称、图标、分组、权限、受控目标 | C-01、各页服务区 |
| A06 product.list | GET `/api/wealth/products` | P；category,scene,page,pageSize | 产品摘要；scene区分home精选/wealth推荐/普通列表，类型覆盖首页与财富全部产品入口 | H-01F、W-01E/F、W-02 |
| A07 product.detail | GET `/api/wealth/products/{id}` | P；id | 摘要+介绍、风险等级、指标口径/期间、期限、起购金额、详情段落、演示说明 | W-03 |
| A08 wealth.content | GET `/api/wealth/content` | P | 服务目录引用、推荐分类、市场解读articleId、专题及活动Banner | W-01 |
| A09 asset.summary | GET `/api/assets/summary` | L | 总资产/收益、收益日期、总负债/净资产、分类构成、截至时间；同一源 | W-01B、W-04、P-01D |
| A10 account.list | GET `/api/accounts` | L；page,pageSize | accountId、脱敏名称/卡号、类型、余额；统计总数口径一致 | W-05 |
| A11 account.detail | GET `/api/accounts/{id}` | L；id须归属当前账户 | 账户字段、余额、状态、明细入口 | W-05详情 |
| A12 transaction.list | GET `/api/transactions` | L；accountId可选,direction=all/in/out,page,pageSize | 金额、方向、日期、余额、类型、摘要；默认日期倒序 | W-06 |
| A13 transaction.detail | GET `/api/transactions/{id}` | L；归属校验 | 流水ID、账户、金额、余额、方向、类型、时间、脱敏对方信息 | W-06详情 |
| A14 article.list | GET `/api/articles` | P；category可选,page,pageSize | 资讯ID、标题、摘要、分类、发布时间、本地封面 | W-01资讯、W-07 |
| A15 article.detail | GET `/api/articles/{id}` | P | 标题、来源说明、发布时间、内容段落、本地图片 | W-07详情/市场解读 |
| A16 member.content | GET `/api/member/content` | P | 会员欢迎、专属/乐园分类、签到规则文案、会员卡片与权益ID | M-01 |
| A17 member.summary | GET `/api/member/summary` | L | 演示等级、能量余额、今日签到、连续日数、已领数量、业务日 | M-01登录卡/乐园、P-01B |
| A18 checkin.status | GET `/api/member/checkin` | L | businessDate、cycleId、连续天数、已签到日期、本周期已奖励档、能量余额 | M-03 |
| A19 checkin.submit | POST `/api/member/checkin` | L；expectedBusinessDate、idempotencyKey | 权威签到状态、awardedEnergy、余额、alreadyChecked；日期过时需刷新，不按过期日期写入 | M-03 |
| A20 benefit.list | GET `/api/member/benefits` | P；category可选,page,pageSize | benefitId、标题、类型、所需等级、有效期、库存展示、规则 | M-04、M-01卡片 |
| A21 benefit.detail | GET `/api/member/benefits/{id}` | P；有会话附带当前资格/已领状态 | 完整规则、资格、库存、领取状态；未认证请求返回loginRequired | M-04详情 |
| A22 benefit.claim | POST `/api/member/benefits/{id}/claims` | L；idempotencyKey | claimId、benefitId、claimedAt、当前状态、已领数量；重复返既有结果 | M-04领取 |
| A23 claim.list | GET `/api/member/claims` | L；page,pageSize | 当前用户已领记录、权益标题、有效期、状态、说明 | M-01已领、M-05 |
| A24 claim.detail | GET `/api/member/claims/{id}` | L；归属校验 | 领取ID、时间、权益与使用说明；无真实兑换码 | M-05详情 |
| A25 energy.records | GET `/api/member/energy/records` | L；page,pageSize | balance、记录ID、业务类型、增减值、时间、关联签到；日期倒序 | M-06 |
| A26 activity.list | GET `/api/activities` | P；category可选,page,pageSize | 活动ID、标题、起止时间、状态、本地封面、简述 | M-01活动、M-02 |
| A27 activity.detail | GET `/api/activities/{id}` | P | 起止时间、状态、规则、段落、本地素材、受控CTA目标 | M-02详情、各Banner |
| A28 profile.summary | GET `/api/profile/summary` | L | 脱敏资料引用、银行卡数、待办数、卡券数、积分、能量值 | P-01 |
| A29 profile.detail | GET `/api/profile` | L | 演示昵称、脱敏手机号、本地头像key、等级、低碳展示标签 | P-02 |
| A30 profile.marketing | GET `/api/profile/marketing` | P | 信用卡/借款营销、最高示例额度、利率说明、资产区营销与活动ID | P-01 |
| A31 personal.items | GET `/api/profile/items` | L；type=todo/points/orders,page,pageSize | type对应记录、摘要（含积分余额）、时间及只读详情ID | P-04 |
| A32 personal.itemDetail | GET `/api/profile/items/{id}` | L；type；归属校验 | 事项类型、标题、状态、时间、说明/变更值；无交易处理 | P-04详情 |
| A33 message.list | GET `/api/messages` | L；page,pageSize | messageId、标题、摘要、时间、isRead | C-03 |
| A34 message.detail | GET `/api/messages/{id}` | L；归属校验 | 标题、时间、内容、当前已读状态 | C-03详情 |
| A35 message.read | POST `/api/messages/{id}/read` | L；idempotencyKey | isRead=true、unreadCount；幂等更新 | C-03详情 |
| A36 message.unread | GET `/api/messages/unread-count` | L | unreadCount，与列表同源 | 四根页角标 |
| A37 search.query | GET `/api/search` | P；keyword,scope=services/products/articles/activities,page,pageSize | 统一{type,id,title,summary,target}；仅所选范围；不搜个人数据 | C-02 |

范围说明：C-04/C-05 为本地 RN 说明模板，不额外定义业务 HTTP；P-03 关于页/退出确认是本地 UI，金额显隐为会话内状态，其他typed偏好通过 Native 存储能力；Native 会话快照、能力协商、宿主导航不是 HTTP API，不为它们添加虚构网络延迟。A03是Native受控命令，不参与HTTP Mock延迟或fixture计数。

目录引用规则：A04/A08/A16/A30 可引用 A05 的 serviceId、A07 的 productId、A21 的 benefitId、A27 的 activityId。内容接口不得给出没有对应详情或演示说明目标的悬空 ID。列表页只请求当前选项；不一次加载所有分类，再让页面从万能 JSON 裁切。

## 3. 本地 JSON 文件与匹配规则

推荐逻辑结构如下，实际目录由技术方案确定。文件必须随仓库交付、可审查。Android构建必须将manifest、逐API JSON、seed及schema/hash打入APK的assets/mock/，不只放源码目录；优化演示release无Metro且飞行模式可读取。AAB交付还需验证实际安装split内资源；未来iOS打入应用bundle。运行变更进入数据库，不改只读assets。

```text
mock/
  manifest.json                        # apiId、method、path模板、权限与fixture映射
  auth/login/request.schema.json
  auth/login/response.success.json
  auth/login/response.invalidCredentials.json
  auth/register/request.schema.json
  auth/register/response.success.json
  auth/register/response.duplicate.json
  home/content/response.success.json
  wealth/products/request.schema.json
  wealth/products/response.success.fund.page1.json
  wealth/products/response.success.fund.page2.json
  wealth/products/response.empty.json
  wealth/products/response.businessError.json
  member/checkin/request.schema.json
  member/checkin/response.success.json
  member/checkin/response.alreadyChecked.json
  member/benefitClaim/response.success.json
  member/benefitClaim/response.soldOut.json
  ...                                  # 第2节全部API逐项覆盖
  seeds/users.json
  seeds/accounts.json
  seeds/member-state.json
  seeds/messages.json
  scenarios/acceptance.json             # API级场景及Clock/延迟控制
```

上图为命名示意，不是声称已生成数据文件。每个 API 至少有请求参数约束（JSON schema或等效JSON描述）和正常响应 JSON；集合接口有 empty；有业务校验的接口有对应业务失败文件；关键场景有错误响应文件或共享错误模板的显式映射。最终交付时 manifest 可自动枚举全部 API、场景、文件路径并校验不存在缺失文件。

匹配优先级：apiId/method/path 命中 → 参数及归属校验 → 当前 API 显式故障场景 → 当前用户/资源状态 → 参数变体 → 正常 fixture。无效参数返回 VALIDATION_ERROR；无效资源返回 RESOURCE_NOT_FOUND；没有匹配 API 返回 MOCK_NOT_FOUND。查询参数顺序不影响匹配，category/scene/direction/keyword/page 等改变必须改变对应结果，不能永远返回第一页。

JSON 是结构/示例数据与初始化来源；运行时签到、领取、已读由 Native Mock 状态生成结果，并按同一响应 schema 输出，不能为每次操作覆盖仓库内的 JSON 文件。动态字段如 requestId、traceId、时间和余额可受控填充，禁止不安全拼接字符串构造 JSON。

### 3.1 示例：产品列表正常响应

```json
{
  "code": 200,
  "message": "success",
  "traceId": "mock-trace-example",
  "data": {
    "items": [{
      "id": "product-fund-001",
      "name": "演示纯债基金A",
      "category": "fund",
      "indicator": {
        "ratio": "0.0267",
        "label": "近一年涨幅（模拟）",
        "period": "2025-10-14/2026-10-13"
      },
      "minimumAmount": {"currency": "CNY", "minorUnit": 2, "amountMinor": "100"},
      "riskLevel": "R2",
      "riskText": "模拟产品，历史表现不代表未来收益",
      "isDemo": true
    }],
    "page": 1,
    "pageSize": 10,
    "total": 1,
    "hasMore": false
  }
}
```

例子说明字段语义，不要求产品总数为1。验收种子必须覆盖第6节的数据规模。

## 4. 模拟状态、幂等与时间

| 状态 | 权威来源与持久性 | 规则 |
| --- | --- | --- |
| 会话 | Native 单一会话存储，进程内 | Mock凭证只供Native；进程终止后不可凭旧落盘Token恢复 |
| 演示账号 | Native模拟服务端存储，跨进程 | 固定种子用户+可注册用户；账号唯一，不能仅保存“最后注册账号”导致前者丢失；密码验证数据不明文存储 |
| 个人资产/账户/流水 | 用户维度固定种子；资产不作页面持久缓存 | 只读，一份事实供各入口；总资产等于各资产分类之和，净资产=总资产-负债；账户余额与资产分类口径明确 |
| 签到/能量账本 | 用户、业务日/周期，跨进程 | 唯一(user,businessDate)；第3/7天各一次奖励；奖励及账本记录原子写入，失败不部分增加；Clock可控制跨日 |
| 权益/领取 | 权益库存+用户领取记录，跨进程 | 唯一(user,benefitId)；检查有效期/等级/库存后原子领用；库存不能小于0；同幂等键不同payload返回IDEMPOTENCY_CONFLICT |
| 消息已读 | 用户、messageId，跨进程 | read只从false变true，一次有效更新；unreadCount由该状态计算 |
| 统计摘要 | 从上述权威状态派生 | 不手写另一份不随操作更新的卡券/能量/未读数字 |
| 展示偏好/公开缓存 | Native存储，按类型/用户隔离 | 偏好按账户保留；公开缓存5分钟；重置种子时一并清理相关缓存 |

所有写操作以业务唯一约束抵御不同 requestId/幂等键的重复。幂等键在同一逻辑操作的重试中复用；已提交但响应超时，重试返回已提交结果，不再次扣库存或奖励。事务完成前取消不产生状态变更；完成后取消不回滚已成功事实，下一次读取显示权威状态。网络层不自动重放 POST，UI 可提供用户主动重试并保留原幂等键。

Native Mock Clock 默认跟随真实当前时间，业务时区固定上海；验收可冻结/推进时间，所有活动/权益有效期、签到业务日和响应时间使用同一 Clock。fixture 用相对种子时间初始化或固定验收时间，避免原图10月活动导致日后全部失效。资产“截至日期”可使用固定快照日期，不能伪装为实时行情。

## 5. 错误和场景矩阵

Native Mock场景控制仅用于开发/验收，不暴露普通用户任意切换业务状态的入口。支持应用级默认、API级覆盖和下一次请求故障注入；设置后有清晰回显，可重置，日志记录场景。正常演示随机延迟；确定性测试允许固定1000/2000/3000ms或随机种子。

| 场景 | Mock/Native行为 | RN期望 |
| --- | --- | --- |
| success | 200+code200，完整数据 | 正常内容 |
| empty | 集合items=[]，total=0 | 明确空态；非集合不套用空数组 |
| businessError | 200+非200业务码 | 业务文案/失败态，已有内容保留 |
| httpError | 500及安全错误体 | 可重试错误，不误解析为成功 |
| offline | 注入NETWORK_UNAVAILABLE | 首载失败或旧公开缓存；正常飞行模式不等同此注入场景 |
| timeout | 等待至请求超时触发 | 超时提示可重试；不能永久Loading；延迟可超3秒，仅此显式异常模式允许 |
| malformed | 200+非法JSON或必填缺失 | 协议/解析失败，不能白屏/误用0 |
| authExpired | 401/AUTH_REQUIRED | 同一有效会话仅失效一次，清个人态 |
| forbidden | 403/FORBIDDEN | 说明无权限；会话仍有效 |
| cancel | 主动取消请求，终态CANCELLED | 无失败Toast、不回写已离开的页面 |
| storageError | 事务/偏好读写注入失败 | 写业务不假成功；偏好提示未保存 |
| staleResponse | A旧会话或旧筛选延迟后返回 | 被新页面/新会话忽略 |
| duplicate | 连点/重试/不同幂等键重复业务 | 只一条签到/领取记录，结果可重复读取 |
| unknownApi | 无method/path匹配 | MOCK_NOT_FOUND，日志可定位，无公网放行 |
| unavailableBridge | 宿主缺能力/版本不支持 | Native指定通路显示基础能力故障，不自动切RN HTTP；RN指定的独立公开API可按策略继续运行 |

业务错误至少覆盖：VALIDATION_ERROR、INVALID_CREDENTIALS、ACCOUNT_EXISTS、AUTH_REQUIRED、FORBIDDEN、RESOURCE_NOT_FOUND、BUSINESS_DATE_CHANGED、BENEFIT_NOT_ELIGIBLE、BENEFIT_SOLD_OUT、BENEFIT_EXPIRED、IDEMPOTENCY_CONFLICT、MOCK_NOT_FOUND。重复签到、已领权益、重复已读返回既有成功结果及重复标记，不作为未知异常；状态已过期则按资格/有效期规则处理。

## 6. 最低演示种子与验收数据

- 两个稳定用户：`demo_a`/`Entry123!`、`demo_b`/`Entry123!`，均为本地公开演示凭据，不得用于任何真实系统；支持额外注册账号。A有资产、消息及普通会员资格；B具有不同资产/身份和较高演示等级，以验证隔离及等级权益。A/B数据必须可识别地区分。
- 产品至少覆盖理财、基金、存款、私募、保险、黄金、零钱+、信芯家族；“钻石理财/优选/热门”等作为场景/标签。至少一个类别有12条以覆盖两页；另有空类、负收益、0、长名称和失效产品场景。
- 资产金额可核算：例如A总资产100,000.00、负债20,000.00、净资产80,000.00；分类之和必须一致；至少一个账户与12条流水，包含收入/支出/0余额/长摘要；B使用不同数值。页面均标明模拟，不要求采用这些示例数值。
- 资讯、活动、消息、已领权益/个人事项各提供可验证分页的正常集合或专用分页场景；列表详情引用完整。消息至少6条未读，对应截图角标只是演示种子，已读后按真实本地状态变化。
- 至少3种活动状态：未开始、进行中、已结束；各有详情及受控CTA。营销利率、额度只使用营销接口数据，不映射为个人授信。
- 权益至少覆盖可领、已领、等级不满足、库存0、过期；等级礼遇与好书对应有效ID；领取详情完整；可用单独验收预置账户生成12条领取记录，不能借重复领取同权益凑数。
- 签到提供第2天待签到、第6天待签到、已签到、断签、新周期和跨上海零点场景；场景重置可重放3/7天奖励，不必真实等待7天。
- 新注册用户：用户域有唯一ID与演示资料；初始资产/负债/收益为0、账户/流水/待办/订单/卡券/积分/能量/消息为空，普通等级，可领无门槛权益；展示0与空态，不继承demo_a的数据。
- 所有种子有数据版本；重置恢复到同一版本的可重复结果。全量重置清除注册账号及其数据，需显式确认；普通退出不重置。

## 7. 后续技术方案必须提交的契约成果

1. 按 API ID 输出最终请求/响应 schema、字段可空规则、HTTP/业务错误、页面引用及transport/auth/cache/telemetry策略；HTTP API显式默认nativeMock，退出单独使用Host命令。
2. 输出 bridge 网络/存储/认证/日志/导航的序列化类型、异步终态、取消和版本协商机制；说明RN版本实测依据。
3. 输出 Mock manifest、逐 API JSON 文件清单、状态更新/持久化/幂等方案和 Clock/场景控制入口；禁止内联万能JSON或公网fallback。
4. 输出Mock账号校验数据的存储边界、schema升级与异常策略；会话不落RN存储，不以清数据解决升级。
5. 输出验收脚本或步骤，覆盖正常1000～3000ms、固定延迟测试、分页、故障、跨日、重复、切账号、进程重启和重置；记录实际证据。
6. Android 本期逐项验证；iOS只维护逻辑契约兼容要求，未来实现时复用数据并补平台测试，不在本期填通过。
