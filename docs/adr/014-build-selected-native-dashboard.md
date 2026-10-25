# ADR-014：构建选择 RN 或 Native 根页

状态：采用。

## 决策

app Gradle 计算 BuildConfig.USE_REACT_NATIVE_PAGES：显式 entrytaskUiMode 优先，其次 IDE 标记 Native、CLI 端口参数 RN，默认 Native。RootContentHostFragment 读取构建配置，选择 Compose Native 四页或唯一 RN Surface。

两种模式共用包名、MainApplication、MainActivity、认证、根底栏、API 与 Mock 资源。Native 根页放 biz_dashboard，按分域 MVVM 实现；业务可见 UI 使用 Compose，XML 保留稳定宿主容器。

## 取舍

模式写入 APK，切换必须重新构建安装；桌面重开或 Reload 不改变模式。CLI 安装已有 binary 不会重新判断来源，升级 CLI 时需复核端口参数约定。自动化使用显式参数。

不新增 flavor、第二个 runtime 或根 Tab 状态源。两端共享协议和素材，各自负责视图与生命周期；演示入口与实际写操作范围以首页方案为准。

[运行模式](../development/native-dashboard.md) · [首页方案](../design/首页与四Tab技术方案.md)
