import UIKit
import CoreKit

/// 进程组合根只创建一次；窗口由单Scene入口连接，兼容当前iOS SDK生命周期要求。
@main class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private var container: AppContainer?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        AppLogger.log("application.starting")
        container = AppContainer()
        return true
    }
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "EntryTask", sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
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
