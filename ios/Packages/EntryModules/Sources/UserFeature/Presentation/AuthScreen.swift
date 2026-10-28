#if canImport(UIKit)
import SwiftUI
import UIKit
import CoreKit

/// 导航容器只绑定生命周期/成功状态；两张表单分别持有VM，不共享输入。
public struct AuthScreen: View {
    @StateObject private var login: AuthViewModel
    @StateObject private var register: AuthViewModel
    @State private var registration = false
    @State private var delivered = false
    private let onAuthSuccess: () -> Void
    public init(repository: AuthRepository, onAuthSuccess: @escaping () -> Void) {
        _login = StateObject(wrappedValue: AuthViewModel(repository: repository, registration: false))
        _register = StateObject(wrappedValue: AuthViewModel(repository: repository, registration: true))
        self.onAuthSuccess = onAuthSuccess
    }
    public var body: some View {
        let model = registration ? register : login
        return AuthFormContent(state: model.state,
            onUsername: model.username, onPassword: model.password, onSubmit: model.submit,
            onMessageShown: model.consumeMessage, onLeave: model.leave, registration: registration, switchForm: {
            (registration ? register : login).leave(); registration.toggle()
        }).onChange(of: login.state.authenticated) { _ in deliver() }.onChange(of: register.state.authenticated) { _ in deliver() }
          .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in deliver() }
          .onDisappear { login.leave(); register.leave() }
    }
    private func deliver() {
        guard UIApplication.shared.applicationState == .active, !delivered, (registration ? register : login).state.authenticated else { return }
        delivered = true; onAuthSuccess()
    }
}

/// 复用认证布局，输入标签在框外，显隐为局部UI状态；预览不依赖网络或数据库。
private struct AuthFormContent: View {
    let state: AuthUiState
    let onUsername: (String) -> Void
    let onPassword: (String) -> Void
    let onSubmit: () -> Void
    let onMessageShown: () -> Void
    let onLeave: () -> Void
    let registration: Bool
    let switchForm: () -> Void
    @State private var visible = false
    @State private var toast: String?
    @State private var toastTask: Task<Void, Never>?
    @FocusState private var focus: Int?
    private var disabled: Bool { state.isLoading || state.authenticated }
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
         .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in visible = false }
         .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in presentMessage() }
         .onDisappear { visible = false; toastTask?.cancel(); onLeave() }
    }
    private func submit() { visible = false; focus = nil; onSubmit() }
    private func presentMessage() {
        guard UIApplication.shared.applicationState == .active, let message = state.message else { return }
        toast = message; onMessageShown(); toastTask?.cancel()
        toastTask = Task { try? await Task.sleep(nanoseconds: 2500000000); if !Task.isCancelled { toast = nil } }
    }
}
#endif
