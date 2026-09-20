"""재시작 / 정지 패널."""
import threading

from PyQt5.QtCore import Qt
from PyQt5.QtWidgets import (
    QAbstractItemView, QGroupBox, QHBoxLayout, QLabel,
    QListWidget, QListWidgetItem, QMessageBox, QPushButton,
    QSpinBox, QTextEdit, QVBoxLayout, QWidget,
)

import db.database as db
from services.restart_service import restart_all, restart_sequential, shutdown_instances


class RestartPanel(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._build_ui()
        self.reload_instances()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        inst_box = QGroupBox('대상 인스턴스')
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

        seq_box = QGroupBox('순차 재시작 옵션')
        sh = QHBoxLayout(seq_box)
        sh.addWidget(QLabel('인스턴스 간 대기(초):'))
        self.s_wait = QSpinBox(); self.s_wait.setRange(0, 300); self.s_wait.setValue(10)
        sh.addWidget(self.s_wait); sh.addStretch()
        layout.addWidget(seq_box)

        ah = QHBoxLayout()
        actions = [
            ('일괄 재시작',   '#2563eb', self._restart_all),
            ('순차 재시작',   '#0891b2', self._restart_seq),
            ('Graceful 정지', '#7c3aed', self._shutdown),
        ]
        for label, color, slot in actions:
            b = QPushButton(label)
            b.setStyleSheet(f'background:{color};color:white;font-weight:bold;')
            b.clicked.connect(slot)
            ah.addWidget(b)
        ah.addStretch()
        layout.addLayout(ah)

        self.log = QTextEdit(); self.log.setReadOnly(True)
        layout.addWidget(self.log)

    def reload_instances(self):
        self.inst_list.clear()
        for g in db.get_all_groups():
            for inst in db.get_instances_by_group(g.id):
                item = QListWidgetItem(
                    f'[{g.name}] {inst.name}  ({inst.type} / {inst.host}:{inst.port})')
                item.setData(Qt.UserRole, inst)
                self.inst_list.addItem(item)

    def _selected(self):
        return [item.data(Qt.UserRole) for item in self.inst_list.selectedItems()]

    def _cb(self, msg: str):
        self.log.append(msg)

    def _confirm(self, action: str, instances) -> bool:
        return QMessageBox.question(
            self, f'{action} 확인',
            f'선택된 {len(instances)}개 인스턴스를 {action}하시겠습니까?'
        ) == QMessageBox.Yes

    def _restart_all(self):
        instances = self._selected()
        if not instances or not self._confirm('일괄 재시작', instances):
            return
        threading.Thread(target=restart_all, args=(instances, self._cb), daemon=True).start()

    def _restart_seq(self):
        instances = self._selected()
        if not instances or not self._confirm('순차 재시작', instances):
            return
        wait = self.s_wait.value()
        threading.Thread(target=restart_sequential,
                         args=(instances, self._cb, wait), daemon=True).start()

    def _shutdown(self):
        instances = self._selected()
        if not instances or not self._confirm('Graceful 정지', instances):
            return
        threading.Thread(target=shutdown_instances, args=(instances, self._cb), daemon=True).start()
