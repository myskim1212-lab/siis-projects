"""재시작 / 정지 서비스.

MI  : Management API PATCH /server (status=restart|restartGracefully|shutdown|shutdownGracefully)
      또는 SSH sh wso2server.sh restart
APIM: SSH 전용 (Management API 미지원)
"""
from typing import Callable, List, Optional

import paramiko

from api.mi_client import MIClient
from db.models import Instance, is_mi_type
from services.audit_service import log_action

StatusCallback = Callable[[str], None]   # 진행 상황 텍스트 → UI 콜백


def _ssh_command(inst: Instance, cmd: str) -> tuple[int, str, str]:
    """SSH로 명령 실행. (exit_code, stdout, stderr) 반환."""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        kw: dict = dict(hostname=inst.effective_ssh_host, port=inst.ssh_port,
                        username=inst.ssh_user, timeout=15)
        if inst.ssh_key_path:
            kw['key_filename'] = inst.ssh_key_path
        else:
            kw['password'] = inst.ssh_pass
        client.connect(**kw)
        _, stdout, stderr = client.exec_command(cmd)
        exit_code = stdout.channel.recv_exit_status()
        return exit_code, stdout.read().decode(), stderr.read().decode()
    finally:
        client.close()


def _resolve_method(inst: Instance, method: Optional[str]) -> str:
    """method가 'API'/'SSH'로 명시되면 그대로 쓰되 실제로 가능한지 검증하고,
    None(자동)이면 기존 규칙대로 고른다 (SSH 설정돼 있으면 SSH, 아니면 API)."""
    if method in ('API', 'SSH'):
        chosen = method
    else:
        chosen = 'SSH' if (inst.ssh_enabled or not is_mi_type(inst.type)) else 'API'

    if chosen == 'API' and not is_mi_type(inst.type):
        raise RuntimeError('APIM 인스턴스는 API 방식 재시작/정지를 지원하지 않습니다 (SSH 필요).')
    if chosen == 'SSH' and not inst.ssh_enabled:
        raise RuntimeError('SSH 접속이 설정되어 있지 않습니다 (인스턴스 설정에서 SSH를 켜주세요).')
    return chosen


def _ssh_script_path(inst: Instance) -> str:
    """SSH 재시작/정지에 쓸 실행 스크립트 전체 경로. bin_path/service_script는 인스턴스
    설정에서 직접 지정한다 (log_path에서 추측하지 않음 — 예전엔 추측하다가 경로 계산도
    틀리고 스크립트 이름도 레거시(wso2server.sh)로 잘못 가정했었음). 실제로는 제품별로
    다르다: MI는 micro-integrator.sh, APIM은 api-manager.sh."""
    bin_path = (inst.bin_path or '/opt/wso2mi/bin').rstrip('/')
    script = inst.service_script or 'micro-integrator.sh'
    return f'{bin_path}/{script}'


def _restart_one(inst: Instance, cb: StatusCallback, graceful: bool = True,
                 method: Optional[str] = None) -> dict:
    mode = 'Graceful' if graceful else '즉시'
    try:
        chosen = _resolve_method(inst, method)
    except Exception as e:
        cb(f'[{inst.name}] ❌ 오류: {e}')
        return {'success': False, 'instance': inst.name, 'error': str(e), 'method': method or '', 'mode': mode}

    if chosen == 'SSH':
        cb(f'[{inst.name}] 재시작 요청 중... (SSH — graceful 옵션 미지원, {inst.service_script})')
    else:
        cb(f'[{inst.name}] 재시작 요청 중... ({mode}/API)')
    try:
        if chosen == 'API':
            mi = MIClient(inst)
            mi.restart(graceful=graceful)
            mi.close()
            cb(f'[{inst.name}] API 재시작 요청 완료')
        else:
            script = _ssh_script_path(inst)
            # nohup ... & 로 백그라운드 실행하면 셸 자체는 항상 exit 0을 돌려주므로
            # (스크립트가 없어도 마찬가지) 실행 전에 스크립트 존재 여부를 먼저 확인해
            # "명령은 성공했다는데 실제로는 아무 일도 안 일어나는" 상황을 방지한다.
            _, exist_out, _ = _ssh_command(inst, f'test -f {script} && echo OK || echo MISSING')
            if 'OK' not in exist_out:
                raise RuntimeError(f'재시작 스크립트를 찾을 수 없습니다: {script} (인스턴스 설정의 bin 경로/스크립트명을 확인하세요)')
            code, out, err = _ssh_command(inst, f'nohup {script} restart > /dev/null 2>&1 &')
            if code not in (0, None):
                raise RuntimeError(err or 'SSH restart failed')
            cb(f'[{inst.name}] SSH 재시작 명령 전송 완료')
        return {'success': True, 'instance': inst.name, 'method': chosen, 'mode': mode}
    except Exception as e:
        cb(f'[{inst.name}] ❌ 오류: {e}')
        return {'success': False, 'instance': inst.name, 'error': str(e), 'method': chosen, 'mode': mode}


