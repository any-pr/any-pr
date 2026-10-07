//
//  AppSettings.swift
//  RunProcess
//
//  Created by Haoran on 2026/9/13.
//

import Foundation

/// 外观风格
enum AppearanceStyle: String, CaseIterable {
    case none
    case frostedGlass
    case liquidGlass

    var displayNameKey: String {
        switch self {
        case .none: return "appearance.none"
        case .frostedGlass: return "appearance.frostedGlass"
        case .liquidGlass: return "appearance.liquidGlass"
        }
    }

    var isSupported: Bool {
        switch self {
        case .none, .frostedGlass:
            return true
        case .liquidGlass:
            if #available(macOS 26.0, *) { return true }
            return false
        }
    }
}

/// 全局设置，封装 UserDefaults
///
/// 注意：不要叫 `Settings`，会与 SwiftUI 的 `Settings` 场景类型冲突。
enum AppSettings {

    // MARK: - Keys

    enum Keys {
        enum Session {
            static let enabled = "session.enabled"
        }
        enum WorkingDirectory {
            static let `default` = "workingDirectory.default"
        }
        enum Appearance {
            static let style = "appearance.style"
        }
        enum Window {
            static let hideOnDeactivate = "window.hideOnDeactivate"
        }
        enum Sudo {
            static let `default` = "sudo.default"
        }
        enum Startup {
            static let launchAtLogin = "startup.launchAtLogin"
            static let hideOnLaunch = "startup.hideOnLaunch"
        }
        enum Output {
            static let colorsEnabled = "output.colorsEnabled"
            static let colorScheme = "output.colorScheme"
        }

        // MARK: 兼容旧引用（保留后，全项目 @AppStorage 引用零改动）
        static let sessionModeEnabled = Session.enabled
        static let defaultWorkingDirectory = WorkingDirectory.default
        static let appearanceStyle = Appearance.style
        static let hideOnDeactivate = Window.hideOnDeactivate
        static let defaultSudo = Sudo.default
        static let launchAtLogin = Startup.launchAtLogin
        static let hideWindowOnLaunch = Startup.hideOnLaunch
        static let outputColorsEnabled = Output.colorsEnabled
        static let outputColorScheme = Output.colorScheme
    }

    private static let defaults = UserDefaults.standard

    // MARK: - 会话模式

    static var sessionModeEnabled: Bool {
        get { defaults.bool(forKey: Keys.sessionModeEnabled) }
        set { defaults.set(newValue, forKey: Keys.sessionModeEnabled) }
    }

    // MARK: - 默认工作目录

    static var defaultWorkingDirectoryRaw: String {
        get { defaults.string(forKey: Keys.defaultWorkingDirectory) ?? "" }
        set { defaults.set(newValue, forKey: Keys.defaultWorkingDirectory) }
    }

    static var resolvedWorkingDirectory: String {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        let raw = defaultWorkingDirectoryRaw
        guard !raw.isEmpty else { return home }

        let expanded = (raw as NSString).expandingTildeInPath
        var isDirectory: ObjCBool = false
        let exists = FileManager.default.fileExists(atPath: expanded, isDirectory: &isDirectory)

        if exists && isDirectory.boolValue {
            return expanded
        } else {
            return home
        }
    }

    static var isWorkingDirectoryValid: Bool {
        let raw = defaultWorkingDirectoryRaw
        guard !raw.isEmpty else { return true }

        let expanded = (raw as NSString).expandingTildeInPath
        var isDirectory: ObjCBool = false
        let exists = FileManager.default.fileExists(atPath: expanded, isDirectory: &isDirectory)
        return exists && isDirectory.boolValue
    }

    static var displayWorkingDirectory: String {
        let raw = defaultWorkingDirectoryRaw
        guard !raw.isEmpty else { return "" }
        return (raw as NSString).abbreviatingWithTildeInPath
    }

    // MARK: - 外观风格

    static var appearanceStyleRaw: AppearanceStyle {
        get {
            let raw = defaults.string(forKey: Keys.appearanceStyle) ?? ""
            return AppearanceStyle(rawValue: raw) ?? defaultAppearance
        }
        set {
            defaults.set(newValue.rawValue, forKey: Keys.appearanceStyle)
        }
    }

    static var resolvedAppearanceStyle: AppearanceStyle {
        let raw = appearanceStyleRaw
        if raw == .liquidGlass && !AppearanceStyle.liquidGlass.isSupported {
            return .frostedGlass
        }
        return raw
    }

    private static var defaultAppearance: AppearanceStyle {
        if #available(macOS 26.0, *) {
            return .liquidGlass
        }
        return .frostedGlass
    }

    // MARK: - 失焦关闭

    /// 窗口失去焦点（App 不再 active）时是否自动隐藏
    /// 默认 true
    static var hideOnDeactivate: Bool {
        get {
            if defaults.object(forKey: Keys.hideOnDeactivate) == nil {
                return true
            }
            return defaults.bool(forKey: Keys.hideOnDeactivate)
        }
        set { defaults.set(newValue, forKey: Keys.hideOnDeactivate) }
    }

    // MARK: - 默认 Sudo

    /// 默认以 root 执行。开启后主界面 sudo 开关固定为开且不可改。
    /// 默认 false。
    static var defaultSudo: Bool {
        get { defaults.bool(forKey: Keys.defaultSudo) }
        set { defaults.set(newValue, forKey: Keys.defaultSudo) }
    }

    // MARK: - 启动

    /// 开机自启动偏好缓存。实际状态以 SMAppService 为准。
    static var launchAtLogin: Bool {
        get { defaults.bool(forKey: Keys.launchAtLogin) }
        set { defaults.set(newValue, forKey: Keys.launchAtLogin) }
    }

    /// 启动后隐藏窗口，只保留菜单栏图标。
    /// 默认 false。
    static var hideWindowOnLaunch: Bool {
        get { defaults.bool(forKey: Keys.hideWindowOnLaunch) }
        set { defaults.set(newValue, forKey: Keys.hideWindowOnLaunch) }
    }

    // MARK: - 输出颜色

    /// 是否解析并显示 ANSI 颜色
    /// 默认 true
    static var outputColorsEnabled: Bool {
        get {
            if defaults.object(forKey: Keys.outputColorsEnabled) == nil {
                return true
            }
            return defaults.bool(forKey: Keys.outputColorsEnabled)
        }
        set { defaults.set(newValue, forKey: Keys.outputColorsEnabled) }
    }

    /// 输出颜色的色板选择
    enum OutputColorScheme: String, CaseIterable {
        case auto
        case dark
        case light

        var displayNameKey: String {
            switch self {
            case .auto:  return "output.colors.scheme.auto"
            case .dark:  return "output.colors.scheme.dark"
            case .light: return "output.colors.scheme.light"
            }
        }
    }

    static var outputColorScheme: OutputColorScheme {
        get {
            let raw = defaults.string(forKey: Keys.outputColorScheme) ?? ""
            return OutputColorScheme(rawValue: raw) ?? .auto
        }
        set { defaults.set(newValue.rawValue, forKey: Keys.outputColorScheme) }
    }
}