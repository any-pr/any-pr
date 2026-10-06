//
//  CommandHistory.swift
//  RunProcess
//
//  Created by Haoran on 2026/8/21.
//

import Foundation

nonisolated struct HistoryEntry: Codable {
    let command: String
    var count: Int
    var lastUsed: Date

    init(command: String) {
        self.command = command
        self.count = 1
        self.lastUsed = Date()
    }

    mutating func recordUsage() {
        count += 1
        lastUsed = Date()
    }

    var frecency: Double {
        let days = max(0, Date().timeIntervalSince(lastUsed) / 86400)
        return Double(count) / (1.0 + days)
    }
}

/// YAML 顶层结构
nonisolated struct HistoryFile: Codable {
    var entries: [HistoryEntry]
}

class CommandHistory {
    static let shared = CommandHistory()

    private let maxEntries = 500
    private let store = YAMLStore<HistoryFile>(
        fileName: "history.yml",
        queueLabel: "com.runprocess.history"
    )
    private var entries: [String: HistoryEntry] = [:]
    private let readWriteLock = NSLock()

    private init() {
        store.legacyDecoder = { data in
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .deferredToDate
            let dict = try decoder.decode([String: HistoryEntry].self, from: data)
            return HistoryFile(entries: Array(dict.values))
        }

        if let file = store.load() {
            entries = Dictionary(uniqueKeysWithValues: file.entries.map { ($0.command, $0) })
        }
    }

    // MARK: - 保存

    private func saveSync() {
        readWriteLock.lock()
        let copy = Array(entries.values)
        readWriteLock.unlock()
        let file = HistoryFile(entries: copy.sorted { $0.lastUsed > $1.lastUsed })
        store.save(file)
    }

    // MARK: - 记录

    func record(_ command: String) {
        let trimmed = command.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        readWriteLock.lock()
        if var existing = entries[trimmed] {
            existing.recordUsage()
            entries[trimmed] = existing
        } else {
            if entries.count >= maxEntries,
               let oldest = entries.min(by: { $0.value.lastUsed < $1.value.lastUsed }) {
                entries.removeValue(forKey: oldest.key)
            }
            entries[trimmed] = HistoryEntry(command: trimmed)
        }
        readWriteLock.unlock()
        saveSync()
    }

    // MARK: - 查询

    func query(prefix: String) -> [HistoryEntry] {
        query(prefix: prefix, sortBy: { $0.count > $1.count })
    }

    func queryByFrecency(prefix: String) -> [HistoryEntry] {
        query(prefix: prefix, sortBy: { $0.frecency > $1.frecency })
    }

    private func query(prefix: String,
                       sortBy: (HistoryEntry, HistoryEntry) -> Bool) -> [HistoryEntry] {
        guard !prefix.isEmpty else { return [] }
        readWriteLock.lock(); let copy = entries; readWriteLock.unlock()
        return copy.values
            .filter { $0.command.hasPrefix(prefix) }
            .sorted(by: sortBy)
            .prefix(20).map { $0 }
    }

    func search(_ query: String) -> [HistoryEntry] {
        readWriteLock.lock(); let copy = entries; readWriteLock.unlock()
        let q = query.lowercased()
        guard !q.isEmpty else {
            return copy.values.sorted { $0.frecency > $1.frecency }.prefix(50).map { $0 }
        }
        return copy.values
            .filter { fuzzyMatch(q, in: $0.command.lowercased()) }
            .sorted { $0.frecency > $1.frecency }
            .prefix(50).map { $0 }
    }

    private func fuzzyMatch(_ query: String, in text: String) -> Bool {
        var qi = query.startIndex
        for ch in text {
            if qi < query.endIndex, ch == query[qi] {
                qi = query.index(after: qi)
            }
        }
        return qi == query.endIndex
    }

    // MARK: - 管理

    func clearAll() {
        readWriteLock.lock(); entries.removeAll(); readWriteLock.unlock()
        saveSync()
    }

    func count() -> Int {
        readWriteLock.lock(); defer { readWriteLock.unlock() }
        return entries.count
    }

    func getAll() -> [HistoryEntry] {
        readWriteLock.lock(); defer { readWriteLock.unlock() }
        return Array(entries.values)
    }
}