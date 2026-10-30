#!/usr/bin/env python3
"""audit-anypr -- measure the governance experiment from its own PR history.

`docs/EXPERIMENT-LOG.md` and `docs/LEGAL-OPINION.md` describe this repository
from outside: who created it, what the Regulations say, which clauses have been
shown toothless. Neither counts anything; every claim is about a handful of
examples. The PR history is the experiment's actual output, and nobody has
measured it. This does.

    python3 scripts/audit-anypr.py --fetch --repo any-pr/any-pr
    python3 scripts/audit-anypr.py prdata/*.json
    python3 scripts/audit-anypr.py prdata/*.json --json
    python3 scripts/audit-anypr.py --self-test

Either pass the raw JSON the GitHub REST API returns for
`GET /repos/{owner}/{repo}/pulls?state=all` (one file per page), or let the
script fetch it with --fetch. Fetching needs no token for a public repository
but is subject to the anonymous rate limit; a token in GITHUB_TOKEN raises it.
No snapshot is committed, so the numbers always describe the live repository
rather than a stale copy.

Exit status: 0 on success, 2 on unusable input. Standard library only.
"""

import argparse
import json
import os
import statistics
import sys
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone


def load(paths: list[str]) -> list[dict]:
    """Concatenate every PR page, de-duplicating by number."""
    by_number: dict[int, dict] = {}
    for path in paths:
        with open(path, encoding="utf-8", errors="replace") as fh:
            page = json.load(fh)
        if not isinstance(page, list):
            raise ValueError(f"{path}: expected a JSON array")
        by_number.update({p["number"]: p for p in page
                          if isinstance(p, dict) and "number" in p})
    return [by_number[k] for k in sorted(by_number)]


def fetch(repo: str, pages: int, per_page: int = 100) -> list[dict]:
    """Pull PR pages from the GitHub API, oldest first.

    Follows Link headers rather than trusting --pages, so a repo with more
    history than expected is not silently truncated.
    """
    out: list[dict] = []
    url = (f"https://api.github.com/repos/{repo}/pulls"
           f"?state=all&per_page={per_page}&sort=created&direction=asc&page=1")
    token = os.environ.get("GITHUB_TOKEN")
    seen_pages = 0
    while url and seen_pages < pages:
        req = urllib.request.Request(url, headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "audit-anypr",
            **({"Authorization": f"Bearer {token}"} if token else {}),
        })
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                page = json.load(resp)
                link = resp.headers.get("Link", "")
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"{exc.code} {exc.reason} on page {seen_pages + 1}") from exc
        if not isinstance(page, list) or not page:
            break
        out.extend(p for p in page if isinstance(p, dict) and "number" in p)
        seen_pages += 1

        url = next((p.split(";")[0].strip().strip("<>")
                    for p in link.split(",") if 'rel="next"' in p), None)
    return out


