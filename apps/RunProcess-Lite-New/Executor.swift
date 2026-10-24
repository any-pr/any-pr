import Foundation

final class Executor {
    private var currentProcess: Process?
    private let queue = DispatchQueue(label: "com.runprocess-lite.executor")

    func run(command: String, sudo: Bool, password: String?,
             completion: @escaping (Result<String, Error>) -> Void) {
        queue.async { [weak self] in
            guard let self = self else { return }

            let task = Process()
            let stdout = Pipe()
            let stderr = Pipe()

            let shell = ProcessInfo.processInfo.environment["SHELL"] ?? "/bin/zsh"

            if sudo {
                guard let pw = password, !pw.isEmpty else {
                    DispatchQueue.main.async {
                        completion(.failure(NSError(
                            domain: "RunProcess-Lite", code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "Password required"])))
                    }
                    return
                }
                task.launchPath = "/usr/bin/sudo"
                task.arguments = ["-S", "-p", "", shell, "-l", "-c", command]
            } else {
                task.launchPath = shell
                task.arguments = ["-l", "-c", command]
            }

            task.standardOutput = stdout
            task.standardError = stderr

            let stdinPipe = Pipe()
            task.standardInput = stdinPipe

            self.currentProcess = task

            do {
                try task.run()
            } catch {
                self.currentProcess = nil
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }

            if sudo, let pw = password {
                let data = (pw + "\n").data(using: .utf8) ?? Data()
                try? stdinPipe.fileHandleForWriting.write(contentsOf: data)
            }
            try? stdinPipe.fileHandleForWriting.close()

            let deadline = DispatchWorkItem { [weak task] in
                if let t = task, t.isRunning {
                    t.terminate()
                }
            }
            DispatchQueue.global().asyncAfter(deadline: .now() + 30, execute: deadline)

            task.waitUntilExit()
            deadline.cancel()
            self.currentProcess = nil

            let outData = stdout.fileHandleForReading.readDataToEndOfFile()
            let errData = stderr.fileHandleForReading.readDataToEndOfFile()

            var output = String(data: outData, encoding: .utf8) ?? ""
            let errText = String(data: errData, encoding: .utf8) ?? ""

            // sudo 密码错误时，sudo 会往 stderr 写 "Sorry, try again."
            if sudo, errText.contains("Sorry, try again") {
                DispatchQueue.main.async {
                    completion(.failure(NSError(
                        domain: "RunProcess-Lite", code: -2,
                        userInfo: [NSLocalizedDescriptionKey: "Wrong password"])))
                }
                return
            }

            if !errText.isEmpty {
                output += (output.isEmpty ? "" : "\n") + errText.trimmingCharacters(in: .newlines)
            }

            let status = task.terminationStatus
            DispatchQueue.main.async {
                if status == 0 {
                    completion(.success(output))
                } else {
                    let msg = output.isEmpty ? "Exit code: \(status)" : output
                    completion(.failure(NSError(
                        domain: "RunProcess-Lite", code: Int(status),
                        userInfo: [NSLocalizedDescriptionKey: msg])))
                }
            }
        }
    }

    func cancel() {
        queue.async { [weak self] in
            self?.currentProcess?.terminate()
            self?.currentProcess = nil
        }
    }
}
