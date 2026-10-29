// 文件职责：登录注册导航容器，分别持有两个VM并协调前台成功回调。
#if canImport(UIKit)
import SwiftUI
import UIKit
import CoreKit

/// 导航容器只绑定生命周期/成功状态；两张表单分别持有VM，不共享输入。
public struct AuthScreen: View {
    /// 登录页独立VM，与注册输入隔离。
    @StateObject private var login: AuthViewModel
    /// 注册页独立VM，与登录输入隔离。
    @StateObject private var register: AuthViewModel
    /// 当前是否为注册表单；登录与注册分别持有状态。
    @State private var registration = false
    /// 成功事件已交付标记，防止前台恢复重复关闭认证。
    @State private var delivered = false
    /// 登录或注册成功的统一回调，由宿主处理导航。
    private let onAuthSuccess: () -> Void
    /// 分别创建登录与注册StateObject，避免切页复用另一张表单输入。
    public init(repository: AuthRepository, onAuthSuccess: @escaping () -> Void) {
        _login = StateObject(wrappedValue: AuthViewModel(repository: repository, registration: false))
        _register = StateObject(wrappedValue: AuthViewModel(repository: repository, registration: true))
        self.onAuthSuccess = onAuthSuccess
    }
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    public var body: some View {
        let model = registration ? register : login
        return AuthFormContent(state: model.state,
            onUsername: model.username, onPassword: model.password, onSubmit: model.submit,
            onMessageShown: model.consumeMessage, onLeave: model.leave, registration: registration, switchForm: {
            (registration ? register : login).leave(); registration.toggle()
        // 成功可能发生在后台，回到前台后再交付一次宿主导航动作。
        }).onChange(of: login.state.authenticated) { _ in deliver() }.onChange(of: register.state.authenticated) { _ in deliver() }
          .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in deliver() }
          .onDisappear { login.leave(); register.leave() }
    }
    /// 只在前台且当前表单已认证时交付一次成功回调，避免后台呈现UI。
    private func deliver() {
        guard UIApplication.shared.applicationState == .active, !delivered, (registration ? register : login).state.authenticated else { return }
        delivered = true; onAuthSuccess()
    }
}

/// 复用认证布局，输入标签在框外，显隐为局部UI状态；预览不依赖网络或数据库。
private struct AuthFormContent: View {
    /// 当前可观察UI状态或模拟账本，不允许跨会话迟到写回。
    let state: AuthUiState
    /// 输入变化交给VM，不在View中直接修改业务状态。
    let onUsername: (String) -> Void
    /// 密码变化回调，只流经内存状态。
    let onPassword: (String) -> Void
    /// 表单提交动作，由VM校验和防重。
    let onSubmit: () -> Void
    /// 提示已展示的消费回调，避免重复弹出。
    let onMessageShown: () -> Void
    /// 离页清理动作，取消请求并清除密码。
    let onLeave: () -> Void
    /// 当前是否为注册表单；登录与注册分别持有状态。
    let registration: Bool
    /// 切换认证表单的宿主动作。
    let switchForm: () -> Void
    /// 当前显示敏感值或密码的局部/会话内状态。
    @State private var visible = false
    /// 当前浮层提示，展示后自动消失。
    @State private var toast: String?
    /// 提示自动关闭任务，新提示或离页时取消旧任务。
    @State private var toastTask: Task<Void, Never>?
    /// 当前输入焦点，账号为0、密码为1。
    @FocusState private var focus: Int?
    /// 提交中或已认证时禁止继续编辑与重复操作。
    private var disabled: Bool { state.isLoading || state.authenticated }
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                BankBrandView().padding(.top, 44).padding(.bottom, 36)
                VStack(alignment: .leading, spacing: 10) {
                    Text(registration ? "注册新账号" : "账号登录").font(.system(size: 30, weight: .semibold))
                    Text(registration ? "欢迎加入，让财富有温度" : "欢迎使用中信银行").foregroundColor(.secondary)
                }
                VStack(alignment: .leading, spacing: 9) {
                    Text("用户名").font(.subheadline)
                    TextField("请输入用户名", text: Binding(get: { state.username }, set: onUsername))
                        .textInputAutocapitalization(.never).autocorrectionDisabled().textContentType(.username).focused($focus, equals: 0)
                        .submitLabel(.next).onSubmit { focus = 1 }.padding(16).background(Color.white).cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(focus == 0 ? Color.red : Color.gray.opacity(0.25)))
                        .accessibilityIdentifier("auth.username")
                }
                VStack(alignment: .leading, spacing: 9) {
                    Text("密码").font(.subheadline)
                    HStack {
                        Group {
                            if visible { TextField("请输入密码", text: Binding(get: { state.password }, set: onPassword)) }
                            else { SecureField("请输入密码", text: Binding(get: { state.password }, set: onPassword)) }
                        }.focused($focus, equals: 1).submitLabel(.done).onSubmit { submit() }.accessibilityIdentifier("auth.password")
                        Button { visible.toggle() } label: { Image(systemName: visible ? "eye.slash" : "eye").frame(width: 48, height: 48) }
                            .accessibilityLabel(visible ? "隐藏密码" : "显示密码")
                    }.padding(.leading, 16).background(Color.white).cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(focus == 1 ? Color.red : Color.gray.opacity(0.25)))
                }
                Button(action: submit) {
                    HStack { if state.isLoading { ProgressView().tint(.white) }; Text(state.isLoading ? "正在提交" : registration ? "注册并登录" : "登录").fontWeight(.semibold) }
                        .frame(maxWidth: .infinity, minHeight: 52).background(Color(red: 0.77, green: 0.08, blue: 0.16)).foregroundColor(.white).cornerRadius(8)
                }.accessibilityIdentifier("auth.submit")
                Button(registration ? "已有账号，返回登录" : "没有账号？立即注册") { focus = nil; visible = false; switchForm() }
                    .frame(maxWidth: .infinity, minHeight: 48).foregroundColor(.red).accessibilityIdentifier("auth.switch")
                Spacer(minLength: 36)
            }.disabled(disabled).padding(24).frame(maxWidth: 480).frame(maxWidth: .infinity)
        }.background(Color(red: 0.99, green: 0.97, blue: 0.94).ignoresSafeArea())
         .overlay(alignment: .bottom) { if let toast { Text(toast).foregroundColor(.white).padding(14).background(.black.opacity(0.8)).cornerRadius(8).padding(24).accessibilityIdentifier("auth.toast") } }
         .onChange(of: state.message) { _ in presentMessage() }
         // 退到后台立即恢复密码隐藏；离页时同时取消Toast任务并通知VM清理。
         .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in visible = false }
         .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in presentMessage() }
         .onDisappear { visible = false; toastTask?.cancel(); onLeave() }
    }
    /// 提交前隐藏密码、收起键盘，再把动作交给VM。
    private func submit() { visible = false; focus = nil; onSubmit() }
    /// 仅前台展示待消费提示，新提示替换旧关闭任务。
    private func presentMessage() {
        guard UIApplication.shared.applicationState == .active, let message = state.message else { return }
        toast = message; onMessageShown(); toastTask?.cancel()
        toastTask = Task { try? await Task.sleep(nanoseconds: 2500000000); if !Task.isCancelled { toast = nil } }
    }
}
#endif
