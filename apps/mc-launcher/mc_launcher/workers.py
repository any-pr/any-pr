from pathlib import Path
from PyQt6.QtCore import QObject, pyqtSignal
from .manifest import Downloader
from .forge import ForgeDownloader
from .fabric import FabricInstaller


# ============================================================
# Vanilla download thread
# ============================================================
class DownloadWorker(QObject):
    log = pyqtSignal(str)
    finished = pyqtSignal(bool)
    progress = pyqtSignal(int, int, int)

    def __init__(self, version_id, mc_dir, version_json_url, version_json_sha1):
        super().__init__()
        self.version_id = version_id
        self.mc_dir = mc_dir
        self.version_json_url = version_json_url
        self.version_json_sha1 = version_json_sha1

    def run(self):
        try:
            dl = Downloader(log_callback=self.log)
            dl.install_version(
                self.version_id, self.mc_dir,
                self.version_json_url, self.version_json_sha1,
                progress_cb=lambda d, t, f: self.progress.emit(d, t, f),
            )
            self.finished.emit(True)
        except Exception as e:
            self.log.emit(f"❌ Download failed: {type(e).__name__}: {e}")
            import traceback
            self.log.emit(traceback.format_exc())
            self.finished.emit(False)


# ============================================================
# Forge install thread
# ============================================================
class ForgeInstallWorker(QObject):
    log = pyqtSignal(str)
    finished = pyqtSignal(bool)
    progress = pyqtSignal(int, int)

    def __init__(self, mc_version, forge_version, mc_dir, java_path):
        super().__init__()
        self.mc_version = mc_version
        self.forge_version = forge_version
        self.mc_dir = mc_dir
        self.java_path = java_path

    def run(self):
        try:
            dl = ForgeDownloader(log_callback=self.log)
            installer_name = f"forge-{self.mc_version}-{self.forge_version}-installer.jar"
            installer_path = Path(self.mc_dir) / "temp" / installer_name

            self.log.emit(f"[1/2] Downloading the Forge installer...")
            dl.download_installer(
                self.mc_version, self.forge_version,
                installer_path,
                progress_cb=lambda d, t: self.progress.emit(d, t),
            )
            self.log.emit(f"    ✓ Downloaded: {installer_name}")

            self.log.emit(f"[2/2] Running the Forge installer (may take a few minutes)...")
            dl.install_forge(installer_path, Path(self.mc_dir), self.java_path)

            try:
                installer_path.unlink()
            except:
                pass

            self.log.emit(f"✅ Forge {self.forge_version} for {self.mc_version} installed!")
            self.finished.emit(True)
        except Exception as e:
            self.log.emit(f"❌ Forge install failed: {type(e).__name__}: {e}")
            import traceback
            self.log.emit(traceback.format_exc())
            self.finished.emit(False)


# ============================================================
# Fabric install thread
# ============================================================
class FabricInstallWorker(QObject):
    log = pyqtSignal(str)
    finished = pyqtSignal(bool)
    progress = pyqtSignal(int, int, int)

    def __init__(self, mc_version, loader_version, mc_dir, api_info, isolated):
        super().__init__()
        self.mc_version = mc_version
        self.loader_version = loader_version
        self.mc_dir = mc_dir
        self.api_info = api_info
        self.isolated = isolated

    def run(self):
        try:
            installer = FabricInstaller(log_callback=self.log)
            installer.install(
                self.mc_version,
                self.loader_version,
                self.mc_dir,
                api_version_info=self.api_info,
                isolated=self.isolated,
                progress_cb=lambda d, t, f: self.progress.emit(d, t, f),
            )
            self.finished.emit(True)
        except Exception as e:
            self.log.emit(f"❌ Fabric install failed: {type(e).__name__}: {e}")
            import traceback
            self.log.emit(traceback.format_exc())
            self.finished.emit(False)
