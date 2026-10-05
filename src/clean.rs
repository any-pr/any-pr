//! Port of `clean.py`: clears the terminal via ANSI/VT escape sequences.
//!
//! - `\x1b[3J`: clear scrollback buffer
//! - `\x1b[2J`: clear the visible screen
//! - `\x1b[H`:  move the cursor to the top-left corner
//!
//! `flush()` ensures the sequence is emitted immediately.

use std::io::Write;

fn main() {
    print!("\x1b[3J\x1b[2J\x1b[H");
    std::io::stdout().flush().expect("stdout flush failed");
}
