# RN 工程与运行说明

RN 代码位于 src，入口 src/index.js、根组件 src/app/App.tsx，组件注册名 EntryTask。根目录配置、specs、contracts 和 mock 是同一工程组成部分，不另建 RN 子工程或根 index.js。

## 启动

仓库根目录：

```sh
npm ci
npx react-native run-android
```

依赖已安装时不必重复 npm ci。可在独立终端 npm start 运行 Metro；按 j 打开 RN DevTools。另开终端 npm run logs:android 查看设备日志，多个设备的实际 serial 用 adb devices -l 确认。

官方 CLI 默认构建 RN 页面，Android Studio 默认 Compose Native 四根页，切换需重建安装同一 com.entrytask。具体输入优先级与覆盖方式见[双页面模式](native-dashboard.md)。Native 代码、Bridge、Codegen、Gradle 或 APK assets 变化不能只用 Fast Refresh 验证。

## 架构和启动边界

App 在 Surface 挂载时创建一次应用图，组合根注入 Repository、网络、日志、状态及 Native Port。View 负责渲染，VM 负责状态和动作，域契约隔离 IO，实现及组装按职责分目录。详细目录见[RN README](../../src/README.md)和[RN方案](../design/前端RN技术方案.md)。

Native 提前判断登录态并在 MainActivity RESUMED 拉起认证；RN 挂载后仍默认保护业务页，经 Session Bridge 校准会话，HelpCenter 公开。认证请求通过 Host Bridge，最终是否成功由 Native 会话决定。单ReactHost与单业务Surface贯穿四Tab；Native底栏为唯一根导航权威。

域通道在effect中按epoch创建与清理，Route绑定订阅及激活，Screen只组装区域。服务端响应由RTK Query管理，客户端分类/滚动由封装状态管理；不在VM复制持久缓存。

## 网络与资源

EndpointRegistry按API选择Native或RN HTTP，不因失败切换通路。当前演示请求由Native本地拦截并读取分API JSON，普通延迟1～3秒，可取消；RN不import响应fixture替代请求。

contracts运行时JSON通过Metro解析，Native使用的contracts/mock由Gradle生成并打入APK。素材从src/shared/assets单份来源生成Native资源。共享资源更改需要同步契约校验和重新打包。

Native账号校验记录使用Room，登录会话在Native内存；普通偏好与demo_engine轻量账本保持各自存储边界。RN没有账号库或银行Token副本。

## 验证与排障

```sh
npm run typecheck
npm run lint
npm run architecture:check
npm run contracts:check
npm test -- --runInBand
```

schema改动执行contracts:generate；入口/Metro改动补bundle:android；Bridge/Codegen改动补Native生成构建和官方CLI主链路。Watchman不可用时明确环境原因后可使用 --watchman=false，不跳过失败用例。

RN Logger必须独立输出初始化诊断，Native链路用flowId/requestId关联。构建终端没有JS输出不代表没有日志；不可用页按错误码检查模块注册、协议及APK资源，见[排障说明](troubleshooting.md)。

- [首页方案](../design/首页与四Tab技术方案.md)
- [认证方案](../design/登录注册技术方案.md)
- [启动源码详解](启动与登录全链路源码详解.md)
