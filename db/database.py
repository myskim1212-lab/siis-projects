import sqlite3
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import List, Optional

from db.models import AuditLog, DeploymentHistory, Instance, InstanceGroup, Server, ServerGroup

DB_PATH = Path(__file__).parent.parent / 'data' / 'mgmt.db'

# 그룹>서버>인스턴스 구조 도입 전에 만들어진 인스턴스를 마이그레이션할 때 배정하는
# 기본 서버 이름. instance_io_service.py의 구버전(서버 계층 없는) 가져오기 파일
# 처리에도 동일한 이름을 쓴다.
DEFAULT_SERVER_NAME = '기본서버'

# 서버그룹>서버>인스턴스그룹>인스턴스 구조 도입 전(그룹>서버>인스턴스 구조)에 만들어진
# 서버를 마이그레이션할 때, 그 서버 아래에 자동으로 만들어주는 기본 인스턴스그룹 이름.
# instance_io_service.py의 구버전(인스턴스그룹 계층 없는) 가져오기 파일 처리에도 쓴다.
DEFAULT_INSTANCE_GROUP_NAME = '기본그룹'


def get_connection() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA foreign_keys = ON')
    return conn


@contextmanager
def transaction():
    conn = get_connection()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def _table_exists(conn: sqlite3.Connection, name: str) -> bool:
    return conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (name,)).fetchone() is not None


