from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

# 인스턴스 세부 타입 — WSO2 프로파일 단위로 구분한다.
INSTANCE_TYPES = [
    'MI_ALL_IN_ONE', 'MI_MANAGER', 'MI_RUNTIME',
    'APIM_ALL_IN_ONE', 'APIM_MANAGER', 'APIM_GATEWAY',
]
# 배포(아티팩트 업로드) 대상이 될 수 있는 타입 — Gateway/Runtime은 실행 전용 노드라 제외.
DEPLOYABLE_TYPES = {'MI_ALL_IN_ONE', 'MI_MANAGER', 'APIM_ALL_IN_ONE', 'APIM_MANAGER'}

# 인스턴스 운영 환경 구분 — 운영(PROD) 인스턴스는 재시작/정지 시 강한 경고가 필요하다.
ENVIRONMENTS = ['DEV', 'PROD']

# 제품명 / 버전 — INSTANCE_TYPES(프로파일 단위 구분)와 별개로, 실제 배포된 WSO2
# 제품과 그 버전을 기록해두기 위한 값. EI는 INSTANCE_TYPES에 대응하는 프로파일이
# 없는 구형 제품이라 독립된 필드로 둔다(타입에서 유추하지 않음).
PRODUCTS = ['EI', 'MI', 'APIM']
VERSIONS = ['2.1', '6.1', '6.6', '4.3', '4.5', '4.6']

# 서버 운영 환경 구분 — 인스턴스와 달리 한 서버에 운영/개발 인스턴스가 동시에 떠있을 수 있어
# BOTH(운영개발 동시사용)가 추가로 있다.
SERVER_ENVIRONMENTS = ['DEV', 'PROD', 'BOTH']


def is_mi_type(t: str) -> bool:
    return t.startswith('MI_')


def is_apim_type(t: str) -> bool:
    return t.startswith('APIM_')


@dataclass
class ServerGroup:
    """최상위 계층 — 서버그룹>서버>인스턴스그룹>인스턴스 구조의 루트.
    보통 사이트/프로젝트/고객사 단위의 큰 묶음(예: "삼성 SDI 운영")을 나타낸다."""
    id: Optional[int] = None
    name: str = ''
    description: str = ''
    created_at: Optional[datetime] = None


@dataclass
class Server:
    """서버그룹 아래, 인스턴스그룹 위의 계층 — 물리적으로 실재하는 장비 한 대를 나타낸다.
    IP/운영환경처럼 그 장비 자체에 속하는 정보를 가지며, 인스턴스 쪽 IP 관련 값들은
    기본적으로 여기서 값을 물려받는다(인스턴스에서 개별 재정의 가능).

    OS SSH 접속 정보(SSH로 접속해 재시작/정지, 로그 tail -f 등을 수행하는 데 쓰는 계정)도
    여기 속한다 — 한 서버 위에서 여러 인스턴스가 같은 OS 계정을 공유하는 경우가 대부분이라
    인스턴스마다 반복 입력할 필요가 없도록 서버 단위로 한 번만 등록한다. (Management API
    인증정보인 admin_user/admin_pass/token_url은 인스턴스별로 다를 수 있어 Instance에 남아있다.)"""
    id: Optional[int] = None
    server_group_id: int = 0
    name: str = ''
    environment: str = 'DEV'  # DEV | PROD | BOTH(운영개발 동시사용) — SERVER_ENVIRONMENTS 참고
    ip: str = ''
    description: str = ''
    # OS SSH 접속 정보
    ssh_enabled: bool = False
    ssh_host: str = ''        # 비어 있으면 ip 사용
    ssh_port: int = 22
    ssh_user: str = ''
    ssh_pass: str = ''
    ssh_key_path: str = ''    # PEM 키 경로 (패스워드 대신 사용 가능)
    created_at: Optional[datetime] = None

    @property
    def effective_ssh_host(self) -> str:
        return self.ssh_host or self.ip


@dataclass
class InstanceGroup:
    """서버 아래, 인스턴스 위의 계층 — 한 서버 안에서 인스턴스를 용도별로 묶는 용도
    (예: 같은 서버에 뜬 MI 인스턴스들을 업무 단위로 구분)."""
    id: Optional[int] = None
    server_id: int = 0
    name: str = ''
    type: str = 'MI'          # MI | APIM
    description: str = ''
    created_at: Optional[datetime] = None


