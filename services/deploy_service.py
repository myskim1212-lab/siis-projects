"""배포 / 삭제 / 롤백 서비스."""
import base64
import re
import shutil
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path
from typing import List, Optional

import paramiko

from api.mi_client import MIClient
from db.database import add_deployment, get_deployments
from db.models import DeploymentHistory, Instance, is_mi_type
from services.audit_service import log_action

BACKUP_DIR = Path(__file__).parent.parent / 'data' / 'backups'


def _sftp_client(inst: Instance) -> paramiko.SSHClient:
    """SSH 연결 생성 (호출자가 반드시 close() 해야 함). JAR 배포/조회/백업/삭제가 모두
    같은 인증 정보(ssh_host/port/user/pass 또는 key)를 공유하므로 연결 로직을 공용화한다."""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    kw: dict = dict(hostname=inst.effective_ssh_host, port=inst.ssh_port,
                    username=inst.ssh_user, timeout=15)
    if inst.ssh_key_path:
        kw['key_filename'] = inst.ssh_key_path
    else:
        kw['password'] = inst.ssh_pass
    client.connect(**kw)
    return client


def _sftp_upload(inst: Instance, local_path: Path, remote_dir: str) -> str:
    """SFTP로 JAR 등 파일을 원격 디렉터리에 업로드. 반환값: 업로드된 원격 전체 경로.
    Management API에는 lib 디렉터리에 JAR을 올릴 수 있는 엔드포인트가 없어서 SSH/SFTP로 직접 전송한다."""
    client = _sftp_client(inst)
    try:
        sftp = client.open_sftp()
        try:
            remote_path = f"{remote_dir.rstrip('/')}/{local_path.name}"
            sftp.put(str(local_path), remote_path)
            return remote_path
        finally:
            sftp.close()
    finally:
        client.close()


def _sftp_list_jars(inst: Instance) -> list[dict]:
    """lib_path 디렉터리의 .jar 파일 목록 (JAR 삭제 대상 조회용)."""
    client = _sftp_client(inst)
    try:
        sftp = client.open_sftp()
        try:
            entries = sftp.listdir_attr(inst.lib_path)
            return [
                {'name': e.filename, 'size': e.st_size, 'mtime': e.st_mtime}
                for e in entries if e.filename.lower().endswith('.jar')
            ]
        finally:
            sftp.close()
    finally:
        client.close()


def _sftp_list_sequences(inst: Instance) -> list[dict]:
    """sequence_path 디렉터리의 .xml 파일 목록 (시퀀스 삭제 대상 조회용)."""
    client = _sftp_client(inst)
    try:
        sftp = client.open_sftp()
        try:
            entries = sftp.listdir_attr(inst.sequence_path)
            return [
                {'name': e.filename, 'size': e.st_size, 'mtime': e.st_mtime}
                for e in entries if e.filename.lower().endswith('.xml')
            ]
        finally:
            sftp.close()
    finally:
        client.close()


def _sftp_download(inst: Instance, remote_path: str, local_path: Path):
    """원격 파일을 로컬로 다운로드 (삭제/덮어쓰기 전 백업용)."""
    client = _sftp_client(inst)
    try:
        sftp = client.open_sftp()
        try:
            sftp.get(remote_path, str(local_path))
        finally:
            sftp.close()
    finally:
        client.close()


def _sftp_remove(inst: Instance, remote_path: str):
    client = _sftp_client(inst)
    try:
        sftp = client.open_sftp()
        try:
            sftp.remove(remote_path)
        finally:
            sftp.close()
    finally:
        client.close()


def _sftp_exists(inst: Instance, remote_path: str) -> bool:
    client = _sftp_client(inst)
    try:
        sftp = client.open_sftp()
        try:
            sftp.stat(remote_path)
            return True
        except FileNotFoundError:
            return False
        finally:
            sftp.close()
    finally:
        client.close()