def _migrate_to_4level_schema():
    """오래된 스키마를 서버그룹>서버>인스턴스그룹>인스턴스(4계층) 구조로 승격한다.
    완전 새 설치(instance_group 테이블 자체가 없음)나 이미 승격된 DB는 건드리지 않고 바로 반환한다.

    옛 그룹>인스턴스(2단, 서버 계층 자체가 없던 가장 오래된 구조)와 그룹>서버>인스턴스
    (3단) 구조를 여기서 한 번에 4단으로 올린다 — 컬럼 제약(server.group_id NOT NULL,
    instance_group.name 전역 UNIQUE)이 새 구조와 맞지 않아 테이블 재생성이 필요한데
    (SQLite는 컬럼 제약을 바꾸는 ALTER를 지원하지 않음), foreign_keys가 켜진 채로 DROP TABLE을
    하면 ON DELETE CASCADE가 걸린 자식 테이블(instance) 데이터까지 암묵적으로 삭제해버리는
    SQLite 동작 때문에 이 함수 전용의 별도 연결을 FK OFF 상태로 열어 처리한다 (PRAGMA
    foreign_keys는 트랜잭션 도중에는 바꿀 수 없어 init_db()의 공용 연결과는 분리해야 함).
    완료 후 이 연결을 닫으면, 나머지 init_db() 로직은 평소처럼 FK ON인 공용 연결에서 진행된다.
    """
    if not DB_PATH.exists():
        return
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA foreign_keys = OFF')
    try:
        if not _table_exists(conn, 'instance_group'):
            return  # 완전 새 설치 — init_db()의 나머지 부분이 처음부터 4계층 스키마로 만들어줌

        server_exists = _table_exists(conn, 'server')
        server_columns = ({row['name'] for row in conn.execute('PRAGMA table_info(server)')}
                           if server_exists else set())
        if server_exists and 'server_group_id' in server_columns:
            return  # 이미 4계층으로 승격된 DB

        conn.execute('''
            CREATE TABLE IF NOT EXISTS server_group (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT NOT NULL UNIQUE,
                description TEXT DEFAULT '',
                created_at  TEXT DEFAULT (datetime('now','localtime'))
            )
        ''')
        new_server_schema = '''
            CREATE TABLE server (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                server_group_id INTEGER NOT NULL REFERENCES server_group(id) ON DELETE CASCADE,
                name            TEXT NOT NULL,
                environment     TEXT NOT NULL DEFAULT 'DEV',
                ip              TEXT DEFAULT '',
                description     TEXT DEFAULT '',
                created_at      TEXT DEFAULT (datetime('now','localtime')),
                UNIQUE(server_group_id, name)
            )
        '''
        new_instance_group_schema = '''
            CREATE TABLE instance_group (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                server_id   INTEGER REFERENCES server(id) ON DELETE CASCADE,
                name        TEXT NOT NULL,
                type        TEXT NOT NULL DEFAULT 'MI',
                description TEXT DEFAULT '',
                created_at  TEXT DEFAULT (datetime('now','localtime')),
                UNIQUE(server_id, name)
            )
        '''

        if server_exists:
            # 그룹>서버>인스턴스(3단) DB — 옛 server/instance_group을 스냅샷(_legacy_v1)으로
            # 통째로 복사해 영구 보존한 뒤, 원래 이름으로 새 스키마 테이블을 다시 만든다.
            # RENAME이 아니라 스냅샷+DROP+CREATE라서(둘 다 FK OFF 상태) instance 등 다른 테이블의
            # FK 정의(REFERENCES server(id) 등) 텍스트 자체는 전혀 건드려지지 않고, SQLite가 FK
            # 대상을 이름으로 동적 해석하므로 원래 이름으로 새 테이블을 만들면 자연히 그쪽을
            # 가리키게 된다 (RENAME은 참조하는 다른 테이블의 FK 텍스트까지 자동으로 고쳐 써버려서
            # 오히려 문제가 됨).
            conn.execute('CREATE TABLE server_legacy_v1 AS SELECT * FROM server')
            conn.execute('DROP TABLE server')
            conn.execute(new_server_schema)
        else:
            conn.execute(new_server_schema)

        # 옛 instance_group도 같은 방식으로 스냅샷에 영구 보존한 뒤 재생성한다. 옛 행(최상위
        # 그룹들)은 id를 유지한 채 새 테이블에도 그대로 복사해둔다 (server_id는 아직 비워둠 —
        # 아래에서 server_group으로 승격되는 소스일 뿐, 그 자체는 더 이상 어떤 서버에도 속하지
        # 않는 고아 레코드로 영구히 남는다).
        conn.execute('CREATE TABLE instance_group_legacy_v1 AS SELECT * FROM instance_group')
        conn.execute('DROP TABLE instance_group')
        conn.execute(new_instance_group_schema)
        conn.execute('''
            INSERT INTO instance_group (id, server_id, name, type, description, created_at)
            SELECT id, NULL, name, type, description, created_at FROM instance_group_legacy_v1
        ''')

        # 옛 최상위 instance_group(프로젝트/사이트 단위 묶음)을 새 server_group으로 승격.
        # 이름/설명을 그대로 옮기고, 옛 group_id -> 새 server_group_id 매핑을 만든다.
        old_top_groups = conn.execute(
            'SELECT id, name, description FROM instance_group_legacy_v1').fetchall()
        old_group_id_to_server_group_id = {}
        for row in old_top_groups:
            new_sg_id = conn.execute(
                'INSERT INTO server_group (name, description) VALUES (?,?)',
                (row['name'], row['description'] or '')).lastrowid
            old_group_id_to_server_group_id[row['id']] = new_sg_id

        if server_exists:
            # 3단 DB: 옛 서버들을 새 server 테이블로 복사하면서 server_group_id를 채운다
            # (id를 그대로 유지해야 아래에서 server_id 기준으로 인스턴스를 찾을 수 있음).
            for srow in conn.execute('SELECT id, group_id, name, description, created_at FROM server_legacy_v1'):
                new_sg_id = old_group_id_to_server_group_id[srow['group_id']]
                conn.execute(
                    'INSERT INTO server (id, server_group_id, name, description, created_at) VALUES (?,?,?,?,?)',
                    (srow['id'], new_sg_id, srow['name'], srow['description'] or '', srow['created_at']))

            # 서버마다 기본 인스턴스그룹을 하나씩 만들고, 그 서버 소속(구 instance.server_id
            # 기준) 인스턴스를 전부 옮긴다.
            instance_columns = {row['name'] for row in conn.execute('PRAGMA table_info(instance)')}
            has_server_id = 'server_id' in instance_columns
            for srow in conn.execute('SELECT id FROM server'):
                server_id = srow['id']
                new_group_id = conn.execute(
                    'INSERT INTO instance_group (server_id, name, description) VALUES (?,?,?)',
                    (server_id, DEFAULT_INSTANCE_GROUP_NAME,
                     '서버그룹>서버>인스턴스그룹>인스턴스 구조 도입 시 자동 생성된 기본 그룹')).lastrowid
                if has_server_id:
                    conn.execute('UPDATE instance SET group_id=? WHERE server_id=?', (new_group_id, server_id))
        else:
            # 2단(서버 계층 자체가 없던 가장 오래된) DB: 옛 최상위 그룹마다 기본 서버 하나와
            # 기본 인스턴스그룹 하나를 만들고, 그 그룹 소속 인스턴스를 전부 옮긴다.
            for old_gid, new_sg_id in old_group_id_to_server_group_id.items():
                new_server_id = conn.execute(
                    'INSERT INTO server (server_group_id, name, description) VALUES (?,?,?)',
                    (new_sg_id, DEFAULT_SERVER_NAME,
                     '서버그룹>서버>인스턴스그룹>인스턴스 구조 도입 시 자동 생성된 기본 서버')).lastrowid
                new_group_id = conn.execute(
                    'INSERT INTO instance_group (server_id, name, description) VALUES (?,?,?)',
                    (new_server_id, DEFAULT_INSTANCE_GROUP_NAME,
                     '서버그룹>서버>인스턴스그룹>인스턴스 구조 도입 시 자동 생성된 기본 그룹')).lastrowid
                conn.execute('UPDATE instance SET group_id=? WHERE group_id=?', (new_group_id, old_gid))

        conn.commit()
    finally:
        conn.close()


