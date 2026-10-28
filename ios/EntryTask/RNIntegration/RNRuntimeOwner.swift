import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import CoreKit

/// 进程唯一factory与根视图，认证和Tab不另起运行时；首期不预热。
@MainActor final class RNRuntimeOwner {
    private let delegate: ReactNativeDelegate
    private let factory: RCTReactNativeFactory
    private var root: UIView?
    init(services: ETBridgeServices) {
        delegate = ReactNativeDelegate(services: services)
        delegate.dependencyProvider = RCTAppDependencyProvider()
        factory = RCTReactNativeFactory(delegate: delegate)
    }
    func rootView() -> UIView {
        if let root { return root }
        let view = factory.rootViewFactory.view(withModuleName: "EntryTask", initialProperties: [:], launchOptions: nil)
        root = view; AppLogger.log("runtime.surface.created"); return view
    }
}
final class ReactNativeDelegate: ETFactoryDelegate {
    override func sourceURL(for bridge: RCTBridge) -> URL? { bundleURL() }
    override func bundleURL() -> URL? {
        #if DEBUG
        RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "src/index")
        #else
        Bundle.main.url(forResource: "main", withExtension: "jsbundle")
        #endif
    }
}
final class RNContainerViewController: UIViewController {
    private let root: UIView
    init(root: UIView) { self.root = root; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    override func loadView() { view = root }
}