def _shutdown_one(inst: Instance, cb: StatusCallback, graceful: bool = True,
                  method: Optional[str] = None) -> dict:
    mode = 'Graceful' if graceful else '즉시'
    try:
        chosen = _resolve_method(inst, method)
    except Exception as e:
        cb(f'[{inst.name}] ❌ 오류: {e}')
        return {'success': False, 'instance': inst.name, 'error': str(e), 'method': method or '', 'mode': mode}

    if chosen == 'SSH':
        cb(f'[{inst.name}] 정지 요청 중... (SSH — graceful 옵션 미지원, {inst.service_script})')
    else:
        cb(f'[{inst.name}] 정지 요청 중... ({mode}/API)')
    try:
        if chosen == 'API':
            mi = MIClient(inst)
            mi.shutdown(graceful=graceful)
            mi.close()
            cb(f'[{inst.name}] API 정지 요청 완료')
        else:
            script = _ssh_script_path(inst)
            code, out, err = _ssh_command(inst, f'{script} stop')
            if code not in (0, None):
                raise RuntimeError(err or 'SSH stop failed')
            cb(f'[{inst.name}] SSH 정지 완료')
        return {'success': True, 'instance': inst.name, 'method': chosen, 'mode': mode}
    except Exception as e:
        cb(f'[{inst.name}] ❌ 오류: {e}')
        return {'success': False, 'instance': inst.name, 'error': str(e), 'method': chosen, 'mode': mode}


# ── 공개 API ──────────────────────────────────────────────────────────────

def restart_all(instances: List[Instance], cb: StatusCallback,
                operator: str = 'admin', graceful: bool = False,
                method: Optional[str] = None) -> List[dict]:
    """일괄 재시작 (병렬). method: None(자동)/'API'/'SSH' — 명시하면 그 방식으로 강제."""
    import concurrent.futures
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(instances) or 1) as ex:
        futures = {ex.submit(_restart_one, inst, cb, graceful, method): inst for inst in instances}
        for f in concurrent.futures.as_completed(futures):
            results.append(f.result())
    _log_restart(instances, results, 'RESTART_ALL', operator)
    return results


def restart_selected(instances: List[Instance], cb: StatusCallback,
                     operator: str = 'admin') -> List[dict]:
    """지정 인스턴스 재시작 (일괄)."""
    return restart_all(instances, cb, operator)


def shutdown_instances(instances: List[Instance], cb: StatusCallback,
                       operator: str = 'admin', graceful: bool = True,
                       method: Optional[str] = None) -> List[dict]:
    """정지 (일괄). method: None(자동)/'API'/'SSH' — 명시하면 그 방식으로 강제."""
    results = []
    for inst in instances:
        results.append(_shutdown_one(inst, cb, graceful, method))
    _log_restart(instances, results, 'SHUTDOWN', operator)
    return results


def _log_restart(instances, results, action, operator):
    errors = [r.get('error', '') for r in results if not r['success']]
    overall = 'SUCCESS' if not errors else ('PARTIAL' if len(errors) < len(instances) else 'FAILED')
    parts = []
    for r in results:
        tag = f"{r.get('mode', '')}/{r.get('method', '')}"
        if r['success']:
            parts.append(f"{r['instance']}: 성공 [{tag}]")
        else:
            parts.append(f"{r['instance']}: 실패({r.get('error', '')}) [{tag}]")
    log_action(action, instances, overall, detail=', '.join(parts), operator=operator)
