// 文件职责：将系统Scene连接到AppDelegate创建的窗口。
import UIKit

/// UIKit系统入口只转交窗口工厂；业务模块不通过UIApplication定位服务。
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    /// 当前Scene的窗口引用，避免重复创建根宿主。
    var window: UIWindow?
    /// 验证系统Scene类型，连接窗口并使其成为可见主窗口。
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene, let app = UIApplication.shared.delegate as? AppDelegate else { return }
        window = app.makeWindow(for: scene)
        window?.makeKeyAndVisible()
    }
}