def _api_backup_existing(inst: Instance, artifact_type: str, file_name: str, ts: Optional[str],
                          backup_name: Optional[str] = None) -> str:
    """SIISManagementApi(filedeploy)로 기존 파일을 다운로드해 로컬 백업 후 경로를 반환한다
    (없으면 빈 문자열). 백업 실패해도 배포/삭제 자체는 계속 진행해야 하므로 예외를 삼킨다
    (_sftp_download 실패를 조용히 넘기는 것과 동일한 정책).

    backup_name: 배포 전 덮어쓸 기존 파일 백업은 'existing_{name}'(재배포/롤백용과
    구분), 단순 삭제 전 백업은 원본 이름 그대로 — 호출부가 지정한다."""
    try:
        mi = MIClient(inst)
        try:
            res = mi.file_deploy_download(artifact_type, file_name)
        finally:
            mi.close()
        if res.get('exists'):
            bp = _backup_path(inst, backup_name or file_name, ts)
            bp.write_bytes(base64.b64decode(res['content']))
            return str(bp)
    except Exception:
        pass
    return ''


def _api_deploy(inst: Instance, artifact_type: str, file_path: Path) -> dict:
    """SSH 없이 SIISManagementApi(filedeploy)로 배포한다. 대상 서버가 자기 자신의
    carbon.home 기준 lib/시퀀스 디렉터리에 직접 쓰므로 inst.lib_path/sequence_path는
    쓰지 않는다(SSH 방식과 달리 서버가 스스로 경로를 안다)."""
    mi = MIClient(inst)
    try:
        res = mi.file_deploy_upload(artifact_type, file_path.name, file_path.read_bytes())
    finally:
        mi.close()
    if not res.get('success'):
        raise RuntimeError(res.get('error') or 'API 배포 실패')
    return res


def _api_undeploy(inst: Instance, artifact_type: str, file_name: str) -> None:
    """SSH 없이 SIISManagementApi(filedeploy)로 삭제한다."""
    mi = MIClient(inst)
    try:
        res = mi.file_deploy_delete(artifact_type, file_name)
    finally:
        mi.close()
    if not res.get('success'):
        raise RuntimeError(res.get('error') or 'API 삭제 실패')


def _safe_name(name: str) -> str:
    """파일시스템에 못 쓰는 문자를 치환 (인스턴스명은 자유 입력이라 방어 필요)."""
    return re.sub(r'[\\/:*?"<>|]', '_', name).strip() or 'unnamed'


def _backup_path(inst: Instance, artifact_name: str, ts: Optional[str] = None) -> Path:
    """{타임스탬프}/{인스턴스명_id{ID}}/{파일명} 구조.
    타임스탬프를 상위에 둬서 탐색기에서 날짜순으로 훑어보기 쉽게 하고, 인스턴스명은
    가독성을 위해 넣되 이름 중복/변경에 안전하도록 인스턴스 ID를 항상 같이 붙인다.
    여러 인스턴스에 한 번에 배포할 때는 ts를 배치 단위로 공유해 같은 타임스탬프
    폴더 아래 인스턴스별 하위 폴더로 묶이게 한다 (ts 미지정 시 단건 배포로 간주해 새로 생성)."""
    ts = ts or datetime.now().strftime('%Y%m%d_%H%M%S')
    d = BACKUP_DIR / ts / f'{_safe_name(inst.name)}_id{inst.id}'
    d.mkdir(parents=True, exist_ok=True)
    return d / artifact_name


def _xml_root_local_name(file_path: Path) -> Optional[str]:
    """XML 루트 엘리먼트의 로컬 이름(네임스페이스 접두어 제외)을 반환한다.
    파싱 실패(XML이 아니거나 깨짐) 시 None."""
    try:
        tag = ET.parse(file_path).getroot().tag
        return tag.split('}')[-1] if '}' in tag else tag
    except ET.ParseError:
        return None


