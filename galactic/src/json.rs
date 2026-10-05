//! Minimal canonical JSON writer for mission reports, dependency-free.
//! Keys are emitted in sorted order with no insignificant whitespace,
//! matching `json.dumps(sort_keys=True, separators=(",", ":"))`.

use crate::{Report, Route, Star};

pub fn esc(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    for ch in s.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

fn num(v: f64) -> String {
    // Debug formatting keeps the ".0" suffix like Python's repr/json.
    format!("{:?}", v)
}

fn star(s: &Star) -> String {
    format!(
        "{{\"kind\":\"{}\",\"name\":\"{}\",\"x\":{},\"y\":{}}}",
        esc(s.kind), esc(&s.name), s.x, s.y
    )
}

fn route(r: &Route) -> String {
    let path = r.path.iter().map(|p| format!("\"{}\"", esc(p))).collect::<Vec<_>>().join(",");
    format!("{{\"distance\":{},\"hops\":{},\"path\":[{}]}}", num(r.distance), r.hops, path)
}

/// Canonical JSON of every report field except `fingerprint`.
pub fn canonical(r: &Report) -> String {
    let stars = r.stars.iter().map(star).collect::<Vec<_>>().join(",");
    let route = r.route.as_ref().map(route).unwrap_or_else(|| "null".into());
    format!(
        "{{\"destination\":\"{}\",\"jump_range\":{},\"origin\":\"{}\",\"route\":{},\"seed\":\"{}\",\"stars\":[{}],\"version\":{}}}",
        esc(&r.destination), num(r.jump_range), esc(&r.origin), route, esc(&r.seed), stars, r.version
    )
}

/// Same report as canonical JSON including `fingerprint`, pretty-printed
/// with two-space indent (approximates `json.dumps(indent=2)`).
pub fn pretty(r: &Report) -> String {
    let mut compact = canonical(r);
    compact.pop(); // strip trailing '}'
    compact.push_str(&format!(",\"fingerprint\":\"{}\"}}", esc(&r.fingerprint)));
    let mut out = String::with_capacity(compact.len() * 2);
    let mut depth = 0usize;
    let mut in_string = false;
    let mut escaped = false;
    for c in compact.chars() {
        if in_string {
            out.push(c);
            match (escaped, c) {
                (true, _) => escaped = false,
                (false, '\\') => escaped = true,
                (false, '"') => in_string = false,
                _ => {}
            }
            continue;
        }
        match c {
            '"' => {
                in_string = true;
                out.push(c);
            }
            '{' | '[' => {
                out.push(c);
                depth += 1;
                out.push('\n');
                out.push_str(&"  ".repeat(depth));
            }
            '}' | ']' => {
                depth = depth.saturating_sub(1);
                out.push('\n');
                out.push_str(&"  ".repeat(depth));
                out.push(c);
            }
            ',' => {
                out.push(',');
                out.push('\n');
                out.push_str(&"  ".repeat(depth));
            }
            ':' => out.push_str(": "),
            c => out.push(c),
        }
    }
    out
}
