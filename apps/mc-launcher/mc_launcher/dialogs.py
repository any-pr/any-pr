from PyQt6.QtWidgets import (
    QVBoxLayout, QLabel, QMessageBox, QComboBox, QDialog, QDialogButtonBox, QCheckBox,
)
from PyQt6.QtGui import QFont


# ============================================================
# Forge selection dialog
# ============================================================
class ForgeSelectDialog(QDialog):
    def __init__(self, mc_version, forge_list, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"Select Forge Version - MC {mc_version}")
        self.resize(480, 380)
        self.selected = None

        layout = QVBoxLayout(self)
        title = QLabel(f"Forge versions available for MC {mc_version} ({len(forge_list)} found)")
        title.setFont(QFont("Microsoft YaHei", 10, QFont.Weight.Bold))
        layout.addWidget(title)

        self.combo = QComboBox()
        for item in forge_list:
            label = f"{item['version']}  (build {item['build']})  {item.get('modified', '')[:10]}"
            self.combo.addItem(label, userData=item)
        layout.addWidget(self.combo)

        self.info = QLabel("")
        self.info.setWordWrap(True)
        layout.addWidget(self.info)
        self.combo.currentIndexChanged.connect(self._on_change)
        self._on_change()
        layout.addStretch()

        btn_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        btn_box.accepted.connect(self._on_ok)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

    def _on_change(self):
        item = self.combo.currentData()
        if item:
            self.info.setText(
                f"MC version: {item['mcversion']}\n"
                f"Forge version: {item['version']}\n"
                f"Build: {item['build']}\n"
                f"Released: {item.get('modified', '')}"
            )

    def _on_ok(self):
        self.selected = self.combo.currentData()
        self.accept()


# ============================================================
# Fabric selection dialog
# ============================================================
class FabricSelectDialog(QDialog):
    def __init__(self, mc_version, loader_list, api_list, parent=None):
        super().__init__(parent)
        self.setWindowTitle(f"Install Fabric - MC {mc_version}")
        self.resize(540, 480)
        self.selected = None

        layout = QVBoxLayout(self)
        title = QLabel(f"Install Fabric - MC {mc_version}")
        title.setFont(QFont("Microsoft YaHei", 11, QFont.Weight.Bold))
        layout.addWidget(title)

        layout.addWidget(QLabel("Fabric Loader"))
        self.loader_combo = QComboBox()
        for item in loader_list:
            stable = " [stable]" if item.get("stable") else ""
            self.loader_combo.addItem(f"{item['version']}{stable}", userData=item)
        layout.addWidget(self.loader_combo)

        self.loader_info = QLabel("")
        layout.addWidget(self.loader_info)

        layout.addWidget(QLabel("Fabric API"))
        self.api_combo = QComboBox()
        self.api_combo.addItem("(do not install Fabric API)", userData=None)
        for item in api_list:
            date = item.get("date_published", "")[:10]
            label = f"{item['version_number']}  ({date})"
            self.api_combo.addItem(label, userData=item)
        layout.addWidget(self.api_combo)

        self.api_info = QLabel("")
        layout.addWidget(self.api_info)

        self.isolated_check = QCheckBox("Enable version isolation (keep mods inside the version folder)")
        self.isolated_check.setChecked(False)
        layout.addWidget(self.isolated_check)

        hint = QLabel(
            "Note:\n"
            "- isolation off -> mods go to .minecraft/mods/\n"
            "- isolation on  -> mods go to versions/<version>/mods/"
        )
        hint.setStyleSheet("color: #888; font-size: 11px;")
        layout.addWidget(hint)

        layout.addStretch()

        btn_box = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        btn_box.accepted.connect(self._on_ok)
        btn_box.rejected.connect(self.reject)
        layout.addWidget(btn_box)

        self.loader_combo.currentIndexChanged.connect(self._update_loader_info)
        self.api_combo.currentIndexChanged.connect(self._update_api_info)
        self._update_loader_info()
        self._update_api_info()

    def _update_loader_info(self):
        item = self.loader_combo.currentData()
        if item:
            self.loader_info.setText(
                f"  Version: {item['version']}\n"
                f"  Stable: {'yes' if item.get('stable') else 'no'}"
            )

    def _update_api_info(self):
        item = self.api_combo.currentData()
        if item:
            size_mb = item.get("size", 0) / 1024 / 1024
            self.api_info.setText(
                f"  File name: {item['filename']}\n"
                f"  Size: {size_mb:.1f} MB\n"
                f"  Published: {item.get('date_published', '')[:10]}"
            )
        else:
            self.api_info.setText("  (Fabric API not installed)")

    def _on_ok(self):
        loader = self.loader_combo.currentData()
        api = self.api_combo.currentData()
        isolated = self.isolated_check.isChecked()
        if not loader:
            QMessageBox.warning(self, "Notice", "Please select a Fabric Loader version")
            return
        self.selected = {
            "loader": loader,
            "api": api,
            "isolated": isolated,
        }
        self.accept()
