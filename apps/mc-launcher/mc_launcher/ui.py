import threading
from pathlib import Path
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QTextEdit, QLabel, QLineEdit, QFormLayout, QGroupBox, QMessageBox, QComboBox, QDialog,
)
from PyQt6.QtGui import QFont
from .config import DEFAULT_JAVA, DEFAULT_MC_DIR, DEFAULT_MEMORY, DEFAULT_USERNAME
from .manifest import ManifestFetcher
from .forge import ForgeDownloader
from .fabric import FabricInstaller
from .workers import DownloadWorker, FabricInstallWorker, ForgeInstallWorker
from .launch import Worker
from .dialogs import FabricSelectDialog, ForgeSelectDialog
from .ui_actions import MainWindowActionsMixin
from .ui_install import MainWindowInstallMixin


# ============================================================
# Main window
# ============================================================
class MainWindow(MainWindowActionsMixin, MainWindowInstallMixin, QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("mc-launcher")
        self.resize(1120, 780)

        self.manifest_versions = []
        self._remembered_version_id = None
        self.worker = None
        self.download_worker = None
        self.forge_worker = None
        self.fabric_worker = None

        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)

        # ========== Configuration ==========
        config_box = QGroupBox("Configuration")
        form = QFormLayout(config_box)

        self.mc_dir_input = QLineEdit(DEFAULT_MC_DIR)
        form.addRow("MC directory:", self.mc_dir_input)

        self.java_input = QLineEdit(DEFAULT_JAVA)
        form.addRow("Java path:", self.java_input)

        row = QHBoxLayout()
        row.addWidget(QLabel("Version:"))
        self.version_combo = QComboBox()
        self.version_combo.setMinimumWidth(380)
        self.version_combo.setEditable(False)
        row.addWidget(self.version_combo)

        self.reload_btn = QPushButton("🔄")
        self.reload_btn.setFixedSize(32, 32)
        self.reload_btn.setToolTip("Fetch the version manifest again")
        self.reload_btn.clicked.connect(self.load_manifest)
        row.addWidget(self.reload_btn)

        row.addSpacing(15)
        row.addWidget(QLabel("Player name:"))
        self.username_input = QLineEdit(DEFAULT_USERNAME)
        self.username_input.setFixedWidth(110)
        row.addWidget(self.username_input)

        row.addSpacing(15)
        row.addWidget(QLabel("Memory (MB):"))
        self.memory_input = QLineEdit(str(DEFAULT_MEMORY))
        self.memory_input.setFixedWidth(80)
        row.addWidget(self.memory_input)
        row.addStretch()

        row_widget = QWidget()
        row_widget.setLayout(row)
        form.addRow("", row_widget)

        layout.addWidget(config_box)

        # ========== Buttons ==========
        btn_row = QHBoxLayout()

        self.launch_btn = QPushButton("▶  Launch Game")
        self.launch_btn.setFixedHeight(52)
        self.launch_btn.setStyleSheet("""
            QPushButton {
                background-color: #3fa34d; color: white;
                font-size: 16px; font-weight: bold;
                border: none; border-radius: 8px;
            }
            QPushButton:hover { background-color: #4bb85a; }
            QPushButton:pressed { background-color: #348a40; }
            QPushButton:disabled { background-color: #555; color: #999; }
        """)
        self.launch_btn.clicked.connect(self.on_launch)

        self.download_btn = QPushButton("⬇  Download Vanilla")
        self.download_btn.setFixedHeight(52)
        self.download_btn.setFixedWidth(120)
        self.download_btn.setStyleSheet("""
            QPushButton {
                background-color: #4a9eff; color: white;
                font-size: 14px; font-weight: bold;
                border: none; border-radius: 8px;
            }
            QPushButton:hover { background-color: #5aaeff; }
            QPushButton:disabled { background-color: #555; color: #999; }
        """)
        self.download_btn.clicked.connect(self.on_download)

        self.forge_btn = QPushButton("🔧  Install Forge")
        self.forge_btn.setFixedHeight(52)
        self.forge_btn.setFixedWidth(140)
        self.forge_btn.setStyleSheet("""
            QPushButton {
                background-color: #d97706; color: white;
                font-size: 14px; font-weight: bold;
                border: none; border-radius: 8px;
            }
            QPushButton:hover { background-color: #ea8a17; }
            QPushButton:pressed { background-color: #b96305; }
            QPushButton:disabled { background-color: #555; color: #999; }
        """)
        self.forge_btn.clicked.connect(self.on_install_forge)

        self.fabric_btn = QPushButton("🧵  Install Fabric")
        self.fabric_btn.setFixedHeight(52)
        self.fabric_btn.setFixedWidth(140)
        self.fabric_btn.setStyleSheet("""
            QPushButton {
                background-color: #7c3aed; color: white;
                font-size: 14px; font-weight: bold;
                border: none; border-radius: 8px;
            }
            QPushButton:hover { background-color: #8b4bf7; }
            QPushButton:pressed { background-color: #6b2ad8; }
            QPushButton:disabled { background-color: #555; color: #999; }
        """)
        self.fabric_btn.clicked.connect(self.on_install_fabric)

        self.stop_btn = QPushButton("■  Stop")
        self.stop_btn.setFixedHeight(52)
        self.stop_btn.setFixedWidth(90)
        self.stop_btn.setEnabled(False)
        self.stop_btn.clicked.connect(self.on_stop)

        self.clear_btn = QPushButton("Clear")
        self.clear_btn.setFixedHeight(52)
        self.clear_btn.setFixedWidth(70)
        self.clear_btn.clicked.connect(lambda: self.log_text.clear())

        btn_row.addWidget(self.launch_btn, 1)
        btn_row.addWidget(self.download_btn)
        btn_row.addWidget(self.forge_btn)
        btn_row.addWidget(self.fabric_btn)
        btn_row.addWidget(self.stop_btn)
        btn_row.addWidget(self.clear_btn)
        layout.addLayout(btn_row)

        # ========== Log ==========
        layout.addWidget(QLabel("Log:"))
        self.log_text = QTextEdit()
        self.log_text.setReadOnly(True)
        self.log_text.setFont(QFont("Consolas", 9))
        self.log_text.setStyleSheet("""
            QTextEdit {
                background-color: #1e1e1e;
                color: #d4d4d4;
                border: 1px solid #333;
                border-radius: 6px;
            }
        """)
        layout.addWidget(self.log_text, 1)

        self.log("Ready. v3.0: vanilla / Forge / Fabric side by side")
        self.log(f"MC directory: {DEFAULT_MC_DIR}")
        self.log(f"Java: {DEFAULT_JAVA}")

        self.load_manifest()

    def log(self, msg):
        self.log_text.append(msg)
        sb = self.log_text.verticalScrollBar()
        sb.setValue(sb.maximum())

    # ---------- Version manifest ----------
    def load_manifest(self):
        current = self._get_selected_version()
        self._remembered_version_id = current["id"] if current else None

        self.version_combo.clear()
        self.version_combo.addItem("(loading...)")
        self.version_combo.setEnabled(False)

        self.manifest_fetcher = ManifestFetcher()
        self.manifest_fetcher.log.connect(self.log)
        self.manifest_fetcher.finished.connect(self._on_manifest_loaded)
        threading.Thread(target=self.manifest_fetcher.run, daemon=True).start()

    def _on_manifest_loaded(self, versions):
        self.manifest_versions = versions
        self.version_combo.clear()

        local_installed = self._get_installed_versions()
        official_ids = {v["id"] for v in versions}
        local_only = sorted(local_installed - official_ids)

        if not versions:
            # Only the manifest failed. Versions already on disk are still
            # launchable, so list them instead of showing nothing -- and
            # re-enable the combo, which load_manifest() disabled.
            self.log("⚠ Could not fetch the version manifest")
            if local_only:
                self.log(f"   {len(local_only)} version(s) found locally")
                for vid in local_only:
                    self.version_combo.addItem(
                        vid,
                        userData={"id": vid, "_local": True},
                    )
            else:
                self.version_combo.addItem("(fetch failed)")
            self.version_combo.setEnabled(True)
            self.version_combo.setCurrentIndex(0)
            return

        release_versions = [v for v in versions if v.get("type") == "release"]
        snapshot_versions = [v for v in versions if v.get("type") == "snapshot"]
        old_versions = [v for v in versions if v.get("type") in ("old_beta", "old_alpha")]
        release_versions.sort(key=lambda v: v.get("releaseTime", ""), reverse=True)
        snapshot_versions.sort(key=lambda v: v.get("releaseTime", ""), reverse=True)

        def add_group_title(label):
            self.version_combo.insertSeparator(self.version_combo.count())
            self.version_combo.addItem(f"── {label} ──")
            idx = self.version_combo.count() - 1
            self.version_combo.model().item(idx).setEnabled(False)

        if local_only:
            add_group_title("Local versions")
            for vid in local_only:
                self.version_combo.addItem(
                    vid,
                    userData={"id": vid, "_local": True},
                )
            self.version_combo.insertSeparator(self.version_combo.count())

        if release_versions:
            first = release_versions[0]
            mark = "  [installed]" if first["id"] in local_installed else ""
            self.version_combo.addItem(f"{first['id']}{mark}", userData=first)

            if len(release_versions) > 1:
                add_group_title("Releases")
                for v in release_versions[1:]:
                    vid = v["id"]
                    mark = "  [installed]" if vid in local_installed else ""
                    self.version_combo.addItem(f"{vid}{mark}", userData=v)
                self.version_combo.insertSeparator(self.version_combo.count())

        if snapshot_versions:
            add_group_title("Snapshots")
            for v in snapshot_versions:
                vid = v["id"]
                mark = "  [installed]" if vid in local_installed else ""
                self.version_combo.addItem(f"{vid}{mark}", userData=v)
            self.version_combo.insertSeparator(self.version_combo.count())

        if old_versions:
            add_group_title("Old versions")
            for v in old_versions:
                vid = v["id"]
                mark = "  [installed]" if vid in local_installed else ""
                self.version_combo.addItem(f"{vid}{mark}", userData=v)

        self.version_combo.setEnabled(True)

        restored = False
        remembered = self._remembered_version_id
        if remembered:
            for i in range(self.version_combo.count()):
                data = self.version_combo.itemData(i)
                if isinstance(data, dict) and data.get("id") == remembered:
                    self.version_combo.setCurrentIndex(i)
                    restored = True
                    self.log(f"✓ Restored the previously selected version: {remembered}")
                    break
        if not restored:
            # Index 0 is a separator or a "── group ──" title whenever
            # local versions are listed, and neither carries itemData --
            # landing on one leaves no version selected.
            first = next(
                (i for i in range(self.version_combo.count())
                 if isinstance(self.version_combo.itemData(i), dict)),
                None,
            )
            self.version_combo.setCurrentIndex(first if first is not None else 0)

        self.log(f"✓ Version list populated ({len(versions)} official + {len(local_only)} local)")

