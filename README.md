# siis-projects

SIIS 관련 프로젝트 모노레포. 각 하위 디렉터리는 원래 독립된 저장소였고, `git subtree`로
커밋 히스토리를 보존한 채 이 저장소로 합쳤습니다.

- [`SIISDbConnectorJava`](./SIISDbConnectorJava) — WSO2 MI/EI용 JDBC 커넥터 (JdbcMediator/DbInfoMediator 등)
- [`SIISDbConnectorTest`](./SIISDbConnectorTest) — 위 커넥터의 REST API 기능/성능 테스트 스위트
- [`SIISManagementApi`](./SIISManagementApi) — JVM 모니터링/로그 뷰어 Synapse API (Composite Application)
- [`SIISManagementJava`](./SIISManagementJava) — 위 API가 호출하는 클래스 미디에이터 구현체 (Java 8 호환)
- [`backend-emulator`](./backend-emulator) — WSO2 API Gateway 테스트용 백엔드 에뮬레이터
- [`wso2-mgmt-tool`](./wso2-mgmt-tool) — WSO2 인스턴스 관리 데스크톱 앱(pywebview). 실제 운영 데이터
  (`data/mgmt.db` 등 접속정보 평문 포함)는 `.gitignore`로 제외되어 있음 — 처음 실행 시 로컬에 새로 생성됨
