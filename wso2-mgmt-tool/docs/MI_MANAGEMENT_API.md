# WSO2 MI Management API 정리

이 문서는 `wso2-mgmt-tool`이 실제로 사용하는 WSO2 Micro Integrator(MI) Management API를
정리한 것이다. 공식 문서만으로는 로그인/재시작 관련 세부 규약이 명확하지 않아, 로컬
MI 4.5.0 인스턴스(LOCALMI)를 라이브로 호출해보고 `org.wso2.micro.integrator.management.apis_4.5.0.jar`
(`<MI_HOME>/wso2/components/plugins/`)를 디컴파일(javap)해서 검증했다.

**표기 기준**: 아래 경로는 모두 `management_url` 기준 상대 경로다.
`management_url = https://{host}:{port}/management` ([db/models.py](../db/models.py) `Instance.management_url`).

**⚠️ Management API가 아닌 것도 섞여 있음**: §5-2의 dbinfo API는 Management API(9164, 인증
필요)가 아니라 **데이터플레인 서비스 포트**(`service_port`, 기본 8290, plain HTTP, 인증 불필요)에
배포된 별도 Synapse API다. WSO2 MI 자체 기능이 아니라 SIIS JDBC 커넥터 프로젝트가 자체
배포한 API이므로, 이 문서에서도 별도 표기했다.

## 1. 인증

MI Management API는 **OAuth2 client_credentials 방식이 아니다** — APIM과 다르다. 흔히
그렇게 짐작하고 구현하면 항상 404가 난다 (실제로 이 프로젝트도 처음엔 그렇게 잘못 구현돼 있었음).

```
GET /management/login
Authorization: Basic base64(admin_user:admin_pass)
```

응답:
```json
{"AccessToken": "eyJraWQiOi...(JWT)"}
```

- 메서드는 **GET**, 바디 없음. `access_token`(소문자)이 아니라 **`AccessToken`**(파스칼케이스) 키.
- 응답 토큰은 JWT라 `exp` 클레임을 그대로 읽어서 만료 시각을 알 수 있음 (서명 검증 불필요, payload만 디코딩).
- 이후 모든 요청은 `Authorization: Bearer <AccessToken>` 헤더로 호출.
- 구현: [api/base_client.py](../api/base_client.py) `_get_token()` — `is_mi_type(inst.type)`으로 MI/APIM 분기.

APIM 계열 인스턴스는 표준 OAuth2를 그대로 씀 (`POST /oauth2/token`, `grant_type=client_credentials`,
응답 키 `access_token` 소문자) — 같은 `_get_token()` 함수 안에서 else 분기로 처리.

## 2. 서버 정보 조회 / 재시작·정지 제어

**같은 리소스, 메서드로 동작이 갈린다.** 핸들러 클래스: `MetaDataResource`
(`GET`, `PATCH`만 허용 — 클래스 생성자에서 확인됨).

```
GET /management/server          → 서버 메타데이터 조회
PATCH /management/server        → 서버 제어 (재시작/정지)
```

`GET` 응답 예시:
```json
{
  "productVersion": "4.5.0", "productName": "WSO2 Micro Integrator",
  "osName": "Windows 11", "osVersion": "10.0",
  "javaVersion": "21.0.9", "javaVendor": "Eclipse Adoptium",
  "javaHome": "...", "carbonHome": "..."
}
```

`PATCH` 바디 — `status` 필드 하나로 4가지 동작을 지정 (문자열 상수는 디컴파일로 확인):

| status              | 동작                        |
|---------------------|----------------------------|
| `restart`           | 즉시 재시작                  |
| `restartGracefully` | Graceful 재시작              |
| `shutdown`          | 즉시 정지                    |
| `shutdownGracefully`| Graceful 정지                |

```json
{"status": "restartGracefully"}
```

**독립된 `/server/restart`, `/server/shutdown` 경로는 존재하지 않는다** (이 프로젝트가
처음에 그렇게 잘못 구현해서 API 경로로는 재시작/정지가 항상 404로 실패했었음 — 지금은
[api/mi_client.py](../api/mi_client.py) `restart()`/`shutdown()`에서 위 규약대로 수정됨).

내부적으로 `status` 값은 `CoreServerInitializerHolder`의 `restart()` / `restartGracefully()` /
`shutdown()` / `shutdownGracefully()`를 호출한다.

### ⚠️ 재시작의 실행 환경 제약 (중요)

