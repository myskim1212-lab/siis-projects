"""메인 윈도우 — 탭으로 모든 패널 통합."""
from PyQt5.QtCore import Qt
from PyQt5.QtWidgets import (
    QHBoxLayout, QMainWindow, QSplitter, QStatusBar,
    QTabWidget, QVBoxLayout, QWidget,
)

from ui.audit_log_panel import AuditLogPanel
from ui.deploy_panel import DeployPanel
from ui.instance_panel import InstancePanel
from ui.log_viewer import LogViewer
from ui.restart_panel import RestartPanel


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle('WSO2 Management Tool')
        self.setMinimumSize(1200, 700)
        self._build_ui()
        self._status('준비')

    def _build_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        root = QHBoxLayout(central)
        root.setContentsMargins(4, 4, 4, 4)

        self.inst_panel = InstancePanel()
        self.inst_panel.setMaximumWidth(360)

        self.tabs = QTabWidget()
        self.deploy_panel  = DeployPanel()
        self.restart_panel = RestartPanel()
        self.log_viewer    = LogViewer()
        self.audit_panel   = AuditLogPanel()

        self.tabs.addTab(self.deploy_panel,  '배포')
        self.tabs.addTab(self.restart_panel, '재시작/정지')
        self.tabs.addTab(self.log_viewer,    '로그 뷰어')
        self.tabs.addTab(self.audit_panel,   '감사 로그')

        splitter = QSplitter(Qt.Horizontal)
        splitter.addWidget(self.inst_panel)
        splitter.addWidget(self.tabs)
        splitter.setStretchFactor(1, 1)
        root.addWidget(splitter)

        self.inst_panel.instance_selected.connect(self._on_instance_selected)
        self.tabs.currentChanged.connect(self._on_tab_changed)
        self.setStatusBar(QStatusBar())

    def _on_instance_selected(self, inst):
        if inst is None:
            return
        if self.tabs.currentWidget() is self.log_viewer:
            self.log_viewer.open_instance(inst)
        self._status(f'선택: [{inst.type}] {inst.name} ({inst.host}:{inst.port})')

    def _on_tab_changed(self, _idx):
        widget = self.tabs.currentWidget()
        if hasattr(widget, 'reload_instances'):
            widget.reload_instances()
        if widget is self.audit_panel:
            widget.refresh()

    def _status(self, msg: str):
        self.statusBar().showMessage(msg)

    def closeEvent(self, event):
        self.log_viewer.closeEvent(event)
        super().closeEvent(event)
