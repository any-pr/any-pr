//
//  CommandAlias.swift
//  RunProcess
//
//  Created by Haoran on 2026/10/3.
//

import Foundation

nonisolated struct CommandAlias: Codable, Identifiable, Equatable {
    var id: String { name }
    let name: String
    let expansion: String

    init(name: String, expansion: String) {
        self.name = name
        self.expansion = expansion
    }
}

/// YAML 顶层结构
nonisolated struct AliasFile: Codable {
    var aliases: [CommandAlias]
}

final class AliasStore {
    static let shared = AliasStore()

    private(set) var aliases: [CommandAlias] = []
    private let store = YAMLStore<AliasFile>(
        fileName: "aliases.yml",
        queueLabel: "com.runprocess.aliases"
    )

    private static let header = """
    # RunProcess aliases
    #
    # 格式:
    #   aliases:
    #     - name: gs
    #       expansion: git status
    #
    # 修改后重启应用生效。

    """

    private init() {
        store.legacyDecoder = { data in
            let list = try JSONDecoder().decode([CommandAlias].self, from: data)
            return AliasFile(aliases: list)
        }

        if let file = store.load() {
            aliases = file.aliases
        }

        if aliases.isEmpty {
            aliases = [
                CommandAlias(name: "gs", expansion: "git status"),
                CommandAlias(name: "gp", expansion: "git pull --rebase"),
                CommandAlias(name: "ll", expansion: "ls -lah"),
                CommandAlias(name: "serve", expansion: "python3 -m http.server 8000"),
            ]
            save()
        }
    }

    private func save() {
        store.save(AliasFile(aliases: aliases), header: Self.header)
    }

    // MARK: - 增删查

    func add(_ alias: CommandAlias) {
        if let idx = aliases.firstIndex(where: { $0.name == alias.name }) {
            aliases[idx] = alias
        } else {
            aliases.append(alias)
        }
        save()
    }

    /// 整体替换所有别名（用于排序等批量操作）
    func replaceAll(_ aliases: [CommandAlias]) {
        self.aliases = aliases
        save()
    }

    func remove(name: String) {
        aliases.removeAll { $0.name == name }
        save()
    }

    func match(prefix: String) -> [CommandAlias] {
        guard !prefix.isEmpty else { return [] }
        return aliases.filter { $0.name.hasPrefix(prefix) }
    }

    func expansion(for name: String) -> String? {
        aliases.first { $0.name == name }?.expansion
    }
}