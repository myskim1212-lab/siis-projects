"""실시간 로그 스트리밍 서비스.

인스턴스의 ssh_enabled=True 이면 Paramiko SSH tail -f,
아니면 SIISManagementApi의 로그 뷰어 API(/mi/monitor/v1/logviewer) 폴링으로 자동 전환.

API 방식은 이전 응답의 오프셋부터 이어서만 읽는 방식(MIClient.log_viewer_read)이라
WSO2 MI 기본 제공 로그 API(/logs?file=, 매 조회마다 파일 전체를 서버 메모리에 올림)와
달리 파일 크기와 무관하게 가볍다 — 그래서 과거에 있던 "파일이 너무 크면 API 방식
자체를 거부" 게이트(api_log_max_mb)는 더 이상 필요 없다.
"""
import threading
import time
from pathlib import PurePosixPath
from typing import Callable, Optional

import paramiko

from api.mi_client import MIClient
from db.models import Instance, is_mi_type

# callback 타입: (instance_id, line: str) -> None
LineCallback = Callable[[int, str], None]


class LogStreamer:
    """단일 인스턴스 로그 스트리머. 백그라운드 스레드에서 실행."""

    def __init__(self, inst: Instance, callback: LineCallback, tail_lines: int = 100,
                method: Optional[str] = None, log_filename: Optional[str] = None):
        self.inst = inst
        self.callback = callback
        self.tail_lines = tail_lines
        self.method = method  # None(자동)/'API'/'SSH' — 명시하면 그 방식으로 강제
        self.log_filename = log_filename  # API 방식에서 조회할 파일명 (없으면 inst.log_path 기준)
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self):
        chosen = self.method if self.method in ('API', 'SSH') else \
            ('SSH' if self.inst.ssh_enabled else 'API')
        print(f'[DEBUG][log-start] instance_id={self.inst.id} name={self.inst.name!r} '
              f'requested_method={self.method!r} chosen={chosen} ssh_enabled={self.inst.ssh_enabled} '
              f'type={self.inst.type} log_path={self.inst.log_path!r}')
        if chosen == 'SSH' and not self.inst.ssh_enabled:
            self.callback(self.inst.id, '[ERROR] SSH 접속이 설정되어 있지 않습니다 (인스턴스 설정에서 SSH를 켜주세요).')
            return
        if chosen == 'API' and not is_mi_type(self.inst.type):
            self.callback(self.inst.id, '[ERROR] APIM 인스턴스는 API 방식 로그 조회를 지원하지 않습니다 (SSH 필요).')
            return
        target = self._stream_ssh if chosen == 'SSH' else self._stream_api
        self._thread = threading.Thread(target=target, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=5)

    def _log_filename(self) -> str:
        if self.log_filename:
            return self.log_filename
        return PurePosixPath(self.inst.log_path).name or 'wso2carbon.log'

    # ── SSH tail -f ────────────────────────────────────────────────────────

    def _stream_ssh(self):
        inst = self.inst
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        try:
            connect_kwargs: dict = dict(
                hostname=inst.effective_ssh_host,
                port=inst.ssh_port,
                username=inst.ssh_user,
                timeout=10,
            )
            if inst.ssh_key_path:
                connect_kwargs['key_filename'] = inst.ssh_key_path
            else:
                connect_kwargs['password'] = inst.ssh_pass

            client.connect(**connect_kwargs)
            log_path = inst.log_path or '/opt/wso2mi/repository/logs/wso2carbon.log'
            cmd = f'tail -n {self.tail_lines} -f {log_path}'
            _, stdout, _ = client.exec_command(cmd, get_pty=False)

            for line in iter(stdout.readline, ''):
                if self._stop.is_set():
                    break
                self.callback(inst.id, line.rstrip('\n'))

        except Exception as e:
            self.callback(inst.id, f'[SSH ERROR] {e}')
        finally:
            client.close()

    # ── SIISManagementApi 로그 뷰어 API 폴링 (SSH 불가 시 폴백) ─────────────

    def _stream_api(self):
        """SIISManagementApi의 /mi/monitor/v1/logviewer를 폴링한다.

        MI 기본 제공 로그 API와 달리 이 API는 이전 응답의 nextOffset부터 이어서만
        읽으므로(MIClient.log_viewer_read), 매 폴링마다 "새로 추가된 줄"만 그대로
        받는다 — 과거처럼 전체를 다시 받아 seen_lines로 중복을 걸러낼 필요가 없다."""
        inst = self.inst
        log_filename = self._log_filename()
        mi = MIClient(inst)
        poll_interval = 3  # 초

        try:
            try:
                initial = mi.log_viewer_tail(log_filename, lines=self.tail_lines)
            except Exception as e:
                self.callback(inst.id, f'[API POLL ERROR] {e}')
                return
            if not initial.get('success', True):
                self.callback(inst.id, f"[ERROR] {initial.get('error', '로그 조회 실패')}")
                return
            for line in initial.get('lines', []):
                self.callback(inst.id, line)
            offset = initial.get('nextOffset', 0)

            while not self._stop.is_set():
                time.sleep(poll_interval)
                if self._stop.is_set():
                    break
                try:
                    chunk = mi.log_viewer_read(log_filename, offset=offset)
                    if not chunk.get('success', True):
                        self.callback(inst.id, f"[API POLL ERROR] {chunk.get('error', '로그 조회 실패')}")
                        continue
                    if chunk.get('rotated'):
                        self.callback(inst.id, f'[INFO] 로그 파일이 새로 시작되어 처음부터 다시 읽습니다: {log_filename}')
                    for line in chunk.get('lines', []):
                        self.callback(inst.id, line)
                    offset = chunk.get('nextOffset', offset)
                except Exception as e:
                    self.callback(inst.id, f'[API POLL ERROR] {e}')
        finally:
            mi.close()


class LogManager:
    """여러 인스턴스의 LogStreamer를 관리."""

    def __init__(self):
        self._streamers: dict[int, LogStreamer] = {}

    def start(self, inst: Instance, callback: LineCallback, tail_lines: int = 100,
             method: Optional[str] = None, log_filename: Optional[str] = None):
        self.stop(inst.id)
        s = LogStreamer(inst, callback, tail_lines, method, log_filename)
        self._streamers[inst.id] = s
        s.start()

    def stop(self, instance_id: int):
        s = self._streamers.pop(instance_id, None)
        if s:
            s.stop()

    def stop_all(self):
        for iid in list(self._streamers):
            self.stop(iid)
