import WebKit

/// Serves the web UI bundled in the app (Resources/web) at mono://app/...
final class BundleSchemeHandler: NSObject, WKURLSchemeHandler {
    static let scheme = "mono"
    static let entry = URL(string: "mono://app/index.html")!

    private let root = Bundle.main.resourceURL!.appendingPathComponent("web").standardizedFileURL

    func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
        guard let url = task.request.url else { task.didFailWithError(URLError(.badURL)); return }
        let rel = url.path == "/" || url.path.isEmpty ? "index.html" : String(url.path.dropFirst())
        var file = root.appendingPathComponent(rel).standardizedFileURL
        guard file.path.hasPrefix(root.path) else { task.didFailWithError(URLError(.badURL)); return }

        var status = 200
        if !FileManager.default.fileExists(atPath: file.path) {
            if file.pathExtension.isEmpty {
                file = root.appendingPathComponent("index.html")   // SPA fallback
            } else {
                status = 404
            }
        }
        let data = status == 200 ? ((try? Data(contentsOf: file)) ?? Data()) : Data()
        let headers = [
            "Content-Type": Self.mime(file.pathExtension),
            "Content-Length": String(data.count),
            "Access-Control-Allow-Origin": "*",
        ]
        let resp = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
        task.didReceive(resp)
        task.didReceive(data)
        task.didFinish()
    }

    func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {}

    private static func mime(_ ext: String) -> String {
        switch ext.lowercased() {
        case "html", "htm": return "text/html; charset=utf-8"
        case "js", "mjs": return "text/javascript; charset=utf-8"
        case "css": return "text/css; charset=utf-8"
        case "json", "webmanifest": return "application/json; charset=utf-8"
        case "svg": return "image/svg+xml"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "webp": return "image/webp"
        case "gif": return "image/gif"
        case "ico": return "image/x-icon"
        case "woff": return "font/woff"
        case "woff2": return "font/woff2"
        case "ttf": return "font/ttf"
        case "wasm": return "application/wasm"
        case "mp3": return "audio/mpeg"
        case "txt": return "text/plain; charset=utf-8"
        default: return "application/octet-stream"
        }
    }
}