def init_db():
    _migrate_to_4level_schema()
    with transaction() as conn:
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS server_group (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            name        TEXT NOT NULL UNIQUE,
            description TEXT DEFAULT '',
            created_at  TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE IF NOT EXISTS server (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            server_group_id INTEGER NOT NULL REFERENCES server_group(id) ON DELETE CASCADE,
            name            TEXT NOT NULL,
            environment     TEXT NOT NULL DEFAULT 'DEV',
            ip              TEXT DEFAULT '',
            description     TEXT DEFAULT '',
            ssh_enabled     INTEGER NOT NULL DEFAULT 0,
            ssh_host        TEXT DEFAULT '',
            ssh_port        INTEGER NOT NULL DEFAULT 22,
            ssh_user        TEXT DEFAULT '',
            ssh_pass        TEXT DEFAULT '',
            ssh_key_path    TEXT DEFAULT '',
            created_at      TEXT DEFAULT (datetime('now','localtime')),
            UNIQUE(server_group_id, name)
        );

        CREATE TABLE IF NOT EXISTS instance_group (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            server_id   INTEGER REFERENCES server(id) ON DELETE CASCADE,
            name        TEXT NOT NULL,
            type        TEXT NOT NULL DEFAULT 'MI',
            description TEXT DEFAULT '',
            created_at  TEXT DEFAULT (datetime('now','localtime')),
            UNIQUE(server_id, name)
        );

        CREATE TABLE IF NOT EXISTS instance (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            group_id     INTEGER NOT NULL REFERENCES instance_group(id) ON DELETE CASCADE,
            server_id    INTEGER REFERENCES server(id) ON DELETE CASCADE,
            name         TEXT NOT NULL,
            host         TEXT NOT NULL,
            port         INTEGER NOT NULL DEFAULT 9164,
            service_port INTEGER NOT NULL DEFAULT 8290,
            base_path    TEXT NOT NULL DEFAULT '',
            lib_path     TEXT NOT NULL DEFAULT '/opt/wso2mi/lib',
            sequence_path TEXT NOT NULL DEFAULT '/opt/wso2mi/repository/deployment/server/synapse-configs/default/sequences',
            jdbc_registry_path TEXT NOT NULL DEFAULT 'registry/config/jdbc',
            type         TEXT NOT NULL DEFAULT 'MI_ALL_IN_ONE',
            product      TEXT NOT NULL DEFAULT '',
            version      TEXT NOT NULL DEFAULT '',
            environment  TEXT NOT NULL DEFAULT 'DEV',
            admin_user   TEXT NOT NULL DEFAULT 'admin',
            admin_pass   TEXT NOT NULL DEFAULT '',
            token_url    TEXT NOT NULL DEFAULT '',
            log_path     TEXT NOT NULL DEFAULT '',
            api_log_max_mb INTEGER NOT NULL DEFAULT 10,
            bin_path     TEXT NOT NULL DEFAULT '/opt/wso2mi/bin',
            service_script TEXT NOT NULL DEFAULT 'micro-integrator.sh',
            description  TEXT DEFAULT '',
            ssh_enabled  INTEGER NOT NULL DEFAULT 0,
            ssh_host     TEXT DEFAULT '',
            ssh_port     INTEGER NOT NULL DEFAULT 22,
            ssh_user     TEXT DEFAULT '',
            ssh_pass     TEXT DEFAULT '',
            ssh_key_path TEXT DEFAULT '',
            created_at   TEXT DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE IF NOT EXISTS deployment_history (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            instance_id   INTEGER NOT NULL REFERENCES instance(id) ON DELETE CASCADE,
            artifact_name TEXT NOT NULL,
            artifact_type TEXT NOT NULL,
            source_path   TEXT DEFAULT '',
            backup_path   TEXT DEFAULT '',
            previous_backup_path TEXT DEFAULT '',
            deployed_at   TEXT DEFAULT (datetime('now','localtime')),
            deployed_by   TEXT DEFAULT 'admin',
            status        TEXT NOT NULL DEFAULT 'SUCCESS',
            reason        TEXT DEFAULT ''
        );

        CREATE TABLE IF NOT EXISTS audit_log (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp    TEXT DEFAULT (datetime('now','localtime')),
            action       TEXT NOT NULL,
            target_name  TEXT DEFAULT '',
            instance_ids TEXT DEFAULT '',
            operator     TEXT DEFAULT 'admin',
            result       TEXT DEFAULT '',
            detail       TEXT DEFAULT ''
        );

        CREATE TABLE IF NOT EXISTS datasource_jndi_cache (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            instance_id INTEGER NOT NULL REFERENCES instance(id) ON DELETE CASCADE,
            ds_name     TEXT NOT NULL,
            jndi_name   TEXT NOT NULL,
            updated_at  TEXT DEFAULT (datetime('now','localtime')),
            UNIQUE(instance_id, ds_name)
        );

        CREATE TABLE IF NOT EXISTS token_cache (
            instance_id INTEGER PRIMARY KEY REFERENCES instance(id) ON DELETE CASCADE,
            token       TEXT NOT NULL,
            expires_at  REAL NOT NULL,
            updated_at  TEXT DEFAULT (datetime('now','localtime'))
        );
        ''')
        # 구버전 타입 값('MI'/'APIM')을 세분화된 타입으로 정규화 (멱등)
        conn.execute("UPDATE instance SET type='MI_ALL_IN_ONE' WHERE type='MI'")
        conn.execute("UPDATE instance SET type='APIM_ALL_IN_ONE' WHERE type='APIM'")

        # 기존 DB에 environment 컬럼이 없으면 추가 (CREATE TABLE IF NOT EXISTS는
        # 이미 존재하는 테이블에 새 컬럼을 반영하지 않으므로 별도 마이그레이션 필요)
        columns = {row['name'] for row in conn.execute('PRAGMA table_info(instance)')}
        if 'environment' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN environment TEXT NOT NULL DEFAULT 'DEV'")
        if 'service_port' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN service_port INTEGER NOT NULL DEFAULT 8290")
        if 'lib_path' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN lib_path TEXT NOT NULL DEFAULT '/opt/wso2mi/lib'")
        if 'sequence_path' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN sequence_path TEXT NOT NULL DEFAULT '/opt/wso2mi/repository/deployment/server/synapse-configs/default/sequences'")
        if 'jdbc_registry_path' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN jdbc_registry_path TEXT NOT NULL DEFAULT 'registry/config/jdbc'")
        if 'bin_path' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN bin_path TEXT NOT NULL DEFAULT '/opt/wso2mi/bin'")
        if 'service_script' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN service_script TEXT NOT NULL DEFAULT 'micro-integrator.sh'")
        if 'base_path' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN base_path TEXT NOT NULL DEFAULT ''")
        if 'api_log_max_mb' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN api_log_max_mb INTEGER NOT NULL DEFAULT 10")
        if 'server_id' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN server_id INTEGER REFERENCES server(id) ON DELETE CASCADE")
        if 'product' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN product TEXT NOT NULL DEFAULT ''")
        if 'version' not in columns:
            conn.execute("ALTER TABLE instance ADD COLUMN version TEXT NOT NULL DEFAULT ''")

        # OS SSH 접속 정보를 인스턴스에서 서버 단위로 옮긴다. server 테이블에 컬럼이 아직
        # 없던 기존 DB라면(=이번에 처음 추가), 그동안 인스턴스별로 등록해뒀던 SSH 정보가
        # 그냥 사라지면 안 되므로 그 서버 아래 인스턴스 중 SSH가 설정된 첫 번째 것의 값을
        # 서버로 끌어올려 보존한다(완전히 새로 만드는 컬럼이라 매번 실행돼도 이 백필은
        # 한 번만 일어남 — 이후 재실행 시엔 컬럼이 이미 있어 이 블록 자체를 타지 않음).
        server_columns = {row['name'] for row in conn.execute('PRAGMA table_info(server)')}
        if 'ssh_enabled' not in server_columns:
            conn.execute("ALTER TABLE server ADD COLUMN ssh_enabled INTEGER NOT NULL DEFAULT 0")
            conn.execute("ALTER TABLE server ADD COLUMN ssh_host TEXT DEFAULT ''")
            conn.execute("ALTER TABLE server ADD COLUMN ssh_port INTEGER NOT NULL DEFAULT 22")
            conn.execute("ALTER TABLE server ADD COLUMN ssh_user TEXT DEFAULT ''")
            conn.execute("ALTER TABLE server ADD COLUMN ssh_pass TEXT DEFAULT ''")
            conn.execute("ALTER TABLE server ADD COLUMN ssh_key_path TEXT DEFAULT ''")

            for srow in conn.execute('SELECT id FROM server'):
                src = conn.execute('''
                    SELECT i.ssh_host, i.ssh_port, i.ssh_user, i.ssh_pass, i.ssh_key_path
                    FROM instance i
                    JOIN instance_group ig ON i.group_id = ig.id
                    WHERE ig.server_id = ? AND i.ssh_enabled = 1
                    LIMIT 1
                ''', (srow['id'],)).fetchone()
                if src:
                    conn.execute('''
                        UPDATE server SET ssh_enabled=1, ssh_host=?, ssh_port=?, ssh_user=?, ssh_pass=?, ssh_key_path=?
                        WHERE id=?
                    ''', (src['ssh_host'], src['ssh_port'], src['ssh_user'], src['ssh_pass'],
                          src['ssh_key_path'], srow['id']))

        dep_columns = {row['name'] for row in conn.execute('PRAGMA table_info(deployment_history)')}
        if 'reason' not in dep_columns:
            conn.execute("ALTER TABLE deployment_history ADD COLUMN reason TEXT DEFAULT ''")
        if 'previous_backup_path' not in dep_columns:
            conn.execute("ALTER TABLE deployment_history ADD COLUMN previous_backup_path TEXT DEFAULT ''")


# ── ServerGroup (서버그룹>서버>인스턴스그룹>인스턴스 구조의 최상위 계층) ────────

def _row_to_server_group(row: sqlite3.Row) -> ServerGroup:
    return ServerGroup(
        id=row['id'], name=row['name'], description=row['description'] or '',
        created_at=datetime.fromisoformat(row['created_at']) if row['created_at'] else None,
    )


def get_all_server_groups() -> List[ServerGroup]:
    with get_connection() as conn:
        # NOCASE 정렬 이유는 get_all_groups() 주석 참고 (대문자 시작 이름이 몰리는 것 방지)
        return [_row_to_server_group(r) for r in
                conn.execute('SELECT * FROM server_group ORDER BY name COLLATE NOCASE')]


def get_server_group(server_group_id: int) -> Optional[ServerGroup]:
    with get_connection() as conn:
        row = conn.execute('SELECT * FROM server_group WHERE id=?', (server_group_id,)).fetchone()
        return _row_to_server_group(row) if row else None


def save_server_group(sg: ServerGroup) -> ServerGroup:
    with transaction() as conn:
        if sg.id is None:
            cur = conn.execute(
                'INSERT INTO server_group (name,description) VALUES (?,?)',
                (sg.name, sg.description))
            sg.id = cur.lastrowid
        else:
            conn.execute(
                'UPDATE server_group SET name=?,description=? WHERE id=?',
                (sg.name, sg.description, sg.id))
    return sg


def delete_server_group(server_group_id: int):
    with transaction() as conn:
        conn.execute('DELETE FROM server_group WHERE id=?', (server_group_id,))


# ── Server (서버그룹 아래, 인스턴스그룹 위의 계층) ─────────────────────────────

def _row_to_server(row: sqlite3.Row) -> Server:
    return Server(
        id=row['id'], server_group_id=row['server_group_id'], name=row['name'],
        environment=row['environment'] or 'DEV', ip=row['ip'] or '',
        description=row['description'] or '',
        ssh_enabled=bool(row['ssh_enabled']),
        ssh_host=row['ssh_host'] or '', ssh_port=row['ssh_port'] or 22,
        ssh_user=row['ssh_user'] or '', ssh_pass=row['ssh_pass'] or '',
        ssh_key_path=row['ssh_key_path'] or '',
        created_at=datetime.fromisoformat(row['created_at']) if row['created_at'] else None,
    )


def get_servers_by_server_group(server_group_id: int) -> List[Server]:
    with get_connection() as conn:
        rows = conn.execute(
            'SELECT * FROM server WHERE server_group_id=? ORDER BY name COLLATE NOCASE', (server_group_id,))
        return [_row_to_server(r) for r in rows]


def get_all_servers() -> List[Server]:
    with get_connection() as conn:
        rows = conn.execute('SELECT * FROM server ORDER BY name COLLATE NOCASE')
        return [_row_to_server(r) for r in rows]


def get_server(server_id: int) -> Optional[Server]:
    with get_connection() as conn:
        row = conn.execute('SELECT * FROM server WHERE id=?', (server_id,)).fetchone()
        return _row_to_server(row) if row else None


def save_server(s: Server) -> Server:
    fields = (s.server_group_id, s.name, s.environment, s.ip, s.description,
              int(s.ssh_enabled), s.ssh_host, s.ssh_port, s.ssh_user, s.ssh_pass, s.ssh_key_path)
    with transaction() as conn:
        if s.id is None:
            cur = conn.execute('''
                INSERT INTO server
                (server_group_id,name,environment,ip,description,ssh_enabled,ssh_host,ssh_port,ssh_user,ssh_pass,ssh_key_path)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)''', fields)
            s.id = cur.lastrowid
        else:
            conn.execute('''
                UPDATE server SET
                server_group_id=?,name=?,environment=?,ip=?,description=?,
                ssh_enabled=?,ssh_host=?,ssh_port=?,ssh_user=?,ssh_pass=?,ssh_key_path=?
                WHERE id=?''', fields + (s.id,))
    return s


def delete_server(server_id: int):
    with transaction() as conn:
        conn.execute('DELETE FROM server WHERE id=?', (server_id,))


# ── InstanceGroup (서버 아래, 인스턴스 위의 계층) ─────────────────────────────

def _row_to_group(row: sqlite3.Row) -> InstanceGroup:
    return InstanceGroup(
        id=row['id'], server_id=row['server_id'], name=row['name'], type=row['type'],
        description=row['description'] or '',
        created_at=datetime.fromisoformat(row['created_at']) if row['created_at'] else None,
    )


def get_groups_by_server(server_id: int) -> List[InstanceGroup]:
    with get_connection() as conn:
        rows = conn.execute(
            'SELECT * FROM instance_group WHERE server_id=? ORDER BY name COLLATE NOCASE', (server_id,))
        return [_row_to_group(r) for r in rows]


def get_all_groups() -> List[InstanceGroup]:
    with get_connection() as conn:
        # SQLite 기본 정렬(BINARY)은 대소문자를 구분해서 'Apple'이 'apple'보다 항상
        # 앞에 오는 등 대문자로 시작하는 이름들이 통째로 앞으로 몰린다 — 사용자가
        # 기대하는 알파벳 순서가 아니므로 대소문자 구분 없는 NOCASE로 정렬한다
        # (한글은 대소문자 개념이 없어 NOCASE를 써도 코드포인트 순서 그대로 유지됨).
        return [_row_to_group(r) for r in conn.execute('SELECT * FROM instance_group ORDER BY name COLLATE NOCASE')]


def get_group(group_id: int) -> Optional[InstanceGroup]:
    with get_connection() as conn:
        row = conn.execute('SELECT * FROM instance_group WHERE id=?', (group_id,)).fetchone()
        return _row_to_group(row) if row else None


def save_group(g: InstanceGroup) -> InstanceGroup:
    with transaction() as conn:
        if g.id is None:
            cur = conn.execute(
                'INSERT INTO instance_group (server_id,name,type,description) VALUES (?,?,?,?)',
                (g.server_id, g.name, g.type, g.description))
            g.id = cur.lastrowid
        else:
            conn.execute(
                'UPDATE instance_group SET server_id=?,name=?,type=?,description=? WHERE id=?',
                (g.server_id, g.name, g.type, g.description, g.id))
    return g


def delete_group(group_id: int):
    with transaction() as conn:
        conn.execute('DELETE FROM instance_group WHERE id=?', (group_id,))


# ── Instance ─────────────────────────────────────────────────────────────────

# 인스턴스의 OS SSH 접속 정보는 instance 테이블 자체에 저장하지 않고(컬럼은 하위호환을
# 위해 남아있지만 더 이상 쓰이지 않는다), 소속 서버(instance.group_id ->
# instance_group.server_id -> server)의 값을 조회 시점에 함께 읽어와 Instance 객체에
# 그대로 반영해준다 — Instance를 그대로 쓰던 restart/log 서비스 쪽 코드를 바꾸지 않아도
# 되도록 하기 위함이다. instance_group.server_id가 비어있는(예전 구조에서 넘어온) 고아
# 레코드도 있을 수 있어 LEFT JOIN을 쓴다.
_INSTANCE_SELECT_WITH_SERVER_SSH = '''
    SELECT instance.*,
           server.ssh_enabled  AS srv_ssh_enabled,
           server.ssh_host     AS srv_ssh_host,
           server.ssh_port     AS srv_ssh_port,
           server.ssh_user     AS srv_ssh_user,
           server.ssh_pass     AS srv_ssh_pass,
           server.ssh_key_path AS srv_ssh_key_path
    FROM instance
    LEFT JOIN instance_group ON instance.group_id = instance_group.id
    LEFT JOIN server ON instance_group.server_id = server.id
'''


def _row_to_instance(row: sqlite3.Row) -> Instance:
    return Instance(
        id=row['id'], group_id=row['group_id'], name=row['name'],
        host=row['host'], port=row['port'], type=row['type'],
        product=row['product'] or '', version=row['version'] or '',
        service_port=row['service_port'] or 8290,
        base_path=row['base_path'] or '',
        lib_path=row['lib_path'] or '/opt/wso2mi/lib',
        sequence_path=row['sequence_path'] or '/opt/wso2mi/repository/deployment/server/synapse-configs/default/sequences',
        jdbc_registry_path=row['jdbc_registry_path'] or 'registry/config/jdbc',
        environment=row['environment'] or 'DEV',
        admin_user=row['admin_user'], admin_pass=row['admin_pass'],
        token_url=row['token_url'], log_path=row['log_path'],
        api_log_max_mb=row['api_log_max_mb'] or 10,
        bin_path=row['bin_path'] or '/opt/wso2mi/bin',
        service_script=row['service_script'] or 'micro-integrator.sh',
        description=row['description'],
        ssh_enabled=bool(row['srv_ssh_enabled']),
        ssh_host=row['srv_ssh_host'] or '', ssh_port=row['srv_ssh_port'] or 22,
        ssh_user=row['srv_ssh_user'] or '', ssh_pass=row['srv_ssh_pass'] or '',
        ssh_key_path=row['srv_ssh_key_path'] or '',
        created_at=datetime.fromisoformat(row['created_at']) if row['created_at'] else None,
    )


def get_instances_by_group(group_id: int) -> List[Instance]:
    with get_connection() as conn:
        rows = conn.execute(
            _INSTANCE_SELECT_WITH_SERVER_SSH +
            ' WHERE instance.group_id=? ORDER BY instance.type COLLATE NOCASE, instance.name COLLATE NOCASE',
            (group_id,))
        return [_row_to_instance(r) for r in rows]


def get_all_instances() -> List[Instance]:
    with get_connection() as conn:
        rows = conn.execute(
            _INSTANCE_SELECT_WITH_SERVER_SSH +
            ' ORDER BY instance.type COLLATE NOCASE, instance.name COLLATE NOCASE')
        return [_row_to_instance(r) for r in rows]


def get_instance(instance_id: int) -> Optional[Instance]:
    with get_connection() as conn:
        row = conn.execute(
            _INSTANCE_SELECT_WITH_SERVER_SSH + ' WHERE instance.id=?', (instance_id,)).fetchone()
        return _row_to_instance(row) if row else None


def save_instance(inst: Instance) -> Instance:
    # OS SSH 접속 정보는 인스턴스에 저장하지 않는다(서버 단위로 관리, save_server 참고)
    # — instance 테이블의 ssh_* 컬럼은 하위호환을 위해 남아있지만 여기서 더 이상 쓰지
    # 않으므로 이 INSERT/UPDATE에도 포함하지 않는다.
    fields = (inst.group_id, inst.name, inst.host, inst.port, inst.service_port, inst.base_path, inst.lib_path,
               inst.sequence_path, inst.jdbc_registry_path, inst.type, inst.product, inst.version, inst.environment,
               inst.admin_user, inst.admin_pass,
               inst.token_url, inst.log_path, inst.api_log_max_mb, inst.bin_path, inst.service_script, inst.description)
    with transaction() as conn:
        if inst.id is None:
            cur = conn.execute('''
                INSERT INTO instance
                (group_id,name,host,port,service_port,base_path,lib_path,sequence_path,jdbc_registry_path,type,product,version,environment,
                 admin_user,admin_pass,token_url,log_path,api_log_max_mb,bin_path,service_script,description)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''', fields)
            inst.id = cur.lastrowid
        else:
            conn.execute('''
                UPDATE instance SET
                group_id=?,name=?,host=?,port=?,service_port=?,base_path=?,lib_path=?,sequence_path=?,jdbc_registry_path=?,type=?,
                product=?,version=?,
                environment=?,admin_user=?,admin_pass=?,token_url=?,log_path=?,api_log_max_mb=?,bin_path=?,
                service_script=?,description=?
                WHERE id=?''', fields + (inst.id,))
    return inst


def delete_instance(instance_id: int):
    with transaction() as conn:
        conn.execute('DELETE FROM instance WHERE id=?', (instance_id,))


def reset_all_data() -> dict:
    """등록된 모든 그룹/인스턴스/배포이력/감사로그/JNDI캐시/토큰캐시를 삭제한다.
    스키마는 그대로 두고 데이터만 비운다 (앱은 재기동 없이 계속 사용 가능).
    호출 전에 백업을 뜨는 건 호출부(bridge)의 책임 — 이 함수는 삭제만 한다."""
    tables = ('server_group', 'server', 'instance_group', 'instance', 'deployment_history',
              'audit_log', 'datasource_jndi_cache', 'token_cache')
    counts = {}
    with transaction() as conn:
        for t in tables:
            counts[t] = conn.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
        for t in tables:
            conn.execute(f'DELETE FROM "{t}"')
        conn.execute('DELETE FROM sqlite_sequence')
    with get_connection() as conn:
        conn.execute('VACUUM')
    return counts


# ── DeploymentHistory ────────────────────────────────────────────────────────

def _row_to_deployment(r: sqlite3.Row) -> DeploymentHistory:
    return DeploymentHistory(
        id=r['id'], instance_id=r['instance_id'],
        artifact_name=r['artifact_name'], artifact_type=r['artifact_type'],
        source_path=r['source_path'], backup_path=r['backup_path'],
        previous_backup_path=r['previous_backup_path'] or '',
        deployed_at=datetime.fromisoformat(r['deployed_at']) if r['deployed_at'] else None,
        deployed_by=r['deployed_by'], status=r['status'], reason=r['reason'] or '',
    )


def add_deployment(d: DeploymentHistory) -> DeploymentHistory:
    with transaction() as conn:
        cur = conn.execute('''
            INSERT INTO deployment_history
            (instance_id,artifact_name,artifact_type,source_path,backup_path,previous_backup_path,deployed_by,status,reason)
            VALUES (?,?,?,?,?,?,?,?,?)''',
            (d.instance_id, d.artifact_name, d.artifact_type,
             d.source_path, d.backup_path, d.previous_backup_path, d.deployed_by, d.status, d.reason))
        d.id = cur.lastrowid
    return d


def get_deployments(instance_id: int, limit: int = 50) -> List[DeploymentHistory]:
    with get_connection() as conn:
        rows = conn.execute(
            'SELECT * FROM deployment_history WHERE instance_id=? ORDER BY deployed_at DESC LIMIT ?',
            (instance_id, limit))
        return [_row_to_deployment(r) for r in rows]


def search_deployments(instance_id: int = None, artifact_name: str = None,
                        reason_keyword: str = None, date_from: str = None,
                        date_to: str = None, limit: int = 200) -> List[DeploymentHistory]:
    sql = 'SELECT * FROM deployment_history WHERE 1=1'
    params: list = []
    if instance_id:
        sql += ' AND instance_id=?'; params.append(instance_id)
    if artifact_name:
        sql += ' AND artifact_name LIKE ?'; params.append(f'%{artifact_name}%')
    if reason_keyword:
        sql += ' AND reason LIKE ?'; params.append(f'%{reason_keyword}%')
    if date_from:
        sql += ' AND deployed_at >= ?'; params.append(date_from)
    if date_to:
        sql += ' AND deployed_at <= ?'; params.append(date_to)
    sql += ' ORDER BY deployed_at DESC LIMIT ?'
    params.append(limit)
    with get_connection() as conn:
        rows = conn.execute(sql, params)
        return [_row_to_deployment(r) for r in rows]


# ── AuditLog ─────────────────────────────────────────────────────────────────

def add_audit(log: AuditLog) -> AuditLog:
    with transaction() as conn:
        cur = conn.execute('''
            INSERT INTO audit_log (action,target_name,instance_ids,operator,result,detail)
            VALUES (?,?,?,?,?,?)''',
            (log.action, log.target_name, log.instance_ids,
             log.operator, log.result, log.detail))
        log.id = cur.lastrowid
    return log


def _row_to_audit(r: sqlite3.Row) -> AuditLog:
    return AuditLog(
        id=r['id'],
        timestamp=datetime.fromisoformat(r['timestamp']) if r['timestamp'] else None,
        action=r['action'], target_name=r['target_name'],
        instance_ids=r['instance_ids'], operator=r['operator'],
        result=r['result'], detail=r['detail'],
    )


def get_audit_logs(limit: int = 200, action: str = '', result: str = '') -> List[AuditLog]:
    sql = 'SELECT * FROM audit_log WHERE 1=1'
    params: list = []
    if action:
        sql += ' AND action=?'; params.append(action)
    if result:
        sql += ' AND result=?'; params.append(result)
    sql += ' ORDER BY timestamp DESC LIMIT ?'
    params.append(limit)
    with get_connection() as conn:
        rows = conn.execute(sql, params)
        return [_row_to_audit(r) for r in rows]


# 재시작/정지는 감사로그(audit_log)에 action='RESTART_ALL'/'SHUTDOWN'으로 같이
# 기록된다 — 별도 테이블이 아니라 감사로그를 이 두 액션으로 걸러서 보여주는 뷰.
RESTART_HISTORY_ACTIONS = ('RESTART_ALL', 'SHUTDOWN')


def search_restart_history(instance_id: int = None, date_from: str = None,
                           date_to: str = None, limit: int = 200) -> List[AuditLog]:
    placeholders = ','.join('?' * len(RESTART_HISTORY_ACTIONS))
    sql = f'SELECT * FROM audit_log WHERE action IN ({placeholders})'
    params: list = list(RESTART_HISTORY_ACTIONS)
    if instance_id:
        # instance_ids는 "1,12,3" 형태의 콤마 구분 문자열이라, 앞뒤에 콤마를 붙여
        # 부분일치(예: id=1이 12를 잘못 매칭)를 방지한다.
        sql += " AND (',' || instance_ids || ',') LIKE ?"
        params.append(f'%,{instance_id},%')
    if date_from:
        sql += ' AND timestamp >= ?'; params.append(date_from)
    if date_to:
        sql += ' AND timestamp <= ?'; params.append(date_to)
    sql += ' ORDER BY timestamp DESC LIMIT ?'
    params.append(limit)
    with get_connection() as conn:
        rows = conn.execute(sql, params)
        return [_row_to_audit(r) for r in rows]


def clear_restart_history() -> int:
    """재시작/정지 이력(감사로그의 RESTART_ALL/SHUTDOWN 항목)만 삭제한다.
    같은 테이블(audit_log)을 쓰므로 감사 로그 탭에서도 이 항목들은 함께 사라진다."""
    placeholders = ','.join('?' * len(RESTART_HISTORY_ACTIONS))
    with transaction() as conn:
        cur = conn.execute(
            f'DELETE FROM audit_log WHERE action IN ({placeholders})',
            RESTART_HISTORY_ACTIONS)
        return cur.rowcount


# ── 데이터소스 JNDI 이름 캐시 ────────────────────────────────────────────────
# CAR을 다운로드해 파싱해야 알 수 있는 실제 JNDI 이름을 인스턴스별로 캐싱해둔다.
# 한 번 조회된 데이터소스는 그 다음부터 CAR 다운로드 없이 이 캐시만 참조한다.

def get_cached_jndi(instance_id: int, ds_name: str) -> Optional[str]:
    with get_connection() as conn:
        row = conn.execute(
            'SELECT jndi_name FROM datasource_jndi_cache WHERE instance_id=? AND ds_name=?',
            (instance_id, ds_name)).fetchone()
        return row['jndi_name'] if row else None


def set_cached_jndi(instance_id: int, ds_name: str, jndi_name: str):
    with transaction() as conn:
        conn.execute('''
            INSERT INTO datasource_jndi_cache (instance_id, ds_name, jndi_name, updated_at)
            VALUES (?, ?, ?, datetime('now','localtime'))
            ON CONFLICT(instance_id, ds_name)
            DO UPDATE SET jndi_name=excluded.jndi_name, updated_at=excluded.updated_at
        ''', (instance_id, ds_name, jndi_name))


def clear_cached_jndi_for_instance(instance_id: int) -> int:
    """이 인스턴스의 JNDI 캐시를 전부 지운다. 반환값: 삭제된 항목 수."""
    with transaction() as conn:
        cur = conn.execute('DELETE FROM datasource_jndi_cache WHERE instance_id=?', (instance_id,))
        return cur.rowcount


# ── Management API 토큰 캐시 ────────────────────────────────────────────────
# 앱을 껐다 켤 때마다 메모리 캐시(api/base_client.py의 _token_cache)가 초기화되어
# 매번 재로그인하면서 서버 로그인 로그가 도배되는 것을 막기 위해, 아직 만료 전인
# 토큰은 DB에도 남겨뒀다가 재시작 후 그대로 재사용한다.

def get_cached_token(instance_id: int) -> Optional[tuple]:
    with get_connection() as conn:
        row = conn.execute(
            'SELECT token, expires_at FROM token_cache WHERE instance_id=?',
            (instance_id,)).fetchone()
        return (row['token'], row['expires_at']) if row else None


def set_cached_token(instance_id: int, token: str, expires_at: float):
    with transaction() as conn:
        conn.execute('''
            INSERT INTO token_cache (instance_id, token, expires_at, updated_at)
            VALUES (?, ?, ?, datetime('now','localtime'))
            ON CONFLICT(instance_id)
            DO UPDATE SET token=excluded.token, expires_at=excluded.expires_at, updated_at=excluded.updated_at
        ''', (instance_id, token, expires_at))


def delete_cached_token(instance_id: int):
    with transaction() as conn:
        conn.execute('DELETE FROM token_cache WHERE instance_id=?', (instance_id,))
