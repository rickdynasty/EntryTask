# 登录注册 UI 实现说明

更新日期：2026-10-22。实施范围为 Android Native 认证页面，保留 Compose + MVVM、独立页面 ViewModel 及既有认证导航；RN 与 iOS 本次没有改动。

## 参考与视觉

用户提供的外部登录截图路径无法读取，因此没有进行该图片的逐像素比对。本次依据明确的文字要求及仓库 `prd/Home.jpg`、`prd/Me.jpg` 完成设计：使用中信银行名称、已有首页圆形线条标志、暖白背景和红色主操作。标志沿用工程现有 `ic_tab_home.xml` 的几何路径，不将它声称为官方完整品牌素材。

- 顶部银行标志、中文名称、英文名称与欢迎语。
- 页面标题区分“账号登录”“注册新账号”，表单结构保持一致。
- 用户名、密码标签固定在输入框上方，输入框内提供操作提示。
- 输入框使用白底、浅色描边、8dp圆角；聚焦时描边使用品牌红。
- 按钮至少52dp高，密码显隐与页面切换保留至少48dp点击区域。
- 支持滚动、系统安全区及键盘避让，宽屏表单限制最大宽度480dp。
- 浅色背景使用深色系统栏图标。

## 行为与状态

1. 密码初始隐藏，通过眼睛按钮显示/隐藏，按钮提供中文无障碍描述。
2. 显隐仅为组件瞬时状态，不存入Bundle；页面进入后台、提交或重建后恢复隐藏。
3. 用户名键盘“下一项”进入密码；密码键盘“完成”提交并收起键盘。
4. 请求中按钮内显示进度及“正在登录/注册”，禁用重复提交、表单修改与切页；失败后恢复操作。
5. 保留原有切页规则：登录与注册不共享字段；离页清密码并取消本页请求。账号仅保留在原页面VM中。
6. 网络与业务失败经 `AuthUiEffect.ShowToast` 展示，不使用错误弹窗，不在页面中新增持久错误状态；取消不弹失败提示。
7. 原有成功流程保持：Native建立会话，认证Activity关闭，返回存活的MainActivity。

## 错误提示

| 场景 | Toast |
| --- | --- |
| 空账号/密码 | 账号或密码不能为空 |
| 登录业务400/401 | 账号或密码错误，请检查后重试 |
| 注册业务409 | 该用户名已注册，请更换用户名或直接登录 |
| SocketTimeoutException | 网络请求超时，请稍后重试 |
| 其他IOException | 网络连接失败，请检查网络后重试 |
| 其他业务/内部失败 | 登录失败，请稍后重试 / 注册失败，请稍后重试 |

Repository 将业务失败转换为受控的 `AuthRejectedException`；VM区分网络与其他异常。Toast 不直接显示服务端message或底层异常详情；诊断保留操作名、状态码、异常类名，不记录输入值。本次没有修改既有认证Mock/持久化方案，也没有为UI演示新建账号。

## 组件与代码

| 位置 | 职责 |
| --- | --- |
| [AuthFormContent.kt](../../android/biz_user/src/main/java/com/entry/biz/user/ui/AuthFormContent.kt) | 共用布局、外置标签、密码显隐、键盘、品牌区及按钮状态 |
| [LoginScreen.kt](../../android/biz_user/src/main/java/com/entry/biz/user/ui/LoginScreen.kt)、[RegisterScreen.kt](../../android/biz_user/src/main/java/com/entry/biz/user/ui/RegisterScreen.kt) | 各自收集VM状态，处理Toast/成功导航副作用 |
| [AuthActivity.kt](../../android/biz_user/src/main/java/com/entry/biz/user/ui/AuthActivity.kt) | Native认证宿主与浅色系统栏适配 |
| [UserViewModel.kt](../../android/biz_user/src/main/java/com/entry/biz/user/viewmodel/UserViewModel.kt) | 提交、防重、取消和安全错误提示映射 |
| [UserRepository.kt](../../android/biz_user/src/main/java/com/entry/biz/user/repository/UserRepository.kt) | 请求、业务失败分类、既有Native会话建立 |
| `android/biz_user/src/main/res/` | 中文文案、银行标志与显隐矢量图标 |

## 验证方式

在 `android/` 执行：

```sh
./gradlew :biz_user:testDebugUnitTest :app:assembleDebug :biz_user:lintDebug -PreactNativeArchitectures=arm64-v8a --max-workers=2
./gradlew :biz_user:testDebugUnitTest :biz_user:lintDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.entrytask.auth.AuthFormIsolationTest -PreactNativeArchitectures=arm64-v8a --max-workers=2
```

单元回归覆盖业务失败、网络/超时/内部失败、取消不提示、重复提交与表单隔离；设备回归覆盖切页字段隔离、密码显隐与重建后隐藏。设备测试只填写合成表单，不提交登录注册，不修改已有账号。设备测试按实际密码无障碍状态等待Compose更新，不能仅依赖Accessibility空闲就立即断言。

2026-10-22 本次验证结果：

- 首轮Debug构建、业务单测及Lint通过；最终代码复验构建/测试成功，设备用例2项通过。
- `AuthFormStateTest` 6项通过，另有现有基础单测1项通过，无失败/跳过。
- Lint：0 errors，11项现有依赖版本提示；未为此升级依赖。
- 模拟器：Pixel_10、Android17/API37、arm64-v8a。检查登录页实际布局与密码开关；设备自动测试覆盖登录和注册两种表单。
- 初次新增显隐用例在点击后立即读取到旧的无障碍密码状态而失败；手动检查确认按钮生效后，将用例修正为按状态有界等待，最终两项用例通过，没有跳过失败用例。
- 业务失败/网络失败的提示由Fake服务单元测试验证，未通过提交真实表单或修改既有账号验证；本次没有进行断网端到端专项测试。
- 未验证iOS、真机及大字体/横屏专项；外部附件不可读，不能声称与其逐像素一致。

本次安装最终Debug APK后保存的空表单截图：[登录](verification/auth-login.png)、[注册](verification/auth-register.png)。截图不包含输入账号或密码。