WSO2 서버의 "재시작"은 JVM이 특정 exit code로 종료되면 **`wso2server.sh`/`wso2server.bat`
래퍼 스크립트**가 그 exit code를 감시하다가 JVM을 다시 띄워주는 방식으로 동작한다.
Management API가 하는 일은 딱 "JVM을 그 exit code로 종료시키는 것"까지다.

- 정식 배포판(`wso2server.sh`/`.bat`로 기동)에서는 정상적으로 재시작됨.
- **WSO2 Integration Studio에 내장된 MI**처럼 래퍼 스크립트 없이 JVM이 직접 떠 있는
  환경에서는, 재시작 신호를 보내면 그냥 프로세스가 죽고 아무도 다시 띄워주지 않는다 —
  "정지만 되고 재시작은 안 되는" 것처럼 보이는데, 이건 버그가 아니라 이 실행 환경의
  구조적 한계다.

## 3. Carbon Application (CAR)

```
GET    /management/applications                         → 목록
POST   /management/applications                         → 배포 (multipart, field name: file)
GET    /management/applications?carbonAppName={file}    → 원본 바이너리 다운로드
                                                            (Accept: application/octet-stream 필수)
DELETE /management/applications/{name}                  → 삭제 (경로 파라미터!)
```

**목록 응답은 `{"list":[...]}`가 아니다** — 실제로는
`{"activeCount":N,"activeList":[...],"faultyCount":N,"faultyList":[...]}` (라이브 검증. 예전
코드가 `list` 키를 읽어서 항상 빈 배열을 돌려주는 버그가 있었음). 각 항목은 `{"name":...,
"version":...,"artifacts":[...]}` — **원본 파일명은 안 준다.** Maven/CAR 빌드 관례상
`{name}_{version}.car`로 유도해서 쓴다 (실제 배포된 앱으로 라이브 검증 완료).

**삭제는 쿼리스트링이 아니라 경로 파라미터다** — `DELETE /applications?appFileName=...`는
항상 400 `Missing required name parameter in the path`가 난다 (이 프로젝트가 오랫동안 이렇게
잘못 구현돼 있어서 삭제 기능이 실제로는 동작한 적이 없었음). 진짜 계약은
`DELETE /applications/{name}`이고, `{name}`은 **앱 등록명이 아니라 원본 파일명에서 `.car`
확장자만 뗀 문자열**이어야 정확히 매칭된다 (`CarbonAppResource`가 내부적으로
`filename.equals(name + ".car")`로 정확히 일치하는 파일만 지운다 — 라이브 검증: 등록명
`SDIDataSource_Dev`로 삭제 시도하면 404 `does not exist`, 파일명 stem
`SDIDataSource_Dev_1.0.0-SNAPSHOT`로 삭제 시도하면 200 성공).

**다운로드는 반대로 쿼리스트링**이고, `carbonAppName`은 **확장자를 포함한 실제 파일명**이어야
한다 (`GET /applications?carbonAppName={name}_{version}.car` + `Accept: application/octet-stream`
→ zip 바이너리 그대로 응답, 라이브 검증). 삭제 전 원본 백업에 사용.

구현: [api/mi_client.py](../api/mi_client.py) `list_apps()`/`undeploy_app()`/`download_app()`,
[services/deploy_service.py](../services/deploy_service.py) `undeploy_from_instances()`
(삭제 전 자동 백업 포함). ✅ 목록/다운로드/삭제 전부 라이브 검증 완료 (더미 CAR로 생성→조회→
삭제→백업 파일 무결성까지 확인 후 정리).

## 4. Sequence (Management API로는 배포 불가 — 실제 배포/수정 수단 없음)

```
GET  /management/sequences            → 목록
GET  /management/sequences/{name}     → 단건 조회
POST /management/sequences            → ⚠️ 배포 아님, 트레이싱 토글 전용 (아래 참고)
```

**`SequenceResource.class`를 직접 디컴파일해서 확인함**
(`org.wso2.micro.integrator.management.apis_4.5.0.jar` → `SequenceResource`,
로컬 `wso2mi-4.5.0` 설치본 기준). `getMethods()`는 `{"GET", "POST"}`만 반환하고
`PUT`/`DELETE`는 아예 없다. **`POST`가 존재해서 배포용처럼 보이지만 실제로는
함정이다** — `handlePost()`의 실제 동작:

