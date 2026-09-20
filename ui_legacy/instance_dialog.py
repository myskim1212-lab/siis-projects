"""인스턴스 등록/수정 다이얼로그."""
from PyQt5.QtWidgets import (
    QCheckBox, QComboBox, QDialog, QDialogButtonBox, QFormLayout,
    QGroupBox, QLineEdit, QMessageBox, QSpinBox, QVBoxLayout,
)

from db.models import Instance


class InstanceDialog(QDialog):
    def __init__(self, parent=None, instance: Instance = None, group_id: int = None):
        super().__init__(parent)
        self.setWindowTitle('인스턴스 등록' if instance is None else '인스턴스 수정')
        self.setMinimumWidth(460)
        self._inst = instance or Instance(group_id=group_id or 0)
        self._build_ui()
        self._load()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        basic = QGroupBox('기본 정보')
        f = QFormLayout(basic)
        self.e_name = QLineEdit()
        self.c_type = QComboBox(); self.c_type.addItems(['MI', 'APIM'])
        self.e_host = QLineEdit()
        self.s_port = QSpinBox(); self.s_port.setRange(1, 65535); self.s_port.setValue(9164)
        self.e_desc = QLineEdit()
        f.addRow('이름 *', self.e_name)
        f.addRow('타입',   self.c_type)
        f.addRow('Host *', self.e_host)
        f.addRow('Port',   self.s_port)
        f.addRow('설명',   self.e_desc)
        layout.addWidget(basic)

        auth = QGroupBox('인증 / API')
        f2 = QFormLayout(auth)
        self.e_user      = QLineEdit('admin')
        self.e_pass      = QLineEdit(); self.e_pass.setEchoMode(QLineEdit.Password)
        self.e_token_url = QLineEdit()
        self.e_log_path  = QLineEdit('/opt/wso2mi/repository/logs/wso2carbon.log')
        f2.addRow('Admin 계정',    self.e_user)
        f2.addRow('Admin 패스워드', self.e_pass)
        f2.addRow('Token URL',    self.e_token_url)
        f2.addRow('로그 경로',    self.e_log_path)
        layout.addWidget(auth)

        ssh_box = QGroupBox('SSH 접속')
        f3 = QFormLayout(ssh_box)
        self.chk_ssh    = QCheckBox('SSH 접속 사용')
        self.e_ssh_host = QLineEdit()
        self.s_ssh_port = QSpinBox(); self.s_ssh_port.setRange(1, 65535); self.s_ssh_port.setValue(22)
        self.e_ssh_user = QLineEdit()
        self.e_ssh_pass = QLineEdit(); self.e_ssh_pass.setEchoMode(QLineEdit.Password)
        self.e_ssh_key  = QLineEdit()
        f3.addRow('', self.chk_ssh)
        f3.addRow('SSH Host (비우면 Host 사용)', self.e_ssh_host)
        f3.addRow('SSH Port',  self.s_ssh_port)
        f3.addRow('SSH 계정',  self.e_ssh_user)
        f3.addRow('SSH 패스워드', self.e_ssh_pass)
        f3.addRow('PEM 키 경로', self.e_ssh_key)
        layout.addWidget(ssh_box)

        self.chk_ssh.toggled.connect(self._toggle_ssh)
        self._toggle_ssh(False)

        btn = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        btn.accepted.connect(self._accept)
        btn.rejected.connect(self.reject)
        layout.addWidget(btn)

    def _toggle_ssh(self, checked: bool):
        for w in (self.e_ssh_host, self.s_ssh_port, self.e_ssh_user,
                  self.e_ssh_pass, self.e_ssh_key):
            w.setEnabled(checked)

    def _load(self):
        i = self._inst
        self.e_name.setText(i.name)
        self.c_type.setCurrentText(i.type)
        self.e_host.setText(i.host)
        self.s_port.setValue(i.port)
        self.e_desc.setText(i.description)
        self.e_user.setText(i.admin_user)
        self.e_pass.setText(i.admin_pass)
        self.e_token_url.setText(i.token_url)
        self.e_log_path.setText(i.log_path)
        self.chk_ssh.setChecked(i.ssh_enabled)
        self.e_ssh_host.setText(i.ssh_host)
        self.s_ssh_port.setValue(i.ssh_port)
        self.e_ssh_user.setText(i.ssh_user)
        self.e_ssh_pass.setText(i.ssh_pass)
        self.e_ssh_key.setText(i.ssh_key_path)
        self._toggle_ssh(i.ssh_enabled)

    def _accept(self):
        if not self.e_name.text().strip():
            QMessageBox.warning(self, '입력 오류', '이름을 입력하세요.')
            return
        if not self.e_host.text().strip():
            QMessageBox.warning(self, '입력 오류', 'Host를 입력하세요.')
            return
        i = self._inst
        i.name         = self.e_name.text().strip()
        i.type         = self.c_type.currentText()
        i.host         = self.e_host.text().strip()
        i.port         = self.s_port.value()
        i.description  = self.e_desc.text().strip()
        i.admin_user   = self.e_user.text().strip()
        i.admin_pass   = self.e_pass.text()
        i.token_url    = self.e_token_url.text().strip()
        i.log_path     = self.e_log_path.text().strip()
        i.ssh_enabled  = self.chk_ssh.isChecked()
        i.ssh_host     = self.e_ssh_host.text().strip()
        i.ssh_port     = self.s_ssh_port.value()
        i.ssh_user     = self.e_ssh_user.text().strip()
        i.ssh_pass     = self.e_ssh_pass.text()
        i.ssh_key_path = self.e_ssh_key.text().strip()
        self.accept()

    def get_instance(self) -> Instance:
        return self._inst
