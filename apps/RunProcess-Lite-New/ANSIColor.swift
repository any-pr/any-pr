import SwiftUI
import AppKit

enum ANSIColor {
    static func parse(_ input: String) -> NSAttributedString {
        let result = NSMutableAttributedString()
        var state = State()
        var buffer = ""
        var bufferState = state

        func flush() {
            guard !buffer.isEmpty else { return }
            result.append(piece(buffer, state: bufferState))
            buffer = ""
        }

        var i = input.startIndex
        while i < input.endIndex {
            let ch = input[i]

            if ch == "\u{1B}" {
                let next = input.index(after: i)
                if next < input.endIndex, input[next] == "[" {
                    var j = input.index(after: next)
                    var params = ""
                    var found = false
                    while j < input.endIndex {
                        let c = input[j]
                        if c == "m" { found = true; break }
                        if c.isNumber || c == ";" {
                            params.append(c)
                            j = input.index(after: j)
                        } else { break }
                    }
                    if found {
                        flush()
                        applySGR(params, to: &state)
                        bufferState = state
                        i = input.index(after: j)
                        continue
                    }
                }
                i = input.index(after: i)
                continue
            }

            if buffer.isEmpty { bufferState = state }
            buffer.append(ch)
            i = input.index(after: i)
        }
        flush()
        return result
    }

    private struct State {
        var fg: Color?
        var bg: Color?
        var bold = false
        var italic = false
        var underline = false
        var dim = false
        var reversed = false

        mutating func reset() { self = State() }
    }

    private enum Color {
        case std(Int, bright: Bool)
        case indexed(Int)
        case rgb(Int, Int, Int)

        var nsColor: NSColor {
            switch self {
            case .std(let n, let b):
                return Palette.standard(n: n, bright: b)
            case .indexed(let i):
                return Palette.indexed(i)
            case .rgb(let r, let g, let b):
                return NSColor(red: CGFloat(r)/255, green: CGFloat(g)/255,
                               blue: CGFloat(b)/255, alpha: 1)
            }
        }
    }

    private static func piece(_ text: String, state: State) -> NSAttributedString {
        var attrs: [NSAttributedString.Key: Any] = [:]

        var font: NSFont = OutputFont.regular
        if state.bold { font = OutputFont.bold }
        if state.italic { font = OutputFont.italic }
        attrs[.font] = font

        let fgColor: NSColor
        let bgColor: NSColor?

        if state.reversed {
            fgColor = state.bg?.nsColor ?? Palette.defaultBg
            bgColor = state.fg?.nsColor ?? Palette.defaultFg
        } else {
            fgColor = state.fg?.nsColor ?? Palette.defaultFg
            bgColor = state.bg?.nsColor
        }

        attrs[.foregroundColor] = state.dim ? fgColor.withAlphaComponent(0.6) : fgColor
        if let bg = bgColor { attrs[.backgroundColor] = bg }
        if state.underline { attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue }

        return NSAttributedString(string: text, attributes: attrs)
    }

    private static func applySGR(_ params: String, to state: inout State) {
        let codes: [Int] = params.isEmpty
            ? [0]
            : params.split(separator: ";", omittingEmptySubsequences: false).map { Int($0) ?? 0 }

        var i = 0
        while i < codes.count {
            let c = codes[i]
            switch c {
            case 0: state.reset()
            case 1: state.bold = true
            case 2: state.dim = true
            case 3: state.italic = true
            case 4: state.underline = true
            case 7: state.reversed = true
            case 22: state.bold = false; state.dim = false
            case 23: state.italic = false
            case 24: state.underline = false
            case 27: state.reversed = false
            case 30...37: state.fg = .std(c - 30, bright: false)
            case 39: state.fg = nil
            case 40...47: state.bg = .std(c - 40, bright: false)
            case 49: state.bg = nil
            case 90...97: state.fg = .std(c - 90, bright: true)
            case 100...107: state.bg = .std(c - 100, bright: true)
            case 38, 48:
                if i + 1 < codes.count {
                    let mode = codes[i + 1]
                    if mode == 5, i + 2 < codes.count {
                        let col = Color.indexed(codes[i + 2])
                        if c == 38 { state.fg = col } else { state.bg = col }
                        i += 2
                    } else if mode == 2, i + 4 < codes.count {
                        let col = Color.rgb(codes[i + 2], codes[i + 3], codes[i + 4])
                        if c == 38 { state.fg = col } else { state.bg = col }
                        i += 4
                    }
                }
            default: break
            }
            i += 1
        }
    }

    private enum Palette {
        static var defaultFg: NSColor {
            NSColor(name: nil) { appearance in
                appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
                    ? NSColor(white: 0.92, alpha: 1)
                    : NSColor(white: 0.12, alpha: 1)
            }
        }
        static var defaultBg: NSColor {
            NSColor(name: nil) { appearance in
                appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
                    ? NSColor(white: 0.08, alpha: 1)
                    : NSColor(white: 0.98, alpha: 1)
            }
        }

        static func standard(n: Int, bright: Bool) -> NSColor {
            let idx = min(max(n, 0), 7)
            return bright ? brightColors[idx] : normalColors[idx]
        }

        static func indexed(_ n: Int) -> NSColor {
            if n < 0 { return .labelColor }
            if n < 8 { return standard(n: n, bright: false) }
            if n < 16 { return standard(n: n - 8, bright: true) }
            if n < 232 {
                let i = n - 16
                let r = CGFloat((i / 36) % 6) / 5.0
                let g = CGFloat((i / 6) % 6) / 5.0
                let b = CGFloat(i % 6) / 5.0
                return NSColor(red: r, green: g, blue: b, alpha: 1)
            }
            let v = CGFloat(n - 232) / 23.0
            return NSColor(white: v, alpha: 1)
        }

        private static let normalColors: [NSColor] = [
            NSColor(red: 0.30, green: 0.30, blue: 0.30, alpha: 1),
            NSColor(red: 0.85, green: 0.30, blue: 0.30, alpha: 1),
            NSColor(red: 0.35, green: 0.75, blue: 0.35, alpha: 1),
            NSColor(red: 0.85, green: 0.70, blue: 0.30, alpha: 1),
            NSColor(red: 0.35, green: 0.55, blue: 0.90, alpha: 1),
            NSColor(red: 0.75, green: 0.45, blue: 0.80, alpha: 1),
            NSColor(red: 0.35, green: 0.75, blue: 0.80, alpha: 1),
            NSColor(red: 0.80, green: 0.80, blue: 0.80, alpha: 1),
        ]

        private static let brightColors: [NSColor] = [
            NSColor(red: 0.50, green: 0.50, blue: 0.50, alpha: 1),
            NSColor(red: 1.00, green: 0.45, blue: 0.45, alpha: 1),
            NSColor(red: 0.50, green: 0.95, blue: 0.50, alpha: 1),
            NSColor(red: 1.00, green: 0.90, blue: 0.50, alpha: 1),
            NSColor(red: 0.50, green: 0.75, blue: 1.00, alpha: 1),
            NSColor(red: 0.90, green: 0.65, blue: 1.00, alpha: 1),
            NSColor(red: 0.50, green: 0.95, blue: 1.00, alpha: 1),
            NSColor(red: 1.00, green: 1.00, blue: 1.00, alpha: 1),
        ]
    }
}
