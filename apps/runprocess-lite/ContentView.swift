//
//  ContentView.swift
//  RunProcess
//
//  Created by Haoran on 2026/8/19.
//

import SwiftUI

struct ContentView: View {
    @ObservedObject var viewModel: CommandViewModel
    @FocusState private var isFocused: Bool

    @State private var useSudo: Bool = AppSettings.defaultSudo
    @State private var showSudoPasswordDialog = false
    @State private var sudoPassword = ""
    @State private var outputHeight: CGFloat = 100

    @AppStorage(AppSettings.Keys.appearanceStyle) private var appearanceStyleRaw: String = AppSettings.appearanceStyleRaw.rawValue
    @AppStorage(AppSettings.Keys.defaultSudo) private var defaultSudo: Bool = false

    private var appearanceStyle: AppearanceStyle { AppSettings.resolvedAppearanceStyle }

    init(viewModel: CommandViewModel) { self.viewModel = viewModel }

    var body: some View {
        VStack(spacing: 12) {
            inputRow
            optionRow
            if viewModel.outputText.isEmpty { hintRow } else { outputView }
        }
        .padding(20)
        .frame(width: 520)
        .frame(height: viewModel.outputText.isEmpty ? 140 : 120 + outputHeight)
        .background(
            AdaptiveWindowBackground(
                style: appearanceStyle,
                material: .underWindowBackground,
                blendingMode: .behindWindow
            )
            .ignoresSafeArea()
        )
        .onExitCommand { viewModel.closeSuggestions() }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("FocusTextField"))) { _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { isFocused = true }
        }
        .sheet(isPresented: $showSudoPasswordDialog) {
            SudoPasswordDialog(
                password: $sudoPassword,
                onConfirm: { executeWithSudo() },
                onCancel: { showSudoPasswordDialog = false; sudoPassword = "" }
            )
        }
        .sheet(isPresented: $viewModel.showHistoryPanel) {
            HistorySearchView(viewModel: viewModel)
        }
    }

    // MARK: - 输入行

    private var inputRow: some View {
        HStack(spacing: 8) {
            Image(systemName: "terminal")
                .foregroundColor(.secondary)
                .font(.system(size: 18))
                .frame(height: 44)

            RunTextField(
                text: $viewModel.inputText,
                onTab: viewModel.requestSuggestions,
                onEnter: executeCommand,
                onUp: { viewModel.navigateHistoryUp() },
                onDown: { viewModel.navigateHistoryDown() }
            )
            .font(.system(size: 18, design: .monospaced))
            .focused($isFocused)
            .onAppear { isFocused = true }
            .frame(height: 44)
            .overlay(
                NSViewAccessor { nsView in viewModel.registerTextField(nsView) }
            )

            if viewModel.isRunning && viewModel.canCancel {
                Button(action: viewModel.cancelExecution) {
                    Image(systemName: "stop.circle.fill")
                        .foregroundColor(.red)
                        .font(.system(size: 22))
                }
                .buttonStyle(.plain)
                .frame(height: 44)
                .help(NSLocalizedString("button.cancel.tooltip", comment: ""))
            }

            Button(action: executeCommand) {
                Image(systemName: viewModel.isRunning ? "ellipsis.circle" : "return")
                    .foregroundColor(.secondary)
                    .font(.system(size: 20))
            }
            .buttonStyle(.plain)
            .frame(height: 44)
            .keyboardShortcut(.defaultAction)
            .disabled(viewModel.inputText.isEmpty || viewModel.isRunning)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(NSColor.controlBackgroundColor).opacity(0.6))
                .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 2)
        )
    }

    // MARK: - 选项行

    private var optionRow: some View {
        HStack {
            Toggle(isOn: $useSudo) {
                HStack(spacing: 4) {
                    Image(systemName: defaultSudo ? "lock.fill" : "lock.shield")
                        .font(.system(size: 12))
                        .foregroundColor(useSudo ? .orange : .secondary)
                    Text(NSLocalizedString("sudo.toggle.label", comment: ""))
                        .font(.system(size: 12))
                        .foregroundColor(useSudo ? .orange : .secondary)
                }
            }
            .toggleStyle(.checkbox)
            .disabled(defaultSudo)
            .help(defaultSudo
                  ? NSLocalizedString("sudo.toggle.locked.hint", comment: "")
                  : NSLocalizedString("sudo.toggle.label", comment: ""))

            Spacer()

            if !viewModel.outputText.isEmpty {
                Button(action: viewModel.clearOutput) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary.opacity(0.5))
                }
                .buttonStyle(.plain)
                .help(NSLocalizedString("output.clear.tooltip", comment: ""))

                Button {
                    let pb = NSPasteboard.general
                    pb.clearContents()
                    pb.setString(viewModel.outputText, forType: .string)
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary.opacity(0.7))
                }
                .buttonStyle(.plain)
                .help("Copy output")
            }

            if viewModel.isRunning {
                ProgressView().controlSize(.small).padding(.leading, 4)
            }
        }
        .padding(.horizontal, 4)
        .onAppear {
            if defaultSudo { useSudo = true }
        }
        .onChange(of: defaultSudo) { newValue in
            if newValue { useSudo = true }
        }
    }

    // MARK: - 输出

    private var outputView: some View {
        ScrollView {
            Text(viewModel.outputAttributed)
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
                .textSelection(.enabled)
        }
        .frame(height: outputHeight)
        .background(HeightObserver(text: viewModel.outputText, height: $outputHeight))
        .transition(.opacity)
    }

    // MARK: - 提示

    private var hintRow: some View {
        HStack(spacing: 16) {
            Text(NSLocalizedString("hint.drag.file", comment: ""))
            Text("·")
            Text(NSLocalizedString("hint.tab.completion", comment: ""))
            Text("·")
            Text(NSLocalizedString("hint.history.navigation", comment: ""))
            Text("·")
            Text(NSLocalizedString("hint.hide.window", comment: ""))
            Text("·")
            Text(NSLocalizedString("hint.shift.enter", comment: ""))
        }
        .font(.system(size: 11))
        .foregroundColor(.secondary.opacity(0.6))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }

    // MARK: - 动作

    func executeCommand() {
        guard !viewModel.inputText.isEmpty else { return }
        if useSudo {
            showSudoPasswordDialog = true
            sudoPassword = ""
        } else {
            viewModel.executeCommand(useSudo: false, password: nil) { _ in
                viewModel.closeSuggestions()
            }
        }
    }

    func executeWithSudo() {
        showSudoPasswordDialog = false
        let password = sudoPassword
        sudoPassword = ""
        viewModel.executeCommand(useSudo: true, password: password) { _ in
            viewModel.closeSuggestions()
        }
    }
}

