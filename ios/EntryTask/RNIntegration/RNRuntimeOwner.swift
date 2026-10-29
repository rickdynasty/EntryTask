// 文件职责：进程内唯一RN工厂与根视图的所有者，按需创建并复用。
import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import CoreKit

/// 进程唯一factory与根视图，认证和Tab不另起运行时；首期不预热。
@MainActor final class RNRuntimeOwner {
    /// 保留工厂委托，为依赖注入与Bundle地址提供入口。
    private let delegate: ReactNativeDelegate
    /// 进程唯一React Native工厂，不按Tab创建。
    private let factory: RCTReactNativeFactory
    /// 已创建的唯一业务根视图，后续挂载复用该实例。
    private var root: UIView?
    /// 注入唯一工厂依赖或已有根视图，保持一个业务Surface。
    init(services: ETBridgeServices) {
        delegate = ReactNativeDelegate(services: services)
        delegate.dependencyProvider = RCTAppDependencyProvider()
        factory = RCTReactNativeFactory(delegate: delegate)
    }
    /// 缓存并复用唯一RN根视图，不因切Tab或认证创建新Surface。
    func rootView() -> UIView {
        if let root { return root }
        let view = factory.rootViewFactory.view(withModuleName: "EntryTask", initialProperties: [:], launchOptions: nil)
        root = view; AppLogger.log("runtime.surface.created"); return view
    }
}
/// 为RN工厂提供Codegen依赖和开发/生产Bundle地址。
final class ReactNativeDelegate: ETFactoryDelegate {
    /// 把RN查询Bundle地址转给统一模式选择方法。
    override func sourceURL(for bridge: RCTBridge) -> URL? { bundleURL() }
    /// Debug使用src/index的Metro地址，Release使用包内main.jsbundle。
    override func bundleURL() -> URL? {
        #if DEBUG
        RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "src/index")
        #else
        Bundle.main.url(forResource: "main", withExtension: "jsbundle")
        #endif
    }
}
/// 通过UIKit子控制器生命周期承载已有RN根视图。
final class RNContainerViewController: UIViewController {
    /// 已创建的唯一业务根视图，后续挂载复用该实例。
    private let root: UIView
    /// 注入唯一工厂依赖或已有根视图，保持一个业务Surface。
    init(root: UIView) { self.root = root; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    /// 使用已创建的RN根视图作为容器内容，避免再次启动引擎。
    override func loadView() { view = root }
}
