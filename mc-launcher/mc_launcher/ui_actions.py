import threading
from pathlib import Path
from PyQt6.QtWidgets import (
    QMessageBox,
)
from .launch import Worker
from .workers import DownloadWorker


class MainWindowActionsMixin:
    def _get_installed_versions(self):
        mc_dir = Path(self.mc_dir_input.text().strip())
        versions_dir = mc_dir / "versions"
        if not versions_dir.exists():
            return set()
        try:
            result = set()
            for p in versions_dir.iterdir():
                if p.is_dir() and list(p.glob("*.json")):
                    result.add(p.name)
            return result
        except:
            return set()

    def _get_selected_version(self):
        data = self.version_combo.currentData()
        if not data or not isinstance(data, dict):
            return None
        return data

    def _refresh_installed_marks(self):
        installed = self._get_installed_versions()
        for i in range(self.version_combo.count()):
            data = self.version_combo.itemData(i)
            if not isinstance(data, dict):
                continue
            if data.get("_local"):
                continue
            vid = data["id"]
            mark = "  [installed]" if vid in installed else ""
            self.version_combo.setItemText(i, f"{vid}{mark}")

    # ---------- Launch ----------
    def on_launch(self):
        vinfo = self._get_selected_version()
        if not vinfo:
            self.log("❌ Please select a version first")
            return

        version = vinfo["id"]
        config = {
            "mc_dir": self.mc_dir_input.text().strip(),
            "java": self.java_input.text().strip(),
            "version": version,
            "username": self.username_input.text().strip(),
            "memory": int(self.memory_input.text().strip() or "2048"),
        }
        config["version_dir"] = Path(config["mc_dir"]) / "versions" / version

        if not Path(config["mc_dir"]).exists():
            self.log(f"❌ MC directory does not exist: {config['mc_dir']}")
            return
        json_path = config["version_dir"] / f"{version}.json"
        if not json_path.exists():
            self.log(f"❌ Version JSON not found: {json_path}")
            return
        if not Path(config["java"]).exists():
            self.log(f"❌ Java not found: {config['java']}")
            return

        self.launch_btn.setEnabled(False)
        self.download_btn.setEnabled(False)
        self.forge_btn.setEnabled(False)
        self.fabric_btn.setEnabled(False)
        self.stop_btn.setEnabled(True)

        self.worker = Worker(config)
        self.worker.log.connect(self.log)
        self.worker.finished.connect(self._on_launch_finished)
        threading.Thread(target=self.worker.launch, daemon=True).start()

    def _on_launch_finished(self):
        self.launch_btn.setEnabled(True)
        self.download_btn.setEnabled(True)
        self.forge_btn.setEnabled(True)
        self.fabric_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)

    def on_stop(self):
        if self.worker:
            self.worker.stop()

    # ---------- Download vanilla ----------
    def on_download(self):
        vinfo = self._get_selected_version()
        if not vinfo:
            self.log("❌ Please select a version first")
            return
        if vinfo.get("_local"):
            self.log("❌ Local versions (Forge/Fabric) do not need a vanilla download")
            return
        if "url" not in vinfo:
            self.log("❌ No download info for this version")
            return

        version = vinfo["id"]
        mc_dir = self.mc_dir_input.text().strip()

        if not Path(mc_dir).exists():
            self.log(f"❌ MC directory does not exist: {mc_dir}")
            return

        version_dir = Path(mc_dir) / "versions" / version
        if (version_dir / f"{version}.json").exists():
            reply = QMessageBox.question(
                self, "Download again?",
                f"Version {version} already exists.\nDownload it again? (this overwrites it)",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            )
            if reply != QMessageBox.StandardButton.Yes:
                return
        else:
            reply = QMessageBox.question(
                self, "Confirm download",
                f"Version: {version}\nType: {vinfo.get('type', 'unknown')}\n"
                f"Released: {vinfo.get('releaseTime', '')[:10]}\n\n"
                f"Saved to: {mc_dir}\n"
                f"Expect a few hundred MB up to several GB. Continue?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            )
            if reply != QMessageBox.StandardButton.Yes:
                return

        self.launch_btn.setEnabled(False)
        self.download_btn.setEnabled(False)
        self.forge_btn.setEnabled(False)
        self.fabric_btn.setEnabled(False)
        self.log("=" * 60)
        self.log(f"Downloading version: {version}")

        self.download_worker = DownloadWorker(
            version, mc_dir, vinfo["url"], vinfo.get("sha1")
        )
        self.download_worker.log.connect(self.log)
        self.download_worker.progress.connect(self._on_download_progress)
        self.download_worker.finished.connect(self._on_download_finished)
        threading.Thread(target=self.download_worker.run, daemon=True).start()

    def _on_download_progress(self, done, total, failed):
        self.log(f"    Progress: {done}/{total} (failed {failed})")

    def _on_download_finished(self, success):
        self.launch_btn.setEnabled(True)
        self.download_btn.setEnabled(True)
        self.forge_btn.setEnabled(True)
        self.fabric_btn.setEnabled(True)
        if success:
            self.log("🎉 Download complete!")
            self._refresh_installed_marks()

    # ---------- Install Forge ----------
