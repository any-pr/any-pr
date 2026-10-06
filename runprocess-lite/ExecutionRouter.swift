//
//  ExecutionRouter.swift
//  RunProcess
//

import Foundation

/// 根据「是否 sudo / 是否会话模式」路由到对应执行器
enum ExecutionRouter {

    static func route(
        command: String,
        useSudo: Bool,
        password: String?,
        usesSessionMode: Bool,
        shell: PersistentShell?,
        sudoAuth: SudoAuthManager,
        timeout: TimeInterval,
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        if useSudo {
            guard let password else {
                completion(.failure(NSError(
                    domain: "RunProcess", code: -1,
                    userInfo: [NSLocalizedDescriptionKey:
                        NSLocalizedString("error.sudo.missing.password", comment: "")])))
                return
            }
            sudoAuth.executeSudo(command, password: password,
                                 timeout: timeout, completion: completion)
        } else if usesSessionMode, let shell {
            shell.execute(command, timeout: timeout) { result in
                switch result {
                case .success(let r): completion(.success(r.output))
                case .failure(let e): completion(.failure(e))
                }
            }
        } else {
            CommandExecutor.shared.execute(command, timeout: timeout,
                                           completion: completion)
        }
    }
}