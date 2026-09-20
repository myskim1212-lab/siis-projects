"""pywebview JS <-> Python 브리지.

JS에서는 `window.pywebview.api.<method>(...)` 형태로 아래 메서드를 호출한다.
Python에서 먼저 알려야 하는 실시간 이벤트(로그 라인, 재시작 진행상황, 배치 작업
완료)는 `window.dispatchEvent(new CustomEvent(name, {detail: payload}))`를
`evaluate_js`로 실행해 JS 쪽 `window.addEventListener(name, ...)`로 전달한다.
"""
import concurrent.futures
import csv
import json
import shutil
import threading
from dataclasses import asdict, is_dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional

import requests
import webview

import db.database as db
from api.apim_client import APIMClient
from api.ei_client import POOL_FIELDS, EIClient
from api.mi_client import MIClient
from db.models import Instance, InstanceGroup, Server, ServerGroup, is_mi_type
from services import deploy_service, instance_io_service, restart_service
from services.audit_service import get_logs, log_action
from services.log_service import LogManager


def _serialize(obj):
    if is_dataclass(obj):
        d = asdict(obj)
        for k, v in d.items():
            if isinstance(v, datetime):
                d[k] = v.isoformat()
        return d
    return obj


def _serialize_list(items):
    return [_serialize(i) for i in items]


