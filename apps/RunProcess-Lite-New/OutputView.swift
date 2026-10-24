import SwiftUI
import AppKit

struct OutputView: NSViewRepresentable {
    let attributed: NSAttributedString
    var onContentWidth: ((CGFloat) -> Void)?

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.hasHorizontalScroller = true
        scroll.autohidesScrollers = true
        scroll.drawsBackground = false
        scroll.borderType = .noBorder

        let tv = ReadOnlyTextView()
        tv.isEditable = false
        tv.isSelectable = true
        tv.isRichText = true
        tv.drawsBackground = false
        tv.backgroundColor = .clear
        tv.textContainerInset = NSSize(width: 0, height: 4)
        tv.textContainer?.lineFragmentPadding = 0

        let inf: CGFloat = .greatestFiniteMagnitude
        tv.textContainer?.widthTracksTextView = false
        tv.textContainer?.containerSize = NSSize(width: inf, height: inf)
        tv.isHorizontallyResizable = true
        tv.isVerticallyResizable = true
        tv.maxSize = NSSize(width: inf, height: inf)
        tv.minSize = .zero
        tv.autoresizingMask = []

        tv.font = OutputFont.regular
        tv.typingAttributes = [
            .font: OutputFont.regular,
            .foregroundColor: NSColor.labelColor
        ]

        scroll.documentView = tv
        return scroll
    }

    func updateNSView(_ nsView: NSScrollView, context: Context) {
        guard let tv = nsView.documentView as? ReadOnlyTextView else { return }
        let incoming = NSMutableAttributedString(attributedString: attributed)

        let full = NSRange(location: 0, length: incoming.length)
        incoming.enumerateAttribute(.font, in: full, options: []) { value, range, _ in
            if value == nil {
                incoming.addAttribute(.font, value: OutputFont.regular, range: range)
            }
        }

        if tv.textStorage?.isEqual(to: incoming) == false {
            tv.textStorage?.setAttributedString(incoming)
            if let cb = onContentWidth {
                let w = Self.measureWidth(incoming)
                DispatchQueue.main.async { cb(w) }
            }
        }
    }

    private static func measureWidth(_ attr: NSAttributedString) -> CGFloat {
        let text = attr.string as NSString
        var maxW: CGFloat = 0
        var start = 0
        while start < text.length {
            var lineEnd = 0
            var contentEnd = 0
            text.getLineStart(&start, end: &lineEnd, contentsEnd: &contentEnd,
                              for: NSRange(location: start, length: 0))
            let range = NSRange(location: start, length: contentEnd - start)
            let line = attr.attributedSubstring(from: range)
            maxW = max(maxW, line.size().width)
            start = lineEnd
        }
        return maxW
    }
}

enum OutputFont {
    static let size: CGFloat = 13

    static var regular: NSFont {
        NSFont(name: "SFMono-Regular", size: size)
            ?? NSFont.monospacedSystemFont(ofSize: size, weight: .regular)
    }
    static var bold: NSFont {
        NSFont(name: "SFMono-Bold", size: size)
            ?? NSFont.monospacedSystemFont(ofSize: size, weight: .bold)
    }
    static var italic: NSFont {
        let base = NSFont.monospacedSystemFont(ofSize: size, weight: .regular)
        let desc = base.fontDescriptor.withSymbolicTraits(.italic)
        return NSFont(descriptor: desc, size: size) ?? base
    }
}

final class ReadOnlyTextView: NSTextView {
    override var acceptsFirstResponder: Bool { false }
    override func becomeFirstResponder() -> Bool { false }

    override func menu(for event: NSEvent) -> NSMenu? {
        let menu = NSMenu()
        menu.addItem(withTitle: "Copy", action: #selector(NSText.copy(_:)), keyEquivalent: "c")
        menu.addItem(withTitle: "Select All", action: #selector(NSText.selectAll(_:)), keyEquivalent: "a")
        return menu
    }
}
