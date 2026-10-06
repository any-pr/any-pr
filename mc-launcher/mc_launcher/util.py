import platform
import re
from pathlib import Path
from .config import LOG_NOISE_PATTERNS


# A coordinate segment that cannot safely name a path component: ".", "..",
# or anything carrying a separator. Used to keep maven_to_path from building
# a relative path that escapes the directory it is joined onto.
UNSAFE_SEGMENT = re.compile(r"^\.\.?$|[/\\]")


# ============================================================
# Shared helpers
# ============================================================
def rules_allow(rules):
    if not rules:
        return True
    sys_name = platform.system()
    current_os = {"Windows": "windows", "Darwin": "osx", "Linux": "linux"}.get(sys_name, "")
    current_arch = "x86_64" if platform.machine() in ("AMD64", "x86_64") else platform.machine().lower()
    for rule in rules:
        action = rule.get("action")
        os_rule = rule.get("os", {})
        os_name = os_rule.get("name")
        os_arch = os_rule.get("arch")
        if os_name and os_name != current_os:
            continue
        # Compare the architecture directly. Special-casing only "x86" and
        # "x86_64" made every other value -- "arm64", "aarch64" -- fall
        # through as if it matched, so a rule gated to another architecture
        # was applied on this one.
        if os_arch and os_arch != current_arch:
            continue
        if "features" in rule:
            continue
        if action == "allow":
            return True
        if action == "disallow":
            return False
    return False


def get_natives_key(lib):
    natives = lib.get("natives", {})
    if not natives:
        return None
    sys_name = platform.system()
    os_key = {"Windows": "windows", "Darwin": "osx", "Linux": "linux"}.get(sys_name, "")
    key = natives.get(os_key)
    if not key:
        return None
    if "${arch}" in key:
        arch = "64" if platform.machine() in ("AMD64", "x86_64") else "32"
        key = key.replace("${arch}", arch)
    return key


def maven_to_path(name):
    """Maven coordinates -> (relative path, relative directory)

    Returns ``(None, None)`` for coordinates that cannot name a file inside
    the libraries directory: fewer than three segments, an empty group,
    artifact or version, or a segment holding a separator or a ``.``/``..``
    component. Without this, ``":artifact:1.0"`` produced
    ``"/artifact/1.0/artifact-1.0.jar"``, and ``libraries / rel_path``
    resolves a leading separator to the drive root -- writing outside the
    instance directory entirely.
    """
    if not name or ":" not in name:
        return None, None
    parts = name.split(":")
    if len(parts) < 3:
        return None, None
    group = parts[0]
    artifact = parts[1]
    version = parts[2]
    classifier = parts[3] if len(parts) > 3 else None

    segments = [group, artifact, version] + ([classifier] if classifier else [])
    if any(not s or UNSAFE_SEGMENT.search(s) for s in segments):
        return None, None

    group_path = group.replace(".", "/")
    if classifier:
        filename = f"{artifact}-{version}-{classifier}.jar"
    else:
        filename = f"{artifact}-{version}.jar"

    rel_dir = f"{group_path}/{artifact}/{version}"
    rel_path = f"{rel_dir}/{filename}"
    return rel_path, rel_dir


def safe_under(base, relative):
    """Join ``relative`` onto ``base``, refusing to leave ``base``.

    Returns ``None`` when the result would not be inside ``base``. Library
    paths come from the version JSON and mod file names from Modrinth, and
    ``Path.__truediv__`` does not sanitise them: a leading separator resets
    the path to the drive root and a ``..`` component climbs out.

        safe_under("D:/mc/mods", "../../evil.jar")  -> None
        safe_under("D:/mc/mods", "/abs/evil.jar")   -> None
        safe_under("D:/mc/mods", "fabric-api.jar")  -> D:/mc/mods/fabric-api.jar

    Symlinks are followed, so a link inside ``base`` that points outside it
    is rejected too.
    """
    root = Path(base).resolve()
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root)
    except ValueError:
        return None
    return candidate


def make_log_fn(log_callback):
    if log_callback is None:
        return print
    if hasattr(log_callback, "emit"):
        return log_callback.emit
    return log_callback


def is_noise(line):
    """Return True if the log line is noise."""
    return any(p in line for p in LOG_NOISE_PATTERNS)
