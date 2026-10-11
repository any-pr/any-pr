use std::{collections::HashMap, env, io::Write, process::{Command, Stdio}};

#[derive(Debug, PartialEq)]
struct Source {
    path: Vec<u8>,
    language: usize,
    bytes: u64,
}

#[derive(Default)]
struct Stats {
    files: u64,
    bytes: u64,
    excluded_files: u64,
    excluded_bytes: u64,
}

fn percentage(part: u64, whole: u64) -> f64 {
    if whole == 0 { 0.0 } else { 100.0 * part as f64 / whole as f64 }
}

fn nul_fields<'a>(data: &'a [u8], command: &str) -> Result<Vec<&'a [u8]>, String> {
    if !data.is_empty() && !data.ends_with(&[0]) {
        return Err(format!("unterminated git {command} output"));
    }
    let mut fields: Vec<_> = data.split(|b| *b == 0).collect();
    fields.pop();
    Ok(fields)
}

fn tree_sources(data: &[u8]) -> Result<Vec<Source>, String> {
    let mut sources = Vec::new();
    for entry in nul_fields(data, "ls-tree")? {
        let tab = entry.iter().position(|b| *b == b'\t')
            .ok_or("missing path separator in git ls-tree output")?;
        let metadata = std::str::from_utf8(&entry[..tab])
            .map_err(|_| "invalid git ls-tree metadata")?;
        let fields: Vec<_> = metadata.split_whitespace().collect();
        let path = &entry[tab + 1..];
        if fields.len() != 4 || path.is_empty() {
            return Err("malformed git ls-tree entry".into());
        }
        if !matches!(fields[0], "100644" | "100755") || fields[1] != "blob" {
            continue;
        }
        let bytes = fields[3].parse().map_err(|_| "invalid blob size")?;
        let language = if path.ends_with(b".swift") || path.ends_with(b".swift.gyb") {
            0
        } else if path.ends_with(b".rs") {
            1
        } else {
            continue;
        };
        sources.push(Source { path: path.to_vec(), language, bytes });
    }
    Ok(sources)
}

fn exclusions(data: &[u8]) -> Result<HashMap<Vec<u8>, bool>, String> {
    let fields = nul_fields(data, "check-attr")?;
    if fields.len() % 3 != 0 {
        return Err("malformed git check-attr output".into());
    }
    let mut flags: HashMap<Vec<u8>, [Option<bool>; 2]> = HashMap::new();
    for triple in fields.chunks_exact(3) {
        if triple[0].is_empty() {
            return Err("empty attribute path".into());
        }
        let index = match triple[1] {
            b"linguist-vendored" => 0,
            b"linguist-generated" => 1,
            _ => return Err("unexpected attribute name".into()),
        };
        let entry = flags.entry(triple[0].to_vec()).or_insert([None, None]);
        if entry[index].is_some() {
            return Err("duplicate attribute result".into());
        }
        entry[index] = Some(matches!(triple[2], b"set" | b"true"));
    }
    flags.into_iter().map(|(path, values)| {
        match values {
            [Some(vendored), Some(generated)] => Ok((path, vendored || generated)),
            _ => Err("incomplete attribute results".into()),
        }
    }).collect()
}

fn git(repo: &std::ffi::OsStr, args: &[&str], input: &[u8]) -> Result<Vec<u8>, String> {
    let mut child = Command::new("git").arg("-C").arg(repo)
        .args(["-c", "core.fsmonitor=false"]).args(args)
        .stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped())
        .spawn().map_err(|e| format!("cannot start git: {e}"))?;
    let mut stdin = child.stdin.take().unwrap();
    let input = input.to_vec();
    let writer = std::thread::spawn(move || stdin.write_all(&input));
    let output = child.wait_with_output().map_err(|e| format!("cannot wait for git: {e}"))?;
    let write_result = writer.join().map_err(|_| "Git input writer panicked")?;
    if !output.status.success() {
        return Err(format!("git {} failed: {}", args[0],
            String::from_utf8_lossy(&output.stderr).trim()));
    }
    write_result.map_err(|e| format!("cannot send paths to git: {e}"))?;
    Ok(output.stdout)
}

