import SwiftUI

struct SudoPrompt: View {
    let onSubmit: (String) -> Void
    let onCancel: () -> Void

    @State private var password: String = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "lock.shield.fill")
                    .foregroundColor(.orange)
                    .font(.system(size: 18))
                Text("Administrator Privileges Required")
                    .font(.headline)
                Spacer()
            }

            Text("This command requires root privileges. Please enter your password.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)
                .focused($focused)
                .onAppear { focused = true }
                .onSubmit { submit() }

            HStack {
                Image(systemName: "info.circle")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary.opacity(0.6))
                Text("Password is used only in memory and is never stored.")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary.opacity(0.6))
                Spacer()
            }

            HStack(spacing: 12) {
                Spacer()
                Button("Cancel") { onCancel() }
                    .keyboardShortcut(.escape)
                Button("Execute") { submit() }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                    .disabled(password.isEmpty)
            }
        }
        .padding(20)
        .frame(width: 400)
        .background(VisualEffectBackground())
    }

    private func submit() {
        guard !password.isEmpty else { return }
        let pw = password
        password = ""
        onSubmit(pw)
    }
}
