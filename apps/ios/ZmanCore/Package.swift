// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "ZmanCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ZmanCore", targets: ["ZmanCore"]),
    ],
    targets: [
        .target(name: "ZmanCore"),
        .testTarget(name: "ZmanCoreTests", dependencies: ["ZmanCore"]),
    ]
)