```java
// 요청 바디: {"name": "기존 시퀀스명"}
SequenceMediator sequence = configuration.getDefinedSequences().get(seqName);
if (sequence != null) {
    // 시퀀스 내용을 바꾸는 게 아니라 "sequence_trace" 트레이싱을 토글할 뿐
    response = Utils.handleTracing(performedBy, "sequence_trace", ...);
} else {
    response = createJsonError("Specified sequence ('...') not found", ..., "400");
}
```

즉 **이미 배포되어 메모리에 로드된 시퀀스만 대상으로 하고, 그 시퀀스의 내용을
생성/수정하는 게 아니라 트레이싱 on/off만 토글한다.** 없는 이름을 보내면 그냥
400 에러만 난다 — 이 엔드포인트로는 새 시퀀스를 만들거나 기존 시퀀스 내용을
바꿀 방법이 전혀 없다.

그래서 CAR에 담기지 않은 개별 시퀀스는 원래부터 MI의 hot-deploy 디렉터리
(`repository/deployment/server/synapse-configs/default/sequences`)에 XML 파일을
직접 떨어뜨리는 방식으로 배포하며, 이 도구도 JAR과 동일하게 **SSH/SFTP로 해당
디렉터리에 직접 업로드**하는 방식으로 시퀀스 배포를 지원한다 (Management API를
통한 배포가 아님 — JAR과 같은 우회 방식). 인스턴스별 대상 경로는 `sequence_path`
설정값(기본값 위 경로).

구현: [services/deploy_service.py](../services/deploy_service.py)
`deploy_to_instance()`(SEQUENCE 분기)/`list_sequence_files()`/`undeploy_from_instances()`.
✅ 디컴파일로 "Management API에 배포 수단이 없다"는 사실 자체는 확정. SFTP 배포
경로(`sequence_path` 기본값의 실제 유효성)는 라이브 검증 안 함.

## 5. Connector

```
GET  /management/connectors          → 목록
POST /management/connectors          → 배포 (multipart, .zip)
```

## 5-1. 데이터소스 (DataSourceResource, GET만 허용)

```
GET /management/data-sources                    → 전체 목록: {"count":N,"list":[{"name":...,"type":...}]}
GET /management/data-sources?name={name}        → 단건 상세
GET /management/data-sources?searchKey={key}    → 이름 부분 검색 (소문자 비교)
```

단건 상세 응답 예시 (비밀번호는 서버에서 마스킹돼서 내려옴):
```json
{
  "name": "SST0002_MSSQL", "description": "SST0002_MSSQL", "type": "RDBMS",
  "driverClass": "com.microsoft.sqlserver.jdbc.SQLServerDriver",
  "url": "jdbc:sqlserver://localhost:1433;databaseName=master;...",
  "userName": "siisadmin",
  "configuration": "<configuration>...<password>*****</password>...</configuration>",
  "configurationParameters": {"maxActive": 20, "maxIdle": 10, "validationQuery": "SELECT 1", ...}
}
```

구현: [api/mi_client.py](../api/mi_client.py) `list_datasources()`/`get_datasource()`/`search_datasources()`.
✅ 라이브 검증 완료 (LOCALMI의 실제 데이터소스 3개로 목록/상세 조회 모두 확인).

**⚠️ 이 응답엔 JNDI 이름이 없다** — `configuration` XML 블록까지 포함해 어디에도 JNDI 관련
필드가 없다 (라이브로 전체 raw 응답 확인함). 그래서 `_jndi_name()`은 등록명에서 `_` 앞부분을
떼어 `jdbc/` 접두어를 붙이는 **명명 규칙 추정**일 뿐, Management API가 실제로 알려주는 값이
아니다.

**실제 JNDI 이름을 정확히 알아내는 방법**: 데이터소스는 CAR로 배포되는데, 그 CAR 안에
`{name}_{version}/artifact.xml`(→ `<file>` 태그로 실제 정의 파일명을 알려줌)과
`{name}-{version}.xml`(`<datasource><jndiConfig><name>jdbc/SST0002</name></jndiConfig>...`)이
들어있다. `MIClient.download_app()`으로 CAR을 받아 이 XML을 파싱하면 정확한 JNDI 이름을 얻을
수 있다 — 라이브로 3개 데이터소스 전부 확인, `_jndi_name()`의 추정값과 정확히 일치했다.
**주의**: 이 정의 XML엔 DB 비밀번호가 평문으로 들어있으므로 `jndiConfig/name` 값 외에는 절대
UI/로그에 노출하면 안 된다. 구현: `MIClient.get_datasource_jndi_name()` — jndi 이름 한 값만
추출해서 반환, XML 원문은 어디에도 노출하지 않음. UI: 데이터소스 상세보기 모달의
"실제 JNDI 이름" 항목.

