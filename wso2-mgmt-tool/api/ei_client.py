"""WSO2 EI 6.1(Carbon 4.4.x) 데이터소스 관리 — NDataSourceAdmin SOAP 관리 서비스 래퍼.

EI는 MI Management API 같은 REST API가 없다. 데이터소스 등록/조회/수정/삭제는
Carbon의 SOAP 기반 admin 서비스인 NDataSourceAdmin(WSDL 스키마 기준: 서비스 스텁
`org.wso2.carbon.ndatasource.stub`)으로만 가능하다. 인증은 별도 세션 로그인
없이 HTTP Basic Auth로 바로 된다 (Carbon admin 서비스 다수가 지원하는 방식,
이 프로젝트의 MI 로그인처럼 별도 토큰 발급 절차가 필요 없음).

NDataSourceAdmin에는 "수정"에 해당하는 별도 오퍼레이션이 없다 — WSO2 Carbon
콘솔 자신도 데이터소스 수정 시 addDataSource를 그대로 다시 호출한다(이름이
같으면 서버가 덮어씀, savedatasource.jsp 기준). 그래서 이 클라이언트도 등록과
수정 모두 add_datasource() 하나로 처리한다.

SOAP 바디는 별도 SOAP 라이브러리(zeep 등) 없이 문자열 템플릿으로 직접 구성한다.
네임스페이스는 WSDL에 정의된 그대로다:
  - ns1  (http://org.apache.axis2/xsd)                       오퍼레이션 래퍼 + dsmInfo
  - xsd0 (http://services.core.ndatasource.carbon.wso2.org/xsd)  WSDataSourceMetaInfo 필드
  - xsd1 (http://core.ndatasource.carbon.wso2.org/xsd)        JNDIConfig 필드
데이터소스의 실제 DB 접속 정보(driverClassName/url/username/password)는 그
안에서 다시 한번 XML 문자열로 직렬화되어 <definition><dsXMLConfiguration>
안에 이스케이프된 텍스트로 들어간다(RDBMSConfiguration 스키마, Carbon 콘솔이
등록하는 것과 동일한 형태) — 이중 XML 구조라는 점에 주의.
"""
import time
import xml.etree.ElementTree as ET
from typing import Optional
from xml.sax.saxutils import escape as xml_escape

import requests

from db.models import Instance

_ENV_NS = 'http://schemas.xmlsoap.org/soap/envelope/'
_NS1 = 'http://org.apache.axis2/xsd'
_NSD = 'http://services.core.ndatasource.carbon.wso2.org/xsd'
_NSC = 'http://core.ndatasource.carbon.wso2.org/xsd'
_XSI_NIL = '{http://www.w3.org/2001/XMLSchema-instance}nil'

# RDBMSDSXMLConfiguration(carbon-commons ndatasource UI)의 @XmlType propOrder 그대로 —
# driverClassName/url/username/password(핵심 4개)를 제외한 전체 커넥션 풀/동작 옵션.
# dataSourceClassName·dataSourceProps는 여기 없다: 그 둘은 driver+url이 아니라 외부
# DataSource 팩토리 클래스로 접속하는 완전히 다른 방식(dsProviderType=external)이라
# 이 도구가 지원하는 "driver+url" 등록 폼과는 별개의 폼이 필요해 지금은 범위 밖이다.
POOL_FIELDS = (
    'defaultAutoCommit', 'defaultReadOnly', 'defaultTransactionIsolation', 'defaultCatalog',
    'maxActive', 'maxIdle', 'minIdle', 'initialSize', 'maxWait',
    'testOnBorrow', 'testOnReturn', 'testWhileIdle', 'validationQuery', 'validatorClassName',
    'timeBetweenEvictionRunsMillis', 'numTestsPerEvictionRun', 'minEvictableIdleTimeMillis',
    'accessToUnderlyingConnectionAllowed', 'removeAbandoned', 'removeAbandonedTimeout', 'logAbandoned',
    'connectionProperties', 'initSQL', 'jdbcInterceptors', 'validationInterval', 'jmxEnabled', 'fairQueue',
    'abandonWhenPercentageFull', 'maxAge', 'useEquals', 'suspectTimeout', 'validationQueryTimeout',
    'alternateUsernameAllowed',
)


