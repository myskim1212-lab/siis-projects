"""WSO2 APIM Admin REST API 래퍼 (v4.x 기준)."""
from pathlib import Path

from api.base_client import ApiClient
from db.models import Instance, is_apim_type


class APIMClient:
    def __init__(self, inst: Instance):
        assert is_apim_type(inst.type)
        self._c = ApiClient(inst)

    # ── 상태 ───────────────────────────────────────────────────────────────

    def ping(self) -> bool:
        try:
            self._c.get('/throttling/policies/application')
            return True
        except Exception:
            return False

    # ── API 목록 ───────────────────────────────────────────────────────────

    def list_apis(self) -> list[dict]:
        return self._c.get('/apis').json().get('list', [])

    # ── 재시작 / 정지 (APIM은 Management API 미제공 → SSH 전용) ───────────

    def restart(self):
        raise NotImplementedError('APIM restart must be done via SSH')

    def shutdown(self):
        raise NotImplementedError('APIM shutdown must be done via SSH')

    # ── 로그 레벨 ─────────────────────────────────────────────────────────

    def get_log_levels(self) -> list[dict]:
        return self._c.get('/system-scopes/system-scopes').json().get('list', [])

    def close(self):
        self._c.close()
