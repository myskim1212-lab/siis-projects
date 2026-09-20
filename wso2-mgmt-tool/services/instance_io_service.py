"""등록된 서버그룹/서버/인스턴스그룹/인스턴스 정보의 export/import.

DeploymentHistory/AuditLog 등 운영 이력은 대상이 아니고, "등록 정보"
(ServerGroup + Server + InstanceGroup + Instance, admin/SSH 자격증명 포함)만 다룬다.

EXPORT_VERSION 이력:
  1: 그룹>인스턴스 2단 구조 (서버 계층 도입 이전)
  2: 그룹>서버>인스턴스 3단 구조
  3: 서버그룹>서버>인스턴스그룹>인스턴스 4단 구조 (현재)
import_data()는 세 버전을 모두 읽을 수 있다 — 옛 파일(v1/v2)은 각 계층을 1:1로
새 구조에 대응시키는 대신, 아래 규칙으로 흡수한다:
  v1: 그룹 -> server_group, 인스턴스는 DEFAULT_SERVER_NAME 서버 + DEFAULT_INSTANCE_GROUP_NAME
      인스턴스그룹 아래로.
  v2: 그룹 -> server_group, 서버는 이름 그대로 유지, 인스턴스는 그 서버 아래
      DEFAULT_INSTANCE_GROUP_NAME 인스턴스그룹으로.
"""
from dataclasses import asdict
from datetime import datetime
from typing import List, Optional, Set

from db.database import (
    DEFAULT_INSTANCE_GROUP_NAME,
    DEFAULT_SERVER_NAME,
    get_all_server_groups,
    get_groups_by_server,
    get_instances_by_group,
    get_servers_by_server_group,
    save_group,
    save_instance,
    save_server,
    save_server_group,
)
from db.models import Instance, InstanceGroup, Server, ServerGroup

EXPORT_VERSION = 3

# id/*_id/created_at는 원본 DB 상태에 종속된 값이라, import 시에는 새로 발급되게 제외한다
# (그래야 다른 DB로 옮기거나 재수입해도 충돌 없이 새 레코드로 생성되거나, 이름 매칭으로
# 기존 레코드에 정확히 합쳐짐).
_SERVER_GROUP_EXCLUDE = {'id', 'created_at'}
_SERVER_EXCLUDE = {'id', 'server_group_id', 'created_at'}
_GROUP_EXCLUDE = {'id', 'server_id', 'created_at'}
_INSTANCE_EXCLUDE = {'id', 'group_id', 'created_at'}


def export_selected(instance_ids: Optional[Set[int]] = None) -> dict:
    """서버그룹>서버>인스턴스그룹>인스턴스를 계층 구조 dict로 만든다 (그대로 json.dump 가능).

    instance_ids가 None이면 전체를 내보낸다. 값이 주어지면 그 id들에 해당하는
    인스턴스만 포함하고, 내보낼 인스턴스가 하나도 없는 인스턴스그룹/서버/서버그룹은
    결과에서 통째로 제외한다.
    """
    server_groups_data = []
    for sg in get_all_server_groups():
        servers_data = []
        for s in get_servers_by_server_group(sg.id):
            groups_data = []
            for g in get_groups_by_server(s.id):
                instances = get_instances_by_group(g.id)
                if instance_ids is not None:
                    instances = [i for i in instances if i.id in instance_ids]
                    if not instances:
                        continue
                g_dict = {k: v for k, v in asdict(g).items() if k not in _GROUP_EXCLUDE}
                g_dict['instances'] = [
                    {k: v for k, v in asdict(i).items() if k not in _INSTANCE_EXCLUDE}
                    for i in instances
                ]
                groups_data.append(g_dict)
            if not groups_data:
                continue
            s_dict = {k: v for k, v in asdict(s).items() if k not in _SERVER_EXCLUDE}
            s_dict['groups'] = groups_data
            servers_data.append(s_dict)
        if not servers_data:
            continue
        sg_dict = {k: v for k, v in asdict(sg).items() if k not in _SERVER_GROUP_EXCLUDE}
        sg_dict['servers'] = servers_data
        server_groups_data.append(sg_dict)

    return {
        'export_version': EXPORT_VERSION,
        'exported_at': datetime.now().isoformat(),
        'server_groups': server_groups_data,
    }


def _iter_group_buckets(s_data: dict):
    """서버 dict 하나에서 (인스턴스그룹명, 설명, 인스턴스dict목록) 튜플을 순서대로 뽑는다.
    v3(groups 키)와 v2(instances 키를 서버 dict가 직접 가짐) 파일을 여기서 함께 흡수한다."""
    if 'groups' in s_data:
        for g_data in s_data.get('groups', []):
            g_name = (g_data.get('name') or '').strip() or DEFAULT_INSTANCE_GROUP_NAME
            yield g_name, g_data.get('description', ''), g_data.get('instances', [])
    else:
        # v2(인스턴스그룹 계층 없음) — 이 서버의 인스턴스를 전부 기본 인스턴스그룹 하나로 이관
        instances = s_data.get('instances', [])
        if instances:
            yield DEFAULT_INSTANCE_GROUP_NAME, '구버전(v2) 가져오기 파일 마이그레이션으로 생성된 기본 그룹', instances


def _iter_server_buckets(sg_data: dict):
    """서버그룹(v3) 또는 그룹(v1/v2) dict 하나에서 (서버명, 서버설명, _iter_group_buckets용
    서버 dict) 튜플을 순서대로 뽑는다. v1(서버 계층 자체가 없음)은 DEFAULT_SERVER_NAME
    서버 하나로 묶는다."""
    if 'servers' in sg_data:
        for s_data in sg_data.get('servers', []):
            s_name = (s_data.get('name') or '').strip() or DEFAULT_SERVER_NAME
            yield s_name, s_data.get('description', ''), s_data
    else:
        # v1(서버/인스턴스그룹 계층 모두 없음) — 이 그룹의 인스턴스를 전부 기본 서버 +
        # 기본 인스턴스그룹 하나로 이관
        instances = sg_data.get('instances', [])
        if instances:
            yield DEFAULT_SERVER_NAME, '구버전(v1) 가져오기 파일 마이그레이션으로 생성된 기본 서버', {'instances': instances}


