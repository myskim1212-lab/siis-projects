"""실시간 로그 뷰어 — 인스턴스별 탭, SSH/API 자동 분기."""
from PyQt5.QtCore import Qt, pyqtSignal, QObject
from PyQt5.QtGui import QColor, QTextCharFormat, QTextCursor
from PyQt5.QtWidgets import (
    QHBoxLayout, QInputDialog, QLabel, QLineEdit, QPlainTextEdit,
    QPushButton, QTabWidget, QVBoxLayout, QWidget,
)

import db.database as db
from db.models import Instance
from services.log_service import LogManager


class _LineEmitter(QObject):
    line_received = pyqtSignal(int, str)


class LogTab(QWidget):
    def __init__(self, inst: Instance, manager: LogManager, emitter: _LineEmitter):
        super().__init__()
        self.inst = inst
        self.manager = manager
        self.emitter = emitter
        self._build_ui()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        th = QHBoxLayout()
        self.lbl_mode = QLabel('SSH' if self.inst.ssh_enabled else 'API 폴링')
        btn_start = QPushButton('시작')
        btn_stop  = QPushButton('중지')
        btn_clear = QPushButton('지우기')
        lbl_filter = QLabel('필터:')
        self.e_filter = QLineEdit(); self.e_filter.setPlaceholderText('키워드 필터')
        btn_start.clicked.connect(self.start)
        btn_stop.clicked.connect(self.stop)
        btn_clear.clicked.connect(self._clear)
        for w in (self.lbl_mode, btn_start, btn_stop, btn_clear, lbl_filter, self.e_filter):
            th.addWidget(w)
        th.addStretch()
        layout.addLayout(th)

        self.text = QPlainTextEdit()
        self.text.setReadOnly(True)
        self.text.setMaximumBlockCount(5000)
        self.text.setStyleSheet(
            'background:#0f1117;color:#e2e8f0;'
            'font-family:Consolas,monospace;font-size:12px;')
        layout.addWidget(self.text)

    def start(self):
        self.manager.start(self.inst, self._on_line)
        self.text.appendPlainText(f'--- [{self.inst.name}] 로그 스트리밍 시작 ---')

    def stop(self):
        self.manager.stop(self.inst.id)
        self.text.appendPlainText(f'--- [{self.inst.name}] 로그 스트리밍 중지 ---')

    def _clear(self):
        self.text.clear()

    def _on_line(self, inst_id: int, line: str):
        self.emitter.line_received.emit(inst_id, line)

    def append_line(self, line: str):
        kw = self.e_filter.text().strip().lower()
        if kw and kw not in line.lower():
            return

        cursor = self.text.textCursor()
        cursor.movePosition(QTextCursor.End)
        fmt = QTextCharFormat()

        ll = line.lower()
        if 'error' in ll or 'exception' in ll:
            fmt.setForeground(QColor('#f87171'))
        elif 'warn' in ll:
            fmt.setForeground(QColor('#facc15'))
        elif 'info' in ll:
            fmt.setForeground(QColor('#e2e8f0'))
        else:
            fmt.setForeground(QColor('#94a3b8'))

        cursor.insertText(line + '\n', fmt)
        self.text.setTextCursor(cursor)
        self.text.ensureCursorVisible()


class LogViewer(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.manager = LogManager()
        self.emitter = _LineEmitter()
        self.emitter.line_received.connect(self._dispatch_line)
        self._tabs = {}
        self._build_ui()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        th = QHBoxLayout()
        btn_add   = QPushButton('+ 탭 추가')
        btn_close = QPushButton('탭 닫기')
        btn_add.clicked.connect(self._add_tab_dialog)
        btn_close.clicked.connect(self._close_current_tab)
        th.addWidget(btn_add); th.addWidget(btn_close); th.addStretch()
        layout.addLayout(th)

        self.tabs = QTabWidget()
        self.tabs.setTabsClosable(True)
        self.tabs.tabCloseRequested.connect(self._close_tab)
        layout.addWidget(self.tabs)

    def open_instance(self, inst: Instance):
        if inst.id in self._tabs:
            self.tabs.setCurrentWidget(self._tabs[inst.id])
            return
        tab = LogTab(inst, self.manager, self.emitter)
        self._tabs[inst.id] = tab
        mode = 'SSH' if inst.ssh_enabled else 'API'
        self.tabs.addTab(tab, f'{inst.name} [{mode}]')
        self.tabs.setCurrentWidget(tab)

    def _add_tab_dialog(self):
        instances = db.get_all_instances()
        if not instances:
            return
        names = [f'[{i.type}] {i.name} ({i.host}:{i.port})' for i in instances]
        choice, ok = QInputDialog.getItem(self, '인스턴스 선택', '로그를 볼 인스턴스:', names, 0, False)
        if ok:
            self.open_instance(instances[names.index(choice)])

    def _close_tab(self, index: int):
        tab = self.tabs.widget(index)
        inst_id = next((iid for iid, t in self._tabs.items() if t is tab), None)
        if inst_id:
            self.manager.stop(inst_id)
            del self._tabs[inst_id]
        self.tabs.removeTab(index)

    def _close_current_tab(self):
        self._close_tab(self.tabs.currentIndex())

    def _dispatch_line(self, inst_id: int, line: str):
        tab = self._tabs.get(inst_id)
        if tab:
            tab.append_line(line)

    def closeEvent(self, event):
        self.manager.stop_all()
        super().closeEvent(event)
