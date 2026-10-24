import Foundation

struct Suggestion: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let icon: String
    let historyCount: Int?

    init(text: String, icon: String, historyCount: Int? = nil) {
        self.text = text
        self.icon = icon
        self.historyCount = historyCount
    }

    static func == (lhs: Suggestion, rhs: Suggestion) -> Bool {
        lhs.text == rhs.text && lhs.icon == rhs.icon
    }
}

final class Completer {
    private let queue = DispatchQueue(label: "com.runprocess-lite.completer", qos: .userInitiated)
    private var cachedCommands: [String] = []
    private var cacheTime: Date = .distantPast
    private let cacheTTL: TimeInterval = 60

    func suggest(for input: String, completion: @escaping ([Suggestion]) -> Void) {
        queue.async { [weak self] in
            guard let self = self else {
                DispatchQueue.main.async { completion([]) }
                return
            }
            let results = self.generate(for: input)
            DispatchQueue.main.async { completion(results) }
        }
    }

    private func generate(for input: String) -> [Suggestion] {
        let words = input.split(separator: " ", omittingEmptySubsequences: false)
        guard let last = words.last.map(String.init), !last.isEmpty else { return [] }

        if last.hasPrefix("/") || last.hasPrefix("~") || last.hasPrefix("./") {
            return pathSuggestions(last)
        }

        return commandSuggestions(last)
    }

    private func commandSuggestions(_ prefix: String) -> [Suggestion] {
        var out: [Suggestion] = []
        var seen = Set<String>()

        for e in History.shared.query(prefix: prefix) {
            if seen.insert(e.command).inserted {
                out.append(Suggestion(text: e.command, icon: "clock.arrow.circlepath",
                                      historyCount: e.count))
            }
        }

        for cmd in systemCommands(prefix: prefix) {
            if seen.insert(cmd).inserted {
                out.append(Suggestion(text: cmd, icon: "terminal"))
            }
        }

        return Array(out.prefix(12))
    }

    private func pathSuggestions(_ input: String) -> [Suggestion] {
        let expanded = (input as NSString).expandingTildeInPath
        let partial = (expanded as NSString).lastPathComponent
        let dir = (expanded as NSString).deletingLastPathComponent
        let dirPath = dir.isEmpty ? "." : dir

        guard let files = try? FileManager.default.contentsOfDirectory(atPath: dirPath) else {
            return []
        }

        let showHidden = partial.hasPrefix(".")
        var out: [Suggestion] = []

        for name in files.sorted().prefix(50) {
            if !showHidden && name.hasPrefix(".") { continue }
            if !name.hasPrefix(partial) { continue }

            let full = (dirPath as NSString).appendingPathComponent(name)
            var isDir: ObjCBool = false
            FileManager.default.fileExists(atPath: full, isDirectory: &isDir)

            let display = (input as NSString).deletingLastPathComponent
            let prefix = display.isEmpty ? "" : display + "/"
            let suffix = isDir.boolValue ? "/" : ""
            let text = prefix + name + suffix

            out.append(Suggestion(text: shellQuote(text), icon: isDir.boolValue ? "folder" : "doc"))
            if out.count >= 10 { break }
        }
        return out
    }

    private func systemCommands(prefix: String) -> [String] {
        let now = Date()
        if now.timeIntervalSince(cacheTime) < cacheTTL, !cachedCommands.isEmpty {
            return cachedCommands.filter { $0.hasPrefix(prefix) }.prefix(10).map { $0 }
        }
        cachedCommands = loadPATHCommands()
        cacheTime = now
        return cachedCommands.filter { $0.hasPrefix(prefix) }.prefix(10).map { $0 }
    }

    private func loadPATHCommands() -> [String] {
        let path = ProcessInfo.processInfo.environment["PATH"]
            ?? "/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/opt/homebrew/bin"
        var result: [String] = []
        var seen = Set<String>()

        for dir in path.split(separator: ":") {
            let dirStr = String(dir)
            guard let files = try? FileManager.default.contentsOfDirectory(atPath: dirStr) else {
                continue
            }
            for file in files {
                guard !file.hasPrefix("."), seen.insert(file).inserted else { continue }
                let full = (dirStr as NSString).appendingPathComponent(file)
                if isExecutable(full) { result.append(file) }
            }
        }
        return result.sorted()
    }

    private func isExecutable(_ path: String) -> Bool {
        FileManager.default.isExecutableFile(atPath: path)
    }

    private func shellQuote(_ s: String) -> String {
        let safe = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-./~@+")
        if s.unicodeScalars.allSatisfy({ safe.contains($0) }) { return s }
        let escaped = s
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }
}
