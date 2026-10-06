//
//  CommandViewModel.swift
//  RunProcess
//
//  Created by Haoran on 2026/8/21.
//

import SwiftUI
import Combine

class CommandViewModel: ObservableObject {
    @Published var inputText: String = ""
    @Published var outputText: String = ""
    @Published var outputAttributed: AttributedString = AttributedString()
    @Published var isRunning: Bool = false
    @Published var canCancel: Bool = false
    @Published var suggestions: [Suggestion] = []
    @Published var selectedIndex: Int = 0
    @Published var showHistoryPanel: Bool = false

    var usesSessionMode: Bool = false
    weak var session: Session?

    var executeHandler: ((String, Bool, String?, @escaping (Result<String, Error>) -> Void) -> Void)?
    var cancelHandler: (() -> Void)?

    private var historyIndex: Int = -1
    private var historyCommands: [String] = []
    private var currentInputBackup: String = ""

    private let suggester = CommandSuggester()
    private let history = CommandHistory.shared
    private var cancellables = Set<AnyCancellable>()
    private weak var textField: NSView?

    private var cachedColorsEnabled = AppSettings.outputColorsEnabled
    private var cachedColorScheme = AppSettings.outputColorScheme

    // MARK: - Init

    init() {
        $inputText
            .dropFirst()
            .sink { [weak self] _ in
                self?.closeSuggestions()
                self?.resetHistoryNavigation()
            }
            .store(in: &cancellables)

        $outputText
            .sink { [weak self] text in
                self?.outputAttributed = ANSIParser.parse(text)
            }
            .store(in: &cancellables)

        NotificationCenter.default.addObserver(
            forName: UserDefaults.didChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            let enabled = AppSettings.outputColorsEnabled
            let scheme = AppSettings.outputColorScheme
            if enabled != self.cachedColorsEnabled || scheme != self.cachedColorScheme {
                self.cachedColorsEnabled = enabled
                self.cachedColorScheme = scheme
                self.outputAttributed = ANSIParser.parse(self.outputText)
            }
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - TextField 注册

    func registerTextField(_ view: NSView) {
        textField = view
    }

    var positioningView: NSView? {
        textField
    }

    // MARK: - 补全

    func requestSuggestions() {
        guard !inputText.isEmpty else {
            closeSuggestions()
            return
        }

        suggester.suggest(for: inputText) { [weak self] results in
            guard let self = self else { return }
            if results.isEmpty {
                self.closeSuggestions()
            } else {
                self.suggestions = results
                self.selectedIndex = 0
                SuggestionPanel.shared.show(with: self)
            }
        }
    }

    func selectNext() {
        guard !suggestions.isEmpty else { return }
        selectedIndex = (selectedIndex + 1) % suggestions.count
        SuggestionPanel.shared.updateContent(self)
    }

    func selectPrevious() {
        guard !suggestions.isEmpty else { return }
        selectedIndex = (selectedIndex - 1 + suggestions.count) % suggestions.count
        SuggestionPanel.shared.updateContent(self)
    }

    func confirmSelection() {
        guard selectedIndex < suggestions.count else { return }
        applySuggestion(suggestions[selectedIndex])
    }

    func closeSuggestions() {
        suggestions = []
        selectedIndex = 0
        SuggestionPanel.shared.hide()
    }

    // MARK: - 历史导航

    func navigateHistoryUp() -> String? {
        if historyCommands.isEmpty {
            historyCommands = history.getAll()
                .sorted { $0.frecency > $1.frecency }
                .map { $0.command }
            currentInputBackup = inputText
        }
        guard !historyCommands.isEmpty else { return nil }
        if historyIndex == -1 {
            historyIndex = historyCommands.count - 1
        } else if historyIndex > 0 {
            historyIndex -= 1
        }
        return historyCommands[historyIndex]
    }

    func navigateHistoryDown() -> String? {
        guard !historyCommands.isEmpty else { return nil }
        if historyIndex < historyCommands.count - 1 && historyIndex >= 0 {
            historyIndex += 1
            return historyCommands[historyIndex]
        } else if historyIndex == historyCommands.count - 1 {
            historyIndex = -1
            return currentInputBackup
        }
        return nil
    }

    func resetHistoryNavigation() {
        historyIndex = -1
        historyCommands = []
        currentInputBackup = ""
    }

    // MARK: - 执行

    func executeCommand(useSudo: Bool, password: String?,
                        completion: @escaping (String) -> Void) {
        guard !inputText.isEmpty else { return }

        let result = CommandPipeline.process(inputText)
        let processedCommand = result.command

        if processedCommand != inputText {
            inputText = processedCommand
        }

        if result.isInteractive {
            isRunning = false
            canCancel = false
            outputText = NSLocalizedString("error.interactive.command", comment: "")
            completion(outputText)
            return
        }

        history.record(processedCommand)
        resetHistoryNavigation()

        isRunning = true
        canCancel = true
        outputText = ""

        guard let handler = executeHandler else {
            isRunning = false
            canCancel = false
            outputText = "❌ 执行器未配置"
            completion(outputText)
            return
        }

        handler(processedCommand, useSudo, password) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.isRunning = false
                self.canCancel = false
                switch result {
                case .success(let text):
                    self.outputText = text.isEmpty
                        ? NSLocalizedString("output.success", comment: "")
                        : text
                case .failure(let error):
                    self.outputText = "❌ \(error.localizedDescription)"
                }
                completion(self.outputText)
            }
        }
    }

