// 文件职责：进程启动入口，持有组合根并为单窗口创建稳定宿主。
import UIKit
import CoreKit

/// 进程组合根只创建一次；窗口由单Scene入口连接，兼容当前iOS SDK生命周期要求。
@main class AppDelegate: UIResponder, UIApplicationDelegate {
    /// 当前Scene的窗口引用，避免重复创建根宿主。
    var window: UIWindow?
    /// 进程组合根；Bridge中使用弱引用避免反向延长应用图生命周期。
    private var container: AppContainer?
    /// 创建进程组合根并记录启动阶段。
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        AppLogger.log("application.starting")
        container = AppContainer()
        return true
    }
    /// 为系统Scene指定SceneDelegate，不创建第二套应用图。
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "EntryTask", sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
    /// 复用当前Scene窗口；首次创建稳定Shell并固定浅色外观。
    func makeWindow(for scene: UIWindowScene) -> UIWindow {
        if let window, window.windowScene === scene { return window }
        guard let graph = container else { preconditionFailure("Application graph must be created before its scene") }
        let window = UIWindow(windowScene: scene)
        window.overrideUserInterfaceStyle = .light
        window.rootViewController = ShellViewController(graph: graph)
        self.window = window
        return window
    }
}