def parse(ts: str | None) -> datetime | None:
    """Parse an API timestamp; tolerate nulls and both formats."""
    if not ts:
        return None
    try:
        return datetime.strptime(ts, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError:
        try:
            return datetime.fromisoformat(ts.replace("Z", "+00:00"))
        except ValueError:
            return None


def seconds(a: datetime | None, b: datetime | None) -> float | None:
    if a is None or b is None:
        return None
    return (b - a).total_seconds()


def summarise(prs: list[dict]) -> dict:
    """Every number this script reports, in one dict."""
    total = len(prs)
    merged = [p for p in prs if p.get("merged_at")]
    closed_unmerged = [p for p in prs if not p.get("merged_at") and p.get("state") == "closed"]
    still_open = [p for p in prs if p.get("state") == "open"]

    decide = []
    for p in prs:
        c = parse(p.get("closed_at"))
        o = parse(p.get("created_at"))
        d = seconds(o, c)
        if d is not None and d >= 0:
            decide.append(d)

    merge_latency = []
    for p in merged:
        d = seconds(parse(p.get("created_at")), parse(p.get("merged_at")))
        if d is not None and d >= 0:
            merge_latency.append(d)

    authors = Counter((p.get("user") or {}).get("login", "?") for p in prs)
    assoc = Counter(p.get("author_association", "?") for p in prs)

    noise = Counter()
    for p in prs:
        t = (p.get("title") or "").strip().lower()
        for tag in ("test", "update", "add", "fix", "feat", "docs", "audit", "naiwa", "revival"):
            if t.startswith(tag):
                noise[tag] += 1
                break
        else:
            noise["other"] += 1

    literal_test = sum(
        1 for p in prs
        if (p.get("title") or "").strip().lower().startswith("test")
    )

    by_month = defaultdict(lambda: [0, 0])  # month -> [merged, total]
    for p in prs:
        o = parse(p.get("created_at"))
        if o is None:
            continue
        bucket = by_month[o.strftime("%Y-%m")]
        bucket[1] += 1
        bucket[0] += 1 if p.get("merged_at") else 0

    return {
        "total": total,
        "merged": len(merged),
        "closed_unmerged": len(closed_unmerged),
        "open": len(still_open),
        "decide_median_s": statistics.median(decide) if decide else None,
        "decide_p90_s": sorted(decide)[int(len(decide) * 0.9)] if decide else None,
        "decide_min_s": min(decide) if decide else None,
        "merge_median_s": statistics.median(merge_latency) if merge_latency else None,
        "authors": authors,
        "assoc": assoc,
        "title_kinds": noise,
        "literal_test_titles": literal_test,
        "by_month": dict(sorted(by_month.items())),
    }


def human(seconds_value: float | None) -> str:
    """Render a duration compactly."""
    if seconds_value is None:
        return "-"
    if seconds_value < 1:
        return f"{seconds_value*1000:.0f}ms"
    if seconds_value < 120:
        return f"{seconds_value:.0f}s"
    return (f"{seconds_value/60:.1f}m" if seconds_value < 7200
            else f"{seconds_value/3600:.1f}h")


def report(s: dict) -> None:
    total = s["total"]
    pct = lambda n: f"{n} ({100 * n / total:.1f}%)" if total else str(n)

    print(f"pull requests   {total}")
    print(f"  merged        {pct(s['merged'])}")
    print(f"  closed        {pct(s['closed_unmerged'])}")
    print(f"  still open    {pct(s['open'])}")
    print("\ndecision latency (open -> closed)")
    for label, key in (("median", "decide_median_s"), ("p90", "decide_p90_s"),
                       ("fastest", "decide_min_s")):
        print(f"  {label:<8} {human(s[key])}")
    print(f"  merged only {human(s['merge_median_s'])}")
    print("\ncontributors")
    for login, n in s["authors"].most_common(8):
        print(f"  {login:<28} {n}")
    print(f"  distinct authors {len(s['authors'])} | {dict(s['assoc'])}")
    print("\nwhat the titles say")
    for kind, n in s["title_kinds"].most_common():
        print(f"  {kind:<10} {n}")
    print(f"  literal 'test' titles {s['literal_test_titles']}")
    print("\nmerge rate by month")
    for month, (m, t) in s["by_month"].items():
        print(f"  {month}  {m:>5}/{t:<5} " + "#" * round(20 * m / t if t else 0))


def self_test() -> int:
    """Exercise the arithmetic on synthetic records, no network or files."""
    checks = 0
    failures = []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    now = "2026-10-02T00:00:00Z"
    later = "2026-10-02T00:00:12Z"
    prs = [
        {"number": 1, "state": "closed", "title": "test1",
         "created_at": now, "closed_at": later, "merged_at": later,
         "user": {"login": "a"}, "author_association": "CONTRIBUTOR"},
        {"number": 2, "state": "closed", "title": "test2",
         "created_at": now, "closed_at": later, "merged_at": None,
         "user": {"login": "b"}, "author_association": "NONE"},
        {"number": 3, "state": "open", "title": "Add a thing",
         "created_at": now, "closed_at": None, "merged_at": None,
         "user": {"login": "a"}, "author_association": "CONTRIBUTOR"},
    ]

    s = summarise(prs)
    check(s["total"] == 3, "counts total")
    check(s["merged"] == 1, "counts merged")
    check(s["closed_unmerged"] == 1, "counts closed unmerged")
    check(s["open"] == 1, "counts open")
    check(s["decide_median_s"] == 12, f"median latency is 12s, got {s['decide_median_s']}")
    check(s["decide_min_s"] == 12, "min latency")
    check(s["authors"]["a"] == 2, "author tally")
    check(s["literal_test_titles"] == 2, "literal test titles")
    check(s["title_kinds"]["test"] == 2, "test tag")
    check(s["title_kinds"]["add"] == 1, "add tag")
    check(s["by_month"]["2026-10"] == [1, 3], "month bucket")

    check(parse(None) is None, "parse None")
    for form in (now, "2026-10-02T00:00:00+00:00"):
        check(parse(form) is not None, f"parse {form}")
    check(seconds(None, None) is None, "seconds None")

    for value, want in ((0.5, "500ms"), (12, "12s"), (150, "2.5m"),
                        (7200, "2.0h"), (None, "-")):
        check(human(value) == want, f"human {value} -> {human(value)}")

    for f in failures:
        print(f"  FAIL {f}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="audit-anypr",
        description="Measure the any-pr governance experiment from PR history.",
    )
    parser.add_argument("pages", nargs="*", help="JSON files from the pulls API")
    parser.add_argument("--fetch", action="store_true",
                        help="fetch from the API instead of reading files")
    parser.add_argument("--repo", default="any-pr/any-pr",
                        help="owner/name for --fetch (default any-pr/any-pr)")
    parser.add_argument("--max-pages", type=int, default=200,
                        help="safety cap on fetched pages (default 200)")
    parser.add_argument("--json", action="store_true", help="emit numbers as JSON")
    parser.add_argument("--self-test", action="store_true", help="run built-in checks")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    if not args.pages and not args.fetch:
        parser.error("give JSON files, --fetch, or --self-test")
        return 2

    try:
        prs = fetch(args.repo, args.max_pages) if args.fetch else load(args.pages)
    except (OSError, ValueError, RuntimeError) as exc:
        print(f"audit-anypr: {exc}", file=sys.stderr)
        return 2

    if not prs:
        print("audit-anypr: no pull requests in the given files", file=sys.stderr)
        return 2

    s = summarise(prs)
    if args.json:
        out = {k: (dict(v) if isinstance(v, Counter) else v) for k, v in s.items()}
        print(json.dumps(out, indent=2, default=str))
    else:
        report(s)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))