    func cancelExecution() {
        guard isRunning else { return }
        cancelHandler?()
        isRunning = false
        canCancel = false
        outputText = NSLocalizedString("output.cancelled", comment: "")
    }

    func clearHistory() {
        history.clearAll()
        resetHistoryNavigation()
        closeSuggestions()
    }

    func clearOutput() {
        outputText = ""
        outputAttributed = AttributedString()
    }

    func showSessionResetNotice() {
        outputText = NSLocalizedString("session.reset.notice", comment: "")
    }

    private func applySuggestion(_ suggestion: Suggestion) {
        let words = inputText.split(separator: " ", omittingEmptySubsequences: false)
        if words.count > 1 {
            let prefix = words.dropLast().joined(separator: " ")
            inputText = prefix + " " + suggestion.text
        } else {
            inputText = suggestion.text
        }
        closeSuggestions()
        resetHistoryNavigation()
    }
}

// MARK: - 纯函数：命令预处理

enum CommandPreprocessor {

    static func process(_ input: String) -> String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = trimmed.lowercased()

        if lower.hasPrefix("open ") || lower.hasPrefix("start ") { return trimmed }

        let unwrapped = unwrapQuotes(trimmed)

        if unwrapped.hasSuffix(".app") || unwrapped.hasSuffix(".app/") {
            return "open \(ShellQuoting.quote(unwrapped))"
        }

        if unwrapped.contains(".app/Contents/") || unwrapped.contains(".app/Contents/MacOS/") {
            if let range = unwrapped.range(of: ".app", options: .backwards) {
                let appPath = String(unwrapped[..<range.upperBound])
                return "open \(ShellQuoting.quote(appPath))"
            }
        }
        return trimmed
    }

    static func isInteractive(_ command: String) -> Bool {
        let unquoted = stripQuotes(command)
        let hasPipe = unquoted.contains("|")
        let hasRedirect = unquoted.contains(">") || unquoted.contains("<")

        let firstPart = command.split(separator: "|").first.map(String.init) ?? command
        let trimmed = firstPart.trimmingCharacters(in: .whitespacesAndNewlines)

        let interactive = [
            "vim","vi","nano","emacs","top","htop","less","more",
            "ssh","telnet","ftp","sftp",
            "python","python3","ipython","irb","node",
            "mysql","psql","sqlite3",
            "gdb","lldb","bc","dc",
            "sh","bash","zsh","fish",
            "mail","mutt","pine"
        ]
        for cmd in interactive {
            if trimmed == cmd || trimmed.hasPrefix(cmd + " ") {
                if hasPipe || hasRedirect { return false }
                return true
            }
        }
        if unquoted.contains(" -i ") || unquoted.contains(" --interactive ") { return true }
        return false
    }

    private static func unwrapQuotes(_ s: String) -> String {
        guard s.count >= 2 else { return s }
        let first = s.first!
        let last = s.last!
        if (first == "\"" && last == "\"") || (first == "'" && last == "'") {
            let inner = String(s.dropFirst().dropLast())
            if !inner.contains(first) {
                return inner
            }
        }
        return s
    }

    private static func stripQuotes(_ s: String) -> String {
        var result = ""
        var inSingle = false
        var inDouble = false
        var escaped = false
        for ch in s {
            if escaped { escaped = false; continue }
            if ch == "\\" { escaped = true; continue }
            if ch == "'" && !inDouble { inSingle.toggle(); continue }
            if ch == "\"" && !inSingle { inDouble.toggle(); continue }
            if !inSingle && !inDouble { result.append(ch) }
        }
        return result
    }
}