class EIDataSourceError(Exception):
    """NDataSourceAdmin이 SOAP Fault로 돌려준 오류 (DataSourceException.errorMessage)."""


def _local(tag: str) -> str:
    return tag.split('}')[-1]


def _child(el: Optional[ET.Element], name: str) -> Optional[ET.Element]:
    if el is None:
        return None
    for c in el:
        if _local(c.tag) == name:
            return c
    return None


def _text(el: Optional[ET.Element]) -> str:
    return (el.text or '').strip() if el is not None and el.text else ''


def _is_nil_or_empty(el: Optional[ET.Element]) -> bool:
    return el is None or el.get(_XSI_NIL) == 'true' or len(el) == 0


def _extract_fault_message(fault_el: ET.Element) -> str:
    for el in fault_el.iter():
        if _local(el.tag) == 'errorMessage' and el.text:
            return el.text.strip()
    for el in fault_el.iter():
        if _local(el.tag) == 'faultstring' and el.text:
            return el.text.strip()
    return 'EI 데이터소스 서비스 오류'


def _build_config_xml(driver_class_name: str, url: str, username: str, password: str,
                       extra: Optional[dict] = None) -> str:
    """RDBMSConfiguration 스키마의 <configuration> XML을 만든다 (Carbon 콘솔이
    데이터소스를 저장할 때 dsXMLConfiguration에 넣는 것과 동일한 구조)."""
    parts = [
        f'<driverClassName>{xml_escape(driver_class_name)}</driverClassName>',
        f'<url>{xml_escape(url)}</url>',
        f'<username>{xml_escape(username)}</username>',
        f'<password encrypted="false">{xml_escape(password)}</password>',
    ]
    for key in POOL_FIELDS:
        val = (extra or {}).get(key)
        if val not in (None, ''):
            parts.append(f'<{key}>{xml_escape(str(val))}</{key}>')
    return f'<configuration>{"".join(parts)}</configuration>'


def _build_dsm_info_xml(name: str, description: str, jndi_name: str,
                         driver_class_name: str, url: str, username: str, password: str,
                         extra: Optional[dict] = None) -> str:
    """WSDataSourceMetaInfo XML 조각 (WSDL 스키마 순서: definition, description,
    jndiConfig, name, system 그대로)."""
    config_xml = _build_config_xml(driver_class_name, url, username, password, extra)
    jndi_block = ''
    if jndi_name:
        jndi_block = (
            f'<xsd0:jndiConfig>'
            f'<xsd1:name>{xml_escape(jndi_name)}</xsd1:name>'
            f'<xsd1:useDataSourceFactory>false</xsd1:useDataSourceFactory>'
            f'</xsd0:jndiConfig>'
        )
    desc_block = f'<xsd0:description>{xml_escape(description)}</xsd0:description>' if description else ''
    return (
        f'<xsd0:definition>'
        f'<xsd0:dsXMLConfiguration>{xml_escape(config_xml)}</xsd0:dsXMLConfiguration>'
        f'<xsd0:type>RDBMS</xsd0:type>'
        f'</xsd0:definition>'
        f'{desc_block}'
        f'{jndi_block}'
        f'<xsd0:name>{xml_escape(name)}</xsd0:name>'
        f'<xsd0:system>false</xsd0:system>'
    )


def _parse_rdbms_config(xml_text: str) -> dict:
    """dsXMLConfiguration 안에 이중으로 들어있는 <configuration> XML을 다시 파싱한다."""
    if not xml_text:
        return {}
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return {}
    out = {
        'driver_class_name': _text(_child(root, 'driverClassName')),
        'url': _text(_child(root, 'url')),
        'username': _text(_child(root, 'username')),
    }
    pw_el = _child(root, 'password')
    out['password'] = (pw_el.text or '').strip() if pw_el is not None and pw_el.text else ''
    for key in POOL_FIELDS:
        el = _child(root, key)
        if el is not None and el.text:
            out[key] = el.text.strip()
    return out


