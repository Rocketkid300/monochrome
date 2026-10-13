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

    /// Optional override (iOS Settings -> Monochrome -> Server URL, or build-time MonoURL).
    /// nil means: use the web UI bundled inside the app.
    static func override() -> URL? {
        let stored = UserDefaults.standard.string(forKey: key)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let built = (Bundle.main.object(forInfoDictionaryKey: "MonoURL") as? String) ?? ""
        for raw in [stored, built] where !raw.isEmpty {
            let s = raw.contains("://") ? raw : "https://" + raw
            if let u = URL(string: s), u.host != nil { return u }
        }
        return nil
    }
}

struct RootView: View {
    @Environment(\.scenePhase) private var phase
    @State private var remote = ServerURL.override()

    var body: some View {
        WebView(remote: remote)
            .onChange(of: phase) { p in
                guard p == .active else { return }
                AudioSession.activate()
                let latest = ServerURL.override()
                if latest != remote { remote = latest }
            }
    }
}
