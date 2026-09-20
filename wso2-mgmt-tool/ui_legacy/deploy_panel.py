"""배포 패널 — 파일 선택 → 인스턴스/그룹 선택 → 배포."""
import threading
from pathlib import Path

from PyQt5.QtCore import Qt, pyqtSignal
from PyQt5.QtWidgets import (
    QAbstractItemView, QFileDialog, QGroupBox, QHBoxLayout,
    QLabel, QListWidget, QListWidgetItem, QMessageBox,
    QPushButton, QTextEdit, QVBoxLayout, QWidget,
)

import db.database as db
from services.deploy_service import deploy_to_instances, undeploy_from_instances


class DeployPanel(QWidget):
    status_updated = pyqtSignal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._selected_file = None
        self._build_ui()
        self.reload_instances()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        file_box = QGroupBox('배포 파일')
        fv = QVBoxLayout(file_box)
        fh = QHBoxLayout()
        self.lbl_file = QLabel('선택된 파일 없음')
        btn_browse = QPushButton('파일 선택...')
        btn_browse.clicked.connect(self._browse_file)
        fh.addWidget(self.lbl_file, 1)
        fh.addWidget(btn_browse)
        fv.addLayout(fh)
        layout.addWidget(file_box)

        inst_box = QGroupBox('대상 인스턴스 (다중 선택 가능)')
        iv = QVBoxLayout(inst_box)
        self.inst_list = QListWidget()
        self.inst_list.setSelectionMode(QAbstractItemView.MultiSelection)
        ih = QHBoxLayout()
        btn_all  = QPushButton('전체 선택')
        btn_none = QPushButton('선택 해제')
        btn_all.clicked.connect(self.inst_list.selectAll)
        btn_none.clicked.connect(self.inst_list.clearSelection)
        ih.addWidget(btn_all); ih.addWidget(btn_none); ih.addStretch()
        iv.addLayout(ih)
        iv.addWidget(self.inst_list)
        layout.addWidget(inst_box)

        ah = QHBoxLayout()
        btn_deploy   = QPushButton('배포')
        btn_undeploy = QPushButton('삭제')
        btn_deploy.setStyleSheet('background:#2563eb;color:white;font-weight:bold;')
        btn_undeploy.setStyleSheet('background:#dc2626;color:white;')
        btn_deploy.clicked.connect(self._deploy)
        btn_undeploy.clicked.connect(self._undeploy)
        ah.addWidget(btn_deploy); ah.addWidget(btn_undeploy); ah.addStretch()
        layout.addLayout(ah)

        self.log = QTextEdit()
        self.log.setReadOnly(True)
        self.log.setMaximumHeight(180)
        layout.addWidget(self.log)

    def reload_instances(self):
        self.inst_list.clear()
        for g in db.get_all_groups():
            for inst in db.get_instances_by_group(g.id):
                item = QListWidgetItem(f'[{g.name}] {inst.name}  ({inst.host}:{inst.port})')
                item.setData(Qt.UserRole, inst)
                self.inst_list.addItem(item)

    def _browse_file(self):
        path, _ = QFileDialog.getOpenFileName(
            self, '배포 파일 선택', '',
            'WSO2 Artifacts (*.car *.xml *.jar);;All Files (*)')
        if path:
            self._selected_file = Path(path)
            self.lbl_file.setText(str(self._selected_file))

    def _selected_instances(self):
        return [item.data(Qt.UserRole) for item in self.inst_list.selectedItems()]

    def _deploy(self):
        if not self._selected_file:
            QMessageBox.warning(self, '오류', '배포 파일을 선택하세요.')
            return
        instances = self._selected_instances()
        if not instances:
            QMessageBox.warning(self, '오류', '대상 인스턴스를 선택하세요.')
            return
        if QMessageBox.question(self, '배포 확인',
                f'{len(instances)}개 인스턴스에 [{self._selected_file.name}]을 배포하시겠습니까?'
                ) != QMessageBox.Yes:
            return
        self.log.append(f'배포 시작: {self._selected_file.name} -> {len(instances)}개 인스턴스')
        file_path = self._selected_file

        def run():
            results = deploy_to_instances(instances, file_path)
            for r in results:
                icon = 'OK' if r['success'] else 'FAIL'
                self.log.append(f'  [{icon}] {r["instance"]}  {r.get("error", "")}')
            self.log.append('배포 완료')

        threading.Thread(target=run, daemon=True).start()

    def _undeploy(self):
        instances = self._selected_instances()
        if not instances:
            QMessageBox.warning(self, '오류', '대상 인스턴스를 선택하세요.')
            return
        if not self._selected_file:
            QMessageBox.warning(self, '오류', '삭제할 파일을 선택하세요.')
            return
        app_name = self._selected_file.name
        if QMessageBox.question(self, '삭제 확인',
                f'{len(instances)}개 인스턴스에서 [{app_name}]을 삭제하시겠습니까?'
                ) != QMessageBox.Yes:
            return

        def run():
            results = undeploy_from_instances(instances, app_name)
            for r in results:
                icon = 'OK' if r['success'] else 'FAIL'
                self.log.append(f'  [{icon}] {r["instance"]}  {r.get("error", "")}')

        threading.Thread(target=run, daemon=True).start()
