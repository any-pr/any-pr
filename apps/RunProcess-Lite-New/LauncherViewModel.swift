import SwiftUI
import Combine
import AppKit

final class LauncherViewModel: ObservableObject {
    @Published var inputText: String = ""
    @Published var outputText: String = ""
    @Published var outputAttributed: NSAttributedString = NSAttributedString()
    @Published var isRunning: Bool = false
    @Published var suggestions: [Suggestion] = []
    @Published var selectedIndex: Int = 0
    @Published var useSudo: Bool = false
    @Published var showSudoPrompt: Bool = false
    @Published var focusRequested: Int = 0

    weak var window: LauncherWindow?

    private var executor = Executor()
    private var completer = Completer()
    private var historyIndex: Int = -1
    private var historyCommands: [String] = []
    private var backupInput: String = ""
    private var cancellables = Set<AnyCancellable>()

    init() {
        $outputText
            .sink { [weak self] text in
                self?.outputAttributed = ANSIColor.parse(text)
            }
            .store(in: &cancellables)
    }

    func requestSuggestions() {
        guard !inputText.isEmpty else { closeSuggestions(); return }
        completer.suggest(for: inputText) { [weak self] results in
            guard let self = self else { return }
            if results.isEmpty {
                self.closeSuggestions()
            } else {
                self.suggestions = results
                self.selectedIndex = 0
            }
        }
    }

    func selectNext() {
        guard !suggestions.isEmpty else { return }
        selectedIndex = (selectedIndex + 1) % suggestions.count
    }

    func selectPrevious() {
        guard !suggestions.isEmpty else { return }
        selectedIndex = (selectedIndex - 1 + suggestions.count) % suggestions.count
    }

    func confirmSelection() {
        guard selectedIndex < suggestions.count else { return }
        applySuggestion(suggestions[selectedIndex])
    }

    func closeSuggestions() {
        suggestions = []
        selectedIndex = 0
    }

    private func applySuggestion(_ s: Suggestion) {
        let words = inputText.split(separator: " ", omittingEmptySubsequences: false)
        if words.count > 1 {
            let prefix = words.dropLast().joined(separator: " ")
            inputText = prefix + " " + s.text
        } else {
            inputText = s.text
        }
        closeSuggestions()
        resetHistoryNav()
    }

    func navigateHistoryUp() -> String? {
        if historyCommands.isEmpty {
            historyCommands = History.shared.allSortedByFrecency().map { $0.command }
            backupInput = inputText
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
            return backupInput
        }
        return nil
    }

    func resetHistoryNav() {
        historyIndex = -1
        historyCommands = []
        backupInput = ""
    }

    func execute(password: String? = nil) {
        guard !inputText.isEmpty else { return }
        let command = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !command.isEmpty else { return }

        History.shared.record(command)
        resetHistoryNav()
        closeSuggestions()

        isRunning = true
        outputText = ""
        let sudo = useSudo

        executor.run(command: command, sudo: sudo, password: password) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.isRunning = false
                switch result {
                case .success(let text):
                    self.outputText = text.isEmpty ? "✓ Done" : text
                case .failure(let error):
                    self.outputText = "❌ \(error.localizedDescription)"
                }
            }
        }
    }

    func cancel() {
        guard isRunning else { return }
        executor.cancel()
        isRunning = false
        outputText = "Cancelled"
    }

    func clearOutput() {
        outputText = ""
        outputAttributed = NSAttributedString()
    }

    func copyOutput() {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(outputText, forType: .string)
    }

    func resizeWindow(to width: CGFloat) {
        window?.setContentWidth(width)
    }
}
