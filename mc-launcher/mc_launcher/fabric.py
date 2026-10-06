import json
import requests
from pathlib import Path
from .util import make_log_fn, maven_to_path, rules_allow
from .manifest import Downloader


# ============================================================
# Fabric installer
# ============================================================
class FabricInstaller:
    META = "https://meta.fabricmc.net"
    MODRINTH = "https://api.modrinth.com/v2"

    def __init__(self, log_callback=None):
        self.log = make_log_fn(log_callback)
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": "DHML/1.0"})

    def get_loader_versions(self, mc_version):
        url = f"{self.META}/v2/versions/loader/{mc_version}"
        r = self.session.get(url, timeout=30)
        r.raise_for_status()
        data = r.json()
        result = []
        for item in data:
            loader = item.get("loader", {})
            result.append({
                "version": loader.get("version", ""),
                "stable": loader.get("stable", False),
            })
        return result

    def get_api_versions(self, mc_version):
        url = f"{self.MODRINTH}/project/fabric-api/version"
        params = {
            "game_versions": json.dumps([mc_version]),
            "loaders": json.dumps(["fabric"]),
        }
        r = self.session.get(url, params=params, timeout=30)
        r.raise_for_status()
        data = r.json()

        result = []
        for item in data:
            files = item.get("files", [])
            primary = next((f for f in files if f.get("primary")), None)
            if not primary and files:
                primary = files[0]
            if not primary:
                continue
            result.append({
                "version_number": item.get("version_number", ""),
                "date_published": item.get("date_published", ""),
                "download_url": primary.get("url", ""),
                "filename": primary.get("filename", ""),
                "size": primary.get("size", 0),
                "sha1": primary.get("hashes", {}).get("sha1"),
            })
        result.sort(key=lambda x: x["date_published"], reverse=True)
        return result

    def install(self, mc_version, loader_version, mc_dir,
                api_version_info=None, isolated=False, progress_cb=None):
        mc_dir = Path(mc_dir)

        self.log(f"[1/4] Fetching Fabric version JSON...")
        url = f"{self.META}/v2/versions/loader/{mc_version}/{loader_version}/profile/json"
        r = self.session.get(url, timeout=30)
        r.raise_for_status()
        vj = r.json()

        version_id = f"{mc_version}-Fabric {loader_version}"
        version_dir = mc_dir / "versions" / version_id
        version_dir.mkdir(parents=True, exist_ok=True)

        vj["id"] = version_id
        vj["inheritsFrom"] = mc_version

        json_path = version_dir / f"{version_id}.json"
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(vj, f, ensure_ascii=False, indent=2)
        self.log(f"    ✓ Version directory: {version_id}")

        self.log(f"[2/4] Downloading Fabric libraries...")
        tasks = []
        for lib in vj.get("libraries", []):
            if not rules_allow(lib.get("rules", [])):
                continue
            name = lib.get("name", "")
            downloads = lib.get("downloads", {})
            artifact = downloads.get("artifact")
            if artifact:
                tasks.append({
                    "url": artifact["url"],
                    "path": mc_dir / "libraries" / artifact["path"],
                    "sha1": artifact.get("sha1"),
                })
            elif name and ":" in name:
                rel_path, _ = maven_to_path(name)
                if rel_path:
                    repo_url = lib.get("url", "https://maven.fabricmc.net/")
                    if not repo_url.endswith("/"):
                        repo_url += "/"
                    tasks.append({
                        "url": repo_url + rel_path,
                        "path": mc_dir / "libraries" / rel_path,
                        "sha1": None,
                    })

        self.log(f"    {len(tasks)} libraries in total")
        dl = Downloader(log_callback=self.log)
        done, failed = dl.download_batch(tasks, max_workers=16, progress_cb=progress_cb)
        self.log(f"    Done {done}/{len(tasks)}, failed {failed}")
        if failed > len(tasks) * 0.2:
            raise RuntimeError(f"{failed}/{len(tasks)} libraries failed")

        if api_version_info:
            self.log(f"[3/4] Downloading Fabric API...")
            if isolated:
                mods_dir = version_dir / "mods"
            else:
                mods_dir = mc_dir / "mods"
            mods_dir.mkdir(parents=True, exist_ok=True)
            mod_path = mods_dir / api_version_info["filename"]
            if dl.download_file(
                api_version_info["download_url"],
                mod_path,
                sha1=api_version_info.get("sha1"),
            ):
                self.log(f"    ✓ {api_version_info['filename']}")
                self.log(f"    Saved to: {mod_path}")
            else:
                self.log(f"    ⚠ Fabric API download failed, skipping")
        else:
            self.log(f"[3/4] Skipping Fabric API")

        self.log(f"[4/4] Done")
        self.log(f"✅ Fabric {loader_version} for {mc_version} installed!")
        if api_version_info:
            self.log(f"   Fabric API: {api_version_info['version_number']}")
        return version_dir