**캐싱**: CAR 다운로드는 상대적으로 비싼 작업이라, (인스턴스, 데이터소스명) → JNDI 이름 매핑을
`datasource_jndi_cache` 테이블(`db/database.py`의 `get_cached_jndi()`/`set_cached_jndi()`)에
저장해둔다. `Api._resolve_jndi_name()`이 캐시 → CAR 조회(성공 시 캐시 저장) → `_jndi_name()`
명명규칙 추정 순으로 값을 결정하며, 상세보기 조회(`get_datasource_jndi_name`)와 일괄 접속
테스트(`test_datasources`) 둘 다 이 경로를 탄다. 라이브 검증: 캐시 미스 시 CAR 다운로드 1회
발생(약 150ms) 후 캐시에 저장되고, 같은 데이터소스를 다시 조회하면 CAR 다운로드 없이 캐시값만
사용(약 1ms)함을 `download_app()` 호출 횟수 계측으로 확인.

## 5-2. 데이터소스 접속 테스트 (dbinfo API — Management API 아님, SIIS 커넥터 자체 API)

```
POST {service_base_url}/mi/jdbc/v1/dbinfo     (service_base_url = http://{host}:{service_port}, 기본 8290)
Content-Type: application/json

{"action": "CONNECTION_TEST", "jndi_name": "jdbc/SST0001"}
```

- 인증 불필요 (Management API 토큰과 무관), 응답에 실제 DB 접속 결과 + 메타데이터를 담아 돌려준다:
  ```json
  {"success": true, "action": "CONNECTION_TEST", "jndi_name": "jdbc/SST0001",
   "db_type": "ORACLE", "product": "Oracle", "version": "...", "url": "...", "user": "...",
   "elapsed_ms": 401}
  ```
- **jndi_name 유도 규칙**: 데이터소스 등록명(`GET /management/data-sources`의 `name`)에서
  `_` 앞부분을 떼어 `jdbc/` 접두어와 결합 — 예) `SST0001_ORACLE` → `jdbc/SST0001`. 3개
  데이터소스(ORACLE/MSSQL/POSTGRES) 전부 라이브로 이 규칙이 맞음을 확인했다
  (`api/mi_client.py` `MIClient._jndi_name()`). 이 규칙은 SIIS 프로젝트의 명명 관례이므로
  다른 환경에 그대로 적용된다는 보장은 없다 — 컨벤션이 다르면 재확인 필요.
- 구현: `api/mi_client.py` `test_datasource_connection()`/`test_datasource()`,
  `bridge/api.py` `test_datasources()`. UI의 "데이터소스" 탭에서 전체/선택 접속 테스트 버튼으로 노출.
- ✅ 라이브 검증 완료 (LOCALMI 데이터소스 3개 전부 성공 케이스, 존재하지 않는 이름으로
  500 에러 케이스도 확인 — 둘 다 UI에 깔끔하게 반영됨).

### CONNECTION_TEST_DIRECT — 등록 전 접속 테스트

같은 dbinfo API의 다른 액션. JNDI로 등록된 기존 데이터소스가 아니라 **URL/계정 정보만으로
직접** 접속을 확인한다 — 데이터소스를 실제로 등록하기 전에 미리 접속 가능 여부를 확인하는 용도.

```json
{"action": "CONNECTION_TEST_DIRECT", "url": "jdbc:oracle:thin:@//localhost:1521/XE",
 "user": "C##siis", "password": "siis1234"}
```

- 사용자(user)로부터 스펙을 직접 전달받아 구현 — LOCALMI가 현재 꺼져있어 라이브 검증은
  못했고, 기존 `CONNECTION_TEST`와 동일한 응답 패턴(성공 시 db_type/product/version/elapsed_ms,
  실패 시 error)일 것으로 가정하고 구현함. **다음에 LOCALMI가 켜져 있을 때 한 번 확인 필요.**
- 구현: `api/mi_client.py` `test_datasource_direct()`, `bridge/api.py`
  `test_datasource_direct_multi()` (여러 인스턴스 동시 테스트, 기존 `test_datasource_multi()`와
  동일한 병렬 패턴). UI: "데이터소스" 탭의 "여러 인스턴스에서 접속 테스트" 박스 — JNDI/URL
  직접 입력 모드 라디오 버튼으로 전환, 인스턴스 체크리스트와 결과 테이블은 공유.

