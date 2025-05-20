// swift-tools-version:5.5
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "iOSAutomatorDependencies", // This name is for the package itself
    platforms: [
        .iOS(.v15) // Aligning with the project's deployment target
    ],
    dependencies: [
        // Dependencies declare other packages that this package depends on.
        .package(name: "Firebase", url: "https://github.com/firebase/firebase-ios-sdk.git", from: "10.0.0") // Using a recent version
    ],
    targets: [
        // Targets are the basic building blocks of a package. A target can define a module or a test suite.
        // Targets can depend on other targets in this package, and on products in packages this package depends on.
        // We define a dummy target here because a Package.swift needs at least one target.
        // The main purpose of this file in this context is to declare dependencies for the Xcode project.
        .target(
            name: "iOSAutomatorAppTargetDependencies",
            dependencies: [
                .product(name: "FirebaseVertexAI", package: "Firebase"),
                .product(name: "FirebaseAnalytics", package: "Firebase")
            ],
            path: "iOSAutomator" // Point to the app's source code directory
        )
    ]
)