def summarize(data: dict) -> dict:
    """가져오기 전 미리보기용 — 실제 DB에 손대지 않고 파일 내용만 요약한다."""
    server_groups = []
    total_instances = 0
    for sg_data in data.get('server_groups', data.get('groups', [])):
        servers = []
        for s_name, _s_desc, s_data in _iter_server_buckets(sg_data):
            groups = []
            for g_name, _g_desc, i_list in _iter_group_buckets(s_data):
                names = [i.get('name', '(이름없음)') for i in i_list]
                groups.append({'name': g_name, 'instance_names': names})
                total_instances += len(names)
            servers.append({'name': s_name, 'groups': groups})
        server_groups.append({'name': sg_data.get('name', '(이름없음)'), 'servers': servers})
    return {
        'export_version': data.get('export_version'),
        'exported_at': data.get('exported_at'),
        'server_group_count': len(server_groups),
        'instance_count': total_instances,
        'server_groups': server_groups,
    }


def import_data(data: dict) -> dict:
    """export_selected()가 만든(또는 같은 구조의) dict를 서버그룹/서버/인스턴스그룹/
    인스턴스로 반영한다.

    각 계층은 같은 부모 아래에서 이름이 겹치면 기존 레코드를 재사용한다 (서버그룹은
    전역 UNIQUE, 서버/인스턴스그룹은 각자의 부모 범위 안에서만 유일하면 됨). 인스턴스는
    **같은 인스턴스그룹 안에 같은 이름의 인스턴스가 이미 있으면 그 레코드를 그대로
    업데이트**하고, 없으면 새로 생성한다 (재수입해도 중복이 쌓이지 않고 최신 값으로 덮어써짐).

    export_version 1(서버/인스턴스그룹 계층 없음), 2(인스턴스그룹 계층 없음)로 만들어진
    옛 파일도 그대로 읽을 수 있다 — _iter_server_buckets()/_iter_group_buckets()가 부족한
    계층을 기본 서버/기본 그룹으로 자동 보강한다.
    """
    existing_server_groups = {sg.name: sg for sg in get_all_server_groups()}
    created_server_groups = 0
    created_servers = 0
    created_groups = 0
    created_instances = 0
    updated_instances = 0
    errors: List[str] = []

    for sg_data in data.get('server_groups', data.get('groups', [])):
        sg_name = (sg_data.get('name') or '').strip()
        if not sg_name:
            errors.append('이름이 없는 서버그룹 항목을 건너뜀')
            continue

        server_group = existing_server_groups.get(sg_name)
        if server_group is None:
            try:
                server_group = save_server_group(ServerGroup(
                    name=sg_name, description=sg_data.get('description', ''),
                ))
                existing_server_groups[sg_name] = server_group
                created_server_groups += 1
            except Exception as e:
                errors.append(f'서버그룹 [{sg_name}] 생성 실패: {e}')
                continue

        existing_servers_by_name = {s.name: s for s in get_servers_by_server_group(server_group.id)}

        for s_name, s_desc, s_data in _iter_server_buckets(sg_data):
            server = existing_servers_by_name.get(s_name)
            if server is None:
                try:
                    server = save_server(Server(
                        server_group_id=server_group.id, name=s_name,
                        environment=s_data.get('environment', 'DEV'),
                        ip=s_data.get('ip', ''), description=s_desc,
                    ))
                    existing_servers_by_name[s_name] = server
                    created_servers += 1
                except Exception as e:
                    errors.append(f'서버 [{sg_name}/{s_name}] 생성 실패: {e}')
                    continue

            existing_groups_by_name = {g.name: g for g in get_groups_by_server(server.id)}

            for g_name, g_desc, i_list in _iter_group_buckets(s_data):
                group = existing_groups_by_name.get(g_name)
                if group is None:
                    try:
                        group = save_group(InstanceGroup(server_id=server.id, name=g_name, description=g_desc))
                        existing_groups_by_name[g_name] = group
                        created_groups += 1
                    except Exception as e:
                        errors.append(f'인스턴스그룹 [{sg_name}/{s_name}/{g_name}] 생성 실패: {e}')
                        continue

                existing_instances_by_name = {i.name: i for i in get_instances_by_group(group.id)}

                for i_data in i_list:
                    i_name = (i_data.get('name') or '').strip() or '(이름없음)'
                    try:
                        fields = {
                            k: v for k, v in i_data.items()
                            if k in Instance.__dataclass_fields__ and k not in _INSTANCE_EXCLUDE
                        }
                        existing = existing_instances_by_name.get(i_name)
                        if existing:
                            inst = Instance(id=existing.id, group_id=group.id, **fields)
                            saved = save_instance(inst)
                            updated_instances += 1
                        else:
                            inst = Instance(group_id=group.id, **fields)
                            saved = save_instance(inst)
                            created_instances += 1
                        existing_instances_by_name[i_name] = saved
                    except Exception as e:
                        errors.append(f'인스턴스 [{sg_name}/{s_name}/{g_name}/{i_name}] 처리 실패: {e}')

    return {
        'created_server_groups': created_server_groups,
        'created_servers': created_servers,
        'created_groups': created_groups,
        'created_instances': created_instances,
        'updated_instances': updated_instances,
        'errors': errors,
    }
