# WSO2 API Manager 4.3 — 기술 아키텍처 완전 가이드

> 작성일: 2026-04-15 / 최종 수정: 2026-04-17  
> 대상 버전: WSO2 API Manager 4.3 / Micro Integrator 4.3  
> 구성: CP 2대(10.0.0.11/12) + GW Worker 2대(10.0.0.21/22)

---

## 목차

1. [용어 해설](#1-용어-해설)
2. [API 추가·삭제 시 CP ↔ GW 이벤트 흐름](#2-api-추가삭제-시-cp--gw-이벤트-흐름)
3. [API 호출 시 Key Manager / Traffic Manager / GW 이벤트 흐름](#3-api-호출-시-key-manager--traffic-manager--gw-이벤트-흐름)
4. [GW Stateless 설계 원리 요약](#4-gw-stateless-설계-원리-요약)
5. [WSO2 APIM 4.3 클러스터 구성 상세](#5-wso2-apim-43-클러스터-구성-상세)
6. [공개키·개인키·JWT·서명 검증 심화](#6-공개키개인키jwt서명-검증-심화)
7. [내부 Repository DB 테이블 상세](#7-내부-repository-db-테이블-상세)
8. [분산 배포 구성 완전 가이드 (공식 문서 기반)](#8-분산-배포-구성-완전-가이드-공식-문서-기반)

---

## 1. 용어 해설

### 1-1. CP 고가용성(HA) 구성 원리 (WSO2 APIM 4.x 변경 사항)

> **⚠️ WSO2 APIM 4.x 중요 변경:**  
> WSO2 API Manager **3.x까지**는 CP 노드 간 상태 동기화에 **Hazelcast In-Memory Data Grid**를 사용했으나,  
> **4.0 이상부터 Hazelcast 클러스터링이 완전히 제거**되었다.  
> `[clustering]` 설정 블록은 더 이상 사용하지 않으며, Port 4000 방화벽 규칙도 불필요하다.

#### 3.x vs 4.x CP HA 방식 비교

| 항목 | 3.x (Hazelcast) | **4.x — 공유 DB 방식** |
|------|-----------------|----------------------|
| CP 간 상태 공유 | Hazelcast 인메모리 분산 캐시 | 공유 관계형 DB (apim_db, shared_db) |
| 필요 추가 포트 | TCP 4000 (Hazelcast WKA) | **없음** |
| 세션 공유 | Hazelcast 세션 복제 | DB 기반 세션 저장 |
| 스로틀링 카운터 | Hazelcast AtomicLong 분산 집계 | 각 TM 노드 독립 처리 (Siddhi CEP) — GW가 양 TM에 동시 전송하므로 각 TM이 독립 카운터 유지 (정책 한도가 노드 수만큼 배수 허용될 수 있음) |
| CP 노드 추가 | 클러스터 멤버 목록 수동 등록 필요 | DB 접속 정보만 동일하면 자동 참여 |
| CP 간 직접 통신 | 필수 (Hazelcast 클러스터링) | **데이터 동기화용 직접 통신 없음** — 단, 이벤트 복제용 AMQP/Thrift 연결은 존재 |

#### 4.x CP HA 동작 원리

```
CP-Server1 (10.0.0.11)                      CP-Server2 (10.0.0.12)
┌──────────────────────┐                     ┌──────────────────────┐
│  API Manager Core    │ ◀── AMQP 5672 ────▶ │  API Manager Core    │
│  Key Manager         │ (event_duplicate_url │  Key Manager         │
│  Traffic Manager     │  토큰취소 이벤트복제) │  Traffic Manager     │
│  Qpid Broker (5672)  │ ◀── TCP/SSL 9611 ──▶ │  Qpid Broker (5672)  │
│                      │  (event_hub publish) │                      │
└──────────┬───────────┘                     └──────────┬───────────┘
           │                                            │
           └─────────────────┬──────────────────────────┘
                             │ JDBC (공유 DB)
                  ┌──────────▼───────────┐
                  │   공유 데이터베이스   │
                  │   apim_db            │  ← API 메타데이터, 토큰, 구독, 정책
                  │   shared_db          │  ← 사용자, 세션, 레지스트리
                  └──────────────────────┘

핵심 원리:
  - 두 CP 노드는 운영 데이터 동기화를 위한 직접 통신을 하지 않는다
    (3.x Hazelcast 클러스터링 같은 분산 인메모리 캐시 없음)
  - 모든 상태(State)는 공유 DB에만 존재 → DB에서 동일한 데이터를 읽음
  - 단, 이벤트 복제를 위한 CP 간 직접 연결은 존재한다:
      ① [apim.event_hub] event_duplicate_url : CP1 Qpid → CP2 Qpid (AMQP 5672)
         공식 문서에서 명시한 용도: 토큰 취소(revocation) 이벤트 복제
         공식 확인: "token revocation events … will be duplicated to the other event hub using event_duplicate_url"
         → 토큰 취소 이벤트 전용으로 공식 문서에서 명확히 확인됨 (AMQP 브로커 전체 미러링 아님)
         ※ 스로틀링 결정 복제용 설정은 APIM 4.3에 없음 ([apim.throttling] event_duplicate_url은 존재하지 않는 파라미터)
      ② [[apim.event_hub.publish.url_group]] : CP1 → CP1·CP2 Event Hub (Thrift TCP 9611 / SSL 9711)
         공식 문서에서 명시한 용도: API 배포·구독·KM 이벤트를 CP 양 노드 Event Hub 에 전달
         (HA 구성 시 자신 포함 모든 CP 노드를 url_group 에 등록)
  - 노드 장애 시 LB가 정상 노드로만 라우팅 → 서비스 지속
```

#### CP 노드 HA 설정 (4.x)

4.x에서는 `[clustering]` 블록 없이 **DB 접속 정보만 동일하게** 설정하면 HA 완성이다.

```toml
# CP-Server1, CP-Server2 모두 동일한 DB를 바라보면 자동으로 HA 구성
# [clustering] 섹션은 작성하지 않는다 (4.x에서 미사용)

[database.apim_db]
url = "jdbc:postgresql://db.example.com:5432/apim_db"

[database.shared_db]
url = "jdbc:postgresql://db.example.com:5432/shared_db"
```

---

### 1-1-1. CP 간 이벤트 동기화 상세

> DB 공유로 **데이터 동기화**는 해결된다.  
> 그러나 CP 노드가 2대 이상일 때, **한 노드에서 발생한 이벤트를 다른 노드도 즉시 인지**해야 한다.  
> (예: CP1에서 API를 배포하면 CP2도 즉시 알고, GW에 전달해야 함)  
> 이를 위해 CP 간 **AMQP 이벤트 복제**가 사용된다.

#### 문제: 이벤트는 발생한 노드의 메모리에만 존재

```
사용자 → LB → CP-Server1 에서 API 배포
         │
         CP-Server1: DB에 API 저장 ✅
         CP-Server1: 자신의 Qpid(5672) 에 이벤트 발행 ✅
         CP-Server2: DB에서 읽으면 알 수 있지만 → 즉시 알 수 없음 ❌
                     CP-Server2의 Qpid 에는 이벤트가 없음 ❌
                     → GW가 CP-Server2 구독 중이면 배포 이벤트 누락
```

#### 해결: event_duplicate_url — 상대 CP 브로커로 이벤트 복제

`[apim.event_hub]`의 `event_duplicate_url` 설정으로, 자신이 발행한 이벤트를 **상대 CP 노드의 Qpid에도 동시에 전송**한다.

```
CP-Server1 (10.0.0.11)                    CP-Server2 (10.0.0.12)
┌──────────────────────────────┐           ┌──────────────────────────────┐
│  Event Hub                   │           │  Event Hub                   │
│  ┌─────────────────────┐     │           │  ┌─────────────────────┐     │
│  │ API 배포 이벤트 발생 │     │           │  │                     │     │
│  └─────────┬───────────┘     │           │  └─────────┬───────────┘     │
│            │                 │           │            │                 │
│  ┌─────────▼───────────┐     │  복제     │  ┌─────────▼───────────┐     │
│  │ Qpid (5672)         │─────┼──────────▶│  │ Qpid (5672)         │     │
│  │  └ localhost 구독   │     │event_dup  │  │  └ localhost 구독   │     │
│  └─────────────────────┘     │  licaate  │  └─────────────────────┘     │
└──────────────────────────────┘           └──────────────────────────────┘
         │                                           │
         ▼                                           ▼
  GW가 CP1 구독 → 이벤트 수신 ✅          GW가 CP2 구독 → 이벤트 수신 ✅
```

#### 이벤트 동기화 전체 흐름 (CP 2노드 기준)

```
① 사용자가 Publisher에서 API 배포 (CP-Server1 또는 CP2 중 하나로 라우팅)

② 받은 CP 노드 (예: CP-Server1):
   - apim_db에 API 메타데이터 저장 (DB 공유이므로 CP-Server2도 즉시 읽기 가능)
   - 자신의 Qpid (tcp://localhost:5672) 에 배포 이벤트 발행
   - event_duplicate_url(tcp://10.0.0.12:5672) 로 동일 이벤트를 CP-Server2에도 복제

③ CP-Server2:
   - 수신한 이벤트를 자신의 Qpid 구독자에게 전달

④ GW Worker (throttle_decision_endpoints = [cp1:5672, cp2:5672] 구독):
   - CP-Server1 또는 CP-Server2 어느 쪽에서든 이벤트 수신
   - service_url (REST API) 로 실제 아티팩트 Pull

결과: GW는 어느 CP로 이벤트가 왔어도 빠짐없이 수신하여 API 동기화 완료
```

#### event_hub.publish.url_group — CP 간 이벤트 동기화 경로

`[[apim.event_hub.publish.url_group]]`은 **CP HA 구성 시 CP 노드 간 이벤트 동기화**에 사용된다.  
CP1에서 발생한 API·구독·KM 이벤트를 Thrift(9611/9711)로 CP2에도 전달하여, 어느 CP로 요청이 와도 GW가 이벤트를 빠짐없이 수신하도록 한다.  
스로틀링 카운터 전송과는 무관하다.

> **혼동 주의 — 유사한 이름의 설정 구분:**
>
> | 설정 | 위치 (노드) | 방향 | 포트 | 전송 데이터 |
> |------|------------|------|------|------------|
> | `[apim.event_hub]` `event_duplicate_url` | **CP** | CP1 → CP2 Qpid | 5672 (AMQP) | 토큰 취소 이벤트 복제 (**공식 문서 확인:** *"The token revocation events that are received to an event hub will be duplicated to the other event hub using event_duplicate_url."*) |
> | `[[apim.event_hub.publish.url_group]]` | **CP** | CP1 → CP1·CP2 Event Hub | 9611/9711 (Thrift) | API 배포·구독·KM 이벤트 동기화 (공식 확인) |
> | `[[apim.throttling.url_group]]` | **GW** | GW → TM 양 노드 | 9611/9711 (Thrift) | GW 호출량(스로틀링 카운터) 전송 |
> | ~~`[apim.throttling]` `event_duplicate_url`~~ | ~~TM~~ | ~~TM1 → TM2~~ | ~~5672~~ | ❌ **APIM 4.3에 존재하지 않는 설정** — `event_duplicate_url`은 `[apim.event_hub]`에만 존재 |

```toml
# CP-Server1의 deployment.toml 기준

[apim.event_hub]
event_listening_endpoints = ["tcp://localhost:5672"]      # 자신의 Qpid 브로커 구독
event_duplicate_url       = ["tcp://10.0.0.12:5672"]      # CP2 로 토큰 취소 이벤트 복제 (AMQP)

# CP 간 API·구독·KM 이벤트 동기화 (Thrift) — CP 자신 포함, 상대 CP 포함
[[apim.event_hub.publish.url_group]]
urls      = ["tcp://10.0.0.11:9611"]
auth_urls = ["ssl://10.0.0.11:9711"]

[[apim.event_hub.publish.url_group]]
urls      = ["tcp://10.0.0.12:9611"]
auth_urls = ["ssl://10.0.0.12:9711"]
```

#### CP HA 동기화 요약

| 동기화 대상 | 방법 | 실시간? |
|------------|------|---------|
| API·토큰·구독 **데이터** | 공유 DB (apim_db) | ✅ 즉시 (DB 읽기) |
| 세션·레지스트리 **데이터** | 공유 DB (shared_db) | ✅ 즉시 |
| **토큰 취소 이벤트** 알림 | AMQP `event_duplicate_url` (5672) | ✅ 즉시 (비동기) |
| **API 배포·구독·KM 이벤트** 알림 | Thrift `[[apim.event_hub.publish.url_group]]` (9611/9711) | ✅ 즉시 (비동기) |
| 스로틀링 **카운터** | 각 TM 독립 Siddhi CEP | ⚠️ 노드별 독립 (GW가 양 TM에 동시 전송 → 각 TM이 독립 카운터 유지) |
| 로컬 인메모리 **캐시** (토큰 캐시 등) | 직접 동기화 없음 → TTL 만료 후 재조회 | ⚠️ TTL 기반 |

> **스로틀링 카운터 주의:** CP-Server1의 TM과 CP-Server2의 TM은 **각자 독립적으로 카운터를 관리**한다.  
> GW는 `[[apim.throttling.url_group]]`에 TM 양 노드를 모두 등록하여 **카운터를 동시에 전송**하지만,  
> 두 TM이 집계를 합산하지는 않는다. 따라서 정책 한도(예: 분당 100)는 **각 TM이 독립 판단**하며,  
> GW는 어느 한 TM에서 "초과" 결정이 오면 즉시 429를 반환한다.  
> ⚠️ `[apim.throttling]` 섹션에 `event_duplicate_url` 파라미터는 **APIM 4.3에 존재하지 않는다.**  
> TM 간 스로틀링 결정을 동기화하는 별도 설정은 없으며, 각 TM이 독립 동작하는 것이 APIM 4.3의 표준 동작이다.

---

### 1-2. AMQP (Advanced Message Queuing Protocol)

**AMQP**는 메시지 브로커와 클라이언트 간 통신을 위한 개방형 표준 프로토콜이다.  
WSO2 APIM에서는 내장 **Apache Qpid Broker**가 AMQP 5672 포트로 서비스된다.

#### AMQP vs 기타 프로토콜 비교

| 항목 | AMQP | HTTP REST | JMS |
|------|------|-----------|-----|
| 통신 방식 | 비동기 메시지 큐 | 동기 요청/응답 | 비동기 (Java 전용) |
| 연결 방식 | 지속 연결(Persistent) | 요청마다 연결 | 지속 연결 |
| 확장성 | ✅ 높음 | 보통 | 보통 |
| 언어 독립성 | ✅ 독립 | ✅ 독립 | ❌ Java 전용 |

#### WSO2 APIM 4.x에서 AMQP 사용처

WSO2 APIM 4.x에서 GW Worker의 AMQP 사용 방식이 3.x와 달라졌다.

| 기능 | 3.x | **4.x (표준 분산 배포 — CP+TM 공존)** |
|------|-----|---------|
| 스로틀링·API/App/KM 이벤트 수신 | GW가 `event_listening_endpoints` AMQP 구독 | **GW가 `throttle_decision_endpoints` JMS 구독** (단일 연결로 통합) |
| 아티팩트 실제 데이터 Pull | AMQP 이벤트 내 포함 | **이벤트 수신 후 별도 REST API Pull** (`[apim.throttling] service_url`) |
| GW `[apim.event_hub]` 설정 | 필요 | **불필요** (CP 전용 — TM 분리 배포 시에만 GW에도 필요) |

> **공식 문서 원문:** *"Rate limiting configurations are used to configure both traffic management as well as the event hub for the Gateway. The same JMS connection will be used to subscribe for events received from the event hub. Gateway will subscribe for API/Application/Subscription and Keymanager operations related events. service_url points to the internal API in the event hub that is used to pull artifacts and information from the db."*

> ⚠️ **TM 분리 배포(CP/TM 별도 노드) 시 차이:**  
> GW는 `[apim.throttling] throttle_decision_endpoints` → **TM** Qpid 연결(스로틀링 결정)과  
> `[apim.event_hub] event_listening_endpoints` → **CP** Qpid 연결(API/App/Sub/KM 이벤트)을  
> **별도의 두 JMS 연결**로 분리 구성해야 한다. 본 문서는 **표준 분산 배포(CP+TM 공존)** 기준이다.

```
CP (Qpid Broker, tcp:5672)
  │
  ├── 스로틀링 결정 + API/App/Subscription/KM 이벤트 발행
  │     └── GW Worker 구독 ← [apim.throttling] throttle_decision_endpoints (JMS 단일 연결)
  │           ① 이벤트 수신 (배포/삭제/KM변경 알림)
  │           ② service_url 로 REST API 호출 → 실제 아티팩트 Pull
  │
  └── CP 노드 간 토큰취소 이벤트 복제 (event_duplicate_url)
        └── CP-Server1 ↔ CP-Server2
```

#### GW 설정에서의 AMQP 연결 (4.x 기준)

```toml
# deployment.toml — GW Worker (4.x, 표준 분산 배포)
# [apim.event_hub] 섹션 없음 — 표준 분산 배포에서 GW Worker에는 불필요
# throttle_decision_endpoints 의 JMS 연결이
# ① 스로틀링 결정 수신 + ② API/App/Subscription/KM 이벤트 수신 을 모두 담당
[apim.throttling]
service_url = "https://cp.example.com/services/"   # 아티팩트 Pull용 REST API
throttle_decision_endpoints = ["tcp://10.0.0.11:5672", "tcp://10.0.0.12:5672"]

# 이 Gateway가 처리할 Gateway 레이블 지정 (CP 배포 시 사용한 레이블과 일치해야 함)
[apim.sync_runtime_artifacts.gateway]
gateway_labels = ["Default"]
```

> 4.x 표준 분산 배포에서 GW Worker는 `[apim.event_hub]`를 설정하지 않는다.  
> `throttle_decision_endpoints` 하나의 JMS 연결로 스로틀링 결정과 API/App/KM 이벤트를 모두 수신하며,  
> 이벤트 수신 후 `service_url`(REST API)로 실제 아티팩트를 Pull하는 2단계 구조다.

---

### 1-3. AMQP or Choreo (분석 이벤트 전송 방식)

WSO2 APIM 4.x부터 **API Analytics** 이벤트 전송 방식이 두 가지로 나뉜다.

| 방식 | 설명 | 사용 시나리오 |
|------|------|---------------|
| **AMQP 방식** | 로컬 Qpid를 통해 내부 Analytics 서버로 전송 | On-Premise 자체 Analytics 구성 |
| **Choreo Analytics** | WSO2 Cloud (choreo.dev)로 HTTPS 직접 전송 | 클라우드 기반 분석 사용 시 |

```toml
# On-Premise Analytics (AMQP)
[apim.analytics]
enable = true
type  = "elk"
```

```toml
# Choreo Cloud Analytics
[apim.analytics]
enable = true
type   = "choreo"

[[apim.analytics.url_group]]
analytics_url     = ["tcp://analytics.choreo.dev:7612"]
analytics_auth_url = ["ssl://analytics.choreo.dev:7712"]
```

> 본 문서의 구성(On-Premise 4대 서버)에서는 **AMQP 방식**이 기본이다.

---

### 1-4. 토픽 (Topic)

**토픽**은 AMQP/JMS 메시지 브로커에서 **발행-구독(Pub-Sub) 패턴**의 채널 이름이다.

```
발행자(Publisher) ──토픽 이름──▶ 브로커 ──▶ 구독자 1 (GW-Inst1)
                                        └──▶ 구독자 2 (GW-Inst2)
                                        └──▶ 구독자 3 (GW-Server2 Inst1)
```

#### WSO2 APIM 4.x — GW가 수신하는 이벤트 종류

GW Worker는 `[apim.throttling] throttle_decision_endpoints` 의 **단일 JMS 연결**로 아래 이벤트를 모두 수신한다.

| 이벤트 종류 | 발행자 | JMS 토픽 | 내용 |
|------------|--------|----------|------|
| 스로틀링 결정 | CP Traffic Manager | `org.wso2.apimgt.throttleout` | API/App/User Tier 한도 초과 결정 |
| API 배포/삭제 | CP Event Hub | `org.wso2.apimgt.deploymentEvent` | Gateway에 배포할 API 변경 알림 |
| Application 변경 | CP Event Hub | `org.wso2.apimgt.applicationEvent` | 앱 생성·수정·삭제 알림 |
| Subscription 변경 | CP Event Hub | `org.wso2.apimgt.subscriptionEvent` | 구독 생성·취소 알림 |
| Key Manager 변경 | CP Event Hub | `org.wso2.apimgt.keymanager` | 외부 KM 등록·변경 알림 |
| 토큰 취소 | CP Event Hub | `tokenRevocation` | 취소된 토큰 → GW `gateway_token_cache` 즉시 무효화 |

> **4.x 구조 (표준 분산 배포):** 이벤트는 `throttle_decision_endpoints` JMS 단일 연결로 수신(알림),  
> 실제 아티팩트 데이터는 `[apim.throttling] service_url` REST API로 Pull(2단계).  
> `[apim.event_hub]`는 **CP 노드에만** 필요하며, GW Worker 설정에는 추가하지 않는다.  
> ※ TM 분리 배포 시에는 GW에 `[apim.event_hub]`를 별도로 추가해야 한다.

---

### 1-5. JWT (JSON Web Token)

**JWT**는 JSON 기반의 개방형 표준(RFC 7519)으로, 당사자 간에 정보를 안전하게 전달하기 위한 토큰 형식이다.

#### JWT 구조

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9   ← Header (Base64URL)
.
eyJzdWIiOiJ1c2VyMSIsImFwaSI6Ik9yZGVyQVBJIn0  ← Payload (Base64URL)
.
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c  ← Signature
```

##### Header (알고리즘 정보)
```json
{
  "alg": "RS256",    // RSA + SHA-256 서명 알고리즘
  "typ": "JWT",
  "kid": "key-id-1" // 서명에 사용된 키 식별자
}
```

##### Payload (클레임 정보)
```json
{
  "sub":           "user@example.com",     // 토큰 소유자
  "iss":           "https://cp.example.com/oauth2/token",  // 발급자
  "aud":           ["OrderAPI"],           // 대상 API
  "exp":           1716000000,             // 만료 시간 (Unix timestamp)
  "iat":           1715996400,             // 발급 시간
  "jti":           "abc-123-uuid",         // 토큰 고유 ID (취소에 사용)
  "scope":         "read:orders write:orders",
  "application":   { "id": 5, "name": "MyApp", "tier": "Unlimited" },
  "subscribedAPIs": [{ "name": "OrderAPI", "version": "v1", "context": "/orders" }]
}
```

##### WSO2 APIM에서 JWT 처리 흐름
```
클라이언트 → GW (Bearer JWT)
   │
   GW: Header 파싱 → kid로 공개키 선택
   GW: 서명 검증 (로컬, CP 호출 없음)
   GW: exp(만료) 확인
   GW: jti로 토큰 취소 블랙리스트 확인
   GW: scope, subscribedAPIs 정책 확인
   │
   └─ 모두 통과 → 백엔드 호출
```

> JWT는 **자기 완결형(Self-Contained)** 토큰이므로  
> GW가 CP 없이 로컬에서 검증 완료 → **응답 지연 최소화**

---

### 1-6. 서명 검증 (Signature Verification)

**서명 검증**은 JWT가 신뢰할 수 있는 발급자(CP)가 발행했음을 확인하는 과정이다.

#### RS256 서명 방식 (비대칭 키)

```
[CP — 토큰 발행 시]
  Private Key (비공개, CP만 보유)
      │
      └─▶ SHA-256(Header.Payload) 해시 → RSA 암호화 → Signature

[GW — 토큰 검증 시]
  Public Key (공개, GW가 CP에서 다운로드)
      │
      └─▶ Signature RSA 복호화 → 해시 A
          SHA-256(Header.Payload) 재계산 → 해시 B
          해시 A == 해시 B ? → 유효 : 위변조
```

#### GW에서 공개키 획득 과정

```
1. GW 시작 시 1회:
   GET https://cp.example.com:9443/oauth2/jwks
   응답:
   {
     "keys": [{
       "kty": "RSA",
       "kid": "key-id-1",
       "n":   "...(모듈러스)...",
       "e":   "AQAB",
       "use": "sig"
     }]
   }

2. 공개키를 GW 메모리에 캐시

3. 이후 모든 JWT 검증은 캐시된 공개키로 로컬 처리

4. CP가 키를 교체(Key Rotation) 시:
   - AMQP 이벤트로 GW에 알림
   - GW가 JWKS endpoint를 재조회하여 새 공개키 캐시 갱신
```

---

### 1-7. 공개키 / 개인키 (Public Key / Private Key)

**비대칭 암호화(Asymmetric Cryptography)** 방식으로, 수학적으로 쌍을 이루는 두 키를 사용한다.

| 키 종류 | 보관 위치 | 용도 |
|---------|-----------|------|
| **Private Key (개인키)** | CP만 보유 (절대 외부 공개 안 함) | JWT 서명 생성 |
| **Public Key (공개키)** | 누구나 조회 가능 (JWKS endpoint) | JWT 서명 검증 |

```
핵심 원리:
  Private Key로 서명한 내용은 → 해당 Private Key의 Public Key로만 검증 가능
  ∴ Public Key 검증 성공 = "CP가 발행한 토큰이 맞다"는 수학적 증명
```

#### WSO2 APIM 키스토어 위치

```
$APIM_HOME/repository/resources/security/
  ├── wso2carbon.jks   ← Private Key 포함 KeyStore
  └── client-truststore.jks  ← 신뢰할 Public Key 저장소
```

> 운영 환경에서는 반드시 **자체 서명 인증서(Self-Signed) 대신**  
> **CA 발급 인증서 또는 내부 PKI 인증서**로 교체 권장.

---

### 1-8. Opaque 토큰 (Opaque Token)

**Opaque 토큰**은 JWT와 달리 **내용을 직접 파싱할 수 없는** 불투명한 참조 문자열이다.

#### JWT vs Opaque 토큰 비교

| 항목 | JWT | Opaque 토큰 |
|------|-----|-------------|
| 형식 | `xxxxx.yyyyy.zzzzz` (3부분 구조) | `a1b2c3d4e5f6...` (임의 문자열) |
| 크기 | 수백 바이트 (Claim 포함) | 수십 바이트 (참조 ID만) |
| 자체 검증 | ✅ 가능 (공개키 사용) | ❌ 불가 |
| CP 호출 필요 | ❌ (캐시 미스 시만) | ✅ 매번 또는 캐시 |
| 토큰 취소 즉각성 | 캐시 TTL만큼 지연 | 즉각 반영 |
| 저장 위치 | 발행 기록만 DB 저장 | 전체 토큰을 DB에 저장 |

#### Opaque 토큰 검증 흐름

```
GW 수신 (Opaque Token: "2a8fd9c3b1e7...")
   │
   ▼
gateway_token_cache 조회
   ├─ HIT → 캐시된 검증 결과 사용 (CP 호출 없음)
   └─ MISS
         │
         POST https://cp.example.com:9443/oauth2/introspect
         Body: token=2a8fd9c3b1e7...
         │
         CP → apim_db에서 토큰 조회
         │
         응답:
         {
           "active": true,
           "username": "user@example.com",
           "scope": "read:orders",
           "client_id": "app-client-id",
           "exp": 1716000000,
           "sub": "user@example.com",
           "iss": "https://cp.example.com/oauth2/token"
         }
         │
         GW → cache 저장 → 이후 요청은 캐시 처리
```

> Opaque 토큰은 **즉각적인 취소(Revoke)** 가 필요한 경우 유리하지만,  
> GW가 CP에 자주 호출해야 하므로 **CP 부하가 증가**한다.  
> 운영 환경에서는 **JWT + 토큰 취소 이벤트** 조합이 성능상 권장된다.

---

## 2. API 추가·삭제 시 CP ↔ GW 이벤트 흐름

### 2-1. API 배포(추가) 전체 흐름

```
[개발자]
   │  Publisher Portal (9443) 접속
   │  API 생성/수정 → Revision 생성 → Deploy to Gateway 클릭
   ▼
[CP — API Manager Core]
   │  1. apim_db에 API 메타데이터 저장
   │     - EndpointConfig (Backend URL, TLS 설정)
   │     - URITemplates (리소스 경로, HTTP Method)
   │     - Security Scheme (OAuth2, API Key, Basic)
   │     - Throttling Policy (Gold, Silver, Unlimited 등)
   │     - CORS 설정, Mediation Policy
   │
   │  2. APIDeploymentService 이벤트 생성
   │     이벤트 타입: API_DEPLOY
   │     페이로드 예시:
   │     {
   │       "apiId":      "abc-123",
   │       "apiName":    "OrderAPI",
   │       "apiVersion": "v1.0",
   │       "context":    "/orders/v1",
   │       "type":       "REST",
   │       "revisionId": 3,
   │       "gatewayLabel": ["Default"]
   │     }
   ▼
[CP — Qpid Broker (AMQP 5672)]
   │  토픽에 이벤트 발행:
   │  └─ org.wso2.apimgt.deploymentEvent
   ▼
[GW Worker — 이벤트 수신 + 아티팩트 Pull]
   │  throttle_decision_endpoints JMS 연결로 이벤트 수신
   │  gateway_labels 로 자신에게 해당하는 이벤트만 필터링
   │
   │  처리 파이프라인:
   │
   │  Step 1: JMS로 API_DEPLOY 이벤트 수신 (apiId, context, revisionId 포함)
   │  Step 2: [apim.throttling] service_url 로 아티팩트 REST API Pull
   │          GET https://cp.example.com/api/am/gateway/v2/apis/{apiId}
   │          응답: Synapse API 설정 XML
   │          (InSequence, OutSequence, FaultSequence, Endpoint 포함)
   │  Step 3: API 캐시 무효화 (해당 apiId 항목)
   │  Step 4: Synapse API Engine에 핫 배포 (재시작 없이 즉시 적용)
   │  Step 5: gateway_token_cache, resource_cache 해당 API 항목 무효화
   ▼
[GW Worker — API 처리 준비 완료]
   클라이언트의 새 API 요청 수신 가능
```

**HA 시나리오 (CP-Server1 장애 중일 때 API 배포):**

```
개발자 → Publisher (CP-Server2 접속, LB 경유)
CP-Server2 → apim_db에 배포 상태 저장
GW Worker → CP LB (cp.example.com)를 통해 REST API Pull
→ 정상 배포 완료 (LB가 CP-Server2로 라우팅)
```

---

### 2-2. API 삭제(Undeploy) 흐름

```
[개발자]
   │  Publisher Portal → API Undeploy 또는 Revision Undeploy
   ▼
[CP — API Manager Core]
   │  apim_db 배포 상태 변경: DEPLOYED → UNDEPLOYED
   │  이벤트 타입: API_UNDEPLOY
   │  페이로드: { "apiId": "abc-123", "context": "/orders/v1" }
   ▼
[CP — apim_db]
   │  배포 상태 변경: DEPLOYED → UNDEPLOYED
   ▼
[GW Worker — Artifact Sync]
   │  CP REST API를 통해 변경 감지 및 동기화:
   │  Step 1: Synapse API Engine에서 해당 API 언로드
   │  Step 2: gateway_token_cache에서 해당 context의 항목 전부 제거
   │  Step 3: resource_cache 해당 API 항목 제거
   │
   │  이후 /orders/v1 경로 요청 수신 시:
   │  └─ 즉시 HTTP 404 Not Found 반환
   │     (백엔드 미도달, 클라이언트에게 "No matching resource found" 응답)
```

**GW 재시작 시 복구 흐름:**

```
GW 재시작
   │
   ▼
[apim.sync_runtime_artifacts.gateway] 설정 기반
CP REST API 전체 API 목록 Pull
GET https://cp.example.com/api/am/gateway/v2/apis
   │
   ▼
현재 배포 상태 기준으로 모든 API 재로드
→ DB 없이 CP 메모리/DB 기준으로 완전 복원
```

---

## 3. API 호출 시 Key Manager / Traffic Manager / GW 이벤트 흐름

### 3-1. 전체 처리 단계 개요

```
클라이언트
   │  HTTPS 요청: POST https://gw.example.com:8243/orders/v1/items
   │  Header: Authorization: Bearer {token}
   ▼
GW LB (8243)
   │  Round-Robin 분산 → GW-Inst1 선택
   ▼
GW Worker — Synapse Engine
   │
   ├─ [Step 1] 토큰 검증          ← Key Manager 관여
   ├─ [Step 2] 스로틀링 판단      ← Traffic Manager 관여
   ├─ [Step 3] 미디에이션 실행    ← InSequence (변환, 로깅)
   ├─ [Step 4] 백엔드 / MI 호출
   └─ [Step 5] 응답 반환
```

---

### 3-2. Step 1 — 토큰 검증 (Key Manager)

#### JWT 토큰 처리 (권장 방식)

```
GW 수신: Bearer eyJhbGci...

1. Header 파싱 → alg: RS256, kid: key-id-1
2. gateway_token_cache 조회 (jti 기준)
   ├─ Cache HIT (TTL 900초 내)
   │   └─ 즉시 통과, CP 호출 없음
   │
   └─ Cache MISS
         │
         3. Payload 파싱 → exp 만료 확인
         4. 공개키 캐시에서 kid 매칭 → 서명 검증 (로컬)
            서명 검증 실패 → HTTP 401 Unauthorized
         5. jti로 토큰 취소 블랙리스트 확인
            블랙리스트 존재 → HTTP 401 Unauthorized
         6. subscribedAPIs에 현재 API context 포함 여부 확인
            미포함 → HTTP 403 Forbidden
         7. scope 확인
         8. 검증 성공 → gateway_token_cache에 저장 (TTL 900초)
         └─ 이후 900초 동안 CP 호출 없이 캐시로 처리
```

#### Opaque 토큰 처리

```
GW 수신: Bearer 2a8fd9c3b1e7...

1. gateway_token_cache 조회
   ├─ HIT → 통과
   └─ MISS
         │
         POST https://cp.example.com:9443/oauth2/introspect
         (또는 /keymanager-operations/token/validate)
         │
         CP Key Manager:
           apim_db → OAuth2_ACCESS_TOKEN 테이블 조회
           구독 정보, 정책, 만료 여부 확인
           응답: { active, username, scope, client_id, exp, ... }
         │
         GW → cache 저장 → 통과
```

#### 토큰 취소(Revoke) 이벤트 흐름

```
클라이언트 or 관리자
   │  POST https://cp.example.com:9443/oauth2/revoke
   │  Body: token=eyJhbGci...
   ▼
CP Key Manager
   │  apim_db에 취소 기록
   │  ApimOauthEventInterceptor (event_listener) 발동
   │
   ▼
CP Internal API
   │  POST https://cp.example.com/internal/data/v1/notify  ← notification_endpoint
   │  (CP 자신의 엔드포인트, GW URL이 아님)
   │
   ▼
CP Event Hub
   │  Qpid 토픽 발행: tokenRevocation
   │  페이로드: { "jti": "abc-123-uuid", "expiryTime": 1716000000 }
   │  event_duplicate_url 로 CP-Server2 에도 복제
   ▼
GW Worker (throttle_decision_endpoints JMS로 실시간 수신)
   │  gateway_token_cache에서 jti 매칭 항목 즉시 제거
   │  토큰 취소 블랙리스트에 jti 추가
   ▼
이후 해당 토큰 요청 → 캐시 HIT 없음 + 블랙리스트 매칭 → HTTP 401
```

> **핵심:** `notification_endpoint`는 **CP 자신의 URL**이다 (`https://cp.wso2.com/internal/data/v1/notify`).  
> GW URL이 아니며, CP가 자신의 내부 API를 통해 Event Hub에 이벤트를 등록하면 JMS로 GW에 전파된다.

---

### 3-3. Step 2 — 스로틀링 판단 (Traffic Manager)

#### GW 로컬 스로틀링 판단

```
GW (토큰 검증 완료)
   │
   ▼
로컬 스로틀링 상태 맵 조회
  {apiKey: "OrderAPI:v1:Gold", isThrottled: false}
   │
   ├─ isThrottled = false → 통과 ──────────────── ▶ Step 3
   └─ isThrottled = true  → HTTP 429 Too Many Requests
                             (CP 재확인 없이 즉시 거부)
```

#### 카운터 전송 (비동기, 매 요청마다)

```
GW → CP Traffic Manager
  Thrift 프로토콜 (바이너리, 경량)
  TCP 9611 (평문) / TCP 9711 (SSL)
  이벤트 페이로드:
  {
    "throttleKey":  "OrderAPI:v1:Gold",     // API + Tier
    "appKey":       "MyApp:Unlimited",      // Application + Tier
    "userKey":      "user@example.com:20", // User + Tier
    "properties": {
      "api.id":     "abc-123",
      "app.id":     "5",
      "ip":         "192.168.1.100"
    }
  }

  ※ receiver_url에 양 CP 주소 콤마 구분 → 양 CP에 동시 전송
     tcp://10.0.0.11:9611,tcp://10.0.0.12:9611
```

#### CP Traffic Manager 집계 및 결정 흐름

```
CP-Server1 Traffic Manager                CP-Server2 Traffic Manager
  (Siddhi CEP, 독립 처리)                   (Siddhi CEP, 독립 처리)
      │                                           │
      │ GW는 양 TM에 동시 전송                      │
      │ (각 TM이 독립적으로 카운터 집계)              │
      │                                           │
  자체 카운터 증가                           자체 카운터 증가
  정책 임계값과 비교                         정책 임계값과 비교
  예) Gold Tier = 분당 100회                예) Gold Tier = 분당 100회
      │                                           │
  ┌───┴────┐                               ┌───────┴────┐
 초과    미초과                            초과       미초과
  │                                           │
  │ 각 TM이 독립적으로 AMQP 발행                │
  ▼                                           ▼
AMQP 발행 (5672)                         AMQP 발행 (5672)
토픽: org.wso2.apimgt.throttleout        토픽: org.wso2.apimgt.throttleout
{                                        {
  "throttleKey": "OrderAPI:v1:Gold",       "throttleKey": "OrderAPI:v1:Gold",
  "isThrottled": true,                     "isThrottled": true,
  "expiryTimeStamp": 1716000060000         "expiryTimeStamp": 1716000060000
}                                        }
  │                                           │
  └───────────────────┬───────────────────────┘
                      ▼
               GW Worker (양 TM에서 동일 이벤트 수신 — GW는 먼저 도착한 것 적용)
                      │
               로컬 스로틀링 상태 맵 업데이트:
               { "OrderAPI:v1:Gold": { isThrottled: true, expiry: ... } }
                      │
               이후 해당 API 요청 → GW 로컬에서 즉시 429 반환
               (expiryTimeStamp 경과 후 자동 해제)

※ 4.x TM 처리 특성:
   GW가 양 TM에 동시 전송하므로 각 TM은 전체 트래픽을 수신함.
   TM 1대가 장애여도 나머지 TM이 스로틀링 판단을 지속한다.

※ TM 독립 카운터 주의:
   각 TM이 독립적으로 카운터를 유지하므로, 정책 한도 100 req/min 기준으로
   실제로는 각 TM이 100을 허용 → 총 200 req/min까지 허용될 수 있음.
   ⚠️ APIM 4.3에서 `[apim.throttling]` 섹션에는 `event_duplicate_url` 파라미터가 없다.
   TM 간 스로틀링 카운터 동기화를 위해서는 TM 분리(Separated TM) 구성 또는
   외부 Redis 기반 분산 카운터 구성이 필요하다.
```

---

### 3-4. Step 3-5 — 미디에이션 & 백엔드 응답

#### InSequence 미디에이션 파이프라인

```
Synapse InSequence 실행:
  1. Authorization 헤더 제거 (백엔드에 토큰 미전달)
  2. X-JWT-Assertion 헤더 추가 (사용자 정보를 백엔드에 전달)
  3. 커스텀 헤더 추가/변환
  4. Analytics 이벤트 생성 (비동기 발행)
  5. 엔드포인트 결정
```

#### 직접 백엔드 vs MI 경유

```
케이스 1: 단순 REST 프록시
  GW → Backend HTTP (직접)
  Backend → GW → Client

케이스 2: MI 경유 (복잡 미디에이션)
  GW → MI HTTP (8290/8291)
    MI: InSequence 실행
      - 데이터 변환 (XML↔JSON, 필드 매핑)
      - 업무 DB JDBC 조회/저장 (Oracle/MSSQL/PostgreSQL)
      - 외부 서비스 호출
    MI → OutSequence → 응답 변환
  MI → GW → Client
```

---

### 3-5. 전체 시퀀스 다이어그램 (캐시 MISS 기준)

```
Client    GW-LB   GW-Inst1   CP-KM(9443)   CP-TM(9611)   Backend/MI
  │         │         │             │              │             │
  │─ HTTPS ─▶         │             │              │             │
  │         │─ 분산 ──▶            │              │             │
  │         │         │─ POST /introspect ─▶       │             │
  │         │         │         (Opaque) or         │             │
  │         │         │─ 로컬 서명검증(JWT)          │             │
  │         │         │◀─ {active, policy} ─────────│             │
  │         │         │─ cache 저장                 │             │
  │         │         │─ Thrift 카운터 ─────────────▶             │
  │         │         │ (비동기, 논블로킹)            │             │
  │         │         │─ HTTP Request ──────────────────────────▶ │
  │         │         │◀──────────────────── Response ────────── │
  │◀─────── HTTPS Response ─────────────────────────────────────  │
  │         │         │                             │             │
  │ [이후 900초 내 동일 토큰 요청]                  │             │
  │─ HTTPS ─▶         │             │              │             │
  │         │─ 분산 ──▶            │              │             │
  │         │         │─ cache HIT (CP 호출 없음)   │             │
  │         │         │─ HTTP Request ──────────────────────────▶ │
  │◀─────── HTTPS Response ─────────────────────────────────────  │
```

---

## 4. GW Stateless 설계 원리 요약

### 4-1. GW가 보관하는 상태 (메모리 캐시)

| 캐시 항목 | 저장 위치 | TTL | 갱신 트리거 |
|-----------|-----------|-----|-------------|
| API 메타데이터 | GW 로컬 메모리 | 영구 (이벤트로 갱신) | CP AMQP 배포 이벤트 |
| 토큰 검증 결과 | gateway_token_cache | 900초 | 취소 이벤트 수신 시 즉시 만료 |
| 공개키(JWKS) | GW 로컬 메모리 | 영구 (이벤트로 갱신) | CP 키 교체 이벤트 |
| 스로틀링 결정 | 로컬 스로틀링 맵 | expiryTimeStamp | CP TM AMQP 이벤트 |
| API resource 정보 | resource_cache | 900초 | API 배포/삭제 이벤트 |

### 4-2. GW가 보관하지 않는 상태 (CP 의존)

| 항목 | 저장 위치 | 이유 |
|------|-----------|------|
| 스로틀링 카운터 집계 | CP Traffic Manager (각 노드 독립 Siddhi CEP) | 다중 GW 전역 집계 필요 |
| 토큰 원본 데이터 | CP apim_db | 취소·만료 관리 필요 |
| API 정책·구독 정보 | CP apim_db | 단일 소스 유지 |
| 분석(Analytics) 원본 | CP Analytics DB | 중앙 집계 필요 |

### 4-3. 설계가 가져오는 운영 장점

```
1. 수평 확장 (Scale-Out)
   GW 노드 추가 → LB 등록 → 자동으로 이벤트 구독 시작
   → 추가 설정 없이 즉시 운영 투입

2. 장애 격리
   GW-Inst1 장애 → LB Health Check 실패 → 자동 제외
   GW-Inst2 정상 운영 지속 (세션 없으므로 재연결 불필요)

3. 무중단 배포
   GW-Inst1 중단 → API 배포/업데이트 → GW-Inst1 재시작
   재시작 후 자동으로 CP에서 최신 상태 복원

4. CP 장애 격리
   CP 장애 중에도 GW는 캐시로 요청 처리 지속 (최대 900초)
   다만, 토큰 캐시 만료 후 신규 검증 불가 → CP 복구 우선
```

---

## 참고: 주요 포트 및 프로토콜 정리

| 포트 | 프로토콜 | 구간 | 용도 |
|------|----------|------|------|
| 9443 | HTTPS | GW → CP | 토큰 검증(Introspect), API 정보 Pull, JWKS |
| 5672 | AMQP | GW ↔ CP | 이벤트 구독 (API 배포, 스로틀링, 토큰 취소) |
| 9611 | Thrift TCP | GW → CP | 스로틀링 카운터 전송 (평문) |
| 9711 | Thrift SSL | GW → CP | 스로틀링 카운터 전송 (암호화) |
| 8243 | HTTPS | Client → GW | API 호출 (HTTPS) |
| 8280 | HTTP  | Client → GW | API 호출 (HTTP, 내부망 전용) |

---

## 5. WSO2 APIM 4.3 클러스터 구성 상세

### 5-1. 전체 배포 아키텍처

WSO2 API Manager 4.3은 단일 바이너리로 배포되지만, `deployment.toml` 의 `[server]` 역할 설정과 구동 프로파일로 **컴포넌트를 분리** 운영한다.

```
                    ┌──────────────────────────────────────┐
                    │            Load Balancer             │
                    │   CP용: cp.example.com:9443          │
                    │   GW용: gw.example.com:8243/8280     │
                    └──────────┬──────────────┬────────────┘
                               │ CP           │ GW
          ┌────────────────────┘              └──────────────────────┐
          │                                                          │
┌─────────▼──────────┐  직접통신없음   ┌────────────────────────────▼──────┐
│   CP-Server1        │   (공유 DB만)  │   CP-Server2                      │
│   10.0.0.11         │                │   10.0.0.12                       │
│ ┌─────────────────┐ │                │ ┌─────────────────────────────────┐│
│ │ Publisher Portal│ │                │ │ Publisher Portal (HA 복제)       ││
│ │ DevPortal       │ │                │ │ DevPortal                        ││
│ │ Admin Portal    │ │                │ │ Admin Portal                     ││
│ │ Key Manager     │ │                │ │ Key Manager                      ││
│ │ Traffic Manager │ │                │ │ Traffic Manager                  ││
│ │ Qpid (5672)     │ │                │ │ Qpid (5672)                      ││
│ └─────────────────┘ │                │ └─────────────────────────────────┘│
└──────────┬──────────┘                └───────────────┬────────────────────┘
           │                                           │
           └───────────────┬───────────────────────────┘
                           │ 공유 DB 접속 (JDBC)
               ┌───────────▼──────────────┐
               │     공유 데이터베이스      │
               │  apim_db  (PostgreSQL)   │
               │  shared_db / um_db       │
               │  reg_db  (Registry)      │
               └──────────────────────────┘

┌──────────────────────────┐       ┌──────────────────────────┐
│   GW-Worker1             │       │   GW-Worker2             │
│   10.0.0.21              │       │   10.0.0.22              │
│ ┌──────────────────────┐ │       │ ┌──────────────────────┐ │
│ │ Synapse Engine       │ │       │ │ Synapse Engine       │ │
│ │ JWT Validator        │ │       │ │ JWT Validator        │ │
│ │ Throttle Handler     │ │       │ │ Throttle Handler     │ │
│ │ gateway_token_cache  │ │       │ │ gateway_token_cache  │ │
│ │ API Artifact Cache   │ │       │ │ API Artifact Cache   │ │
│ └──────────────────────┘ │       │ └──────────────────────┘ │
│ throttle ← 10.0.0.11:5672│       │ throttle ← 10.0.0.11:5672│
│ event_hub← 10.0.0.12:5672│       │ event_hub← 10.0.0.12:5672│
└──────────────────────────┘       └──────────────────────────┘
```

> **GW Worker는 CP와 직접 통신 없이 완전히 독립(Stateless) 동작한다.**  
> 공유 DB 접근도 없다. 모든 상태는 AMQP 이벤트로 CP에서 Push 받는다.

---

### 5-2. Control Plane 구성요소 상세

#### 5-2-1. Publisher Portal

| 항목 | 내용 |
|------|------|
| URL | `https://cp.example.com:9443/publisher` |
| 역할 | API 설계·생성·수명주기 관리·배포 |
| 주요 기능 | API Revision, API Product, Mediation Policy, Rate Limit 할당 |
| 내부 DB | `apim_db.AM_API`, `AM_API_URL_MAPPING`, `AM_API_REVISION` |

API 배포(Deploy) 클릭 시 내부 흐름:

```
Publisher UI → APIM REST API (9443/api/am/publisher/v4/apis/{id}/deploy-revision)
  → APIDeploymentService.deployAPIRevision()
  → apim_db에 배포 상태 저장
  → EventPublisher → Qpid 5672 → org.wso2.apimgt.deploymentEvent 토픽
```

#### 5-2-2. Developer Portal

| 항목 | 내용 |
|------|------|
| URL | `https://cp.example.com:9443/devportal` |
| 역할 | API 구독, Application 생성, API Key/OAuth 자격증명 발급 |
| 주요 기능 | Application Tier 설정, 구독 승인(Workflow), Swagger 문서 |
| 내부 DB | `apim_db.AM_APPLICATION`, `AM_SUBSCRIPTION`, `AM_APP_KEY_MAPPING` |

#### 5-2-3. Admin Portal

| 항목 | 내용 |
|------|------|
| URL | `https://cp.example.com:9443/admin` |
| 역할 | 플랫폼 전체 정책 관리 |
| 주요 기능 | Advanced Throttling Policy, Custom Policy, Bot Detection, Key Manager 등록 |

#### 5-2-4. Key Manager

OAuth2 / OIDC 토큰 발급 및 검증 서비스.

| 엔드포인트 | 경로 | HTTP Method | 기능 |
|-----------|------|-------------|------|
| Token | `/oauth2/token` | POST | Access Token / Refresh Token 발급 |
| Revoke | `/oauth2/revoke` | POST | 토큰 취소 |
| Introspect | `/oauth2/introspect` | POST | Opaque 토큰 검증 |
| JWKS | `/oauth2/jwks` | GET | RSA 공개키 목록 (JSON Web Key Set) |
| Authorize | `/oauth2/authorize` | GET | Authorization Code Grant 진입점 |
| UserInfo | `/oauth2/userinfo` | GET | OIDC 사용자 클레임 반환 |
| Token Validate | `/keymanager-operations/token/validate` | POST | GW용 내부 검증 API |

**External Key Manager 연동 (WSO2 IS, Okta, Azure AD 등)**

WSO2 APIM 4.3은 복수의 Key Manager를 등록할 수 있다:

```
Admin Portal → Key Managers → Add Key Manager
  type: "Okta" / "KeyCloak" / "WSO2-IS" / "Default"
  
설정 후 동작:
  - 해당 KM 발급 토큰을 GW가 검증 시 해당 KM의 JWKS/Introspect 사용
  - apim_db.IDP_METADATA 테이블에 등록
  - AMQP → org.wso2.apimgt.keymanager 토픽으로 GW에 전파
```

#### 5-2-5. Traffic Manager

| 항목 | 내용 |
|------|------|
| 수신 프로토콜 | Thrift Binary Protocol (TCP 9611 평문 / 9711 SSL) |
| 집계 방식 | **Siddhi CEP 엔진** — 각 TM 노드가 독립적으로 인메모리 카운터 관리 (`event_duplicate_url` 설정 시 초과 결정을 타 TM에 복제하여 동기화) |
| 결정 전파 | AMQP 5672 → `org.wso2.apimgt.throttleout` 토픽 |
| 정책 스토어 | `apim_db.AM_POLICY_*` 테이블 |

**스로틀링 정책 계층구조:**

```
API-Level Tier
  └─ Resource-Level Tier  (URI별 설정 가능)
        └─ Application-Level Tier  (앱 등록 시 설정)
              └─ Subscription-Level Tier  (구독 시 설정)
                    └─ User-Level Tier  (고급 정책)

예) Gold Tier = API Tier "Gold" → 분당 100 req
    한 요청이 모든 계층의 카운터를 동시에 증가시킴
    어느 한 계층이라도 초과 → 즉시 429 반환
```

#### 5-2-6. Qpid Broker (내장)

WSO2 APIM 4.3은 **Apache Qpid**를 내장 메시지 브로커로 사용한다.  
(ActiveMQ는 APIM 3.x 이하에서 사용되었으며, 4.x부터 Qpid로 교체되었다.)

| 포트 | 용도 |
|------|------|
| 5672 | AMQP 0-9-1 (GW 이벤트 구독, CP 간 이벤트 복제) |
| 8672 | AMQP TLS/SSL (보안 연결 시 사용) |

> Qpid는 별도의 deployment.toml 설정 없이 APIM 기동 시 자동으로 활성화된다.  
> TLS 연결이 필요한 경우 `throttle_decision_endpoints`와 `event_listening_endpoints`에 `ssl://` 스킴을 사용하고,  
> 아래 브로커 SSL 설정을 추가한다.

```toml
# CP deployment.toml — Qpid SSL 적용 시 (선택)
[broker.transport.amqp.ssl_connection]
enabled  = true
port     = 8672
ssl_only = false

[broker.transport.amqp.ssl_connection.keystore]
location  = "$ref{keystore.tls.file_name}"
password  = "$ref{keystore.tls.password}"
cert_type = "SunX509"

[broker.transport.amqp.ssl_connection.truststore]
location  = "$ref{truststore.file_name}"
password  = "$ref{truststore.password}"
cert_type = "SunX509"
```

---

### 5-3. CP deployment.toml 전체 설정 (CP-Server1 기준)

```toml
##############################################################
# WSO2 API Manager 4.3 — Control Plane Node 1
# 파일: $APIM_HOME/repository/conf/deployment.toml
# 노드: 10.0.0.11 (cp-server1)
##############################################################

# ═══════════════════════════════════════════
# 1. 서버 기본 정보
# ═══════════════════════════════════════════
[server]
hostname    = "cp.example.com"   # LB FQDN (URL 생성에 사용, 노드 IP가 아님)
node_ip     = "127.0.0.1"        # 공식 문서 기준 127.0.0.1 (4.x에서 클러스터링 미사용)
server_role = "control-plane"   # 4.x 분산 배포 시 역할 명시
base_path   = "${carbon.protocol}://${carbon.host}:${carbon.management.port}"

# ═══════════════════════════════════════════
# 2. Super Admin 계정
# ═══════════════════════════════════════════
[super_admin]
username             = "admin"
password             = "Admin@12345"
create_admin_account = true

# ═══════════════════════════════════════════
# 3. 사용자 저장소
# ═══════════════════════════════════════════
[user_store]
type = "database_unique_id"
# LDAP 연동 시: type = "read_write_ldap_unique_id"

# ═══════════════════════════════════════════
# 4. 데이터베이스 — APIM 메타데이터 DB
# ═══════════════════════════════════════════
[database.apim_db]
type     = "postgresql"
url      = "jdbc:postgresql://db.example.com:5432/apim_db"
username = "apim_user"
password = "apim_pass"
driver   = "org.postgresql.Driver"

[database.apim_db.pool_options]
max_active       = 50
max_idle         = 10
min_idle         = 5
test_on_borrow   = true
validation_query = "SELECT 1"
default_auto_commit = true

# ═══════════════════════════════════════════
# 5. 데이터베이스 — 사용자·레지스트리 DB
# ═══════════════════════════════════════════
[database.shared_db]
type     = "postgresql"
url      = "jdbc:postgresql://db.example.com:5432/shared_db"
username = "shared_user"
password = "shared_pass"
driver   = "org.postgresql.Driver"

[database.shared_db.pool_options]
max_active       = 30
max_idle         = 5
min_idle         = 2
test_on_borrow   = true
validation_query = "SELECT 1"

# ═══════════════════════════════════════════
# 6. 클러스터링 설정 — WSO2 APIM 4.x에서 제거됨
# CP 노드 간 HA는 공유 DB(apim_db, shared_db)로 달성
# [clustering] 섹션을 작성하지 않는다
# ═══════════════════════════════════════════

# ═══════════════════════════════════════════
# 7. Keystore 설정
# ═══════════════════════════════════════════
# Primary Keystore: JWT 서명·내부 데이터 암호화에 사용
[keystore.primary]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

# TLS Keystore: HTTPS 인증서 (운영 시 공인 CA 인증서로 교체 필수)
[keystore.tls]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

# Internal Keystore: 내부 컴포넌트 간 암호화
[keystore.internal]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

# Trust Store: 신뢰할 서버 인증서 저장 (GW, 외부 KM 등)
[truststore]
file_name = "client-truststore.jks"
type      = "JKS"
password  = "wso2carbon"

# ═══════════════════════════════════════════
# 8. Key Manager 설정
# HA 구성에서는 CP LB URL 사용 (localhost 아님)
# ═══════════════════════════════════════════
[apim.key_manager]
service_url = "https://cp.example.com/services/"
username    = "$ref{super_admin.username}"
password    = "$ref{super_admin.password}"

# ═══════════════════════════════════════════
# 9. JWT (Backend JWT / API 호출자 정보 전달)
# ═══════════════════════════════════════════
[apim.jwt]
enable             = true
header             = "X-JWT-Assertion"      # 백엔드에 전달할 헤더명
encoding           = "base64"
generator_impl     = "org.wso2.carbon.apimgt.keymgt.token.JWTGenerator"
claim_dialect      = "http://wso2.org/claims"
convert_dialect    = false
signing_algorithm  = "SHA256withRSA"        # RS256
enable_user_claims = true
claims_extractor_impl = "org.wso2.carbon.apimgt.impl.token.DefaultClaimsRetriever"

# ═══════════════════════════════════════════
# 10. OAuth 토큰 설정
# ═══════════════════════════════════════════
[apim.oauth_config]
enable_outbound_auth_header    = true      # Authorization 헤더 백엔드 전달 여부
auth_header                    = "Authorization"
revoke_endpoint_url            = "https://${carbon.local.ip}:${https.nio.port}/oauth2/revoke"
enable_token_encryption        = false     # DB 저장 시 토큰 암호화 여부
enable_token_hashing           = false     # 토큰 해싱 저장 여부

# ═══════════════════════════════════════════
# 11. 스로틀링 (CP에는 [apim.throttling] 섹션 불필요)
# CP는 Traffic Manager 자신이므로 GW→TM 방향의 url_group 설정이 없다.
# [[apim.throttling.url_group]] 은 GW Worker deployment.toml에만 존재한다.
# (공식 CP 샘플 기준)
# ═══════════════════════════════════════════

# ═══════════════════════════════════════════
# 12. Event Hub (이벤트 발행 — CP 전용)
# ⚠️  CP HA(2대 이상) 구성 시에만 필요.
#     CP가 1대뿐인 경우 이 섹션 전체 생략 가능.
# CP-Server1 기준: 자신(localhost)에서 이벤트 수신,
# CP-Server2를 event_duplicate_url로 지정
# ═══════════════════════════════════════════
[apim.event_hub]
enable      = true
username    = "$ref{super_admin.username}"
password    = "$ref{super_admin.password}"
service_url = "https://cp.example.com/services/"        # CP LB URL
event_listening_endpoints = ["tcp://localhost:5672"]    # 자신의 AMQP 포트
event_duplicate_url       = ["tcp://10.0.0.12:5672"]   # 상대 CP 노드 (CP-Server2)

[[apim.event_hub.publish.url_group]]
urls      = ["tcp://10.0.0.11:9611"]
auth_urls = ["ssl://10.0.0.11:9711"]

[[apim.event_hub.publish.url_group]]
urls      = ["tcp://10.0.0.12:9611"]
auth_urls = ["ssl://10.0.0.12:9711"]

# ═══════════════════════════════════════════
# 12-1. 토큰 취소 Event Listener
# ⚠️  Resident Key Manager(CP 내장) 사용 시에만 CP에 추가.
#     WSO2 IS를 Key Manager로 사용하는 경우,
#     이 설정은 CP가 아닌 IS 노드의 deployment.toml에 추가해야 한다.
# ═══════════════════════════════════════════
[[event_listener]]
id    = "token_revocation"
type  = "org.wso2.carbon.identity.core.handler.AbstractIdentityHandler"
name  = "org.wso2.is.notification.ApimOauthEventInterceptor"
order = 1

[event_listener.properties]
notification_endpoint        = "https://cp.example.com/internal/data/v1/notify"
username                     = "${admin.username}"
password                     = "${admin.password}"
'header.X-WSO2-KEY-MANAGER'  = "default"

# ═══════════════════════════════════════════
# 13. DevPortal URL (분산 배포 시 명시 필요)
# ═══════════════════════════════════════════
[apim.devportal]
url = "https://cp.example.com/devportal"

# ═══════════════════════════════════════════
# 14. Gateway 환경 등록
# ═══════════════════════════════════════════
[[apim.gateway.environment]]
name                      = "Default"
type                      = "hybrid"
display_in_api_console    = true
description               = "This is a hybrid gateway that handles both production and sandbox token traffic."
show_as_token_endpoint_url = true
service_url               = "https://gw.example.com/services/"   # GW LB URL (포트 없음 — LB가 443→9443 처리)
http_endpoint             = "http://gw.example.com"
https_endpoint            = "https://gw.example.com"
ws_endpoint               = "ws://gw.example.com:9099"
wss_endpoint              = "wss://gw.example.com:8099"
# username / password 는 공식 샘플에 없으며 기본값으로 동작함

# ═══════════════════════════════════════════
# 14. Analytics (On-Premise ELK)
# ═══════════════════════════════════════════
[apim.analytics]
enable = true
type   = "elk"

# Choreo Analytics 사용 시 아래로 교체:
# type = "choreo"
# [[apim.analytics.url_group]]
# analytics_url      = ["tcp://analytics.choreo.dev:7612"]
# analytics_auth_url = ["ssl://analytics.choreo.dev:7712"]

# ═══════════════════════════════════════════
# 15. CORS 전역 설정
# ═══════════════════════════════════════════
[apim.cors]
allow_origins      = "*"
allow_methods      = ["GET","PUT","POST","DELETE","PATCH","OPTIONS"]
allow_headers      = ["authorization","Access-Control-Allow-Origin",
                      "Content-Type","SOAPAction","apikey","Internal-Key"]
allow_credentials  = false
enable_validations = false

# ═══════════════════════════════════════════
# 16. 토큰 캐시 설정
# ═══════════════════════════════════════════
[apim.cache]
gateway_token_cache_enabled   = true
km_token_cache                = true
gateway_resource_cache_enabled = true
jwt_claim_cache_expiry         = 900     # 초

# ═══════════════════════════════════════════
# 17. Devportal 설정
# ═══════════════════════════════════════════
[apim.devportal]
enable_application_sharing      = false
application_sharing_type        = ""      # "default" or "saml"
display_multiple_versions       = false
display_deprecated_apis         = false
enable_comments                 = true
enable_ratings                  = true
enable_forum                    = true
enable_anonymous_mode           = true   # 비로그인 API 목록 조회 허용

# ═══════════════════════════════════════════
# 18. 워크플로우 설정 (구독 승인 등)
# ═══════════════════════════════════════════
[apim.workflow]
enable         = false   # true: 구독/앱 생성 시 관리자 승인 필요
service_url    = "https://localhost:9445/bpmn"
username       = "$ref{super_admin.username}"
password       = "$ref{super_admin.password}"
callback_endpoint = "https://localhost:${mgt.transport.https.port}/api/am/admin/v0.17/workflows/update-workflow-status"
token_endpoint = "https://localhost:${https.nio.port}/token"
client_registration_endpoint = "https://localhost:${mgt.transport.https.port}/client-registration/v0.17/register"

# ═══════════════════════════════════════════
# 19. 로그 감사 (Audit Log)
# ═══════════════════════════════════════════
[apim.publisher_workflows]
enable_consumer_key_generation = true

# ═══════════════════════════════════════════
# 20. 분산 캐시 무효화 (CP HA 구성 시 권장)
# ═══════════════════════════════════════════
[apim.cache_invalidation]
enabled = true
domain  = "control-plane-domain"

# ═══════════════════════════════════════════
# 21. Qpid Heartbeat (AMQP idle 연결 유지)
# ═══════════════════════════════════════════
[qpid.heartbeat]
delay          = 1
timeout_factor = 3.0

# ═══════════════════════════════════════════
# 22. 포트 설정 (기본값, 충돌 시 변경)
# ═══════════════════════════════════════════
[transport.http]
properties.port      = 9763
properties.proxyPort = 80

[transport.https]
properties.port      = 9443
properties.proxyPort = 443

# ═══════════════════════════════════════════
# 23. [선택] Qpid Broker — AMQP TLS/SSL 적용
# GW에서 throttle_decision_endpoints / event_listening_endpoints를
# ssl:// 로 설정하는 경우 CP(Qpid Broker) 측에도 아래 SSL 설정 추가 필요
# ═══════════════════════════════════════════
# [broker.transport.amqp.ssl_connection]
# enabled   = true
# port      = 8672
# ssl_only  = false   # true 설정 시 비암호화 연결(5672) 거부
#
# [broker.transport.amqp.ssl_connection.keystore]
# location = "$ref{keystore.tls.file_name}"
# password = "$ref{keystore.tls.password}"
# cert_type = "SunX509"
#
# [broker.transport.amqp.ssl_connection.truststore]
# location = "$ref{truststore.file_name}"
# password = "$ref{truststore.password}"
# cert_type = "SunX509"
```

> **CP-Server2(10.0.0.12) 설정 차이점:** `event_duplicate_url = ["tcp://10.0.0.11:5672"]` 로 상대 노드만 바꾸면 된다. `node_ip`, DB 접속, Gateway 환경, Analytics 설정은 모두 동일하다.

---

### 5-4. GW Worker deployment.toml 전체 설정

GW Worker는 CP의 구성요소(Publisher, DevPortal, Key Manager UI 등)를 **구동하지 않는다.** Synapse 게이트웨이 엔진만 활성화된다.

```toml
##############################################################
# WSO2 API Manager 4.3 — Gateway Worker Node 1
# 파일: $APIM_HOME/repository/conf/deployment.toml
# 노드: 10.0.0.21 (gw-worker1)
##############################################################

# ═══════════════════════════════════════════
# 1. 서버 기본 정보
# ═══════════════════════════════════════════
[server]
hostname    = "gw.example.com"   # GW LB FQDN (URL 생성에 사용, 노드 IP가 아님)
node_ip     = "127.0.0.1"        # 공식 문서 기준 127.0.0.1
server_role = "gateway-worker"  # 4.x 분산 배포 시 역할 명시

# ═══════════════════════════════════════════
# 2. Super Admin 계정 (CP와 동일하게 설정)
# ═══════════════════════════════════════════
[super_admin]
username             = "admin"
password             = "Admin@12345"
create_admin_account = true

# ═══════════════════════════════════════════
# 3. 사용자 저장소
# ═══════════════════════════════════════════
[user_store]
type = "database_unique_id"

# ═══════════════════════════════════════════
# 4. 데이터베이스
# apim_db: 미사용 (설정 불필요)
# shared_db: 단일 테넌트 환경에서는 실제 데이터 접근 없음
#            단, Carbon 프레임워크 초기화(registry/user core)가
#            설정 존재를 요구하므로 설정은 유지해야 함
#            (멀티테넌시·Google Analytics 사용 시에는 실제 사용)
# ═══════════════════════════════════════════
[database.shared_db]
type     = "postgresql"
url      = "jdbc:postgresql://db.example.com:5432/shared_db"
username = "shared_user"
password = "shared_pass"
driver   = "org.postgresql.Driver"

# ═══════════════════════════════════════════
# 5. 클러스터링 — GW Worker는 [clustering] 섹션 불필요
# CP와 마찬가지로 4.x에서 Hazelcast 미사용
# GW는 Stateless 독립 운영 (apim_db 직접 접근 없음, shared_db만 필요)
# ═══════════════════════════════════════════

# ═══════════════════════════════════════════
# 6. Keystore 설정 (CP와 동일 인증서 사용 권장)
# ═══════════════════════════════════════════
[keystore.primary]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

[keystore.tls]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

[truststore]
file_name = "client-truststore.jks"
type      = "JKS"
password  = "wso2carbon"

# ═══════════════════════════════════════════
# 7. Gateway 아티팩트 동기화 대상 레이블 지정 (필수)
# CP에서 배포 시 지정한 Gateway Label과 일치해야 함
# ═══════════════════════════════════════════
[apim.sync_runtime_artifacts.gateway]
gateway_labels = ["Default"]

# ═══════════════════════════════════════════
# 9. Key Manager 연결 (토큰 검증 Endpoint)
# [apim.event_hub] 섹션은 표준 분산 배포에서 GW Worker 불필요 — CP 전용
# (TM 분리 배포 시에만 GW에 [apim.event_hub] 추가)
# ═══════════════════════════════════════════
[apim.key_manager]
service_url = "https://cp.example.com/services/"   # CP LB URL
username    = "$ref{super_admin.username}"
password    = "$ref{super_admin.password}"

# ═══════════════════════════════════════════
# 10. 스로틀링 카운터 전송 (Traffic Manager)
# throttle_decision_endpoints 의 단일 JMS 연결이
# ① 스로틀링 결정 + ② API/App/Sub/KM 이벤트를 모두 수신
# ═══════════════════════════════════════════
[apim.throttling]
service_url                 = "https://cp.example.com/services/"   # CP LB
throttle_decision_endpoints = ["tcp://10.0.0.11:5672", "tcp://10.0.0.12:5672"]
enable_data_publishing      = true   # GW→TM 카운터 전송 활성
enable_policy_deploy        = false  # GW는 정책 배포 주체가 아님
enable_blacklist_condition  = true   # IP/토큰 블랙리스트 적용

# TM 양 노드에 동시 전송 (HA, 카운터 이중화)
[[apim.throttling.url_group]]
traffic_manager_urls      = ["tcp://10.0.0.11:9611"]
traffic_manager_auth_urls = ["ssl://10.0.0.11:9711"]

[[apim.throttling.url_group]]
traffic_manager_urls      = ["tcp://10.0.0.12:9611"]
traffic_manager_auth_urls = ["ssl://10.0.0.12:9711"]

# ═══════════════════════════════════════════
# 10-1. [선택] JMS(AMQP) 연결에 TLS/SSL 적용
# throttle_decision_endpoints 를 ssl:// 로 변경하고
# 아래 섹션을 추가한다. (기본값: 비암호화 tcp://)
# ═══════════════════════════════════════════
# [apim.throttling.jms]
# topic_connection_factory = "amqp://admin:Admin@12345@clientid/carbon?brokerlist='tcp://10.0.0.11:5672?ssl='true'&ssl_cert_alias='wso2carbon''"

# ═══════════════════════════════════════════
# 10. JWT 검증 및 Backend JWT
# ═══════════════════════════════════════════
[apim.jwt]
enable            = true
header            = "X-JWT-Assertion"
signing_algorithm = "SHA256withRSA"
enable_user_claims = true

# ═══════════════════════════════════════════
# 11. Analytics (GW → Analytics Server)
# ═══════════════════════════════════════════
[apim.analytics]
enable = true
type   = "elk"

# ═══════════════════════════════════════════
# 12. 토큰·리소스 캐시
# ═══════════════════════════════════════════
[apim.cache]
gateway_token_cache_enabled    = true
gateway_resource_cache_enabled = true
jwt_claim_cache_expiry         = 900    # 초 (15분)
# 캐시 항목: gateway_token_cache, resource_cache, km_token_cache

# ═══════════════════════════════════════════
# 13. 포트 및 Proxy 설정
# ═══════════════════════════════════════════
[transport.http]
properties.port      = 9763
properties.proxyPort = 80    # LB 앞단 HTTP 포트 (외부 노출 포트)

[transport.https]
properties.port      = 9443
properties.proxyPort = 443   # LB 앞단 HTTPS 포트 (외부 노출 포트)

# ═══════════════════════════════════════════
# 14. 미디에이션 실행 엔진 (Synapse)
# ═══════════════════════════════════════════
[mediation]
sync_mediators_enabled  = true
statistics_enabled      = true
flow_statistics_enabled = true

# ═══════════════════════════════════════════
# 15. 게이트웨이 구동 프로파일 지정
# start.sh -Dprofile=gateway-worker 로 기동
# ═══════════════════════════════════════════
```

**GW Worker 구동 명령:**

```bash
# ── 방법 1 (권장): 사전 최적화 후 기동 ──────────────────
# 1단계: 불필요한 webapp 제거 및 설정 최적화 (최초 1회)
sh $APIM_HOME/bin/profileSetup.sh -Dprofile=gateway-worker
# Windows: profileSetup.bat -Dprofile=gateway-worker

# 2단계: 서버 기동
sh $APIM_HOME/bin/api-manager.sh -Dprofile=gateway-worker
# Windows: api-manager.bat --run -Dprofile=gateway-worker

# ── 방법 2: 기동과 동시에 최적화 ────────────────────────
sh $APIM_HOME/bin/api-manager.sh --optimize -Dprofile=gateway-worker
# 이미 최적화된 설정을 유지하려면:
# sh $APIM_HOME/bin/api-manager.sh --optimize -Dprofile=gateway-worker --skipConfigOptimization
```

**CP 구동 명령:**

```bash
# ── 방법 1 (권장): 사전 최적화 후 기동 ──────────────────
sh $APIM_HOME/bin/profileSetup.sh -Dprofile=control-plane
sh $APIM_HOME/bin/api-manager.sh -Dprofile=control-plane
# Windows: api-manager.bat --run -Dprofile=control-plane

# ── 방법 2: 기동과 동시에 최적화 ────────────────────────
sh $APIM_HOME/bin/api-manager.sh --optimize -Dprofile=control-plane
```

> **profileSetup.sh란?** 해당 프로파일에 불필요한 webapp(Publisher, DevPortal, Admin, oauth2 등)을  
> 서버 디렉토리에서 물리적으로 제거하고 설정을 최적화한다. 기동 속도와 메모리 사용량을 줄여준다.  
> WSO2 in-place update 후에는 다시 실행이 필요할 수 있다.

---

### 5-5. 로드밸런서 구성

#### CP LB 구성 (Nginx 예시)

```nginx
# /etc/nginx/conf.d/cp-apim.conf

upstream cp_cluster {
    # CP 세션 관리 — 4.x는 DB 기반 세션이므로 일반 round-robin 사용 가능
    # 단, Publisher 작업 중 세션 유지 필요 시 ip_hash 사용
    least_conn;
    server 10.0.0.11:9443 weight=1 max_fails=2 fail_timeout=30s;
    server 10.0.0.12:9443 weight=1 max_fails=2 fail_timeout=30s;
    keepalive 32;
}

server {
    listen 443 ssl;
    server_name cp.example.com;

    ssl_certificate     /etc/ssl/certs/cp.example.com.crt;
    ssl_certificate_key /etc/ssl/private/cp.example.com.key;

    # Publisher / DevPortal / Admin 포털
    location /publisher { proxy_pass https://cp_cluster; }
    location /devportal  { proxy_pass https://cp_cluster; }
    location /admin      { proxy_pass https://cp_cluster; }
    location /carbon     { proxy_pass https://cp_cluster; }

    # OAuth2 / JWKS 엔드포인트 (GW에서 접근)
    location /oauth2     { proxy_pass https://cp_cluster; }
    location /services   { proxy_pass https://cp_cluster; }

    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 90s;
}
```

#### GW LB 구성 (Nginx 예시)

```nginx
# /etc/nginx/conf.d/gw-apim.conf

upstream gw_cluster_https {
    least_conn;
    server 10.0.0.21:8243 weight=1 max_fails=2 fail_timeout=30s;
    server 10.0.0.22:8243 weight=1 max_fails=2 fail_timeout=30s;
    keepalive 64;
}

upstream gw_cluster_http {
    least_conn;
    server 10.0.0.21:8280 weight=1 max_fails=2 fail_timeout=30s;
    server 10.0.0.22:8280 weight=1 max_fails=2 fail_timeout=30s;
    keepalive 64;
}

server {
    listen 8243 ssl;
    server_name gw.example.com;

    ssl_certificate     /etc/ssl/certs/gw.example.com.crt;
    ssl_certificate_key /etc/ssl/private/gw.example.com.key;

    location / {
        proxy_pass         https://gw_cluster_https;
        proxy_http_version 1.1;
        proxy_set_header   Connection "";
        proxy_set_header   Host $host;
        proxy_read_timeout 120s;
    }
}

server {
    listen 8280;
    server_name gw.example.com;

    location / {
        proxy_pass         http://gw_cluster_http;
        proxy_http_version 1.1;
        proxy_set_header   Connection "";
        proxy_set_header   Host $host;
    }
}
```

> **GW는 Stateless**이므로 LB 세션 유지(Sticky Session) 불필요.  
> 단, WebSocket API 사용 시 연결 유지 동안 같은 GW로 고정 필요 → `ip_hash` 적용.

---

### 5-6. 데이터베이스 구성

WSO2 APIM 4.3은 세 가지 DB를 사용한다. 모두 **CP 공유**, GW는 접근하지 않는다.

#### DB 용도별 상세

| DB 이름 | 설정 키 | 주요 테이블 | 역할 |
|---------|---------|------------|------|
| `apim_db` | `database.apim_db` | AM_API, AM_APPLICATION, AM_SUBSCRIPTION, AM_POLICY_* | API 메타데이터, 구독, 정책, 토큰 |
| `shared_db` | `database.shared_db` | UM_USER, UM_ROLE, REG_RESOURCE | 사용자·레지스트리 |

**PostgreSQL 초기 스크립트 경로:**

```
$APIM_HOME/dbscripts/apimgt/
  ├── postgresql.sql          ← apim_db 스키마
$APIM_HOME/dbscripts/
  ├── postgresql.sql          ← shared_db (UM + REG 통합)
```

**DB 스크립트 실행 순서:**

```sql
-- 1. shared_db 생성 및 스키마
CREATE DATABASE shared_db OWNER shared_user;
\c shared_db
\i $APIM_HOME/dbscripts/postgresql.sql

-- 2. apim_db 생성 및 스키마
CREATE DATABASE apim_db OWNER apim_user;
\c apim_db
\i $APIM_HOME/dbscripts/apimgt/postgresql.sql
```

#### 주요 apim_db 테이블

| 테이블 | 내용 |
|--------|------|
| `AM_API` | API 기본 정보 (name, version, context, status) |
| `AM_API_URL_MAPPING` | API 리소스(URI Template) 정의 |
| `AM_API_REVISION` | API Revision 이력 |
| `AM_APPLICATION` | Developer Portal 애플리케이션 |
| `AM_SUBSCRIPTION` | 앱-API 구독 매핑 |
| `AM_APP_KEY_MAPPING` | 앱별 OAuth Consumer Key/Secret |
| `AM_POLICY_APPLICATION` | Application Tier 정책 |
| `AM_POLICY_API` | API Tier 정책 |
| `AM_POLICY_SUBSCRIPTION` | Subscription Tier 정책 |
| `IDN_OAUTH2_ACCESS_TOKEN` | OAuth Access Token 저장 |
| `IDN_OAUTH2_AUTHORIZATION_CODE` | Authorization Code Grant 코드 |
| `IDP_METADATA` | External Key Manager 등록 정보 |

---

### 5-7. 구성 요약 비교표

| 항목 | CP Server | GW Worker |
|------|-----------|-----------|
| 역할 | 관리·정책·토큰 발급 | API 호출 처리 |
| 노드 간 직접 통신 | ❌ 없음 (공유 DB 방식, 4.x부터 Hazelcast 제거) | ❌ 없음 |
| 공유 DB 접속 | ✅ apim_db + shared_db | shared_db만 (최소) |
| AMQP Broker 운영 | ✅ (Qpid 5672) | ❌ 구독자만 |
| Synapse Engine | ❌ (내부 관리용 최소) | ✅ 핵심 엔진 |
| 포털 UI 서비스 | ✅ Publisher / DevPortal / Admin | ❌ |
| 수평 확장 | 일반적으로 2대 고정 (DB 병목) | ✅ 자유롭게 Scale-Out |
| 장애 시 영향 | API 관리·발급 불가 (기존 호출은 GW 캐시로 지속) | 해당 노드 LB 제외, 나머지 정상 |
| 구동 프로파일 | `-Dprofile=control-plane` | `-Dprofile=gateway-worker` |

---

## 6. 공개키·개인키·JWT·서명 검증 심화

### 6-1. 비대칭 암호화(RSA) 원리

RSA는 두 개의 큰 소수의 곱을 인수분해하기 어렵다는 수학적 성질을 기반으로 한다.

```
소수 p = 61,  소수 q = 53  (실제는 2048bit 이상)
n = p × q = 3233               → 공개 (모듈러스)
φ(n) = (p-1)(q-1) = 3120       → 비밀

공개 지수 e = 17  (e와 φ(n)이 서로소)
개인 지수 d: e × d ≡ 1 (mod φ(n)) → d = 2753

Public Key  = (e=17,  n=3233)  → 누구나 알 수 있음
Private Key = (d=2753, n=3233) → 절대 공개 안 함

암호화 (서명): C = M^d mod n  (Private Key 사용)
복호화 (검증): M = C^e mod n  (Public Key 사용)
```

**WSO2 APIM에서는 실제로 2048-bit RSA 키쌍을 사용하며, SHA-256 해시와 결합하여 RS256 알고리즘을 구성한다.**

---

### 6-2. WSO2 Keystore 구조 상세

#### 키스토어 파일 위치

```
$APIM_HOME/repository/resources/security/
  ├── wso2carbon.jks             ← 기본 KeyStore (Private Key + 인증서)
  └── client-truststore.jks      ← TrustStore (신뢰할 Public Key/인증서)
```

#### KeyStore 종류별 역할

| Keystore 종류 | 설정 키 | 주 용도 | 비고 |
|--------------|---------|---------|------|
| **Primary** | `[keystore.primary]` | JWT 서명, 내부 암호화 | 가장 중요, Private Key 포함 |
| **TLS** | `[keystore.tls]` | HTTPS/TLS 인증서 | 클라이언트가 보는 서버 인증서 |
| **Internal** | `[keystore.internal]` | 내부 서비스 간 암호화 | 외부 노출 없음 |
| **TrustStore** | `[truststore]` | 신뢰할 상대방 인증서 저장 | GW가 CP를 신뢰하기 위해 CP 인증서 등록 |

#### 운영 환경 인증서 교체 절차

```bash
# 1. CA 서명 인증서로 새 KeyStore 생성
keytool -genkeypair \
  -alias mycompany \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -keystore new-wso2.jks \
  -validity 730

# 2. CSR 생성 → CA 제출
keytool -certreq -alias mycompany -keystore new-wso2.jks -file mycompany.csr

# 3. CA 서명 인증서 Import
keytool -importcert -alias mycompany -keystore new-wso2.jks -file mycompany.crt

# 4. 공개키를 TrustStore에 추가 (모든 노드)
keytool -importcert -alias mycompany \
  -keystore client-truststore.jks \
  -file mycompany.crt

# 5. deployment.toml 인증서 별칭 변경
# alias = "mycompany"
```

---

### 6-3. JWT 발급 프로세스 상세

#### OAuth2 Grant Type별 흐름

##### Client Credentials Grant (M2M, 서버 간 통신)

```
클라이언트 앱 (Consumer Key + Consumer Secret 보유)
   │
   POST https://cp.example.com:9443/oauth2/token
   Content-Type: application/x-www-form-urlencoded
   Authorization: Basic Base64(consumerKey:consumerSecret)
   Body: grant_type=client_credentials&scope=read:orders
   │
   ▼
CP Key Manager
   │  1. consumerKey로 AM_APP_KEY_MAPPING 조회
   │  2. consumerSecret 검증 (해시 비교)
   │  3. 구독 정보 조회 (AM_SUBSCRIPTION)
   │  4. 스코프 유효성 확인
   │  5. JWT Payload 구성:
   │     {
   │       "sub":  "clientapp@carbon.super",
   │       "iss":  "https://cp.example.com:9443/oauth2/token",
   │       "aud":  "https://cp.example.com/oauth2/token",
   │       "exp":  now + 3600,
   │       "iat":  now,
   │       "jti":  UUID(),
   │       "scope": "read:orders",
   │       "application": { "id": 5, "name": "MyApp", "tier": "Unlimited" },
   │       "subscribedAPIs": [...]
   │     }
   │  6. Primary KeyStore Private Key로 RS256 서명
   │  7. IDN_OAUTH2_ACCESS_TOKEN 테이블에 발행 기록 저장
   │
   응답:
   {
     "access_token":  "eyJhbGci...",   ← JWT
     "token_type":    "Bearer",
     "expires_in":    3600,
     "scope":         "read:orders"
   }
```

##### Authorization Code Grant (사용자 로그인 기반)

```
사용자 브라우저                 클라이언트 앱 서버              CP Key Manager
     │                              │                              │
     │ 로그인 버튼 클릭             │                              │
     │─── Redirect ────────────────▶ https://cp.example.com:9443/oauth2/authorize
     │                              │  ?response_type=code         │
     │                              │  &client_id=consumerKey      │
     │                              │  &redirect_uri=https://...   │
     │                              │  &scope=openid profile       │
     │                              │  &state=random               │
     │◀── 로그인 화면 ──────────────────────────────────────────── │
     │─── 사용자 ID/PW 입력 ────────────────────────────────────▶ │
     │                              │  ← Authorization Code        │
     │─── Redirect 302 ────────────▶                              │
     │   https://app.com/callback?code=AUTH_CODE&state=random
     │                              │                              │
     │                              │─ POST /oauth2/token ────────▶│
     │                              │  grant_type=authorization_code
     │                              │  code=AUTH_CODE              │
     │                              │  redirect_uri=...            │
     │                              │                              │
     │                              │◀─ JWT Access Token + ID Token│
     │◀── 로그인 성공 화면 ─────────│                              │
```

---

### 6-4. JWT 서명 생성 상세 (CP 내부)

```
1단계: Header 구성
  {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "wso2carbon"    ← Primary KeyStore의 alias (JWKS에서 공개키 찾는 키)
  }
  → Base64URL 인코딩 → "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Indzbzky..."

2단계: Payload 구성
  { ... 클레임 데이터 ... }
  → Base64URL 인코딩 → "eyJzdWIiOiJ1c2VyMSIsImlzcyI6Imh0dH..."

3단계: 서명 생성
  signing_input = Base64URL(Header) + "." + Base64URL(Payload)
  
  // SHA-256 해시 계산
  hash = SHA256( signing_input.getBytes("UTF-8") )
  
  // RSA Private Key로 암호화 (PKCS#1 v1.5 패딩)
  signature_bytes = RSA_Decrypt( hash, private_key )
  
  // Base64URL 인코딩
  signature = Base64URL( signature_bytes )

4단계: JWT 조합
  JWT = signing_input + "." + signature
      = eyJhbGci...  .  eyJzdWIi...  .  SflKxwRJ...
         (Header)        (Payload)       (Signature)
```

**실제 Java 코드 레벨 (WSO2 내부 구현 참고):**

```java
// org.wso2.carbon.identity.oauth2.token.JWTTokenGenerator 내부 로직 참고
KeyStore keyStore = KeyStore.getInstance("JKS");
keyStore.load(new FileInputStream("wso2carbon.jks"), "wso2carbon".toCharArray());

PrivateKey privateKey = (PrivateKey) keyStore.getKey("wso2carbon", "wso2carbon".toCharArray());

Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);
signer.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
byte[] signatureBytes = signer.sign();

String jwt = header + "." + payload + "." + Base64.getUrlEncoder()
    .withoutPadding().encodeToString(signatureBytes);
```

---

### 6-5. JWT 서명 검증 체인 (GW 핸들러 체인)

GW는 Synapse Handler Chain을 통해 JWT를 단계적으로 검증한다.

```
HTTP Request 수신 (Synapse PassThrough Transport)
   │
   ▼
APIAuthenticationHandler
   │
   ├─ 1. Authorization 헤더 추출
   │     Bearer eyJhbGci...  or  apikey xxx  or  Basic xxx
   │
   ├─ 2. 토큰 타입 판별
   │     ├─ "eyJ" 로 시작 → JWT 처리 경로
   │     └─ 기타            → Opaque / API Key 처리 경로
   │
   ├─ 3. [JWT] Header 파싱 → kid 추출
   │
   ├─ 4. [JWT] gateway_token_cache 조회 (jti 기준)
   │     ├─ HIT  → 즉시 통과
   │     └─ MISS → 5번으로
   │
   ├─ 5. [JWT] exp(만료 시간) 확인
   │     만료 → HTTP 401 "Access Token Expired"
   │
   ├─ 6. [JWT] JWKS 캐시에서 kid 매칭 → PublicKey 조회
   │     ├─ 캐시 존재 → 바로 사용
   │     └─ 캐시 없음 → GET https://cp.example.com:9443/oauth2/jwks
   │                     응답 파싱 → 공개키 캐시 저장
   │
   ├─ 7. [JWT] 서명 검증 (로컬, 수학 연산)
   │     │
   │     │ 검증 로직:
   │     │   signInput = header + "." + payload (원본 Base64URL)
   │     │   Signature sig = Signature.getInstance("SHA256withRSA")
   │     │   sig.initVerify(publicKey)
   │     │   sig.update(signInput.getBytes("UTF-8"))
   │     │   boolean valid = sig.verify(Base64URL.decode(signaturePart))
   │     │
   │     ├─ false → HTTP 401 "Invalid Signature"
   │     └─ true  → 8번으로
   │
   ├─ 8. [JWT] jti 블랙리스트 확인 (취소된 토큰)
   │     블랙리스트 존재 → HTTP 401 "Access Token Revoked"
   │
   ├─ 9. Payload 클레임 검증
   │     ├─ iss: 허용된 발급자 목록 확인
   │     ├─ subscribedAPIs: 현재 API context 포함 여부
   │     │   미포함 → HTTP 403 "API not subscribed"
   │     ├─ scope: 요청 리소스의 필요 scope 확인
   │     └─ application.tier: Application Throttle 정책 확인
   │
   ├─ 10. 검증 성공 → gateway_token_cache 저장 (TTL: 900초)
   │
   └─ 11. 다음 핸들러로 위임
            │
            ▼
         ThrottleHandler (스로틀링 판단)
            │
            ▼
         APIManagerExtensionHandler (미디에이션 실행)
            │
            ▼
         백엔드 호출
```

---

### 6-6. JWKS Endpoint 상세

JWKS(JSON Web Key Set)는 공개키를 표준 형식(RFC 7517)으로 제공하는 엔드포인트이다.

**요청:**
```http
GET https://cp.example.com:9443/oauth2/jwks
Accept: application/json
```

**응답:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "wso2carbon",
      "n":   "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
      "e":   "AQAB",
      "x5c": ["MIICpDCCAYwCCQDU+pQ4pHgSpDANBg..."]
    }
  ]
}
```

**JWKS 파라미터 설명:**

| 파라미터 | 의미 |
|---------|------|
| `kty` | Key Type — `RSA` |
| `use` | 사용 목적 — `sig` (서명용) / `enc` (암호화용) |
| `alg` | 알고리즘 — `RS256` |
| `kid` | Key ID — JWT Header의 `kid`와 매칭하여 공개키 선택 |
| `n` | RSA 모듈러스 (Base64URL, 공개키의 핵심 값) |
| `e` | RSA 공개 지수 — `AQAB` = 65537 |
| `x5c` | X.509 인증서 체인 (Base64, 선택) |

**GW의 JWKS 캐싱 동작:**

```
GW 시작
   │
   GET /oauth2/jwks → 공개키 파싱 → JWKSCache에 저장
   │
   이후 JWT 요청 시:
   kid 추출 → JWKSCache.get(kid) → 공개키 즉시 사용
   │
   AMQP: org.wso2.apimgt.keymanager 토픽 수신 (키 교체 이벤트)
   │
   JWKSCache 무효화 → GET /oauth2/jwks 재조회 → 캐시 갱신
```

---

### 6-7. 키 교체 (Key Rotation) 프로세스

운영 환경에서 키를 교체해야 할 때의 무중단 절차:

```
[단계 1] 새 키쌍 생성 (wso2carbon.jks에 새 별칭으로 추가)
   keytool -genkeypair -alias newkey2024 -keyalg RSA -keysize 2048 \
     -keystore wso2carbon.jks -validity 730

[단계 2] deployment.toml 변경 없이 JWKS에 두 키 모두 노출
   → WSO2 4.3에서는 KS에 있는 모든 서명 키를 JWKS에 자동 노출
   → 기존 "wso2carbon" 키도 계속 유효 (기존 토큰 검증 가능)

[단계 3] deployment.toml Primary KeyStore alias를 새 키로 변경
   [keystore.primary]
   alias = "newkey2024"
   → 이후 발급되는 토큰은 새 키로 서명

[단계 4] CP 재시작 (Rolling Restart)
   CP-Server2 재시작 → CP-Server1 재시작
   (4.x는 Hazelcast 미사용, 공유 DB 방식이므로 순차 재시작으로 무중단 가능)

[단계 5] AMQP로 GW에 키 변경 이벤트 자동 전파
   → GW: JWKS 재조회 → 새 공개키 캐시에 추가
   → 기존 키도 JWKS에 남아있으므로 기존 토큰도 계속 검증 가능

[단계 6] 기존 토큰 만료 후 구 키 JWKS에서 제거
   (Access Token TTL = 3600초, 최대 1시간 후 안전하게 제거 가능)
```

---

### 6-8. Backend JWT (X-JWT-Assertion)

GW는 API 백엔드(또는 MI)에 호출자 정보를 **새로운 JWT**로 생성하여 전달한다. 이것이 `X-JWT-Assertion` 헤더이다.

```
[원본 Access Token — 클라이언트가 GW에 제출]
eyJhbGciOiJSUzI1NiIsImtpZCI6Indzbzky...
Payload:
  sub:  "user@example.com"
  scope: "read:orders"
  application: { name: "MyApp" }
  ...

           GW 처리
              │
              ▼
[Backend JWT — GW가 새로 생성하여 백엔드에 전달]
X-JWT-Assertion: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Payload:
  {
    "sub":         "user@example.com",
    "iss":         "wso2.org/products/am",
    "http://wso2.org/claims/subscriber": "user@example.com",
    "http://wso2.org/claims/applicationid": "5",
    "http://wso2.org/claims/applicationname": "MyApp",
    "http://wso2.org/claims/applicationtier": "Unlimited",
    "http://wso2.org/claims/apicontext": "/orders/v1",
    "http://wso2.org/claims/version": "v1.0",
    "http://wso2.org/claims/tier": "Gold",
    "http://wso2.org/claims/keytype": "PRODUCTION",
    "http://wso2.org/claims/usertype": "APPLICATION_USER",
    "http://wso2.org/claims/enduser": "user@example.com",
    "http://wso2.org/claims/enduserTenantId": "-1234",
    "http://wso2.org/claims/emailaddress": "user@example.com",
    "exp": 1716000000,
    "iat": 1715996400,
    "jti": "new-uuid-for-backend-jwt"
  }
```

**Backend JWT는 GW의 Primary KeyStore Private Key로 서명** → 백엔드는 GW의 공개키로 검증 가능.

**백엔드에서 X-JWT-Assertion 검증 (Spring Boot 예시):**

```java
// 백엔드 서버에서 X-JWT-Assertion 헤더 검증
String backendJwt = request.getHeader("X-JWT-Assertion");

// GW 공개키 (GW JWKS에서 미리 조회한 키)
PublicKey gwPublicKey = getGWPublicKey("wso2carbon");

Jwts.parserBuilder()
    .setSigningKey(gwPublicKey)
    .requireIssuer("wso2.org/products/am")
    .build()
    .parseClaimsJws(backendJwt);

// 클레임 추출
Claims claims = ...;
String caller = claims.getSubject();
String appName = claims.get("http://wso2.org/claims/applicationname", String.class);
```

---

### 6-9. 전체 보안 흐름 통합 다이어그램

```
[클라이언트]                [GW]                    [CP]
     │                       │                        │
     │ 1. POST /oauth2/token  │                        │
     │────────────────────────────────────────────────▶│
     │                       │  2. JWT 생성             │
     │                       │     Private Key 서명      │
     │◀───────────────────────────────── JWT ──────────│
     │                       │                        │
     │ 3. GET /orders/v1      │                        │
     │   Bearer JWT           │                        │
     │──────────────────────▶│                        │
     │                       │ 4. kid 추출             │
     │                       │ 5. JWKS 캐시 조회       │
     │                       │    (없으면 GET /oauth2/jwks 호출)
     │                       │──────────────────────▶ │
     │                       │◀──── Public Key ──────  │
     │                       │                        │
     │                       │ 6. 서명 검증 (로컬)      │
     │                       │    SHA256withRSA.verify  │
     │                       │                        │
     │                       │ 7. exp/jti/scope 확인   │
     │                       │                        │
     │                       │ 8. gateway_token_cache  │
     │                       │    저장 (TTL 900s)       │
     │                       │                        │
     │                       │ 9. X-JWT-Assertion 생성  │
     │                       │    (새 JWT, GW Private Key 서명)
     │                       │                        │
     │                       │ 10. 백엔드 호출          │
     │                       │─────────────────▶ Backend
     │                       │ X-JWT-Assertion 헤더 포함│
     │                       │◀──────────── 응답 ───── │
     │◀────────── HTTP 응답 ─│                        │
     │                       │                        │
     │ [900초 내 재요청]       │                        │
     │──────────────────────▶│                        │
     │                       │ gateway_token_cache HIT │
     │                       │ CP 호출 없이 즉시 처리   │
     │◀────────── HTTP 응답 ─│                        │
```

---

### 6-10. 토큰 보안 설계 원칙 요약

| 원칙 | 내용 | WSO2 4.3 구현 방식 |
|------|------|-------------------|
| **위변조 방지** | 토큰 내용 변경 시 서명 검증 실패 | RS256 비대칭 서명 |
| **재사용 방지** | 만료 토큰 거부 | `exp` 클레임 + 시간 검증 |
| **즉각 취소** | 취소된 토큰 즉시 무효화 | AMQP `tokenRevocation` 토픽 → GW 블랙리스트 |
| **최소 권한** | 필요한 API만 접근 | `subscribedAPIs` 클레임 검증 |
| **비밀 보호** | Private Key 탈취 방지 | JKS 파일 접근 제한, 운영 CA 인증서 사용 |
| **성능** | 매 요청 CP 호출 방지 | `gateway_token_cache` TTL 900초 |
| **HA** | CP 장애 시에도 토큰 검증 | 캐시 HIT 시 CP 불필요 + JWKS 로컬 캐시 |

---

## 참고: 전체 포트 및 프로토콜 정리 (확장판)

| 포트 | 프로토콜 | 방향 | 구간 | 용도 | 방화벽 필요 |
|------|----------|------|------|------|-----------|
| 9443 | HTTPS | GW → CP | 토큰 검증, API Pull, JWKS | ✅ |
| 9443 | HTTPS | 브라우저 → CP | Publisher / DevPortal / Admin | ✅ |
| 5672 | AMQP | GW → CP | 이벤트 구독 (API배포·스로틀링·취소) | ✅ |
| 9611 | Thrift TCP | GW → CP | 스로틀링 카운터 (평문) | ✅ |
| 9711 | Thrift SSL | GW → CP | 스로틀링 카운터 (암호화) | ✅ |
| 8243 | HTTPS | Client → GW LB | API 호출 (HTTPS) | ✅ |
| 8280 | HTTP | Client → GW LB | API 호출 (내부망 전용) | 내부망만 |
| 9099 | WS | Client → GW LB | WebSocket API | 선택적 |
| 8099 | WSS | Client → GW LB | WebSocket API (TLS) | 선택적 |
| 5432 | PostgreSQL | CP → DB | DB 접속 | ✅ (DB망) |
| 8672 | AMQP TLS | GW → CP | Qpid SSL 연결 (선택) | ✅ (CP↔GW망) |

---

## 7. 내부 Repository DB 테이블 상세

WSO2 APIM 4.3은 두 개의 관계형 데이터베이스를 사용한다.

| DB 식별자 | deployment.toml 키 | 기본 DB명 | 역할 |
|----------|-------------------|----------|------|
| API Manager DB | `[database.apim_db]` | `WSO2AM_DB` | API·구독·토큰·정책·GW 아티팩트 등 APIM 핵심 데이터 |
| Shared DB | `[database.shared_db]` | `WSO2SHARED_DB` | 사용자·역할·레지스트리·세션·Identity 공통 데이터 |

> 테이블 DDL 원본 위치: `$APIM_HOME/dbscripts/` (PostgreSQL: `apimgt/postgresql.sql`, `postgresql.sql`)

---

### 7-1. apim_db (WSO2AM_DB) — API Manager 핵심 DB

#### 7-1-1. API 정의 관련

##### AM_API
API 메타데이터 마스터 테이블. 배포된 모든 API의 기본 정보가 저장된다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `API_ID` | INT (PK) | 내부 자동증가 ID |
| `API_UUID` | VARCHAR | 외부 노출용 UUID (REST API에서 사용) |
| `API_PROVIDER` | VARCHAR | API 생성자 (예: `admin`, `user@tenant.com`) |
| `API_NAME` | VARCHAR | API 이름 |
| `API_VERSION` | VARCHAR | 버전 문자열 (예: `v1`, `1.0.0`) |
| `CONTEXT` | VARCHAR | 실제 요청 경로 (예: `/orders/v1`) |
| `CONTEXT_TEMPLATE` | VARCHAR | 멀티테넌시용 컨텍스트 템플릿 (예: `/t/{tenant}/orders`) |
| `API_TIER` | VARCHAR | API 레벨 스로틀링 정책 이름 |
| `API_TYPE` | VARCHAR | `HTTP`, `GRAPHQL`, `WS`, `SSE`, `WEBHOOK`, `ASYNC` |
| `STATUS` | VARCHAR | `CREATED`, `PUBLISHED`, `DEPRECATED`, `RETIRED`, `BLOCKED`, `PROTOTYPED` |
| `CREATED_BY` | VARCHAR | 생성자 |
| `CREATED_TIME` | TIMESTAMP | 생성 시각 |
| `LAST_UPDATED_TIME` | TIMESTAMP | 최종 수정 시각 |
| `REVISION_UUID` | VARCHAR | 현재 활성 리비전 UUID |
| `ORGANIZATION` | VARCHAR | 멀티테넌시 조직 식별자 |

```sql
-- 현재 PUBLISHED 상태인 모든 API 조회
SELECT API_NAME, API_VERSION, CONTEXT, API_TYPE, CREATED_BY
FROM AM_API
WHERE STATUS = 'PUBLISHED'
ORDER BY API_PROVIDER, API_NAME;
```

##### AM_API_URL_MAPPING
API의 리소스(Endpoint + HTTP Method) 정의. GW 라우팅 규칙의 원본이 된다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `URL_MAPPING_ID` | INT (PK) | 자동증가 ID |
| `API_ID` | INT (FK → AM_API) | 소속 API |
| `HTTP_METHOD` | VARCHAR | `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS` |
| `URL_PATTERN` | VARCHAR | URI 패턴 (예: `/orders/{id}`) |
| `AUTH_SCHEME` | VARCHAR | `Application & Application User`, `Application`, `Application User`, `None` |
| `THROTTLING_TIER` | VARCHAR | 리소스별 스로틀링 정책 |
| `MEDIATION_SCRIPT` | LONGTEXT | 인라인 미디에이션 스크립트 (사용 시) |
| `REVISION_UUID` | VARCHAR | 해당 리비전 UUID |

##### AM_API_PRODUCT_MAPPING
API Product에 포함된 개별 API 리소스 매핑.

| 컬럼 | 설명 |
|------|------|
| `API_PRODUCT_MAPPING_ID` | PK |
| `API_ID` | 원본 API ID |
| `URL_MAPPING_ID` | 포함된 리소스 ID |
| `REVISION_UUID` | 리비전 UUID |

##### AM_REVISION
API 리비전(버전 스냅샷) 이력.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | INT (PK) | 자동증가 ID |
| `API_UUID` | VARCHAR (FK → AM_API.API_UUID) | 대상 API UUID |
| `REVISION_UUID` | VARCHAR | 리비전 고유 UUID |
| `DESCRIPTION` | VARCHAR | 리비전 설명 메모 |
| `CREATED_BY` | VARCHAR | 생성자 |
| `CREATED_TIME` | TIMESTAMP | 생성 시각 |

---

#### 7-1-2. Gateway 아티팩트 관련

GW Worker가 CP에서 Pull하는 실제 아티팩트 데이터가 저장된다.

##### AM_GW_PUBLISHED_API_DETAILS
GW 환경별 게시된 API 정보 인덱스.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `API_ID` | VARCHAR (PK) | API UUID |
| `TENANT_DOMAIN` | VARCHAR | 테넌트 도메인 |
| `API_NAME` | VARCHAR | API 이름 |
| `API_VERSION` | VARCHAR | API 버전 |
| `API_PROVIDER` | VARCHAR | 제공자 |

##### AM_GW_API_ARTIFACTS
GW로 전달되는 실제 아티팩트(Synapse 설정 XML 등) 저장.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `API_ID` | VARCHAR (FK) | API UUID |
| `REVISION_ID` | VARCHAR | 리비전 UUID |
| `ARTIFACT` | LONGTEXT / BYTEA | JSON 직렬화된 전체 아티팩트 (Synapse 설정 포함) |
| `TIME_STAMP` | BIGINT | 저장 시각 (epoch ms) |
| `GATEWAY_INSTRUCTION` | VARCHAR | `PUBLISH` 또는 `REMOVE` |
| `GATEWAY_LABEL` | VARCHAR | 대상 Gateway Label (예: `Default`) |
| `TYPE` | VARCHAR | `API`, `API_PRODUCT` |

> GW Worker가 `service_url`(REST API)로 Pull할 때 이 테이블의 `ARTIFACT` 컬럼을 읽어 Synapse 엔진에 로딩한다.

##### AM_GW_API_DEPLOYMENTS
API가 어떤 GW 환경·리비전에 배포되었는지 추적.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `NAME` | VARCHAR | API 이름 |
| `VERSION` | VARCHAR | API 버전 |
| `TENANT_DOMAIN` | VARCHAR | 테넌트 |
| `REVISION_ID` | VARCHAR | 배포된 리비전 UUID |
| `LABEL` | VARCHAR | 배포된 Gateway Label |
| `VHOST` | VARCHAR | 가상 호스트 (멀티 GW 환경에서 사용) |

---

#### 7-1-3. 구독·애플리케이션 관련

##### AM_SUBSCRIBER
API 소비자(구독자) 계정 정보.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `SUBSCRIBER_ID` | INT (PK) | 자동증가 ID |
| `USER_ID` | VARCHAR | 사용자 이름 (shared_db UM_USER 연계) |
| `TENANT_ID` | INT | 테넌트 ID |
| `EMAIL_ADDRESS` | VARCHAR | 이메일 |
| `DATE_SUBSCRIBED` | TIMESTAMP | 구독자 등록 시각 |
| `CREATED_BY` | VARCHAR | 생성자 |
| `CREATED_TIME` | TIMESTAMP | 생성 시각 |
| `UPDATED_TIME` | TIMESTAMP | 수정 시각 |

##### AM_APPLICATION
소비자가 생성하는 Application (OAuth 클라이언트 단위).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `APPLICATION_ID` | INT (PK) | 자동증가 ID |
| `NAME` | VARCHAR | 앱 이름 |
| `SUBSCRIBER_ID` | INT (FK → AM_SUBSCRIBER) | 소유자 |
| `APPLICATION_TIER` | VARCHAR | 앱 레벨 스로틀링 정책 |
| `CALLBACK_URL` | VARCHAR | OAuth 리다이렉트 URI |
| `DESCRIPTION` | VARCHAR | 앱 설명 |
| `APPLICATION_STATUS` | VARCHAR | `CREATED`, `APPROVED`, `REJECTED`, `DELETE_PENDING` |
| `GROUP_ID` | VARCHAR | 그룹 기반 접근 제어용 그룹 ID |
| `CREATED_BY` | VARCHAR | 생성자 |
| `CREATED_TIME` | TIMESTAMP | 생성 시각 |
| `UPDATED_TIME` | TIMESTAMP | 수정 시각 |
| `UUID` | VARCHAR | 외부 노출용 UUID |
| `TOKEN_TYPE` | VARCHAR | `JWT` 또는 `OAUTH` (토큰 형식) |
| `ORGANIZATION` | VARCHAR | 조직 식별자 |

##### AM_SUBSCRIPTION
Application ↔ API 구독 연결 테이블. 토큰 검증 시 이 레코드 존재 여부를 확인한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `SUBSCRIPTION_ID` | INT (PK) | 자동증가 ID |
| `TIER_ID` | VARCHAR | 구독 시 선택한 스로틀링 티어 |
| `TIER_ID_PENDING` | VARCHAR | 티어 변경 요청 중인 경우 새 티어 임시 저장 |
| `API_ID` | INT (FK → AM_API) | 구독 대상 API |
| `APPLICATION_ID` | INT (FK → AM_APPLICATION) | 구독 주체 앱 |
| `SUB_STATUS` | VARCHAR | `UNBLOCKED`, `BLOCKED`, `ON_HOLD`, `REJECTED`, `TIER_UPDATE_PENDING`, `DELETE_PENDING` |
| `SUBS_CREATE_STATE` | VARCHAR | `SUBSCRIBE` / `UNSUBSCRIBE` |
| `CREATED_BY` | VARCHAR | 생성자 |
| `CREATED_TIME` | TIMESTAMP | 생성 시각 |
| `UPDATED_TIME` | TIMESTAMP | 수정 시각 |
| `UUID` | VARCHAR | 외부 UUID |
| `ORGANIZATION` | VARCHAR | 조직 식별자 |

```sql
-- 특정 Application의 구독 API 목록
SELECT a.API_NAME, a.API_VERSION, a.CONTEXT, s.TIER_ID, s.SUB_STATUS
FROM AM_SUBSCRIPTION s
JOIN AM_API a ON s.API_ID = a.API_ID
JOIN AM_APPLICATION app ON s.APPLICATION_ID = app.APPLICATION_ID
WHERE app.NAME = 'MyApp'
  AND s.SUB_STATUS = 'UNBLOCKED';
```

##### AM_APPLICATION_KEY_MAPPING
Application과 OAuth Consumer Key(Client ID)의 연결. 토큰 발급 시 이 매핑을 통해 앱을 식별한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `APPLICATION_ID` | INT (FK → AM_APPLICATION) | 연결된 앱 |
| `CONSUMER_KEY` | VARCHAR | OAuth Client ID (IDN_OAUTH_CONSUMER_APPS 연계) |
| `KEY_TYPE` | VARCHAR | `PRODUCTION` 또는 `SANDBOX` |
| `STATE` | VARCHAR | `COMPLETED`, `APPROVED` |
| `CREATE_MODE` | VARCHAR | `CREATED` / `MAPPED` (외부 KM 매핑 시) |
| `KEY_MANAGER` | VARCHAR (FK → AM_KEY_MANAGER.UUID) | 토큰 발급에 사용된 KM |
| `UUID` | VARCHAR | 매핑 UUID |
| `APP_INFO` | TEXT | 추가 메타정보 (JSON) |

##### AM_APPLICATION_ATTRIBUTES
Application 확장 속성(커스텀 필드) 저장.

| 컬럼 | 설명 |
|------|------|
| `APPLICATION_ID` | FK → AM_APPLICATION |
| `NAME` | 속성 키 이름 |
| `APP_ATTRIBUTE` | 속성 값 |
| `TENANT_ID` | 테넌트 |

##### AM_APPLICATION_REGISTRATION
Application이 Key Manager에 등록된 정보(Client Credentials 발급 이력).

| 컬럼 | 설명 |
|------|------|
| `REG_ID` | PK |
| `SUBSCRIBER_ID` | FK → AM_SUBSCRIBER |
| `WF_REF` | 워크플로 참조 ID |
| `APP_ID` | FK → AM_APPLICATION |
| `TOKEN_TYPE` | `PRODUCTION` / `SANDBOX` |
| `TOKEN_SCOPE` | 요청된 OAuth 스코프 |
| `INPUTS` | 추가 입력 JSON |
| `ALLOWED_DOMAINS` | 허용 도메인 |
| `VALIDITY_PERIOD` | 토큰 유효 기간 (초) |
| `KEY_MANAGER` | KM UUID |
| `UNIQUE_ID` | 고유 식별자 |

---

#### 7-1-4. OAuth 토큰 관련

> WSO2 APIM 4.3에서는 Identity 관련 테이블(`IDN_` 접두사)이 **apim_db 내에 함께** 생성된다.  
> 별도 identity DB를 구성하지 않는 한, 아래 테이블 모두 `WSO2AM_DB` 안에 존재한다.

##### IDN_OAUTH_CONSUMER_APPS
OAuth 클라이언트 애플리케이션 등록 정보 (= Client ID / Client Secret 저장).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | INT (PK) | 자동증가 ID |
| `CONSUMER_KEY` | VARCHAR | Client ID (공개값, 토큰 요청 시 전송) |
| `CONSUMER_SECRET` | VARCHAR | Client Secret (해시 저장) |
| `USERNAME` | VARCHAR | 앱 소유자 |
| `TENANT_ID` | INT | 테넌트 |
| `APP_NAME` | VARCHAR | OAuth 앱 이름 |
| `OAUTH_VERSION` | VARCHAR | `2.0` |
| `CALLBACK_URL` | VARCHAR | 리다이렉트 URI |
| `GRANT_TYPES` | VARCHAR | 허용된 Grant Type (공백 구분, 예: `client_credentials password`) |
| `PKCE_MANDATORY` | CHAR(1) | PKCE 필수 여부 |
| `USER_ACCESS_TOKEN_EXPIRE_TIME` | BIGINT | 사용자 액세스 토큰 TTL (ms) |
| `APP_ACCESS_TOKEN_EXPIRE_TIME` | BIGINT | 앱 액세스 토큰 TTL (ms) |
| `REFRESH_TOKEN_EXPIRE_TIME` | BIGINT | 리프레시 토큰 TTL (ms) |
| `ID_TOKEN_EXPIRE_TIME` | BIGINT | ID 토큰 TTL (ms) |
| `PKCE_SUPPORT_PLAIN` | CHAR(1) | PKCE Plain 방식 허용 여부 |

##### IDN_OAUTH2_ACCESS_TOKEN
발급된 OAuth2 액세스 토큰 저장. GW 캐시 미스 시 CP가 이 테이블을 조회한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `TOKEN_ID` | VARCHAR (PK) | 토큰 고유 UUID |
| `ACCESS_TOKEN` | VARCHAR | 액세스 토큰 값 (해시 저장 — 평문 조회 불가) |
| `REFRESH_TOKEN` | VARCHAR | 리프레시 토큰 값 (해시 저장) |
| `CONSUMER_KEY_ID` | INT (FK → IDN_OAUTH_CONSUMER_APPS) | 발급 주체 앱 |
| `AUTHZ_USER` | VARCHAR | 토큰 발급 대상 사용자 |
| `TENANT_ID` | INT | 테넌트 |
| `USER_DOMAIN` | VARCHAR | 사용자 도메인 |
| `USER_TYPE` | VARCHAR | `APPLICATION` (CC Grant) / `APPLICATION_USER` (Password Grant) |
| `GRANT_TYPE` | VARCHAR | `client_credentials`, `password`, `authorization_code`, `refresh_token` 등 |
| `TIME_CREATED` | TIMESTAMP | 발급 시각 |
| `REFRESH_TOKEN_TIME_CREATED` | TIMESTAMP | 리프레시 토큰 발급 시각 |
| `VALIDITY_PERIOD` | BIGINT | 유효 기간 (ms, -1 = 무기한) |
| `REFRESH_TOKEN_VALIDITY_PERIOD` | BIGINT | 리프레시 토큰 유효 기간 (ms) |
| `TOKEN_SCOPE_HASH` | VARCHAR | 스코프 해시 (인덱스용) |
| `TOKEN_STATE` | VARCHAR | **`ACTIVE`**, `EXPIRED`, `INACTIVE`, `REVOKED` |
| `TOKEN_STATE_ID` | VARCHAR | 상태 변경 추적용 UUID |
| `ACCESS_TOKEN_HASH` | VARCHAR | 토큰 해시 (조회 인덱스) |
| `REFRESH_TOKEN_HASH` | VARCHAR | 리프레시 토큰 해시 |
| `IDP_ID` | INT | IdP 식별자 |
| `TOKEN_BINDING_REF` | VARCHAR | 토큰 바인딩 참조 (디바이스 핀닝 등) |

```sql
-- 특정 Client의 활성 토큰 수 조회
SELECT COUNT(*) AS active_tokens
FROM IDN_OAUTH2_ACCESS_TOKEN t
JOIN IDN_OAUTH_CONSUMER_APPS a ON t.CONSUMER_KEY_ID = a.ID
WHERE a.CONSUMER_KEY = '<client_id>'
  AND t.TOKEN_STATE = 'ACTIVE';
```

##### IDN_OAUTH2_ACCESS_TOKEN_SCOPE
토큰별 스코프 저장 (토큰:스코프 = 1:N).

| 컬럼 | 설명 |
|------|------|
| `TOKEN_ID` | FK → IDN_OAUTH2_ACCESS_TOKEN |
| `TOKEN_SCOPE` | 개별 스코프 문자열 (예: `read:orders`) |
| `TENANT_ID` | 테넌트 |

##### IDN_OAUTH2_AUTHORIZATION_CODE
Authorization Code Grant 흐름에서 발급된 임시 코드 저장.

| 컬럼 | 설명 |
|------|------|
| `CODE_ID` | PK UUID |
| `AUTHORIZATION_CODE` | 코드 값 (해시) |
| `CONSUMER_KEY_ID` | FK → IDN_OAUTH_CONSUMER_APPS |
| `CALLBACK_URL` | 리다이렉트 URI |
| `SCOPE` | 요청된 스코프 |
| `AUTHZ_USER` | 인증된 사용자 |
| `TIME_CREATED` | 발급 시각 |
| `VALIDITY_PERIOD` | 유효 기간 (ms, 보통 30,000 = 30초) |
| `STATE` | `ACTIVE`, `INACTIVE` |
| `TOKEN_ID` | 교환된 토큰 ID (교환 후 채워짐) |
| `PKCE_CODE_CHALLENGE` | PKCE code_challenge 값 |
| `PKCE_CODE_CHALLENGE_METHOD` | `S256` / `plain` |

---

#### 7-1-5. 스로틀링·정책 관련

##### AM_POLICY_SUBSCRIPTION
구독 티어(Subscription Tier) 정책 정의.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `POLICY_ID` | INT (PK) | 자동증가 ID |
| `NAME` | VARCHAR | 정책 이름 (예: `Gold`, `Silver`, `Unlimited`) |
| `DISPLAY_NAME` | VARCHAR | UI 표시명 |
| `TENANT_ID` | INT | 테넌트 |
| `DESCRIPTION` | VARCHAR | 설명 |
| `DEFAULT_QUOTA_TYPE` | VARCHAR | `requestCount` / `bandwidthVolume` |
| `DEFAULT_QUOTA` | INT | 허용 요청 수 또는 대역폭 |
| `DEFAULT_QUOTA_UNIT` | VARCHAR | 대역폭 단위 (`KB`, `MB`) |
| `DEFAULT_UNIT_TIME` | INT | 시간 단위 수치 (예: `1`) |
| `DEFAULT_TIME_UNIT` | VARCHAR | `min`, `hour`, `day`, `month` |
| `RATE_LIMIT_COUNT` | INT | 버스트 제한 카운트 |
| `RATE_LIMIT_TIME_UNIT` | VARCHAR | 버스트 시간 단위 |
| `IS_DEPLOYED` | TINYINT | GW 배포 여부 |
| `STOP_ON_QUOTA_REACH` | TINYINT | 한도 초과 시 즉시 차단 여부 (false: 허용 후 과금) |
| `BILLING_PLAN` | VARCHAR | `FREE`, `COMMERCIAL` |
| `UUID` | VARCHAR | 외부 UUID |
| `CONNECTIONS_COUNT` | INT | 동시 연결 수 제한 |

##### AM_POLICY_APPLICATION
애플리케이션 레벨 정책 (AM_POLICY_SUBSCRIPTION과 동일 구조).

| 주요 컬럼 | 설명 |
|----------|------|
| `NAME` | 정책 이름 (예: `50PerMin`, `Unlimited`) |
| `DEFAULT_QUOTA` | 허용 요청 수 |
| `DEFAULT_TIME_UNIT` | 시간 단위 |

##### AM_POLICY_API
API 레벨 정책 (리소스 레벨 스로틀링 포함).

| 주요 컬럼 | 설명 |
|----------|------|
| `NAME` | 정책 이름 |
| `DEFAULT_QUOTA` | 허용 요청 수 |
| `APPLICABLE_LEVEL` | `api` / `resource` |

##### AM_CUSTOM_POLICY
Siddhi CEP 쿼리 기반 커스텀 스로틀링 정책.

| 컬럼 | 설명 |
|------|------|
| `POLICY_ID` | PK |
| `NAME` | 정책 이름 |
| `SIDDHI_QUERY` | 실제 Siddhi QL 쿼리 문자열 |
| `IS_DEPLOYED` | TM 배포 여부 |
| `TENANT_ID` | 테넌트 |

##### AM_BLOCK_CONDITIONS
IP 차단, 토큰 블랙리스트, API 차단 등 블로킹 조건 저장.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `CONDITION_ID` | INT (PK) | 자동증가 ID |
| `TYPE` | VARCHAR | `API`, `APPLICATION`, `IP`, `IPRANGE`, `USER`, `CUSTOM` |
| `VALUE` | VARCHAR | 차단 대상 값 (IP, API 컨텍스트, 사용자명 등) |
| `ENABLED` | TINYINT | 활성화 여부 |
| `DOMAIN` | VARCHAR | 테넌트 도메인 |
| `UUID` | VARCHAR | 외부 UUID |

> GW Worker는 이 테이블 변경 시 `throttle_decision_endpoints` JMS를 통해 즉시 통보받고 메모리 블랙리스트를 갱신한다.

---

#### 7-1-6. Key Manager 관련

##### AM_KEY_MANAGER
등록된 Key Manager 설정 저장 (Resident KM + 외부 KM 포함).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `UUID` | VARCHAR (PK) | KM 고유 UUID |
| `NAME` | VARCHAR | KM 식별 이름 (예: `Resident Key Manager`) |
| `DISPLAY_NAME` | VARCHAR | UI 표시명 |
| `DESCRIPTION` | VARCHAR | 설명 |
| `TYPE` | VARCHAR | `default` (내장), `IS` (WSO2 IS), `custom` |
| `CONFIGURATION` | LONGTEXT | KM 설정 JSON (endpoint URL, 인증 방식 등) |
| `ENABLED` | TINYINT | 활성화 여부 |
| `TENANT_DOMAIN` | VARCHAR | 적용 테넌트 |
| `TOKEN_TYPE` | VARCHAR | `DIRECT` (토큰 직접 발급) / `EXCHANGED` (토큰 교환) |
| `EXTERNAL_REFERENCE_ID` | VARCHAR | 외부 시스템 연계 ID |

---

#### 7-1-7. GW 환경·레이블 관련

##### AM_GATEWAY_ENVIRONMENT
Publisher에서 API 배포 대상으로 선택 가능한 GW 환경 목록.

| 컬럼 | 설명 |
|------|------|
| `ID` | PK |
| `UUID` | 환경 UUID |
| `NAME` | 환경 이름 (예: `Default`, `Production`) |
| `DISPLAY_NAME` | UI 표시명 |
| `DESCRIPTION` | 설명 |
| `PROVIDER` | `wso2` (내장) / `external` |
| `TENANT_DOMAIN` | 테넌트 |

##### AM_LABELS
GW 레이블(Label) 정의. `AM_GW_API_ARTIFACTS`의 `GATEWAY_LABEL`과 연결된다.

| 컬럼 | 설명 |
|------|------|
| `LABEL_ID` | PK |
| `ACCESS_URL` | GW 접근 URL |
| `NAME` | 레이블 이름 (예: `Default`) |
| `DESCRIPTION` | 설명 |
| `TENANT_DOMAIN` | 테넌트 |

---

#### 7-1-8. 워크플로·알림 관련

##### AM_WORKFLOWS
구독 승인, 앱 등록 등 워크플로 요청 이력.

| 컬럼 | 설명 |
|------|------|
| `WF_ID` | PK |
| `WF_REFERENCE` | 외부 참조 ID |
| `WF_TYPE` | `AM_APPLICATION_CREATION`, `AM_SUBSCRIPTION_CREATION`, `AM_USER_SIGNUP` 등 |
| `WF_STATUS` | `CREATED`, `APPROVED`, `REJECTED` |
| `WF_CREATED_TIME` | 요청 시각 |
| `WF_UPDATED_TIME` | 처리 시각 |
| `WF_STATUS_DESC` | 처리 사유 |
| `TENANT_ID` | 테넌트 |
| `WF_EXTERNAL_REF` | 외부 워크플로 시스템 참조 키 |

##### AM_ALERT_TYPES
API Analytics 알림 유형 정의.

| 컬럼 | 설명 |
|------|------|
| `ALERT_TYPE_ID` | PK |
| `ALERT_TYPE_NAME` | 알림 유형 이름 (예: `AbnormalRequestCount`) |
| `CUSTOMER_NAME` | 알림 대상 |

---

### 7-2. shared_db (WSO2SHARED_DB) — 공유 시스템 DB

`shared_db`는 **사용자 관리(UM_)**, **레지스트리(REG_)**, **Identity/세션(IDN_)** 세 영역으로 구성된다.

---

#### 7-2-1. 사용자 관리 (UM_ 접두사)

##### UM_USER
로컬 사용자 계정 저장. LDAP/AD 연동 시에는 사용되지 않을 수 있다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `UM_ID` | INT (PK) | 자동증가 ID |
| `UM_USER_NAME` | VARCHAR | 사용자명 |
| `UM_USER_PASSWORD` | VARCHAR | 해시된 비밀번호 |
| `UM_SALT_VALUE` | VARCHAR | 비밀번호 해시 Salt |
| `UM_REQUIRE_CHANGE` | TINYINT | 다음 로그인 시 비밀번호 변경 강제 |
| `UM_CHANGED_TIME` | TIMESTAMP | 비밀번호 변경 시각 |
| `UM_TENANT_ID` | INT | 소속 테넌트 |

##### UM_ROLE
역할(Role) 정의.

| 컬럼 | 설명 |
|------|------|
| `UM_ID` | PK |
| `UM_ROLE_NAME` | 역할 이름 (예: `admin`, `Internal/publisher`, `Internal/subscriber`) |
| `UM_TENANT_ID` | 테넌트 |
| `UM_SHARED_ROLE` | 테넌트 공유 역할 여부 |

> WSO2 APIM 주요 내장 역할: `Internal/publisher`, `Internal/subscriber`, `Internal/creator`, `Internal/devops`, `Internal/analytics`

##### UM_USER_ROLE
사용자-역할 매핑.

| 컬럼 | 설명 |
|------|------|
| `UM_ID` | PK |
| `UM_USER_ID` | FK → UM_USER |
| `UM_ROLE_ID` | FK → UM_ROLE |
| `UM_TENANT_ID` | 테넌트 |

##### UM_PERMISSION
리소스 경로 기반 권한 정의.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `UM_ID` | INT (PK) | 자동증가 ID |
| `UM_RESOURCE_ID` | VARCHAR | 권한 경로 (예: `/permission/admin/manage/api/publish`) |
| `UM_ACTION` | VARCHAR | 행위 (예: `ui.execute`, `get`, `add`) |
| `UM_TENANT_ID` | INT | 테넌트 |

##### UM_ROLE_PERMISSION
역할-권한 매핑.

| 컬럼 | 설명 |
|------|------|
| `UM_ROLE_ID` | FK → UM_ROLE |
| `UM_PERMISSION_ID` | FK → UM_PERMISSION |
| `UM_IS_ALLOWED` | 허용(1) / 거부(0) |
| `UM_TENANT_ID` | 테넌트 |

##### UM_TENANT
멀티테넌시 테넌트 정보.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `UM_ID` | INT (PK) | 테넌트 내부 ID |
| `UM_DOMAIN_NAME` | VARCHAR | 테넌트 도메인 (예: `acme.com`) |
| `UM_EMAIL` | VARCHAR | 테넌트 관리자 이메일 |
| `UM_ACTIVE` | TINYINT | 활성 여부 |
| `UM_CREATED_DATE` | TIMESTAMP | 생성 시각 |
| `UM_USER_CONFIG` | LONGTEXT | 테넌트 사용자 설정 XML |

##### UM_USER_ATTRIBUTE
사용자 확장 속성 (LDAP claim 매핑값 포함).

| 컬럼 | 설명 |
|------|------|
| `UM_ID` | PK |
| `UM_ATTR_NAME` | 속성 이름 (예: `email`, `givenName`, `http://wso2.org/claims/emailaddress`) |
| `UM_ATTR_VALUE` | 속성 값 |
| `UM_PROFILE_ID` | 프로파일 ID (보통 `default`) |
| `UM_USER_ID` | FK → UM_USER |
| `UM_TENANT_ID` | 테넌트 |

---

#### 7-2-2. 레지스트리 (REG_ 접두사)

WSO2 Carbon 레지스트리는 **설정 파일, 스키마, 정책 문서, 테넌트 테마** 등을 DB에 파일시스템처럼 저장하는 계층이다.

##### REG_PATH
레지스트리 경로 계층 구조 (디렉토리 트리).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `REG_PATH_ID` | INT (PK) | 경로 ID |
| `REG_PATH_VALUE` | VARCHAR | 경로 문자열 (예: `/_system/governance/apimgt/applicationdata`) |
| `REG_PATH_PARENT_ID` | INT | 부모 경로 ID (루트는 NULL) |
| `REG_TENANT_ID` | INT | 테넌트 |

##### REG_RESOURCE
레지스트리 리소스(파일) 메타데이터.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `REG_PATH_ID` | INT (FK → REG_PATH) | 소속 경로 |
| `REG_NAME` | VARCHAR | 리소스 이름 (파일명, NULL이면 컬렉션) |
| `REG_VERSION` | INT (PK 일부) | 버전 번호 |
| `REG_MEDIA_TYPE` | VARCHAR | MIME 타입 (예: `application/json`, `application/wsdl+xml`) |
| `REG_CREATOR` | VARCHAR | 생성자 |
| `REG_CREATED_TIME` | TIMESTAMP | 생성 시각 |
| `REG_LAST_UPDATOR` | VARCHAR | 최종 수정자 |
| `REG_LAST_UPDATED_TIME` | TIMESTAMP | 최종 수정 시각 |
| `REG_CONTENT_ID` | INT (FK → REG_CONTENT) | 실제 바이너리 데이터 참조 |
| `REG_TENANT_ID` | INT | 테넌트 |
| `REG_UUID` | VARCHAR | 리소스 UUID |

##### REG_CONTENT
리소스의 실제 바이너리/텍스트 데이터.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `REG_CONTENT_ID` | INT (PK) | 자동증가 ID |
| `REG_CONTENT_DATA` | BLOB / BYTEA | 파일 내용 (XML, JSON, 이미지 등) |
| `REG_TENANT_ID` | INT | 테넌트 |

> APIM 관련 레지스트리 주요 경로:
> - `/_system/governance/apimgt/` — API 관련 설정, 정책 문서
> - `/_system/config/apimgt/` — 시스템 설정
> - `/_system/local/` — 로컬(테넌트별) 설정

##### REG_PROPERTY
리소스 속성(태그/메타데이터) 저장.

| 컬럼 | 설명 |
|------|------|
| `REG_PROPERTY_ID` | PK |
| `REG_NAME` | 속성 이름 |
| `REG_VALUE` | 속성 값 |
| `REG_TENANT_ID` | 테넌트 |

##### REG_LOG
레지스트리 변경 감사 로그.

| 컬럼 | 설명 |
|------|------|
| `REG_LOG_ID` | PK |
| `REG_PATH` | 변경된 경로 |
| `REG_USER_NAME` | 작업자 |
| `REG_LOGGED_TIME` | 시각 |
| `REG_ACTION` | `1`=ADD, `2`=UPDATE, `3`=DELETE, `7`=TAG, `8`=COMMENT |
| `REG_TENANT_ID` | 테넌트 |

---

#### 7-2-3. Identity / 세션 (IDN_ 접두사, shared_db)

> `IDN_` 테이블 중 **OAuth 토큰 관련**은 apim_db에, **SSO 세션·클레임 관련**은 shared_db에 위치한다.

##### IDN_AUTH_SESSION_STORE
SSO 세션 저장 (브라우저 기반 로그인 세션).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `SESSION_ID` | VARCHAR (PK) | 세션 고유 ID |
| `SESSION_TYPE` | VARCHAR | 세션 유형 |
| `OPERATION` | VARCHAR | `STORE`, `DELETE` |
| `SESSION_OBJECT` | BLOB | 직렬화된 세션 객체 |
| `TIME_CREATED` | BIGINT | 생성 시각 (epoch ms) |
| `TENANT_ID` | INT | 테넌트 |
| `EXPIRY_TIME` | BIGINT | 만료 시각 (epoch ms) |

> 세션 만료 레코드는 `wso2_session_cleanup_task`가 주기적으로 삭제한다.

##### IDN_CLAIM_DIALECT
Claim 방언(dialect) 정의. OIDC, SAML2, 내부 claim 간 매핑 기준점.

| 컬럼 | 설명 |
|------|------|
| `ID` | PK |
| `DIALECT_URI` | Claim Dialect URI (예: `http://wso2.org/claims`, `http://schemas.xmlsoap.org/ws/2005/05/identity`) |
| `TENANT_ID` | 테넌트 |

##### IDN_CLAIM
각 Claim 정의.

| 컬럼 | 설명 |
|------|------|
| `ID` | PK |
| `DIALECT_ID` | FK → IDN_CLAIM_DIALECT |
| `CLAIM_URI` | Claim URI (예: `http://wso2.org/claims/emailaddress`) |
| `TENANT_ID` | 테넌트 |

##### IDN_CLAIM_MAPPED_ATTRIBUTE
Claim과 LDAP/AD 속성 간 매핑.

| 컬럼 | 설명 |
|------|------|
| `ID` | PK |
| `USER_STORE_DOMAIN_NAME` | 도메인 (예: `PRIMARY`) |
| `ATTRIBUTE_NAME` | LDAP 속성 (예: `mail`, `givenName`) |
| `CLAIM_ID` | FK → IDN_CLAIM |
| `TENANT_ID` | 테넌트 |

---

### 7-3. DB 간 관계 요약

```
shared_db (WSO2SHARED_DB)              apim_db (WSO2AM_DB)
┌──────────────────────────────┐       ┌───────────────────────────────────────┐
│ UM_USER                      │       │ AM_SUBSCRIBER                         │
│   UM_USER_NAME ──────────────┼──────▶│   USER_ID (문자열 참조, FK 아님)       │
│                              │       │   └─▶ AM_APPLICATION                  │
│ UM_ROLE                      │       │         └─▶ AM_SUBSCRIPTION            │
│   Internal/publisher 등      │       │               └─▶ AM_API               │
│                              │       │                                        │
│ REG_RESOURCE                 │       │ IDN_OAUTH_CONSUMER_APPS               │
│   API 정책 문서 저장          │       │   CONSUMER_KEY ──▶ AM_APPLICATION      │
│   테넌트 테마                 │       │                    _KEY_MAPPING         │
│                              │       │                                        │
│ IDN_AUTH_SESSION_STORE       │       │ IDN_OAUTH2_ACCESS_TOKEN               │
│   Publisher 로그인 세션       │       │   (토큰 발급·취소 이력)                │
│                              │       │                                        │
│ IDN_CLAIM_DIALECT            │       │ AM_GW_API_ARTIFACTS                   │
│   OIDC/SAML claim 매핑       │       │   (GW 동기화 아티팩트)                 │
└──────────────────────────────┘       └───────────────────────────────────────┘
```

> **주의:** 두 DB 사이에는 실제 외래 키 제약(FK constraint)이 없다.  
> `UM_USER.UM_USER_NAME` → `AM_SUBSCRIBER.USER_ID` 연결은 **애플리케이션 레벨 논리적 참조**다.

---

### 7-4. 주요 운영 쿼리 모음

```sql
-- ① 현재 배포된 API 목록 (GW 아티팩트 기준)
SELECT a.API_NAME, a.API_VERSION, a.CONTEXT, d.LABEL, d.REVISION_ID
FROM AM_GW_API_DEPLOYMENTS d
JOIN AM_API a ON d.NAME = a.API_NAME AND d.VERSION = a.API_VERSION
ORDER BY a.API_NAME;

-- ② 특정 API의 활성 구독 수 및 앱 목록
SELECT app.NAME AS app_name, app.APPLICATION_STATUS,
       s.TIER_ID, s.SUB_STATUS, sub.USER_ID
FROM AM_SUBSCRIPTION s
JOIN AM_APPLICATION app ON s.APPLICATION_ID = app.APPLICATION_ID
JOIN AM_SUBSCRIBER sub ON app.SUBSCRIBER_ID = sub.SUBSCRIBER_ID
JOIN AM_API a ON s.API_ID = a.API_ID
WHERE a.API_NAME = 'OrderAPI'
  AND a.API_VERSION = 'v1'
  AND s.SUB_STATUS = 'UNBLOCKED';

-- ③ 만료되지 않은 활성 토큰 수 (앱별)
SELECT ca.APP_NAME, COUNT(*) AS active_token_count
FROM IDN_OAUTH2_ACCESS_TOKEN t
JOIN IDN_OAUTH_CONSUMER_APPS ca ON t.CONSUMER_KEY_ID = ca.ID
WHERE t.TOKEN_STATE = 'ACTIVE'
  AND (t.TIME_CREATED + (t.VALIDITY_PERIOD / 1000) * INTERVAL '1 second') > NOW()
GROUP BY ca.APP_NAME
ORDER BY active_token_count DESC;

-- ④ 최근 24시간 내 취소된 토큰 목록
SELECT ca.APP_NAME, t.AUTHZ_USER, t.GRANT_TYPE,
       t.TIME_CREATED, t.TOKEN_STATE
FROM IDN_OAUTH2_ACCESS_TOKEN t
JOIN IDN_OAUTH_CONSUMER_APPS ca ON t.CONSUMER_KEY_ID = ca.ID
WHERE t.TOKEN_STATE = 'REVOKED'
  AND t.TIME_CREATED > NOW() - INTERVAL '24 hours'
ORDER BY t.TIME_CREATED DESC;

-- ⑤ 활성화된 블록 조건 목록
SELECT TYPE, VALUE, ENABLED, DOMAIN
FROM AM_BLOCK_CONDITIONS
WHERE ENABLED = 1
ORDER BY TYPE, VALUE;

-- ⑥ GW 아티팩트 최신 배포 상태 (레이블별)
SELECT GATEWAY_LABEL, COUNT(*) AS deployed_apis,
       MAX(TO_TIMESTAMP(TIME_STAMP / 1000)) AS last_deploy_time
FROM AM_GW_API_ARTIFACTS
WHERE GATEWAY_INSTRUCTION = 'PUBLISH'
GROUP BY GATEWAY_LABEL;
```

---

## 8. 분산 배포 구성 완전 가이드 (공식 문서 기반)

> **출처:** WSO2 API Manager 4.3.0 공식 문서 + product-apim GitHub 소스  
> 공식 docs: `docs-apim/4.3.0/en/docs/install-and-setup/setup/distributed-deployment/`  
> 소스 템플릿: `product-apim/modules/distribution/.../conf/`

---

### 8-1. 배포 패턴 선택

WSO2 APIM 4.3은 세 가지 분산 배포 패턴을 공식 지원한다.

| 패턴 | 구성 | 적합 상황 |
|------|------|-----------|
| **Simple Scalable** | CP + GW | 표준 운영 환경 (권장) |
| **TM Separation** | CP + TM + GW | TM만 별도 스케일 필요 시 |
| **KM Separation** | CP + KM + GW | 토큰 발급량이 매우 많아 KM만 별도 스케일 필요 시 |

> 본 가이드는 **Simple Scalable (CP + GW)** 패턴 기준이다.  
> 이 패턴에서 CP는 Traffic Manager, Key Manager, Publisher, Developer Portal을 모두 포함한다.

---

### 8-2. 프로파일(Profile) 개요

WSO2 APIM 서버는 `-Dprofile=` 인수에 따라 시작하는 컴포넌트가 달라진다.

| 프로파일 | 활성 컴포넌트 | 비활성 컴포넌트 |
|----------|-------------|---------------|
| `control-plane` | Traffic Manager, Key Manager, Publisher, Developer Portal | API Gateway |
| `gateway-worker` | API Gateway (Synapse 엔진) | Publisher, DevPortal, KM, TM UI |
| `traffic-manager` | Traffic Manager만 | 나머지 전부 |
| `key-manager` | Key Manager만 | 나머지 전부 |

`profileSetup.sh`를 먼저 실행하면 해당 프로파일에서 **물리적으로 불필요한 webapp들을 삭제**하여 메모리 사용량과 기동 시간을 줄인다.

```bash
# 서버 최초 구성 시 (불필요 webapp 제거 — 1회)
sh $APIM_HOME/bin/profileSetup.sh -Dprofile=gateway-worker

# 이후 매번 기동 시
sh $APIM_HOME/bin/api-manager.sh -Dprofile=gateway-worker
```

---

### 8-3. 프로파일별 DB 사용 매핑

| 프로파일 | apim_db (WSO2AM_DB) | shared_db (WSO2SHARED_DB) |
|----------|---------------------|--------------------------|
| Control Plane | **필수** (API·토큰·구독·정책 모두 읽고 씀) | **필수** (사용자·레지스트리·세션) |
| Gateway Worker | **미사용** | **설정 필요** ⚠️ |
| Traffic Manager | **필수** (정책 테이블 조회) | **필수** |
| Key Manager | **필수** (토큰 발급·검증) | **필수** |

> **⚠️ Gateway의 shared_db 주의사항 (공식 문서 Warning):**  
> Gateway에서 `shared_db`가 실제로 필요하지 않더라도 **설정 자체를 완전히 제거해서는 안 된다.**  
> 서버 초기화 시 `user_core`와 `registry` 모듈이 기본 H2 또는 설정된 shared_db를 참조하기 때문이다.  
> 멀티테넌시 환경이나 Google Analytics 사용 시에는 실제 데이터도 접근한다.

---

### 8-4. 구성 절차 (7단계)

| 단계 | 내용 |
|------|------|
| Step 1 | WSO2 API-M 다운로드 및 프로파일별 복사본 준비 |
| Step 2 | 공유 DB 설치 및 스키마 적용 |
| Step 3 | Production Hardening (비밀번호 변경, JVM 보안 등) |
| Step 4 | SSL 인증서 생성 및 Keystore/Truststore 임포트 |
| Step 5 | API-M Analytics 설정 |
| **Step 6** | **프로파일별 deployment.toml 설정 (핵심)** |
| Step 7 | 노드 기동 |

---

### 8-5. Gateway Node 설정 상세

#### 8-5-1. [server] — 서버 기본 정보

```toml
[server]
hostname    = "gw.wso2.com"
node_ip     = "127.0.0.1"
server_role = "gateway-worker"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `hostname` | **이 노드가 생성하는 URL의 호스트 부분.** 노드의 실제 IP가 아닌 LB FQDN 사용. | GW가 응답 헤더, WSDL, API 엔드포인트 URL을 생성할 때 이 값을 삽입. 잘못 설정하면 클라이언트가 내부 IP를 응답으로 받는다. |
| `node_ip` | **Axis2 클러스터링에 사용되는 IP.** 공식 문서는 `127.0.0.1` 명시. | 4.x에서 Hazelcast 제거로 로컬루프백으로 충분. 과거(3.x)에는 실제 노드 IP가 필요했다. |
| `server_role` | **프로파일 식별자.** `"gateway-worker"`로 설정해야 GW 프로파일로 기동. | APIM 내부에서 이 값을 읽어 어떤 컴포넌트를 활성화할지 분기. 없으면 all-in-one 모드. |

#### 8-5-2. [database.shared_db] — 공유 DB 연결

```toml
[database.shared_db]
type     = "mysql"
hostname = "db.wso2.com"
name     = "shared_db"
port     = "3306"
username = "sharedadmin"
password = "sharedadmin"
```

**GW에서 apim_db를 설정하지 않는 이유:** Gateway는 API 정책/구독/토큰 데이터를 DB에서 직접 읽지 않는다.  
모든 아티팩트는 CP로부터 JMS 이벤트 + REST API Pull로 받아 **메모리(Synapse)에 로딩**한다.  
shared_db는 Carbon 프레임워크 초기화(레지스트리, 사용자 코어)에만 필요하다.

#### 8-5-3. [keystore.tls] / [truststore] — 인증서 설정

```toml
[keystore.tls]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

[truststore]
file_name = "client-truststore.jks"
type      = "JKS"
password  = "wso2carbon"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `keystore.tls` | GW의 **TLS 핸드셰이크에 사용하는 서버 인증서 + 개인키** 저장소. | HTTPS 클라이언트 연결 시 이 인증서를 제공. CP와 동일한 Primary Keystore를 사용해야 레지스트리 암호화 데이터를 공유 가능. |
| `file_name` | `$APIM_HOME/repository/resources/security/` 하위 JKS 파일 경로. | |
| `alias` | Keystore 안에서 인증서를 식별하는 별칭. | |
| `key_password` | 개인키 비밀번호 (JKS 비밀번호와 별개일 수 있음). | |
| `truststore` | **GW가 신뢰하는 외부 인증서 목록.** CP와 통신할 때 CP의 서버 인증서를 이 Truststore에서 검증. | CP의 공개 인증서가 없으면 GW-CP SSL 핸드셰이크가 실패한다. |

> **공식 문서 권고:** 모든 API-M 인스턴스에서 **동일한 Primary Keystore**를 사용해야 한다.  
> 이유: 레지스트리에 암호화되어 저장된 데이터를 모든 노드가 복호화할 수 있어야 하기 때문이다.

#### 8-5-4. [apim.key_manager] — Key Manager 연결

```toml
# HA 구성 (CP 2대, LB 앞단)
[apim.key_manager]
service_url = "https://cp.wso2.com/services/"
username    = "$ref{super_admin.username}"
password    = "$ref{super_admin.password}"

# Single CP 구성
# service_url = "https://cp.wso2.com:9443/services/"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `service_url` | **CP의 Key Manager 서비스 엔드포인트.** HA 구성 시 LB URL, Single 구성 시 `호스트:9443` 직접 지정. 끝에 `/services/` 필수. | GW가 JWT 토큰 캐시 미스 시 이 URL로 토큰 인트로스펙션(introspection) 요청을 보낸다. OAuth2 키 검증, 서브스크립션 확인에 사용. |
| `username` / `password` | KM API 호출 시 Basic 인증 자격증명. `$ref{super_admin.username}`은 [super_admin] 섹션 값 참조. | KM Internal API 호출 시 Authorization 헤더에 삽입. |

> **`$ref{}` 문법:** TOML 내에서 다른 섹션의 값을 참조하는 WSO2 Carbon 고유 문법.  
> `$ref{super_admin.username}` = `[super_admin]` 섹션의 `username` 값으로 치환.

#### 8-5-5. [apim.throttling] — Traffic Manager 연결 (스로틀링 + 이벤트 구독)

```toml
[apim.throttling]
service_url                 = "https://cp.wso2.com/services/"
throttle_decision_endpoints = ["tcp://apim-cp-1:5672", "tcp://apim-cp-2:5672"]
```

이것은 **가장 중요하고 가장 오해하기 쉬운 설정**이다.

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `service_url` | **CP의 Event Hub REST API 엔드포인트.** 아티팩트 Pull에 사용. | GW가 JMS로 "배포 이벤트" 알림을 받으면, 이 URL로 REST API를 호출해 실제 API 아티팩트(Synapse 설정)를 가져온다. JMS는 "알림"만, 데이터는 REST Pull이다. |
| `throttle_decision_endpoints` | **CP의 Qpid Broker AMQP 주소.** `tcp://호스트:5672` 형식. | GW가 이 주소로 JMS 구독을 생성한다. **하나의 JMS 연결**이 두 가지 역할을 동시에 한다: ① 스로틀링 결정(차단 여부) 수신, ② API/App/Subscription/KM 변경 이벤트 수신 (표준 분산 배포 기준). |

> **공식 문서 원문:**  
> *"Rate limiting configurations are used to configure both traffic management as well as the event hub for the Gateway in this scenario. The same JMS connection will be used to subscribe for events received from the event hub. Gateway will subscribe for API/Application/Subscription and Keymanager operations related events. `service_url` points to the internal API resides in the event hub that is used to pull artifacts and information from the db."*

```
throttle_decision_endpoints 의 JMS 연결이 처리하는 두 가지:

① 스로틀링 결정 이벤트:
   CP TM(Siddhi CEP)이 카운터 임계값 초과 판단
   → AMQP 토픽 org.wso2.apimgt.throttleout.* 발행
   → GW JMS 구독자가 수신 → 해당 요청에 429 반환 결정

② API/App/Subscription/KM 변경 이벤트:
   CP Event Hub가 API 배포/삭제/변경 이벤트 발행
   → GW JMS 구독자가 수신 (알림만)
   → GW가 service_url(REST API)로 실제 아티팩트 Pull
   → Synapse 엔진에 로딩/제거
```

#### 8-5-6. [[apim.throttling.url_group]] — 스로틀링 카운터 전송

```toml
[[apim.throttling.url_group]]
traffic_manager_urls      = ["tcp://apim-cp-1:9611"]
traffic_manager_auth_urls = ["ssl://apim-cp-1:9711"]

[[apim.throttling.url_group]]
traffic_manager_urls      = ["tcp://apim-cp-2:9611"]
traffic_manager_auth_urls = ["ssl://apim-cp-2:9711"]
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `traffic_manager_urls` | **CP의 Thrift Binary 수신 포트(9611).** 평문 TCP. | GW가 API 요청을 처리할 때마다 스로틀링 카운터(요청 수, 바이트, 사용자 정보)를 이 주소로 Thrift 프로토콜로 전송. CP TM의 Siddhi CEP 엔진이 수신하여 집계. |
| `traffic_manager_auth_urls` | **Thrift SSL 포트(9711).** 인증용. | Thrift 데이터 전송 인증. 평문(9611)과 SSL(9711)을 쌍으로 구성. |
| `[[...url_group]]` 이중 괄호 | TOML 배열 테이블 문법. 여러 TM 노드 등록. | GW는 **url_group 전체에 동시에** 카운터를 전송한다. CP1이 다운되어도 CP2로 계속 전송. |

포트별 방향 요약:

| 포트 | 프로토콜 | 방향 | 용도 |
|------|---------|------|------|
| 5672 | AMQP (JMS) | GW ← CP | 스로틀링 결정 + 이벤트 수신 (GW가 구독) |
| 9611 | Thrift TCP | GW → CP | 스로틀링 카운터 전송 (GW가 발행) |
| 9711 | Thrift SSL | GW → CP | Thrift 인증 |

#### 8-5-7. [apim.sync_runtime_artifacts.gateway] — 아티팩트 동기화 필터

```toml
[apim.sync_runtime_artifacts.gateway]
gateway_labels = ["Default"]
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `gateway_labels` | **이 GW 노드가 처리할 Gateway Label 목록.** | CP에서 API를 배포할 때 "어느 Gateway Label로 배포할지" 지정한다. GW는 JMS 이벤트를 수신하면 자신의 `gateway_labels`에 해당하는 이벤트만 처리. 불일치 레이블 이벤트는 무시. |

```
Publisher에서 "Default" 레이블로 배포
  → CP가 AMQP에 이벤트 발행
  → gateway_labels=["Default"] GW만 수신 → 아티팩트 Pull → Synapse 로딩
  → gateway_labels=["Premium"] GW는 이벤트 무시
```

스킵 리스트 (선택 설정):

```toml
[apim.sync_runtime_artifacts.gateway.skip_list]
apis          = ["legacy-api-v1.xml"]
endpoints     = ["custom-endpoint.xml"]
sequences     = ["post_with_nobody.xml"]
local_entries = ["file.xml"]
```

GW 로컬에 수동으로 배포한 아티팩트가 CP 동기화에 의해 덮어쓰이지 않도록 보호한다.

#### 8-5-8. [transport.http] / [transport.https] — 포트 바인딩

```toml
[transport.http]
properties.port      = 9763
properties.proxyPort = 80

[transport.https]
properties.port      = 9443
properties.proxyPort = 443
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `properties.port` | Tomcat/Catalina가 **실제로 바인딩하는 포트.** 서버가 직접 리스닝. | 내부 서비스 호출(CP→GW `service_url:9443`)이 이 포트를 사용. |
| `properties.proxyPort` | **LB(Nginx/F5)가 외부에 노출하는 포트를 APIM에 알려준다.** APIM이 생성하는 URL에 이 포트를 사용. | LB가 443→9443 포트포워딩을 하면, APIM이 생성하는 Swagger URL 등에 443이 들어가야 정상. `proxyPort` 없이 9443만 설정하면 외부 클라이언트에게 `:9443`이 노출된다. |

---

### 8-6. Control Plane Node 설정 상세

#### 8-6-1. [server] — CP 서버 기본 정보

```toml
[server]
hostname    = "cp.wso2.com"
node_ip     = "127.0.0.1"
server_role = "control-plane"
base_path   = "${carbon.protocol}://${carbon.host}:${carbon.management.port}"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `hostname` | **CP LB FQDN.** Publisher/DevPortal URL 생성의 기준점. | Publisher가 `https://cp.wso2.com/publisher`처럼 URL을 만들 때 사용. |
| `node_ip` | GW와 동일한 이유로 `127.0.0.1`. | 4.x에서 Hazelcast 제거 후 로컬루프백으로 충분. |
| `server_role` | `"control-plane"` — CP 프로파일로 기동. | 이 값으로 활성화할 컴포넌트를 결정. |
| `base_path` | **CP의 관리 UI 기본 경로.** Carbon 동적 변수 사용. | 실제 값: `https://cp.wso2.com:9443`. Publisher, DevPortal, Admin 포털이 redirect URL 생성에 사용. HA에서 LB FQDN을 올바르게 참조하게 한다. |

Carbon 프레임워크 내부 변수:
- `${carbon.protocol}` → `https`
- `${carbon.host}` → `[server].hostname` 값
- `${carbon.management.port}` → `[transport.https].properties.port` 값 (기본 9443)

#### 8-6-2. [[apim.gateway.environment]] — GW 환경 등록

```toml
[[apim.gateway.environment]]
name                       = "Default"
type                       = "hybrid"
display_in_api_console     = true
description                = "Production and Sandbox hybrid gateway"
show_as_token_endpoint_url = true
service_url                = "https://gw.wso2.com/services/"
ws_endpoint                = "ws://gw.wso2.com:9099"
wss_endpoint               = "wss://gw.wso2.com:8099"
http_endpoint              = "http://gw.wso2.com"
https_endpoint             = "https://gw.wso2.com"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `name` | **GW 환경 식별 이름.** GW의 `gateway_labels`와 일치해야 한다. | Publisher UI에서 "배포할 Gateway" 선택 드롭다운에 표시. |
| `type` | `"hybrid"` = Production/Sandbox 토큰 모두 처리. `"production"` / `"sandbox"` = 특정 토큰만 처리. | GW가 토큰 타입에 따라 요청을 다르게 라우팅할지 결정. |
| `display_in_api_console` | DevPortal의 API 테스트 콘솔(Swagger UI)에 이 환경 표시 여부. | `false`이면 개발자가 이 GW로 직접 API 테스트 불가. |
| `show_as_token_endpoint_url` | DevPortal에서 토큰 엔드포인트 URL을 이 환경 기준으로 표시할지. | OAuth2 토큰 URL을 개발자에게 안내할 때 사용. |
| `service_url` | **CP가 API를 배포/삭제할 때 GW로 호출하는 Admin Service URL.** 포트 9443/services/. | Publisher에서 API Deploy 버튼 클릭 시 CP가 이 URL로 GW에 배포 명령을 보낸다. HA GW이면 LB URL. |
| `http_endpoint` / `https_endpoint` | **외부 클라이언트가 API를 호출하는 URL.** | DevPortal, Swagger UI에서 API Base URL로 표시. LB 포트(80/443) 포함. |
| `ws_endpoint` / `wss_endpoint` | WebSocket API 엔드포인트 (포트 9099/8099). | Streaming API, WebSocket API 사용 시 필요. |

포트 구분 핵심:
- `service_url` → 포트 **9443** (관리용, CP→GW 내부 통신)
- `http_endpoint` / `https_endpoint` → 포트 **8280/8243** (API 호출용, 외부 클라이언트)

#### 8-6-3. [apim.devportal] — DevPortal URL

```toml
[apim.devportal]
url = "https://cp.wso2.com/devportal"
```

분산 배포 시 Publisher UI, Admin 포털에서 DevPortal 링크를 생성할 때 사용.  
All-in-one에서는 hostname으로 자동 생성되지만 분산 배포에서는 명시 필요.  
HA CP에서 LB FQDN으로 설정해야 클라이언트가 정상 접근 가능.

#### 8-6-4. [apim.event_hub] — CP 간 이벤트 복제 (HA 전용)

```toml
# ⚠️ CP가 2대 이상(HA)일 때만 추가. Single CP는 이 섹션 불필요.
[apim.event_hub]
enable                    = true
username                  = "$ref{super_admin.username}"
password                  = "$ref{super_admin.password}"
service_url               = "https://cp.wso2.com/services/"
event_listening_endpoints = ["tcp://localhost:5672"]
event_duplicate_url       = ["tcp://apim-cp-2:5672"]
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `enable` | Event Hub 기능 활성화. | `false`이면 GW에 이벤트가 전달되지 않아 API 배포가 GW에 반영되지 않는다. |
| `username` / `password` | Event Hub Internal API 호출 인증 자격증명. | CP 내부 `/internal/data/v1/` API 접근에 사용. |
| `service_url` | **이 CP의 Event Hub REST API URL.** GW가 아티팩트를 Pull할 때 사용. | GW의 `[apim.throttling].service_url`과 동일한 엔드포인트. HA이면 LB URL 사용. |
| `event_listening_endpoints` | **이 CP 노드 자신의 Qpid AMQP 주소.** | `localhost:5672` = 이 노드의 내장 Qpid. CP 자신이 이벤트를 발행하고 내부적으로 자신의 구독자에게 라우팅. |
| `event_duplicate_url` | **상대 CP 노드의 AMQP 주소.** 이 CP가 발행한 토큰취소 이벤트를 상대 CP에도 복제. | CP-1 이벤트 발생 → CP-1 Qpid 발행 → `event_duplicate_url`로 CP-2 Qpid에 복제 → GW가 CP-2 구독 중이어도 이벤트 수신. CP-2에서는 반대로 CP-1 주소 설정. |

> **공식 문서:** *"The token revocation events that are received to an event hub will be duplicated to the other event hub using `event_duplicate_url`."*

#### 8-6-5. [[apim.event_hub.publish.url_group]] — Thrift 이벤트 스트림 발행

```toml
[[apim.event_hub.publish.url_group]]
urls      = ["tcp://apim-cp-1:9611"]
auth_urls = ["ssl://apim-cp-1:9711"]

[[apim.event_hub.publish.url_group]]
urls      = ["tcp://apim-cp-2:9611"]
auth_urls = ["ssl://apim-cp-2:9711"]
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `urls` | **Thrift 평문 수신 포트(9611).** | CP1이 API 배포·구독·KM 변경 이벤트 스트림을 CP1·CP2 양 노드의 Event Hub(Thrift 수신 포트)로 발행. GW가 어느 CP 브로커를 구독하든 이벤트를 수신할 수 있도록 CP 간 이벤트를 복제한다. |
| `auth_urls` | **Thrift SSL 포트(9711).** | 위와 동일, 암호화 전송. |

> **혼동 주의:** `event_hub.publish.url_group`(Thrift 9611, **CP→CP API/구독/KM 이벤트 스트림 복제**)과  
> `throttling.url_group`(Thrift 9611, **GW→CP TM 스로틀링 카운터 전송**)은 **동일 포트를 사용하지만 전혀 다른 목적**이다.  
> `event_hub.publish.url_group`은 스로틀링 카운터와 무관하다.

> **공식 문서:** *"each event hub has to publish events to both event streams. This will be done through the event streams created with `apim.event_hub.publish.url_group`."*

#### 8-6-6. [apim.key_manager] — CP 자신의 KM 참조

```toml
[apim.key_manager]
service_url = "https://cp.wso2.com/services/"
```

CP가 자신의 Key Manager 컴포넌트를 참조하는 URL. CP 내부에서 토큰 발급, 키 검증, OAuth 메타데이터 조회 시 사용.  
HA 구성에서는 반드시 LB URL이어야 한다. `localhost:9443`으로 설정하면 해당 노드만 참조하게 되어 HA 의미가 없어진다.

#### 8-6-7. [[event_listener]] — 토큰 취소 이벤트 리스너

```toml
# ⚠️ Resident KM 사용 시만 CP에 추가. WSO2 IS를 KM으로 사용 시 IS 노드에 추가.
[[event_listener]]
id    = "token_revocation"
type  = "org.wso2.carbon.identity.core.handler.AbstractIdentityHandler"
name  = "org.wso2.is.notification.ApimOauthEventInterceptor"
order = 1

[event_listener.properties]
notification_endpoint       = "https://gw.wso2.com/internal/data/v1/notify"
username                    = "${admin.username}"
password                    = "${admin.password}"
'header.X-WSO2-KEY-MANAGER' = "default"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `id` | 리스너 식별 이름 (임의 문자열). | 복수 이벤트 리스너 등록 시 구분자. |
| `type` | Java 추상 클래스 FQCN. 리스너가 상속해야 할 베이스 클래스. | Carbon Identity 프레임워크가 이 타입으로 클래스를 로딩한다. |
| `name` | **실제 구현 클래스 FQCN.** `ApimOauthEventInterceptor`가 토큰 취소 이벤트를 가로챈다. | OAuth 토큰 취소(revoke) 요청 발생 시 이 인터셉터가 호출되어 GW에 취소 알림을 전송. |
| `order` | 복수 리스너 등록 시 실행 순서. 낮은 숫자가 먼저 실행. | |
| `notification_endpoint` | **GW에 토큰 취소를 알리는 REST API URL.** | 토큰이 취소되면 KM이 이 URL로 POST 요청을 보낸다. GW는 이 요청을 받아 자신의 토큰 블랙리스트에 추가. HA GW이면 LB URL로 설정. |
| `username` / `password` | `notification_endpoint` 호출 시 Basic 인증. | |
| `'header.X-WSO2-KEY-MANAGER'` | 이 알림이 어느 KM에서 왔는지 식별하는 헤더 값. `"default"` = Resident KM. | GW가 복수 KM 환경에서 취소 이벤트 출처를 판별. TOML에서 점(`.`)이 포함된 키는 작은따옴표로 감싸야 한다. |

`$ref{}` vs `${}` 차이:

| 문법 | 해석 시점 | 참조 대상 |
|------|----------|----------|
| `$ref{super_admin.username}` | TOML 파싱 시 | deployment.toml 내 다른 섹션 값 |
| `${admin.username}` | Carbon 런타임 시 | Carbon 시스템 프로퍼티 |

#### 8-6-8. [apim.cache_invalidation] — 분산 캐시 무효화

```toml
[apim.cache_invalidation]
enabled = true
domain  = "control-plane-domain"
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `enabled` | CP 노드 간 캐시 무효화 메시지 전송 활성화. | CP-1에서 API 수정 → CP-1의 인메모리 캐시 무효화 → 동일 `domain` 그룹 내 CP-2에도 무효화 메시지 전송 → CP-2 캐시도 갱신. |
| `domain` | **캐시 무효화 그룹 이름.** 동일 `domain` 값의 노드들이 같은 그룹으로 묶인다. | 다른 클러스터(CP ↔ GW)가 섞이지 않도록 격리. 같은 `domain` 값을 가진 CP 노드들끼리만 캐시 무효화 메시지를 교환. |

#### 8-6-9. [qpid.heartbeat] — AMQP 유휴 연결 유지

```toml
[qpid.heartbeat]
delay          = 1
timeout_factor = 3.0
```

| 키 | 의미 | 내부 동작 |
|----|------|----------|
| `delay` | **Heartbeat 전송 간격 (초).** | 내장 Qpid 브로커가 연결된 클라이언트(GW의 JMS 구독자)에게 이 간격으로 heartbeat 패킷을 전송. 연결 유휴 상태에서 방화벽/LB의 TCP idle timeout으로 연결이 끊기는 것을 방지. |
| `timeout_factor` | **Heartbeat 응답 대기 배수.** timeout = `delay × timeout_factor` 초. | 이 시간(3초) 안에 응답이 없으면 연결 단절로 간주하고 재연결 시도. |

> **공식 문서:** *"To set an appropriate delay for the heartbeat value when connections remain idle for extended periods, include the following configuration."*

---

### 8-7. 전체 설정 완전 샘플 (HA 클러스터 기준)

#### Control Plane HA 완전 샘플 (CP-Server1: 10.0.0.11)

```toml
[server]
hostname    = "cp.wso2.com"
node_ip     = "127.0.0.1"
server_role = "control-plane"
base_path   = "${carbon.protocol}://${carbon.host}:${carbon.management.port}"

[user_store]
type = "database_unique_id"

[super_admin]
username             = "admin"
password             = "Admin@12345"
create_admin_account = true

[database.apim_db]
type     = "mysql"
hostname = "db.wso2.com"
name     = "apim_db"
port     = "3306"
username = "apimadmin"
password = "apimadmin"

[database.shared_db]
type     = "mysql"
hostname = "db.wso2.com"
name     = "shared_db"
port     = "3306"
username = "sharedadmin"
password = "sharedadmin"

[keystore.tls]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

[truststore]
file_name = "client-truststore.jks"
type      = "JKS"
password  = "wso2carbon"

[[apim.gateway.environment]]
name                       = "Default"
type                       = "hybrid"
display_in_api_console     = true
description                = "Production and Sandbox hybrid gateway"
show_as_token_endpoint_url = true
service_url                = "https://gw.wso2.com/services/"
ws_endpoint                = "ws://gw.wso2.com:9099"
wss_endpoint               = "wss://gw.wso2.com:8099"
http_endpoint              = "http://gw.wso2.com"
https_endpoint             = "https://gw.wso2.com"

[apim.devportal]
url = "https://cp.wso2.com/devportal"

[transport.http]
properties.port      = 9763
properties.proxyPort = 80

[transport.https]
properties.port      = 9443
properties.proxyPort = 443

[apim.event_hub]
enable                    = true
username                  = "$ref{super_admin.username}"
password                  = "$ref{super_admin.password}"
service_url               = "https://cp.wso2.com/services/"
event_listening_endpoints = ["tcp://localhost:5672"]
event_duplicate_url       = ["tcp://10.0.0.12:5672"]

[[apim.event_hub.publish.url_group]]
urls      = ["tcp://10.0.0.11:9611"]
auth_urls = ["ssl://10.0.0.11:9711"]

[[apim.event_hub.publish.url_group]]
urls      = ["tcp://10.0.0.12:9611"]
auth_urls = ["ssl://10.0.0.12:9711"]

[apim.key_manager]
service_url = "https://cp.wso2.com/services/"

[[event_listener]]
id    = "token_revocation"
type  = "org.wso2.carbon.identity.core.handler.AbstractIdentityHandler"
name  = "org.wso2.is.notification.ApimOauthEventInterceptor"
order = 1

[event_listener.properties]
notification_endpoint       = "https://gw.wso2.com/internal/data/v1/notify"
username                    = "${admin.username}"
password                    = "${admin.password}"
'header.X-WSO2-KEY-MANAGER' = "default"

[apim.cache_invalidation]
enabled = true
domain  = "control-plane-domain"

[qpid.heartbeat]
delay          = 1
timeout_factor = 3.0
```

> **CP-Server2(10.0.0.12) 차이점:** `event_duplicate_url = ["tcp://10.0.0.11:5672"]` 만 변경.

---

#### Gateway Worker HA 완전 샘플 (GW-Server1: 10.0.0.21)

```toml
[server]
hostname    = "gw.wso2.com"
node_ip     = "127.0.0.1"
server_role = "gateway-worker"

[user_store]
type = "database_unique_id"

[super_admin]
username             = "admin"
password             = "Admin@12345"
create_admin_account = true

[database.shared_db]
type     = "mysql"
hostname = "db.wso2.com"
name     = "shared_db"
port     = "3306"
username = "sharedadmin"
password = "sharedadmin"

[keystore.tls]
file_name    = "wso2carbon.jks"
type         = "JKS"
password     = "wso2carbon"
alias        = "wso2carbon"
key_password = "wso2carbon"

[truststore]
file_name = "client-truststore.jks"
type      = "JKS"
password  = "wso2carbon"

[transport.http]
properties.port      = 9763
properties.proxyPort = 80

[transport.https]
properties.port      = 9443
properties.proxyPort = 443

[apim.sync_runtime_artifacts.gateway]
gateway_labels = ["Default"]

[apim.key_manager]
service_url = "https://cp.wso2.com/services/"
username    = "$ref{super_admin.username}"
password    = "$ref{super_admin.password}"

[apim.throttling]
service_url                 = "https://cp.wso2.com/services/"
throttle_decision_endpoints = ["tcp://10.0.0.11:5672", "tcp://10.0.0.12:5672"]

[[apim.throttling.url_group]]
traffic_manager_urls      = ["tcp://10.0.0.11:9611"]
traffic_manager_auth_urls = ["ssl://10.0.0.11:9711"]

[[apim.throttling.url_group]]
traffic_manager_urls      = ["tcp://10.0.0.12:9611"]
traffic_manager_auth_urls = ["ssl://10.0.0.12:9711"]
```

---

### 8-8. 설정 누락 시 증상 빠른 참조

| 누락 설정 | 증상 | 원인 |
|----------|------|------|
| `server_role` 미설정 | all-in-one 모드로 기동 (Publisher, DevPortal 등 전부 시작) | 프로파일 분기 조건 미충족 |
| GW `throttle_decision_endpoints` 미설정 | API 배포가 GW에 반영 안 됨. 스로틀링 미적용. | JMS 구독 없으므로 이벤트 수신 불가 |
| GW `gateway_labels` 불일치 | API 배포 성공으로 보이지만 GW에서 404 반환 | 이벤트 필터링에서 모든 이벤트가 무시됨 |
| CP `[[apim.gateway.environment]]` 미설정 | Publisher에서 API 배포 불가. GW 환경 선택지 없음. | CP가 GW 주소를 모름 |
| CP `[apim.event_hub]` HA에서 미설정 | 한 CP로 배포된 API가 다른 CP 구독 GW에 전달 안 됨 | CP 간 이벤트 복제 없음 |
| `event_duplicate_url` 잘못 설정 | 특정 GW에서 API 누락 | 상대 CP가 아닌 자신을 가리키거나 잘못된 IP/포트 |
| CP `[[event_listener]]` 미설정 | 토큰 취소 후에도 GW에서 해당 토큰 계속 허용 | GW 블랙리스트 갱신 미발생 |
| `[truststore]`에 상대 노드 인증서 누락 | SSL 핸드셰이크 오류 (`PKIX path building failed`) | 신뢰 체인 미구성 |
| `service_url`에 `localhost` 사용 (HA) | 단일 노드만 처리. 장애 시 아티팩트 Pull 실패. | LB를 거치지 않아 HA 의미 없음 |
| `proxyPort` 미설정 | API URL에 `:9443` 포함되어 외부 노출 | APIM이 LB 포트를 모르고 내부 포트 사용 |
