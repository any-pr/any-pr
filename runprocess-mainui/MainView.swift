//
//  MainView.swift
//  纯 UI 演示 —— 无任何业务逻辑
//

import SwiftUI

struct MainView: View {
    // 纯 UI 状态，不接任何后端
    @State private var inputText = ""
    @State private var useSudo = false
    @State private var outputText = ""
    @State private var isRunning = false
    @State private var outputHeight: CGFloat = 100
    @State private var showSudoDialog = false
    @State private var sudoPassword = ""
    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(spacing: 12) {
            inputRow
            optionRow
            if outputText.isEmpty { hintRow } else { outputView }
        }
        .padding(20)
        .frame(width: 520)
        .frame(height: outputText.isEmpty ? 140 : 120 + outputHeight)
        .background(.ultraThinMaterial)
        .onAppear { isFocused = true }
        .sheet(isPresented: $showSudoDialog) {
            SudoPasswordDialog(
                password: $sudoPassword,
                onConfirm: { showSudoDialog = false; sudoPassword = "" },
                onCancel: { showSudoDialog = false; sudoPassword = "" }
            )
        }
    }

    // MARK: - 输入行

    private var inputRow: some View {
        HStack(spacing: 8) {
            Image(systemName: "terminal")
                .foregroundColor(.secondary)
                .font(.system(size: 18))
                .frame(height: 44)

            TextField("Enter command or drag file...", text: $inputText)
                .textFieldStyle(.plain)
                .font(.system(size: 18, design: .monospaced))
                .focused($isFocused)
                .frame(height: 44)
                .onSubmit { fakeRun() }

            if isRunning {
                Button { isRunning = false } label: {
                    Image(systemName: "stop.circle.fill")
                        .foregroundColor(.red)
                        .font(.system(size: 22))
                }
                .buttonStyle(.plain)
                .frame(height: 44)
            }

            Button { fakeRun() } label: {
                Image(systemName: isRunning ? "ellipsis.circle" : "return")
                    .foregroundColor(.secondary)
                    .font(.system(size: 20))
            }
            .buttonStyle(.plain)
            .frame(height: 44)
            .disabled(inputText.isEmpty || isRunning)
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
                    Image(systemName: "lock.shield")
                        .font(.system(size: 12))
                        .foregroundColor(useSudo ? .orange : .secondary)
                    Text("Run as root")
                        .font(.system(size: 12))
                        .foregroundColor(useSudo ? .orange : .secondary)
                }
            }
            .toggleStyle(.checkbox)

            Spacer()

            if !outputText.isEmpty {
                Button { outputText = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary.opacity(0.5))
                }
                .buttonStyle(.plain)

                Button {
                    let pb = NSPasteboard.general
                    pb.clearContents()
                    pb.setString(outputText, forType: .string)
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary.opacity(0.7))
                }
                .buttonStyle(.plain)
            }

            if isRunning { ProgressView().controlSize(.small).padding(.leading, 4) }
        }
        .padding(.horizontal, 4)
    }

    // MARK: - 输出 / 提示

    private var outputView: some View {
        ScrollView {
            Text(outputText)
                .font(.system(size: 13, design: .monospaced))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
                .textSelection(.enabled)
        }
        .frame(height: outputHeight)
        .background(HeightObserver(text: outputText, height: $outputHeight))
        .transition(.opacity)
    }

    private var hintRow: some View {
        HStack(spacing: 16) {
            Text("Drop file")
            Text("·")
            Text("Tab complete")
            Text("·")
            Text("Up/Down History")
            Text("·")
            Text("Cmd+W Hide")
            Text("·")
            Text("Shift+Enter Newline")
        }
        .font(.system(size: 11))
        .foregroundColor(.secondary.opacity(0.6))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }

    // MARK: - 纯 UI 演示：只切状态，不干实事

    private func fakeRun() {
        guard !inputText.isEmpty else { return }
        if useSudo {
            showSudoDialog = true
            return
        }
        isRunning = true
        outputText = ""
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            isRunning = false
            outputText = "✅ \(inputText)"
        }
    }
}

// MARK: - Sudo 密码框（纯 UI）

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
                Text("Administrator Privileges Required").font(.headline)
                Spacer()
            }
            .padding(.horizontal, 4)

            Text("This command requires root privileges. Please enter your password.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            SecureField("Enter password", text: $password)
                .textFieldStyle(.roundedBorder)
                .focused($isFocused)
                .onAppear { isFocused = true }
                .onSubmit { if !password.isEmpty { onConfirm() } }

            HStack {
                Image(systemName: "info.circle").font(.system(size: 11))
                Text("Password is used only in memory during execution and is never stored or logged")
                    .font(.system(size: 11))
                Spacer()
            }
            .foregroundColor(.secondary.opacity(0.6))

            HStack(spacing: 12) {
                Button("Cancel") { onCancel() }
                    .keyboardShortcut(.escape)
                Button("Execute") { if !password.isEmpty { onConfirm() } }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                    .disabled(password.isEmpty)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(20)
        .frame(width: 380)
    }
}

// MARK: - 输出高度自适应

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

#Preview {
    MainView()
}