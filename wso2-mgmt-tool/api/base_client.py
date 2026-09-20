import base64
import contextlib
import json
import time
from typing import Optional

import requests
import urllib3

import db.database as db
from db.models import Instance, is_mi_type

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

_token_cache: dict[int, tuple[str, float]] = {}  # instance_id → (token, expires_at)


def _jwt_expiry(token: str) -> Optional[float]:
    """서명 검증 없이 JWT payload의 exp 클레임만 읽는다."""
    try:
        payload = token.split('.')[1]
        padded = payload + '=' * (-len(payload) % 4)
        claims = json.loads(base64.urlsafe_b64decode(padded))
        return claims.get('exp')
    except Exception:
        return None


def _get_token(inst: Instance) -> str:
    """Bearer token 발급 (메모리 → DB → 실제 로그인 순, 만료 60초 전 갱신).

    MI Management API는 `GET <token_url>` + Basic Auth로 로그인하고
    `{"AccessToken": "..."}`를 반환한다 (OAuth2 client_credentials와는 다른
    WSO2 MI 고유 방식). APIM은 표준 OAuth2 client_credentials(`access_token`)를 쓴다.

    메모리 캐시(`_token_cache`)는 프로세스 재시작 시 사라지는데, 앱을 자주 껐다 켜면
    그때마다 재로그인이 발생해 서버 쪽 로그인 로그가 도배된다. 그래서 아직 만료 전인
    토큰은 DB(`token_cache` 테이블)에도 남겨 재시작 후에도 재사용한다.
    """
    cached = _token_cache.get(inst.id or -1)
    if cached and time.time() < cached[1] - 60:
        return cached[0]

    if inst.id is not None:
        db_cached = db.get_cached_token(inst.id)
        if db_cached and time.time() < db_cached[1] - 60:
            _token_cache[inst.id] = db_cached
            return db_cached[0]

    if is_mi_type(inst.type):
        token_url = inst.token_url or f'https://{inst.host}:{inst.port}/management/login'
        print(f'[DEBUG][token] instance_id={inst.id} name={inst.name!r} GET {token_url} (user={inst.admin_user!r})')
        try:
            resp = requests.get(
                token_url,
                auth=(inst.admin_user, inst.admin_pass),
                verify=False,
                timeout=10,
            )
        except Exception as e:
            print(f'[DEBUG][token] request failed before response: {type(e).__name__}: {e}')
            raise
        print(f'[DEBUG][token] status={resp.status_code} body={resp.text[:300]!r}')
        resp.raise_for_status()
        token = resp.json()['AccessToken']
        expires_at = _jwt_expiry(token) or (time.time() + 3600)
    else:
        token_url = inst.token_url or f'https://{inst.host}:{inst.port}/oauth2/token'
        print(f'[DEBUG][token] instance_id={inst.id} name={inst.name!r} POST {token_url} (user={inst.admin_user!r})')
        try:
            resp = requests.post(
                token_url,
                data={'grant_type': 'client_credentials'},
                auth=(inst.admin_user, inst.admin_pass),
                verify=False,
                timeout=10,
            )
        except Exception as e:
            print(f'[DEBUG][token] request failed before response: {type(e).__name__}: {e}')
            raise
        print(f'[DEBUG][token] status={resp.status_code} body={resp.text[:300]!r}')
        resp.raise_for_status()
        data = resp.json()
        token = data['access_token']
        expires_at = time.time() + data.get('expires_in', 3600)

    if inst.id is not None:
        _token_cache[inst.id] = (token, expires_at)
        db.set_cached_token(inst.id, token, expires_at)
    return token


def invalidate_token(instance_id: int):
    _token_cache.pop(instance_id, None)
    with contextlib.suppress(Exception):
        db.delete_cached_token(instance_id)


class ApiClient:
    """인스턴스 하나에 대한 HTTP 세션 래퍼."""

    def __init__(self, inst: Instance, timeout: int = 30):
        self.inst = inst
        self.timeout = timeout
        self._session = requests.Session()
        self._session.verify = False

    def _headers(self) -> dict:
        return {'Authorization': f'Bearer {_get_token(self.inst)}',
                'Content-Type': 'application/json'}

    def _request(self, method: str, path: str, headers: dict = None, **kwargs) -> requests.Response:
        """401(토큰 만료/무효화)이면 토큰 캐시를 버리고 새로 받아 딱 1회만 재시도한다."""
        url = f'{self.inst.management_url}{path}'
        headers = dict(headers) if headers is not None else self._headers()
        print(f'[DEBUG][http] {method} {url}')
        try:
            r = self._session.request(method, url, headers=headers, timeout=self.timeout, **kwargs)
        except Exception as e:
            print(f'[DEBUG][http] request failed before response: {type(e).__name__}: {e}')
            raise
        print(f'[DEBUG][http] status={r.status_code} len={len(r.content)}')
        if r.status_code == 401 and self.inst.id is not None:
            print('[DEBUG][http] 401 -> invalidating cached token and retrying once')
            invalidate_token(self.inst.id)
            headers['Authorization'] = f'Bearer {_get_token(self.inst)}'
            r = self._session.request(method, url, headers=headers, timeout=self.timeout, **kwargs)
            print(f'[DEBUG][http] retry status={r.status_code} len={len(r.content)}')
        if not r.ok:
            print(f'[DEBUG][http] error body={r.text[:500]!r}')
        r.raise_for_status()
        return r

    def get(self, path: str, **kwargs) -> requests.Response:
        return self._request('GET', path, **kwargs)

    def post(self, path: str, json=None, data=None, files=None, **kwargs) -> requests.Response:
        headers = self._headers()
        if files:
            headers.pop('Content-Type', None)  # multipart 자동 설정
        return self._request('POST', path, headers=headers, json=json, data=data, files=files, **kwargs)

    def delete(self, path: str, **kwargs) -> requests.Response:
        return self._request('DELETE', path, **kwargs)

    def patch(self, path: str, json=None, **kwargs) -> requests.Response:
        return self._request('PATCH', path, json=json, **kwargs)

    def post_text(self, path: str, text: str) -> requests.Response:
        """레지스트리 리소스 등 text/plain 바디가 필요한 POST (Content-Type을 json에서 교체)."""
        headers = self._headers()
        headers['Content-Type'] = 'text/plain;charset=utf-8'
        return self._request('POST', path, headers=headers, data=text.encode('utf-8'))

    def put_text(self, path: str, text: str) -> requests.Response:
        headers = self._headers()
        headers['Content-Type'] = 'text/plain;charset=utf-8'
        return self._request('PUT', path, headers=headers, data=text.encode('utf-8'))

    def close(self):
        self._session.close()
