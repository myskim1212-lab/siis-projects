"""감사 로그 패널 — 검색/필터/CSV 내보내기."""
import csv
from datetime import datetime

from PyQt5.QtCore import Qt
from PyQt5.QtWidgets import (
    QAbstractItemView, QComboBox, QFileDialog, QHBoxLayout, QHeaderView,
    QLabel, QLineEdit, QMessageBox, QPushButton, QTableWidget,
    QTableWidgetItem, QVBoxLayout, QWidget,
)

from services.audit_service import get_logs


class AuditLogPanel(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self._rows = []
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        fh = QHBoxLayout()
        fh.addWidget(QLabel('액션:'))
        self.c_action = QComboBox()
        self.c_action.addItems(['전체', 'deploy', 'undeploy', 'restart', 'shutdown', 'cancel', 'other'])
        self.c_action.currentTextChanged.connect(self._apply_filter)
        fh.addWidget(self.c_action)

        fh.addWidget(QLabel('인스턴스:'))
        self.e_instance = QLineEdit(); self.e_instance.setPlaceholderText('이름 검색')
        self.e_instance.textChanged.connect(self._apply_filter)
        fh.addWidget(self.e_instance)

        fh.addWidget(QLabel('키워드:'))
        self.e_keyword = QLineEdit(); self.e_keyword.setPlaceholderText('결과/비고 검색')
        self.e_keyword.textChanged.connect(self._apply_filter)
        fh.addWidget(self.e_keyword)

        btn_refresh = QPushButton('새로고침')
        btn_refresh.clicked.connect(self.refresh)
        btn_csv = QPushButton('CSV 내보내기')
        btn_csv.clicked.connect(self._export_csv)
        fh.addWidget(btn_refresh)
        fh.addWidget(btn_csv)
        layout.addLayout(fh)

        HEADERS = ['시간', '인스턴스', '액션', '파일', '결과', '비고']
        self.table = QTableWidget(0, len(HEADERS))
        self.table.setHorizontalHeaderLabels(HEADERS)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table.setAlternatingRowColors(True)
        layout.addWidget(self.table)

    def refresh(self):
        self._rows = get_logs(limit=2000)
        self._apply_filter()

    def _apply_filter(self):
        action_filter = self.c_action.currentText()
        inst_filter   = self.e_instance.text().strip().lower()
        kw_filter     = self.e_keyword.text().strip().lower()

        visible = []
        for r in self._rows:
            if action_filter != '전체' and r.get('action') != action_filter:
                continue
            if inst_filter and inst_filter not in (r.get('instance_name') or '').lower():
                continue
            combined = f"{r.get('result','')} {r.get('note','')}".lower()
            if kw_filter and kw_filter not in combined:
                continue
            visible.append(r)

        self.table.setRowCount(0)
        for r in visible:
            row = self.table.rowCount()
            self.table.insertRow(row)
            self.table.setItem(row, 0, QTableWidgetItem(r.get('created_at', '')))
            self.table.setItem(row, 1, QTableWidgetItem(r.get('instance_name', '')))
            self.table.setItem(row, 2, QTableWidgetItem(r.get('action', '')))
            self.table.setItem(row, 3, QTableWidgetItem(r.get('file_name', '')))
            result = r.get('result', '')
            item = QTableWidgetItem(result)
            if result == 'success':
                item.setForeground(Qt.darkGreen)
            elif result == 'failure':
                item.setForeground(Qt.red)
            self.table.setItem(row, 4, item)
            self.table.setItem(row, 5, QTableWidgetItem(r.get('note', '')))

    def _export_csv(self):
        if not self._rows:
            QMessageBox.information(self, '내보내기', '내보낼 데이터가 없습니다.')
            return
        path, _ = QFileDialog.getSaveFileName(
            self, 'CSV 저장',
            f'audit_{datetime.now().strftime("%Y%m%d_%H%M%S")}.csv',
            'CSV Files (*.csv)')
        if not path:
            return
        with open(path, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=self._rows[0].keys())
            writer.writeheader()
            writer.writerows(self._rows)
        QMessageBox.information(self, '내보내기 완료', f'저장됨: {path}')
