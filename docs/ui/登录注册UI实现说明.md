# 登录注册 UI 设计说明

## 页面与视觉

Android 认证使用 Native Compose + MVVM。AuthNavHost 的登录和注册条目各有独立 UserViewModel，通过同一 AuthRoute 绑定 AuthFormContent；RN 不实现第二套认证表单，iOS 对等界面待实施。

页面使用中信银行中文/英文名称、工程现有几何标志、暖白背景和红色主操作。标志来自项目矢量素材，不作为官方完整品牌文件交付。

| 区域 | 设计与复用 |
| --- | --- |
| 品牌区 | 标志、银行名称、欢迎语；两页共用 |
| 标题区 | 区分账号登录与注册新账号 |
| 输入区 | 标签固定在框上方，框内提示用途，白底浅描边、8dp 圆角、品牌红聚焦态 |
| 密码区 | 默认隐藏、眼睛按钮切换、中文可访问描述；至少 48dp 点击区 |
| 主按钮 | 至少 52dp 高，加载进度及操作文案；加载/认证成功后禁用 |
| 页面切换 | 登录/注册独立表单，切页取消请求并清密码 |
| 页面容器 | 可滚动、安全区、键盘避让，宽屏最大 480dp，深色系统栏图标 |

密码显隐是局部呈现状态，不进入 Bundle；后台、提交或重建后隐藏。键盘“下一项”进入密码，“完成”提交并收起键盘。

## 结果与返回

错误由 VM 生成安全 message，AuthRoute 在 RESUMED 显示 Toast，随后发送 MessageShown 确认消费；取消不提示失败。成功保存在 AuthUiState.authenticated，由 onAuthSuccess 通知宿主，不通过一次性 Channel 承载成功事实。

| 场景 | 提示/行为 |
| --- | --- |
| 空账号或密码 | 提示不能为空 |
| 登录凭据错误 | 提示检查账号密码后重试 |
| 重复注册 | 提示更换账号或直接登录 |
| 超时、IO、其他失败 | 按类别展示安全提示，不显示原始响应或异常详情 |
| 注册返回 | 回登录并取消、清理注册表单 |
| 登录根页返回 | 先收键盘，再以两秒双击退出整个任务，不能露出未认证首页 |
| 认证成功 | Native 建立会话，AuthActivity 返回结果并关闭自身，MainActivity 重读快照 |

## Preview、源码与验证

[AuthFormContent](../../android/biz_user/src/main/java/com/entry/biz/user/ui/AuthFormContent.kt) 接收状态及动作，不获取 VM、不发请求。通过 @Preview 和 PreviewParameter 检查登录/注册、空表单、加载等状态；Preview 不依赖网络、Room 或 Native 登录。

Android Studio Preview 的构建、刷新与 Out of date 排查见[Compose Preview 指引](../../android/docs/compose_preview使用指引.md)。预览成功不替代设备键盘、生命周期和系统返回测试。

- [AuthRoute](../../android/biz_user/src/main/java/com/entry/biz/user/ui/AuthRoute.kt)：生命周期绑定
- [AuthNavHost](../../android/biz_user/src/main/java/com/entry/biz/user/ui/navigation/AuthNavHost.kt)：独立条目及统一成功回调
- [登录注册方案](../design/登录注册技术方案.md)：状态、数据流与取消
- [Android README](../../android/README.md)：测试命令与账号数据保护

设备验证覆盖切页字段隔离、显隐与重建、失败 Toast、加载防重、后台成功和双击退出；视觉对照检查窄屏、大字体和键盘遮挡，不将某次截图或构建结果当作通用验收。
