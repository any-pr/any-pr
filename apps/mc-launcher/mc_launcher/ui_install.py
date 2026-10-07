import threading
from pathlib import Path
from PyQt6.QtWidgets import (
    QMessageBox, QDialog,
)
from .dialogs import FabricSelectDialog, ForgeSelectDialog
from .fabric import FabricInstaller
from .forge import ForgeDownloader
from .workers import FabricInstallWorker, ForgeInstallWorker


class MainWindowInstallMixin:
    def on_install_forge(self):
        vinfo = self._get_selected_version()
        if not vinfo:
            self.log("❌ Please select a version first")
            return
        if vinfo.get("_local"):
            self.log("❌ Cannot install Forge on a local (Forge/Fabric) version")
            self.log("   Please select an official vanilla version first")
            return

        mc_version = vinfo["id"]
        mc_dir = self.mc_dir_input.text().strip()
        java_path = self.java_input.text().strip()

        if not Path(mc_dir).exists():
            self.log(f"❌ MC directory does not exist: {mc_dir}")
            return
        if not Path(java_path).exists():
            self.log(f"❌ Java not found: {java_path}")
            return

        version_dir = Path(mc_dir) / "versions" / mc_version
        if not (version_dir / f"{mc_version}.json").exists():
            self.log(f"❌ Vanilla {mc_version} is not downloaded yet, download it first")
            return

        self.log(f"Fetching the Forge list for MC {mc_version}...")
        try:
            downloader = ForgeDownloader(log_callback=self.log)
            forge_list = downloader.get_forge_versions(mc_version)
        except Exception as e:
            import traceback
            self.log(f"❌ Failed to fetch the Forge list: {e}")
            self.log(traceback.format_exc())
            return

        if not forge_list:
            self.log(f"❌ No Forge version available for MC {mc_version}")
            return

        self.log(f"✓ Found {len(forge_list)} Forge versions")

        dlg = ForgeSelectDialog(mc_version, forge_list, self)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        if not dlg.selected:
            return

        forge_version = dlg.selected["version"]
        reply = QMessageBox.question(
            self, "Confirm install",
            f"About to install:\n"
            f"MC version: {mc_version}\n"
            f"Forge version: {forge_version}\n"
            f"Build: {dlg.selected['build']}\n\n"
            f"The installer will download its dependencies (a few hundred MB). Continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if reply != QMessageBox.StandardButton.Yes:
            return

        self.launch_btn.setEnabled(False)
        self.download_btn.setEnabled(False)
        self.forge_btn.setEnabled(False)
        self.fabric_btn.setEnabled(False)
        self.log("=" * 60)
        self.log(f"Installing Forge {forge_version} for MC {mc_version}")

        self.forge_worker = ForgeInstallWorker(mc_version, forge_version, mc_dir, java_path)
        self.forge_worker.log.connect(self.log)
        self.forge_worker.progress.connect(self._on_forge_progress)
        self.forge_worker.finished.connect(self._on_forge_finished)
        threading.Thread(target=self.forge_worker.run, daemon=True).start()

    def _on_forge_progress(self, done, total):
        if done % (1024 * 1024) < 8192:
            self.log(f"    Downloaded: {done / 1024 / 1024:.1f} MB / {total / 1024 / 1024:.1f} MB")

    def _on_forge_finished(self, success):
        self.launch_btn.setEnabled(True)
        self.download_btn.setEnabled(True)
        self.forge_btn.setEnabled(True)
        self.fabric_btn.setEnabled(True)
        if success:
            self.log("🎉 Forge installed! Reload the list to see the new version")
            self.load_manifest()

    # ---------- Install Fabric ----------
    def on_install_fabric(self):
        vinfo = self._get_selected_version()
        if not vinfo:
            self.log("❌ Please select a version first")
            return
        if vinfo.get("_local"):
            self.log("❌ Cannot install Fabric on a local (Forge/Fabric) version")
            self.log("   Please select an official vanilla version first")
            return

        mc_version = vinfo["id"]
        mc_dir = self.mc_dir_input.text().strip()

        if not Path(mc_dir).exists():
            self.log(f"❌ MC directory does not exist: {mc_dir}")
            return

        version_dir = Path(mc_dir) / "versions" / mc_version
        if not (version_dir / f"{mc_version}.json").exists():
            self.log(f"❌ Vanilla {mc_version} is not downloaded yet, download it first")
            return

        self.log(f"Fetching the Fabric versions for MC {mc_version}...")
        try:
            installer = FabricInstaller(log_callback=self.log)
            loader_list = installer.get_loader_versions(mc_version)
            api_list = installer.get_api_versions(mc_version)
        except Exception as e:
            import traceback
            self.log(f"❌ Failed to fetch the Fabric list: {e}")
            self.log(traceback.format_exc())
            return

        if not loader_list:
            self.log(f"❌ No Fabric Loader available for MC {mc_version}")
            return

        self.log(f"✓ Found {len(loader_list)} loaders, {len(api_list)} API versions")

        dlg = FabricSelectDialog(mc_version, loader_list, api_list, self)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        if not dlg.selected:
            return

        loader_version = dlg.selected["loader"]["version"]
        api_info = dlg.selected["api"]
        isolated = dlg.selected["isolated"]

        reply = QMessageBox.question(
            self, "Confirm install",
            f"About to install:\n"
            f"MC version: {mc_version}\n"
            f"Fabric Loader: {loader_version}\n"
            f"Fabric API: {api_info['version_number'] if api_info else 'none'}\n"
            f"Version isolation: {'yes' if isolated else 'no'}\n\n"
            f"Continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if reply != QMessageBox.StandardButton.Yes:
            return

        self.launch_btn.setEnabled(False)
        self.download_btn.setEnabled(False)
        self.forge_btn.setEnabled(False)
        self.fabric_btn.setEnabled(False)
        self.log("=" * 60)
        self.log(f"Installing Fabric {loader_version} for MC {mc_version}")

        self.fabric_worker = FabricInstallWorker(
            mc_version, loader_version, mc_dir, api_info, isolated
        )
        self.fabric_worker.log.connect(self.log)
        self.fabric_worker.progress.connect(self._on_fabric_progress)
        self.fabric_worker.finished.connect(self._on_fabric_finished)
        threading.Thread(target=self.fabric_worker.run, daemon=True).start()

    def _on_fabric_progress(self, done, total, failed):
        self.log(f"    Progress: {done}/{total} (failed {failed})")

    def _on_fabric_finished(self, success):
        self.launch_btn.setEnabled(True)
        self.download_btn.setEnabled(True)
        self.forge_btn.setEnabled(True)
        self.fabric_btn.setEnabled(True)
        if success:
            self.log("🎉 Fabric installed! Reload the list to see the new version")
            if self.fabric_worker:
                fabric_version = f"{self.fabric_worker.mc_version}-Fabric {self.fabric_worker.loader_version}"
                self._remembered_version_id = fabric_version
                self.log(f"    → Will auto-select after reload: {fabric_version}")
            self.load_manifest()
