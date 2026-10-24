# RunProcess-Lite

A minimal Spotlight-style command launcher for macOS. Menu bar app, zero dependencies.

## Features

- Run any shell command from a floating window, with live output and ANSI colors
- Tab completion for history, PATH commands, and file paths
- ↑ / ↓ history navigation ranked by frecency
- Drag files from Finder to insert escaped paths
- Optional root execution via `sudo`
- Global hotkey `⌘⌥R` to show/hide

## Requirements

- macOS 13.0 or later
- Apple Silicon or Intel

## Build

```
git clone <repo>
cd RunProcess-Lite
open RunProcess-Lite.xcodeproj
```

Select the `RunProcess-Lite` scheme and run (`⌘R`). Look for the ⚡ icon in the menu bar.

## Shortcuts

| Key | Action |
|-----|--------|
| `⌘⌥R` | Show / hide window |
| `Enter` | Execute |
| `Shift+Enter` | Newline |
| `Tab` | Completion |
| `↑` `↓` | History |
| `⌘W` | Hide |
| `⌘Q` | Quit |

## Files

```
RunProcessLiteApp.swift   App entry, menu bar, hotkey
LauncherWindow.swift      NSPanel wrapper
LauncherViewModel.swift   State and command flow
LauncherView.swift        Main SwiftUI view
CommandField.swift        NSTextView input
OutputView.swift          Read-only output view
Executor.swift            Process execution
History.swift             Frecency history (JSON)
ANSIColor.swift           ANSI parser
Completer.swift           Tab completion
HotKey.swift              Carbon global hotkey
SudoPrompt.swift          Password dialog
```

## License

MIT
