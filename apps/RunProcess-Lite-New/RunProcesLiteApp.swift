import SwiftUI
import AppKit
import Carbon.HIToolbox

@main
struct RunProcessLiteApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        Settings { EmptyView() }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem?
    private var window: LauncherWindow?
    private var hotKey: HotKey?
    private var keyMonitor: Any?
    private var hasBeenActive = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        setupStatusBar()
        setupHotKey()
        setupKeyMonitor()
        setupActiveObserver()

        window = LauncherWindow()
        window?.show()
        NSApp.activate(ignoringOtherApps: true)
        hasBeenActive = true
    }

    func applicationWillTerminate(_ notification: Notification) {
        NotificationCenter.default.removeObserver(self)
        if let m = keyMonitor { NSEvent.removeMonitor(m) }
    }

    private func setupStatusBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        guard let button = statusItem?.button else { return }
        let image = NSImage(systemSymbolName: "bolt.fill", accessibilityDescription: "RunProcess-Lite")
        image?.isTemplate = true
        button.image = image
        button.action = #selector(toggleWindow)
        button.target = self
    }

    private func setupHotKey() {
        hotKey = HotKey(keyCode: UInt32(kVK_ANSI_R),
                        modifiers: UInt32(cmdKey | optionKey)) { [weak self] in
            DispatchQueue.main.async { self?.toggleWindow() }
        }
    }

    private func setupKeyMonitor() {
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            guard let self = self else { return event }
            let flags = event.modifierFlags.intersection(.deviceIndependentFlagsMask)
            if flags == .command, event.charactersIgnoringModifiers?.lowercased() == "w" {
                self.window?.hide()
                return nil
            }
            if flags == .command, event.charactersIgnoringModifiers?.lowercased() == "q" {
                NSApp.terminate(nil)
                return nil
            }
            return event
        }
    }

    private func setupActiveObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidResignActive),
            name: NSApplication.didResignActiveNotification,
            object: nil
        )
    }

    @objc private func appDidResignActive() {
        guard hasBeenActive else { return }
        window?.hide()
    }

    @objc private func toggleWindow() {
        guard let window = window else { return }
        if window.isVisible {
            window.hide()
        } else {
            window.show()
            NSApp.activate(ignoringOtherApps: true)
            hasBeenActive = true
        }
    }
}
