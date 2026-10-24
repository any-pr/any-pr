import Foundation

struct HistoryEntry: Codable {
    var command: String
    var count: Int
    var lastUsed: Date

    var frecency: Double {
        let days = max(0, Date().timeIntervalSince(lastUsed) / 86400)
        return Double(count) / (1.0 + days)
    }
}

final class History {
    static let shared = History()

    private var entries: [String: HistoryEntry] = [:]
    private let maxEntries = 500
    private let fileURL: URL
    private let lock = NSLock()

    private init() {
        let support = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = support.appendingPathComponent("RunProcess-Lite")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("history.json")
        load()
    }

    func record(_ command: String) {
        let trimmed = command.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        lock.lock()
        if var e = entries[trimmed] {
            e.count += 1
            e.lastUsed = Date()
            entries[trimmed] = e
        } else {
            if entries.count >= maxEntries,
               let oldest = entries.min(by: { $0.value.lastUsed < $1.value.lastUsed }) {
                entries.removeValue(forKey: oldest.key)
            }
            entries[trimmed] = HistoryEntry(command: trimmed, count: 1, lastUsed: Date())
        }
        lock.unlock()
        save()
    }

    func allSortedByFrecency() -> [HistoryEntry] {
        lock.lock(); defer { lock.unlock() }
        return entries.values.sorted { $0.frecency > $1.frecency }
    }

    func query(prefix: String) -> [HistoryEntry] {
        guard !prefix.isEmpty else { return [] }
        lock.lock(); defer { lock.unlock() }
        return entries.values
            .filter { $0.command.hasPrefix(prefix) }
            .sorted { $0.frecency > $1.frecency }
            .prefix(8).map { $0 }
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        guard let decoded = try? JSONDecoder().decode([HistoryEntry].self, from: data) else { return }
        entries = Dictionary(uniqueKeysWithValues: decoded.map { ($0.command, $0) })
    }

    private func save() {
        lock.lock()
        let snapshot = Array(entries.values)
        lock.unlock()
        DispatchQueue.global(qos: .background).async { [fileURL] in
            guard let data = try? JSONEncoder().encode(snapshot) else { return }
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}
