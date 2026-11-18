import SenadaShared
import SwiftUI

private struct SenadaView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct SenadaApp: App {
    var body: some Scene {
        WindowGroup {
            SenadaView().ignoresSafeArea()
        }
    }
}
