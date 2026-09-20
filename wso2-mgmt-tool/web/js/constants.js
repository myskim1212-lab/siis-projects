/* 인스턴스 세부 타입 — db/models.py의 INSTANCE_TYPES/DEPLOYABLE_TYPES와 동일하게 유지.
 * colorClass는 타입을 한눈에 구분하기 위한 글자색(트리/배포/재시작 목록에서 사용). */
const INSTANCE_TYPES = [
  { value: 'MI_ALL_IN_ONE', label: 'MI ALL_IN_ONE', short: 'MI·AIO', colorClass: 'type-color-mi-aio' },
  { value: 'MI_MANAGER', label: 'MI Manager', short: 'MI·Mgr', colorClass: 'type-color-mi-mgr' },
  { value: 'MI_RUNTIME', label: 'MI Runtime', short: 'MI·Run', colorClass: 'type-color-mi-run' },
  { value: 'APIM_ALL_IN_ONE', label: 'APIM ALL_IN_ONE', short: 'APIM·AIO', colorClass: 'type-color-apim-aio' },
  { value: 'APIM_MANAGER', label: 'APIM Manager', short: 'APIM·Mgr', colorClass: 'type-color-apim-mgr' },
  { value: 'APIM_GATEWAY', label: 'APIM Gateway', short: 'APIM·GW', colorClass: 'type-color-apim-gw' },
];

const DEPLOYABLE_TYPES = new Set(['MI_ALL_IN_ONE', 'MI_MANAGER', 'APIM_ALL_IN_ONE', 'APIM_MANAGER']);

function instanceTypeShortLabel(value) {
  return INSTANCE_TYPES.find((t) => t.value === value)?.short || value;
}

function instanceTypeColorClass(value) {
  return INSTANCE_TYPES.find((t) => t.value === value)?.colorClass || '';
}

/* 제품명 / 버전 — db/models.py의 PRODUCTS/VERSIONS와 동일하게 유지.
 * INSTANCE_TYPES(프로파일 단위 구분)와 별개로 실제 배포된 WSO2 제품/버전을 기록한다. */
const PRODUCTS = ['EI', 'MI', 'APIM'];
const VERSIONS = ['2.1', '6.1', '6.6', '4.3', '4.5', '4.6'];

/* 개발 / 운영 환경 구분 — 운영 인스턴스는 재시작·정지 시 강한 경고가 필요하다 */
const ENVIRONMENTS = [
  { value: 'DEV', label: '개발' },
  { value: 'PROD', label: '운영' },
];

function environmentLabel(value) {
  return ENVIRONMENTS.find((e) => e.value === value)?.label || value;
}

/* 서버 운영 환경 구분 — db/models.py의 SERVER_ENVIRONMENTS와 동일하게 유지.
 * 인스턴스와 달리 한 서버에 운영/개발 인스턴스가 동시에 뜰 수 있어 BOTH(운영개발 동시사용)가 있다. */
const SERVER_ENVIRONMENTS = [
  { value: 'DEV', label: '개발' },
  { value: 'PROD', label: '운영' },
  { value: 'BOTH', label: '운영개발' },
];

function serverEnvironmentLabel(value) {
  return SERVER_ENVIRONMENTS.find((e) => e.value === value)?.label || value;
}

function serverEnvironmentClass(value) {
  if (value === 'PROD') return 'env-prod';
  if (value === 'BOTH') return 'env-both';
  return 'env-dev';
}

/* 서버 select 옵션 라벨 — 개발/운영 서버 이름이 같으면 드롭다운에서 구분이 안 되는
 * 문제가 있어, 어디서 서버를 목록에 넣든 항상 환경 표시를 같이 붙인다. */
function serverOptionLabel(s) {
  return `${s.name} (${serverEnvironmentLabel(s.environment)})`;
}
