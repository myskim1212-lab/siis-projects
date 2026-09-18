# Backend Emulator

WSO2 API Gateway 테스트용 백엔드 에뮬레이터.  
클라이언트가 보낸 요청을 수신하고, 설정에 따라 응답 코드/헤더/바디/스트리밍/오류율을 제어할 수 있다.

---

## 요구사항

| 항목 | 버전 |
|------|------|
| Java | 1.8 이상 |
| Maven | 3.6 이상 |
| Spring Boot | 2.7.18 |

---

## 프로젝트 구조

```
backend-emulator/
├── pom.xml
└── src/main/
    ├── java/ipaas/backend/emulator/
    │   ├── BackendEmulatorApplication.java      # 메인 클래스
    │   ├── StartupLogger.java                   # 시작 시 URL/샘플 출력
    │   ├── controller/
    │   │   ├── EmulatorController.java          # /backend/emulator 엔드포인트
    │   │   └── HistoryController.java           # /history 엔드포인트
    │   ├── service/
    │   │   ├── EmulatorService.java             # 핵심 로직
    │   │   └── RequestHistoryService.java       # 요청 히스토리 관리
    │   └── model/
    │       ├── EmulatorRequest.java             # 제어 요청 모델
    │       └── RequestLog.java                  # 히스토리 모델
    └── resources/
        └── application.properties               # 서버 설정 (포트: 8888)
```

---

## 빌드

### 컴파일만 (JAR 미생성)

```bash
mvn compile
```

### JAR 빌드

```bash
mvn clean package -DskipTests
```

빌드 완료 후 생성 위치:

```
target/backend-emulator-1.0.0.jar
```

---

## 실행

### 방법 1 - Maven으로 직접 실행 (개발용)

```bash
mvn spring-boot:run
```

### 방법 2 - JAR 실행 (Windows)

```bash
java -jar target/backend-emulator-1.0.0.jar
```

### 방법 3 - JAR 실행 (Linux 백그라운드)

```bash
nohup java -jar backend-emulator-1.0.0.jar \
  > emulator.log 2>&1 &

echo $! > emulator.pid
```

**로그 확인:**
```bash
tail -f emulator.log
```

**종료:**
```bash
kill $(cat emulator.pid)
```

### 포트 변경 방법

**방법 1 - 실행 옵션:**
```bash
java -jar backend-emulator-1.0.0.jar --server.port=9999
```

**방법 2 - application.properties 수정:**
```properties
server.port=9999
```

**방법 3 - 환경변수 (Linux):**
```bash
export SERVER_PORT=9999
java -jar backend-emulator-1.0.0.jar
```

---

## 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| `*` | `http://localhost:8888/backend/emulator` | 에뮬레이터 메인 |
| `GET` | `http://localhost:8888/history` | 요청 히스토리 전체 조회 |
| `GET` | `http://localhost:8888/history?limit=10` | 최근 N건 조회 |
| `DELETE` | `http://localhost:8888/history` | 히스토리 초기화 |

---

## 제어 JSON 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `response_code` | Integer | N | HTTP 응답 코드 (기본값: 200) |
| `response_header` | String | N | 응답 헤더 (`KEY=VALUE,KEY=VALUE` 형식) |
| `response_content_type` | String | N | 응답 Content-Type |
| `response_body` | String | Y | 응답 바디 |
| `stream` | Boolean | N | 스트리밍 여부 (기본값: false) |
| `chunk_size` | Integer | N | 스트리밍 시 1회 전송 바이트 수 |
| `delay_time` | Integer | N | 응답 지연 시간 ms (stream=false 시에도 적용) |
| `error_rate` | Integer | N | 오류 발생 확률 0~100 (%) |

---

## 사용 방법

### 모드 1 - Echo 모드

제어 JSON 구조가 아닌 임의의 요청을 보내면 수신한 헤더와 바디를 그대로 응답한다.

```bash
curl -X POST http://localhost:8888/backend/emulator \
  -H "Content-Type: application/json" \
  -H "X-Custom-Header: hello" \
  -d '{"name":"test","value":123}'
```

---

### 모드 2 - 제어 모드 (즉시 응답)

응답 코드, 헤더, Content-Type, 바디를 직접 지정한다.

```bash
curl -X POST http://localhost:8888/backend/emulator \
  -H "Content-Type: application/json" \
  -d '{
    "response_code"        : 201,
    "response_header"      : "RECORD_COUNT=100,RECORD_SIZE=200",
    "response_content_type": "application/json",
    "response_body"        : "{\"result\":\"ok\"}",
    "delay_time"           : 1000
  }'
```

**응답 헤더:**
```
HTTP/1.1 201
Content-Type: application/json
RECORD_COUNT: 100
RECORD_SIZE: 200
```

