import SwiftUI
import AppKit

struct LauncherView: View {
    @ObservedObject var viewModel: LauncherViewModel
    @State private var outputHeight: CGFloat = 100

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "terminal")
                    .foregroundColor(.secondary)
                    .font(.system(size: 18))
                    .frame(height: 44)

                CommandField(
                    text: $viewModel.inputText,
                    onTab: viewModel.requestSuggestions,
                    onEnter: handleEnter,
                    onUp: { viewModel.navigateHistoryUp() },
                    onDown: { viewModel.navigateHistoryDown() },
                    onEscape: { viewModel.closeSuggestions() },
                    focusTrigger: viewModel.focusRequested
                )
                .frame(height: 44)

                if viewModel.isRunning {
                    Button(action: viewModel.cancel) {
                        Image(systemName: "stop.circle.fill")
                            .foregroundColor(.red)
                            .font(.system(size: 22))
                    }
                    .buttonStyle(.plain)
                    .frame(height: 44)
                }

                Button(action: { handleEnter() }) {
                    Image(systemName: viewModel.isRunning ? "ellipsis.circle" : "return")
                        .foregroundColor(.secondary)
                        .font(.system(size: 20))
                }
                .buttonStyle(.plain)
                .frame(height: 44)
                .disabled(viewModel.inputText.isEmpty || viewModel.isRunning)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(NSColor.controlBackgroundColor).opacity(0.5))
            )

            HStack {
                Toggle(isOn: $viewModel.useSudo) {
                    HStack(spacing: 4) {
                        Image(systemName: "lock.shield")
                            .font(.system(size: 12))
                            .foregroundColor(viewModel.useSudo ? .orange : .secondary)
                        Text("Run as root")
                            .font(.system(size: 12))
                            .foregroundColor(viewModel.useSudo ? .orange : .secondary)
                    }
                }
                .toggleStyle(.checkbox)

                Spacer()

                if !viewModel.outputText.isEmpty {
                    Button(action: viewModel.clearOutput) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary.opacity(0.5))
                    }
                    .buttonStyle(.plain)

                    Button(action: viewModel.copyOutput) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary.opacity(0.7))
                    }
                    .buttonStyle(.plain)
                }

                if viewModel.isRunning {
                    ProgressView().controlSize(.small).padding(.leading, 4)
                }
            }
            .padding(.horizontal, 4)

            if !viewModel.outputText.isEmpty {
                OutputView(
                    attributed: viewModel.outputAttributed,
                    onContentWidth: { width in
                        let target = min(max(width + 40, 680), 1200)
                        viewModel.resizeWindow(to: target)
                    }
                )
                .frame(height: outputHeight)
                .padding(.horizontal, 4)
                .background(HeightMeasurer(text: viewModel.outputText, height: $outputHeight))
            } else {
                HStack(spacing: 14) {
                    Text("Drag file")
                    Text("·")
                    Text("Tab complete")
                    Text("·")
                    Text("↑↓ History")
                    Text("·")
                    Text("⌘W Hide")
                }
                .font(.system(size: 11))
                .foregroundColor(.secondary.opacity(0.6))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
            }

            if !viewModel.suggestions.isEmpty {
                SuggestionList(viewModel: viewModel)
            }
        }
        .padding(20)
        .frame(minWidth: 680, idealWidth: 680)
        .frame(maxWidth: .infinity)
        .frame(height: viewModel.outputText.isEmpty ? 140 : 120 + outputHeight)
        .background(
            VisualEffectBackground()
                .ignoresSafeArea()
        )
        .sheet(isPresented: $viewModel.showSudoPrompt) {
            SudoPrompt(
                onSubmit: { password in
                    viewModel.showSudoPrompt = false
                    viewModel.execute(password: password)
                },
                onCancel: {
                    viewModel.showSudoPrompt = false
                }
            )
        }
    }

    private func handleEnter() {
        guard !viewModel.inputText.isEmpty else { return }
        if viewModel.useSudo {
            viewModel.showSudoPrompt = true
        } else {
            viewModel.execute()
        }
    }
}

private struct HeightMeasurer: View {
    let text: String
    @Binding var height: CGFloat

    var body: some View {
        GeometryReader { _ in
            Color.clear
                .onChange(of: text) { _ in update() }
                .onAppear { update() }
        }
    }

    private func update() {
        let lines = text.components(separatedBy: "\n").count
        let h = min(max(CGFloat(lines) * 20 + 20, 60), 220)
        withAnimation(.easeInOut(duration: 0.15)) { height = h }
    }
}

struct VisualEffectBackground: NSViewRepresentable {
    func makeNSView(context: Context) -> NSVisualEffectView {
        let v = NSVisualEffectView()
        v.material = .hudWindow
        v.blendingMode = .behindWindow
        v.state = .active
        return v
    }
    func updateNSView(_ nsView: NSVisualEffectView, context: Context) {}
}

private struct SuggestionList: View {
    @ObservedObject var viewModel: LauncherViewModel

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(Array(viewModel.suggestions.enumerated()), id: \.element.id) { idx, s in
                        SuggestionRowView(suggestion: s, isSelected: idx == viewModel.selectedIndex)
                            .onTapGesture {
                                viewModel.selectedIndex = idx
                                viewModel.confirmSelection()
                            }
                    }
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 6)
            }
            .frame(maxHeight: 180)
        }
    }
}

private struct SuggestionRowView: View {
    let suggestion: Suggestion
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: suggestion.icon)
                .font(.system(size: 12))
                .foregroundColor(isSelected ? .white : .secondary)
                .frame(width: 16)

            Text(suggestion.text)
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(isSelected ? .white : .primary)
                .lineLimit(1)

            Spacer()

            if let count = suggestion.historyCount, count > 1 {
                Text("\(count)×")
                    .font(.system(size: 10))
                    .foregroundColor(isSelected ? .white.opacity(0.7) : .secondary)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 5)
                .fill(isSelected ? Color.accentColor : Color.clear)
        )
        .contentShape(Rectangle())
    }
}
