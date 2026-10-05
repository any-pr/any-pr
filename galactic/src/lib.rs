//! Galactic Mission Control — deterministic galaxy charts and jump
//! routing. Port of `mission.py`; zero external dependencies.

mod json;
mod sha256;

use std::cmp::Ordering;
use std::collections::{BinaryHeap, HashMap, HashSet};

const KINDS: [&str; 4] = ["ocean", "desert", "forest", "ice"];

/// Deterministic PRNG (FNV-1a seeding -> xorshift64*), replacing
/// `random.Random`. Maps are reproducible within this implementation
/// only — not across ports or runtimes.
struct Rng(u64);

impl Rng {
    fn new(seed: &str) -> Self {
        let mut h = 0xcbf2_9ce4_8422_2325u64;
        for &b in seed.as_bytes() {
            h ^= u64::from(b);
            h = h.wrapping_mul(0x0000_0100_0000_01b3);
        }
        Rng(h | 1)
    }
    fn next(&mut self) -> u64 {
        let mut x = self.0;
        x ^= x >> 12;
        x ^= x << 25;
        x ^= x >> 27;
        self.0 = x;
        x.wrapping_mul(0x2545_f491_4f6c_dd1d)
    }
    fn range(&mut self, n: u64) -> u64 {
        self.next() % n
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Star {
    pub name: String,
    pub x: u32,
    pub y: u32,
    pub kind: &'static str,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Route {
    pub path: Vec<String>,
    pub distance: f64,
    pub hops: usize,
}

#[derive(Debug)]
pub struct Report {
    pub version: u32,
    pub seed: String,
    pub jump_range: f64,
    pub origin: String,
    pub destination: String,
    pub stars: Vec<Star>,
    pub route: Option<Route>,
    pub fingerprint: String,
}

pub fn galaxy(seed: &str, count: usize) -> Result<Vec<Star>, String> {
    if !(2..=200).contains(&count) {
        return Err("star count must be between 2 and 200".into());
    }
    let mut rng = Rng::new(seed);
    let mut stars = Vec::with_capacity(count);
    let mut occupied = HashSet::new();
    for index in 0..count {
        let mut point = (rng.range(100), rng.range(100));
        while !occupied.insert(point) {
            point = (rng.range(100), rng.range(100));
        }
        stars.push(Star {
            name: format!("S{:03}", index),
            x: point.0 as u32,
            y: point.1 as u32,
            kind: KINDS[rng.range(4) as usize],
        });
    }
    Ok(stars)
}

fn distance(a: &Star, b: &Star) -> f64 {
    ((a.x as f64 - b.x as f64).powi(2) + (a.y as f64 - b.y as f64).powi(2)).sqrt()
}

/// Priority-queue entry; `Ord` is reversed so `BinaryHeap` pops the
/// cheapest pending star first.
struct Entry {
    cost: f64,
    idx: usize,
}
impl PartialEq for Entry {
    fn eq(&self, o: &Self) -> bool {
        self.cost == o.cost && self.idx == o.idx
    }
}
impl Eq for Entry {}
impl PartialOrd for Entry {
    fn partial_cmp(&self, o: &Self) -> Option<Ordering> {
        Some(self.cmp(o))
    }
}
impl Ord for Entry {
    fn cmp(&self, o: &Self) -> Ordering {
        o.cost.partial_cmp(&self.cost).unwrap_or(Ordering::Equal)
    }
}

/// Dijkstra shortest route; `Ok(None)` means unreachable.
pub fn route(stars: &[Star], origin: &str, destination: &str, jump: f64) -> Result<Option<Route>, String> {
    if !jump.is_finite() || jump <= 0.0 {
        return Err("jump range must be finite and positive".into());
    }
    let lookup: HashMap<&str, usize> = stars
        .iter()
        .enumerate()
        .map(|(i, s)| (s.name.as_str(), i))
        .collect();
    if lookup.len() != stars.len() {
        return Err("star names must be unique".into());
    }
    let (oi, di) = match (lookup.get(origin), lookup.get(destination)) {
        (Some(&o), Some(&d)) => (o, d),
        _ => return Err("unknown origin or destination".into()),
    };
    let mut best = vec![f64::INFINITY; stars.len()];
    let mut previous: HashMap<usize, usize> = HashMap::new();
    let mut heap = BinaryHeap::new();
    best[oi] = 0.0;
    heap.push(Entry { cost: 0.0, idx: oi });
    while let Some(Entry { cost, idx }) = heap.pop() {
        if cost > best[idx] {
            continue;
        }
        if idx == di {
            let mut path = vec![di];
            while *path.last().unwrap() != oi {
                path.push(previous[path.last().unwrap()]);
            }
            path.reverse();
            return Ok(Some(Route {
                hops: path.len() - 1,
                path: path.iter().map(|&i| stars[i].name.clone()).collect(),
                distance: (cost * 1000.0).round() / 1000.0,
            }));
        }
        for (ti, other) in stars.iter().enumerate() {
            if ti == idx {
                continue;
            }
            let leg = distance(&stars[idx], other);
            let candidate = cost + leg;
            if leg <= jump && candidate < best[ti] {
                best[ti] = candidate;
                previous.insert(ti, idx);
                heap.push(Entry { cost: candidate, idx: ti });
            }
        }
    }
    Ok(None)
}

pub fn render(stars: &[Star], path: &[String], width: usize, height: usize) -> Result<String, String> {
    if width < 2 || height < 2 {
        return Err("chart dimensions must be at least 2".into());
    }
    let selected: HashSet<&str> = path.iter().map(String::as_str).collect();
    let mut canvas = vec![vec![' '; width]; height];
    for star in stars {
        let x = ((star.x as f64 * (width - 1) as f64 / 99.0).round() as usize).min(width - 1);
        let y = ((star.y as f64 * (height - 1) as f64 / 99.0).round() as usize).min(height - 1);
        if canvas[y][x] != '@' {
            canvas[y][x] = if selected.contains(star.name.as_str()) { '@' } else { '*' };
        }
    }
    let border = format!("+{}+", "-".repeat(width));
    let mut out = border.clone();
    for row in &canvas {
        out.push_str(&format!("\n|{}|", row.iter().collect::<String>()));
    }
    out.push('\n');
    out.push_str(&border);
    Ok(out)
}

pub fn mission(
    seed: &str,
    count: usize,
    origin: &str,
    destination: Option<&str>,
    jump: f64,
) -> Result<Report, String> {
    let stars = galaxy(seed, count)?;
    let destination = destination
        .map(str::to_owned)
        .unwrap_or_else(|| stars.last().unwrap().name.clone());
    let route = route(&stars, origin, &destination, jump)?;
    let mut report = Report {
        version: 1,
        seed: seed.to_owned(),
        jump_range: jump,
        origin: origin.to_owned(),
        destination,
        stars,
        route,
        fingerprint: String::new(),
    };
    report.fingerprint = sha256::sha256_hex(json::canonical(&report).as_bytes());
    Ok(report)
}

/// Runs the CLI against `args` (excluding argv[0]) and returns the
/// output text plus the intended exit code. `Err` means invalid input
/// (exit code 2 after printing the message to stderr).
pub fn run(args: &[String]) -> Result<(String, i32), String> {
    let mut seed = "any-pr".to_owned();
    let mut stars = 24usize;
    let mut origin = "S000".to_owned();
    let mut destination: Option<String> = None;
    let mut jump = 30.0f64;
    let mut as_json = false;
    fn val<'a, I: Iterator<Item = &'a String>>(it: &mut I, name: &str) -> Result<String, String> {
        it.next().cloned().ok_or_else(|| format!("{} requires a value", name))
    }
    let mut it = args.iter();
    while let Some(arg) = it.next() {
        match arg.as_str() {
            "--seed" => seed = val(&mut it, "--seed")?,
            "--stars" => {
                stars = val(&mut it, "--stars")?.parse().map_err(|_| "--stars must be an integer".to_owned())?
            }
            "--origin" => origin = val(&mut it, "--origin")?,
            "--destination" => destination = Some(val(&mut it, "--destination")?),
            "--jump" => {
                jump = val(&mut it, "--jump")?.parse().map_err(|_| "--jump must be a number".to_owned())?
            }
            "--json" => as_json = true,
            other => return Err(format!("unrecognized argument: {}", other)),
        }
    }
    let data = mission(&seed, stars, &origin, destination.as_deref(), jump)?;
    if as_json {
        return Ok((json::pretty(&data), 0));
    }
    let path: Vec<String> = data.route.as_ref().map(|r| r.path.clone()).unwrap_or_default();
    let mut out = String::from("ANY-PR // GALACTIC MISSION CONTROL\n");
    out.push_str(&render(&data.stars, &path, 50, 20)?);
    out.push_str(&format!("\nSeed: {} | Stars: {} | Jump: {}", seed, stars, jump));
    out.push_str(&format!("\nMission: {} -> {}", data.origin, data.destination));
    match &data.route {
        Some(r) => {
            out.push_str(&format!("\nRoute: {}", r.path.join(" -> ")));
            out.push_str(&format!("\nDistance: {} | Hops: {}", r.distance, r.hops));
        }
        None => out.push_str("\nUNREACHABLE: increase --jump or choose another destination."),
    }
    out.push_str(&format!("\nFingerprint: {}", data.fingerprint));
    Ok((out, if data.route.is_some() { 0 } else { 2 }))
}