def _artifact_type(file_path: Path) -> str:
    ext = file_path.suffix.lower()
    if ext == '.car':
        return 'CAR'
    if ext == '.xml':
        # .xml은 시퀀스 말고도 Proxy Service/Endpoint/API/Local Entry/Message
        # Processor 등 다른 Synapse 아티팩트도 전부 같은 확장자를 쓴다. 확장자만으로
        # 판단하면 실수로 다른 아티팩트를 시퀀스 hot-deploy 경로에 잘못 올릴 수 있어,
        # 실제 루트 엘리먼트가 <sequence>인지 내용까지 확인한다.
        return 'SEQUENCE' if _xml_root_local_name(file_path) == 'sequence' else 'UNKNOWN'
    if ext == '.jar':
        return 'JAR'
    return 'UNKNOWN'


# ── 단일 인스턴스 배포 ────────────────────────────────────────────────────

def deploy_to_instance(inst: Instance, file_path: Path, operator: str = 'admin',
                       reason: str = '', ts: Optional[str] = None) -> dict:
    """단일 인스턴스에 아티팩트 배포. 결과 dict 반환.

    CAR은 Management API(/applications)로, JAR은 Management API에 대응 엔드포인트가
    없어 SSH/SFTP로 lib_path에 직접 업로드한다. 아티팩트 종류와 무관하게 배포 시도
    전에 항상 로컬 백업 디렉터리(data/backups/)에 실제 파일을 복사해둔다.

    ts: 배치 배포(deploy_to_instances)에서 여러 인스턴스가 같은 타임스탬프 폴더를
        공유하도록 넘기는 값. 단건 호출(롤백 등)은 생략하면 새로 생성된다.
    """
    if not is_mi_type(inst.type):
        return {'success': False, 'instance': inst.name, 'instance_id': inst.id,
                'error': 'APIM 배포는 아직 지원되지 않습니다 (추후 지원 예정)'}

    artifact_type = _artifact_type(file_path)

    backup_path_str = ''
    try:
        bp = _backup_path(inst, file_path.name, ts)
        shutil.copy2(file_path, bp)
        backup_path_str = str(bp)
    except Exception:
        pass  # 백업 실패해도 배포 자체는 계속 시도 (원본 파일은 그대로 남아있음)

    # 배포 직전 서버에 이미 있던 파일(있다면)을 별도로 백업해둔다 — 위 backup_path는
    # "지금 배포하는 새 파일"이라 재배포에만 쓸 수 있고, 배포 전 상태로 되돌리는
    # 진짜 롤백에는 이 previous_backup_path가 필요하다.
    previous_backup_path_str = ''

    try:
        if artifact_type == 'CAR':
            mi = MIClient(inst)
            try:
                existing = next((a for a in mi.list_apps() if a['file_name'] == file_path.name), None)
                if existing:
                    content = mi.download_app(file_path.name)
                    ebp = _backup_path(inst, f'existing_{file_path.name}', ts)
                    ebp.write_bytes(content)
                    previous_backup_path_str = str(ebp)
            except Exception:
                pass  # 기존 파일 백업 실패해도 배포는 계속 진행
            result = mi.deploy_app(file_path)
            mi.close()
        elif artifact_type == 'JAR':
            if inst.ssh_enabled:
                if not inst.lib_path:
                    raise RuntimeError('인스턴스에 lib 경로가 설정되어 있지 않습니다.')
                remote_target = f"{inst.lib_path.rstrip('/')}/{file_path.name}"
                try:
                    if _sftp_exists(inst, remote_target):
                        existing_bp = _backup_path(inst, f'existing_{file_path.name}', ts)
                        _sftp_download(inst, remote_target, existing_bp)
                        previous_backup_path_str = str(existing_bp)
                except Exception:
                    pass  # 기존 파일 백업 실패해도 배포는 계속 진행
                remote_path = _sftp_upload(inst, file_path, inst.lib_path)
                result = {'remote_path': remote_path}
            else:
                # SSH가 안 되는 인스턴스는 SIISManagementApi(filedeploy)로 대체 —
                # 대상 서버 자신이 carbon.home 기준 lib 디렉터리에 직접 쓴다.
                previous_backup_path_str = _api_backup_existing(inst, 'JAR', file_path.name, ts, backup_name=f'existing_{file_path.name}')
                result = _api_deploy(inst, 'JAR', file_path)
        elif artifact_type == 'SEQUENCE':
            # Management API는 시퀀스를 조회만 지원(POST/PUT 없음)해서, CAR에 담기지
            # 않은 개별 시퀀스는 MI의 hot-deploy 디렉터리(synapse-configs/default/sequences)에
            # 직접 파일을 떨어뜨리는 방식으로 배포한다 — SSH가 되면 SFTP로, 안 되면
            # SIISManagementApi(filedeploy)로.
            if inst.ssh_enabled:
                if not inst.sequence_path:
                    raise RuntimeError('인스턴스에 시퀀스 경로가 설정되어 있지 않습니다.')
                remote_target = f"{inst.sequence_path.rstrip('/')}/{file_path.name}"
                try:
                    if _sftp_exists(inst, remote_target):
                        existing_bp = _backup_path(inst, f'existing_{file_path.name}', ts)
                        _sftp_download(inst, remote_target, existing_bp)
                        previous_backup_path_str = str(existing_bp)
                except Exception:
                    pass  # 기존 파일 백업 실패해도 배포는 계속 진행
                remote_path = _sftp_upload(inst, file_path, inst.sequence_path)
                result = {'remote_path': remote_path}
            else:
                previous_backup_path_str = _api_backup_existing(inst, 'SEQUENCE', file_path.name, ts, backup_name=f'existing_{file_path.name}')
                result = _api_deploy(inst, 'SEQUENCE', file_path)
        elif file_path.suffix.lower() == '.xml':
            root_name = _xml_root_local_name(file_path)
            raise RuntimeError(
                f'이 XML은 루트 엘리먼트가 <{root_name or "파싱 실패"}>라 시퀀스가 아닙니다. '
                f'시퀀스 배포는 <sequence> 루트 엘리먼트를 가진 파일만 지원합니다 '
                f'(Proxy Service/Endpoint/API 등 다른 Synapse 아티팩트는 아직 미지원).'
            )
        else:
            raise RuntimeError(f'지원하지 않는 배포 형식입니다: {artifact_type}')

        add_deployment(DeploymentHistory(
            instance_id=inst.id,
            artifact_name=file_path.name,
            artifact_type=artifact_type,
            source_path=str(file_path),
            backup_path=backup_path_str,
            previous_backup_path=previous_backup_path_str,
            deployed_by=operator,
            status='SUCCESS',
            reason=reason,
        ))
        return {'success': True, 'instance': inst.name, 'instance_id': inst.id, 'detail': result}

    except Exception as e:
        add_deployment(DeploymentHistory(
            instance_id=inst.id,
            artifact_name=file_path.name,
            artifact_type=artifact_type,
            source_path=str(file_path),
            backup_path=backup_path_str,
            previous_backup_path=previous_backup_path_str,
            deployed_by=operator,
            status='FAILED',
            reason=reason,
        ))
        return {'success': False, 'instance': inst.name, 'instance_id': inst.id, 'error': str(e)}


