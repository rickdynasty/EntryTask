# EntryTask Android 工程文档

> 需求基线更新（2026-10-17）：本文件为旧技术方案初稿。后续方案请先阅读[原型需求规格说明书](../../docs/requirements/01-prototype-requirements.md)和[接口与 Mock 数据需求](../../docs/requirements/02-api-mock-requirements.md)。本期明确由 RN 实现四 Tab 及全部业务子页，Native 提供认证、宿主、网络/存储/日志能力；本地 Mock 经 Native 网络拦截并按 API 使用 JSON，普通请求随机延迟 1～3 秒；iOS 本期延期。本文中原生业务 UI 优先、直接 Fake 数据、注册返回登录及强制 Room 样板等冲突项不再作为本期实施依据，待技术方案统一修订。

版本：1.0 · 编制日期：2026-10-17 · 状态：建议基线，供评审与后续实施

本文档包基于当前 Android 源码及仓库根目录 `prd/Home.jpg`、`Wealth.jpg`、`Member.jpg`、`Me.jpg` 编制。方案中的新增模块、接口、代码示意和验收目标均为待实施设计，不代表当前已具备这些能力。本次仅新增设计文档，未修改业务代码或依赖。

## 阅读顺序

| 文档 | 解决的问题 | 主要读者 |
| --- | --- | --- |
| [工程架构与能力封装设计](01-architecture-design.md) | 为什么这样分层，各模块怎么实现、如何协作 | 架构负责人、开发、评审人员 |
| [工程开发与架构规范](02-engineering-standards.md) | 新增和修改代码必须遵守什么规则 | 所有开发与代码评审人员 |
| [实施路线与验收清单](03-implementation-roadmap.md) | 先做什么、如何迁移、怎样证明完成 | 开发、测试、任务负责人 |

## 基线决策

- `app` 是组装根；`biz_*` 按业务域组织；`lib_*` 仅提供通用技术能力。
- 全业务使用 MVVM：View → ViewModel → 可选 UseCase → Repository → DataSource；通过只读状态驱动 UI。
- Android 原生 MVVM 是本期业务落地路径：保留 Fragment 宿主，新业务 UI 优先 Compose；保留 RN 工程基础，不同时实现同一业务的两套状态与数据逻辑。
- 基础三库继续保留；先修边界，再引入确有需要的业务模块和契约模块，不为每个页面机械拆 Gradle 模块。
- 游客浏览、注册后返回登录、会话仅进程内有效是本方案的默认产品假设，待产品确认；不是从图片中推导出的完整业务事实。
- 模拟项目内的金额、收益和权益均为演示数据；真实转账、支付、授信等交易闭环不在本期默认范围。

## 文档维护

设计文档负责说明方案；规范文档负责约束；实施文档负责排期和验收。发生冲突时，先修订受影响文档并记录决策，再实施。产品未决事项集中维护在设计文档第 3 节；完成某阶段时在实施文档记录实际版本、命令、结果和证据，不能把计划直接标记为已完成。

后续架构变更使用 ADR，至少记录：背景、选项、决策、影响、迁移、验证、日期和负责人。可从实施文档附录复制模板。

## 当前技术设计入口

2026-10-19 当前基线为 [Android 技术方案 V1.2](../../docs/design/Android%20技术方案.md)：明确 RN 全局登录门禁和 singleTask 认证返回流程、AppGlobals/Startup 对原 AppProxy 的替代、lib_net 下载初版与可靠性缺口。以下旧稿中的游客浏览与 AppProxy 初始化建议均为历史背景。

本目录保留历史初稿。当前混合应用总体设计见[EtryTask 项目设计方案](../../docs/design/EtryTask%20项目设计方案.md)，本期Android实施依据见[Android 技术方案](../../docs/design/Android%20技术方案.md)，共享业务页面见[前端RN技术方案](../../docs/design/前端RN技术方案.md)，iOS延期设计见[iOS技术方案](../../docs/design/iOS技术方案.md)。

2026-10-18：当前设计已修订为V1.1，支持按API选择RN/Native网络、RN独立Logger及核心跨端关联、Axios/Redux Toolkit/RTK Query封装、开发前RN-P0整理与单RN instance。37原型API默认Native Mock，JSON随APK打包；本目录历史初稿不覆盖这些新决策。
