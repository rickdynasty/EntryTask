import UIKit

/// UIKit系统入口只转交窗口工厂；业务模块不通过UIApplication定位服务。
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene, let app = UIApplication.shared.delegate as? AppDelegate else { return }
        window = app.makeWindow(for: scene)
        window?.makeKeyAndVisible()
    }
}