# ── 그룹(복수 인스턴스) 일괄 배포 ────────────────────────────────────────

def _summarize(results: List[dict]) -> str:
    """인스턴스별(파일이 여러 개면 파일별로도) 성공/실패를 사람이 읽기 좋은 한 줄 요약으로 만든다."""
    parts = []
    for r in results:
        label = f"{r['file_name']}→{r['instance']}" if r.get('file_name') else r['instance']
        if r['success']:
            parts.append(f"{label}: 성공")
        else:
            parts.append(f"{label}: 실패({r.get('error', '')})")
    return ', '.join(parts)


def deploy_to_instances(instances: List[Instance], file_paths: List[Path],
                        operator: str = 'admin', reason: str = '') -> List[dict]:
    """여러 파일을 여러 인스턴스에 배포한다 (파일 x 인스턴스 조합을 전부 시도).
    모든 파일/인스턴스 조합이 같은 타임스탬프 배치 폴더를 공유하고, 감사 로그에도
    하나의 DEPLOY 작업으로 함께 기록된다."""
    batch_ts = datetime.now().strftime('%Y%m%d_%H%M%S')
    results = []
    errors = []
    for file_path in file_paths:
        for inst in instances:
            r = deploy_to_instance(inst, file_path, operator, reason, ts=batch_ts)
            r['file_name'] = file_path.name
            results.append(r)
            if not r['success']:
                errors.append(f"{file_path.name} → {inst.name}: {r.get('error')}")

    overall = 'SUCCESS' if not errors else ('PARTIAL' if len(errors) < len(results) else 'FAILED')
    reason_note = f' (사유: {reason})' if reason else ''
    file_names = ', '.join(f.name for f in file_paths)
    log_action(
        action='DEPLOY',
        instances=instances,
        result=overall,
        target_name=file_names,
        detail=f'[파일 {len(file_paths)}개] {file_names}{reason_note} → {_summarize(results)}',
        operator=operator,
    )
    return results


