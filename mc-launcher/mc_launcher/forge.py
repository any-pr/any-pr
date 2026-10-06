import sys
import subprocess
import requests
from pathlib import Path
from .util import make_log_fn


# ============================================================
# Forge downloader
# ============================================================
class ForgeDownloader:
    BMCLAPI = "https://bmclapi2.bangbang93.com"

    def __init__(self, log_callback=None):
        self.log = make_log_fn(log_callback)
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": "DHML/1.0"})

    def get_forge_versions(self, mc_version):
        url = f"{self.BMCLAPI}/forge/minecraft/{mc_version}"
        r = self.session.get(url, timeout=30)
        r.raise_for_status()
        data = r.json()

        result = []
        for item in data:
            forge_ver = item.get("version", "")
            build = item.get("build", 0)
            modified = item.get("modified", "")
            installer = None
            for f in item.get("files", []):
                if f.get("category") == "installer" and f.get("format") == "jar":
                    installer = f
                    break
            result.append({
                "mcversion": mc_version,
                "version": forge_ver,
                "build": build,
                "modified": modified,
                "installer_hash": installer.get("hash") if installer else None,
            })
        result.sort(key=lambda x: x["build"], reverse=True)
        return result

    def download_installer(self, mc_version, forge_version, save_path, progress_cb=None):
        url = f"{self.BMCLAPI}/forge/download"
        params = {
            "mcversion": mc_version,
            "version": forge_version,
            "category": "installer",
            "format": "jar",
        }
        r = self.session.get(url, params=params, stream=True, timeout=60)
        r.raise_for_status()

        save_path = Path(save_path)
        save_path.parent.mkdir(parents=True, exist_ok=True)

        total = int(r.headers.get("content-length", 0))
        done = 0
        with open(save_path, "wb") as f:
            for chunk in r.iter_content(8192):
                f.write(chunk)
                done += len(chunk)
                if progress_cb and total:
                    progress_cb(done, total)
        return save_path

    def install_forge(self, installer_jar, mc_dir, java_path):
        cmd = [
            java_path,
            "-jar", str(installer_jar),
            "--installClient", str(mc_dir),
        ]
        self.log(f"Running installer: {' '.join(cmd)}")
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            cwd=str(installer_jar.parent),
            creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
        )
        for line in process.stdout:
            line = line.rstrip()
            if line:
                self.log(f"[Forge] {line}")
        rc = process.wait()
        if rc != 0:
            raise RuntimeError(f"Forge installer exit code: {rc}")
        return rc
