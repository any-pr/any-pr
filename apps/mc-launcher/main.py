"""
MC Launcher Test Window v3.0 (final)
- Version dropdown (BMCLAPI official + locally installed versions grouped)
- Vanilla download
- Forge download + install
- Fabric download + install (loader + API)
- Launch vanilla + Forge + Fabric (inheritsFrom merging)
- Log filtering (hide noise)
- Live log output

Changes in v3.0:
- Restored the Forge button (coexists with Fabric)
- Added log filtering: hides noise such as "Saving chunks" and "Time elapsed"
"""


import sys
from PyQt6.QtWidgets import (
    QApplication,
)
from mc_launcher.ui import MainWindow


def main():
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    window = MainWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
