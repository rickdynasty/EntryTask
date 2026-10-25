# Android 双页面运行模式

## 模式选择

| 启动方式 | 默认内容 |
| --- | --- |
| 仓库根目录 `npx react-native run-android` | RN 四根页及业务子页 |
| Android Studio 打开 android 工程并运行 app | Native Compose 四根页 |
| 直接 Gradle 构建 | Native；可显式覆盖 |

app/build.gradle 的选择优先级：`-PentrytaskUiMode=rn|native` → IDE 标记 Native → CLI 的 reactNativeDevServerPort 参数 RN → 默认 Native。结果写入 BuildConfig.USE_REACT_NATIVE_PAGES，RootContentHostFragment.usesReactNative 读取。

两种模式共用 com.entrytask、MainApplication、singleTask MainActivity、认证和 Compose 根底栏。选择由构建输入确定，不由 Metro 是否连接决定；覆盖安装 APK 才能切换，Reload 无法切换。CLI 安装已有 binary 也不会重新选择模式。不要在 gradle.properties 中永久保存 CLI 端口或临时模式参数。

```sh
# 仓库根：官方 RN 路径
npx react-native run-android

# android/：显式 Native 或 RN 构建，之后仍需安装
./gradlew :app:assembleDebug -PentrytaskUiMode=native
./gradlew :app:assembleDebug -PentrytaskUiMode=rn
```

Release 的直接 Gradle 构建同样默认 Native；需要 RN 内置 Bundle 时显式选择 rn。Native 模式保留 RN 插件、Codegen 和集成配置，但不挂载 ReactFragment、不执行 RN 预热。

## 结构与数据流

RootContentHostFragment 是唯一根内容宿主。Native 分支创建 ComposeView，NativeDashboardRoute 绑定当前 Tab 的分域 VM；RN 分支恢复或创建唯一业务 Surface。XML 只定义 MainActivity 容器，可见业务卡片、宫格与四页布局使用 Compose 或 RN。

```text
Native：Screen → ViewModel → Domain Repository → NativeApiClient → Native Mock
RN：Screen/Route → ViewModel → Repository → NetworkPort → Bridge → Native Mock
```

两种实现共享产品区域、API、素材和演示账本，各自管理 UI 状态与生命周期。默认认证、epoch 隔离、切页取消和刷新保留数据的行为一致；签到为真实 Mock 写操作，多数其他入口为演示说明，不能据根页存在认定全部 Native 业务子页已实现。

- [首页与四 Tab 技术方案](../design/首页与四Tab技术方案.md)：区域、组件和 MVVM
- [首页 UI 新手指引](首页UI新手指引.md)：按源码理解层次
- [Android README](../../android/README.md)：构建、覆盖安装和测试
