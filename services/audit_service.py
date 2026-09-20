"""모든 작업 행위를 AuditLog에 기록하는 서비스."""
from datetime import datetime
from typing import List

from db.database import add_audit, get_audit_logs
from db.models import AuditLog, Instance


def log_action(
    action: str,
    instances: List[Instance],
    result: str,
    target_name: str = '',
    detail: str = '',
    operator: str = 'admin',
) -> AuditLog:
    ids = ','.join(str(i.id) for i in instances if i.id)
    names = ', '.join(i.name for i in instances)
    entry = AuditLog(
        timestamp=datetime.now(),
        action=action,
        target_name=target_name or names,
        instance_ids=ids,
        operator=operator,
        result=result,
        detail=detail,
    )
    return add_audit(entry)


def get_logs(limit: int = 200, action: str = '', result: str = '') -> List[AuditLog]:
    return get_audit_logs(limit=limit, action=action, result=result)
