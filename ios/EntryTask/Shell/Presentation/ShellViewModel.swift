import Foundation
import Combine
import CoreKit
import UserFeature

struct ShellSnapshot: Equatable {
    var tabId = 1
    var depth = 0
    var authenticated = false
    var sessionEpoch = 0
    var revision = 0
    var json: JSONObject { ["tabId": tabId, "depth": depth, "authenticated": authenticated, "sessionEpoch": sessionEpoch, "revision": revision] }
}
/// Shell唯一状态源；UI/Bridge共享同一实例，根Tab和会话不各存一份。
@MainActor final class ShellViewModel: ObservableObject {
    @Published private(set) var snapshot = ShellSnapshot()
    @Published private(set) var authFlow: UUID?
    private(set) var mayMountGate = false
    private let session: SessionPort
    private let commits: CommitExecutor
    var changed: ((ShellSnapshot) -> Void)?
    init(session: SessionPort, commits: CommitExecutor) { self.session = session; self.commits = commits; refresh() }
    func refresh(force: Bool = false) {
        let current = session.snapshot(); var next = snapshot
        if current.epoch != next.sessionEpoch { next.depth = 0; if !current.authenticated { next.tabId = 1 } }
        next.authenticated = current.authenticated; next.sessionEpoch = current.epoch
        if next != snapshot || force { next.revision = snapshot.revision + 1; snapshot = next; changed?(next) }
    }
    func requestAuthentication() {
        refresh()
        guard !snapshot.authenticated else { refresh(force: true); return }
        guard authFlow == nil else { return }
        authFlow = UUID(); AppLogger.log("auth.presentation.requested")
    }
    func authenticationReturned(_ flow: UUID, success: Bool) {
        guard authFlow == flow else { return }
        authFlow = nil; mayMountGate = true; refresh(force: true)
        AppLogger.log(success ? "auth.presentation.completed" : "auth.presentation.canceled")
    }
    func selectTab(_ tab: Int) {
        guard (1...4).contains(tab) else { return }
        if snapshot.tabId == tab { return }
        var next = snapshot; next.tabId = tab; next.depth = 0; publish(next)
    }
    func navigation(_ depth: Int) {
        guard (0...20).contains(depth), snapshot.depth != depth else { return }
        var next = snapshot; next.depth = depth; publish(next)
    }
    func logout() async throws {
        try await commits.perform { self.session.logout() }
        refresh(force: true)
    }
    private func publish(_ value: ShellSnapshot) { var value = value; value.revision = snapshot.revision + 1; snapshot = value; changed?(value) }
}
