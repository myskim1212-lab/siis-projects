/* 데이터소스 조회 + 접속 테스트 — 좌측에서 선택한 인스턴스를 대상으로 동작
 * 검색어가 있으면 MI의 searchKey 검색 API를 서버 쪽에서 호출해 그 결과만 가져온다
 * (전체 목록을 받아서 화면에서 거르는 방식이 아님). */
const DataSources = (() => {
  let currentInstance = null;
  let currentItems = [];
  let eiAllItems = []; // EI 모드에서 전체 목록 캐시 (검색은 서버 API 없이 여기서 클라이언트 필터링)
  let eiMode = false;
  let checkedNames = new Set();
  let testResults = {}; // name -> 마지막 접속 테스트 결과 (MI: dbinfo 응답, EI: SOAP testDataSourceConnection 결과)
  let instSelect = null; // 서버그룹>서버>그룹>인스턴스 선택 — ui.js의 UI.wireInstanceCascade

  /* 제품명이 EI인 인스턴스는 버전과 무관하게 EI 모드 — EI는 MI Management API
   * 같은 REST가 없어 Carbon의 NDataSourceAdmin SOAP 웹서비스로만 데이터소스를
   * 다루는데, 이 서비스는 EI 6.1~6.6 전체에서 안정적으로 동일하다
   * (api/ei_client.py, bridge/api.py의 *_ei_datasource* 메서드 참고). */
  function isEiInstance(inst) {
    return !!inst && inst.product === 'EI';
  }

  function setLabel() {
    const label = document.getElementById('ds-instance-label');
    if (!currentInstance) {
      label.textContent = '좌측에서 인스턴스를 선택하세요.';
      return;
    }
    const tag = eiMode ? `${currentInstance.product} ${currentInstance.version}` : instanceTypeShortLabel(currentInstance.type);
    label.textContent = `[${tag}] ${currentInstance.name} (${currentInstance.host}:${currentInstance.port})`;
  }

  /* EI 전용 버튼(등록)과 MI 전용 버튼(JNDI 캐시 초기화, 여러 인스턴스 테스트로 보내기)을
   * 모드에 맞춰 보이거나 숨긴다. */
  function updateModeButtons() {
    const show = (id, visible) => { const el = document.getElementById(id); if (el) el.style.display = visible ? '' : 'none'; };
    show('btn-ds-ei-add', eiMode);
    show('ds-ei-hint', eiMode);
    show('btn-ds-clear-jndi-cache', !eiMode);
    // "여러 인스턴스 테스트로 보내기"는 EI/MI 둘 다 지원 — EI는 JNDI가 아니라
    // 데이터소스 등록명으로, MI는 dbinfo API로 각각 다르게 동작한다(아래 참고).
    // 모드가 바뀌면 대상 인스턴스 체크리스트도 다시 걸러야 하므로(EI↔MI 섞이면 안 됨)
    // 필터 변경 때와 마찬가지로 선택을 비운다.
    updateMultiJndiModeLabels();
    updateMultiModeUI();
    multiCheckedIds.clear();
    renderMultiInstanceList();
  }

  /* "JNDI 이름" 입력칸은 MI 전용 개념이라(EI는 등록명 자체가 식별자), 모드에 따라
   * placeholder/안내문구를 바꿔준다. */
  function updateMultiJndiModeLabels() {
    const input = document.getElementById('ds-multi-jndi');
    const hint = document.getElementById('ds-multi-hint');
    const header = document.getElementById('ds-multi-result-id-header');
    if (eiMode) {
      input.placeholder = '데이터소스 등록명 (,로 구분해 여러 개 입력 가능 — 예: SST0001_ORACLE, SST0002_MSSQL)';
      hint.textContent = 'EI는 등록명 그대로 입력하세요(JNDI 이름이 아님). 여러 개를 ,로 구분해 입력하면 선택한 인스턴스마다 전부 테스트합니다.';
      header.textContent = '데이터소스 등록명';
    } else {
      input.placeholder = 'JNDI 이름 (,로 구분해 여러 개 입력 가능 — 예: jdbc/SST0001, jdbc/SST0002)';
      hint.textContent = '데이터소스 등록명(예: SST0002_MSSQL)이 아니라 JNDI 이름(예: jdbc/SST0002, "_" 앞부분만)을 입력하세요. 여러 JNDI 이름을 ,로 구분해 입력하면 선택한 인스턴스마다 전부 테스트합니다.';
      header.textContent = 'JNDI 이름';
    }
  }

  function renderMessage(text) {
    document.querySelector('#ds-table tbody').innerHTML =
      `<tr><td colspan="5" class="hint">${UI.esc(text)}</td></tr>`;
  }

  function eiStatusCellHtml(ds) {
    if (ds.status_mode === 'ACTIVE') return '<span class="result-success">✅ ACTIVE</span>';
    if (ds.status_mode) return `<span class="result-failed">❌ ${UI.esc(ds.status_mode)}</span>`;
    return '<span class="hint">-</span>';
  }

  function renderRows() {
    if (!currentItems.length) { renderMessage('검색 결과가 없습니다.'); return; }
    const tbody = document.querySelector('#ds-table tbody');
    tbody.innerHTML = '';
    for (const ds of currentItems) {
      const tr = document.createElement('tr');
      tr.dataset.name = ds.name;
      tr.innerHTML = `
        <td><input type="checkbox"></td>
        <td class="ds-name-cell" style="cursor:pointer">${UI.esc(ds.name)}</td>
        <td>${UI.esc(eiMode ? (ds.type || 'RDBMS') : ds.type)}</td>
        <td class="ds-status-cell">${eiMode ? eiStatusCellHtml(ds) : '-'}</td>
        <td class="ds-manage-cell"></td>`;
      const cb = tr.querySelector('input');
      cb.checked = checkedNames.has(ds.name);
      cb.addEventListener('change', () => {
        if (cb.checked) checkedNames.add(ds.name); else checkedNames.delete(ds.name);
      });
      tr.querySelector('.ds-name-cell').addEventListener('click', () => {
        if (eiMode) openEiDatasourceDialog(ds); else showDetail(ds.name);
      });
      tr.querySelector('.ds-status-cell').addEventListener('click', () => {
        if (!eiMode) { showTestResult(ds.name); return; }
        if (testResults[ds.name]) showEiTestResult(ds.name); else testEiRow(ds.name);
      });
      if (eiMode) {
        const delBtn = document.createElement('button');
        delBtn.className = 'btn btn-sm';
        delBtn.textContent = '삭제';
        delBtn.addEventListener('click', (e) => { e.stopPropagation(); deleteEiRow(ds.name); });
        tr.querySelector('.ds-manage-cell').appendChild(delBtn);
      }
      tbody.appendChild(tr);
    }
  }

  function resetState() {
    currentItems = [];
    eiAllItems = [];
    checkedNames.clear();
    testResults = {};
  }

  /* 인스턴스만 선택된 상태 — MI는 아직 아무것도 조회하지 않지만(자동 전체 조회 안 함),
   * EI는 목록 조회 자체가 가벼운 SOAP 호출 한 번이라 바로 불러온다. */
  function showPrompt() {
    setLabel();
    resetState();
    document.getElementById('ds-search').value = '';
    eiMode = isEiInstance(currentInstance);
    updateModeButtons();
    if (!currentInstance) { renderMessage('인스턴스를 선택하면 데이터소스 목록이 표시됩니다.'); return; }
    if (eiMode) { loadEi(); return; }
    if (!currentInstance.type.startsWith('MI_')) {
      renderMessage('MI 인스턴스 또는 EI 6.1 인스턴스에서만 데이터소스 관리가 가능합니다.');
      return;
    }
    renderMessage('검색어를 입력하거나 "전체 목록" 버튼을 눌러 조회하세요.');
  }

  async function loadEi() {
    renderMessage('불러오는 중...');
    const res = await Api.call('list_ei_datasources', currentInstance.id);
    if (!res.success) { renderMessage(`조회 실패: ${res.error || ''}`); return; }
    eiAllItems = res.items;
    currentItems = res.items;
    if (!eiAllItems.length) { renderMessage('등록된 데이터소스가 없습니다. "데이터소스 등록" 버튼으로 새로 추가하세요.'); return; }
    renderRows();
  }

  async function load() {
    setLabel();
    resetState();
    document.getElementById('ds-search').value = '';
    eiMode = isEiInstance(currentInstance);
    updateModeButtons();
    if (!currentInstance) { renderMessage('인스턴스를 선택하면 데이터소스 목록이 표시됩니다.'); return; }
    if (eiMode) { await loadEi(); return; }
    if (!currentInstance.type.startsWith('MI_')) {
      renderMessage('MI 인스턴스 또는 EI 6.1 인스턴스에서만 데이터소스 관리가 가능합니다.');
      return;
    }
    renderMessage('불러오는 중...');
    const res = await Api.call('list_datasources', currentInstance.id);
    if (!res.success) { renderMessage(`조회 실패: ${res.error || ''}`); return; }
    if (!res.items.length) { renderMessage('등록된 데이터소스가 없습니다.'); return; }
    currentItems = res.items;
    renderRows();
  }

  /* 데이터소스명은 MI 자체 searchKey API로 서버에서 바로 걸러진 결과만 받아오지만,
   * JNDI 이름/설명은 MI의 데이터소스 목록 API에 아예 포함되지 않는 필드라(각각 별도
   * 조회 필요 — JNDI는 배포된 CAR을 읽어야 하고, 설명은 단건 상세 조회로만 나온다)
   * 전체 목록을 받은 뒤 항목마다 그 필드를 조회해 클라이언트에서 걸러낼 수밖에 없다. */
  async function runSearch() {
    if (!currentInstance) return;
    const raw = document.getElementById('ds-search').value;
    const terms = raw.split(',').map((t) => t.trim()).filter(Boolean);
    if (!terms.length) { await load(); return; }
    const field = document.getElementById('ds-search-field').value;

    /* EI는 목록이 이미 전부 메모리에 있으므로(eiAllItems) 서버 재조회 없이 여기서
     * 바로 걸러낸다 — MI처럼 이름/JNDI/설명별로 별도 API를 호출할 필요가 없다. */
    if (eiMode) {
      checkedNames.clear();
      const lowerTerms = terms.map((t) => t.toLowerCase());
      const matchesTerms = (value) => !!value && lowerTerms.some((t) => value.toLowerCase().includes(t));
      const key = field === 'jndi' ? 'jndi_name' : field === 'description' ? 'description' : 'name';
      currentItems = eiAllItems.filter((ds) => matchesTerms(ds[key]));
      renderRows();
      return;
    }

    resetState();

    if (field === 'name') {
      renderMessage('검색 중...');
      const res = await Api.call('search_datasources', currentInstance.id, terms);
      if (!res.success) { renderMessage(`검색 실패: ${res.error || ''}`); return; }
      currentItems = res.items;
      renderRows();
      return;
    }

    renderMessage('검색 중... (전체 목록을 받아와 항목별로 조회하는 중이라 다소 걸릴 수 있습니다)');
    const listRes = await Api.call('list_datasources', currentInstance.id);
    if (!listRes.success) { renderMessage(`검색 실패: ${listRes.error || ''}`); return; }
    const all = listRes.items;
    if (!all.length) { renderMessage('등록된 데이터소스가 없습니다.'); return; }

    const lowerTerms = terms.map((t) => t.toLowerCase());
    const matchesTerms = (value) => !!value && lowerTerms.some((t) => value.toLowerCase().includes(t));

    let matched;
    if (field === 'jndi') {
      const jndiResults = await Promise.all(
        all.map((ds) => Api.call('get_datasource_jndi_name', currentInstance.id, ds.name, false)));
      matched = all.filter((ds, i) => matchesTerms(jndiResults[i].success ? jndiResults[i].jndi_name : ''));
    } else { // description
      const detailResults = await Promise.all(
        all.map((ds) => Api.call('get_datasource', currentInstance.id, ds.name)));
      matched = all.filter((ds, i) => matchesTerms(detailResults[i].success ? detailResults[i].data.description : ''));
    }
    currentItems = matched;
    renderRows();
  }

  function selectAll() { checkedNames = new Set(currentItems.map((i) => i.name)); renderRows(); }
  function selectNone() { checkedNames.clear(); renderRows(); }

  /* 지금 화면에 조회돼 있는 데이터소스 목록을 CSV로 내보낸다 — 접속 테스트를
   * 해본 항목은 그 결과(성공/실패, DB타입, URL 등)도 같이 담는다. */
  /* 이름/타입 같은 요약 정보뿐 아니라 등록된 설정 전체(드라이버/URL/사용자/커넥션 풀
   * 옵션 등)를 내보낸다. EI는 목록 조회(getAllDataSources) 자체가 이미 전체 설정을
   * 포함하지만(_parse_ds_info), MI의 목록 API(GET /data-sources)는 요약만 주므로
   * 데이터소스마다 상세 조회(get_datasource)를 한 번씩 더 호출해서 모은다.
   *
   * 컬럼을 하드코딩하지 않고 실제 응답에 있는 키를 그대로 펼치는 방식이라, 두 제품의
   * 필드 이름이 서로 달라도(EI: driver_class_name/username, MI: driverClass/userName)
   * 항상 "설정된 값 전부"가 빠짐없이 나간다. 비밀번호 계열 키("password"가 들어간
   * 키)는 응답에 있더라도 방어적으로 제외한다. */
  async function exportDsList() {
    if (!currentItems.length) { UI.toast('내보낼 데이터소스가 없습니다.', 'error'); return; }

    let fullItems;
    if (eiMode) {
      fullItems = currentItems;
    } else {
      UI.toast(`전체 설정 조회 중... (${currentItems.length}개)`);
      const results = await Promise.all(
        currentItems.map((ds) => Api.call('get_datasource', currentInstance.id, ds.name)));
      fullItems = results.map((r, i) => (r.success && r.data)
        ? { name: currentItems[i].name, ...r.data }
        : { name: currentItems[i].name, 조회실패: r.error || '' });
    }

    const keyOrder = [];
    const seen = new Set();
    for (const it of fullItems) {
      for (const k of Object.keys(it)) {
        if (/pass/i.test(k)) continue;
        if (!seen.has(k)) { seen.add(k); keyOrder.push(k); }
      }
    }
    const toCell = (v) => {
      if (v === null || v === undefined) return '';
      if (typeof v === 'object') return JSON.stringify(v);
      return String(v);
    };

    const headers = [...keyOrder, '접속상태', 'DB타입', '응답시간(ms)', '오류'];
    const rows = fullItems.map((it) => {
      const r = testResults[it.name];
      return [
        ...keyOrder.map((k) => toCell(it[k])),
        r ? (r.success ? '성공' : '실패') : '미테스트',
        r ? (r.db_type || '') : '',
        r && r.elapsed_ms != null ? String(r.elapsed_ms) : '',
        r ? (r.error || '') : '',
      ];
    });
    const instLabel = currentInstance ? currentInstance.name : 'instance';
    UI.exportCsv(`datasources_${instLabel}_${Date.now()}.csv`, headers, rows);
  }

  function setStatusCell(name, html, className) {
    const tr = document.querySelector(`#ds-table tr[data-name="${CSS.escape(name)}"]`);
    if (!tr) return;
    const cell = tr.querySelector('.ds-status-cell');
    cell.innerHTML = html;
    cell.className = `ds-status-cell ${className || ''}`.trim();
  }

  function applyResult(r) {
    testResults[r.name] = r;
    if (r.success) {
      setStatusCell(r.name, `✅ ${UI.esc(r.db_type || '')} (${r.elapsed_ms ?? '-'}ms) — 상세보기`, 'result-success');
    } else {
      setStatusCell(r.name, `❌ ${UI.esc(r.error || '실패')} — 상세보기`, 'result-failed');
    }
  }

  function showTestResult(name) {
    const r = testResults[name];
    if (!r) { UI.toast('먼저 접속 테스트를 실행하세요.'); return; }
    UI.detailModal(`접속 테스트 결과: ${name}`, [
      ['JNDI 이름', r.jndi_name], ['결과', r.success ? '성공' : '실패'],
      ['DB 타입', r.db_type], ['제품', r.product], ['버전', r.version],
      ['URL', r.url], ['사용자', r.user], ['응답 시간', r.elapsed_ms != null ? `${r.elapsed_ms}ms` : null],
      ['오류', r.error],
    ], 560);
  }

  // ── EI 6.1 데이터소스 (등록/조회/수정/삭제 — NDataSourceAdmin 웹서비스) ─

  function applyEiResult(name, r) {
    testResults[name] = r;
    if (r.success) {
      setStatusCell(name, `✅ 성공 (${r.elapsed_ms ?? '-'}ms) — 상세보기`, 'result-success');
    } else {
      setStatusCell(name, `❌ ${UI.esc(r.error || '실패')} — 상세보기`, 'result-failed');
    }
  }

  function showEiTestResult(name) {
    const r = testResults[name];
    if (!r) { testEiRow(name); return; }
    UI.detailModal(`접속 테스트 결과: ${name}`, [
      ['결과', r.success ? '성공' : '실패'],
      ['응답 시간', r.elapsed_ms != null ? `${r.elapsed_ms}ms` : null],
      ['오류', r.error],
    ], 460, [{ label: '다시 테스트', onClick: () => testEiRow(name) }]);
  }

  /* 이미 저장된 데이터소스를 이름으로 재테스트 — 비밀번호는 서버(Python)에서만
   * 조회해 쓰고 이 화면으로는 절대 넘어오지 않는다. */
  async function testEiRow(name) {
    setStatusCell(name, '테스트 중...', '');
    const res = await Api.call('test_ei_datasource_existing', currentInstance.id, name);
    applyEiResult(name, res);
  }

  async function deleteEiRow(name) {
    const ok = await UI.confirm(`데이터소스 [${name}]을(를) 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.`, '삭제 확인');
    if (!ok) return;
    const res = await Api.call('delete_ei_datasource', currentInstance.id, name);
    if (!res.success) { UI.toast(`삭제 실패: ${res.error || ''}`, 'error'); return; }
    delete testResults[name];
    UI.toast('삭제되었습니다.');
    await loadEi();
  }

  /* RDBMSConfiguration(carbon-kernel)의 전체 커넥션 풀/동작 옵션 — driverClassName/
   * url/username/password(핵심 4개)를 제외한 나머지 전부. api/ei_client.py의
   * POOL_FIELDS와 키를 정확히 맞춰야 한다. type:
   *   'bool' → (기본값)/true/false 3단 선택 (미선택=설정 안 함, 서버 기본값 사용)
   *   'enum' → 정해진 값 중 선택
   *   'number'/'text' → 비워두면 설정 안 함 */
  const EI_POOL_FIELD_GROUPS = [
    { title: '커넥션 풀 크기', fields: [
      { key: 'maxActive', label: 'Max Active', type: 'number' },
      { key: 'maxIdle', label: 'Max Idle', type: 'number' },
      { key: 'minIdle', label: 'Min Idle', type: 'number' },
      { key: 'initialSize', label: 'Initial Size', type: 'number' },
      { key: 'maxWait', label: 'Max Wait (ms)', type: 'number' },
    ] },
    { title: '유효성 검사', fields: [
      { key: 'validationQuery', label: 'Validation Query', type: 'text' },
      { key: 'validatorClassName', label: 'Validator Class Name', type: 'text' },
      { key: 'testOnBorrow', label: 'Test On Borrow', type: 'bool' },
      { key: 'testOnReturn', label: 'Test On Return', type: 'bool' },
      { key: 'testWhileIdle', label: 'Test While Idle', type: 'bool' },
      { key: 'validationInterval', label: 'Validation Interval (ms)', type: 'number' },
      { key: 'validationQueryTimeout', label: 'Validation Query Timeout (초)', type: 'number' },
      { key: 'timeBetweenEvictionRunsMillis', label: 'Time Between Eviction Runs (ms)', type: 'number' },
      { key: 'numTestsPerEvictionRun', label: 'Num Tests Per Eviction Run', type: 'number' },
      { key: 'minEvictableIdleTimeMillis', label: 'Min Evictable Idle Time (ms)', type: 'number' },
    ] },
    { title: '방치 커넥션 처리', fields: [
      { key: 'removeAbandoned', label: 'Remove Abandoned', type: 'bool' },
      { key: 'removeAbandonedTimeout', label: 'Remove Abandoned Timeout (초)', type: 'number' },
      { key: 'logAbandoned', label: 'Log Abandoned', type: 'bool' },
      { key: 'suspectTimeout', label: 'Suspect Timeout (초)', type: 'number' },
    ] },
    { title: '트랜잭션 / 커넥션 기본값', fields: [
      { key: 'defaultAutoCommit', label: 'Default Auto Commit', type: 'bool' },
      { key: 'defaultReadOnly', label: 'Default Read Only', type: 'bool' },
      { key: 'defaultTransactionIsolation', label: 'Default Transaction Isolation', type: 'enum',
        options: ['NONE', 'READ_COMMITTED', 'READ_UNCOMMITTED', 'REPEATABLE_READ', 'SERIALIZABLE'] },
      { key: 'defaultCatalog', label: 'Default Catalog', type: 'text' },
    ] },
    { title: '기타', fields: [
      { key: 'accessToUnderlyingConnectionAllowed', label: 'Access To Underlying Connection Allowed', type: 'bool' },
      { key: 'connectionProperties', label: 'Connection Properties', type: 'text' },
      { key: 'initSQL', label: 'Init SQL', type: 'text' },
      { key: 'jdbcInterceptors', label: 'JDBC Interceptors', type: 'text' },
      { key: 'jmxEnabled', label: 'JMX Enabled', type: 'bool' },
      { key: 'fairQueue', label: 'Fair Queue', type: 'bool' },
      { key: 'abandonWhenPercentageFull', label: 'Abandon When Percentage Full', type: 'number' },
      { key: 'maxAge', label: 'Max Age (ms)', type: 'number' },
      { key: 'useEquals', label: 'Use Equals', type: 'bool' },
      { key: 'alternateUsernameAllowed', label: 'Alternate Username Allowed', type: 'bool' },
    ] },
  ];

  /* DB 엔진별 등록 템플릿 — 실제 운영 중인 WSO2 EI 콘솔 등록 화면의 기본값을
   * 그대로 따르되(공통 풀 설정), validationQuery만 엔진에 맞게 고쳐서 쓴다.
   * "select 1 from dual"은 Oracle/Tibero(오라클 호환) 전용 문법이라 DUAL 테이블이
   * 없는 MSSQL·PostgreSQL에 그대로 쓰면 접속 테스트/유효성 검사가 항상 실패한다. */
  const EI_DB_TEMPLATES = {
    oracle: {
      label: 'Oracle',
      driverClassName: 'oracle.jdbc.OracleDriver',
      urlPlaceholder: '예: jdbc:oracle:thin:@호스트:1521:SID 또는 jdbc:oracle:thin:@//호스트:1521/서비스명',
      validationQuery: 'SELECT 1 FROM DUAL',
    },
    mssql: {
      label: 'Microsoft SQL Server',
      driverClassName: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
      urlPlaceholder: '예: jdbc:sqlserver://호스트:1433;databaseName=DB명',
      validationQuery: 'SELECT 1',
    },
    postgresql: {
      label: 'PostgreSQL',
      driverClassName: 'org.postgresql.Driver',
      urlPlaceholder: '예: jdbc:postgresql://호스트:5432/DB명',
      validationQuery: 'SELECT 1',
    },
    tibero: {
      label: 'Tibero',
      // Tibero JDBC 드라이버 클래스명은 티베로 버전에 따라 다를 수 있어(구버전 "Tibero" ↔
      // 최신 버전 "com.tmax.tibero.jdbc.TbDriver") 실제 tibero-jdbc.jar와 맞는지 확인 필요.
      driverClassName: 'com.tmax.tibero.jdbc.TbDriver',
      urlPlaceholder: '예: jdbc:tibero:thin:@호스트:8629:DB명',
      validationQuery: 'SELECT 1 FROM DUAL', // Tibero는 Oracle 호환이라 DUAL 테이블 존재
    },
  };

  // 스크린샷 기준 WSO2 EI 콘솔 등록 화면의 커넥션 풀 기본값 (DB 엔진과 무관하게 공통).
  // maxWait/removeAbandoned 계열은 스크린샷 원값 대신 운영 안전성을 위해 조정한 값 —
  // 근거는 datasources.js 커밋 이력의 "EI 기본 데이터소스 설정 평가" 논의 참고:
  //  - maxWait 60000ms(60초)는 풀 고갈 시 ESB 워커 스레드를 너무 오래 붙잡아 장애가
  //    전체로 번질 위험이 있어 8초로 단축(빨리 실패시켜 상위에서 대응하게 함).
  //  - removeAbandoned/logAbandoned는 꺼두면 커넥션 누수가 조용히 풀을 고갈시켜도
  //    로그에 아무 흔적이 안 남는다 — 켜두되 removeAbandonedTimeout을 5분으로 넉넉히
  //    잡아 정상적으로 오래 걸리는 쿼리까지 강제 회수하지 않게 한다.
  const EI_COMMON_POOL_DEFAULTS = {
    defaultAutoCommit: 'false',
    defaultReadOnly: 'false',
    maxActive: '50',
    minIdle: '0',
    maxWait: '8000',
    testOnBorrow: 'false',
    testOnReturn: 'false',
    testWhileIdle: 'true',
    timeBetweenEvictionRunsMillis: '180000',
    minEvictableIdleTimeMillis: '300000',
    accessToUnderlyingConnectionAllowed: 'false',
    removeAbandoned: 'true',
    removeAbandonedTimeout: '300',
    logAbandoned: 'true',
    fairQueue: 'false',
    jmxEnabled: 'false',
    maxAge: '0',
    useEquals: 'false',
  };

  function detectEiTemplateKey(driverClassName) {
    for (const [key, tpl] of Object.entries(EI_DB_TEMPLATES)) {
      if (tpl.driverClassName === driverClassName) return key;
    }
    return '';
  }

  function eiFieldInputHtml(f, existingValue) {
    const id = `ei-adv-${f.key}`;
    const val = existingValue != null ? String(existingValue) : '';
    if (f.type === 'bool') {
      return `<select id="${id}">
        <option value="">(기본값)</option>
        <option value="true" ${val === 'true' ? 'selected' : ''}>true</option>
        <option value="false" ${val === 'false' ? 'selected' : ''}>false</option>
      </select>`;
    }
    if (f.type === 'enum') {
      return `<select id="${id}"><option value="">(기본값)</option>${f.options
        .map((o) => `<option value="${o}" ${val === o ? 'selected' : ''}>${o}</option>`).join('')}</select>`;
    }
    return `<input type="${f.type === 'number' ? 'number' : 'text'}" id="${id}" value="${UI.esc(val)}">`;
  }

  function eiAdvancedSectionHtml(existing) {
    return EI_POOL_FIELD_GROUPS.map((group) => `
      <div class="field-group">
        <div class="field-group-title">${UI.esc(group.title)}</div>
        ${group.fields.map((f) => `<div class="field-row"><label>${UI.esc(f.label)}</label>${eiFieldInputHtml(f, existing ? existing[f.key] : null)}</div>`).join('')}
      </div>`).join('');
  }

  function collectEiAdvancedFields(backdrop) {
    const out = {};
    for (const group of EI_POOL_FIELD_GROUPS) {
      for (const f of group.fields) {
        const v = backdrop.querySelector(`#ei-adv-${f.key}`).value;
        if (v !== '') out[f.key] = v;
      }
    }
    return out;
  }

  /* 신규 등록 시에만 쓰는 배포 대상 인스턴스 체크리스트 — 기본으로는 지금 보고 있는
   * 인스턴스 하나만 체크돼 있어서, 아무것도 안 건드리면 예전과 동일하게 동작한다.
   * 수정(isEdit)은 이미 존재하는 특정 인스턴스의 특정 데이터소스를 고치는 것이라
   * "여러 곳에 배포"라는 개념 자체가 안 맞아서 대상에 포함하지 않는다.
   *
   * "여러 인스턴스에서 접속 테스트"(multiVisibleInstances 등)와 같은 서버그룹/서버타입/
   * 서버/그룹 필터를 쓰되, EI 인스턴스 자체가 보통 많지 않아 서버그룹을 고르기 전에도
   * 기본으로 전체를 보여준다 — 그래야 기본 체크된 currentInstance가 처음부터 보인다.
   * 필터를 바꾸면(=사용자가 직접 좁혔으면) 배포/재시작 탭과 같은 이유로 체크를
   * 비운다(의도와 다른 대상에 잘못 등록되는 사고 방지).
   */
  function eiTargetVisibleInstances(backdrop) {
    const sgId = backdrop.querySelector('#ei-target-sg-filter').value;
    const typeId = backdrop.querySelector('#ei-target-type-filter').value;
    const serverId = backdrop.querySelector('#ei-target-server-filter').value;
    const groupId = backdrop.querySelector('#ei-target-group-filter').value;
    return Instances.getFlatInstances().filter((i) =>
      i.product === 'EI' &&
      (!sgId || String(i.server_group_id) === sgId) &&
      (!typeId || i.server_environment === typeId) &&
      (!serverId || String(i.server_id) === serverId) &&
      (!groupId || String(i.group_id) === groupId));
  }

  function renderEiTargetServerFilterOptions(backdrop) {
    const sgId = backdrop.querySelector('#ei-target-sg-filter').value;
    const typeId = backdrop.querySelector('#ei-target-type-filter').value;
    const sel = backdrop.querySelector('#ei-target-server-filter');
    const prev = sel.value;
    if (!sgId) {
      sel.innerHTML = '<option value="">전체 서버</option>';
      sel.disabled = true;
      renderEiTargetGroupFilterOptions(backdrop);
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === sgId);
    const servers = (sg ? sg.servers : []).filter((s) => !typeId || s.environment === typeId);
    sel.innerHTML = '<option value="">전체 서버</option>' +
      servers.map((s) => `<option value="${s.id}">${UI.esc(serverOptionLabel(s))}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderEiTargetGroupFilterOptions(backdrop);
  }

  function renderEiTargetGroupFilterOptions(backdrop) {
    const sgId = backdrop.querySelector('#ei-target-sg-filter').value;
    const serverId = backdrop.querySelector('#ei-target-server-filter').value;
    const sel = backdrop.querySelector('#ei-target-group-filter');
    const prev = sel.value;
    if (!sgId || !serverId) {
      sel.innerHTML = '<option value="">전체 그룹</option>';
      sel.disabled = true;
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === sgId);
    const server = sg ? sg.servers.find((s) => String(s.id) === serverId) : null;
    const groups = server ? server.groups : [];
    sel.innerHTML = '<option value="">전체 그룹</option>' +
      groups.map((g) => `<option value="${g.id}">${UI.esc(g.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
  }

  function renderEiTargetSgFilterOptions(backdrop) {
    const sel = backdrop.querySelector('#ei-target-sg-filter');
    sel.innerHTML = '<option value="">전체 서버그룹</option>' +
      Instances.getServerGroups().map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('');
    renderEiTargetServerFilterOptions(backdrop);
  }

  function renderEiTargetInstanceList(backdrop, checkedIds) {
    const container = backdrop.querySelector('#ei-target-instance-list');
    container.innerHTML = '';
    const eiInstances = eiTargetVisibleInstances(backdrop);
    if (!eiInstances.length) {
      container.innerHTML = '<div class="hint" style="padding:8px">조건에 맞는 EI 인스턴스가 없습니다.</div>';
      renderEiTargetCheckedSummary(backdrop, checkedIds);
      return;
    }
    for (const inst of eiInstances) {
      const row = document.createElement('label');
      row.className = 'checklist-item';
      const envClass = inst.environment === 'PROD' ? 'env-prod' : 'env-dev';
      row.innerHTML = `<input type="checkbox"> <span class="env-badge ${envClass}">${UI.esc(environmentLabel(inst.environment))}</span> [${UI.esc(inst.server_group_name)} / ${UI.esc(inst.server_name)} / ${UI.esc(inst.group_name)}] ${UI.esc(inst.name)} (${UI.esc(inst.host)}:${inst.port})`;
      const cb = row.querySelector('input');
      cb.checked = checkedIds.has(inst.id);
      cb.addEventListener('change', () => {
        if (cb.checked) checkedIds.add(inst.id); else checkedIds.delete(inst.id);
        renderEiTargetCheckedSummary(backdrop, checkedIds);
      });
      container.appendChild(row);
    }
    renderEiTargetCheckedSummary(backdrop, checkedIds);
  }

  function renderEiTargetCheckedSummary(backdrop, checkedIds) {
    const el = backdrop.querySelector('#ei-target-checked-summary');
    if (!el) return;
    el.textContent = checkedIds.size ? `${checkedIds.size}개 인스턴스 선택됨` : '선택된 인스턴스가 없습니다.';
  }

  function openEiDatasourceDialog(existing) {
    const isEdit = !!existing;
    const targetIds = new Set(isEdit ? [] : [currentInstance.id]);
    const hasAdvancedValues = isEdit && EI_POOL_FIELD_GROUPS.some((g) =>
      g.fields.some((f) => existing[f.key] != null && existing[f.key] !== ''));
    const backdrop = UI.openModal(`
      <div class="modal-header">${isEdit ? `데이터소스 수정: ${UI.esc(existing.name)}` : 'EI 데이터소스 등록'}</div>
      <div class="modal-body">
        <div class="field-group">
          <div class="field-row"><label>이름 *</label>
            <input type="text" id="ei-name" value="${isEdit ? UI.esc(existing.name) : ''}" ${isEdit ? 'disabled' : ''}></div>
          <div class="field-row"><label>설명</label>
            <input type="text" id="ei-desc" value="${isEdit ? UI.esc(existing.description || '') : ''}"></div>
          <div class="field-row"><label>JNDI 이름</label>
            <input type="text" id="ei-jndi" value="${isEdit ? UI.esc(existing.jndi_name || '') : ''}" placeholder="예: jdbc/MyDS"></div>
          <div class="field-row"><label>DB 템플릿</label>
            <select id="ei-template">
              <option value="">(직접 입력)</option>
              ${Object.entries(EI_DB_TEMPLATES).map(([key, t]) => `<option value="${key}">${UI.esc(t.label)}</option>`).join('')}
            </select></div>
          <div class="hint">템플릿을 고르면 Driver Class/Validation Query와 커넥션 풀 기본값을 자동으로 채웁니다.
            URL·사용자·비밀번호처럼 서버마다 다른 값만 직접 입력하면 됩니다.</div>
          <div class="field-row"><label>Driver Class *</label>
            <input type="text" id="ei-driver" value="${isEdit ? UI.esc(existing.driver_class_name || '') : ''}" placeholder="예: com.mysql.cj.jdbc.Driver"></div>
          <div class="field-row"><label>JDBC URL *</label>
            <input type="text" id="ei-url" value="${isEdit ? UI.esc(existing.url || '') : ''}" placeholder="예: jdbc:mysql://host:3306/db"></div>
          <div class="field-row"><label>사용자</label>
            <input type="text" id="ei-user" value="${isEdit ? UI.esc(existing.username || '') : ''}"></div>
          <div class="field-row"><label>비밀번호${isEdit ? '' : ' *'}</label>
            <input type="password" id="ei-pass" placeholder="${isEdit ? '비워두면 기존 비밀번호 유지' : ''}"></div>
        </div>
        <div class="row" style="justify-content:space-between;align-items:center">
          <div class="box-title">고급 설정 (커넥션 풀)</div>
          <button type="button" class="btn btn-sm" id="ei-adv-toggle">${hasAdvancedValues ? '접기' : '펼치기'}</button>
        </div>
        <div id="ei-adv-body" style="${hasAdvancedValues ? '' : 'display:none'}">${eiAdvancedSectionHtml(existing)}</div>
        <div id="ei-test-result" class="hint"></div>
        ${isEdit ? '' : `
        <div class="field-group">
          <div class="field-group-title">배포 대상 인스턴스</div>
          <div class="hint">체크한 모든 EI 인스턴스에 같은 정의로 동시에 등록합니다 (기본값: 지금 보고 있는 인스턴스).</div>
          <div class="row">
            <label for="ei-target-sg-filter" style="width:60px">서버그룹</label>
            <select id="ei-target-sg-filter" style="flex:1"><option value="">전체 서버그룹</option></select>
            <label for="ei-target-type-filter" style="width:60px">서버타입</label>
            <select id="ei-target-type-filter" style="flex:0.7">
              <option value="">전체</option>
              <option value="DEV">개발</option>
              <option value="PROD">운영</option>
              <option value="BOTH">운영개발</option>
            </select>
          </div>
          <div class="row">
            <label for="ei-target-server-filter" style="width:60px">서버</label>
            <select id="ei-target-server-filter" style="flex:1"><option value="">전체 서버</option></select>
            <label for="ei-target-group-filter" style="width:60px">그룹</label>
            <select id="ei-target-group-filter" style="flex:1"><option value="">전체 그룹</option></select>
          </div>
          <div class="row">
            <button type="button" class="btn btn-sm" id="ei-target-select-all">전체 선택</button>
            <button type="button" class="btn btn-sm" id="ei-target-select-none">선택 해제</button>
          </div>
          <div id="ei-target-instance-list" class="checklist" style="max-height:160px"></div>
          <div id="ei-target-checked-summary" class="hint"></div>
        </div>`}
      </div>
      <div class="modal-footer">
        <button class="btn" data-act="test">접속 테스트</button>
        <button class="btn" data-act="cancel">취소</button>
        <button class="btn btn-primary" data-act="save">${isEdit ? '수정' : '등록'}</button>
      </div>`, 760, { closeOnBackdrop: false });

    if (!isEdit) {
      renderEiTargetSgFilterOptions(backdrop);
      renderEiTargetInstanceList(backdrop, targetIds);

      const refreshTargetList = () => renderEiTargetInstanceList(backdrop, targetIds);
      backdrop.querySelector('#ei-target-sg-filter').addEventListener('change', () => {
        targetIds.clear();
        renderEiTargetServerFilterOptions(backdrop);
        refreshTargetList();
      });
      backdrop.querySelector('#ei-target-type-filter').addEventListener('change', () => {
        targetIds.clear();
        renderEiTargetServerFilterOptions(backdrop);
        refreshTargetList();
      });
      backdrop.querySelector('#ei-target-server-filter').addEventListener('change', () => {
        targetIds.clear();
        renderEiTargetGroupFilterOptions(backdrop);
        refreshTargetList();
      });
      backdrop.querySelector('#ei-target-group-filter').addEventListener('change', () => {
        targetIds.clear();
        refreshTargetList();
      });
      backdrop.querySelector('#ei-target-select-all').addEventListener('click', () => {
        for (const inst of eiTargetVisibleInstances(backdrop)) targetIds.add(inst.id);
        refreshTargetList();
      });
      backdrop.querySelector('#ei-target-select-none').addEventListener('click', () => {
        targetIds.clear();
        refreshTargetList();
      });
    }

    const advToggle = backdrop.querySelector('#ei-adv-toggle');
    const advBody = backdrop.querySelector('#ei-adv-body');
    advToggle.addEventListener('click', () => {
      const show = advBody.style.display === 'none';
      advBody.style.display = show ? '' : 'none';
      advToggle.textContent = show ? '접기' : '펼치기';
    });

    // 편집 중이면 현재 Driver Class와 일치하는 템플릿을 미리 선택만 해둔다(정보 표시용,
    // 값을 덮어쓰진 않음 — change 이벤트를 일부러 발생시키지 않는다).
    const templateSel = backdrop.querySelector('#ei-template');
    if (isEdit) templateSel.value = detectEiTemplateKey(existing.driver_class_name);
    templateSel.addEventListener('change', () => {
      const tpl = EI_DB_TEMPLATES[templateSel.value];
      if (!tpl) return;
      backdrop.querySelector('#ei-driver').value = tpl.driverClassName;
      backdrop.querySelector('#ei-url').placeholder = tpl.urlPlaceholder;
      backdrop.querySelector('#ei-adv-validationQuery').value = tpl.validationQuery;
      for (const [key, val] of Object.entries(EI_COMMON_POOL_DEFAULTS)) {
        const el = backdrop.querySelector(`#ei-adv-${key}`);
        if (el) el.value = val;
      }
      advBody.style.display = '';
      advToggle.textContent = '접기';
      UI.toast(`${tpl.label} 기본값을 적용했습니다. URL·사용자·비밀번호를 입력하세요.`);
    });

    const formData = () => ({
      name: backdrop.querySelector('#ei-name').value.trim(),
      description: backdrop.querySelector('#ei-desc').value.trim(),
      jndi_name: backdrop.querySelector('#ei-jndi').value.trim(),
      driver_class_name: backdrop.querySelector('#ei-driver').value.trim(),
      url: backdrop.querySelector('#ei-url').value.trim(),
      username: backdrop.querySelector('#ei-user').value.trim(),
      password: backdrop.querySelector('#ei-pass').value,
      ...collectEiAdvancedFields(backdrop),
    });

    backdrop.querySelector('[data-act=cancel]').onclick = () => UI.closeModal(backdrop);

    backdrop.querySelector('[data-act=test]').onclick = async () => {
      const f = formData();
      if (!f.driver_class_name || !f.url) { UI.toast('Driver Class와 JDBC URL을 입력하세요.', 'error'); return; }
      if (!isEdit && !f.password) { UI.toast('비밀번호를 입력하세요 (또는 저장된 값으로 재테스트하려면 먼저 등록하세요).', 'error'); return; }
      const resultEl = backdrop.querySelector('#ei-test-result');
      resultEl.textContent = '테스트 중...';
      resultEl.className = 'hint';
      // 수정 중이고 비밀번호를 비워뒀으면(=기존 값 유지) 서버에 저장된 값으로 재테스트한다.
      const res = (isEdit && !f.password)
        ? await Api.call('test_ei_datasource_existing', currentInstance.id, existing.name)
        : await Api.call('test_ei_datasource', currentInstance.id, f);
      resultEl.textContent = res.success ? `✅ 접속 성공 (${res.elapsed_ms ?? '-'}ms)` : `❌ ${res.error || '접속 실패'}`;
      resultEl.className = `hint ${res.success ? 'result-success' : 'result-failed'}`;
    };

    backdrop.querySelector('[data-act=save]').onclick = async () => {
      const f = formData();
      if (!f.name) { UI.toast('이름을 입력하세요.', 'error'); return; }
      if (!f.driver_class_name || !f.url) { UI.toast('Driver Class와 JDBC URL을 입력하세요.', 'error'); return; }
      if (!isEdit && !f.password) { UI.toast('비밀번호를 입력하세요.', 'error'); return; }

      if (isEdit) {
        const res = await Api.call('save_ei_datasource', currentInstance.id, { ...f, is_edit: true });
        if (!res.success) { UI.toast(`저장 실패: ${res.error || ''}`, 'error'); return; }
        UI.toast('수정되었습니다.');
        UI.closeModal(backdrop);
        await loadEi();
        return;
      }

      // 신규 등록 — 체크된 인스턴스 전부에 같은 정의로 병렬 등록.
      if (!targetIds.size) { UI.toast('배포 대상 인스턴스를 최소 1개 선택하세요.', 'error'); return; }
      const targets = Array.from(targetIds);
      const results = await Promise.all(
        targets.map((id) => Api.call('save_ei_datasource', id, { ...f, is_edit: false })));
      const allInstances = Instances.getFlatInstances();
      const failed = [];
      results.forEach((r, i) => {
        if (!r.success) {
          const inst = allInstances.find((x) => x.id === targets[i]);
          failed.push(`${inst ? inst.name : targets[i]}: ${r.error || ''}`);
        }
      });
      if (failed.length) {
        UI.toast(`${targets.length - failed.length}/${targets.length}개 성공, 실패 — ${failed.join('; ')}`, 'error');
      } else {
        UI.toast(targets.length > 1 ? `${targets.length}개 인스턴스에 등록되었습니다.` : '등록되었습니다.');
      }
      UI.closeModal(backdrop);
      if (targetIds.has(currentInstance.id)) await loadEi();
    };
  }

  async function runTest(names) {
    if (!currentInstance) return;
    if (!names.length) { UI.toast('데이터소스를 선택하세요.', 'error'); return; }
    for (const name of names) setStatusCell(name, '테스트 중...', '');
    if (eiMode) {
      await Promise.all(names.map(async (name) => {
        const res = await Api.call('test_ei_datasource_existing', currentInstance.id, name);
        applyEiResult(name, res);
      }));
      return;
    }
    const res = await Api.call('test_datasources', currentInstance.id, names);
    if (!res.success) { UI.toast(`테스트 실패: ${res.error || ''}`, 'error'); return; }
    for (const r of res.results) applyResult(r);
  }

  const testSelected = () => runTest(Array.from(checkedNames));

  /* 위쪽 데이터소스 목록에서 체크한 것들을 "여러 인스턴스에서 접속 테스트"의 입력칸으로
   * 보낸다. MI는 데이터소스 등록명(예: SST0002_MSSQL)과 실제 JNDI 이름(예: jdbc/SST0002)이
   * 이름 규칙일 뿐 보장된 게 아니므로 get_datasource_jndi_name로 실제 JNDI 이름을 조회해서
   * 채운다(showDetail과 같은 캐시 경로 사용). EI는 등록명 자체가 식별자라 조회 없이 그대로
   * 보낸다. */
  async function sendCheckedToMultiJndi() {
    if (!currentInstance) return;
    const names = Array.from(checkedNames);
    if (!names.length) { UI.toast('보낼 데이터소스를 선택하세요.', 'error'); return; }

    if (eiMode) {
      document.getElementById('ds-multi-jndi').value = names.join(', ');
      document.querySelector('input[name="ds-multi-mode"][value="jndi"]').checked = true;
      updateMultiModeUI();
      if (document.getElementById('ds-multi-body').style.display === 'none') toggleDsMultiSection();
      UI.toast(`${names.length}개 데이터소스를 보냈습니다.`);
      return;
    }

    UI.toast(`JNDI 이름 조회 중... (${names.length}개)`);
    const results = await Promise.all(
      names.map((n) => Api.call('get_datasource_jndi_name', currentInstance.id, n, false)));
    const jndiNames = [];
    const failed = [];
    results.forEach((r, i) => {
      if (r.success && r.jndi_name) jndiNames.push(r.jndi_name); else failed.push(names[i]);
    });
    if (!jndiNames.length) { UI.toast('JNDI 이름을 하나도 조회하지 못했습니다.', 'error'); return; }

    document.getElementById('ds-multi-jndi').value = jndiNames.join(', ');
    document.querySelector('input[name="ds-multi-mode"][value="jndi"]').checked = true;
    updateMultiModeUI();
    if (document.getElementById('ds-multi-body').style.display === 'none') toggleDsMultiSection();

    if (failed.length) {
      UI.toast(`${jndiNames.length}개를 보냈습니다 (JNDI 조회 실패 ${failed.length}개: ${failed.join(', ')})`, 'error');
    } else {
      UI.toast(`${jndiNames.length}개 데이터소스를 JNDI 입력으로 보냈습니다.`);
    }
  }

  async function showDetail(name, forceRefreshJndi = false) {
    const [res, jndiRes] = await Promise.all([
      Api.call('get_datasource', currentInstance.id, name),
      Api.call('get_datasource_jndi_name', currentInstance.id, name, forceRefreshJndi),
    ]);
    if (!res.success) { UI.toast(`조회 실패: ${res.error || ''}`, 'error'); return; }
    const d = res.data;
    const jndiLabel = jndiRes.success ? jndiRes.jndi_name : `조회 실패 (${jndiRes.error || ''})`;
    if (forceRefreshJndi) {
      UI.toast(jndiRes.success ? 'JNDI 이름을 캐시 무시하고 다시 조회했습니다.' : `새로고침 실패: ${jndiRes.error || ''}`,
        jndiRes.success ? undefined : 'error');
    }
    UI.detailModal(`데이터소스: ${name}`, [
      ['이름', d.name], ['설명', d.description], ['타입', d.type],
      ['드라이버', d.driverClass], ['URL', d.url], ['사용자', d.userName],
      ['실제 JNDI 이름', jndiLabel],
    ], 520, [
      { label: '🔄 JNDI 새로고침', onClick: () => showDetail(name, true) },
    ]);
  }

  /* 좌측 트리에서 서버그룹/서버/그룹/인스턴스 중 무엇을 클릭하든 그 깊이까지 4단
   * 셀렉트를 맞춘다 — 지정 안 된 하위 단계는 wireInstanceCascade가 첫 항목으로 채운다. */
  function onTreeNodeSelected(e) {
    instSelect.refresh(UI.treeNodeToChain(e.detail.kind, e.detail.data));
  }

  // ── 여러 인스턴스에서 같은 JNDI 이름(들)으로 접속 테스트 ─────────────────

  let multiCheckedIds = new Set();
  let multiResults = {}; // "instanceId::jndiName" -> 결과

  // 서버그룹을 고르기 전에는 아무것도 보여주지 않는다 — 인스턴스가 많은 환경에서
  // 탭을 펼치자마자 전체 인스턴스가 한꺼번에 나오면 부담스럽다는 요청에 따른 것.
  function multiVisibleInstances() {
    const sgId = document.getElementById('ds-multi-sg-filter').value;
    if (!sgId) return [];
    const typeId = document.getElementById('ds-multi-type-filter').value;
    const serverId = document.getElementById('ds-multi-server-filter').value;
    const groupId = document.getElementById('ds-multi-group-filter').value;
    // 대상 인스턴스는 지금 보고 있는 소스와 같은 방식(EI SOAP ↔ MI dbinfo REST)이어야
    // 한다 — 식별자(등록명 vs JNDI)와 프로토콜이 서로 달라 섞어서 보낼 수 없다.
    return Instances.getFlatInstances().filter((i) =>
      (eiMode ? i.product === 'EI' : i.type.startsWith('MI_')) &&
      String(i.server_group_id) === sgId &&
      (!typeId || i.server_environment === typeId) &&
      (!serverId || String(i.server_id) === serverId) &&
      (!groupId || String(i.group_id) === groupId));
  }

  // 서버 필터는 서버그룹을, 그룹 필터는 서버를 먼저 골라야 의미가 있다 —
  // 상위가 "전체"면 하위 선택지를 비우고 비활성화한다. 서버타입은 서버의 운영환경으로
  // 서버 단을 한 번 더 좁히는 보조 필터다.
  function renderMultiServerFilterOptions() {
    const sgId = document.getElementById('ds-multi-sg-filter').value;
    const typeId = document.getElementById('ds-multi-type-filter').value;
    const sel = document.getElementById('ds-multi-server-filter');
    const prev = sel.value;
    if (!sgId) {
      sel.innerHTML = '<option value="">전체 서버</option>';
      sel.disabled = true;
      renderMultiGroupFilterOptions();
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === sgId);
    const servers = (sg ? sg.servers : []).filter((s) => !typeId || s.environment === typeId);
    sel.innerHTML = '<option value="">전체 서버</option>' +
      servers.map((s) => `<option value="${s.id}">${UI.esc(serverOptionLabel(s))}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderMultiGroupFilterOptions();
  }

  function renderMultiGroupFilterOptions() {
    const sgId = document.getElementById('ds-multi-sg-filter').value;
    const serverId = document.getElementById('ds-multi-server-filter').value;
    const sel = document.getElementById('ds-multi-group-filter');
    const prev = sel.value;
    if (!sgId || !serverId) {
      sel.innerHTML = '<option value="">전체 그룹</option>';
      sel.disabled = true;
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === sgId);
    const server = sg ? sg.servers.find((s) => String(s.id) === serverId) : null;
    const groups = server ? server.groups : [];
    sel.innerHTML = '<option value="">전체 그룹</option>' +
      groups.map((g) => `<option value="${g.id}">${UI.esc(g.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
  }

  function renderMultiSgFilterOptions() {
    const sel = document.getElementById('ds-multi-sg-filter');
    const prev = sel.value;
    // "전체"가 아니라 "선택..."으로 — 서버그룹을 고르기 전엔 목록이 비어있으므로
    // 플레이스홀더가 실제 동작(아무것도 안 보여줌)과 맞아야 한다.
    sel.innerHTML = '<option value="">서버그룹 선택...</option>' +
      Instances.getServerGroups().map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderMultiServerFilterOptions();
  }

  function renderMultiInstanceList() {
    const container = document.getElementById('ds-multi-instance-list');
    container.innerHTML = '';
    if (!document.getElementById('ds-multi-sg-filter').value) {
      container.innerHTML = '<div class="hint" style="padding:8px">서버그룹을 선택하면 인스턴스 목록이 표시됩니다.</div>';
      renderMultiCheckedSummary();
      return;
    }
    for (const inst of multiVisibleInstances()) {
      const row = document.createElement('label');
      row.className = 'checklist-item';
      const envClass = inst.environment === 'PROD' ? 'env-prod' : 'env-dev';
      row.innerHTML = `<input type="checkbox"> <span class="env-badge ${envClass}">${UI.esc(environmentLabel(inst.environment))}</span> [${UI.esc(inst.server_name)} / ${UI.esc(inst.group_name)}] ${UI.esc(inst.name)} (${UI.esc(inst.host)}:${inst.port})`;
      const cb = row.querySelector('input');
      cb.checked = multiCheckedIds.has(inst.id);
      cb.addEventListener('change', () => {
        if (cb.checked) multiCheckedIds.add(inst.id); else multiCheckedIds.delete(inst.id);
        renderMultiCheckedSummary();
      });
      container.appendChild(row);
    }
    renderMultiCheckedSummary();
  }

  /* 필터가 바뀌면 multiCheckedIds를 비우므로(init()의 필터 change 리스너 참고 — 배포/
   * 재시작 탭에서 실제로 "2개 체크한 줄 알았는데 4개가 대상" 사고가 있어서 필터가
   * 바뀌면 선택을 비우는 걸 기본값으로 바꿨다) 이 요약줄은 평상시엔 항상 현재
   * 체크리스트와 일치해야 정상이다. 그래도 방어적으로, 삭제된 id처럼 더 이상
   * 존재하지 않는 항목은 여기서 걸러내면서 multiCheckedIds 자체에서도 정리한다. */
  function renderMultiCheckedSummary() {
    const el = document.getElementById('ds-multi-checked-summary');
    if (!el) return;
    const all = Instances.getFlatInstances();
    const targets = [];
    for (const id of Array.from(multiCheckedIds)) {
      const inst = all.find((i) => i.id === id);
      if (inst) targets.push(inst); else multiCheckedIds.delete(id);
    }
    if (!targets.length) { el.textContent = ''; return; }
    const visibleIds = new Set(multiVisibleInstances().map((i) => i.id));
    const names = targets.map((i) => {
      const label = `[${i.server_group_name} / ${i.server_name} / ${i.group_name}] ${i.name}`;
      return visibleIds.has(i.id) ? label : `${label} (현재 필터에 안 보임)`;
    });
    el.textContent = `현재 선택된 테스트 대상 (전체 ${targets.length}개): ${names.join(', ')}`;
  }

  function multiSelectAll() {
    multiCheckedIds = new Set(multiVisibleInstances().map((i) => i.id));
    renderMultiInstanceList();
  }
  function multiSelectNone() { multiCheckedIds.clear(); renderMultiInstanceList(); }

  function renderMultiResults(results) {
    multiResults = {};
    const tbody = document.querySelector('#ds-multi-result-table tbody');
    tbody.innerHTML = '';
    if (!results.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="hint">결과가 없습니다.</td></tr>';
      return;
    }
    // 결과에는 instance_name만 있어서, 같은 이름의 인스턴스가 여러 개 있으면(또는
    // 실제로는 같은 인스턴스인데 중복 전송돼서) 결과 행끼리 구분이 안 되는 문제가
    // 있었다 — 이미 로드된 인스턴스 목록에서 소속 서버그룹/서버/그룹을 찾아 같이 보여준다.
    const allInstances = Instances.getFlatInstances();
    for (const r of results) {
      const key = `${r.instance_id}::${r.jndi_name || ''}`;
      multiResults[key] = r;
      const ctx = allInstances.find((i) => i.id === r.instance_id);
      const displayName = ctx ? `[${ctx.server_group_name} / ${ctx.server_name} / ${ctx.group_name}] ${r.instance_name}` : r.instance_name;
      const tr = document.createElement('tr');
      tr.style.cursor = 'pointer';
      const statusHtml = r.success
        ? `<span class="result-success">✅ ${UI.esc(r.db_type || '')} (${r.elapsed_ms ?? '-'}ms)</span>`
        : `<span class="result-failed">❌ ${UI.esc(r.error || '실패')}</span>`;
      tr.innerHTML = `
        <td>${UI.esc(displayName)}</td>
        <td>${UI.esc(r.jndi_name || '-')}</td>
        <td>${statusHtml}</td>
        <td>${UI.esc(r.db_type || '-')}</td>
        <td style="max-width:320px;overflow-wrap:anywhere">${UI.esc(r.url || '-')}</td>
        <td>${r.elapsed_ms != null ? `${r.elapsed_ms}ms` : '-'}</td>`;
      tr.addEventListener('click', () => {
        UI.detailModal(`접속 테스트 결과: ${displayName}`, [
          ['인스턴스', displayName], ['JNDI 이름', r.jndi_name], ['결과', r.success ? '성공' : '실패'],
          ['DB 타입', r.db_type], ['제품', r.product], ['버전', r.version],
          ['URL', r.url], ['사용자', r.user],
          ['응답 시간', r.elapsed_ms != null ? `${r.elapsed_ms}ms` : null], ['오류', r.error],
        ], 560);
      });
      tbody.appendChild(tr);
    }
  }

  /* JNDI(등록된 데이터소스) / URL 직접 입력(등록 전) 두 모드를 같은 인스턴스
   * 체크리스트+결과 테이블로 공유한다. */
  function getMultiMode() {
    return document.querySelector('input[name="ds-multi-mode"]:checked').value; // 'jndi' | 'direct'
  }

  function updateMultiModeUI() {
    const mode = getMultiMode();
    document.getElementById('ds-multi-jndi-row').style.display = mode === 'jndi' ? '' : 'none';
    document.getElementById('ds-multi-direct-row').style.display = mode === 'direct' ? '' : 'none';
    document.getElementById('ds-multi-hint').style.display = mode === 'jndi' ? '' : 'none';
    // EI는 SOAP testDataSourceConnection에 driverClassName이 필수라 드라이버 선택이
    // 필요하지만, MI dbinfo의 CONNECTION_TEST_DIRECT는 URL 접두어로 자동 판별한다.
    document.getElementById('ds-direct-driver-select').style.display = (mode === 'direct' && eiMode) ? '' : 'none';
  }

  async function runMultiTest() {
    if (!multiCheckedIds.size) { UI.toast('대상 인스턴스를 선택하세요.', 'error'); return; }
    const mode = getMultiMode();
    let res;
    if (mode === 'jndi') {
      const names = document.getElementById('ds-multi-jndi').value.split(',').map((s) => s.trim()).filter(Boolean);
      if (!names.length) {
        UI.toast(eiMode ? '데이터소스 등록명을 입력하세요 (여러 개는 ,로 구분).' : 'JNDI 이름을 입력하세요 (여러 개는 ,로 구분).', 'error');
        return;
      }
      document.querySelector('#ds-multi-result-table tbody').innerHTML =
        `<tr><td colspan="6" class="hint">테스트 중... (인스턴스 ${multiCheckedIds.size}개 × ${names.length}개)</td></tr>`;
      res = eiMode
        ? await Api.call('test_ei_datasource_multi', names, Array.from(multiCheckedIds))
        : await Api.call('test_datasource_multi', names, Array.from(multiCheckedIds));
    } else {
      const url = document.getElementById('ds-direct-url').value.trim();
      const user = document.getElementById('ds-direct-user').value.trim();
      const password = document.getElementById('ds-direct-password').value;
      if (!url) { UI.toast('JDBC URL을 입력하세요.', 'error'); return; }
      document.querySelector('#ds-multi-result-table tbody').innerHTML =
        `<tr><td colspan="6" class="hint">테스트 중...</td></tr>`;
      if (eiMode) {
        const driverKey = document.getElementById('ds-direct-driver-select').value;
        const driverClassName = EI_DB_TEMPLATES[driverKey].driverClassName;
        res = await Api.call('test_ei_datasource_direct_multi', driverClassName, url, user, password, Array.from(multiCheckedIds));
      } else {
        res = await Api.call('test_datasource_direct_multi', url, user, password, Array.from(multiCheckedIds));
      }
    }
    if (!res.success) { UI.toast(`테스트 실패: ${res.error || ''}`, 'error'); return; }
    renderMultiResults(res.results);
  }

  function toggleDsListSection() {
    const sec = document.getElementById('ds-table-wrap');
    const btn = document.getElementById('btn-ds-list-toggle');
    const collapse = sec.style.display !== 'none';
    sec.style.display = collapse ? 'none' : '';
    btn.textContent = collapse ? '목록 펼치기' : '목록 접기';
  }

  /* "여러 인스턴스에서 접속 테스트"는 기본적으로 접어둔다 — 펼쳐져 있으면 그 아래
   * 체크리스트/결과 테이블이 공간을 많이 차지해서 정작 위쪽 "조회된 데이터소스
   * 목록"이 좁아 보이는 문제가 있었다. 접혀 있는 동안은 화면에서 안 보이기만 하는
   * 게 아니라 실제로도 안 쓰는 상태라(필터/체크박스는 숨겨진 동안 조작할 수 없다),
   * 펼칠 때 그 시점 기준으로 목록만 한 번 새로 그려준다. */
  function toggleDsMultiSection() {
    const body = document.getElementById('ds-multi-body');
    const btn = document.getElementById('btn-ds-multi-toggle');
    const expand = body.style.display === 'none';
    body.style.display = expand ? '' : 'none';
    btn.textContent = expand ? '접기' : '펼치기';
    if (expand) renderMultiInstanceList();
  }

  async function clearJndiCache() {
    if (!currentInstance) { UI.toast('인스턴스를 먼저 선택하세요.', 'error'); return; }
    const ok = await UI.confirm(
      `[${currentInstance.name}]의 JNDI 캐시를 전부 삭제합니다.\n` +
      '다음 조회부터는 (개별 데이터소스마다) CAR을 다시 읽어서 새로 채워집니다.',
      'JNDI 캐시 초기화 확인');
    if (!ok) return;
    const res = await Api.call('clear_datasource_jndi_cache', currentInstance.id);
    if (!res.success) { UI.toast(`초기화 실패: ${res.error || ''}`, 'error'); return; }
    UI.toast(`JNDI 캐시 ${res.cleared}개 항목을 삭제했습니다.`);
  }

  function init() {
    document.getElementById('btn-ds-refresh').addEventListener('click', load);
    document.getElementById('btn-ds-clear-jndi-cache').addEventListener('click', clearJndiCache);
    document.getElementById('btn-ds-ei-add').addEventListener('click', () => openEiDatasourceDialog(null));
    document.getElementById('btn-ds-list-toggle').addEventListener('click', toggleDsListSection);
    document.getElementById('btn-ds-multi-toggle').addEventListener('click', toggleDsMultiSection);
    document.getElementById('btn-ds-export').addEventListener('click', exportDsList);
    document.getElementById('btn-ds-send-to-multi').addEventListener('click', sendCheckedToMultiJndi);
    document.getElementById('btn-ds-search').addEventListener('click', runSearch);
    document.getElementById('btn-ds-search-clear').addEventListener('click', load);
    document.getElementById('ds-search').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') runSearch();
    });
    document.getElementById('btn-ds-select-all').addEventListener('click', selectAll);
    document.getElementById('btn-ds-select-none').addEventListener('click', selectNone);
    document.getElementById('btn-ds-test-selected').addEventListener('click', testSelected);
    document.getElementById('btn-ds-multi-select-all').addEventListener('click', multiSelectAll);
    document.getElementById('btn-ds-multi-select-none').addEventListener('click', multiSelectNone);
    document.getElementById('btn-ds-multi-test').addEventListener('click', runMultiTest);
    document.querySelectorAll('input[name="ds-multi-mode"]').forEach((r) => r.addEventListener('change', updateMultiModeUI));
    document.getElementById('ds-multi-sg-filter').addEventListener('change', () => { multiCheckedIds.clear(); renderMultiServerFilterOptions(); renderMultiInstanceList(); });
    document.getElementById('ds-multi-type-filter').addEventListener('change', () => { multiCheckedIds.clear(); renderMultiServerFilterOptions(); renderMultiInstanceList(); });
    document.getElementById('ds-multi-server-filter').addEventListener('change', () => { multiCheckedIds.clear(); renderMultiGroupFilterOptions(); renderMultiInstanceList(); });
    document.getElementById('ds-multi-group-filter').addEventListener('change', () => { multiCheckedIds.clear(); renderMultiInstanceList(); });

    instSelect = UI.wireInstanceCascade(
      document.getElementById('ds-sg-select'), document.getElementById('ds-type-select'),
      document.getElementById('ds-server-select'),
      document.getElementById('ds-group-select'), document.getElementById('ds-instance-select'),
      (inst) => { currentInstance = inst; showPrompt(); });

    window.addEventListener('instances-changed', () => {
      renderMultiSgFilterOptions();
      renderMultiInstanceList();
      instSelect.refresh(currentInstance ? currentInstance.id : null);
    });
    window.addEventListener('tree-node-selected', onTreeNodeSelected);
    showPrompt();
  }

  return { init, load };
})();
