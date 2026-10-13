import SwiftUI
import WebKit

struct WebView: UIViewRepresentable {
    let url: URL

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        cfg.allowsAirPlayForMediaPlayback = true
        cfg.websiteDataStore = .default()

        let js = """
        window.addEventListener('error', function(e){ webkit.messageHandlers.diag.postMessage('JS error: ' + e.message + ' @' + (e.filename||'') + ':' + e.lineno); });
        window.addEventListener('unhandledrejection', function(e){ webkit.messageHandlers.diag.postMessage('JS rejection: ' + (e.reason && (e.reason.message || e.reason))); });
        """
        cfg.userContentController.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        cfg.userContentController.add(context.coordinator, name: "diag")

        let web = WKWebView(frame: .zero, configuration: cfg)
        web.isOpaque = false
        web.backgroundColor = .black
        web.scrollView.backgroundColor = .black
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.allowsBackForwardNavigationGestures = true
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        context.coordinator.load(url, in: web)
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        if context.coordinator.loaded != url { context.coordinator.load(url, in: web) }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        var loaded: URL?

        func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
            Diag.shared.log("\(m.body)", error: true)
        }

        func webView(_ web: WKWebView, didStartProvisionalNavigation n: WKNavigation!) {
            Diag.shared.log("start \(web.url?.absoluteString ?? "?")")
        }
        func webView(_ web: WKWebView, didFinish n: WKNavigation!) {
            web.evaluateJavaScript("document.title + ' | text=' + (document.body ? document.body.innerText.length : -1)") { r, _ in
                Diag.shared.log("finished \(web.url?.absoluteString ?? "?") title/text: \(r ?? "nil")")
            }
        }
        func webView(_ web: WKWebView, didFail n: WKNavigation!, withError e: Error) {
            Diag.shared.log("load failed: \(e.localizedDescription)", error: true)
        }
        func webView(_ web: WKWebView, didFailProvisionalNavigation n: WKNavigation!, withError e: Error) {
            Diag.shared.log("load failed: \(e.localizedDescription)", error: true)
        }
        func webViewWebContentProcessDidTerminate(_ web: WKWebView) {
            Diag.shared.log("web content process terminated", error: true)
            web.reload()
        }

        func load(_ url: URL, in web: WKWebView) {
            loaded = url
            web.load(URLRequest(url: url))
        }

        // Links to other sites open in Safari; the app itself stays on the server's host.
        private func sameSite(_ a: String?, _ b: String?) -> Bool {
            func root(_ h: String?) -> String { (h ?? "").split(separator: ".").suffix(2).joined(separator: ".") }
            return root(a) == root(b)
        }

        func webView(_ web: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if action.navigationType == .linkActivated,
               let target = action.request.url, !sameSite(target.host, loaded?.host),
               ["http", "https"].contains(target.scheme ?? "") {
                UIApplication.shared.open(target)
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }

        func webView(_ web: WKWebView, createWebViewWith cfg: WKWebViewConfiguration,
                     for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let u = action.request.url { UIApplication.shared.open(u) }
            return nil
        }
    }
}