def _parse_ds_info(info_el: ET.Element) -> dict:
    """WSDataSourceInfo(dsMetaInfo + dsStatus) 하나를 dict로. 비밀번호는 담지 않는다
    (목록/조회 화면으로 그대로 나갈 수 있는 값이라 여기서부터 제외 — 필요하면
    get_datasource_with_password()로 서버 안에서만 사용). driver_class_name/url/
    username과 POOL_FIELDS에 해당하는 나머지 커넥션 풀 옵션은 실제로 설정된 것만
    cfg에서 그대로 옮겨 담는다 (등록/수정 폼에서 값을 다시 채워 보여주기 위함)."""
    meta = _child(info_el, 'dsMetaInfo')
    status = _child(info_el, 'dsStatus')
    definition = _child(meta, 'definition')
    jndi_el = _child(meta, 'jndiConfig')
    cfg = _parse_rdbms_config(_text(_child(definition, 'dsXMLConfiguration'))) if definition is not None else {}
    has_password = bool(cfg.pop('password', ''))
    result = {
        'name': _text(_child(meta, 'name')),
        'description': _text(_child(meta, 'description')),
        'jndi_name': _text(_child(jndi_el, 'name')) if jndi_el is not None else '',
        'type': _text(_child(definition, 'type')) if definition is not None else '',
        'has_password': has_password,
        'status_mode': _text(_child(status, 'mode')) if status is not None else '',
        'status_details': _text(_child(status, 'details')) if status is not None else '',
    }
    result.update(cfg)  # driver_class_name/url/username + 설정된 POOL_FIELDS 전부
    return result