## 5-3. 레지스트리 리소스 (RegistryResource류)

```
GET    /management/registry-resources?path={경로}                         → 컬렉션(디렉터리) 자식 목록
GET    /management/registry-resources/content?path={경로}&mediaType=text   → 조회
POST   /management/registry-resources/content?path={경로}&mediaType=text   → 신규 생성
PUT    /management/registry-resources/content?path={경로}&mediaType=text   → 기존 수정
DELETE /management/registry-resources/content?path={경로}&mediaType=text   → 삭제
```

**목록 조회(`RegistryResource`, `/registry-resources`)는 `/content`가 안 붙는 별도 리소스**다
(`ManagementInternalApi`에 별개로 등록됨 — 디컴파일로 확인). 응답:
```json
{"count":2,"list":[{"name":"jdbc","mediaType":"directory","properties":[]},
                    {"name":"repository","mediaType":"directory","properties":[]}]}
```
`mediaType`이 `"directory"`면 하위 탐색 가능한 컬렉션, 아니면(`"text"` 등) 리프 리소스 —
UI "레지스트리" 탭의 트리 브라우징이 이걸로 동작한다 (루트는 `registry`부터 시작, 라이브로
`registry → config → jdbc → DeleteInsert.yaml`까지 실제 탐색 확인).

**`searchKey` 쿼리 파라미터은 "이름 포함 검색"이 아니다** (`javap`로 `RegistryResource.invoke()`
바이트코드 재확인 — 이전 버전 문서의 추정이 틀렸음): `searchKey`가 비어있지 않으면 그 값을
그대로 `populateRegistryResourceJSON(searchKey, ...)`에 리소스 **이름**으로 넘겨서 `path` 아래
그 정확한 이름의 리소스 하나를 직접 조회한다 — `expand=true`일 때와 동일한 코드 경로이고,
부분 일치/포함 검색 로직은 어디에도 없다. 즉 서버에 진짜 "포함 검색" API가 없으므로, 이 프로젝트의
이름 포함 검색 기능(`MIClient.search_registry_resources()`)은 `/registry-resources`로 디렉터리를
하나씩 내려받아 클라이언트에서 직접 이름을 필터링하는 방식으로 구현했다 (레지스트리가 크면 느릴 수
있어 방문 디렉터리 수 상한을 둠).

예시 경로: `registry/config/jdbc/DeleteInsert.yaml`

- **GET 응답은 JSON이 아니라 리소스 원문 텍스트 그대로** (Content-Type: `application/txt;
  charset=UTF-8`). 없는 경로면 200이 아니라 **400** `{"Error":"Can not find the registry:
  {경로}"}` (라이브 검증).
- **PUT과 POST가 존재 여부로 엄격히 구분된다** (라이브 검증):
  - 이미 있는 경로에 **POST**하면 400 `{"Error":"Registry already exists. Can not POST an
    existing registry"}`.
  - 없는 경로에 **PUT**하면 400 `{"Error":"Registry does not exists in the path: {경로}"}`.
  - 즉 "생성"과 "수정"을 호출자가 정확히 구분해서 메서드를 골라야 한다. 이 프로젝트는
    존재 여부를 몰라도 되도록 **PUT을 먼저 시도하고 "does not exist" 오류일 때만 POST로
    재시도**하는 방식으로 흡수했다 (`MIClient.save_registry_resource()`).
  - 성공 응답은 둘 다 JSON: `{"message":"Successfully added/modified the registry resource"}`.
- **쓰기 요청(POST/PUT)은 `Content-Type: text/plain;charset=utf-8` 필수** (기본 Bearer+JSON
  헤더를 그대로 쓰면 실패). GET/DELETE는 요청 바디가 없으므로 기본 헤더로 충분.
- DELETE 성공 응답: `{"message":"Successfully deleted the registry resource"}`.

구현: [api/mi_client.py](../api/mi_client.py) `get_registry_resource()`/
`save_registry_resource()`/`delete_registry_resource()`, [api/base_client.py](../api/base_client.py)
`ApiClient.post_text()`/`put_text()` (Content-Type 교체 전용 헬퍼). UI "레지스트리" 탭.
✅ 생성→조회→수정→조회→삭제→조회(404 확인) 전체 사이클 라이브 검증 완료.

