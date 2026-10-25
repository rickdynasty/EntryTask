# ADR-014：按构建来源选择RN或XML Native四根页

日期：2026-10-24。状态：按用户新增需求实施。

## 背景

此前四域页面由RN实现、HomeFragment用源码常量保留Native占位。新增要求是官方CLI命令直接运行RN，而Android Studio运行XML Native原型，认证和根底栏继续共用。

## 决策

使用编译期BuildConfig选择页面渲染器。优先级：显式`entrytaskUiMode`覆盖 > IDE标记 > 当前官方CLI的devServerPort参数 > 默认Native。不依赖Metro连通性或外部Intent切换。保持应用包名、组件注册、入口、RN插件及单Host约定。

新增有实际职责的biz_dashboard模块，包含XML四根页、共用自定义View、VM、领域Repository及Mock适配。app只组装和连接Shell。Native与RN共用已有Mock客户端、分API JSON与会话权威；不复制响应fixture或持久查询缓存。共享装饰图用声明输入/输出的AGP生成任务打包。

## 影响与边界

- IDE/CLI生成不同配置的同一安装身份APK，切换需覆盖安装；桌面重开不会改变模式。
- CLI的binary-path复用既有APK，不承诺重新识别来源；升级CLI时复核端口注入约定。支持显式参数作为稳定自动化入口。
- Native根页提供可见入口反馈，Native业务子页和写操作未扩展；RN已有全流程不因此迁移或删除。
- 未引入flavor/第二个runtime/第二套根Tab状态。iOS保持原范围。

操作、数据流和验证命令见[双页面模式说明](../development/native-dashboard.md)。
