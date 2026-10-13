import SwiftUI

/// Collects load/script errors: appends to Documents/diag.log and shows the latest on screen.
final class Diag: ObservableObject {
    static let shared = Diag()
    @Published var banner = ""
    private let file = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("diag.log")

    func log(_ text: String, error: Bool = false) {
        let line = "\(Date()) \(text)\n"
        if let h = try? FileHandle(forWritingTo: file) {
            h.seekToEndOfFile(); h.write(Data(line.utf8)); try? h.close()
        } else {
            try? line.write(to: file, atomically: true, encoding: .utf8)
        }
        if error { DispatchQueue.main.async { self.banner = String(text.prefix(300)) } }
    }
}