## 6. 로그

```
GET /management/logs               → 로그 파일 목록 ({"count":N,"list":[{"Size":"...","FileName":"..."}]})
GET /management/logs?file={파일명}  → 로그 파일 전체 내용 (텍스트 그대로)
GET /management/logging            → 로거별 현재 레벨 목록
PATCH /management/logging          → 로그 레벨 변경, 바디 {"loggerName":"...","loggingLevel":"..."}
```

**해결됨**: 개별 로그 파일 "내용"은 `GET /management/logs/{filename}`(경로 파라미터)이 아니라
**`GET /management/logs?file={filename}`(쿼리 파라미터)**였다 (`LogFilesResource` 디컴파일로
확인 — `Utils.getQueryParameter(mc, "file")`). 응답은 JSON이 아니라 **해당 로그 파일 전체
내용을 텍스트로 그대로** 준다 (`Content-Type: application/txt`) — 최근 N줄만 보려면 클라이언트
에서 `splitlines()[-N:]`으로 잘라야 한다. 라이브 검증: LOCALMI의 `wso2carbon.log` 실제 내용
정상 조회 확인. 이전에는 잘못된 경로(`/logs/{filename}`)를 써서 항상 404였고, 그 결과 **SSH
미설정 인스턴스의 "API 폴링" 로그 뷰어 모드가 전혀 동작하지 않았다** (SSH `tail -f` 모드는
원래부터 정상). [api/mi_client.py](../api/mi_client.py) `get_log_content()`를 수정해 해결.

## 7. 아직 안 쓰는 리소스 (jar에서 클래스명만 확인, 미검증)

`org.wso2.micro.integrator.management.apis` 패키지에 아래 리소스가 더 있다.
필요해지면 위와 같은 방식(라이브 테스트 + `javap -p -c`로 바이트코드 확인)으로 규약을 확인하고 붙이면 됨:

`ConfigsResource`, `DataServiceResource`,
`EndpointResource`, `ExternalVaultResource`, `InboundEndpointResource`, `LocalEntryResource`,
`LoginResource`/`LogoutResource`(§1의 로그인이 이쪽일 가능성), `MessageProcessorResource`,
`MessageStoreResource`, `ProxyServiceResource`, `RequestCountResource`,
`RoleResource`/`RolesResource`, `TaskResource`, `TemplateResource`, `UserResource`/`UsersResource`.

## 8. 확인 상태 요약

| 기능              | 경로                              | 상태 |
|-------------------|-----------------------------------|------|
| 로그인             | `GET /management/login`           | ✅ 라이브 검증 |
| 서버 정보           | `GET /management/server`          | ✅ 라이브 검증 |
| 재시작/정지         | `PATCH /management/server`        | ✅ 디컴파일 + 라이브 호출(정지까지) 검증. 재시작 후 재기동은 실행 환경 의존 (§2 참고) |
| CAR 배포/조회/삭제  | `/management/applications`        | ✅ 목록/다운로드/삭제 라이브 검증 (배포는 코드상 구현, 별도 라이브 확인 안 함) |
| Sequence 조회      | `/management/sequences`           | 🟡 코드상 구현됨, 라이브 검증 안 함 |
| Sequence 배포 불가 확인 | `/management/sequences` POST      | ✅ 디컴파일 확인 (트레이싱 토글 전용, 배포 수단 아님) |
| Sequence 배포/삭제  | SSH/SFTP (Management API 아님)     | 🟡 코드상 구현됨, 라이브 검증 안 함 |
| Connector 배포/조회 | `/management/connectors`          | 🟡 코드상 구현됨, 라이브 검증 안 함 |
| 데이터소스 목록/상세 | `GET /management/data-sources`    | ✅ 라이브 검증 (LOCALMI 실제 데이터소스 3개로 확인) |
| 레지스트리 CRUD     | `/management/registry-resources/content` | ✅ 생성/조회/수정/삭제 전체 사이클 라이브 검증 |
| 로그 파일 목록      | `GET /management/logs`            | ✅ 라이브 검증 |
| 로그 레벨 조회/변경  | `/management/logging`             | 🟡 코드상 구현됨, 라이브 검증 안 함 |
| 로그 파일 내용      | `GET /management/logs?file={filename}` | ✅ 라이브 검증 (기존 경로 파라미터 버전은 404였음, 수정 완료) |
