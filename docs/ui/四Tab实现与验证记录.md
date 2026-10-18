# 四 Tab 实现与验证记录

日期：2026-10-18。设计输入见 [原型分析与 RN 页面设计](四Tab原型分析与RN页面设计.md)。本文件区分当前实现、实际验证与剩余范围。

## 页面交付

| Tab | 已落地内容 | 主要实现入口 |
| --- | --- | --- |
| 首页 | 搜索/消息/客服、四快捷、秋冬主视觉、两行宫格、服务条、认证营销、分类产品卡、跨 Tab 财富入口 | `features/home/presentation/HomeScreen.tsx` |
| 财富 | 财富/资讯分段、资产掩码/偏好、市场资讯、服务宫格、推荐分类、纯债演示图、活动卡 | `features/wealth/presentation/WealthScreen.tsx` |
| 会员 | 游客/登录卡、会员/活动/已领分段、专属/能量区、签到奖励、卡号/等级/图书营销 | `features/member/presentation/MemberScreen.tsx` |
| 我的 | 游客/脱敏身份、五统计、常用服务、共享资产、信用卡/借钱营销、设置与退出 | `features/profile/presentation/ProfileScreen.tsx` |

组件沉淀在 `shared/ui`，域 VM 经 ResourceSource 访问封装的 RTK Query。四页共用一份 Native 用户资产偏好，根 Tab 为 Native 权威。子页是有真实返回/加载/错误/分页/列表详情的 RN 通用模板，不是四张截图贴图。

子页覆盖服务目录、搜索、产品/资讯/活动列表详情、资产、账户/流水空态、会员权益/已领/能量、签到、个人资料/事项、消息、设置与金融服务说明。账户与事项等新注册账号以0/空态演示，未实现真实金融动作。

## 数据及架构增量

- 34个 RN endpoint/schema/fixture；全部走独立 Native demo client。本轮不提供公网服务，也不将既有 Native 认证冒充已完成全部重构。
- 新增 `android/demo_engine`，避免把签到/领取/消息规则塞入 app 或通用 lib_net；本地用户账本原子提交。
- Host 协议1.1：单 Surface、快照+revision事件、Native选中Tab、RN深度、认证/退出受控命令。旧安装包会明确提示需要重建，不能仅刷新 JS。
- RN 独立日志继续保留，网络结果与 Native Mock 使用 requestId 对照；界面切换不新建 runtime。
- 原型装饰图来源见 `src/shared/assets/README.md`。文字、按钮、图标、图表和布局由 RN / SVG 实现；仅 Native 底栏和认证为原生控件。

## 实际验证

本轮先遇到 API37 模拟器系统包管理服务消失/DeadSystemException，保留用户数据冷启动后恢复；后续出现QEMU图形线程挂起与旧崩溃报告弹窗阻塞，最终以软件图形后端且不上传报告的方式重启。早期 Fast Refresh 中间态错误不计为最终通过证据；最终以重新安装、冷启动进程为准。

| 检查 | 实际结果 |
| --- | --- |
| TypeScript / ESLint / 架构检查 / 资源契约 | 已通过；ESLint无新增警告 |
| Jest | 7 suites / 16 tests通过；涵盖查询共享/隔离、Native revision、跨会话偏好、协议、日志等 |
| Native 签到 | 3个JVM用例通过；3/7日奖励、重复、断签/过期业务日、用户隔离 |
| JS生产Bundle | 已成功生成，含本地素材与原生导航模块图 |
| Android Debug | arm64构建成功；官方 `npx react-native run-android` 默认全ABI编译、安装、启动成功 |
| 四根页 | 已实际切换并核对最终截图；API37/arm64、1080×2400、默认字体、游客态 |
| 子页导航 | 已观察子页隐藏Native底栏、认证取消返回会员、注册登录后恢复签到目标；签到结果与领取后卡券数跨页同步已在设备验证。搜索ETF后切资讯返回匹配结果，Back先收键盘且保留搜索页，结果可进入资讯详情 |
| Release / 最终资源字节核对 | Release arm64构建通过；Debug/Release各79个契约/Mock JSON与源码逐字节一致，Release内置JS Bundle，13张装饰图按解码后像素核对通过；已安装Release，开启飞行模式且Wi-Fi/移动数据均为0，冷启动后首页和本地Mock内容正常渲染 |

Debug设备曾提示 Redux serializable middleware 耗时40ms；没有关闭校验或屏蔽Console，性能不能据此当作Release结论。模拟器还出现过System UI ANR，此项为环境限制，不伪造无故障记录。

## 页面截图与设备清理

最终截图：[首页](verification/home.png)、[财富](verification/wealth.png)、[会员](verification/member.png)、[我的](verification/profile.png)、[搜索](verification/search.png)、[资讯详情](verification/article.png)、[Release离线首页](verification/release-offline.png)。根页截图均为最终Debug安装包；没有对截图内容进行修饰。

验证登录/签到/领取时先备份原账号偏好，完成后已恢复并比较一致；仅删除本轮新建的测试账本，未清空应用数据。最终官方CLI重新安装和启动成功；离线验证后恢复飞行模式关闭、Wi-Fi/移动数据开启及Debug包；早先因模拟器离线导致的一次安装失败保留为环境记录。

## 视觉差异与剩余范围

- 原型是五 Tab，工程按已确认需求为四 Tab；游客身份不泄露截图中的手机号，金融动作均为说明入口，这是有意差异。
- 图片无字体、切图、矢量/图层原稿；采用系统字体及压缩截图局部装饰素材，存在字形、边缘背景和清晰度差异，不声称像素级完全一致。
- 截图外的子页依需求补齐设计系统模板，无原稿可用于像素验收。某些营销图文字来自固定示例，均标明模拟，需正式素材后替换。
- 本轮未完成生产级会话失效协调、完整进程/配置恢复、Room/global库存及全部API故障场景；未对四域全部排列组合做验收。
- 当前Native认证仍是既有实现，固定多账号种子/安全验证迁移未完成。新注册账户资产/账户/流水为空；消息是独立用户的演示消息种子，尚未落实新用户完全空消息策略。
- iOS业务与Pods均未修改或验证；依赖接入不代表iOS原生能力已存在。

后续优先：精确素材与字体替换 → 四页逐尺寸视觉验收 → 会话/生命周期与故障注入专项 → Native演示账本及认证迁移 → iOS对等实施。
