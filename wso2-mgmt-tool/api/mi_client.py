"""WSO2 MI Management API 래퍼 (v4.x 기준)."""
import base64
import io
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any, Optional
from urllib.parse import quote

import requests

from api.base_client import ApiClient
from db.models import Instance, is_mi_type


class MIClient:
    def __init__(self, inst: Instance):
        assert is_mi_type(inst.type)
        self.inst = inst
        self._c = ApiClient(inst)

    # ── 서버 정보 ──────────────────────────────────────────────────────────

    def get_server_info(self) -> dict:
        return self._c.get('/server').json()

    def ping(self) -> bool:
        try:
            self.get_server_info()
            return True
        except Exception:
            return False

    # ── Carbon Application ─────────────────────────────────────────────────

    def list_apps(self) -> list[dict]:
        """배포된 CAR 앱 목록. 실제 응답은 {"list":[...]}가 아니라
        {"activeList":[...], "faultyList":[...]}. 각 항목에는 등록명/버전만 있고
        원본 파일명이 없으므로 Maven/CAR 빌드 관례({name}_{version}.car)로 유도해
        둔다 — 다운로드(carbonAppName=파일명)와 삭제(경로파라미터=파일명 stem) 모두
        이 파일명이 필요하다 (라이브 검증 완료)."""
        data = self._c.get('/applications').json()
        apps = []
        for a in data.get('activeList', []):
            apps.append({**a, 'file_name': f"{a['name']}_{a['version']}.car", 'faulty': False})
        for a in data.get('faultyList', []):
            apps.append({**a, 'file_name': f"{a['name']}_{a['version']}.car", 'faulty': True})
        return apps

    def deploy_app(self, car_path: str | Path) -> dict:
        car_path = Path(car_path)
        with open(car_path, 'rb') as f:
            resp = self._c.post(
                '/applications',
                files={'file': (car_path.name, f, 'application/octet-stream')},
            )
        return resp.json()

    def undeploy_app(self, file_name: str) -> dict:
        """CAR 삭제. 실제 계약은 DELETE /applications/{name} (경로 파라미터)이며,
        여기서 name은 앱 등록명이 아니라 원본 파일명에서 .car 확장자만 뗀 문자열이어야
        정확히 매칭된다 (CarbonAppResource가 filename.equals(name + ".car")로 비교).
        라이브 검증: 쿼리스트링(?appFileName=)은 항상 400, 등록명만 넘기면 404."""
        stem = file_name[:-4] if file_name.lower().endswith('.car') else file_name
        return self._c.delete(f'/applications/{quote(stem)}').json()

    def download_app(self, file_name: str) -> bytes:
        """배포된 CAR 원본 바이너리 다운로드 (삭제 전 백업용).
        GET /applications?carbonAppName={파일명.car} + Accept: application/octet-stream
        (라이브 검증: 실제 zip 바이너리 응답 확인). carbonAppName은 삭제와 달리
        확장자를 포함한 실제 파일명이어야 한다."""
        headers = self._c._headers()
        headers['Accept'] = 'application/octet-stream'
        return self._c.get(f'/applications?carbonAppName={quote(file_name)}', headers=headers).content

    # ── Sequence ───────────────────────────────────────────────────────────

    def list_sequences(self) -> list[dict]:
        return self._c.get('/sequences').json().get('list', [])

    def get_sequence(self, name: str) -> dict:
        return self._c.get(f'/sequences/{name}').json()

    # ── Connector / JAR ────────────────────────────────────────────────────

    def list_connectors(self) -> list[dict]:
        return self._c.get('/connectors').json().get('list', [])

    def deploy_connector(self, zip_path: str | Path) -> dict:
        zip_path = Path(zip_path)
        with open(zip_path, 'rb') as f:
            resp = self._c.post(
                '/connectors',
                files={'file': (zip_path.name, f, 'application/octet-stream')},
            )
        return resp.json()

    # ── 데이터소스 ────────────────────────────────────────────────────────
    # GET /data-sources — 파라미터 없으면 전체 목록, name= 이면 단건 상세,
    # searchKey= 이면 부분 검색 (DataSourceResource, GET만 허용).

    def list_datasources(self) -> list[dict]:
        return self._c.get('/data-sources').json().get('list', [])

    def get_datasource(self, name: str) -> dict:
        return self._c.get(f'/data-sources?name={quote(name)}').json()

    def search_datasources(self, search_key: str) -> list[dict]:
        return self._c.get(f'/data-sources?searchKey={quote(search_key)}').json().get('list', [])

    @staticmethod
    def _jndi_name(ds_name: str) -> str:
        """데이터소스 등록명(예: SST0001_ORACLE)에서 JNDI 이름(jdbc/SST0001)을 유도한다.
        '_' 앞부분을 jdbc/ 접두어와 결합하는 규약 — SIIS JDBC 커넥터 프로젝트의 명명 규칙 기준."""
        return f'jdbc/{ds_name.split("_")[0]}'

    def test_datasource_connection(self, jndi_name: str) -> dict:
        """SIIS JDBC 커넥터가 배포한 자체 dbinfo API로 실제 접속 테스트 + DB 메타데이터 조회.
        Management API(9164, 인증 필요)가 아니라 데이터플레인 서비스 포트(기본 8290, 인증
        불필요, plain HTTP)에서 동작한다."""
        resp = requests.post(
            f'{self.inst.service_base_url}/mi/jdbc/v1/dbinfo',
            json={'action': 'CONNECTION_TEST', 'jndi_name': jndi_name},
            timeout=15,
        )
        # dbinfo는 실패(JNDI lookup 실패 등)도 200이 아닌 상태코드(예: 500)로
        # {"success": false, "error": "..."} JSON을 돌려준다. raise_for_status()를
        # 먼저 하면 이 진짜 원인 메시지를 버리고 raw HTTP 에러만 남으므로,
        # JSON 파싱이 되면 상태코드와 무관하게 그대로 반환한다.
        try:
            return resp.json()
        except ValueError:
            resp.raise_for_status()
            raise

    def test_datasource(self, ds_name: str) -> dict:
        return self.test_datasource_connection(self._jndi_name(ds_name))

    @staticmethod
    def _find_datasource_artifact_name(app: dict, ds_name: str) -> Optional[str]:
        """CAR 앱의 artifacts 중 이 데이터소스에 해당하는 실제 아티팩트 이름을 찾는다.

        환경에 따라 CAR 내부 아티팩트명이 데이터소스 등록명과 완전히 같지 않고
        뒤에 티켓ID 등 접미사가 붙는 경우가 있다 (라이브 확인:
        등록명 "SDI_PACK_MES_VIETNAM" / 실제 아티팩트명 "SDI_PACK_MES_VIETNAM_SST6524DEF").
        그래서 완전 일치를 우선하되, 없으면 "등록명_"으로 시작하는 것도 허용한다.
        단순 부분 포함(contains)으로 하면 "SDI_PACK_MES_VIETNAM"이
        "SDI_PACK_CMES_VIETNAM_..."(전혀 다른 데이터소스)까지 잘못 집어낼 수 있어
        반드시 "_"로 끝나는 접두사인지까지 확인한다.
        """
        candidates = [
            a['name'] for a in app.get('artifacts', [])
            if a.get('type') == 'datasource' and a.get('name')
            and (a['name'] == ds_name or a['name'].startswith(f'{ds_name}_'))
        ]
        if not candidates:
            return None
        return ds_name if ds_name in candidates else candidates[0]

    def _read_datasource_definition(self, zf: zipfile.ZipFile, artifact_name: str):
        """CAR(zip) 안에서 artifact_name의 artifact.xml -> 실제 정의 XML을 찾아 파싱한다.
        반환: (등록명 엘리먼트 <name>, JNDI 엘리먼트 <jndiConfig><name>). 못 찾으면 (None, None)."""
        names = zf.namelist()
        artifact_xml_name = next(
            (n for n in names if n.startswith(f'{artifact_name}_') and n.endswith('artifact.xml')), None)
        if not artifact_xml_name:
            return None, None
        artifact_root = ET.fromstring(zf.read(artifact_xml_name))
        file_el = artifact_root.find('./file')
        if file_el is None or not file_el.text:
            return None, None
        ds_xml_path = artifact_xml_name.rsplit('/', 1)[0] + '/' + file_el.text.strip()
        if ds_xml_path not in zf.namelist():
            return None, None
        ds_root = ET.fromstring(zf.read(ds_xml_path))
        return ds_root.find('./name'), ds_root.find('./jndiConfig/name')

    def get_datasource_jndi_name(self, ds_name: str) -> Optional[str]:
        """실제 JNDI 이름을 배포된 CAR에서 직접 읽어온다 (_jndi_name()의 명명규칙 추정이
        아니라 정확한 값). 데이터소스는 Management API 응답에 JNDI 정보가 없어서(라이브
        확인됨 — /data-sources 응답 어디에도 jndi 필드가 없음), 그 데이터소스를 포함한
        CAR을 다운로드해 안의 datasource 정의 XML을 파싱해야 한다.

        정의 XML엔 두 개의 서로 다른 <name>이 있다:
          <datasource><name>등록명</name>            ← /data-sources의 name과 같은 값
                      <jndiConfig><name>JNDI명</name></jndiConfig></datasource>
        CAR 내부 "아티팩트 이름"(폴더명, 빌드/티켓ID가 섞인 값)은 이 등록명과 다를 수
        있고, 극단적으로는 완전히 무관할 수도 있다. 그래서 2단계로 찾는다:
          1차) 빠른 길 — 아티팩트 이름이 등록명과 같거나 "등록명_접미사" 형태인 CAR만
               우선 확인 (대부분의 환경에서 이걸로 충분, CAR 다운로드 최소화)
          2차) 폴백 — 1차로 못 찾으면, 이름 규칙을 아예 무시하고 type=datasource인
               모든 아티팩트를 열어 정의 XML의 최상위 <name>이 실제로 ds_name과
               같은지 "내용"으로 직접 비교한다. 아티팩트 이름이 등록명과 완전히
               무관해도 이 경로로는 반드시 찾을 수 있다.

        보안 주의: 그 정의 XML에는 DB 비밀번호가 평문으로 들어있을 수 있다(라이브 확인됨).
        이 메서드는 jndiConfig/name 값 하나만 추출해서 반환하고 XML 원문이나 다른 필드는
        절대 반환/로깅하지 않는다.
        """
        apps = self.list_apps()
        downloaded: dict = {}

        def get_zip(file_name: str) -> zipfile.ZipFile:
            if file_name not in downloaded:
                downloaded[file_name] = self.download_app(file_name)
            return zipfile.ZipFile(io.BytesIO(downloaded[file_name]))

        # 1차: 이름 기반 빠른 매칭
        for app in apps:
            artifact_name = self._find_datasource_artifact_name(app, ds_name)
            if artifact_name is None:
                continue
            with get_zip(app['file_name']) as zf:
                _, jndi_el = self._read_datasource_definition(zf, artifact_name)
                if jndi_el is not None and jndi_el.text:
                    return jndi_el.text.strip()

        # 2차 폴백: 아티팩트 이름이 등록명과 아예 무관할 수 있으므로, type=datasource인
        # 모든 아티팩트를 내용까지 열어서 <name>(등록명)으로 직접 재확인한다.
        for app in apps:
            for a in app.get('artifacts', []):
                if a.get('type') != 'datasource' or not a.get('name'):
                    continue
                artifact_name = a['name']
                with get_zip(app['file_name']) as zf:
                    name_el, jndi_el = self._read_datasource_definition(zf, artifact_name)
                    if name_el is None or (name_el.text or '').strip() != ds_name:
                        continue
                    if jndi_el is not None and jndi_el.text:
                        return jndi_el.text.strip()

        return None

    def test_datasource_direct(self, url: str, user: str, password: str) -> dict:
        """등록 전 DB 접속 테스트 (dbinfo API, action=CONNECTION_TEST_DIRECT). 이미 등록된
        JNDI 데이터소스가 아니라 URL/계정 정보만으로 직접 접속을 확인한다 — 데이터소스를
        실제로 등록하기 전에 접속 가능 여부를 미리 확인하는 용도."""
        resp = requests.post(
            f'{self.inst.service_base_url}/mi/jdbc/v1/dbinfo',
            json={'action': 'CONNECTION_TEST_DIRECT', 'url': url, 'user': user, 'password': password},
            timeout=15,
        )
        try:
            return resp.json()
        except ValueError:
            resp.raise_for_status()
            raise

    # ── JVM 모니터링 ──────────────────────────────────────────────────────

    def get_jvm_info(self) -> dict:
        """이 MI 인스턴스 자신의 JVM/데이터소스 커넥션 풀 상태 조회 (SIIS 커넥터의
        JvmInfoMediator, /mi/monitor/v1/jvminfo). dbinfo와 마찬가지로 데이터플레인
        서비스 포트(기본 8290)에서 인증 없이 동작하며, dbinfo와는 완전히 별개의
        URL·클래스로 배포된 기능이다 (JDBC 처리와 코드/리소스 경합 없음)."""
        resp = requests.post(
            f'{self.inst.service_base_url}/mi/monitor/v1/jvminfo',
            json={'action': 'JVM_INFO'},
            timeout=15,
        )
        try:
            return resp.json()
        except ValueError:
            resp.raise_for_status()
            raise

    # ── 재시작 / 정지 ─────────────────────────────────────────────────────

    def restart(self, graceful: bool = True) -> dict:
        """MI 인스턴스 재시작. Management API는 PATCH /server + status 바디로 제어한다
        (org.wso2.micro.integrator.management.apis.MetaDataResource 기준,
        허용 status: shutdown/shutdownGracefully/restart/restartGracefully)."""
        status = 'restartGracefully' if graceful else 'restart'
        return self._c.patch('/server', json={'status': status}).json()

    def shutdown(self, graceful: bool = True) -> dict:
        """MI 인스턴스 정지 (위와 동일한 /server PATCH 규약)."""
        status = 'shutdownGracefully' if graceful else 'shutdown'
        return self._c.patch('/server', json={'status': status}).json()

    # ── 로그 ──────────────────────────────────────────────────────────────

    def list_log_files(self) -> list[dict]:
        return self._c.get('/logs').json().get('list', [])

    def _log_viewer_call(self, action: str, **params) -> dict:
        """SIISManagementApi가 배포하는 자체 로그 뷰어 API 호출
        (POST {service_base_url}/mi/monitor/v1/logviewer).

        WSO2 MI 기본 제공 로그 API(GET /logs?file=)는 부분 읽기를 지원하지 않아
        매 호출마다 파일 전체를 서버 메모리에 올려 응답한다(LogFilesResource
        디컴파일로 확인) — 로그 뷰어처럼 몇 초 간격으로 폴링하는 용도로는 대용량
        로그 파일에서 서버/네트워크 부담이 크다. 이 API는 SIISManagementJava의
        LogFileExecutor가 파일 끝에서 역방향 탐색(TAIL) 또는 이전 오프셋 이후만
        읽는(READ) 방식으로 구현해, 파일 크기와 무관하게 가볍다.

        dbinfo(test_datasource_connection)와 마찬가지로 Management API(9164, 인증
        필요)가 아니라 데이터플레인 서비스 포트(기본 8290, 인증 불필요)에서 동작하며,
        SIISManagementApi CAR이 배포되어 있지 않은 인스턴스에서는 404/연결 오류가 난다."""
        resp = requests.post(
            f'{self.inst.service_base_url}/mi/monitor/v1/logviewer',
            json={'action': action, **params},
            timeout=15,
        )
        try:
            return resp.json()
        except ValueError:
            resp.raise_for_status()
            raise

    def log_viewer_list_files(self) -> list[dict]:
        return self._log_viewer_call('LIST_FILES').get('files', [])

    def log_viewer_tail(self, filename: str, lines: int = 200) -> dict:
        """파일 끝에서 최근 N줄만 읽는다 (로그 뷰어 최초 진입 시)."""
        return self._log_viewer_call('TAIL', file=filename, lines=lines)

    def log_viewer_read(self, filename: str, offset: int = 0, max_bytes: Optional[int] = None) -> dict:
        """이전 응답의 nextOffset부터 이어서 읽는다 (폴링용, tail -f 유사) —
        새로 추가된 만큼만 읽으므로 파일 크기와 무관하게 비용이 거의 일정하다."""
        params: dict = {'file': filename, 'offset': offset}
        if max_bytes is not None:
            params['maxBytes'] = max_bytes
        return self._log_viewer_call('READ', **params)

    def log_viewer_search(self, filename: str, keyword: str,
                          max_matches: int = 100, max_lines_scanned: int = 200_000) -> dict:
        """파일을 스트리밍으로 훑으며 키워드가 포함된 줄만 찾는다."""
        return self._log_viewer_call(
            'SEARCH', file=filename, keyword=keyword,
            maxMatches=max_matches, maxLinesScanned=max_lines_scanned)

    def get_log_levels(self) -> list[dict]:
        return self._c.get('/logging').json().get('list', [])

    def set_log_level(self, logger_name: str, level: str) -> dict:
        return self._c.patch('/logging', json={'loggerName': logger_name, 'loggingLevel': level}).json()

    # ── 파일 배포 (SSH 대체) ──────────────────────────────────────────────
    # SIISManagementApi가 배포하는 자체 파일 배포 API 호출
    # (POST {service_base_url}/mi/monitor/v1/filedeploy). JAR/시퀀스 배포는 원래
    # SSH/SFTP로만 가능했는데(WSO2 Management API에 lib/시퀀스 디렉터리용 엔드포인트가
    # 없어서), SSH를 켤 수 없는 인스턴스는 배포 자체가 불가능했다. 이 API는 대상
    # 서버 안에서 직접 실행되므로 파일 내용을 base64로 실어 보내면 SSH 없이도 같은
    # 결과를 얻는다 — dbinfo/jvminfo/logviewer와 마찬가지로 인증 불필요한 서비스
    # 포트(기본 8290)에서 동작하며, SIISManagementApi CAR이 배포되어 있지 않은
    # 인스턴스에서는 404/연결 오류가 난다.

    def _file_deploy_call(self, action: str, **params) -> dict:
        resp = requests.post(
            f'{self.inst.service_base_url}/mi/monitor/v1/filedeploy',
            json={'action': action, **params},
            timeout=30,  # 파일 업로드라 로그 조회보다 여유 있게
        )
        try:
            return resp.json()
        except ValueError:
            resp.raise_for_status()
            raise

    def file_deploy_list(self, artifact_type: str) -> list[dict]:
        """artifact_type: 'JAR' | 'SEQUENCE'."""
        return self._file_deploy_call('LIST_FILES', type=artifact_type).get('files', [])

    def file_deploy_download(self, artifact_type: str, file_name: str) -> dict:
        """{'exists': False} 또는 {'exists': True, 'size':.., 'content': <base64>}.
        덮어쓰기/삭제 전 클라이언트가 기존 파일을 백업하는 용도 (SSH 방식과 동일한 흐름)."""
        return self._file_deploy_call('DOWNLOAD', type=artifact_type, file=file_name)

    def file_deploy_upload(self, artifact_type: str, file_name: str, content: bytes) -> dict:
        """SEQUENCE 타입은 서버 쪽에서 루트 엘리먼트가 <sequence>인지 검증한다."""
        return self._file_deploy_call(
            'DEPLOY', type=artifact_type, file=file_name,
            content=base64.b64encode(content).decode('ascii'))

    def file_deploy_delete(self, artifact_type: str, file_name: str) -> dict:
        return self._file_deploy_call('DELETE', type=artifact_type, file=file_name)

    # ── 레지스트리 리소스 ────────────────────────────────────────────────
    # GET/PUT/POST/DELETE /registry-resources/content?path={경로}&mediaType=text
    # (라이브 검증: GET 응답은 JSON이 아니라 리소스 원문 텍스트 그대로. PUT은 기존
    # 리소스 수정 전용 — 없는 경로면 400 "Registry does not exists". POST는 신규
    # 생성 전용 — 이미 있으면 400 "Can not POST an existing registry". 쓰기 요청은
    # Content-Type: text/plain;charset=utf-8 필수.)

    def list_registry_resources(self, path: str) -> list[dict]:
        """레지스트리 컬렉션(디렉터리)의 자식 목록 (브라우징용). 각 항목은
        {"name":..., "mediaType": "directory"|<실제 미디어타입>, "properties":[...]} —
        mediaType이 "directory"면 하위 탐색 가능한 컬렉션, 아니면 리프(파일) 리소스다.
        content 엔드포인트와 달리 이쪽은 항상 쿼리 파라미터 path (라이브 검증)."""
        return self._c.get(f'/registry-resources?path={quote(path)}').json().get('list', [])

    def search_registry_resources(self, root_path: str, keyword: str,
                                  max_dirs: int = 300, max_results: int = 200) -> dict:
        """이름에 keyword가 포함된(대소문자 무시) 리소스를 root_path 아래에서 재귀적으로
        찾는다. RegistryResource(/registry-resources)의 searchKey 파라미터는 디컴파일로
        확인한 결과 "이름 포함 검색"이 아니라 populateRegistryResourceJSON()에 그대로
        넘겨져 "정확한 이름으로 단건 직접 조회"하는 용도였다 — 서버 쪽엔 진짜 포함검색
        API가 없다. 그래서 list_registry_resources()로 디렉터리를 하나씩 훑어가며
        클라이언트에서 직접 필터링한다. 레지스트리가 크면 느릴 수 있어 방문 디렉터리 수와
        결과 개수에 상한을 둔다(둘 중 하나라도 넘으면 truncated=True)."""
        kw = keyword.lower()
        matches: list[dict] = []
        visited_dirs = 0
        truncated = False
        queue = [root_path]
        while queue:
            if visited_dirs >= max_dirs:
                truncated = True
                break
            path = queue.pop(0)
            visited_dirs += 1
            try:
                items = self.list_registry_resources(path)
            except Exception:
                continue
            for item in items:
                name = item.get('name', '')
                child_path = f"{path}/{name}"
                is_dir = item.get('mediaType') == 'directory'
                if kw in name.lower():
                    matches.append({'path': child_path, 'name': name, 'is_dir': is_dir})
                    if len(matches) >= max_results:
                        return {'matches': matches, 'truncated': True}
                if is_dir:
                    queue.append(child_path)
        return {'matches': matches, 'truncated': truncated}

    def get_registry_resource(self, path: str) -> str:
        return self._c.get(f'/registry-resources/content?path={quote(path)}&mediaType=text').text

    def save_registry_resource(self, path: str, content: str) -> dict:
        """존재 여부를 몰라도 알아서 생성/수정을 판단한다: PUT(수정)을 먼저 시도하고,
        "존재하지 않음" 오류일 때만 POST(생성)로 재시도한다."""
        q = f'/registry-resources/content?path={quote(path)}&mediaType=text'
        try:
            resp = self._c.put_text(q, content)
            return {'mode': 'UPDATE', **resp.json()}
        except requests.HTTPError as e:
            msg = ''
            if e.response is not None:
                try:
                    msg = str(e.response.json().get('Error', ''))
                except ValueError:
                    pass
            if 'does not exist' not in msg.lower():
                raise
            resp = self._c.post_text(q, content)
            return {'mode': 'CREATE', **resp.json()}

    def delete_registry_resource(self, path: str) -> dict:
        return self._c.delete(f'/registry-resources/content?path={quote(path)}&mediaType=text').json()

    def close(self):
        self._c.close()