class Api:
    def __init__(self):
        self._log_manager = LogManager()

    def _window(self):
        return webview.windows[0] if webview.windows else None

    def _push(self, event: str, payload):
        win = self._window()
        if win is None:
            return
        win.evaluate_js(
            f'window.dispatchEvent(new CustomEvent({json.dumps(event)}, '
            f'{{detail: {json.dumps(payload)}}}))'
        )

    # ── 서버그룹 (서버그룹>서버>인스턴스그룹>인스턴스 구조의 최상위 계층) ────

    def get_server_groups(self):
        return _serialize_list(db.get_all_server_groups())

    def save_server_group(self, data: dict):
        sg = ServerGroup(
            id=data.get('id'),
            name=data.get('name', '').strip(),
            description=data.get('description', ''),
        )
        return _serialize(db.save_server_group(sg))

    def delete_server_group(self, server_group_id: int):
        db.delete_server_group(server_group_id)
        return {'success': True}

    # ── 서버 (서버그룹 아래, 인스턴스그룹 위의 계층) ────────────────────────

    def get_servers(self, server_group_id=None):
        if server_group_id:
            return _serialize_list(db.get_servers_by_server_group(server_group_id))
        return _serialize_list(db.get_all_servers())

    def save_server(self, data: dict):
        s = Server(
            id=data.get('id'),
            server_group_id=data.get('server_group_id', 0),
            name=data.get('name', '').strip(),
            environment=data.get('environment', 'DEV'),
            ip=data.get('ip', '').strip(),
            description=data.get('description', '').strip(),
            ssh_enabled=bool(data.get('ssh_enabled', False)),
            ssh_host=data.get('ssh_host', '').strip(),
            ssh_port=int(data.get('ssh_port', 22)),
            ssh_user=data.get('ssh_user', '').strip(),
            ssh_pass=data.get('ssh_pass', ''),
            ssh_key_path=data.get('ssh_key_path', '').strip(),
        )
        return _serialize(db.save_server(s))

    def delete_server(self, server_id: int):
        db.delete_server(server_id)
        return {'success': True}

    # ── 인스턴스그룹 (서버 아래, 인스턴스 위의 계층) ────────────────────────

    def get_groups(self, server_id=None):
        if server_id:
            return _serialize_list(db.get_groups_by_server(server_id))
        return _serialize_list(db.get_all_groups())

    def save_group(self, data: dict):
        g = InstanceGroup(
            id=data.get('id'),
            server_id=data.get('server_id', 0),
            name=data.get('name', '').strip(),
            type=data.get('type', 'MI'),
            description=data.get('description', ''),
        )
        return _serialize(db.save_group(g))

    def delete_group(self, group_id: int):
        db.delete_group(group_id)
        return {'success': True}

    # ── 인스턴스 ──────────────────────────────────────────────────────────

    def get_instances(self, group_id=None):
        if group_id:
            return _serialize_list(db.get_instances_by_group(group_id))
        return _serialize_list(db.get_all_instances())

    def get_instance(self, instance_id: int):
        inst = db.get_instance(instance_id)
        return _serialize(inst) if inst else None

    def save_instance(self, data: dict):
        inst = Instance(
            id=data.get('id'),
            group_id=data.get('group_id', 0),
            name=data.get('name', '').strip(),
            host=data.get('host', '').strip(),
            port=int(data.get('port', 9164)),
            service_port=int(data.get('service_port', 8290)),
            base_path=data.get('base_path', '').strip(),
            lib_path=data.get('lib_path', '').strip(),
            sequence_path=data.get('sequence_path', '').strip()
                or '/opt/wso2mi/repository/deployment/server/synapse-configs/default/sequences',
            jdbc_registry_path=data.get('jdbc_registry_path', '').strip() or 'registry/config/jdbc',
            type=data.get('type', 'MI_ALL_IN_ONE'),
            product=data.get('product', '').strip(),
            version=data.get('version', '').strip(),
            environment=data.get('environment', 'DEV'),
            admin_user=data.get('admin_user', 'admin').strip(),
            admin_pass=data.get('admin_pass', ''),
            token_url=data.get('token_url', '').strip(),
            log_path=data.get('log_path', '').strip(),
            api_log_max_mb=int(data.get('api_log_max_mb') or 10),
            bin_path=data.get('bin_path', '').strip() or '/opt/wso2mi/bin',
            service_script=data.get('service_script', '').strip() or 'micro-integrator.sh',
            description=data.get('description', '').strip(),
            # OS SSH 접속 정보는 서버 등록/수정(save_server)으로 옮겨졌으므로 여기서는
            # 받지 않는다 — Instance.ssh_* 필드는 조회 시 소속 서버 값이 자동으로 채워진다.
        )
        return _serialize(db.save_instance(inst))

    def delete_instance(self, instance_id: int):
        self._log_manager.stop(instance_id)
        db.delete_instance(instance_id)
        return {'success': True}

    # ── 화면에 표시된 표를 CSV(엑셀)로 저장 ──────────────────────────────
    # 데이터소스 목록, 모니터링 스레드 목록 등 — 서버에서 데이터를 다시 조회하지
    # 않고 프론트가 이미 화면에 그려둔 값을 그대로 받아서 저장한다.

    def export_csv(self, default_filename: str, headers: list, rows: list):
        win = self._window()
        if win is None:
            return {'success': False, 'error': 'No window'}
        path = win.create_file_dialog(
            webview.SAVE_DIALOG, save_filename=default_filename,
            file_types=('CSV Files (*.csv)', 'All files (*.*)'),
        )
        if not path:
            return {'success': False, 'error': 'Cancelled'}
        target = path if isinstance(path, str) else path[0]
        try:
            # 엑셀이 UTF-8 CSV를 BOM 없이 열면 한글이 깨지므로 utf-8-sig로 저장한다.
            with open(target, 'w', newline='', encoding='utf-8-sig') as f:
                writer = csv.writer(f)
                writer.writerow(headers)
                writer.writerows(rows)
            return {'success': True, 'path': target}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    # ── 인스턴스 등록정보 export / import ────────────────────────────────
    # ⚠️ export 파일에는 admin/SSH 비밀번호가 평문으로 포함된다 (DB 자체도 평문
    # 저장이라 노출 수준은 동일하나, 파일로 빠져나가는 것이므로 취급에 주의 필요).

    def export_instances(self, instance_ids: list = None):
        """instance_ids가 없으면 전체, 있으면 해당 인스턴스들(과 그 소속 그룹)만 내보낸다."""
        win = self._window()
        if win is None:
            return {'success': False, 'error': 'No window'}
        ids_set = set(instance_ids) if instance_ids else None
        data = instance_io_service.export_selected(ids_set)
        default_name = f'wso2_instances_{datetime.now().strftime("%Y%m%d_%H%M%S")}.json'
        path = win.create_file_dialog(
            webview.SAVE_DIALOG, save_filename=default_name,
            file_types=('JSON Files (*.json)',),
        )
        if not path:
            return {'success': False, 'error': 'Cancelled'}
        target = path if isinstance(path, str) else path[0]
        try:
            with open(target, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            return {'success': False, 'error': str(e)}

        server_group_count = len(data['server_groups'])
        server_count = sum(len(sg['servers']) for sg in data['server_groups'])
        group_count = sum(len(s['groups']) for sg in data['server_groups'] for s in sg['servers'])
        instance_count = sum(
            len(g['instances'])
            for sg in data['server_groups'] for s in sg['servers'] for g in s['groups'])
        log_action('EXPORT_INSTANCES', [], 'SUCCESS', target_name=target,
                   detail=(f'서버그룹 {server_group_count}개, 서버 {server_count}개, '
                           f'그룹 {group_count}개, 인스턴스 {instance_count}개'), operator='admin')
        return {'success': True, 'path': target, 'server_group_count': server_group_count,
                'server_count': server_count, 'group_count': group_count,
                'instance_count': instance_count}

    def pick_import_file(self):
        win = self._window()
        if win is None:
            return None
        result = win.create_file_dialog(
            webview.OPEN_DIALOG, allow_multiple=False,
            file_types=('JSON Files (*.json)', 'All files (*.*)'),
        )
        return result[0] if result else None

    def preview_import_file(self, file_path: str):
        """가져오기 실행 전, DB에 손대지 않고 파일 내용만 요약해서 보여준다."""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except Exception as e:
            return {'success': False, 'error': f'파일을 읽을 수 없습니다: {e}'}
        try:
            return {'success': True, **instance_io_service.summarize(data)}
        except Exception as e:
            return {'success': False, 'error': f'파일 형식이 올바르지 않습니다: {e}'}

    def import_instances(self, file_path: str):
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except Exception as e:
            return {'success': False, 'error': f'파일을 읽을 수 없습니다: {e}'}

        try:
            result = instance_io_service.import_data(data)
        except Exception as e:
            log_action('IMPORT_INSTANCES', [], 'FAILED', target_name=file_path, detail=str(e), operator='admin')
            return {'success': False, 'error': str(e)}

        outcome = 'SUCCESS' if not result['errors'] else 'PARTIAL'
        detail = (f"서버그룹 {result['created_server_groups']}개 생성, 서버 {result['created_servers']}개 생성, "
                  f"그룹 {result['created_groups']}개 생성, "
                  f"인스턴스 {result['created_instances']}개 생성 / {result['updated_instances']}개 업데이트")
        if result['errors']:
            detail += f" / 오류: {'; '.join(result['errors'])}"
        log_action('IMPORT_INSTANCES', [], outcome, target_name=file_path, detail=detail, operator='admin')
        return {'success': True, **result}

    # ── 전체 데이터 초기화 (위험 — 인스턴스/그룹/이력/캐시 전부 삭제) ────────

    def reset_all_data(self):
        """DB 백업 저장 경로를 먼저 물어보고, 백업이 끝난 뒤에만 전체 데이터를 삭제한다
        (경로 선택 취소 시 아무것도 지우지 않음)."""
        win = self._window()
        if win is None:
            return {'success': False, 'error': 'No window'}
        default_name = f'mgmt_backup_{datetime.now().strftime("%Y%m%d_%H%M%S")}.db'
        path = win.create_file_dialog(
            webview.SAVE_DIALOG, save_filename=default_name,
            file_types=('DB Files (*.db)', 'All files (*.*)'),
        )
        if not path:
            return {'success': False, 'error': 'Cancelled'}
        target = path if isinstance(path, str) else path[0]
        try:
            shutil.copy2(db.DB_PATH, target)
        except Exception as e:
            return {'success': False, 'error': f'백업 실패로 초기화를 중단했습니다: {e}'}

        self._log_manager.stop_all()
        counts = db.reset_all_data()
        return {'success': True, 'backup_path': target, 'counts': counts}

    def test_connection(self, instance_id: int, log: bool = True):
        """log=False면 감사 로그에 남기지 않는다 (백그라운드 자동 상태 확인용,
        1분 주기로 계속 도는데 매번 기록하면 감사 로그가 도배되므로)."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        try:
            if is_mi_type(inst.type):
                with MIClient(inst) as mi:
                    ok = mi.ping()
            else:
                with APIMClient(inst) as ap:
                    ok = ap.ping()
            if log:
                log_action('CONNECTION_TEST', [inst], 'SUCCESS' if ok else 'FAILED',
                           detail=f'{inst.host}:{inst.port}' + ('' if ok else ' — Ping 실패'))
            return {'success': ok, 'instance_id': instance_id}
        except Exception as e:
            if log:
                log_action('CONNECTION_TEST', [inst], 'FAILED', detail=f'{inst.host}:{inst.port} — {e}')
            return {'success': False, 'instance_id': instance_id, 'error': str(e)}

    # ── 데이터소스 ────────────────────────────────────────────────────────

    def list_datasources(self, instance_id: int):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 데이터소스 조회가 가능합니다.'}
        try:
            with MIClient(inst) as mi:
                items = mi.list_datasources()
            return {'success': True, 'items': items}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def search_datasources(self, instance_id: int, terms: list):
        """콤마로 구분된 검색어별로 MI의 searchKey 검색을 호출해 합친다 (서버 쪽에서
        실제로 필터링된 결과만 가져옴 — 전체 목록을 받아서 클라이언트에서 거르지 않는다)."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 데이터소스 조회가 가능합니다.'}
        try:
            merged = {}
            with MIClient(inst) as mi:
                for term in terms:
                    term = term.strip()
                    if not term:
                        continue
                    for ds in mi.search_datasources(term):
                        merged[ds['name']] = ds
            return {'success': True, 'items': list(merged.values())}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def get_datasource(self, instance_id: int, name: str):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 데이터소스 조회가 가능합니다.'}
        try:
            with MIClient(inst) as mi:
                data = mi.get_datasource(name)
            return {'success': True, 'data': data}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    @staticmethod
    def _resolve_jndi_name(mi: MIClient, instance_id: int, ds_name: str) -> str:
        """캐시 → CAR 조회(성공 시 캐시에 저장) → 명명규칙 추정 순으로 JNDI 이름을 결정한다."""
        cached = db.get_cached_jndi(instance_id, ds_name)
        if cached:
            return cached
        try:
            jndi = mi.get_datasource_jndi_name(ds_name)
        except Exception:
            jndi = None
        if jndi:
            db.set_cached_jndi(instance_id, ds_name, jndi)
            return jndi
        return MIClient._jndi_name(ds_name)

    def get_datasource_jndi_name(self, instance_id: int, name: str, force_refresh: bool = False):
        """실제 JNDI 이름을 조회한다 (캐시 우선, 미스 시 배포된 CAR에서 직접 읽어와 캐시에 저장).
        force_refresh=True면 캐시를 무시하고 무조건 CAR을 다시 읽어 캐시를 새 값으로 덮어쓴다
        (재배포로 JNDI 매핑이 바뀐 경우 등)."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 조회가 가능합니다.'}
        try:
            if not force_refresh:
                cached = db.get_cached_jndi(instance_id, name)
                if cached:
                    return {'success': True, 'jndi_name': cached}
            with MIClient(inst) as mi:
                jndi = mi.get_datasource_jndi_name(name)
            if jndi is None:
                return {'success': False, 'error': '해당 데이터소스가 포함된 CAR을 찾지 못했거나 JNDI 설정이 없습니다.'}
            db.set_cached_jndi(instance_id, name, jndi)
            return {'success': True, 'jndi_name': jndi}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def clear_datasource_jndi_cache(self, instance_id: int):
        """이 인스턴스의 JNDI 캐시 전체를 삭제한다 (CAR 재배포로 여러 데이터소스의
        JNDI 매핑이 한꺼번에 바뀌었을 수 있는 경우 등). 개별 항목은 이후 조회 시
        (또는 상세보기의 강제 새로고침으로) 다시 CAR을 읽어 채워진다."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        try:
            count = db.clear_cached_jndi_for_instance(instance_id)
            log_action('CLEAR_JNDI_CACHE', [inst], 'SUCCESS', detail=f'{count}개 항목 삭제', operator='admin')
            return {'success': True, 'cleared': count}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def test_datasources(self, instance_id: int, names: list):
        """선택(또는 전체) 데이터소스에 대해 SIIS dbinfo API로 실제 접속 테스트.
        JNDI 이름은 캐시된 매핑을 우선 참조하고, 없으면 CAR 조회 후 캐시에 저장한다."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 지원됩니다.'}
        results = []
        with MIClient(inst) as mi:
            for name in names:
                try:
                    jndi = self._resolve_jndi_name(mi, instance_id, name)
                    r = mi.test_datasource_connection(jndi)
                    r['name'] = name
                    results.append(r)
                except Exception as e:
                    results.append({'success': False, 'name': name, 'error': str(e)})
        return {'success': True, 'results': results}

    def test_datasource_multi(self, jndi_names, instance_ids: list):
        """하나 이상의 JNDI 이름을 여러 인스턴스에서 동시에 접속 테스트.
        인스턴스 단위로 병렬 실행하고, 한 인스턴스 안에서 여러 JNDI 이름은 순차 처리한다
        (인스턴스별로 MIClient 연결을 하나만 열어서 재사용하기 위함)."""
        if isinstance(jndi_names, str):
            jndi_names = [jndi_names]
        jndi_names = [n for n in (jndi_names or []) if n]
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]

        def run_one(inst):
            if not is_mi_type(inst.type):
                return [{'success': False, 'instance_id': inst.id, 'instance_name': inst.name,
                         'jndi_name': jn, 'error': 'MI 인스턴스가 아닙니다.'} for jn in jndi_names]
            out = []
            with MIClient(inst) as mi:
                for jn in jndi_names:
                    try:
                        r = mi.test_datasource_connection(jn)
                        r['instance_id'] = inst.id
                        r['instance_name'] = inst.name
                        r['jndi_name'] = jn
                    except Exception as e:
                        r = {'success': False, 'instance_id': inst.id, 'instance_name': inst.name,
                             'jndi_name': jn, 'error': str(e)}
                    out.append(r)
            return out

        if not instances or not jndi_names:
            return {'success': True, 'results': []}
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(instances)) as ex:
            nested = list(ex.map(run_one, instances))
        return {'success': True, 'results': [r for group in nested for r in group]}

    def test_datasource_direct_multi(self, url: str, user: str, password: str, instance_ids: list):
        """등록 전 접속 테스트 — 동일한 URL/계정으로 여러 인스턴스에서 동시에 직접 테스트 (병렬)."""
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]

        def run_one(inst):
            if not is_mi_type(inst.type):
                return {'success': False, 'instance_id': inst.id, 'instance_name': inst.name,
                        'error': 'MI 인스턴스가 아닙니다.'}
            try:
                with MIClient(inst) as mi:
                    r = mi.test_datasource_direct(url, user, password)
                r['instance_id'] = inst.id
                r['instance_name'] = inst.name
                return r
            except Exception as e:
                return {'success': False, 'instance_id': inst.id, 'instance_name': inst.name, 'error': str(e)}

        if not instances:
            return {'success': True, 'results': []}
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(instances)) as ex:
            results = list(ex.map(run_one, instances))
        return {'success': True, 'results': results}

    # ── EI 6.1 데이터소스 (NDataSourceAdmin SOAP 웹서비스) ─────────────────
    # MI와 달리 EI는 Management REST API가 없어 Carbon의 SOAP admin 서비스로만
    # 등록/조회/수정/삭제가 가능하다 (api/ei_client.py 참고). NDataSourceAdmin
    # SOAP 서비스는 EI 6.1~6.6 전체에서 안정적으로 동일해 제품명이 EI이기만
    # 하면 버전과 무관하게 지원한다.

    @staticmethod
    def _require_ei(inst) -> Optional[dict]:
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if inst.product != 'EI':
            return {'success': False, 'error': 'EI 인스턴스에서만 지원됩니다 (인스턴스 정보의 제품명을 확인하세요).'}
        return None

    def list_ei_datasources(self, instance_id: int):
        inst = db.get_instance(instance_id)
        err = self._require_ei(inst)
        if err:
            return err
        try:
            with EIClient(inst) as ei:
                items = ei.list_datasources()
            return {'success': True, 'items': items}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def get_ei_datasource(self, instance_id: int, name: str):
        inst = db.get_instance(instance_id)
        err = self._require_ei(inst)
        if err:
            return err
        try:
            with EIClient(inst) as ei:
                data = ei.get_datasource(name)
            if data is None:
                return {'success': False, 'error': f'데이터소스를 찾을 수 없습니다: {name}'}
            return {'success': True, 'data': data}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    @staticmethod
    def _ei_extra_fields(data: dict) -> dict:
        """등록/수정/테스트 폼이 공통으로 보내는 커넥션 풀 고급 옵션 — RDBMSConfiguration의
        전체 필드(api.ei_client.POOL_FIELDS) 중 값이 입력된 것만 골라낸다."""
        return {k: data.get(k) for k in POOL_FIELDS if data.get(k) not in (None, '')}

    def save_ei_datasource(self, instance_id: int, data: dict):
        """등록/수정 공용 — data.is_edit로 구분한다. 수정 시 password를 비워두면
        기존 비밀번호를 그대로 유지한다 (EIClient.save_datasource 참고)."""
        inst = db.get_instance(instance_id)
        err = self._require_ei(inst)
        if err:
            return err
        try:
            with EIClient(inst) as ei:
                extra = self._ei_extra_fields(data)
                ei.save_datasource(
                    name=(data.get('name') or '').strip(),
                    description=(data.get('description') or '').strip(),
                    jndi_name=(data.get('jndi_name') or '').strip(),
                    driver_class_name=(data.get('driver_class_name') or '').strip(),
                    url=(data.get('url') or '').strip(),
                    username=(data.get('username') or '').strip(),
                    password=data.get('password') or '',
                    extra=extra,
                    is_edit=bool(data.get('is_edit')),
                )
            action = 'EI_DATASOURCE_EDIT' if data.get('is_edit') else 'EI_DATASOURCE_ADD'
            log_action(action, [inst], 'SUCCESS', target_name=data.get('name', ''))
            return {'success': True}
        except Exception as e:
            log_action('EI_DATASOURCE_EDIT' if data.get('is_edit') else 'EI_DATASOURCE_ADD',
                        [inst], 'FAILED', target_name=data.get('name', ''), detail=str(e))
            return {'success': False, 'error': str(e)}

    def delete_ei_datasource(self, instance_id: int, name: str):
        inst = db.get_instance(instance_id)
        err = self._require_ei(inst)
        if err:
            return err
        try:
            with EIClient(inst) as ei:
                ei.delete_datasource(name)
            log_action('EI_DATASOURCE_DELETE', [inst], 'SUCCESS', target_name=name)
            return {'success': True}
        except Exception as e:
            log_action('EI_DATASOURCE_DELETE', [inst], 'FAILED', target_name=name, detail=str(e))
            return {'success': False, 'error': str(e)}

    def test_ei_datasource(self, instance_id: int, data: dict):
        """등록/수정 폼에 지금 입력된 값 그대로 접속 테스트 (저장 전)."""
        inst = db.get_instance(instance_id)
        err = self._require_ei(inst)
        if err:
            return err
        try:
            with EIClient(inst) as ei:
                result = ei.test_connection(
                    name=(data.get('name') or '').strip(),
                    driver_class_name=(data.get('driver_class_name') or '').strip(),
                    url=(data.get('url') or '').strip(),
                    username=(data.get('username') or '').strip(),
                    password=data.get('password') or '',
                    extra=self._ei_extra_fields(data),
                )
            return {'success': True, **result}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def test_ei_datasource_existing(self, instance_id: int, name: str):
        """이미 저장된 데이터소스를 이름으로 재테스트 — 비밀번호는 서버(Python)
        안에서만 조회해 쓰고 JS로는 절대 내보내지 않는다."""
        inst = db.get_instance(instance_id)
        err = self._require_ei(inst)
        if err:
            return err
        try:
            with EIClient(inst) as ei:
                result = ei.test_existing_datasource(name)
            return {'success': True, **result}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def test_ei_datasource_multi(self, names, instance_ids: list):
        """하나 이상의 EI 데이터소스 등록명을 여러 EI 인스턴스에서 동시에 재테스트.
        test_datasource_multi(MI)와 대응되는 EI 버전 — EI는 JNDI가 아니라 데이터소스
        등록명으로 조회하므로 이름을 그대로 쓴다."""
        if isinstance(names, str):
            names = [names]
        names = [n for n in (names or []) if n]
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]

        def run_one(inst):
            err = self._require_ei(inst)
            if err:
                return [{'success': False, 'instance_id': inst.id, 'instance_name': inst.name,
                         'jndi_name': n, 'error': err['error']} for n in names]
            out = []
            with EIClient(inst) as ei:
                for n in names:
                    try:
                        r = ei.test_existing_datasource(n)
                        r['instance_id'] = inst.id
                        r['instance_name'] = inst.name
                        r['jndi_name'] = n
                    except Exception as e:
                        r = {'success': False, 'instance_id': inst.id, 'instance_name': inst.name,
                             'jndi_name': n, 'error': str(e)}
                    out.append(r)
            return out

        if not instances or not names:
            return {'success': True, 'results': []}
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(instances)) as ex:
            nested = list(ex.map(run_one, instances))
        return {'success': True, 'results': [r for group in nested for r in group]}

    def test_ei_datasource_direct_multi(self, driver_class_name: str, url: str, user: str, password: str,
                                        instance_ids: list):
        """등록 전 접속 테스트 — 동일한 드라이버/URL/계정으로 여러 EI 인스턴스에서
        동시에 직접 테스트 (test_datasource_direct_multi의 EI 버전)."""
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]

        def run_one(inst):
            err = self._require_ei(inst)
            if err:
                return {'success': False, 'instance_id': inst.id, 'instance_name': inst.name, 'error': err['error']}
            try:
                with EIClient(inst) as ei:
                    r = ei.test_connection(
                        name='TEST_CONNECTION', driver_class_name=driver_class_name,
                        url=url, username=user, password=password)
                r['instance_id'] = inst.id
                r['instance_name'] = inst.name
                return r
            except Exception as e:
                return {'success': False, 'instance_id': inst.id, 'instance_name': inst.name, 'error': str(e)}

        if not instances:
            return {'success': True, 'results': []}
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(instances)) as ex:
            results = list(ex.map(run_one, instances))
        return {'success': True, 'results': results}

    # ── JVM 모니터링 ─────────────────────────────────────────────────────

    def get_jvm_info(self, instance_id: int):
        """이 인스턴스의 JVM/메모리풀/파일디스크립터/커넥션풀/종합 헬스 상태 조회
        (SIIS 커넥터의 JvmInfoMediator, dbinfo와는 별개의 엔드포인트)."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 지원됩니다 (APIM은 추후 지원 예정).'}
        try:
            with MIClient(inst) as mi:
                data = mi.get_jvm_info()
            return data
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def get_jvm_info_multi(self, instance_ids: list):
        """여러 인스턴스의 JVM 정보를 동시에 조회 (모니터링 화면에서 서버그룹/그룹
        단위로 선택했을 때, 그 안의 모든 인스턴스를 한 번에 보여주기 위함).
        인스턴스 단위로 병렬 실행하며, 인스턴스 하나가 실패해도 나머지 결과에는
        영향을 주지 않는다."""
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]

        def run_one(inst):
            if not is_mi_type(inst.type):
                return {'success': False, 'instance_id': inst.id, 'instance_name': inst.name,
                        'error': 'MI 인스턴스에서만 지원됩니다 (APIM은 추후 지원 예정).'}
            try:
                with MIClient(inst) as mi:
                    data = mi.get_jvm_info()
                data['instance_id'] = inst.id
                data['instance_name'] = inst.name
                return data
            except Exception as e:
                return {'success': False, 'instance_id': inst.id, 'instance_name': inst.name, 'error': str(e)}

        if not instances:
            return {'success': True, 'results': []}
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(instances)) as ex:
            results = list(ex.map(run_one, instances))
        return {'success': True, 'results': results}

    # ── 레지스트리 리소스 ────────────────────────────────────────────────

    def list_registry_resources(self, instance_id: int, path: str):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 레지스트리 조회가 가능합니다.'}
        try:
            with MIClient(inst) as mi:
                items = mi.list_registry_resources(path)
            return {'success': True, 'items': items}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def search_registry_resources(self, instance_id: int, root_path: str, keyword: str):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 레지스트리 검색이 가능합니다.'}
        keyword = (keyword or '').strip()
        if not keyword:
            return {'success': False, 'error': '검색어를 입력하세요.'}
        try:
            with MIClient(inst) as mi:
                result = mi.search_registry_resources(root_path or 'registry', keyword)
            return {'success': True, **result}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def get_registry_resource(self, instance_id: int, path: str):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 레지스트리 조회가 가능합니다.'}
        try:
            with MIClient(inst) as mi:
                content = mi.get_registry_resource(path)
            return {'success': True, 'exists': True, 'content': content}
        except requests.HTTPError as e:
            if e.response is not None and e.response.status_code == 400:
                try:
                    msg = str(e.response.json().get('Error', ''))
                except ValueError:
                    msg = ''
                if 'can not find the registry' in msg.lower():
                    return {'success': True, 'exists': False}
            return {'success': False, 'error': str(e)}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def save_registry_resource(self, instance_id: int, path: str, content: str):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 레지스트리 저장이 가능합니다.'}
        try:
            with MIClient(inst) as mi:
                result = mi.save_registry_resource(path, content)
            log_action('REGISTRY_SAVE', [inst], 'SUCCESS', path,
                      f"[{result.get('mode')}] {path}", operator='admin')
            return {'success': True, 'mode': result.get('mode')}
        except Exception as e:
            log_action('REGISTRY_SAVE', [inst], 'FAILED', path, str(e), operator='admin')
            return {'success': False, 'error': str(e)}

    def delete_registry_resource(self, instance_id: int, path: str):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 레지스트리 삭제가 가능합니다.'}
        try:
            with MIClient(inst) as mi:
                mi.delete_registry_resource(path)
            log_action('REGISTRY_DELETE', [inst], 'SUCCESS', path, path, operator='admin')
            return {'success': True}
        except Exception as e:
            log_action('REGISTRY_DELETE', [inst], 'FAILED', path, str(e), operator='admin')
            return {'success': False, 'error': str(e)}

    # ── 배포 ─────────────────────────────────────────────────────────────

    def pick_files(self):
        """여러 파일을 한 번에 골라 한 번의 배포 작업으로 여러 인스턴스에 배포할 수 있게
        다중 선택을 허용한다."""
        win = self._window()
        if win is None:
            return []
        result = win.create_file_dialog(
            webview.OPEN_DIALOG,
            allow_multiple=True,
            file_types=('WSO2 Artifacts (*.car;*.xml;*.jar)', 'All files (*.*)'),
        )
        return list(result) if result else []

    def detect_artifact_type(self, file_path: str):
        """선택한 파일이 배포 시 실제로 어떤 타입으로 처리될지 미리 알려준다
        (deploy_service._artifact_type()과 동일한 판단 — .xml은 내용을 열어
        <sequence> 루트인지까지 확인). 배포 파일 표시 UI에서 뱃지로 보여주는 용도."""
        try:
            return {'success': True, 'type': deploy_service._artifact_type(Path(file_path))}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def deploy(self, file_paths: list, instance_ids: list, reason: str = ''):
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]
        paths = [Path(p) for p in file_paths]

        def run():
            results = deploy_service.deploy_to_instances(instances, paths, reason=reason)
            self._push('deploy-done', {'results': results})

        threading.Thread(target=run, daemon=True).start()
        return {'started': True}

    def undeploy(self, artifact_name: str, artifact_type: str, instance_ids: list):
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]

        def run():
            results = deploy_service.undeploy_from_instances(instances, artifact_name, artifact_type)
            self._push('undeploy-done', {'results': results, 'artifact_name': artifact_name})

        threading.Thread(target=run, daemon=True).start()
        return {'started': True}

    def list_deployed_apps(self, instance_id: int):
        """삭제 대상 조회: 인스턴스에 현재 배포된 CAR 앱 목록."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        try:
            return {'success': True, 'items': deploy_service.list_deployed_cars(inst)}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def list_lib_jars(self, instance_id: int):
        """삭제 대상 조회: 인스턴스 lib 디렉터리의 JAR 파일 목록."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        try:
            return {'success': True, 'items': deploy_service.list_lib_jars(inst)}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def list_sequence_files(self, instance_id: int):
        """삭제 대상 조회: 인스턴스 시퀀스 디렉터리의 .xml 파일 목록."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        try:
            return {'success': True, 'items': deploy_service.list_sequence_files(inst)}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def search_deployments(self, instance_id: int = None, artifact_name: str = '',
                           reason_keyword: str = '', date_from: str = '', date_to: str = '',
                           limit: int = 200):
        try:
            rows = db.search_deployments(
                instance_id=instance_id or None,
                artifact_name=artifact_name or None,
                reason_keyword=reason_keyword or None,
                date_from=date_from or None,
                date_to=date_to or None,
                limit=limit,
            )
            return {'success': True, 'items': _serialize_list(rows)}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    # ── 재시작 / 정지 ────────────────────────────────────────────────────

    def search_restart_history(self, instance_id: int = None, date_from: str = '',
                               date_to: str = '', limit: int = 200):
        try:
            rows = db.search_restart_history(
                instance_id=instance_id or None,
                date_from=date_from or None,
                date_to=date_to or None,
                limit=limit,
            )
            return {'success': True, 'items': _serialize_list(rows)}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def clear_restart_history(self):
        count = db.clear_restart_history()
        return {'success': True, 'count': count}

    def restart_all(self, instance_ids: list, graceful: bool = False, method: str = None):
        return self._run_restart(instance_ids, mode='all', graceful=graceful, method=method)

    def shutdown_instances(self, instance_ids: list, graceful: bool = True, method: str = None):
        return self._run_restart(instance_ids, mode='shutdown', graceful=graceful, method=method)

    def _run_restart(self, instance_ids: list, mode: str, graceful: bool = False, method: str = None):
        instances = [db.get_instance(i) for i in instance_ids]
        instances = [i for i in instances if i]
        method = method or None  # ''(자동 옵션의 빈 문자열)도 None으로 취급

        def cb(msg: str):
            self._push('restart-progress', {'message': msg})

        def run():
            if mode == 'all':
                results = restart_service.restart_all(instances, cb, graceful=graceful, method=method)
            else:
                results = restart_service.shutdown_instances(instances, cb, graceful=graceful, method=method)
            self._push('restart-done', {'results': results, 'mode': mode})

        threading.Thread(target=run, daemon=True).start()
        return {'started': True}

    # ── 로그 스트림 ──────────────────────────────────────────────────────

    def start_log_stream(self, instance_id: int, tail_lines: int = 100, method: str = None,
                         log_filename: str = None):
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}

        def on_line(iid: int, line: str):
            self._push('log-line', {'instance_id': iid, 'line': line})

        method = method or None
        log_filename = (log_filename or '').strip() or None
        self._log_manager.start(inst, on_line, tail_lines, method=method, log_filename=log_filename)
        chosen = method if method in ('API', 'SSH') else ('SSH' if inst.ssh_enabled else 'API')
        return {'success': True, 'mode': chosen}

    def stop_log_stream(self, instance_id: int):
        self._log_manager.stop(instance_id)
        return {'success': True}

    def list_log_files(self, instance_id: int):
        """API 방식으로 조회 가능한 로그 파일 목록 (드롭다운 선택용). MI 전용."""
        inst = db.get_instance(instance_id)
        if not inst:
            return {'success': False, 'error': 'Instance not found'}
        if not is_mi_type(inst.type):
            return {'success': False, 'error': 'MI 인스턴스에서만 로그 파일 목록을 지원합니다.'}
        try:
            with MIClient(inst) as mi:
                files = mi.list_log_files()
            return {'success': True, 'items': files}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    # ── 감사 로그 ────────────────────────────────────────────────────────

    def get_audit_logs(self, limit: int = 200, action: str = '', result: str = ''):
        return _serialize_list(get_logs(limit=limit, action=action, result=result))

    def export_audit_csv(self, rows: list):
        win = self._window()
        if win is None or not rows:
            return {'success': False, 'error': 'No data'}
        default_name = f'audit_{datetime.now().strftime("%Y%m%d_%H%M%S")}.csv'
        path = win.create_file_dialog(
            webview.SAVE_DIALOG, save_filename=default_name,
            file_types=('CSV Files (*.csv)',),
        )
        if not path:
            return {'success': False, 'error': 'Cancelled'}
        target = path if isinstance(path, str) else path[0]
        import csv
        with open(target, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)
        return {'success': True, 'path': target}

    # ── 종료 ─────────────────────────────────────────────────────────────

    def shutdown(self):
        self._log_manager.stop_all()
