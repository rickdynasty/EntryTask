# Jetpack Compose `@Preview` 视觉预览全景指南

本指南结合 `AuthFormContent` 登录/注册表单实战案例，由浅入深讲解 Jetpack Compose 中 `@Preview` 预览机制的初级、进阶与高级用法，帮助你高效完成 UI 开发与多状态测试。

---

## 一、 初级用法：基础单图预览

### 1.1 核心概念
在 Compose 中，由于没有 XML 布局文件，所有的 UI 都是通过纯 Kotlin 代码编写。`@Preview` 注解允许你在 Android Studio 的 **Split** 或 **Design** 视图中实时查看 `@Composable` 函数的绘制效果，无需运行应用。

### 1.2 编写规范
* **无参限制**：`@Preview` 修饰的函数必须没有必填参数（或者参数有默认值）。
* **可见性**：建议添加 `private` 修饰符，避免预览代码污染生产调用的命名空间。
* **摆放位置**：通常直接写在对应 Composable UI 文件的最底部。

### 1.3 实战源码：静态单图预览

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.entry.biz.user.ui.contract.AuthUiState

/**
 * 初级：基础单图预览
 * 直接构造模拟数据 (Mock Data)，预览默认登录页效果。
 */
@Preview(
    name = "1. 默认登录页预览",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun AuthFormContentDefaultPreview() {
    val mockState = AuthUiState(
        username = "",
        password = "",
        isLoading = false
    )
    
    AuthFormContent(
        state = mockState,
        isRegistration = false,
        onIntent = {},
        onSwitchForm = {}
    )
}
```

---

## 二、 进阶用法：多状态与设备环境配置

### 2.1 核心概念
进阶用法主要解决两个痛点：
1. **设备与环境适配**：检查 UI 在深色模式、大屏/平板、刘海屏沉浸式等环境下的表现。
2. **多状态对比**：同屏查看空状态、填写状态、加载中状态等多种业务变体。

### 2.2 实战源码：环境配置与多状态同屏预览

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.entry.biz.user.ui.contract.AuthUiState

/**
 * 进阶 1：系统 UI 预览（深色环境需额外设置 uiMode）
 */
@Preview(
    name = "2. 系统栏预览",
    showBackground = true,
    showSystemUi = true, // 显示状态栏和导航栏，检查 WindowInsets/imePadding
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun AuthFormContentDarkPreview() {
    AuthFormContent(
        state = AuthUiState(username = "bank_user", password = "••••••"),
        isRegistration = false,
        onIntent = {},
        onSwitchForm = {}
    )
}

/**
 * 进阶 2：大屏/平板设备适配预览
 */
@Preview(
    name = "3. 平板设备居中适配",
    showBackground = true,
    widthDp = 800,
    heightDp = 1280
)
@Composable
private fun AuthFormContentTabletPreview() {
    AuthFormContent(
        state = AuthUiState(username = "tablet_user"),
        isRegistration = false,
        onIntent = {},
        onSwitchForm = {}
    )
}

/**
 * 进阶 3：多业务状态对比预览 (加载中状态)
 */
@Preview(name = "4. 提交加载中状态", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AuthFormContentLoadingPreview() {
    AuthFormContent(
        state = AuthUiState(
            username = "admin_user",
            password = "password123",
            isLoading = true // 检查 Loading Indicator 显隐及按钮禁用样式
        ),
        isRegistration = false,
        onIntent = {},
        onSwitchForm = {}
    )
}
```

---

## 三、 高级用法：组合注解与 `@PreviewParameter` 数据驱动

### 3.1 核心概念
* **自定义多重预览注解（Multi-Preview Annotations）**：将通用的设备配置（如：浅色/深色/大字体）打包成一个自定义注解，实现跨组件一键复用。
* **`PreviewParameterProvider`（数据驱动预览）**：通过实现 Provider 接口，为单个预览窗口批量注入数据流，避免重复编写多行 `@Preview` 函数，解决代码冗余问题。

### 3.2 实战源码：高级组合与参数化注入

```kotlin
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.entry.biz.user.ui.contract.AuthUiState

// ==================== 1. 自定义多重预览组合注解 ====================

@Preview(
    name = "浅色模式",
    group = "主题测试",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "深色模式",
    group = "主题测试",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class ThemePreviews

// ==================== 2. 定义数据打包模型与 Provider ====================

/** 预览测试数据卡片 */
data class AuthFormPreviewData(
    val title: String,
    val state: AuthUiState,
    val isRegistration: Boolean
)

/** 状态提供器：一次性输出该表单所有边界用例 */
class AuthFormProvider : PreviewParameterProvider<AuthFormPreviewData> {
    override val values: Sequence<AuthFormPreviewData> = sequenceOf(
        AuthFormPreviewData(
            title = "登录-初始空状态",
            state = AuthUiState(),
            isRegistration = false
        ),
        AuthFormPreviewData(
            title = "登录-已输入状态",
            state = AuthUiState(username = "john_doe", password = "my_password"),
            isRegistration = false
        ),
        AuthFormPreviewData(
            title = "登录-正在提交中",
            state = AuthUiState(username = "john_doe", password = "my_password", isLoading = true),
            isRegistration = false
        ),
        AuthFormPreviewData(
            title = "注册-初始状态",
            state = AuthUiState(),
            isRegistration = true
        )
    )
}

// ==================== 3. 参数化批量注入预览 ====================

@ThemePreviews
@Composable
private fun AuthFormContentMultiStateBatchPreview(
    @PreviewParameter(AuthFormProvider::class) previewData: AuthFormPreviewData
) {
    AuthFormContent(
        state = previewData.state,
        isRegistration = previewData.isRegistration,
        onIntent = {},
        onSwitchForm = {}
    )
}
```

---

## 四、 Common Render Problem 排查清单

当你在 Android Studio 右侧预览区域看到 **`Render problem`** 报错时，请按照以下步骤快速排查：

1. **缺失依赖与导包**：
   * 确保已导入 `PreviewParameter`、`PreviewParameterProvider` 及对应数据模型包。
2. **未进行资源编译同步 (Rebuild Project)**：
   * Compose 预览需要借助编译产生的 R 文件。若含有新的图片资源 (`ic_auth_bank`) 或字符串 (`R.string.auth_login_title`)，请先点击菜单栏 **Build -> Rebuild Project**。
3. **`PreviewParameterProvider` 数据类型不匹配**：
   * 确保 `@PreviewParameter(XxxProvider::class)` 括号中的类型与被调用的参数类型完全匹配。
4. **代码运行时异常**：
   * 若 Composable 内部尝试直接获取未初始化的 ViewModel 或进行复杂的网络/数据库初始化，会导致预览渲染失败。请保持纯 UI 组件（如 `AuthFormContent`）的无状态（Stateless）特性。

## 五、当前工程的进阶预览与刷新

源码入口：[AuthFormContent.kt](../biz_user/src/main/java/com/entry/biz/user/ui/AuthFormContent.kt)。前面的代码用于解释写法，当前实现以源码为准，无需重复粘贴。

- **认证 · 状态**：组合注解 `AuthFormThemePreviews` × `AuthFormProvider`，生成登录/注册的初始、已输入、提交中状态，在浅色/深色系统环境各预览一次，共12项。
- **认证 · 适配**：`AuthFormDevicePreviews` 分别用于登录和注册，覆盖手机系统栏、800×1280dp平板、320×640dp小屏及1.5倍字体，共6项。按预览组筛选，减少同时渲染的开销。
- Provider只提供合成数据，回调为空，不创建ViewModel、不发请求、不读写账号。显隐按钮是组件内部状态，可在Interactive预览中查看；提交和切页不会执行业务。
- `AuthFormContent` 内部显式使用 `lightColorScheme`，因此深色系统环境仍显示浅色表单。这是检查环境兼容性，不代表已实现深色主题。`backgroundColor` 只改变预览背景，不能启用深色主题；系统主题参数为 `uiMode`。
- `showSystemUi` 用于辅助检查系统栏和边距；IME、Activity系统栏图标配置及真实生命周期仍需在设备验证。

### “Out of date”是什么意思

截图的英文是 **Out of date**（预览可能过期），不是“数据用完”。它表示IDE当前显示的预览可能没有反映最新源码或构建结果；仅凭该提示不能判断存在运行时错误，也不能靠增加`@Preview`注解消除。

按顺序处理：

1. 保存文件；若修改过Gradle依赖，先完成Gradle同步，并选择Debug构建变体。
2. 点击预览工具栏右侧的绿色循环箭头，刷新/构建预览；等待构建和渲染结束。IDE版本不同，动作名称可能为Refresh或Build & Refresh。
3. 若仍提示过期，查看Build窗口及预览的错误详情，先处理首个编译或渲染异常。可在仓库`android/`目录执行 `./gradlew :biz_user:assembleDebug` 验证模块编译，再回IDE刷新。
4. 本工程已有`ui-tooling-preview`与Debug专用`ui-tooling`依赖。不要重复添加版本，也不要因为过期提示升级Compose、清应用数据或修改认证流程。

命令行构建成功只证明代码可编译，不会主动刷新Android Studio的预览面板。IDE实际渲染成功需要在Split/Design中确认。
