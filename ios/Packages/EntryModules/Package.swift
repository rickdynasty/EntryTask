// swift-tools-version: 6.0
// 文件职责：Swift Package入口：声明六个library、依赖边界与测试目标；Swift工具版本声明必须保留首行。
import PackageDescription

// 模块通过CoreKit契约协作；StorageKit另依赖GRDB，跨模块实现由App适配器组装。
let package = Package(name: "EntryModules", platforms: [.iOS("15.1"), .macOS(.v13)],
    products: ["CoreKit", "UserFeature", "NetworkKit", "StorageKit", "DemoEngine", "DashboardFeature"].map {
        .library(name: $0, targets: [$0])
    },
    dependencies: [.package(url: "https://github.com/groue/GRDB.swift.git", exact: "7.8.0")],
    targets: [
        .target(name: "CoreKit"),
        .target(name: "UserFeature", dependencies: ["CoreKit"]),
        .target(name: "NetworkKit", dependencies: ["CoreKit"]),
        .target(name: "StorageKit", dependencies: ["CoreKit", .product(name: "GRDB", package: "GRDB.swift")]),
        .target(name: "DemoEngine", dependencies: ["CoreKit"]),
        .target(name: "DashboardFeature", dependencies: ["CoreKit"]),
        .testTarget(name: "EntryModulesTests", dependencies: ["CoreKit", "UserFeature", "NetworkKit", "StorageKit", "DemoEngine", "DashboardFeature"])
    ], swiftLanguageModes: [.v5])
