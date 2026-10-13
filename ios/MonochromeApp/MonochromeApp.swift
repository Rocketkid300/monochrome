import SwiftUI
import AVFoundation

@main
struct MonochromeApp: App {
    init() { AudioSession.activate() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .background(Color.black.ignoresSafeArea())
        }
    }
}

enum AudioSession {
    static func activate() {
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .default, options: [])
        try? s.setActive(true)
    }
}

enum ServerURL {
    static let key = "server_url"

    static let official = URL(string: "https://monochrome.st")!

    /// Official Monochrome site, unless overridden (iOS Settings -> Monochrome -> Server URL, or build-time MonoURL).
    static func current() -> URL {
        let stored = UserDefaults.standard.string(forKey: key)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let built = (Bundle.main.object(forInfoDictionaryKey: "MonoURL") as? String) ?? ""
        for raw in [stored, built] where !raw.isEmpty {
            let s = raw.contains("://") ? raw : "https://" + raw
            if let u = URL(string: s), u.host != nil { return u }
        }
        return official
    }
}

struct RootView: View {
    @Environment(\.scenePhase) private var phase
    @State private var url = ServerURL.current()

    var body: some View {
        WebView(url: url)
            .onChange(of: phase) { p in
                guard p == .active else { return }
                AudioSession.activate()
                let latest = ServerURL.current()
                if latest != url { url = latest }
            }
    }
}
