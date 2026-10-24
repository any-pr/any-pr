import SwiftUI
import AppKit

struct CommandField: NSViewRepresentable {
    @Binding var text: String
    let onTab: () -> Void
    let onEnter: () -> Void
    let onUp: () -> String?
    let onDown: () -> String?
    let onEscape: () -> Void
    let focusTrigger: Int

    func makeNSView(context: Context) -> NSScrollView {
        let scrollView = NSScrollView()
        scrollView.hasVerticalScroller = true
        scrollView.autohidesScrollers = true
        scrollView.drawsBackground = false
        scrollView.borderType = .noBorder

        let tv = FieldTextView()
        tv.delegate = context.coordinator
        tv.font = NSFont.monospacedSystemFont(ofSize: 18, weight: .regular)
        tv.isRichText = false
        tv.isEditable = true
        tv.isSelectable = true
        tv.drawsBackground = false
        tv.backgroundColor = .clear
        tv.textContainer?.lineFragmentPadding = 0
        tv.textContainer?.widthTracksTextView = true
        tv.autoresizingMask = [.width]

        let dynamicColor = NSColor(name: nil) { appearance in
            appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua ? .white : .black
        }
        tv.textColor = dynamicColor
        tv.insertionPointColor = dynamicColor

        tv.registerForDraggedTypes([.fileURL])

        let style = NSMutableParagraphStyle()
        style.minimumLineHeight = 28
        style.maximumLineHeight = 28
        tv.defaultParagraphStyle = style
        tv.typingAttributes = [
            .font: NSFont.monospacedSystemFont(ofSize: 18, weight: .regular),
            .paragraphStyle: style,
            .foregroundColor: dynamicColor
        ]

        scrollView.documentView = tv
        scrollView.contentInsets = NSEdgeInsets(top: 8, left: 0, bottom: 8, right: 0)
        context.coordinator.textView = tv

        DispatchQueue.main.async {
            tv.window?.makeFirstResponder(tv)
        }
        return scrollView
    }

    func updateNSView(_ nsView: NSScrollView, context: Context) {
        guard let tv = nsView.documentView as? FieldTextView else { return }
        if tv.string != text { tv.string = text }

        context.coordinator.lastFocusTrigger = focusTrigger
        if context.coordinator.lastHandledFocus != focusTrigger {
            context.coordinator.lastHandledFocus = focusTrigger
            DispatchQueue.main.async {
                tv.window?.makeFirstResponder(tv)
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: CommandField
        weak var textView: FieldTextView?
        var lastFocusTrigger: Int = 0
        var lastHandledFocus: Int = -1

        init(_ parent: CommandField) { self.parent = parent }

        func textDidChange(_ notification: Notification) {
            guard let tv = textView else { return }
            parent.text = tv.string
        }

        func textView(_ textView: NSTextView, doCommandBy selector: Selector) -> Bool {
            switch selector {
            case #selector(NSResponder.insertNewline(_:)):
                if let event = NSApp.currentEvent,
                   event.modifierFlags.contains(.shift) {
                    insertNewline(textView)
                    return true
                }
                parent.onEnter()
                return true

            case #selector(NSResponder.insertTab(_:)):
                parent.onTab()
                return true

            case #selector(NSResponder.moveUp(_:)):
                let pos = textView.selectedRange.location
                if pos == 0 {
                    if let cmd = parent.onUp() {
                        setText(cmd, in: textView)
                    }
                    return true
                }
                return false

            case #selector(NSResponder.moveDown(_:)):
                let ns = textView.string as NSString
                if textView.selectedRange.location == ns.length {
                    if let cmd = parent.onDown() {
                        setText(cmd, in: textView)
                    }
                    return true
                }
                return false

            case #selector(NSResponder.cancelOperation(_:)):
                parent.onEscape()
                return true

            default:
                return false
            }
        }

        private func setText(_ s: String, in tv: NSTextView) {
            tv.string = s
            parent.text = s
            tv.selectedRange = NSRange(location: (s as NSString).length, length: 0)
        }

        private func insertNewline(_ tv: NSTextView) {
            let ns = tv.string as NSString
            let range = tv.selectedRange
            let new = ns.replacingCharacters(in: range, with: "\n")
            tv.string = new
            tv.selectedRange = NSRange(location: range.location + 1, length: 0)
            parent.text = new
        }
    }
}

final class FieldTextView: NSTextView {
    override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
        guard let urls = sender.draggingPasteboard.readObjects(
            forClasses: [NSURL.self],
            options: [.urlReadingFileURLsOnly: true]
        ) as? [URL], !urls.isEmpty else { return false }

        let quoted = urls.map { shellQuote($0.path) }.joined(separator: " ")
        let ns = string as NSString
        let range = selectedRange
        let new = ns.replacingCharacters(in: range, with: quoted)
        string = new
        selectedRange = NSRange(location: range.location + (quoted as NSString).length, length: 0)
        (delegate as? CommandField.Coordinator)?.parent.text = new
        return true
    }

    override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation { .copy }

    private func shellQuote(_ path: String) -> String {
        let safe = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-./~@+")
        if path.unicodeScalars.allSatisfy({ safe.contains($0) }) { return path }
        let escaped = path
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }
}