@dataclass
class Instance:
    id: Optional[int] = None
    group_id: int = 0
    name: str = ''
    host: str = ''
    port: int = 9164
    service_port: int = 8290     # 데이터플레인 서비스 포트 (배포된 API/커넥터용, HTTP, 인증 불필요)
    base_path: str = ''  # WSO2 설치 루트 (예: /app/ipaas/mi01) — UI에서 lib/시퀀스/bin/로그 경로를
                          # 이 값 + 하위경로로 자동 채우는 데 쓰는 편의 필드. 서버 호출 시 직접
                          # 참조되지는 않고 실제로 쓰이는 건 아래 개별 경로 필드들이다.
    lib_path: str = '/opt/wso2mi/lib'  # JAR 배포(SFTP) 대상 원격 디렉터리
    sequence_path: str = '/opt/wso2mi/repository/deployment/server/synapse-configs/default/sequences'  # 시퀀스 배포(SFTP, hot-deploy) 대상 원격 디렉터리
    jdbc_registry_path: str = 'registry/config/jdbc'  # 레지스트리 탭 기본 경로 (MI 전용)
    type: str = 'MI_ALL_IN_ONE'  # INSTANCE_TYPES 중 하나
    product: str = ''            # PRODUCTS 중 하나 (EI | MI | APIM), 미지정 가능
    version: str = ''            # VERSIONS 중 하나, 미지정 가능
    environment: str = 'DEV'     # DEV | PROD
    admin_user: str = 'admin'
    admin_pass: str = ''
    token_url: str = ''
    log_path: str = '/opt/wso2mi/repository/logs/wso2carbon.log'
    api_log_max_mb: int = 10  # API 방식 로그 조회 허용 최대 파일 크기(MB) — 넘으면 API 스트리밍 거부
    bin_path: str = '/opt/wso2mi/bin'  # SSH 재시작/정지 스크립트가 있는 디렉터리
    service_script: str = 'micro-integrator.sh'  # MI는 micro-integrator.sh, APIM은 api-manager.sh
                                                   # (레거시 wso2server.sh 아님. graceful 옵션 미지원)
    description: str = ''
    # OS SSH 접속 정보 — 실제 등록/저장은 이 인스턴스가 속한 Server에서 이뤄진다(서버
    # 단위로 한 번만 등록하면 그 서버 아래 모든 인스턴스가 공유). 여기 필드들은 DB에서
    # 인스턴스를 읽어올 때(db.database._row_to_instance) 소속 서버의 값을 그대로 복사해
    # 채워주는 "읽기 전용 반영값"이라, restart/log 서비스처럼 이미 Instance만 들고 SSH
    # 정보를 쓰던 코드를 그대로 쓸 수 있다 — 편집/저장(save_instance)은 이 필드들을
    # 더 이상 건드리지 않는다.
    ssh_enabled: bool = False
    ssh_host: str = ''        # 비어 있으면 host 사용
    ssh_port: int = 22
    ssh_user: str = ''
    ssh_pass: str = ''
    ssh_key_path: str = ''    # PEM 키 경로 (패스워드 대신 사용 가능)
    created_at: Optional[datetime] = None

    @property
    def base_url(self) -> str:
        return f'https://{self.host}:{self.port}'

    @property
    def service_base_url(self) -> str:
        """배포된 API/커넥터가 실제로 뜨는 데이터플레인 포트 (기본 8290, plain HTTP)."""
        return f'http://{self.host}:{self.service_port}'

    @property
    def management_url(self) -> str:
        if is_mi_type(self.type):
            return f'{self.base_url}/management'
        return f'{self.base_url}/api/am/admin/v4'

    @property
    def effective_ssh_host(self) -> str:
        return self.ssh_host or self.host


@dataclass
class DeploymentHistory:
    id: Optional[int] = None
    instance_id: int = 0
    artifact_name: str = ''
    artifact_type: str = ''   # CAR | SEQUENCE | JAR
    source_path: str = ''
    backup_path: str = ''            # 이번에 배포한 새 파일의 백업 (재배포용)
    previous_backup_path: str = ''   # 배포 직전 서버에 있던 기존 파일의 백업 (진짜 롤백용, 없으면 신규배포)
    deployed_at: Optional[datetime] = None
    deployed_by: str = ''
    status: str = ''          # SUCCESS | FAILED | ROLLEDBACK
    reason: str = ''          # 배포 사유


@dataclass
class AuditLog:
    id: Optional[int] = None
    timestamp: Optional[datetime] = None
    action: str = ''          # DEPLOY | DELETE | RESTART | STOP | ROLLBACK | ...
    target_name: str = ''
    instance_ids: str = ''    # comma-separated
    operator: str = 'admin'
    result: str = ''          # SUCCESS | FAILED | PARTIAL
    detail: str = ''