fn run() -> Result<(), String> {
    let args: Vec<_> = env::args_os().skip(1).collect();
    if args.len() == 1 && matches!(args[0].to_str(), Some("--help" | "-h")) {
        println!("Usage: language-balance [repository-path]\nAudits Swift/Rust HEAD bytes using current Git exclusion attributes.");
        return Ok(());
    }
    if args.len() > 1 {
        return Err("usage: language-balance [repository-path]".into());
    }
    let repo = args.first().map(|s| s.as_os_str()).unwrap_or(std::ffi::OsStr::new("."));
    let sources = tree_sources(&git(repo, &["ls-tree", "-rlz", "HEAD"], &[])?)?;
    let mut paths = Vec::new();
    for source in &sources {
        paths.extend_from_slice(&source.path);
        paths.push(0);
    }
    let attrs = if paths.is_empty() { HashMap::new() } else {
        exclusions(&git(repo, &["check-attr", "--stdin", "-z",
            "linguist-vendored", "linguist-generated"], &paths)?)?
    };
    let mut stats = [Stats::default(), Stats::default()];
    for source in sources {
        let excluded = *attrs.get(&source.path).ok_or("missing path attribute results")?;
        let total = &mut stats[source.language];
        total.files += 1;
        total.bytes += source.bytes;
        if excluded {
            total.excluded_files += 1;
            total.excluded_bytes += source.bytes;
        }
    }
    println!("Swift/Rust source audit: HEAD blob bytes, current Git attributes.");
    for (name, total) in ["Swift", "Rust"].iter().zip(stats) {
        println!("{name}: total {} files / {} bytes; counted {} files / {} bytes; excluded {} files / {} bytes ({:.2}%)",
            total.files, total.bytes, total.files - total.excluded_files,
            total.bytes - total.excluded_bytes, total.excluded_files,
            total.excluded_bytes, percentage(total.excluded_bytes, total.bytes));
    }
    println!("Counts apply only explicit vendored/generated attributes; this is not a full Linguist percentage prediction.");
    Ok(())
}

fn main() {
    if let Err(error) = run() {
        eprintln!("language-balance: {error}");
        std::process::exit(1);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tree_paths_and_modes() {
        let data = concat!(
            "100644 blob abc 12\tspace dir/名字.swift\0",
            "100755 blob def 34\ttemplate.swift.gyb\0",
            "100644 blob ghi 56\tline\nbreak.rs\0",
            "120000 blob jkl 5\tlink.swift\0",
            "160000 commit mno -\tsubmodule.rs\0",
            "100644 blob pqr 78\tother.txt\0");
        let sources = tree_sources(data.as_bytes()).unwrap();
        assert_eq!(sources.len(), 3);
        assert_eq!(sources[0], Source {
            path: "space dir/名字.swift".as_bytes().to_vec(), language: 0, bytes: 12,
        });
        assert_eq!((sources[1].language, sources[2].language), (0, 1));
        assert_eq!(sources[2].bytes, 56);
        assert!(tree_sources(b"100644 blob abc 2\tnonutf8\xff.rs\0").is_ok());
    }

    #[test]
    fn malformed_trees_fail() {
        for data in [b"no tab\0".as_slice(), b"100644 blob abc nope\ta.rs\0",
            b"100644 blob abc\ta.rs\0", b"100644 blob abc 1\t\0",
            b"100644 blob abc 1\ta.rs", b"\0"] {
            assert!(tree_sources(data).is_err(), "{data:?}");
        }
        assert!(tree_sources(b"").unwrap().is_empty());
    }

    fn attribute_data(vendored: &str, generated: &str) -> Vec<u8> {
        format!("space dir/名字.swift\0linguist-vendored\0{vendored}\0space dir/名字.swift\0linguist-generated\0{generated}\0").into_bytes()
    }

    #[test]
    fn exclusion_values() {
        for (vendored, generated, expected) in [
            ("set", "unspecified", true), ("false", "true", true),
            ("true", "unset", true), ("false", "unset", false),
            ("unspecified", "unspecified", false), ("yes", "1", false),
        ] {
            let values = exclusions(&attribute_data(vendored, generated)).unwrap();
            assert_eq!(values["space dir/名字.swift".as_bytes()], expected);
        }
    }

    #[test]
    fn malformed_attributes_fail() {
        for data in [b"path\0linguist-vendored\0set\0".as_slice(), b"path\0name\0set\0",
            b"path\0", b"path", b"\0linguist-generated\0set\0"] {
            assert!(exclusions(data).is_err());
        }
        let mut duplicate = attribute_data("true", "false");
        duplicate.extend_from_slice(b"space dir/\xe5\x90\x8d\xe5\xad\x97.swift\0linguist-vendored\0set\0");
        assert!(exclusions(&duplicate).is_err());
        assert!(exclusions(b"").unwrap().is_empty());
    }

    #[test]
    fn percentages_handle_empty_and_partial_totals() {
        assert_eq!(percentage(0, 0), 0.0);
        assert_eq!(percentage(0, 100), 0.0);
        assert_eq!(percentage(100, 100), 100.0);
        assert!((percentage(1, 3) - 33.333333333).abs() < 0.000001);
    }
}
