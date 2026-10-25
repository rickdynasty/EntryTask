# 工程文档

方案说明当前实现及明确标出的待实施边界；需求说明产品预期；新手指南解释概念与源码。文档不维护逐次修改流水，运行与验收结论应以对应代码版本的实际检查为准。

## 设计方案

- [总体设计](design/EtryTask%20项目设计方案.md)
- [首页与四 Tab](design/首页与四Tab技术方案.md)
- [登录注册](design/登录注册技术方案.md)
- [RN](design/前端RN技术方案.md)、[Android](design/Android%20技术方案.md)、[iOS（待实施能力）](design/iOS技术方案.md)
- [Room 认证存储](development/Room认证存储设计.md)、[认证 UI](ui/登录注册UI实现说明.md)

## 开发与学习

- [工程 README](../README.md)、[RN README](../src/README.md)、[Android README](../android/README.md)
- [首页 UI 新手指引](development/首页UI新手指引.md)
- [登录注册新手指南](development/登录注册功能新手指南.md)
- [Room 新手指南](development/Room新手使用指南.md)
- [启动与登录源码详解](development/启动与登录全链路源码详解.md)
- [双页面运行模式](development/native-dashboard.md)、[RN 运行说明](development/rn-foundation.md)
- [日志排障](development/troubleshooting.md)、[RN 预热测量](development/rn-warmup-performance.md)
- [Compose Preview](../android/docs/compose_preview使用指引.md)

## 产品与关键决策

- [需求入口](requirements/README.md)
- [默认认证](adr/011-required-native-authentication.md)
- [唯一引擎预热](adr/012-rn-engine-warmup.md)
- [构建选择页面模式](adr/014-build-selected-native-dashboard.md)
- [Room 认证存储](adr/015-room-auth-credential-storage.md)
