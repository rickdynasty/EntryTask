// 文件职责：供Native UI和RN共享的根导航快照，不包含凭证。
import Foundation
import Combine
import CoreKit
import UserFeature

/// 供Native UI和RN共享的根导航快照，不包含凭证。
struct ShellSnapshot: Equatable {
    /// 根Tab编号，1至4对应首页、财富、会员、我的。
    var tabId = 1
    /// 业务子页深度，非零时宿主隐藏根底栏。
    var depth = 0
    /// Native会话认证标志，不等同某次Bridge命令ACK。
    var authenticated = false
    /// 会话代次，帮助消费方丢弃旧用户结果。
    var sessionEpoch = 0
    /// 快照递增版本，用于导航事件排序。
    var revision = 0
    /// 不含凭证的传输快照，供Bridge发布。
    var json: JSONObject { ["tabId": tabId, "depth": depth, "authenticated": authenticated, "sessionEpoch": sessionEpoch, "revision": revision] }
}
/// Shell唯一状态源；UI/Bridge共享同一实例，根Tab和会话不各存一份。
@MainActor final class ShellViewModel: ObservableObject {
    /// 当前可观察状态快照，仅由状态所有者更新。
    @Published private(set) var snapshot = ShellSnapshot()
    /// 当前认证请求编号，存在时合并重入请求。
    @Published private(set) var authFlow: UUID?
    /// 认证返回后允许挂载页面门禁，挂载不代表已认证。
    private(set) var mayMountGate = false
    /// Native会话权威的引用，身份不由页面自行指定。
    private let session: SessionPort
    /// 会话与写事务共用的串行提交协调器。
    private let commits: CommitExecutor
    /// 向Bridge通知快照变化的回调，不复制状态权威。
    var changed: ((ShellSnapshot) -> Void)?
    /// 注入会话和提交器，并立即生成初始导航快照。
    init(session: SessionPort, commits: CommitExecutor) { self.session = session; self.commits = commits; refresh() }
    /// 从Native会话刷新导航快照；强制发布用于Bridge重同步。
    func refresh(force: Bool = false) {
        let current = session.snapshot(); var next = snapshot
        // 会话变化时丢弃旧子栈；退出回首页，防止新用户继承旧导航深度。
        if current.epoch != next.sessionEpoch { next.depth = 0; if !current.authenticated { next.tabId = 1 } }
        next.authenticated = current.authenticated; next.sessionEpoch = current.epoch
        if next != snapshot || force { next.revision = snapshot.revision + 1; snapshot = next; changed?(next) }
    }
    /// 请求认证前刷新事实；已认证或已有流程时不重复发起。
    func requestAuthentication() {
        refresh()
        guard !snapshot.authenticated else { refresh(force: true); return }
        guard authFlow == nil else { return }
        // 认证请求只登记一个flow，UI可在宿主可见时再呈现。
        authFlow = UUID(); AppLogger.log("auth.presentation.requested")
    }
    /// 只接收当前flow的返回，更新快照并允许页面门禁继续协调。
    func authenticationReturned(_ flow: UUID, success: Bool) {
        guard authFlow == flow else { return }
        authFlow = nil; mayMountGate = true; refresh(force: true)
        AppLogger.log(success ? "auth.presentation.completed" : "auth.presentation.canceled")
    }
    /// 校验根Tab范围，切换时清零子页深度并发布新快照。
    func selectTab(_ tab: Int) {
        guard (1...4).contains(tab) else { return }
        if snapshot.tabId == tab { return }
        var next = snapshot; next.tabId = tab; next.depth = 0; publish(next)
    }
    /// 接收有界子页深度，重复值不发布事件。
    func navigation(_ depth: Int) {
        guard (0...20).contains(depth), snapshot.depth != depth else { return }
        var next = snapshot; next.depth = depth; publish(next)
    }
    /// 串行清除Native会话并通知UI，旧请求不能跨epoch回写。
    func logout() async throws {
        try await commits.perform { self.session.logout() }
        refresh(force: true)
    }
    /// 递增导航版本，同时通知Native观察者和Bridge监听方。
    private func publish(_ value: ShellSnapshot) { var value = value; value.revision = snapshot.revision + 1; snapshot = value; changed?(value) }
}