class EIClient:
    def __init__(self, inst: Instance, timeout: int = 20):
        self.inst = inst
        self.timeout = timeout
        self._session = requests.Session()
        self._session.verify = False
        self._session.auth = (inst.admin_user, inst.admin_pass)

    @property
    def _endpoint(self) -> str:
        return f'{self.inst.base_url}/services/NDataSourceAdmin'

    def _soap_call(self, action: str, body_xml: str) -> ET.Element:
        envelope = (
            '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" '
            f'xmlns:ns1="{_NS1}" xmlns:xsd0="{_NSD}" xmlns:xsd1="{_NSC}">'
            f'<soapenv:Body>{body_xml}</soapenv:Body></soapenv:Envelope>'
        )
        headers = {'Content-Type': 'text/xml; charset=UTF-8', 'SOAPAction': f'"urn:{action}"'}
        print(f'[DEBUG][ei-soap] {action} -> {self._endpoint}')
        resp = self._session.post(self._endpoint, data=envelope.encode('utf-8'),
                                   headers=headers, timeout=self.timeout)
        try:
            root = ET.fromstring(resp.content)
        except ET.ParseError:
            resp.raise_for_status()
            raise
        body = root.find(f'{{{_ENV_NS}}}Body')
        fault = _child(body, 'Fault')
        if fault is not None:
            raise EIDataSourceError(_extract_fault_message(fault))
        resp.raise_for_status()
        return body[0] if body is not None and len(body) else body

    # ── 조회 ──────────────────────────────────────────────────────────────

    def ping(self) -> bool:
        try:
            self._soap_call('getDataSourceTypes', '<ns1:getDataSourceTypes/>')
            return True
        except Exception:
            return False

    def list_datasources(self) -> list[dict]:
        result = self._soap_call('getAllDataSources', '<ns1:getAllDataSources/>')
        items = []
        for ret in (result or []):
            if _local(ret.tag) != 'return' or _is_nil_or_empty(ret):
                continue
            items.append(_parse_ds_info(ret))
        return items

    def get_datasource(self, name: str) -> Optional[dict]:
        body = f'<ns1:getDataSource><ns1:dsName>{xml_escape(name)}</ns1:dsName></ns1:getDataSource>'
        result = self._soap_call('getDataSource', body)
        ret = _child(result, 'return')
        if _is_nil_or_empty(ret):
            return None
        return _parse_ds_info(ret)

    def _get_raw_config(self, name: str) -> Optional[dict]:
        """비밀번호 포함 실제 접속 정보 — 수정 시 비밀번호 유지, 기존 데이터소스
        재테스트 용도로만 내부에서 쓰고, bridge 계층 밖(JS)으로는 절대 내보내지 않는다."""
        body = f'<ns1:getDataSource><ns1:dsName>{xml_escape(name)}</ns1:dsName></ns1:getDataSource>'
        result = self._soap_call('getDataSource', body)
        ret = _child(result, 'return')
        if _is_nil_or_empty(ret):
            return None
        meta = _child(ret, 'dsMetaInfo')
        definition = _child(meta, 'definition')
        cfg = _parse_rdbms_config(_text(_child(definition, 'dsXMLConfiguration'))) if definition is not None else {}
        jndi_el = _child(meta, 'jndiConfig')
        cfg['name'] = _text(_child(meta, 'name'))
        cfg['description'] = _text(_child(meta, 'description'))
        cfg['jndi_name'] = _text(_child(jndi_el, 'name')) if jndi_el is not None else ''
        return cfg

    # ── 등록 / 수정 (둘 다 addDataSource) ────────────────────────────────

    def add_datasource(self, name: str, description: str, jndi_name: str,
                        driver_class_name: str, url: str, username: str, password: str,
                        extra: Optional[dict] = None) -> bool:
        dsm = _build_dsm_info_xml(name, description, jndi_name, driver_class_name, url, username, password, extra)
        body = f'<ns1:addDataSource><ns1:dsmInfo>{dsm}</ns1:dsmInfo></ns1:addDataSource>'
        result = self._soap_call('addDataSource', body)
        return _text(_child(result, 'return')).lower() == 'true'

    def save_datasource(self, name: str, description: str, jndi_name: str,
                         driver_class_name: str, url: str, username: str, password: str,
                         extra: Optional[dict] = None, is_edit: bool = False) -> bool:
        """등록(is_edit=False)은 동명 데이터소스가 있으면 거부하고, 수정(is_edit=True)은
        password가 비어 있으면(=변경 안 함) 서버에 저장된 기존 값을 그대로 가져와 채운다
        (WSO2 Carbon 콘솔 자신도 "비밀번호 변경" 체크 안 하면 이렇게 동작함)."""
        if not is_edit:
            if self.get_datasource(name) is not None:
                raise EIDataSourceError(f'이미 존재하는 데이터소스 이름입니다: {name}')
        elif not password:
            existing = self._get_raw_config(name)
            if existing is None:
                raise EIDataSourceError(f'수정할 데이터소스를 찾을 수 없습니다: {name}')
            password = existing.get('password', '')
        return self.add_datasource(name, description, jndi_name, driver_class_name,
                                    url, username, password, extra)

    def delete_datasource(self, name: str) -> bool:
        body = f'<ns1:deleteDataSource><ns1:dsName>{xml_escape(name)}</ns1:dsName></ns1:deleteDataSource>'
        result = self._soap_call('deleteDataSource', body)
        return _text(_child(result, 'return')).lower() == 'true'

    # ── 접속 테스트 ───────────────────────────────────────────────────────

    def test_connection(self, name: str, driver_class_name: str, url: str,
                         username: str, password: str, extra: Optional[dict] = None) -> dict:
        """등록 전(또는 폼에 입력 중인 값 그대로) 접속을 테스트한다. 서버가 실제로
        DriverManager.getConnection()을 시도하므로 결과는 실제 접속 가능 여부다."""
        dsm = _build_dsm_info_xml(name or 'TEST_CONNECTION', '', '', driver_class_name, url, username, password, extra)
        body = f'<ns1:testDataSourceConnection><ns1:dsmInfo>{dsm}</ns1:dsmInfo></ns1:testDataSourceConnection>'
        started = time.time()
        try:
            result = self._soap_call('testDataSourceConnection', body)
            ok = _text(_child(result, 'return')).lower() == 'true'
            elapsed_ms = int((time.time() - started) * 1000)
            return {'success': ok, 'elapsed_ms': elapsed_ms, 'error': None if ok else '접속 실패', 'url': url}
        except EIDataSourceError as e:
            return {'success': False, 'elapsed_ms': int((time.time() - started) * 1000), 'error': str(e), 'url': url}

    def test_existing_datasource(self, name: str) -> dict:
        """이미 등록된 데이터소스를 이름으로 다시 테스트 — 저장된 비밀번호를 서버
        안에서만 읽어 재사용하고 JS로는 내보내지 않는다."""
        cfg = self._get_raw_config(name)
        if cfg is None:
            return {'success': False, 'elapsed_ms': None, 'error': f'데이터소스를 찾을 수 없습니다: {name}'}
        extra = {k: v for k, v in cfg.items() if k in POOL_FIELDS}
        return self.test_connection(name, cfg.get('driver_class_name', ''), cfg.get('url', ''),
                                     cfg.get('username', ''), cfg.get('password', ''), extra)

    def close(self):
        self._session.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        self.close()
        return False
