import AppKit
import SwiftUI

final class LauncherWindow {
    private var panel: NSPanel?
    private var viewModel: LauncherViewModel

    private let baseWidth: CGFloat = 680
    private let baseHeight: CGFloat = 160

    var isVisible: Bool { panel?.isVisible ?? false }

    init() {
        let vm = LauncherViewModel()
        let view = LauncherView(viewModel: vm)
        let hosting = NSHostingController(rootView: view)

        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: baseWidth, height: baseHeight),
            styleMask: [.titled, .fullSizeContentView, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.contentViewController = hosting
        panel.titleVisibility = .hidden
        panel.titlebarAppearsTransparent = true
        panel.isMovableByWindowBackground = true
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.isReleasedWhenClosed = false
        panel.hidesOnDeactivate = false
        panel.minSize = NSSize(width: baseWidth, height: 140)
        panel.maxSize = NSSize(width: 1400, height: 800)

        if let screen = NSScreen.main {
            let sf = screen.visibleFrame
            let x = sf.midX - baseWidth / 2
            let y = sf.midY + 100
            panel.setFrameOrigin(NSPoint(x: x, y: y))
        }

        self.viewModel = vm
        self.panel = panel
        vm.window = self
    }

    func show() {
        guard let panel = panel else { return }
        panel.orderFrontRegardless()
        panel.makeKey()
        viewModel.focusRequested += 1
    }

    func hide() {
        panel?.orderOut(nil)
    }

    func setContentWidth(_ width: CGFloat, animated: Bool = true) {
        guard let panel = panel else { return }
        let target = max(width, baseWidth)
        let current = panel.frame.width
        guard abs(current - target) > 1 else { return }

        var frame = panel.frame
        let delta = target - current
        frame.size.width += delta
        frame.origin.x -= delta / 2

        if animated {
            NSAnimationContext.runAnimationGroup { ctx in
                ctx.duration = 0.18
                panel.animator().setFrame(frame, display: true)
            }
        } else {
            panel.setFrame(frame, display: true)
        }
    }
}