# ── 삭제 대상 조회 ───────────────────────────────────────────────────────

def list_deployed_cars(inst: Instance) -> List[dict]:
    """인스턴스에 현재 배포된 CAR 앱 목록 (삭제 대상 선택용)."""
    if not is_mi_type(inst.type):
        raise RuntimeError('APIM 배포는 아직 지원되지 않습니다 (추후 지원 예정)')
    mi = MIClient(inst)
    try:
        return mi.list_apps()
    finally:
        mi.close()


def _api_list_files(inst: Instance, artifact_type: str) -> List[dict]:
    """SIISManagementApi(filedeploy)로 파일 목록 조회. lastModified(ms)를 SFTP 쪽과
    같은 단위(mtime, 초 단위 Unix timestamp)로 맞춰 반환한다 — 호출부(deploy.js의
    formatMtime)가 초 단위를 전제로 하기 때문."""
    mi = MIClient(inst)
    try:
        files = mi.file_deploy_list(artifact_type)
    finally:
        mi.close()
    return [{'name': f['name'], 'size': f['size'], 'mtime': f['lastModified'] / 1000} for f in files]


def list_lib_jars(inst: Instance) -> List[dict]:
    """인스턴스 lib 디렉터리의 JAR 파일 목록 (삭제 대상 선택용).
    SSH가 되면 lib_path를 SFTP로, 안 되면 SIISManagementApi(filedeploy)로 조회한다."""
    if inst.ssh_enabled:
        if not inst.lib_path:
            raise RuntimeError('인스턴스에 lib 경로가 설정되어 있지 않습니다.')
        return _sftp_list_jars(inst)
    return _api_list_files(inst, 'JAR')


def list_sequence_files(inst: Instance) -> List[dict]:
    """인스턴스 시퀀스 디렉터리의 시퀀스(.xml) 파일 목록 (삭제 대상 선택용).
    SSH가 되면 sequence_path를 SFTP로, 안 되면 SIISManagementApi(filedeploy)로 조회한다."""
    if inst.ssh_enabled:
        if not inst.sequence_path:
            raise RuntimeError('인스턴스에 시퀀스 경로가 설정되어 있지 않습니다.')
        return _sftp_list_sequences(inst)
    return _api_list_files(inst, 'SEQUENCE')


# ── 삭제 ─────────────────────────────────────────────────────────────────

