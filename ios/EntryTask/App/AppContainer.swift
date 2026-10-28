import Foundation
import CoreKit
import UserFeature
import NetworkKit
import StorageKit
import DemoEngine
import DashboardFeature

/// 组合根：App与六个Swift库在此接线，不通过UIApplication或全局变量定位依赖。
@MainActor final class AppContainer {
    let session = SessionStore()
    let commits = CommitExecutor()
    let resources: DirectoryResources
    let database: LocalDatabase
    let preferences: TypedPreferences
    let network: NetworkClient
    let auth: NetworkAuthRepository
    let shell: ShellViewModel
    let bootID = UUID().uuidString
    weak var host: ShellViewController?
    lazy var bridge = ETBridgeServices(container: self)
    lazy var runtime = RNRuntimeOwner(services: bridge)
    lazy var dashboard = DashboardCoordinator(repositories: NetworkDashboardRepository(network: network, context: { [session] in session.requestContext() }))
    init() {
        resources = DirectoryResources(root: Bundle.main.resourceURL!)
        var root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("EntryTask", isDirectory: true)
        #if DEBUG
        // 仅Debug测试入口可隔离存储，不能由普通用户/Release更改正式账号库。
        let testID = ProcessInfo.processInfo.environment["ENTRYTASK_TEST_ID"] ?? ""
        if ProcessInfo.processInfo.arguments.contains("--entrytask-ui-test"), testID.range(of: "^[A-Za-z0-9-]{1,60}$", options: .regularExpression) != nil {
            root = FileManager.default.temporaryDirectory.appendingPathComponent("EntryTaskUITests-" + testID)
        }
        #endif
        database = LocalDatabase(directory: root.appendingPathComponent("Database"))
        preferences = TypedPreferences(directory: root.appendingPathComponent("Preferences"))
        let state = DemoStateAdapter(database: database, session: session, commits: commits)
        let accounts = MockAccountStore(storage: AccountStorageAdapter(database))
        let handler = DemoHandler(resources: resources, accounts: accounts, state: state)
        network = NetworkClient(resources: resources, handler: handler)
        auth = NetworkAuthRepository(network: network, session: session, commits: commits)
        shell = ShellViewModel(session: session, commits: commits)
        AppLogger.log("application.initialized")
    }
}
