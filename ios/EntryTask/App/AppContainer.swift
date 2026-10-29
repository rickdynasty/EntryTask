// 文件职责：组合根，统一组装会话、认证、网络、存储和两种页面模式的依赖。
import Foundation
import CoreKit
import UserFeature
import NetworkKit
import StorageKit
import DemoEngine
import DashboardFeature

/// 组合根：App与六个Swift库在此接线，不通过UIApplication或全局变量定位依赖。
@MainActor final class AppContainer {
    /// Native会话权威的引用，身份不由页面自行指定。
    let session = SessionStore()
    /// 会话与写事务共用的串行提交协调器。
    let commits = CommitExecutor()
    /// 安装包资源提供者，运行时不访问开发机文件。
    let resources: DirectoryResources
    /// 账号和演示账本的数据库门面，首次访问才打开库。
    let database: LocalDatabase
    /// 只接受受控偏好键的存储服务。
    let preferences: TypedPreferences
    /// 通过契约注入的网络能力，不让View直接访问传输层。
    let network: NetworkClient
    /// 复用同一网络客户端的认证仓储。
    let auth: NetworkAuthRepository
    /// 唯一根导航与认证协调状态源。
    let shell: ShellViewModel
    /// 本次进程的诊断编号，不是用户身份或凭证。
    let bootID = UUID().uuidString
    /// 当前宿主的弱引用，用于受控呈现认证界面。
    weak var host: ShellViewController?
    /// 按需创建的Bridge门面，Native页面模式不需触发它。
    lazy var bridge = ETBridgeServices(container: self)
    /// 按需创建的唯一RN运行时所有者，切Tab不会重新创建。
    // 仅RN内容分支访问该lazy属性；Native模式不会因此创建factory。
    lazy var runtime = RNRuntimeOwner(services: bridge)
    /// Native四页协调器，复用同一会话及网络服务。
    lazy var dashboard = DashboardCoordinator(repositories: NetworkDashboardRepository(network: network, context: { [session] in session.requestContext() }))
    /// 组装共享服务并选择Debug测试隔离目录；此处不主动打开数据库或创建RN引擎。
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
        // 先构造存储门面与适配器，再将同一Mock处理器接入网络；此时不打开数据库。
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