def undeploy_from_instances(instances: List[Instance], artifact_name: str, artifact_type: str,
                             operator: str = 'admin') -> List[dict]:
    """artifact_name은 확장자를 포함한 실제 파일명(예: foo_1.0.0.car, bar.jar).
    가능하면 삭제 전에 원본을 로컬 백업 디렉터리에 내려받아 둔다 (실패해도 삭제는 계속 진행)."""
    results = []
    errors = []
    for inst in instances:
        backup_path_str = ''
        try:
            if not is_mi_type(inst.type):
                raise RuntimeError('APIM 배포는 아직 지원되지 않습니다 (추후 지원 예정)')

            if artifact_type == 'CAR':
                mi = MIClient(inst)
                try:
                    content = mi.download_app(artifact_name)
                    bp = _backup_path(inst, artifact_name)
                    bp.write_bytes(content)
                    backup_path_str = str(bp)
                except Exception:
                    pass  # 백업 실패해도 삭제는 계속 진행
                mi.undeploy_app(artifact_name)
                mi.close()
            elif artifact_type == 'JAR':
                if inst.ssh_enabled:
                    if not inst.lib_path:
                        raise RuntimeError('인스턴스에 lib 경로가 설정되어 있지 않습니다.')
                    remote_path = f"{inst.lib_path.rstrip('/')}/{artifact_name}"
                    try:
                        bp = _backup_path(inst, artifact_name)
                        _sftp_download(inst, remote_path, bp)
                        backup_path_str = str(bp)
                    except Exception:
                        pass
                    _sftp_remove(inst, remote_path)
                else:
                    backup_path_str = _api_backup_existing(inst, 'JAR', artifact_name, None)
                    _api_undeploy(inst, 'JAR', artifact_name)
            elif artifact_type == 'SEQUENCE':
                if inst.ssh_enabled:
                    if not inst.sequence_path:
                        raise RuntimeError('인스턴스에 시퀀스 경로가 설정되어 있지 않습니다.')
                    remote_path = f"{inst.sequence_path.rstrip('/')}/{artifact_name}"
                    try:
                        bp = _backup_path(inst, artifact_name)
                        _sftp_download(inst, remote_path, bp)
                        backup_path_str = str(bp)
                    except Exception:
                        pass
                    _sftp_remove(inst, remote_path)
                else:
                    backup_path_str = _api_backup_existing(inst, 'SEQUENCE', artifact_name, None)
                    _api_undeploy(inst, 'SEQUENCE', artifact_name)
            else:
                raise RuntimeError(f'지원하지 않는 삭제 형식입니다: {artifact_type}')

            results.append({'success': True, 'instance': inst.name, 'instance_id': inst.id, 'backup_path': backup_path_str})
        except Exception as e:
            results.append({'success': False, 'instance': inst.name, 'instance_id': inst.id, 'error': str(e)})
            errors.append(f"{inst.name}: {e}")

    overall = 'SUCCESS' if not errors else ('PARTIAL' if len(errors) < len(instances) else 'FAILED')
    log_action('DELETE', instances, overall, artifact_name,
               f'[{artifact_type}] {artifact_name} 삭제 → {_summarize(results)}', operator)
    return results


# ── 롤백 ─────────────────────────────────────────────────────────────────

def rollback(instance: Instance, deployment_id: int, operator: str = 'admin') -> dict:
    """지정한 배포 건을 기준으로, 그 배포 '직전' 서버 상태로 되돌린다.

    previous_backup_path(배포 전 서버에 있던 기존 파일)가 있으면 그것을 재배포해
    실제로 이전 버전으로 되돌아간다. 그 배포가 최초 배포라 이전 파일 자체가
    없었다면(previous_backup_path 없음) 되돌아갈 대상이 없으므로 에러를 반환한다
    (이 경우 되돌리려면 삭제(undeploy)가 맞는 동작)."""
    hists = get_deployments(instance.id, limit=50)
    target = next((h for h in hists if h.id == deployment_id), None)
    if not target:
        return {'success': False, 'error': 'Deployment history not found'}

    if not target.previous_backup_path:
        return {'success': False,
                'error': '이 배포는 최초 배포라 되돌아갈 이전 버전이 없습니다 (삭제를 이용하세요).'}

    backup = Path(target.previous_backup_path)
    if not backup.exists():
        return {'success': False, 'error': f'Backup file not found: {backup}'}

    r = deploy_to_instance(instance, backup, operator)
    if r['success']:
        log_action('ROLLBACK', [instance], 'SUCCESS', target.artifact_name,
                   f'Rolled back to {backup}', operator)
    else:
        log_action('ROLLBACK', [instance], 'FAILED', target.artifact_name,
                   r.get('error', ''), operator)
    return r
