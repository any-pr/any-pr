//! Port of `test_mission.py` to cargo integration tests.

use galactic::{galaxy, mission, render, route, run, Star};

#[test]
fn reproducible_unique_galaxy() {
    let stars = galaxy("epic", 200).unwrap();
    assert_eq!(stars, galaxy("epic", 200).unwrap());
    let coords: std::collections::HashSet<(u32, u32)> =
        stars.iter().map(|s| (s.x, s.y)).collect();
    assert_eq!(coords.len(), 200);
    assert_ne!(stars, galaxy("other", 200).unwrap());
}

#[test]
fn galaxy_calls_are_independent() {
    // The Python suite asserted the global RNG state was untouched;
    // here determinism across calls is the equivalent contract.
    assert_eq!(galaxy("a", 10).unwrap(), galaxy("a", 10).unwrap());
}

#[test]
fn invalid_count() {
    for count in [0usize, 1, 201] {
        assert!(galaxy("x", count).is_err());
    }
}

#[test]
fn shortest_route_and_boundary() {
    let stars = vec![
        Star { name: "a".into(), x: 0, y: 0, kind: "ice" },
        Star { name: "b".into(), x: 3, y: 4, kind: "ice" },
        Star { name: "c".into(), x: 6, y: 8, kind: "ice" },
        Star { name: "detour".into(), x: 0, y: 5, kind: "ice" },
    ];
    let r = route(&stars, "a", "c", 5.0).unwrap().unwrap();
    assert_eq!(r.path, ["a", "b", "c"]);
    assert_eq!(r.distance, 10.0);
    assert_eq!(r.hops, 2);
    assert!(route(&stars, "a", "c", 4.9).unwrap().is_none());
    assert_eq!(route(&stars, "a", "a", 1.0).unwrap().unwrap().hops, 0);
}

#[test]
fn invalid_route_inputs() {
    let stars = galaxy("any-pr", 24).unwrap();
    for jump in [0.0, -1.0, f64::INFINITY, f64::NAN] {
        assert!(route(&stars, "S000", "S001", jump).is_err());
    }
    assert!(route(&stars, "missing", "S001", 30.0).is_err());
    let mut dup = stars.clone();
    dup.extend(stars.iter().cloned());
    assert!(route(&dup, "S000", "S001", 30.0).is_err());
}

#[test]
fn report_fingerprint() {
    let data = mission("epic", 24, "S000", None, 150.0).unwrap();
    assert_eq!(data.fingerprint, mission("epic", 24, "S000", None, 150.0).unwrap().fingerprint);
    assert_eq!(data.fingerprint.len(), 64);
    assert_ne!(data.fingerprint, mission("other", 24, "S000", None, 30.0).unwrap().fingerprint);
    assert!(data.route.is_some());
}

#[test]
fn chart_dimensions_and_highlight() {
    let stars = galaxy("any-pr", 24).unwrap();
    let chart = render(&stars, &["S000".to_owned()], 30, 10).unwrap();
    assert_eq!(chart.lines().count(), 12);
    assert!(chart.lines().all(|l| l.chars().count() == 32));
    assert!(chart.contains('@'));
    assert!(render(&stars, &[], 1, 10).is_err());
}

#[test]
fn cli_json_and_exit_codes() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    let (out, code) = run(&args(&["--json", "--jump", "150"])).unwrap();
    assert_eq!(code, 0);
    assert!(out.contains("\"version\": 1"));
    let (_, code) = run(&args(&["--jump", "0.001"])).unwrap();
    assert_eq!(code, 2);
    let (_, code) = run(&args(&["--destination", "S000"])).unwrap();
    assert_eq!(code, 0);
}

#[test]
fn json_mode_exit_code_tracks_reachability() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    // GUIDE.md: exit code is 0 for a completed mission, 2 for an
    // unreachable one. --json selects an output format; it does not
    // change the outcome of the mission.
    let (out, code) = run(&args(&["--json", "--jump", "0.001"])).unwrap();
    assert_eq!(code, 2, "unreachable mission must exit 2 in --json mode");
    assert!(out.contains("\"route\": null"));
    let (_, code) = run(&args(&["--json", "--jump", "150"])).unwrap();
    assert_eq!(code, 0);
}

#[test]
fn json_escapes_match_python_dumps() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    // json.rs documents that it matches json.dumps. Python emits the
    // short escape for backspace, tab, newline, form feed and carriage
    // return, and \u00xx for every other control character.
    let seed = "a\tb\rc\u{8}d\u{c}e";
    let (out, _) = run(&args(&["--json", "--seed", seed, "--jump", "150"])).unwrap();
    assert!(
        out.contains("a\\tb\\rc\\bd\\fe"),
        "control characters must use the short escapes: {}",
        out.lines().find(|l| l.contains("seed")).unwrap_or("")
    );
    assert!(!out.contains("\\u0009"));

    // A control character with no short form keeps \u00xx.
    let (out, _) = run(&args(&["--json", "--seed", "\u{1}", "--jump", "150"])).unwrap();
    assert!(out.contains("\\u0001"));
}

#[test]
fn json_pretty_keeps_structure_chars_inside_strings() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    // A seed may contain any character, including JSON structure
    // characters. They are legal inside a string literal and must not
    // be treated as structure by the pretty-printer.
    let seed = "a,b:c{d}e[f]g";
    let (out, _) = run(&args(&["--json", "--seed", seed, "--jump", "150"])).unwrap();
    assert!(out.lines().any(|l| l == format!("  \"seed\": \"{}\",", seed)));
    // Braces must stay balanced across the whole document.
    let opens = out.chars().filter(|c| *c == '{').count();
    let closes = out.chars().filter(|c| *c == '}').count();
    assert_eq!(opens, closes);
}

#[test]
fn json_pretty_survives_unbalanced_seed() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    // A closing brace inside a string used to drive the indent depth
    // below zero, panicking in debug builds and allocating wildly in
    // release builds.
    for seed in ["}", "]", "}{", "\"quote\"", "\\"] {
        let (out, _) = run(&args(&["--json", "--seed", seed, "--jump", "150"])).unwrap();
        assert!(out.contains("\"seed\":"));
    }
}

#[test]
fn help_flag_prints_usage_and_exits_zero() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    for flag in ["--help", "-h"] {
        let (out, code) = run(&args(&[flag])).unwrap();
        assert_eq!(code, 0, "{} must exit 0", flag);
        assert!(out.contains("--seed"), "usage must list options: {}", out);
        assert!(out.contains("--json"), "usage must list options: {}", out);
    }
    // Asking for help is never an error, even next to a bad argument.
    let (_, code) = run(&args(&["--bogus", "--help"])).unwrap();
    assert_eq!(code, 0);
}

#[test]
fn cli_invalid_input() {
    let args = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
    assert!(run(&args(&["--stars", "1"])).is_err());
    assert!(run(&args(&["--bogus"])).is_err());
}