**응답 바디:**
```json
{"result":"ok"}
```

---

### 모드 3 - 스트리밍 모드

`stream: true` 로 설정하면 `response_body` 를 `chunk_size` 바이트 단위로  
`delay_time` ms 간격으로 나누어 스트리밍 응답한다.

```bash
curl -X POST http://localhost:8888/backend/emulator --no-buffer \
  -H "Content-Type: application/json" \
  -d '{
    "response_header": "RECORD_COUNT=100,RECORD_SIZE=200",
    "response_body"  : "aaaabbbccc",
    "stream"         : true,
    "chunk_size"     : 3,
    "delay_time"     : 1000
  }'
```

**동작:**
```
청크 1 → "aaa"  (즉시)
청크 2 → "bbb"  (1000ms 후)
청크 3 → "ccc"  (1000ms 후)
청크 4 → "c"    (1000ms 후)
```

> Postman은 응답이 완료된 후 한번에 표시됨. curl --no-buffer 사용 권장.

---

### 모드 4 - 오류 시뮬레이션

`error_rate` 설정 시 해당 확률(%)로 HTTP 500을 응답한다.

```bash
curl -X POST http://localhost:8888/backend/emulator \
  -H "Content-Type: application/json" \
  -d '{
    "response_body": "ok",
    "error_rate"   : 30
  }'
```

- 30% 확률로 → HTTP 500 + `{"error":"Simulated error (error_rate=30%)"}`
- 70% 확률로 → HTTP 200 + `ok`

---

### 히스토리 API

최근 수신한 요청/응답 내역을 조회한다. 최대 100건 인메모리 보관.

```bash
# 전체 조회
curl http://localhost:8888/history

# 최근 10건 조회
curl http://localhost:8888/history?limit=10

# 초기화
curl -X DELETE http://localhost:8888/history
```

**응답 예시:**
```json
[
  {
    "no": 1,
    "timestamp": "2026-05-20 10:30:00.123",
    "method": "POST",
    "uri": "/backend/emulator",
    "requestHeaders": {"content-type": "application/json"},
    "requestBody": "{\"response_body\":\"hello\"}",
    "responseCode": 200,
    "mode": "CONTROLLED",
    "responseBody": "hello"
  }
]
```

---

## 실행 로그 예시

### 제어 모드

```
======================================================================
 [REQUEST #1]
======================================================================
  Time            : 2026-05-20 10:30:00.123
  Method          : POST
  URI             : /backend/emulator
  -- Headers --
  content-type                  : application/json
  -- Body --
  {"response_code":201,"response_body":"hello","delay_time":1000}
======================================================================

----------------------------------------------------------------------
 [RESPONSE #1]
----------------------------------------------------------------------
  Status          : 201
  Mode            : CONTROLLED (delay_time=1000ms)
  -- Headers --
  -- Body --
  hello
----------------------------------------------------------------------
```

### 스트리밍 모드

```
======================================================================
 [REQUEST #2]
======================================================================
  ...
  -- Body --
  {"response_body":"aaaabbbccc","stream":true,"chunk_size":3,"delay_time":1000}
======================================================================

----------------------------------------------------------------------
 [RESPONSE #2]
----------------------------------------------------------------------
  Status          : 200
  Mode            : STREAM (chunk_size=3, delay_time=1000ms)
  -- Body --
  aaaabbbccc
----------------------------------------------------------------------
[STREAM #2] chunk sent (0 ~ 2 bytes)
[STREAM #2] chunk sent (3 ~ 5 bytes)
[STREAM #2] chunk sent (6 ~ 8 bytes)
[STREAM #2] chunk sent (9 ~ 9 bytes)
```

---

## Linux 배포

```bash
# 1. Windows에서 JAR 빌드
mvn clean package -DskipTests

# 2. Linux 서버로 전송
scp target/backend-emulator-1.0.0.jar user@server_ip:/app/backend-emulator/

# 3. Linux에서 실행
nohup java -jar /app/backend-emulator/backend-emulator-1.0.0.jar \
  > /app/backend-emulator/emulator.log 2>&1 &

echo $! > /app/backend-emulator/emulator.pid
```

**Linux에 JDK가 없는 경우:**
```bash
# Amazon Linux / CentOS / RHEL
yum install -y java-1.8.0-openjdk

# Ubuntu / Debian
apt-get install -y openjdk-8-jdk
```

---

## 주요 변경 이력

| 버전 | 내용 |
|------|------|
| 1.0.0 | 최초 릴리즈 (Java 1.8, Spring Boot 2.7.18) |
| 1.1.0 | response_code, delay_time(non-stream), error_rate, response_content_type, 히스토리 API 추가 |
