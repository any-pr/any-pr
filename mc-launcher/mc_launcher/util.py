import platform
from .config import LOG_NOISE_PATTERNS


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
        if os_arch:
            if os_arch == "x86" and current_arch != "x86":
                continue
            if os_arch == "x86_64" and current_arch != "x86_64":
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
    """Maven coordinates -> (relative path, relative directory)"""
    if not name or ":" not in name:
        return None, None
    parts = name.split(":")
    if len(parts) < 3:
        return None, None
    group = parts[0]
    artifact = parts[1]
    version = parts[2]
    classifier = parts[3] if len(parts) > 3 else None

    group_path = group.replace(".", "/")
    if classifier:
        filename = f"{artifact}-{version}-{classifier}.jar"
    else:
        filename = f"{artifact}-{version}.jar"

    rel_dir = f"{group_path}/{artifact}/{version}"
    rel_path = f"{rel_dir}/{filename}"
    return rel_path, rel_dir


def make_log_fn(log_callback):
    if log_callback is None:
        return print
    if hasattr(log_callback, "emit"):
        return log_callback.emit
    return log_callback


def is_noise(line):
    """Return True if the log line is noise."""
    return any(p in line for p in LOG_NOISE_PATTERNS)
