"""인스턴스 / 그룹 관리 패널."""
import threading

from PyQt5.QtCore import Qt, pyqtSignal
from PyQt5.QtGui import QColor
from PyQt5.QtWidgets import (
    QAbstractItemView, QHBoxLayout, QInputDialog, QMenu, QMessageBox,
    QPushButton, QTreeWidget, QTreeWidgetItem, QVBoxLayout, QWidget,
)

import db.database as db
from db.models import Instance, InstanceGroup
from ui.instance_dialog import InstanceDialog


class InstancePanel(QWidget):
    instance_selected = pyqtSignal(object)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        bar = QHBoxLayout()
        btn_add_group = QPushButton('+ 그룹')
        btn_add_inst  = QPushButton('+ 인스턴스')
        btn_edit      = QPushButton('수정')
        btn_del       = QPushButton('삭제')
        btn_test      = QPushButton('연결 테스트')
        for b in (btn_add_group, btn_add_inst, btn_edit, btn_del, btn_test):
            bar.addWidget(b)
        bar.addStretch()
        layout.addLayout(bar)

        self.tree = QTreeWidget()
        self.tree.setHeaderLabels(['이름', '타입', 'Host:Port', '상태'])
        self.tree.setColumnWidth(0, 180)
        self.tree.setColumnWidth(1, 55)
        self.tree.setColumnWidth(2, 160)
        self.tree.setColumnWidth(3, 60)
        self.tree.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree.customContextMenuRequested.connect(self._context_menu)
        self.tree.currentItemChanged.connect(self._on_select)
        layout.addWidget(self.tree)

        btn_add_group.clicked.connect(self._add_group)
        btn_add_inst.clicked.connect(self._add_instance)
        btn_edit.clicked.connect(self._edit_selected)
        btn_del.clicked.connect(self._delete_selected)
        btn_test.clicked.connect(self._test_selected)

    def refresh(self):
        self.tree.clear()
        for g in db.get_all_groups():
            g_item = QTreeWidgetItem([g.name, g.type, '', ''])
            g_item.setData(0, Qt.UserRole, ('group', g))
            font = g_item.font(0); font.setBold(True); g_item.setFont(0, font)
            for inst in db.get_instances_by_group(g.id):
                i_item = QTreeWidgetItem([inst.name, inst.type,
                                          f'{inst.host}:{inst.port}', ''])
                i_item.setData(0, Qt.UserRole, ('instance', inst))
                g_item.addChild(i_item)
            self.tree.addTopLevelItem(g_item)
            g_item.setExpanded(True)

    def selected_instance(self) -> Instance:
        item = self.tree.currentItem()
        if item is None:
            return None
        data = item.data(0, Qt.UserRole)
        return data[1] if data and data[0] == 'instance' else None

    def selected_group_instances(self) -> list:
        item = self.tree.currentItem()
        if item is None:
            return []
        data = item.data(0, Qt.UserRole)
        if data and data[0] == 'group':
            return db.get_instances_by_group(data[1].id)
        if data and data[0] == 'instance':
            return [data[1]]
        return []

    def _on_select(self, current, _):
        data = current.data(0, Qt.UserRole) if current else None
        inst = data[1] if data and data[0] == 'instance' else None
        self.instance_selected.emit(inst)

    def _add_group(self):
        name, ok = QInputDialog.getText(self, '그룹 추가', '그룹 이름:')
        if not ok or not name.strip():
            return
        type_, ok2 = QInputDialog.getItem(self, '타입 선택', '인스턴스 타입:', ['MI', 'APIM'], 0, False)
        if not ok2:
            return
        db.save_group(InstanceGroup(name=name.strip(), type=type_))
        self.refresh()

    def _add_instance(self):
        item = self.tree.currentItem()
        group_id = None
        if item:
            data = item.data(0, Qt.UserRole)
            if data and data[0] == 'group':
                group_id = data[1].id
            elif data and data[0] == 'instance':
                group_id = data[1].group_id

        if group_id is None:
            groups = db.get_all_groups()
            if not groups:
                QMessageBox.warning(self, '오류', '먼저 그룹을 추가하세요.')
                return
            names = [g.name for g in groups]
            chosen, ok = QInputDialog.getItem(self, '그룹 선택', '그룹:', names, 0, False)
            if not ok:
                return
            group_id = next(g.id for g in groups if g.name == chosen)

        dlg = InstanceDialog(self, group_id=group_id)
        if dlg.exec_():
            db.save_instance(dlg.get_instance())
            self.refresh()

    def _edit_selected(self):
        item = self.tree.currentItem()
        if not item:
            return
        data = item.data(0, Qt.UserRole)
        if data and data[0] == 'instance':
            dlg = InstanceDialog(self, instance=data[1])
            if dlg.exec_():
                db.save_instance(dlg.get_instance())
                self.refresh()
        elif data and data[0] == 'group':
            g = data[1]
            name, ok = QInputDialog.getText(self, '그룹 수정', '그룹 이름:', text=g.name)
            if ok and name.strip():
                g.name = name.strip()
                db.save_group(g)
                self.refresh()

    def _delete_selected(self):
        item = self.tree.currentItem()
        if not item:
            return
        data = item.data(0, Qt.UserRole)
        if data and data[0] == 'instance':
            inst = data[1]
            if QMessageBox.question(self, '삭제 확인',
                    f'인스턴스 [{inst.name}]을 삭제하시겠습니까?') == QMessageBox.Yes:
                db.delete_instance(inst.id)
                self.refresh()
        elif data and data[0] == 'group':
            g = data[1]
            if QMessageBox.question(self, '삭제 확인',
                    f'그룹 [{g.name}] 및 하위 인스턴스를 모두 삭제하시겠습니까?') == QMessageBox.Yes:
                db.delete_group(g.id)
                self.refresh()

    def _test_selected(self):
        instances = self.selected_group_instances()
        if not instances:
            QMessageBox.information(self, '연결 테스트', '인스턴스를 선택하세요.')
            return

        def run():
            for inst in instances:
                item = self._find_item(inst.id)
                if inst.type == 'MI':
                    from api.mi_client import MIClient
                    ok = MIClient(inst).ping()
                else:
                    from api.apim_client import APIMClient
                    ok = APIMClient(inst).ping()
                if item:
                    item.setText(3, 'UP' if ok else 'DOWN')
                    item.setForeground(3, QColor('#4ade80' if ok else '#f87171'))

        threading.Thread(target=run, daemon=True).start()

    def _find_item(self, instance_id: int):
        for i in range(self.tree.topLevelItemCount()):
            g_item = self.tree.topLevelItem(i)
            for j in range(g_item.childCount()):
                child = g_item.child(j)
                data = child.data(0, Qt.UserRole)
                if data and data[0] == 'instance' and data[1].id == instance_id:
                    return child
        return None

    def _context_menu(self, pos):
        item = self.tree.itemAt(pos)
        if not item:
            return
        menu = QMenu(self)
        data = item.data(0, Qt.UserRole)
        if data and data[0] == 'instance':
            menu.addAction('수정', self._edit_selected)
            menu.addAction('삭제', self._delete_selected)
            menu.addSeparator()
            menu.addAction('연결 테스트', self._test_selected)
        elif data and data[0] == 'group':
            menu.addAction('그룹 수정', self._edit_selected)
            menu.addAction('그룹 삭제', self._delete_selected)
            menu.addSeparator()
            menu.addAction('인스턴스 추가', self._add_instance)
            menu.addAction('전체 연결 테스트', self._test_selected)
        menu.exec_(self.tree.viewport().mapToGlobal(pos))