// MARK: - Sudo Password Dialog

struct SudoPasswordDialog: View {
    @Binding var password: String
    let onConfirm: () -> Void
    let onCancel: () -> Void

    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "lock.shield.fill")
                    .foregroundColor(.orange)
                    .font(.system(size: 18))
                Text(NSLocalizedString("sudo.dialog.title", comment: ""))
                    .font(.headline)
                Spacer()
            }
            .padding(.horizontal, 4)

            Text(NSLocalizedString("sudo.dialog.message", comment: ""))
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            SecureField(NSLocalizedString("sudo.dialog.password.placeholder", comment: ""), text: $password)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)
                .onAppear { isFocused = true }
                .onSubmit { if !password.isEmpty { onConfirm() } }

            HStack {
                Image(systemName: "info.circle")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary.opacity(0.6))
                Text(NSLocalizedString("sudo.dialog.security.hint", comment: ""))
                    .font(.system(size: 11))
                    .foregroundColor(.secondary.opacity(0.6))
                Spacer()
            }

            HStack(spacing: 12) {
                Button(NSLocalizedString("button.cancel", comment: "")) { onCancel() }
                    .keyboardShortcut(.escape)
                Button(NSLocalizedString("button.execute", comment: "")) {
                    if !password.isEmpty { onConfirm() }
                }
                .keyboardShortcut(.defaultAction)
                .buttonStyle(.borderedProminent)
                .disabled(password.isEmpty)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(20)
        .frame(width: 380)
        .background(
            AdaptiveGlassBackground(
                style: AppSettings.resolvedAppearanceStyle,
                material: .hudWindow,
                blendingMode: .behindWindow,
                cornerRadius: 12
            )
        )
    }
}

// MARK: - 辅助

struct NSViewAccessor: NSViewRepresentable {
    let callback: (NSView) -> Void
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async { callback(view) }
        return view
    }
    func updateNSView(_ nsView: NSView, context: Context) {}
}

/// 输出高度自适应
private struct HeightObserver: View {
    let text: String
    @Binding var height: CGFloat

    var body: some View {
        GeometryReader { _ in
            Color.clear
                .onAppear { update() }
                .onChange(of: text) { _ in update() }
        }
    }

    private func update() {
        let lines = text.components(separatedBy: "\n").count
        withAnimation(.easeInOut(duration: 0.15)) {
            height = min(max(CGFloat(lines) * 20 + 20, 60), 220)
        }
    }
}