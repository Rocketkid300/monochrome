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

    /// iOS Settings -> Monochrome -> Server URL, else the build-time MonoURL.
    static func current() -> URL? {
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
    @State private var url = ServerURL.current()
    @State private var draft = ""

    var body: some View {
        Group {
            if let url {
                WebView(url: url)
            } else {
                VStack(spacing: 16) {
                    Text("Monochrome").font(.title).bold()
                    Text("Enter the HTTPS address where your Monochrome web UI is hosted.")
                        .multilineTextAlignment(.center).foregroundColor(.secondary)
                    TextField("https://music.example.ts.net", text: $draft)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                    Button("Open") {
                        UserDefaults.standard.set(draft, forKey: ServerURL.key)
                        url = ServerURL.current()
                    }.buttonStyle(.borderedProminent)
                    Text("Change later in iOS Settings → Monochrome.")
                        .font(.footnote).foregroundColor(.secondary)
                }.padding(32)
            }
        }
        .onChange(of: phase) { p in
            guard p == .active else { return }
            AudioSession.activate()
            let latest = ServerURL.current()
            if latest != url { url = latest }
        }
    }
}
