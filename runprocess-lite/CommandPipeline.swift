//
//  CommandPipeline.swift
//  RunProcess
//

import Foundation

/// 命令预处理管道：.app 补全 → 别名展开 → 交互命令判定
/// 纯函数，无状态，可单测
enum CommandPipeline {

    struct Result {
        let command: String
        let isInteractive: Bool
        let didExpandAlias: Bool
    }

    static func process(_ input: String, aliases: AliasStore = .shared) -> Result {
        let preprocessed = CommandPreprocessor.process(input)
        let expanded = expandAlias(in: preprocessed, aliases: aliases)
        let didExpand = expanded != preprocessed
        return Result(
            command: expanded,
            isInteractive: CommandPreprocessor.isInteractive(expanded),
            didExpandAlias: didExpand
        )
    }

    /// 只替换第一个词
    private static func expandAlias(in command: String, aliases: AliasStore) -> String {
        let parts = command.split(separator: " ", maxSplits: 1,
                                  omittingEmptySubsequences: false)
        guard let first = parts.first, !first.isEmpty else { return command }
        guard let expansion = aliases.expansion(for: String(first)) else { return command }
        if parts.count == 1 { return expansion }
        return expansion + " " + parts[1]
    }
}