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
            // json.dumps emits the short form for these five and
            // \u00xx for every other control character.
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{8}' => out.push_str("\\b"),
            '\u{c}' => out.push_str("\\f"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

fn num(v: f64) -> String {
    // Debug formatting keeps the ".0" suffix like Python's repr/json, but
    // writes the exponent without a sign and without zero padding: "1e20"
    // where Python writes "1e+20", and "1e-5" where Python writes "1e-05".
    // Both are valid JSON, but the canonical form is what the fingerprint
    // hashes, so a jump range or route distance that lands in exponent
    // form made this port disagree with the Python one it documents itself
    // as matching. Normalise the exponent: always signed, at least two
    // digits.
    let text = format!("{:?}", v);
    match text.split_once('e') {
        Some((mantissa, exponent)) => {
            let (sign, digits) = match exponent.strip_prefix('-') {
                Some(d) => ("-", d),
                None => ("+", exponent.strip_prefix('+').unwrap_or(exponent)),
            };
            format!("{}e{}{:0>2}", mantissa, sign, digits)
        }
        None => text,
    }
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

#[cfg(test)]
mod tests {
    use super::num;

    /// Values Python renders without an exponent must come out untouched.
    #[test]
    fn plain_values_match_python_repr() {
        assert_eq!(num(30.0), "30.0");
        assert_eq!(num(0.0), "0.0");
        assert_eq!(num(-2.5), "-2.5");
        assert_eq!(num(0.0001), "0.0001");
        assert_eq!(num(1e15), "1000000000000000.0");
    }

    /// Python always signs the exponent and pads it to two digits:
    /// repr(1e20) is "1e+20" and repr(1e-5) is "1e-05".
    #[test]
    fn exponent_matches_python_repr() {
        assert_eq!(num(1e20), "1e+20");
        assert_eq!(num(1e-5), "1e-05");
        assert_eq!(num(1e300), "1e+300");
        assert_eq!(num(1e-300), "1e-300");
        assert_eq!(num(-1e-5), "-1e-05");
        assert_eq!(num(1e16), "1e+16");
    }

    /// Route distances are rounded to three decimals before reaching num(),
    /// so they stay out of exponent form.
    #[test]
    fn rounded_distances_stay_plain() {
        assert_eq!(num((12.3456_f64 * 1000.0).round() / 1000.0), "12.346");
    }
}
