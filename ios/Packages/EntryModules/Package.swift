// swift-tools-version: 6.0
import PackageDescription

// 六个库只依赖CoreKit；App负责实现跨模块适配。
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
